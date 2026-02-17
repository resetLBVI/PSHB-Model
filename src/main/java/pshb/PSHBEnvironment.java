package pshb;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridGeometry2D;
import pshb.Utils.*;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.grid.DoubleGrid2D;
import sim.field.grid.SparseGrid2D;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PSHBEnvironment extends SimState {

    static {
        // Set before anything loads GeoTools
        System.setProperty("org.geotools.referencing.forceXY", "true");
        System.setProperty("org.geotools.referencing.factory.epsg", "true");
        System.setProperty("com.sun.media.imageio.disableCodecLib", "true");
        System.setProperty("org.geotools.coverage.grid.io.imageio.MaskOverviewProvider.spi.disable", "true");
        System.setProperty("org.geotools.coverage.grid.io.imageio.mask.overviews.enabled", "false");
    }

    // ------------------------------------------------------------------------------------
    // Output files
    // ------------------------------------------------------------------------------------
    public String debugFile = "RESET_PSHB_debug.txt";
    public String logFile = "logPSHBWeekly.csv";
    public String agentSummaryFile = "RESET_PSHB_agentSummary.csv";
    public String popSummaryFile = "RESET_PSHB_popSummary.csv";
    public String impactFile = "RESET_PSHB_impact.csv";

    public OutputWriter debugWriter;
    public OutputWriter logWriter;
    public OutputWriter agentSummaryWriter;
    public OutputWriter popSummaryWriter;
    public OutputWriter impactWriter;

    // ------------------------------------------------------------------------------------
    // Temperature service + UI grid
    // ------------------------------------------------------------------------------------
    public WeeklyTempService tempService;

    // cached per-week metadata
    public CoordinateReferenceSystem weekCRS;
    public GridGeometry2D weekGG;

    // UI background: temperature values
    public DoubleGrid2D tempGrid;

    // Agents live on the temperature grid for development (same dimensions as tempGrid)
    public SparseGrid2D agentDevelopGrid;

    // ------------------------------------------------------------------------------------
    // Vegetation rasters + colonization grid
    // ------------------------------------------------------------------------------------
    public CoordinateReferenceSystem crsPrHost;
    public CoordinateReferenceSystem crsPrRepr;
    public GridGeometry2D ggHost;
    public GridGeometry2D ggRepr;

    public LazyVegGeoTiff vegHost;
    public LazyVegGeoTiff vegRepr;

    // Agents live on the vegetation grid for colonization/reproduction
    public SparseGrid2D agentColonizedGrid;

    // ------------------------------------------------------------------------------------
    // Vegetation attributes
    // ------------------------------------------------------------------------------------
    public Map<Integer, VegAttributes> vegInfo;
    // ------------------------------------------------------------------------------------
    // Coordinate transformation constants (kept as-is)
    // ------------------------------------------------------------------------------------
    public double xllcornerTemp = -176101.065660700784 + 1500;
    public double yllcornerTemp = -605553.288401709870 + 1500;
    public int tempCellSize = 3000;
    public int nRowsTemp = 172;

    double xllcornerVeg = -194862 + 15;
    double yllcornerVeg = -695325 + 15;
    int vegCellSize = 30;
    int nRowsVeg = 23518;

    // ------------------------------------------------------------------------------------
    // Model parameters / state variables (kept as-is)
    // ------------------------------------------------------------------------------------
    public int pshbAgentID = 0;

    // mortality
    public double mpPshbMortLarva = 0.01;
    public double mpPshbMortPreovi = 0.01;
    public double mpPshbMortAdultDisp = 0.01;
    public double mpPshbMortAdultCol = 0.01;

    // mating
    public double mpProbMate = 0.65;

    // movement
    public int mpPshbMove = 2300;
    public int mpPshbDirStdDev = 30;
    public double mpPshbShouldIStay = 0.5;

    // colonization
    public Map<String, PSHBVegCell> vegMapCell = new HashMap<>();

    // reproduction
    public int mpPshbSpawn = 5;

    // data collection
    public boolean mpPshbWeeklyOutput = false;

    // scheduling
    public int currentYear = 0; // 0..34
    public int currentWeek = 0; // 0..51

    // population summary
    public int populationSize = 0;
    public int numBirth = 0;
    public int numDeath = 0;
    public int numDeathInLARVA = 0;
    public int numDeathInADULTDISP = 0;
    public int numDeathInADULTCOL = 0;

    // ------------------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------------------
    public PSHBEnvironment(long seed) {
        super(seed);
    }

    // ------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------
    @Override
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
    }

    // ------------------------------------------------------------------------------------
    // Initialization helpers
    // ------------------------------------------------------------------------------------
    private void initWriters() {
        try {
            // debug
            String[] debugHeader = {};
            debugFile = OutputWriter.getFileName(this.debugFile, false);
            debugWriter = new OutputWriter(debugFile);
            debugWriter.createFile(debugHeader);

            // log
            String[] logHeader = {"currentStep", "currentWeek", "currentYear", "agentID", "Stage", "currentAge",
                    "longitude", "latitude", "patchID", "actionExecuted"};
            String logPath = OutputWriter.getFileName(this.logFile, false);
            logWriter = new OutputWriter(logPath);
            logWriter.createFile(logHeader);

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
            String[] impactHeader = {"year", "week", "deadVegetation", "x", "y", "patchID"};
            String impactPath = OutputWriter.getFileName(this.impactFile, false);
            impactWriter = new OutputWriter(impactPath);
            impactWriter.createFile(impactHeader);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initTemperatureServiceAndGrids() {
        // lazy service (no rasters held in memory)
        tempService = new WeeklyTempService(52);

        final int w;
        final int h;
        try {
            w = tempService.getWidth(1);
            h = tempService.getHeight(1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read temp raster dimensions", e);
        }

        // IMPORTANT: create ONCE
        tempGrid = new DoubleGrid2D(w, h);
        agentDevelopGrid = new SparseGrid2D(w, h);

        // cache week metadata
        try {
            weekCRS = tempService.getCRS(currentWeek + 1);
            weekGG  = tempService.getGridGeometry(currentWeek + 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init WeeklyTempService metadata", e);
        }

        // optional: print TIFF readers
        for (Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("tiff"); it.hasNext(); ) {
            var r = it.next();
            System.out.println(" - " + r.getClass().getName());
        }

        // fill background grid once at start
        refreshTempGridForCurrentWeek();

        System.out.println("[ENV] env=" + System.identityHashCode(this) +
                " tempGrid=" + System.identityHashCode(tempGrid));
    }

    // ------------------------------------------------------------------------------------
    // Temperature grid refresh (UI background)
    // ------------------------------------------------------------------------------------
    public void refreshTempGridForCurrentWeek() {
        if (tempGrid == null) throw new IllegalStateException("tempGrid is null");

        final int weekIndex = currentWeek + 1; // service expects 1..52
        final int w = tempGrid.getWidth();
        final int h = tempGrid.getHeight();

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int finite = 0;
        int nan = 0;

        for (int col = 0; col < w; col++) {
            for (int row = 0; row < h; row++) {
                double v;
                try {
                    v = tempService.getTempAtGrid(weekIndex, col, row);
                } catch (Exception ex) {
                    v = Double.NaN;
                }

                tempGrid.field[col][row] = v;

                if (Double.isFinite(v)) {
                    finite++;
                    if (v < min) min = v;
                    if (v > max) max = v;
                } else {
                    nan++;
                }
            }
        }

        System.out.println("[TEMP GRID] week=" + weekIndex +
                " finite=" + finite + " nan=" + nan + " total=" + (w * h) +
                " min=" + (finite > 0 ? min : "NaN") +
                " max=" + (finite > 0 ? max : "NaN"));
    }

    /** Call when sim advances to a new week for temperature maps. */
    public void rollToWeekForTempMaps(int newWeek) {
        currentWeek = newWeek;

        try {
            weekCRS = tempService.getCRS(currentWeek + 1);
            weekGG  = tempService.getGridGeometry(currentWeek + 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load metadata for week " + (currentWeek + 1), e);
        }

        // keep UI background in sync
        refreshTempGridForCurrentWeek();
    }

    public void updateWeek() { this.currentWeek = (int) (schedule.getSteps() % 52); }
    public void updateYear() { this.currentYear = (int) (schedule.getSteps() / 52); }

    // ------------------------------------------------------------------------------------
    // Vegetation import
    // ------------------------------------------------------------------------------------
    public void importTiffVegRasterMaps() throws Exception {
        String hostPrFileName = OutputWriter.getFileName(
                "RESET_PSHB_inputData/VegRaster_PrHost_20240730.tif", true).replace("%20", " ");
        String reprPrFileName = OutputWriter.getFileName(
                "RESET_PSHB_inputData/VegRaster_PrRepr_20240730.tif", true).replace("%20", " ");

        File tiffVegRaster_PrHost = new File(hostPrFileName);
        File tiffVegRaster_PrRepr = new File(reprPrFileName);

        System.out.println("Trying to read: " + tiffVegRaster_PrHost.getAbsolutePath());
        System.out.println("Exists? " + tiffVegRaster_PrHost.exists());
        System.out.println("Can read? " + tiffVegRaster_PrHost.canRead());

        vegHost = new LazyVegGeoTiff(tiffVegRaster_PrHost);
        vegRepr = new LazyVegGeoTiff(tiffVegRaster_PrRepr);

        this.crsPrHost = vegHost.getCRS();
        this.crsPrRepr = vegRepr.getCRS();
        this.ggHost = vegHost.getGridGeometry();
        this.ggRepr = vegRepr.getGridGeometry();
    }

    // ------------------------------------------------------------------------------------
    // Vegetation attributes
    // ------------------------------------------------------------------------------------
    public void readVegAttributes() {
        String vegAttributeInfo = null;
        try {
            vegAttributeInfo = OutputWriter.getFileName("RESET_PSHB_inputData/RESET_merge_attributes.csv", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        VegAttributesLoader attributesLoader = new VegAttributesLoader(vegAttributeInfo);
        vegInfo = attributesLoader.getVegInformation();
    }

    // ------------------------------------------------------------------------------------
    // Agent creation
    // ------------------------------------------------------------------------------------
    public void makeAgentsInSpace() throws IOException {
        String startLocations = OutputWriter.getFileName("RESET_PSHB_inputData/PSHB_StartLocations.csv", true);
        InputDataParser parser = new InputDataParser(startLocations);
        Map<Integer, InfoIdentifier> initialInfo = parser.getDataInformation();
        int nInitialLocations = initialInfo.size();

        for (int i = 1; i < nInitialLocations; i++) {
            InfoIdentifier info = initialInfo.get(i);
            double inputCoordX = info.getInputX();
            double inputCoordY = info.getInputY();

            int nAgentsAtLocation = info.getNumOfPSHBAgents();
            for (int j = 0; j < nAgentsAtLocation; j++) {
                PSHBAgent a = makeAgent(inputCoordX, inputCoordY, Stage.LARVA);
                a.event = schedule.scheduleRepeating(Schedule.EPOCH, 1, a);
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

    // ------------------------------------------------------------------------------------
    // Veg raster queries (kept as-is)
    // ------------------------------------------------------------------------------------
    public int getPatchID(PSHBEnvironment state, int vegGridX, int vegGridY) {
        double hostRasterValue = vegHost.valueAtGrid(vegGridX, vegGridY);
        if (hostRasterValue > 100000) return 0;
        return (int) hostRasterValue;
    }

    public double getVegMapPrHost(PSHBEnvironment state, double coordX, double coordY)
            throws TransformException, IOException {

//        String vegAttributeInfo = OutputWriter.getFileName("RESET_PSHB_inputData/RESET_merge_attributes.csv", true);
//        VegAttributesLoader attributesLoader = new VegAttributesLoader(vegAttributeInfo);
//        Map<Integer, VegAttributes> vegInfo = attributesLoader.getVegInformation();

        int posGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggHost, coordX, coordY)[0];
        int posGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggHost, coordX, coordY)[1];

        double hostRasterValue = vegHost.valueAtGrid(posGridX, posGridY);

        if (hostRasterValue > 100000) {
            return (hostRasterValue - 100000) / 100.0;
        } else {
            int patchID = (int) hostRasterValue;
            int mapCode = vegInfo.get(patchID).mapCode;
            double pWillowSum = vegInfo.get(patchID).pTrWillow + vegInfo.get(patchID).pShWillowM;

            if (mapCode <= 217 && mapCode >= 111) return 1.0;
            if (pWillowSum > 0) return -4.5 + pWillowSum * 15.25;
            return 0.0;
        }
    }

    public double getVegMapPrRepr(PSHBEnvironment state, double coordX, double coordY)
            throws TransformException, IOException {

//        String vegAttributeInfo = OutputWriter.getFileName("RESET_PSHB_inputData/RESET_merge_attributes.csv", true);
//        VegAttributesLoader attributesLoader = new VegAttributesLoader(vegAttributeInfo);
//        Map<Integer, VegAttributes> vegInfo = attributesLoader.getVegInformation();

        int posGridX = CoordinateConverter.coordToGrid(state.crsPrRepr, state.ggRepr, coordX, coordY)[0];
        int posGridY = CoordinateConverter.coordToGrid(state.crsPrRepr, state.ggRepr, coordX, coordY)[1];

        double pixelValue = vegRepr.valueAtGrid(posGridX, posGridY);

        if (pixelValue > 100000) {
            return (pixelValue - 100000) / 100.0;
        } else {
            int patchID = (int) pixelValue;
            int mapCode = vegInfo.get(patchID).mapCode;
            double pWillowSum = vegInfo.get(patchID).pTrWillow + vegInfo.get(patchID).pShWillowM;

            if (mapCode == 217 || mapCode == 215) return 0.0;
            if (mapCode < 217 && mapCode >= 111 && mapCode != 215) return 1.0;
            if (pWillowSum > 0) return 1.0;
            return 0.0;
        }
    }

    public PSHBVegCell getVegCell(int vegGridX, int vegGridY) {
        String key = vegGridX + "-" + vegGridY;
        return vegMapCell.get(key);
    }

    // ------------------------------------------------------------------------------------
    // Getters/Setters (left as-is; keep your existing ones)
    // ------------------------------------------------------------------------------------
    public String getLogFile() { return logFile; }
    public void setLogFile(String logFile) { this.logFile = logFile; }

    public String getPopSummaryFile() { return popSummaryFile; }
    public void setPopSummaryFile(String popSummaryFile) { this.popSummaryFile = popSummaryFile; }

    public String getImpactFile() { return impactFile; }
    public void setImpactFile(String impactFile) { this.impactFile = impactFile; }

    public double getMpPshbMortLarva() { return mpPshbMortLarva; }
    public void setMpPshbMortLarva(double v) { this.mpPshbMortLarva = v; }

    public double getMpPshbMortPreovi() { return mpPshbMortPreovi; }
    public void setMpPshbMortPreovi(double v) { this.mpPshbMortPreovi = v; }

    public double getMpPshbMortAdultDisp() { return mpPshbMortAdultDisp; }
    public void setMpPshbMortAdultDisp(double v) { this.mpPshbMortAdultDisp = v; }

    public double getMpPshbMortAdultCol() { return mpPshbMortAdultCol; }
    public void setMpPshbMortAdultCol(double v) { this.mpPshbMortAdultCol = v; }

    public double getMpProbMate() { return mpProbMate; }
    public void setMpProbMate(double v) { this.mpProbMate = v; }

    public int getMpPshbMove() { return mpPshbMove; }
    public void setMpPshbMove(int v) { this.mpPshbMove = v; }

    public int getMpPshbDirStdDev() { return mpPshbDirStdDev; }
    public void setMpPshbDirStdDev(int v) { this.mpPshbDirStdDev = v; }

    public boolean getMpPshbWeeklyOutput() { return mpPshbWeeklyOutput; }

    public void setMpPshbWeeklyOutput(boolean mpPshbWeeklyOutput) { this.mpPshbWeeklyOutput = mpPshbWeeklyOutput; }

    public double getMpPshbShouldIStay() { return mpPshbShouldIStay; }

    public void setMpPshbShouldIStay(double mpPshbShouldIStay) {
        this.mpPshbShouldIStay = mpPshbShouldIStay;
    }

    public int getMpPshbSpawn() { return mpPshbSpawn; }
    public void setMpPshbSpawn(int v) { this.mpPshbSpawn = v; }

    public boolean isMpPshbWeeklyOutput() { return mpPshbWeeklyOutput; }
}
