package pshb;

import org.locationtech.jts.geom.Envelope;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridGeometry2D;
import pshb.Utils.*;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.grid.SparseGrid2D;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.IOException;
import java.util.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PSHBEnvironment extends SimState {
    static {
        // Set before anything loads GeoTools
        System.setProperty("org.geotools.referencing.forceXY", "true");
        System.setProperty("org.geotools.referencing.factory.epsg", "true");
        System.setProperty("com.sun.media.imageio.disableCodecLib", "true");
        System.setProperty("org.geotools.coverage.grid.io.imageio.MaskOverviewProvider.spi.disable", "true");
        System.setProperty("org.geotools.coverage.grid.io.imageio.mask.overviews.enabled", "false");

        // Cap the JAI/ImageN tile cache to 256 MB so raster tiles don't fill the heap.
        // The raster reads in LazyVegGeoTiff and WeeklyTempService accumulate tiles
        // across the entire 23k-row vegetation grid; without a cap the cache grows unbounded.
        long tileCacheBytes = 256L * 1024 * 1024;
        capTileCache("javax.media.jai.JAI", tileCacheBytes);       // legacy JAI path
        capTileCache("org.eclipse.imagen.JAI", tileCacheBytes);    // Eclipse ImageN path (GeoTools 34+)
    }

    private static void capTileCache(String jaiClassName, long bytes) {
        try {
            Class<?> jai   = Class.forName(jaiClassName);
            Object instance = jai.getMethod("getDefaultInstance").invoke(null);
            Object cache    = jai.getMethod("getTileCache").invoke(instance);
            cache.getClass().getMethod("setMemoryCapacity", long.class).invoke(cache, bytes);
            System.out.printf("Tile cache capped at %d MB via %s%n", bytes / (1024 * 1024), jaiClassName);
        } catch (Exception ignored) {
            // This JAI variant is not on the classpath — that's fine.
        }
    }
    Path currentRelativePath = Paths.get("");
    String projectPath = currentRelativePath.toAbsolutePath().toString();
    // Master switch for verbose debug logging (debug.txt) and hot-path console prints.
    // Keep false for normal/performance runs; flip to true to bring all debug output back.
    public static final boolean DEBUG = false;
    //input and output files path
    public String debugFile = "RESET_PSHB_debug.txt";
    public String logFile = "logPSHBWeekly.csv";
    public String agentSummaryFile = "RESET_PSHB_agentSummary.csv";
    public String popSummaryFile = "RESET_PSHB_popSummary.csv";
    public String impactFile = "RESET_PSHB_impact.csv";
    OutputWriter debugWriter;
    OutputWriter logWriter;
    OutputWriter agentSummaryWriter;
    OutputWriter popSummaryWriter;
    OutputWriter impactWriter;

    // --- temperature service ---
    public WeeklyTempService tempService;
    // (optional) cache per-week metadata so agents don’t call getters repeatedly
    public org.geotools.api.referencing.crs.CoordinateReferenceSystem weekCRS;
    public org.geotools.coverage.grid.GridGeometry2D             weekGG;

    //veg maps
    CoordinateReferenceSystem crsPrHost;
    CoordinateReferenceSystem crsPrRepr;
    GridGeometry2D ggHost;
    GridGeometry2D ggRepr;
    LazyVegGeoTiff vegHost; //primary Host raster
    LazyVegGeoTiff vegRepr; //primary reproduction raster
    //SparseGrid2D can hold multiple objects per location
    public SparseGrid2D agentDevelopGrid; //this raster map is for agent's development, which is based on the temperature maps
    public SparseGrid2D agentColonizedGrid; // this raster map is for agent's colonization and reproduction, which is based on the vegetation maps
    public SparseGrid2D agentDisplayGrid;
    // ------------------------------------------------------------------------------------
    // Vegetation attributes
    // ------------------------------------------------------------------------------------
    public Map<Integer, VegAttributes> vegInfo;
//    public ObjectGrid2D vegCellGrid;
    int displayWidth = 100;
    int displayHeight = 100;
    //transformation
    public double xllcornerTemp = -176101.065660700784;
    public double yllcornerTemp = -605553.288401709870;
    public int tempCellSize = 3000;
    public int nRowsTemp = 172;
    //Coordination Converter: The following four variables are used in manually converting coordinates into grid system. Just in case the crs automation conversion not works
    double xllcornerVeg = -194862;
    double yllcornerVeg = -695325;
    int vegCellSize = 30;
    int nRowsVeg = 23518;


    //Agent state variables
    public int pshbAgentID = 0;
    //mortality
    public double mpPshbMortLarva = 0.01;
    public double mpPshbMortPreovi = 0.01;
    public double mpPshbMortAdultDisp = 0.01;
    public double mpPshbMortAdultCol = 0.01;
    //mating
    public double mpProbMate = 0.65; //the prob of being mated. Please see ODD 7.1 for details.
    //movement
    public int mpPshbMove = 2300; //mean of PSHB agent dispersal kernel, in meter
    public int mpPshbDirStdDev = 30; //SD of normal distribution of next move direction, in degree
    public double mpPshbShouldIStay = 0.5; //stay in the same cell if the vegetation cell is not dead
    //colonization
    public Map<String, PSHBVegCell> vegMapCell = new HashMap<>(); // create a vegMapCell to contain the agents in the cell. The map key is the x-y location of the cell
    //reproduction
    public int mpPshbSpawn = 5; //the mean of a Poisson distribution from which the number of agents spawned is drawn
    //data collection
    public boolean mpPshbWeeklyOutput = false; //when false, collect the data annually, otherwise, collect the data weekly
    public boolean mpWeeklyLog = false; //when false, skip writing logPSHBWeekly.csv entirely (saves disk and memory)
    //Scheduling
    int currentYear = 0; //simulation period is 35 years from 0-34;
    int currentWeek = 0; //the week is from 0-51 in the current year
    //Population summary data
    int populationSize = 0;
    int numBirth = 0; //number of birth
    int numDeath = 0; //number of death
    int numDeathInLARVA = 0; //number of death in larva and Preovi
    int numDeathInADULTDISP = 0; //number of death in dispersal
    int numDeathInADULTCOL = 0; //number of death in colonization

    public PSHBEnvironment(long seed) {
        super(seed);
    }

    public void start() {
        super.start();

        initWriters();

        // 1) Temperature service + grids (CREATE ONCE, DO NOT RECREATE LATER)
        initTemperatureServiceAndGrids();

        // 2) Vegetation rasters + colonization grid
        try {
            importTiffVegRasterMaps();
            this.agentColonizedGrid = new SparseGrid2D(vegHost.getWidth(), vegHost.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) Display grid
        this.agentDisplayGrid = new SparseGrid2D(displayWidth, displayHeight);
        // 3) Read vegetation attributes
        readVegAttributes();

        // 4) Schedule timer (updates week/year + maybe rolls temp week)
        PSHBTimer systemTimer = new PSHBTimer();
        schedule.scheduleRepeating(Schedule.EPOCH, 0, systemTimer);
        // 5) Make initial agents
        try {
            makeAgentsInSpace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 6) Observer
        PSHBObserver observer = new PSHBObserver();
        schedule.scheduleRepeating(Schedule.EPOCH, 2, observer);

        System.out.println("--------------END of the Start Step----------------");
    }

    @Override
    public void finish() {
        super.finish();
        if (tempService != null) {
            try { tempService.close(); } catch (Exception ignored) {}
        }
        if (debugWriter      != null) debugWriter.close();
        if (logWriter        != null) logWriter.close();
        if (agentSummaryWriter != null) agentSummaryWriter.close();
        if (popSummaryWriter != null) popSummaryWriter.close();
        if (impactWriter     != null) impactWriter.close();
    }

    /**
     * Write a line to the debug log, but only when DEBUG logging is enabled.
     * When DEBUG is false the debugWriter is never created, so this is a no-op
     * and avoids any per-agent / per-step file I/O.
     */
    public void debugLog(String text) {
        if (DEBUG && debugWriter != null) {
            debugWriter.addToFile(text);
        }
    }

    // ------------------------------------------------------------------------------------
    // Initialization helpers
    // ------------------------------------------------------------------------------------
    private void initWriters() {
        try {
            // debug (only created when DEBUG logging is enabled)
            if (DEBUG) {
                String[] debugHeader = {};
                debugFile = OutputWriter.getFileName(this.debugFile, false);
                debugWriter = new OutputWriter(debugFile);
                debugWriter.createFile(debugHeader);
            }

            // log (only created when mpWeeklyLog is enabled)
            if (mpWeeklyLog) {
                String[] logHeader = {"currentStep", "currentWeek", "currentYear", "agentID", "Stage", "currentAge",
                        "longitude", "latitude", "tempGridX", "tempGridY", "vegGridX", "vegGridY", "patchID", "actionExecuted"};
                String logPath = OutputWriter.getFileName(this.logFile, false);
                logWriter = new OutputWriter(logPath);
                logWriter.createFile(logHeader);
            }

            // agent summary
            String[] agentSummaryHeader = {"step", "agentID", "birthday", "date of death", "lon at birth",
                    "lat at birth", "lon at death", "lat at death", "death stage", "death age"};
            String agentSummaryPath = OutputWriter.getFileName(this.agentSummaryFile, false);
            agentSummaryWriter = new OutputWriter(agentSummaryPath);
            agentSummaryWriter.createFile(agentSummaryHeader);

            // population summary
            String[] popSummaryHeader = {"year", "POP size", "Num of Births", "Num of Deaths",
                    "Num Deaths in DEV/PREOVI", "Num Deaths in DISP", "Num Deaths in COL"};
            String popSummaryPath = OutputWriter.getFileName(this.popSummaryFile, false);
            popSummaryWriter = new OutputWriter(popSummaryPath);
            popSummaryWriter.createFile(popSummaryHeader);

            // impact
            String[] impactHeader = {"year", "week", "deadVegetation", "vegGridX", "vegGridY", "patchID"};
            String impactPath = OutputWriter.getFileName(this.impactFile, false);
            impactWriter = new OutputWriter(impactPath);
            impactWriter.createFile(impactHeader);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initTemperatureServiceAndGrids() {
        // (1) Init lazy service (holds no rasters in memory)
        tempService = new WeeklyTempService(52);

        // (2) Determine raster dimensions ONCE (for any spatial grids that must match temp extent)
        final int w;
        final int h;
        try {
            // Use week 1 (or any known valid week) just to get dimensions.
            w = tempService.getWidth(1);
            h = tempService.getHeight(1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read temperature raster dimensions from WeeklyTempService", e);
        }

        // (3) Create only the grids you still need
        // Agent development grid uses the same pixel space as the temperature maps.
        agentDevelopGrid = new SparseGrid2D(w, h);

        // (4) Cache metadata for current week (agents can use these for coordinate conversion)
        refreshTemperatureWeekMetadata();

        // (5) Optional debug: list TIFF readers
        debugPrintTiffReaders();

        System.out.println("[ENV] WeeklyTempService initialized (lazy). " +
                "env=" + System.identityHashCode(this) +
                " agentDevelopGrid=" + System.identityHashCode(agentDevelopGrid) +
                " tempGrid=null");
    }

    /** Refresh week CRS + grid geometry cache for currentWeek. */
    private void refreshTemperatureWeekMetadata() {
        final int weekIndex = currentWeek + 1; // your convention: currentWeek is 0-based, GeoTIFF weeks are 1-based
        try {
            weekCRS = tempService.getCRS(weekIndex);
            weekGG  = tempService.getGridGeometry(weekIndex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache temperature metadata for week=" + weekIndex, e);
        }
    }

    private void debugPrintTiffReaders() {
        if (!DEBUG) return;
        for (Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("tiff"); it.hasNext(); ) {
            ImageReader r = it.next();
            System.out.println(" - " + r.getClass().getName());
        }
    }
    /** Call when your sim advances to a new week for temperature maps. */
    public void rollToWeekForTempMaps(int newWeek) {
        currentWeek = newWeek;
        try {
            weekCRS = tempService.getCRS(currentWeek + 1);
            weekGG  = tempService.getGridGeometry(currentWeek + 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load metadata for week " + currentWeek + 1, e);
        }
    }

    public void importTiffVegRasterMaps() throws Exception {
        String hostPrFileName = OutputWriter.getFileName("RESET_PSHB_inputData/inVegRaster_PrHost_20260429.tif", true).replace("%20", " ");
        String reprPrFileName = OutputWriter.getFileName("RESET_PSHB_inputData/inVegRaster_PrRepr_20260429.tif", true).replace("%20", " ");

        File tiffVegRaster_PrHost = new File(hostPrFileName);
        File tiffVegRaster_PrRepr = new File(reprPrFileName);

        if (DEBUG) {
            System.out.println("Trying to read: " + tiffVegRaster_PrHost.getAbsolutePath());
            System.out.println("Exists? " + tiffVegRaster_PrHost.exists());
            System.out.println("Can read? " + tiffVegRaster_PrHost.canRead());
        }

        // IMPORTANT: we do NOT call getRenderedImage().getData() anywhere.
        vegHost = new LazyVegGeoTiff(tiffVegRaster_PrHost); //primary host raster
        vegRepr = new LazyVegGeoTiff(tiffVegRaster_PrRepr); //primary reproduction raster

        // We need CRS or grid geometry for other utilities:
        this.crsPrHost = vegHost.getCRS();
        this.crsPrRepr = vegRepr.getCRS();
        ggHost = vegHost.getGridGeometry();  // we need GridGeometry2D for coordinate transformation
        ggRepr = vegRepr.getGridGeometry();
    }

    //update week and year
    public void updateWeek() {this.currentWeek = (int)(schedule.getSteps() % 52); }

    public void updateYear() {this.currentYear = (int)(schedule.getSteps() / 52); }


    // ------------------------------------------------------------------------------------
    // Vegetation attributes
    // ------------------------------------------------------------------------------------
    public void readVegAttributes() {
        String vegAttributeInfo = null;
        try {
            vegAttributeInfo = OutputWriter.getFileName("RESET_PSHB_inputData/RESET_merge_attributes_corrected.csv", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        VegAttributesLoader attributesLoader = new VegAttributesLoader(vegAttributeInfo);
        vegInfo = attributesLoader.getVegInformation();
    }




    /*
     *********************************************************************************
     *                           MAKE AGENTS IN THE SPACE
     * ********************************************************************************
     */
    public void makeAgentsInSpace() throws IOException {
        String startLocations = OutputWriter.getFileName("RESET_PSHB_inputData/PSHB_StartLocations.csv", true);
        InputDataParser parser = new InputDataParser(startLocations); //initiate a new inputDataParser class
        Map<Integer, InfoIdentifier> initialInfo = parser.getDataInformation(); //get all groupInfo
        int nInitialLocations = initialInfo.size(); // # of initial location
        for(int i=1; i<nInitialLocations; i++){
            InfoIdentifier info = initialInfo.get(i);
            double inputCoordX = info.getInputX();
            double inputCoordY = info.getInputY();
            int nAgentsAtLocation = info.getNumOfPSHBAgents();
            for(int j=0; j<nAgentsAtLocation; j++){
                PSHBAgent a = makeAgent(inputCoordX, inputCoordY, Stage.LARVA);
                a.event = schedule.scheduleRepeating(Schedule.EPOCH, 1, a);
                agentDisplayGrid.setObjectLocation(a, a.displayX, a.displayY);
                agentDevelopGrid.setObjectLocation(a, a.tempGridX, a.tempGridY);
            }
        }
    }

    public PSHBAgent makeAgent(double coordX, double coordY, Stage stage) {
        pshbAgentID++;
        PSHBAgent a = new PSHBAgent(this, coordX, coordY, pshbAgentID, stage);
        a.dateData.put("birthday", this.schedule.getSteps());
        a.locationData.put("lonAtBirth", a.longitudeX);
        a.locationData.put("latAtBirth", a.latitudeY);
        return a;
    }


    /*
     ***********************************************************************************
     *                       Get values from VegRasterMaps
     * **********************************************************************************
     */
    //Get the patch ID
    public int getPatchID(PSHBEnvironment state, int vegGridX, int vegGridY) {
        double hostRasterValue = 0;
        int patchID = 0;
        hostRasterValue = vegHost.valueAtGrid(vegGridX, vegGridY);
        if(hostRasterValue >= 100000) { //not a patch
            return patchID = 0;
        } else { //it's a patch, return the patchID
            patchID = (int) hostRasterValue;
            return patchID;
        }
    }

    public double getVegMapPrHost(PSHBEnvironment state, double coordX, double coordY)
            throws TransformException, IOException {

        int posGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggHost, coordX, coordY)[0];
        int posGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggHost, coordX, coordY)[1];

        double hostRasterValue = vegHost.valueAtGrid(posGridX, posGridY);
        if (DEBUG) System.out.println("hostRasterValue: " + hostRasterValue);
        if (hostRasterValue >= 100000) {
            double prob = (hostRasterValue - 100000) / 100.0;
            if (DEBUG) System.out.println("host prob: " + prob);
            return prob;
        } else {
            int patchID = (int) hostRasterValue;
            if (patchID != 0 && vegInfo.containsKey(patchID)) {
                int mapCode = vegInfo.get(patchID).mapCode;
                double pWillowSum = vegInfo.get(patchID).pTrWillow + vegInfo.get(patchID).pShWillowM;
//                double pWillowSum = 0.3;
                if(mapCode <= 217 && mapCode >= 111) { //debug: 2026-04-22
                    if (DEBUG) System.out.println("mpPshbVegMapPrHost:" + 1.0);
                    return 1.0;
                } else { //doesn't have major vegetation type, so check the secondary type
                    if (pWillowSum > 0) {
                        double prob = (-4.5 + pWillowSum * 15.25) / 100;
                        if (DEBUG) System.out.println("pWillowSum: " + pWillowSum);
                        if (DEBUG) System.out.println("mpPshbVegMapPrHost:" + prob);
                        return prob;
                    } else { //doesn't contain secondary veg types
                        if (DEBUG) System.out.println("mpPshbVegMapPrHost:" + 0.0);
                        return 0.0;
                    }
                }
            } else {
                //outside the study area
                return -1;
            }
        }
    }

    /**
     * This function gets the probabilities from the "VegRaster_PrRepr_20240730" to obtain the probability
     * of successfully overcoming the tree’s defenses, mpPshbColSuccess.
     * @param state
     * @param coordX
     * @param coordY
     * @return
     * @throws TransformException
     * @throws IOException
     */
    public double getVegMapPrRepr(PSHBEnvironment state, double coordX, double coordY)
            throws TransformException, IOException {

        int posGridX = CoordinateConverter.coordToGrid(state.crsPrRepr, state.ggRepr, coordX, coordY)[0];
        int posGridY = CoordinateConverter.coordToGrid(state.crsPrRepr, state.ggRepr, coordX, coordY)[1];

        double pixelValue = vegRepr.valueAtGrid(posGridX, posGridY); //reproRasterValue

        if (pixelValue >= 100000) {
            double prob = (pixelValue - 100000) / 100.0;
            if (DEBUG) System.out.println("repro prob: " + (pixelValue - 100000) / 100.0);
            return prob;
        } else {
            int patchID = (int) pixelValue;
            int mapCode = vegInfo.get(patchID).mapCode;
            double pWillowSum = vegInfo.get(patchID).pTrWillow + vegInfo.get(patchID).pShWillowM;
//            double pWillowSum = 0.3; //change back to correct fomula 2026-07-30
            //debug: 2026-04-22
            if (patchID != 0 && vegInfo.containsKey(patchID)) {
                if(mapCode <= 217 && mapCode >= 111) { //check primary veg type
                    if (mapCode == 215 || mapCode == 217) {
                        return 0.0;
                    } else {
                        return 1.0;
                    }
                } else { //doesn't have major vegetation type, so check the secondary type
                    if (pWillowSum > 0) {
                        return 1.0;
                    } else { //doesn't contain secondary veg types
                        return 0.0;
                    }
                }
            } else {
                //outside the study area
                return -1;
            }
        }
    }


    public PSHBVegCell getVegCell(int vegGridX, int vegGridY) {
        String mapKEy = String.join("-", String.valueOf(vegGridX), String.valueOf(vegGridY));
        return this.vegMapCell.get(mapKEy);
    }


    /*
     *********************************************************************************
     *                           Getters and Setters
     * ********************************************************************************
     */


    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public String getPopSummaryFile() { return popSummaryFile; }

    public void setPopSummaryFile(String popSummaryFile) {  this.popSummaryFile = popSummaryFile; }

    public String getImpactFile() { return impactFile;  }

    public void setImpactFile(String impactFile) {  this.impactFile = impactFile; }

    public double getMpPshbMortLarva() {
        return mpPshbMortLarva;
    }

    public void setMpPshbMortLarva(double mpPshbMortLarva) {
        this.mpPshbMortLarva = mpPshbMortLarva;
    }

    public double getMpPshbMortPreovi() {
        return mpPshbMortPreovi;
    }

    public void setMpPshbMortPreovi(double mpPshbMortPreovi) {
        this.mpPshbMortPreovi = mpPshbMortPreovi;
    }

    public double getMpPshbMortAdultDisp() {
        return mpPshbMortAdultDisp;
    }

    public void setMpPshbMortAdultDisp(double mpPshbMortAdultDisp) {
        this.mpPshbMortAdultDisp = mpPshbMortAdultDisp;
    }

    public double getMpPshbMortAdultCol() {
        return mpPshbMortAdultCol;
    }

    public void setMpPshbMortAdultCol(double mpPshbMortAdultCol) {
        this.mpPshbMortAdultCol = mpPshbMortAdultCol;
    }

    public double getMpProbMate() {
        return mpProbMate;
    }

    public void setMpProbMate(double mpProbMate) {
        this.mpProbMate = mpProbMate;
    }

    public int getMpPshbMove() {
        return mpPshbMove;
    }

    public void setMpPshbMove(int mpPshbMove) {
        this.mpPshbMove = mpPshbMove;
    }

    public int getMpPshbDirStdDev() {
        return mpPshbDirStdDev;
    }

    public void setMpPshbDirStdDev(int mpPshbDirStdDev) {
        this.mpPshbDirStdDev = mpPshbDirStdDev;
    }

    public boolean getMpPshbWeeklyOutput() { return mpPshbWeeklyOutput; }

    public void setMpPshbWeeklyOutput(boolean mpPshbWeeklyOutput) { this.mpPshbWeeklyOutput = mpPshbWeeklyOutput; }

    public boolean isMpWeeklyLog() { return mpWeeklyLog; }

    public void setMpWeeklyLog(boolean mpWeeklyLog) { this.mpWeeklyLog = mpWeeklyLog; }

    public double getMpPshbShouldIStay() { return mpPshbShouldIStay; }

    public void setMpPshbShouldIStay(double mpPshbShouldIStay) {
        this.mpPshbShouldIStay = mpPshbShouldIStay;
    }

    public int getMpPshbSpawn() {
        return mpPshbSpawn;
    }

    public void setMpPshbSpawn(int mpPshbSpawn) {
        this.mpPshbSpawn = mpPshbSpawn;
    }

    public boolean isMpPshbWeeklyOutput() {
        return mpPshbWeeklyOutput;
    }

}
