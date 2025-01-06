package pshb.Utils;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.Position2D;
import org.geotools.geometry.jts.ReferencedEnvelope;

public class CoordinateConverter {

    public static int longitudeXToGridX(Double longitudeX, double xllcorner, int cellSize){
        int x = (int) (longitudeX - xllcorner)/ cellSize;
        return x;
    }

    public static int latitudeYToGridY(Double latitudeY, double yllcorner, int cellSize, int nRows){
        int y = (int) (yllcorner + cellSize * nRows - latitudeY)/ cellSize;
        return y;
    }


    // convert lon/lat gps coordinates to tiff x/y coordinates x=lon; y=lat
    public static int[] coordToGrid(CoordinateReferenceSystem crs, GridGeometry2D gg, double lon, double lat) throws TransformException {
        Position2D posWorld = new Position2D(crs, lon, lat); // longitude supplied first
        GridCoordinates2D posGrid = gg.worldToGrid(posWorld);
        int[] grids = new int[2];
        grids[0] = posGrid.x;
        grids[1] = posGrid.y;
        return grids;
    }

    // convert x/y coordinates to lon/lat
    public static double[] gridToCoord (GridGeometry2D gg, int x, int y) throws TransformException {
        ReferencedEnvelope pixelEnvelop = gg.gridToWorld(new GridEnvelope2D(x, y, 1, 1));
        double[] coords = new double[]{pixelEnvelop.getCenterX(), pixelEnvelop.getCenterY()};
        System.out.println(coords[0]); //longitude supplied first
        System.out.println(coords[1]);
        return coords;
    }
}
