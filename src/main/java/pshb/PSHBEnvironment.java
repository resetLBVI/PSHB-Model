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

        
    }
    Path currentRelativePath = Paths.get("");
    String projectPath = currentRelativePath.toAbsolutePath().toString();
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
//    public ObjectGrid2D vegCellGrid;
    int displayWidth = 100;
    int displayHeight = 100;
    //transformation
    public double xllcornerTemp = -176101.065660700784 + 1500;
    public double yllcornerTemp = -605553.288401709870 + 1500;
    public int tempCellSize = 3000;
    public int nRowsTemp = 172;
    //Coordination Converter: The following four variables are used in manually converting coordinates into grid system. Just in case the crs automation conversion not works
    double xllcornerVeg = -194862 + 15;
    double yllcornerVeg = -695325 + 15;
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

        try {
            // (1) create debugFile
            String[] debugHeader = {};
            debugFile = OutputWriter.getFileName(this.debugFile, false);
            this.debugWriter = new OutputWriter(debugFile);
            this.debugWriter.createFile(debugHeader);
            // (2) create a log file
            String[] logHeader = {"currentStep", "currentWeek", "currentYear", "agentID", "Stage", "currentAge",
                    "longitude", "latitude", "patchID", "actionExecuted"};
            String logFile = OutputWriter.getFileName(this.logFile, false);
            this.logWriter = new OutputWriter(logFile);
            this.logWriter.createFile(logHeader);
            // (3) create agentSummaryFile
            String[] agentSummaryHeader = {"step", "agentID", "birthday", "date of death", "lon at birth",
                    "lat at birth", "lon at death", "lat at death", "death stage", "death age"}; //currently collect 10 data
            String agentSummaryFile = OutputWriter.getFileName(this.agentSummaryFile, false);
            this.agentSummaryWriter = new OutputWriter(agentSummaryFile);
            this.agentSummaryWriter.createFile(agentSummaryHeader);
            // (4) create populationSummaryFile
            String[] popSummaryHeader = {"year", "POP size", "Num of Births", "Num of Deaths", "Num Deaths in DEV/PREOVI",
                    "Num Deaths in DISP", "Num Deaths in COL"}; //currently collect 7 data
            String popSummaryFile = OutputWriter.getFileName(this.popSummaryFile, false);
            this.popSummaryWriter = new OutputWriter(popSummaryFile);
            this.popSummaryWriter.createFile(popSummaryHeader);
            // (5) create impactFile
            String[] impactDataHeader = {"year", "week", "deadVegetation", "x", "y", "patchID"}; //currently collect 6 data
            String impactFile = OutputWriter.getFileName(this.impactFile, false);
            this.impactWriter = new OutputWriter(impactFile);
            this.impactWriter.createFile(impactDataHeader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //(6) import weekly temperature maps
        // (6-1) init the lazy service (keeps no rasters in memory)
        tempService = new WeeklyTempService(52);

        // (6-2) set initial week metadata cache (agents can read these fields)
        try {
            weekCRS = tempService.getCRS(currentWeek +1);
            weekGG  = tempService.getGridGeometry(currentWeek +1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init WeeklyTempService metadata", e);
        }
        for (Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("tiff"); it.hasNext(); ) {
            var r = it.next();
            System.out.println(" - " + r.getClass().getName());
        }

        try {
            // (7) import vegetation maps
            importTiffVegRasterMaps();
            //(8) Initiate other fields
            this.agentDisplayGrid = new SparseGrid2D(displayWidth, displayHeight);
            this.agentDevelopGrid = new SparseGrid2D(tempService.getWidth(1), tempService.getHeight(1));
            this.agentColonizedGrid = new SparseGrid2D(vegHost.getWidth(), vegHost.getHeight());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //(9) initiate timer just to update the time
        PSHBTimer systemTimer = new PSHBTimer();
        schedule.scheduleRepeating(Schedule.EPOCH, 0, systemTimer);
        //(10)make agents
        try {
            makeAgentsInSpace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //(11)initiate observer
        PSHBObserver observer = new PSHBObserver();
        schedule.scheduleRepeating(Schedule.EPOCH, 2, observer);
        System.out.println("--------------END of the Start Step----------------");
    }


    public void importTiffVegRasterMaps() throws Exception {
        String hostPrFileName = OutputWriter.getFileName("RESET_PSHB_inputData/VegRaster_PrHost_20240730.tif", true).replace("%20", " ");
        String reprPrFileName = OutputWriter.getFileName("RESET_PSHB_inputData/VegRaster_PrRepr_20240730.tif", true).replace("%20", " ");

        File tiffVegRaster_PrHost = new File(hostPrFileName);
        File tiffVegRaster_PrRepr = new File(reprPrFileName);

        System.out.println("Trying to read: " + tiffVegRaster_PrHost.getAbsolutePath());
        System.out.println("Exists? " + tiffVegRaster_PrHost.exists());
        System.out.println("Can read? " + tiffVegRaster_PrHost.canRead());

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

    @Override
    public void finish() {
        super.finish();
        if (tempService != null) {
            try { tempService.close(); } catch (Exception ignored) {}
        }
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
//            int tempX = CoordinateConverter.longitudeXToGridX(inputCoordX, xllcornerTemp, tempCellSize); //the x location on the temp map
//            int tempY = CoordinateConverter.latitudeYToGridY(inputCoordY, yllcornerTemp, tempCellSize, nRowsTemp); //the y location on the temp map
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
        if(hostRasterValue > 100000) { //not a patch
            return patchID = 0;
        } else { //it's a patch, return the patchID
            patchID = (int) hostRasterValue;
            return patchID;
        }
    }
    //Get probability of hosting a cell; update 2026-02-03
    public double getVegMapPrHost(PSHBEnvironment state, double coordX, double coordY) throws TransformException, IOException {
        String vegAttributeInfo = OutputWriter.getFileName("RESET_PSHB_inputData/RESET_merge_attributes.csv", true);
        VegAttributesLoader attributesLoader = new VegAttributesLoader(vegAttributeInfo); //initiate a new VegAttributeLoader class
        Map<Integer, VegAttributes> vegInfo = attributesLoader.getVegInformation(); //get all vegetation attributes information
        double hostRasterValue = 0; //read the raster value from the veg tiff map
        double hostProb = 0;
        int posGridX; //grid x in veg map
        int posGridY; //grid y in veg map
        try {
            posGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggHost, coordX, coordY)[0]; //convert lon to gridx
            posGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggHost, coordX, coordY)[1]; //convert lat to gridy
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        hostRasterValue = vegHost.valueAtGrid(posGridX, posGridY); //this value either represents a value of probability (outside a patch) or a patchID
        if(hostRasterValue > 100000) { //means this cell is not a patch
            hostProb = (hostRasterValue - 100000) / 100 ;
        } else { //this cell is in a patch
            int mapCode = vegInfo.get(hostRasterValue).mapCode;
            double pWillowSum = vegInfo.get(hostRasterValue).pTrWillow + vegInfo.get(hostRasterValue).pShWillowM;
            if(mapCode <= 217 && mapCode >= 111) { //the dominant vegetation is the host species
                hostProb = 1.0;
            }  else if (pWillowSum > 0){
                hostProb = -4.5 + pWillowSum * 15.25;
            } else if (pWillowSum == 0) {
                hostProb = 0;
            }
        }
        return hostProb;
    }
    //update: 2026-02-03
    public double getVegMapPrRepr(PSHBEnvironment state, double coordX, double coordY) throws TransformException, IOException {
        String vegAttributeInfo = OutputWriter.getFileName("RESET_PSHB_inputData/RESET_merge_attributes.csv", true);
        VegAttributesLoader attributesLoader = new VegAttributesLoader(vegAttributeInfo); //initiate a new VegAttributeLoader class
        Map<Integer, VegAttributes> vegInfo = attributesLoader.getVegInformation(); //get all vegetation attributes information
        double pixelValue = 0;
        double reprProb = 0;
        int posGridX;
        int posGridY;
        try {
            posGridX = CoordinateConverter.coordToGrid(state.crsPrRepr, state.ggRepr, coordX, coordY)[0]; //convert lon to gridx
            posGridY = CoordinateConverter.coordToGrid(state.crsPrRepr, state.ggRepr, coordX, coordY)[1]; //convert lat to gridy
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        pixelValue = vegRepr.valueAtGrid(posGridX, posGridY);
        if(pixelValue > 100000) { //this cell is not in a patch
            reprProb = (pixelValue - 100000) / 100 ;
        } else { //this cell is in a patch
            int mapCode = vegInfo.get(pixelValue).mapCode;
            double pWillowSum = vegInfo.get(pixelValue).pTrWillow + vegInfo.get(pixelValue).pShWillowM;
            if(mapCode == 217 || mapCode == 215) { //the dominant vegetation is the host species
                reprProb = 0.0;
            }  else if (mapCode < 217 && mapCode >= 111 && mapCode != 215){
                reprProb = 1.0;
            } else if (pWillowSum > 0){
                reprProb = 1.0;
            } else if (pWillowSum == 0) {
                reprProb = 0;
            }
        }
        return reprProb;
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
