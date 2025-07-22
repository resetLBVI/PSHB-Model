package pshb;

import com.vividsolutions.jts.geom.Envelope;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import pshb.Utils.*;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.geo.GeomGridField;
import sim.field.grid.DoubleGrid2D;
import sim.field.grid.SparseGrid2D;
import sim.io.geo.ArcInfoASCGridImporter;

import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PSHBEnvironment extends SimState {
    Path currentRelativePath = Paths.get("");
    String projectPath = currentRelativePath.toAbsolutePath().toString();
    //input and output files path
//    public String inputFilePath = "/Users/lin1789/Desktop/RESET_PSHB_inputData/";
//    public String logFile = "/Users/lin1789/Desktop/test_log.txt";
//    public String outputFile = "/Users/lin1789/Desktop/test_output.csv";
//    public String popSummaryFile = "/Users/lin1789/Desktop/test_popSummary.csv";
//    public String impactFile = "/Users/lin1789/Desktop/test_impact.csv";
    public String debugFile = "RESET_PSHB_debug.txt";
    public String logFile = "RESET_PSHB_log.csv";
    public String agentSummaryFile = "RESET_PSHB_agentSummary.csv";
    public String popSummaryFile = "RESET_PSHB_popSummary.csv";
    public String impactFile = "RESET_PSHB_impact.csv";
    OutputWriter debugWriter;
    OutputWriter logWriter;
    OutputWriter agentSummaryWriter;
    OutputWriter popSummaryWriter;
    OutputWriter impactWriter;

    //Environment parameters are global
    GeomGridField basicGrid = new GeomGridField(); //this is a basic grid shown in the UI (unnecessary)
    GeomGridField[] weeklyTempGrids; // there are 52 temperature grid maps, read them into a list
    DoubleGrid2D tempGrid; //current temperature grid
    int weekOfTemp = 52; //there are 52 temperature maps
    public Envelope globalMBR;
    public SparseGrid2D agentDevlopGrid; //this raster map is for agent's development, which is based on the temperature maps
    SparseGrid2D agentColonizedGrid; // this raster map is for agent's colonization and reproduction, which is based on the vegetation maps
    public double xllcornerTemp = -176101.065660700784 + 1500;
    public double yllcornerTemp = -605553.288401709870 + 1500;
    public int tempCellSize = 3000;
    public int nRowsTemp = 172;
    //Coordination Converter: The following four variables are used in manually converting coordinates into grid system. Just in case the crs automation conversion not works
    double xllcornerVeg = -194862 + 15;
    double yllcornerVeg = -695325 + 15;
    int vegCellSize = 30;
    int nRowsVeg = 23518;
    //veg maps
    CoordinateReferenceSystem crsPrHost;
    CoordinateReferenceSystem crsPrRepr;
    GridGeometry2D ggPrHost;
    GridGeometry2D ggPrRepr;
    Raster tiffRasterHost;
    Raster tiffRasterRepr;

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
            String[] weeklyAgentSummaryHeader = {"step", "agentID", "birthday", "date of death", "lon at birth",
                    "lat at birth", "lon at death", "lat at death", "death stage", "death age"}; //currently collect 10 data
            String agentSummaryFile = OutputWriter.getFileName(this.agentSummaryFile, false);
            this.agentSummaryWriter = new OutputWriter(agentSummaryFile);
            this.agentSummaryWriter.createFile(weeklyAgentSummaryHeader);
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
            //(6) import weekly temperature maps
            importWeeklyRasterMap();
            // (7) import vegetation maps
            importTiffVegRasterMaps();
            //(8) Initiate other fields
            this.agentDevlopGrid = new SparseGrid2D(this.basicGrid.getGridWidth(), this.basicGrid.getGridHeight());
            this.agentColonizedGrid = new SparseGrid2D(this.basicGrid.getGridWidth(), this.basicGrid.getGridHeight());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //(9) initiate teimer just to update the time
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
        schedule.scheduleRepeating(observer);
        System.out.println("--------------END of the Start Step----------------");
    }

    public void importWeeklyRasterMap() {
        try {
            String basicGridFileName = OutputWriter.getFileName("/RESET_PSHB_inputData/TempGridReset_week_1.asc", true);
            System.out.println("reading raster map"); //import 52 maps
            File initialFile = new File(basicGridFileName);
            InputStream inputStream = Files.newInputStream(initialFile.toPath());

            System.out.println("fileName = " + basicGridFileName);
            ArcInfoASCGridImporter.read(inputStream, GeomGridField.GridDataType.DOUBLE, this.basicGrid);
            this.weeklyTempGrids = new GeomGridField[weekOfTemp];
            for(int i=0; i<weekOfTemp; i++){
                this.weeklyTempGrids[i] = new GeomGridField();
                String weeklyTempFileName = OutputWriter.getFileName("/RESET_PSHB_inputData/" + String.format("TempGridReset_week_%d.asc", i+1), true);
                File continueFile = new File(weeklyTempFileName);
                inputStream = Files.newInputStream(continueFile.toPath());
                System.out.println("fileName = " + String.format("TempGridReset_week_%d.asc", i+1));
                ArcInfoASCGridImporter.read(inputStream, GeomGridField.GridDataType.DOUBLE, this.weeklyTempGrids[i]);
            }
            this.tempGrid = (DoubleGrid2D) this.weeklyTempGrids[0].getGrid();
//            this.tempGrid = (SparseGrid2D) this.weeklyTempGrids[0].getGrid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        globalMBR = this.basicGrid.getMBR();
        this.basicGrid.setMBR(globalMBR);
    }

    //ADD
    public void importTiffVegRasterMaps() throws IOException {
        String hostPrFileName = OutputWriter.getFileName("RESET_PSHB_inputData/VegRaster_PrHost_20240730.tif", true);
        String reprPrFileName = OutputWriter.getFileName("RESET_PSHB_inputData/VegRaster_PrRepr_20240730.tif", true);
        File tiffVegRaster_PrHost = new File(hostPrFileName);
        File tiffVegRaster_PrRepr = new File(reprPrFileName);

        GeoTiffReader reader_PrHost = new GeoTiffReader(tiffVegRaster_PrHost);
        GeoTiffReader reader_PrRepr = new GeoTiffReader(tiffVegRaster_PrRepr);
        GridCoverage2D covPrHost = reader_PrHost.read(null);
        GridCoverage2D covPrRepr = reader_PrRepr.read(null);
        tiffRasterHost = covPrHost.getRenderedImage().getData();
        tiffRasterRepr = covPrRepr.getRenderedImage().getData();
        // get x, y bounds
        System.out.println("Raster Host bounds = " + tiffRasterHost.getBounds());
        System.out.println("Raster Repr bounds = " + tiffRasterRepr.getBounds());
        // get lon, lat bounds (longitude supplied first)
        System.out.println(covPrHost.getEnvelope());
        System.out.println(covPrRepr.getEnvelope());
        //making use of the coordinate reference system
        crsPrHost = covPrHost.getCoordinateReferenceSystem2D();
        crsPrRepr = covPrRepr.getCoordinateReferenceSystem2D();
        //Returns a math transform for the two dimensional part for conversion from world to grid coordinates.
        ggPrHost = covPrHost.getGridGeometry();
        ggPrRepr = covPrRepr.getGridGeometry();

    }
    //update week and year
    public void updateWeek() {this.currentWeek = (int)(schedule.getSteps() % 52); }

    public void updateYear() {this.currentYear = (int)(schedule.getSteps() / 52); }


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
            int tempX = CoordinateConverter.longitudeXToGridX(inputCoordX, xllcornerTemp, tempCellSize); //the x location on the temp map
            int tempY = CoordinateConverter.latitudeYToGridY(inputCoordY, yllcornerTemp, tempCellSize, nRowsTemp); //the y location on the temp map
            int nAgentsAtLocation = info.getNumOfPSHBAgents();
            for(int j=0; j<nAgentsAtLocation; j++){
                PSHBAgent a = makeAgent(inputCoordX, inputCoordY, Stage.LARVA);
                a.event = schedule.scheduleRepeating(a);
                agentDevlopGrid.setObjectLocation(a, tempX, tempY);
            }
        }
    }

    public PSHBAgent makeAgent(double coordX, double coordY, Stage stage) {
        pshbAgentID++;
        PSHBAgent a = new PSHBAgent(this, coordX, coordY, pshbAgentID, stage);
        System.out.println("pshbAgentID = " + a.pshbAgentID + "has started his life at step 0");
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
        int[] hostRasterData = new int[1];
        int patchID = 0;
        state.tiffRasterHost.getPixel(vegGridX, vegGridY, hostRasterData);
        if(hostRasterData[0] > 100000) { //not a patch
            return patchID = 0;
        } else { //it's a patch, return the patchID
            patchID = hostRasterData[0];
            return patchID;
        }
    }
    //Get probability of hosting a cell
    public double getVegMapPrHost(PSHBEnvironment state, double coordX, double coordY) throws TransformException {
        // sample tiff data with at pixel coordinate(get Values)
        int[] hostRasterData = new int[1];
        double hostProb = 0;
        int posGridX; //grid x in veg map
        int posGridY; //grid y in veg map
        try {
            posGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, coordX, coordY)[0]; //convert lon to gridx
            posGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, coordX, coordY)[1]; //convert lat to gridy
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        state.tiffRasterHost.getPixel(posGridX, posGridY, hostRasterData);
        if(hostRasterData[0] > 100000) {
            hostProb = (hostRasterData[0] - 100000) / 100 ;
        } else {
            hostProb = random.nextDouble(); //So far, Use a random number because we haven't had veg data
        }
        return hostProb;
    }
    //ADD
    public double getVegMapPrRepr(PSHBEnvironment state, double coordX, double coordY) throws TransformException{
        // sample tiff data with at pixel coordinate(get Values)
        double[] reprRasterData = new double[1];
        double reprProb = 0;
        int posGridX;
        int posGridY;
        try {
            posGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, coordX, coordY)[0]; //convert lon to gridx
            posGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, coordX, coordY)[1]; //convert lat to gridy
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        state.tiffRasterRepr.getPixel(posGridX, posGridY, reprRasterData);
        if(reprRasterData[0] > 100000) {
            reprProb = (reprRasterData[0] - 100000) / 100 ;
        } else {
            reprProb = random.nextDouble(); //So far, Use a random number because we haven't had veg data
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
