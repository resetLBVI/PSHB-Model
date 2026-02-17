package pshb.Utils;

import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.referencing.CRS;

import java.awt.*;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * LazyVegGeoTiff is a memory-efficient raster utility class that lazily loads GeoTIFF vegetation data and
 * provides tile-based pixel sampling and spatial metadata access without loading the entire raster into memory.
 */
public final class LazyVegGeoTiff {
    private final File file;
    private final GeoTiffReader reader;
    private final GridCoverage2D coverage;            // lazy, tile-backed
    private final GridGeometry2D gridGeom;             // for CoordinateConverter helpers
    private final CoordinateReferenceSystem crs;       // for CoordinateConverter helpers
    private final Object lock = new Object();

    public LazyVegGeoTiff(File tiff) throws IOException {
        this.file = Objects.requireNonNull(tiff, "tiff file is null");
        this.reader = new GeoTiffReader(file);
        this.coverage = reader.read((GeneralParameterValue[]) null);  // DO NOT call getData() here
        this.gridGeom = coverage.getGridGeometry();
        this.crs = coverage.getCoordinateReferenceSystem2D();
    }


    /** Sample by grid cell (column, row). Band defaults to 0. */
    public double valueAtGrid(int col, int row) {
        return valueAtGrid(col, row, 0);
    }

    /** Sample by grid cell (column, row, band). Reads only the tile containing that pixel. */
    public double valueAtGrid(int col, int row, int band) {
        Rectangle one = new Rectangle(col, row, 1, 1);
        synchronized (lock) {
            Raster r = coverage.getRenderedImage().getData(one);
            return r.getSampleDouble(one.x, one.y, band);
        }
    }

    /** Optional bounds check helper (so callers can guard before sampling). */
    public boolean inBounds(int col, int row) {
        var img = coverage.getRenderedImage();
        int width = img.getWidth();
        int height = img.getHeight();
        return (col >= 0 && row >= 0 && col < width && row < height);
    }

    public GridGeometry2D getGridGeometry() { return gridGeom; }
    public CoordinateReferenceSystem getCRS() { return crs; }
    /** get the dimensions*/
    public GridEnvelope2D getGridEnvelope() {
        return coverage.getGridGeometry().getGridRange2D();
    }

    public int getWidth() {
        return coverage.getRenderedImage().getWidth();
    }

    public int getHeight() {
        return coverage.getRenderedImage().getHeight();
    }



    public void dispose() {
        try { if (coverage != null) coverage.dispose(true); } catch (Throwable ignored) {}
        try { if (reader != null) reader.dispose(); } catch (Throwable ignored) {}
    }

    @Override public String toString() { return "LazyGeoTiff(" + file.getName() + ")"; }
}
