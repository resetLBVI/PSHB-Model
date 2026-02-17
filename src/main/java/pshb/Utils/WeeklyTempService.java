package pshb.Utils;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.geotools.api.referencing.datum.PixelInCell;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import java.awt.image.RenderedImage;

import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.api.parameter.ParameterValueGroup;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;


/**
 * WeeklyTempService is a lazy, tile-based GeoTIFF temperature access service that provides week-specific
 * raster metadata and per-cell temperature sampling with an LRU cache to minimize memory use and open-file
 * overhead.
 * Weekly temperature sampler for GeoTIFFs named:
 *   /RESET_PSHB_inputData/TempGridReset_week_%d.tif
 *
 * - Pure GeoTools (no GeoMason types).
 * - Lazy per-pixel reads (1×1 windows) to avoid OOM.
 * - Small LRU cache of readers + precomputed transforms per week.
 * - Thread-safe per week (synchronized on context).
 */
public final class WeeklyTempService implements AutoCloseable {

    private final int weeks;
    private final String baseDir;

    /** Small LRU of per-week contexts (limits open files). */
    private final Map<Integer, Ctx> cache = new LinkedHashMap<Integer, Ctx>(8, 0.75f, true) {
        private static final int MAX = 4;
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Ctx> e) {
            if (size() > MAX) { e.getValue().dispose(); return true; }
            return false;
        }
    };

    public WeeklyTempService(int weeks) { this(weeks, "/RESET_PSHB_inputData"); }

    public WeeklyTempService(int weeks, String baseDir) {
        if (weeks < 1) throw new IllegalArgumentException("weeks must be >= 1");
        this.weeks = weeks;
        this.baseDir = Objects.requireNonNull(baseDir);

        //Initiate Tiff readers
        initTiffReaders();
    }

    // ----- public API --------------------------------------------------------

    /** Get the CRS for a given week (use with your coordToGrid helper). */
    public CoordinateReferenceSystem getCRS(int week) throws Exception {
        return ctx(week).crs;
    }

    /** Get the GridGeometry2D for a given week (use with your coordToGrid/gridToCoord helpers). */
    public GridGeometry2D getGridGeometry(int week) throws Exception {
        return ctx(week).ggTemp;
    }

    /** Fast path if you already have raster grid indices. */
    //col = grid[0] = tempX, row = grid[1] = tempY
    public double getTempAtGrid(int week, int col, int row) throws Exception {
//        System.out.println("map week:" + week + " col:" + col + " row:" + row);
        Ctx c = ctx(week);
        synchronized (c) {
            if (col < 0 || row < 0 || col >= c.width || row >= c.height) {
                throw new IndexOutOfBoundsException("Grid index out of range: (" + col + "," + row + ")");
            }

            double value = readOne(c, col, row);
            // Handle NoData or invalid values
            if (week == 1 && col == 189 && row == 154) {
                System.out.println("[DEBUG RAW] week=" + week + " col=" + col + " row=" + row + " value=" + value);
            }

            return value;
        }
    }

    /** Optional metadata lookups. */
    public int getWidth(int week)  throws Exception { return ctx(week).width;  }
    public int getHeight(int week) throws Exception { return ctx(week).height; }

    @Override public void close() {
        for (Ctx c : cache.values()) c.dispose();
        cache.clear();
    }

    // ----- internals ---------------------------------------------------------

    private Ctx ctx(int week) throws Exception {
        if (week < 1 || week > weeks) throw new IllegalArgumentException("week must be 1.."+weeks);
        Ctx c = cache.get(week);
        if (c != null) return c;

        String fileName = OutputWriter.getFileName(
                baseDir + "/" + String.format("TempGridReset_week_%d.tif", week), true);
        Path p = Paths.get(fileName);
        File f = p.toFile();
        if (!f.exists()) throw new IllegalStateException("GeoTIFF not found: " + f.getAbsolutePath());
        c = new Ctx(new GeoTiffReader(f));
        cache.put(week, c);
        return c;
    }

    /** 1×1 lazy read + safe sampling (no full-image getData()). */

    private static double readOne(Ctx c, int col, int row) {
        double[] sample = new double[1];
        c.coverageTemp.evaluate(new GridCoordinates2D(col, row), sample);
        double v = sample[0];

        // NoData handling
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= -3.3E38 || v >= 3.3E38) return Double.NaN;
        if (c.noDataValues != null) {
            for (double nd : c.noDataValues) {
                if (Math.abs(v - nd) <= 1e-6 * (1.0 + Math.abs(nd))) return Double.NaN;
            }
        }
        return v;
    }


    /** Per-week context: reader_Temp, dimensions, CRS, grid geometry. */
    private static final class Ctx {
        // IO + coverage
        final GeoTiffReader reader_Temp;
        final GridCoverage2D coverageTemp;   // keep one coverage to evaluate() directly
        final RenderedImage image;           // for width/height fast access

        // Geometry/CRS
        final GridGeometry2D ggTemp;
        final CoordinateReferenceSystem crs;
        final MathTransform gridToCRS2D;

        // Cached NoData (if present)
        final double[] noDataValues;

        // Dimensions
        final int width, height;

        Ctx(GeoTiffReader reader_Temp) throws Exception {
            this.reader_Temp = Objects.requireNonNull(reader_Temp, "reader_Temp");

            // Read coverage once
            GridCoverage2D cov = (GridCoverage2D) reader_Temp.read(null);
            this.coverageTemp = cov;
            this.ggTemp       = cov.getGridGeometry();
            this.crs          = cov.getCoordinateReferenceSystem2D();
            this.gridToCRS2D  = ggTemp.getGridToCRS2D(); // handy if you ever map grid<->world

            // Dimensions from the actual rendered image (safer than original grid range)
            this.image  = cov.getRenderedImage();
            this.width  = image.getWidth();
            this.height = image.getHeight();
            System.out.printf("Width: %d, Height: %d%n", width, height);

            // Cache NoData once (support common variants)
            this.noDataValues = extractNoData(cov);
        }

        void dispose() {
            try { if (coverageTemp != null) coverageTemp.dispose(true); } catch (Exception ignore) {}
            try { reader_Temp.dispose(); } catch (Exception ignore) {}
        }

        // ---- helpers ----

        private static double[] extractNoData(GridCoverage2D cov) {
            // 1) Properties some writers use
            Object p = cov.getProperty("GC_NODATA");
            double[] nd = valuesFromProp(p);
            if (nd != null) return nd;

            p = cov.getProperty("NoData");
            nd = valuesFromProp(p);
            if (nd != null) return nd;

            // 2) From SampleDimensions (preferred when available)
            try {
                var sd = cov.getSampleDimension(0);
                if (sd != null && sd.getNoDataValues() != null && sd.getNoDataValues().length > 0) {
                    return sd.getNoDataValues();
                }
            } catch (Throwable ignored) { /* version differences */ }

            // 3) Fallback: include the classic float sentinel
            return new double[]{-3.4028235E38, -9999.0, 1.0E20};
        }

        private static double[] valuesFromProp(Object p) {
            if (p == null) return null;
            if (p instanceof Number)   return new double[]{((Number) p).doubleValue()};
            if (p instanceof double[]) return (double[]) p;
            if (p instanceof float[]) {
                float[] fs = (float[]) p;
                double[] d = new double[fs.length];
                for (int i = 0; i < fs.length; i++) d[i] = fs[i];
                return d;
            }
            return null;
        }
    }



    private static void initTiffReaders() {
        // Discover any ImageIO plugins on the classpath
        ImageIO.scanForPlugins();

        // Print what the JVM actually sees
        System.out.println("ImageIO TIFF readers (initial):");
        var it = ImageIO.getImageReadersByFormatName("tiff");
        while (it.hasNext()) {
            System.out.println(" - " + it.next().getClass().getName());
        }

        // Prefer non-GeoSolutions readers (e.g., TwelveMonkeys) if present
        IIORegistry reg = IIORegistry.getDefaultInstance();

        // Deregister GeoSolutions TIFF/COG readers so others are chosen
        var itSpi = reg.getServiceProviders(ImageReaderSpi.class, true);
        java.util.List<ImageReaderSpi> remove = new java.util.ArrayList<>();
        java.util.List<ImageReaderSpi> keep   = new java.util.ArrayList<>();
        while (itSpi.hasNext()) {
            ImageReaderSpi spi = itSpi.next();
            String name = spi.getClass().getName();
            if (name.startsWith("it.geosolutions.imageioimpl.plugins.tiff")
                    || name.startsWith("it.geosolutions.imageioimpl.plugins.cog")) {
                remove.add(spi);
            } else if (name.toLowerCase().contains("twelvemonkeys")) {
                keep.add(spi);
            }
        }
        for (ImageReaderSpi spi : remove) {
            reg.deregisterServiceProvider(spi);
        }

        System.out.println("Removed GeoSolutions TIFF/COG SPIs: " + remove.size());
        System.out.println("Kept potential TwelveMonkeys SPIs: " + keep.size());

        // Print the final list again
        System.out.println("ImageIO TIFF readers (after preference):");
        it = ImageIO.getImageReadersByFormatName("tiff");
        while (it.hasNext()) {
            System.out.println(" - " + it.next().getClass().getName());
        }
    }


}

