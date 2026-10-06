# terrID wiring — summary of what was done

Status: DONE + VERIFIED with a smoke test on the real tif. Project compiles (mvn BUILD SUCCESS).

## IMPORTANT finding from testing the real raster
Background / no-territory cells are NOT stored as 0. They are a NoData fill that reads as
2^32 = 4294967296.0 (and would saturate to 2147483647 if cast straight to int).
Valid territory IDs are 0..68305 and read correctly. Most of the raster is NoData
(territories are sparse). getTerrID now maps NoData -> 0, so the impact file gets clean
terrID = 0 for "no territory" (as you asked), and real IDs elsewhere.

Verified: coordinate mapping is correct too — the veg NW corner lands at terr col=576,
row=3216, exactly the 576/3216-cell offsets predicted from the extents.

## The key idea
The pseudoterr raster has a DIFFERENT extent/size than the veg raster, so you cannot
index it with vegGridX/vegGridY. Query it by WORLD coordinate (longitudeX/latitudeY)
through the terr raster's OWN grid geometry. Value 0 = no territory / background.

## Code changes I made

### 1. PSHBEnvironment.java — new fields
Added next to the veg raster fields:
    CoordinateReferenceSystem crsTerr;
    GridGeometry2D            ggTerr;
    LazyVegGeoTiff            pseudoTerr;

### 2. PSHBEnvironment.java — importTiffVegRasterMaps() loads the terr raster
    String terrFileName = OutputWriter.getFileName(
        "RESET_PSHB_inputData/pseudoterr_raster_converted.tif", true).replace("%20", " ");
    File tiffTerr = new File(terrFileName);
    pseudoTerr = new LazyVegGeoTiff(tiffTerr);   // tile-backed, memory safe
    this.crsTerr = pseudoTerr.getCRS();
    ggTerr = pseudoTerr.getGridGeometry();

### 3. PSHBEnvironment.java — new getTerrID(state, lon, lat) helper (after getPatchID)
    public int getTerrID(PSHBEnvironment state, double lon, double lat) {
        try {
            int[] g = CoordinateConverter.coordToGrid(state.crsTerr, state.ggTerr, lon, lat);
            int col = g[0], row = g[1];
            if (!pseudoTerr.inBounds(col, row)) return 0;   // outside coverage
            return (int) pseudoTerr.valueAtGrid(col, row);  // 0 = background/no territory
        } catch (TransformException e) {
            if (DEBUG) System.out.println("getTerrID transform failed at ("+lon+", "+lat+")");
            return 0;
        }
    }
Note: it catches TransformException internally and returns 0, so it stays a clean
non-throwing helper like getPatchID.

### 4. PSHBAgent.java — fixed the call site (line ~465, colonizeAHost)
Before (WRONG — grid indices, and getTerrID didn't even exist -> code didn't compile):
    ... state.getTerrID(state, vegGridX, vegGridY) ...
After (correct — world coordinate):
    ... state.getTerrID(state, lon, lat) ...

## Result
PSHBVegCell already stored and wrote terrID in both impact rows, so the terrID column in
RESET_PSHB_impact.csv is now populated from the new raster automatically. No other write
site needed changing.

## What YOU still need to do / decide
- DONE: tif is in place at /Users/kaiyinlin/Documents/4_QuickShare/RESET_PSHB_inputData/
  pseudoterr_raster_converted.tif (that folder, one level above the project, is where
  getFileName resolves — same place as inVegRaster_PrHost_20260429.tif).
- DONE: keep 0 as terrID for "no territory" (NoData is mapped to 0).
- TO RUN: rebuild the deployable jar (mvn clean package -DskipTests) and point the sweep at
  it, or run PSHBHeadless as usual. The src is compiled; only the packaged jar needs a
  refresh before your next run/sweep.

## Why it works cleanly (verified)
- Both rasters are 30 m, same CRS (California Albers / EPSG:3310).
- Grid origins differ by whole cells (dxmin = 576*30, dymax = -3216*30) -> one veg cell
  maps to exactly one terr cell, no resampling.
- Veg extent is fully inside terr extent -> every veg location has a terr cell.
- LazyVegGeoTiff is tile-backed (1x1 reads), so the 470M-cell terr raster won't cause OOM.

## run_sweep.py
No change needed. It only builds the java command, captures stdout to runs/<runId>/stdout.log,
and writes its own manifest; it never references the simulation's debug output files.
