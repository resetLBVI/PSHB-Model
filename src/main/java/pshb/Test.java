package pshb;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.coverage.grid.GridGeometry2D;
import pshb.Utils.CoordinateConverter;
import pshb.Utils.LazyVegGeoTiff;
import pshb.Utils.OutputWriter;

import java.io.File;

public class Test {

    // Manual conversion parameters — raw corner coords from actual GeoTIFF metadata
    static final double xllcornerVeg = -177582;
    static final double yllcornerVeg = -604425;
    static final int    vegCellSize  = 30;
    static final int    nRowsVeg     = 17272;

    public static void main(String[] args) throws Exception {

        // Load the veg GeoTIFF (same path as PSHBEnvironment)
        String path = OutputWriter.getFileName("RESET_PSHB_inputData/inVegRaster_PrHost_20260429.tif", true)
                                  .replace("%20", " ");
        LazyVegGeoTiff veg = new LazyVegGeoTiff(new File(path));
        GridGeometry2D         gg  = veg.getGridGeometry();
        CoordinateReferenceSystem crs = veg.getCRS();

        // Test points (projected coordinates in the veg raster's CRS, units = metres)
        // Each row: { x (easting), y (northing) }
//        double[][] testPoints = {
//            { -150000, -400000 },   // mid-raster area
//            { -180000, -600000 },   // near lower-left corner
//            { -100000, -200000 },   // upper-right area
//        };
        double[][] testPoints = {
                { 211391.2169, -494102.8811 },   // PatchID = 803
                { 179997.2869, -441568.4714 },   // PatchID = 11806
                { 179082.2832, -441264.8342 },   // PatchID = 4955
        };

        // Print actual GeoTIFF metadata so we can verify our hardcoded constants
        org.geotools.geometry.jts.ReferencedEnvelope env = veg.getGridGeometry().getEnvelope2D();
        int width  = veg.getWidth();
        int height = veg.getHeight();
        System.out.println("=== GeoTIFF actual metadata ===");
        System.out.printf("  xllcorner (west edge) : %.6f%n", env.getMinX());
        System.out.printf("  yllcorner (south edge): %.6f%n", env.getMinY());
        System.out.printf("  cellSize X            : %.6f%n", env.getWidth()  / width);
        System.out.printf("  cellSize Y            : %.6f%n", env.getHeight() / height);
        System.out.printf("  nCols                 : %d%n",   width);
        System.out.printf("  nRows                 : %d%n",   height);
        System.out.println();

        System.out.println("=== coordToGrid (GeoTools) vs manual conversion ===");
        System.out.printf("%-20s %-20s %-12s %-12s %-12s %-12s %-10s%n",
                "x (easting)", "y (northing)",
                "GT col", "MAN col", "GT row", "MAN row", "MATCH?");
        System.out.println("-".repeat(90));

        boolean allPassed = true;
        for (double[] pt : testPoints) {
            double x = pt[0];
            double y = pt[1];

            // GeoTools approach
            int[] gt = CoordinateConverter.coordToGrid(crs, gg, x, y);
            int gtCol = gt[0];
            int gtRow = gt[1];

            // Manual approach
            int manCol = CoordinateConverter.longitudeXToGridX(x, xllcornerVeg, vegCellSize);
            int manRow = CoordinateConverter.latitudeYToGridY(y, yllcornerVeg, vegCellSize, nRowsVeg);

            boolean match = (gtCol == manCol) && (gtRow == manRow);
            allPassed &= match;

            System.out.printf("%-20.1f %-20.1f %-12d %-12d %-12d %-12d %-10s%n",
                    x, y, gtCol, manCol, gtRow, manRow, match ? "PASS" : "FAIL !");
        }

        System.out.println("-".repeat(90));
        System.out.println(allPassed ? "All tests PASSED." : "Some tests FAILED — check the rows above.");

        veg.dispose();
    }
}