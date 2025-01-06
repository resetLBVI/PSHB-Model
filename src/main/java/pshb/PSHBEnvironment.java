package pshb;

import com.vividsolutions.jts.geom.Envelope;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import pshb.Utils.*;
import sim.engine.SimState;
import sim.field.geo.GeomGridField;
import sim.field.grid.DoubleGrid2D;
import sim.field.grid.SparseGrid2D;
import sim.io.geo.ArcInfoASCGridImporter;

import java.awt.image.Raster;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PSHBEnvironment extends SimState {
    Path currentRelativePath = Paths.get("");
    String projectPath = currentRelativePath.toAbsolutePath().toString();
    //input files location
//    public String inputFile = "/Users/lin1789/Desktop/RESET_PSHB_inputData/PSHB_StartLocations.csv";
    public String inputFilePath = "/Users/lin1789/Desktop/RESET_PSHB_inputData/";
    //output files location
    public String logFile = "/Users/lin1789/Desktop/test_log.txt";
    public String outputFile = "/Users/lin1789/Desktop/test_output.csv";
    public String popSummaryFile = "/Users/lin1789/Desktop/test_popSummary.csv";
    public String impactFile = "/Users/lin1789/Desktop/test_impact.csv";
    OutputWriter logWriter;
    OutputWriter outputWriter;
    OutputWriter popSummaryWriter;
    OutputWriter impactDataWriter;

    //Environment parameters are global
    GeomGridField basicGrid = new GeomGridField();
    GeomGridField[] weeklyTempGrids; // there are 52 temperature grid maps, read them into a list
    DoubleGrid2D tempGrid;
    int weekOfTemp = 52;
    public Envelope globalMBR;
    public SparseGrid2D agentDevlopGrid; //this raster map is for agent's development, which is based on the temperature maps
    SparseGrid2D agentColonizedGrid; // this raster map is for agent's colonization and reproduction, which is based on the vegetation maps
    public double xllcornerTemp = -176101.065660700784 + 1500;
    public double yllcornerTemp = -605553.288401709870 + 1500;
    public int tempCellSize = 3000;
    public int nRowsTemp = 172;
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
    int year = (int)(schedule.getSteps()/52) + 1; //simulation period is 35 years from 1-35;
    int week = (int)(schedule.getSteps() % 52); //the week is from 0-51 in the current year
    //Summary data
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
        // (1) create logFile
        String[] logHeader = {};
        this.logWriter = new OutputWriter(logFile);
        this.logWriter.createFile(logHeader);
        // (2) create outputFile
        String[] weeklyOutputHeader = {"step", "agentID", "birthday", "date of death", "lon at birth",
                "lat at birth", "lon at death", "lat at death", "death stage", "death age"}; //corrently collect 10 data
        this.outputWriter = new OutputWriter(outputFile);
        this.outputWriter.createFile(weeklyOutputHeader);
        // (3) create populationSummaryFile
        String[] popSummaryHeader = {"year", "POP size", "Num of Births", "Num of Deaths", "Num Deaths in DEV/PREOVI",
                "Num Deaths in DISP", "Num Deaths in COL"}; //currently collect 7 data
        this.popSummaryWriter = new OutputWriter(popSummaryFile);
        this.popSummaryWriter.createFile(popSummaryHeader);
        //create impactFile
        String[] impactDataHeader = {"year", "x", "y", "patchID"}; //currently collect 4 data
        this.impactDataWriter = new OutputWriter(impactFile);
        this.impactDataWriter.createFile(impactDataHeader);

        importWeeklyRasterMap();
        importTiffVegRasterMaps();
        this.agentDevlopGrid = new SparseGrid2D(this.basicGrid.getGridWidth(), this.basicGrid.getGridHeight());
        this.agentColonizedGrid = new SparseGrid2D(this.basicGrid.getGridWidth(), this.basicGrid.getGridHeight());
        makeAgentsInSpace();
        //initiate observer
        PSHBObserver observer = new PSHBObserver();
        schedule.scheduleRepeating(observer);
        System.out.println("--------------END of the Start Step----------------");
    }

    public void importWeeklyRasterMap() {
        String fileName = "TempGridReset_week_" + 1 + ".asc";
        try {
            System.out.println("reading raster map"); //import 52 maps
            File initialFile = new File(inputFilePath + fileName);
            InputStream inputStream = Files.newInputStream(initialFile.toPath());

            System.out.println("fileName = " + fileName);
            ArcInfoASCGridImporter.read(inputStream, GeomGridField.GridDataType.DOUBLE, this.basicGrid);
            this.weeklyTempGrids = new GeomGridField[weekOfTemp];
            for(int i=0; i<weekOfTemp; i++){
                this.weeklyTempGrids[i] = new GeomGridField();
                File continueFile = new File(inputFilePath + String.format("TempGridReset_week_%d.asc", i+1));
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
    public void importTiffVegRasterMaps() {
        File tiffVegRaster_PrHost = new File(inputFilePath + "VegRaster_PrHost_20240730.tif");
        File tiffVegRaster_PrRepr = new File(inputFilePath + "VegRaster_PrRepr_20240730.tif");
        try {
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

        } catch (Exception e ) {
            e.printStackTrace();
        }
    }


    /*
     *********************************************************************************
     *                           MAKE AGENTS IN THE SPACE
     * ********************************************************************************
     */
    public void makeAgentsInSpace(){
        String startLocations = inputFilePath + "PSHB_StartLocations.csv";
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
    //ADD
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
    //ADD
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
        int posGridX;
        int posGridY;
        try {
            posGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, coordX, coordY)[0]; //convert lon to gridx
            posGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, coordX, coordY)[1]; //convert lat to gridy
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        state.tiffRasterRepr.getPixel(posGridX, posGridY, reprRasterData);
        return reprRasterData[0];
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

    public String getInputFilePath() {
        return inputFilePath;
    }

    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
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
