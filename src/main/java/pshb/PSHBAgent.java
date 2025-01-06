package pshb;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.geotools.api.referencing.operation.TransformException;
import pshb.Utils.Calculation;
import pshb.Utils.CoordinateConverter;
import pshb.Utils.Stage;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.field.grid.DoubleGrid2D;
import sim.util.Bag;

import java.util.HashMap;
import java.util.Map;

public class PSHBAgent implements Steppable {
    //agent state variables
    int pshbAgentID; //assign each agent an unique ID
    Stage pshbStage; //state an agent's current stage
    double longitudeX; //longitude ie. pshbLong
    double latitudeY; //latitude ie. pshbLat
    int tempGridX; //x location on the temperature grid map
    int tempGridY; //y location on the temperature grid map
    int vegGridX; //x location on the vegetation grid map
    int vegGridY; //y location on the vegetation grid map
    int pshbDegDaysReq; //the degree-days an agent is required for development, define in constructor
    int pshbDegDays; //the counts of the degree-days
    int pshbSonDegDays; // the counts of son's degree-days
    boolean pshbMated; //state if the agent is mated or unmated
    int pshbLongev; //state an agent's longevity in adult stages
    int pshbLongevUsed; //countdown of an agent's longevity in the adult stages
    boolean pshbDorm;
    Integer pshbAge;

    //development
    DoubleGrid2D tempGrid;
    double currentTemp;
    //movement (dispersal)
    public double pshbDispDist; // Distance moved (m)
    public double pshbDispDir; // Direction moved (degree)
    public double pshbDispDirPre; //Direction moved previous tick (degree)
    ExponentialDistribution exponentialGenerator;
    //colonization
    public double attemptingColonization; //prob of attempting colonization
    public double probBasedOnRemainingTicks; // the prob of attempting colonization first depends on the remaining ticks an agent has
    public int pshbRemainingTicks; //the remaining ticks to the end of the adult stage (i.e., to the death)
    double mpPshbVegMapPrHost; //the probability to host a tree in a specific cell (from veg Map_PrHost)
    double mpPshbColSuccess; //the probability successfully colonize a host tree (from veg Map_PrRepr)
    PSHBVegCell pshbHostCell; //an agent will claim a vegCell (a cell entity) only when they colonized a host

    //reproduction
    public int pshbSpawn; //number of eggs an agent lay

    //schedule
    Stoppable event; //schedule ot stop the event
    Long currentStep; //identify current step

    //life history data
    Map<String, Double> locationData;
    Map<String, Long> dateData;
    Map<String, Enum> stageData;
    Map<String, Integer> ageData;



    public PSHBAgent(PSHBEnvironment state, double longitudeX, double latitudeY, int pshbAgentID, Stage stage) {
        super();
        this.pshbStage = stage;
        this.pshbAgentID = pshbAgentID;
        this.longitudeX = longitudeX;
        this.latitudeY = latitudeY;
        this.pshbDegDaysReq = (int) (398 + state.random.nextGaussian() * 17); //every agent has slightly different on development
        this.pshbDegDays = 0;
        this.pshbSonDegDays = 0;
        this.tempGridX = CoordinateConverter.longitudeXToGridX(longitudeX, state.xllcornerTemp, state.tempCellSize);
        this.tempGridY = CoordinateConverter.latitudeYToGridY(latitudeY, state.yllcornerTemp, state.tempCellSize, state.nRowsTemp);
        this.vegGridX = 0; //the vegGridX is activated only when the agent colonized a cell
        this.vegGridY = 0; //the vegGridY is activated only when the agent colonized a cell
        this.pshbMated = false;
        this.pshbLongev = state.random.nextInt(3) + 1;
        this.pshbLongevUsed = 0;
        this.pshbDispDirPre = -1;
        this.pshbDispDir = -1;
        this.pshbDispDist = 0;
        this.pshbRemainingTicks = this.pshbLongev;
        this.exponentialGenerator = new ExponentialDistribution(state.mpPshbMove);
        this.pshbSpawn = Calculation.getPoisson(1/state.mpPshbSpawn);
        this.pshbAge = 0;
        this.pshbHostCell = null;
        //weekly data output
        locationData = new HashMap<>();
        dateData = new HashMap<>();
        stageData = new HashMap<>();
        ageData = new HashMap<>();
    }

    @Override
    //CHANGE
    public void step(SimState state) {
//        System.out.println("Agent " + this.pshbAgentID + "      is during stage = " + this.pshbStage);

        long startTime = System.currentTimeMillis();
        PSHBEnvironment eState = (PSHBEnvironment) state;
        currentStep = eState.schedule.getSteps();
        eState.logWriter.addToFile("========Agent " + this.pshbAgentID + " has started current step = " + currentStep +"===========");
        this.tempGrid = (DoubleGrid2D) eState.weeklyTempGrids[eState.week].getGrid(); //get current temperature map
        this.currentTemp = this.tempGrid.get(this.tempGridX, this.tempGridY);
        eState.logWriter.addToFile("current week = " + eState.week + "   Max Temp = " + this.tempGrid.max() + "   this agent's stage " + this.pshbStage);
        long endTime = System.currentTimeMillis();
//        System.out.println("Step took " + (endTime - startTime) + " milliseconds");
        
        //check dormancy before checking the stages
        if(checkDormancy(eState)){
            if(this.pshbStage == Stage.LARVA || this.pshbStage == Stage.PREOVI){
                if(state.random.nextBoolean(2* eState.mpPshbMortLarva)){
                    eState.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
                    eState.numDeathInLARVA ++;
                    eState.numDeath ++;
                    long deathStart = System.currentTimeMillis();
                    death(eState);
                    long deathEnd = System.currentTimeMillis();
                    System.out.println("Death took " + (deathEnd - deathStart) + " milliseconds");
                    return;
                }
            } else { //if this agent is under dormancy but not dead, return this step
                return;
            }
        }
        //take actions, check the stages
        takeAction(this.pshbStage, eState);
        //age increment
        this.pshbAge ++;
        eState.logWriter.addToFile("===========Agent " + this.pshbAgentID + " finished step " + currentStep + " at   " + this.pshbStage + "===========");
    }
    /*
     ********************************************************************************************
     *                               PERFORM Actions
     * ******************************************************************************************
     */
    public void takeAction(Stage stage, PSHBEnvironment state) {
        switch(stage){
            case LARVA:
                performLarvaAction(state);
                break;
            case ADULTDISP:
                performDISPAction(state);
                break;
            case ADULTCOL:
                performColonizationAction(state);
                break;
            case ADULTREPRO:
                performReproductionAction(state);
            default:
                System.out.println("Unknown stage");
                break;
        }
    }
    public void performLarvaAction(PSHBEnvironment state) {
        if(state.random.nextBoolean(state.mpPshbMortLarva)){ //randomly die when the agent is during larva stage and nor during dormancy
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
            state.numDeathInLARVA ++;
            state.numDeath ++;
            death(state);
        }
        //if not dead, start development action
        boolean d = development(state);
        //after development, the agent will go into the PREOVI stage
        if(this.pshbStage == Stage.PREOVI || d == true){
            if(state.random.nextBoolean(state.mpPshbMortPreovi)){ //some portion of agents will die
                state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
                state.numDeathInLARVA ++;
                state.numDeath ++;
                death(state);
            } else { //this agent doesn't die in the PREOVI stage
                if(state.random.nextBoolean(state.mpProbMate)){ //the agent is mated
                    this.pshbMated = true;
                    this.pshbStage = Stage.ADULTDISP;
                }
            }
        }
    }

    public void performDISPAction(PSHBEnvironment state) {
        //All agents in the dispersal stage suffer a random death
        if(state.random.nextBoolean(state.mpPshbMortAdultDisp)){ //some portion of agents will die
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
            state.numDeathInADULTDISP ++;
            state.numDeath ++;
            death(state);
        }
        //should I stay?
        if(state.random.nextBoolean(state.mpPshbShouldIStay)){ //yes, the agent wants to stay, but need to check if the cell is available
            if(state.getVegCell(this.vegGridX, this.vegGridY) == null || state.getVegCell(this.vegGridX, this.vegGridY).deadVegetation == false) {
                //the cell is available, stay in this cell and move forward into ADULTCOL stage
                this.pshbStage = Stage.ADULTCOL;
            } else { //the cell is not available
                dispersal(state); //dispersal anyway
                this.pshbStage = Stage.ADULTCOL;
            }
        } else { // not want to stay, dispersal anyway
            dispersal(state);
        }
        this.pshbLongevUsed ++;
    }

    public void performColonizationAction(PSHBEnvironment state) {
        //random death in colonization
        if(state.random.nextBoolean(state.mpPshbMortAdultCol)){ //random mortality at colonization stage
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
            state.numDeathInADULTCOL ++;
            state.numDeath ++;
            death(state);
            return;
        }
        try {
            colonization(state);
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        this.pshbLongevUsed ++;
    }

    public void performReproductionAction(PSHBEnvironment state) {
        if(this.pshbMated == true){ //when pshbMated == true, execute the reproduce() because it will reproduce female agents
            reproduce(state,this);
            state.logWriter.addToFile("This agent " + pshbAgentID + "   finished his life cycle!");
            death(state);
        } else { //when pshbMated == false, need to wait the sons developed and turn into be "mated"
            boolean md = maleDevelopment(state);
            if(md == true){ pshbMated = true;}
        }
    }

    /*
    ********************************************************************************************
    *                               DEVELOPMENT
    * ******************************************************************************************
     */

    public boolean development(PSHBEnvironment state){
        //log the start of development
        state.logWriter.addToFile("this agent " + this.pshbAgentID + "   has started development"); //start development
        state.logWriter.addToFile("this pshbDegDaysReq = " + this.pshbDegDaysReq); //specify required Degree-Days
        state.logWriter.addToFile("this pshbDegDays before = " + this.pshbDegDays); //specify Degree-Days before this development step
        //degree-day submodel
        if(pshbDegDays < pshbDegDaysReq){ //if the agent hasn't finished development
            if(currentTemp > 15 && currentTemp< 30){
                pshbDegDays = pshbDegDays + (int) currentTemp * 7;
            }
            state.logWriter.addToFile("pshbDegDays after = " + pshbDegDays);
            return false;
        } else{ //if finishing the development
            pshbDegDays = 0;
            pshbStage = Stage.PREOVI;
            return true;
        }
    }

    public boolean maleDevelopment(PSHBEnvironment state){
        //log the start of son's development
        state.logWriter.addToFile("this mother " + this.pshbAgentID + "   start waiting her son to develop"); //start development
        state.logWriter.addToFile("the son's pshbDegDaysReq = " + this.pshbDegDaysReq); //the son's degree-days in the same as their mom
        state.logWriter.addToFile("this pshbSonDegDays before = " + this.pshbSonDegDays); //specify Son's Degree-Days before this development step
        boolean finished = false;
        //degree-day submodel
        if(pshbSonDegDays < pshbDegDaysReq){ //if the agent hasn't finished development
            if(currentTemp > 15 && currentTemp< 30){
                pshbSonDegDays = pshbSonDegDays + (int) currentTemp * 7;
            }
            state.logWriter.addToFile("pshbSonDegDays after = " + pshbDegDays);
        } else{ //if finishing the development
            pshbSonDegDays = 0;
            finished = true;
        }
        return finished;
    }

    /*
    ************************************************************************
    *                          MOVEMENT
    * **********************************************************************
     */
    //CHANGE
    public void dispersal(PSHBEnvironment state){
        state.logWriter.addToFile("step = " + state.schedule.getSteps() + "    + BEFORE gridX = " + tempGridX + "     + BEFORE gridY = " + tempGridY);
        //Determine the Distance
        pshbDispDist = exponentialGenerator.sample(); //the movement distance follows an exponential distribution
        //Determine the Direction
        if(this.pshbDispDir != -1){ //second step and further
            pshbDispDirPre = pshbDispDir;
            pshbDispDir = pshbDispDirPre + state.random.nextGaussian() * state.mpPshbDirStdDev;
            pshbDispDir = Math.toRadians(pshbDispDir); //convert to Radians
        } else{ //first dispersal: pshbDispDirPre is NAN
            pshbDispDir = Math.random()*360;
            this.pshbDispDir = Math.toRadians(pshbDispDir); //find random direction in Radians
        }
        state.logWriter.addToFile("Dispersal Distance =  " + pshbDispDist); //log the dispersal distance
        state.logWriter.addToFile("pshbDispDirPre = " + pshbDispDirPre); //log previous dispersal direction
        state.logWriter.addToFile("pshb Dispersal Direction in Radian =  " + pshbDispDir); //log current dispersal direction
        //Either die or move. If the temperature is out of range - temp<15 or temp > 30, agents will die
        if(currentTemp < 15 || currentTemp > 30){
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
            state.numDeathInADULTDISP ++;
            state.numDeath ++;
            death(state);
        } else{ //start movement
            state.logWriter.addToFile("this agent " + this.pshbAgentID + "   has started movement"); //start dispersal
            //find a new location, using the lon and lat system
            longitudeX = longitudeX + pshbDispDist * Math.cos(pshbDispDir); //new coordinate X
            latitudeY = latitudeY + pshbDispDist * Math.sin(pshbDispDir); //new coordinate Y
            // the location to the grid map (agentGrid)
            tempGridX = CoordinateConverter.longitudeXToGridX(longitudeX, state.xllcornerTemp, state.tempCellSize); //new grid X
            tempGridY = CoordinateConverter.latitudeYToGridY(latitudeY, state.yllcornerTemp, state.tempCellSize, state.nRowsTemp); //new grid Y
            tempGridX = state.tempGrid.stx(tempGridX);
            tempGridY = state.tempGrid.sty(tempGridY);
            state.agentDevlopGrid.setObjectLocation(this, tempGridX, tempGridY);
            state.logWriter.addToFile("a new red dot at x = " + this.tempGridX + "   y = " + this.tempGridY);
            state.logWriter.addToFile("step = " + state.schedule.getSteps() + "    AFTER gridX = " + tempGridX + "     AFTER gridY = " + tempGridY);
        }

    }

    /*
    **************************************************************************************
    *                           COLONIZATION
    * ************************************************************************************
     */

    public void colonization(PSHBEnvironment state) throws TransformException {
        //calculate the probability of attempting colonization
        this.pshbRemainingTicks = this.pshbLongev - this.pshbLongevUsed;
        probBasedOnRemainingTicks = (5 - pshbRemainingTicks) * 0.2; //(1) first multiplicand, section 7.3, i.e.,(1 - 0.2 * pshbRemainingTicks)
        mpPshbVegMapPrHost = state.getVegMapPrHost(state, this.longitudeX, this.latitudeY); //(2) second multiplicand - from veg map PrHost
        attemptingColonization = probBasedOnRemainingTicks * this.mpPshbVegMapPrHost; //attempting to colonize = (1) * (2)
        //success probability of colonization
        mpPshbColSuccess = state.getVegMapPrRepr(state, this.longitudeX, this.latitudeY); //from veg map PrRepr
        state.logWriter.addToFile("the attempting colonization probability = " + attemptingColonization);
        //attempt to colonize
        if(state.random.nextBoolean(attemptingColonization)){ //determine the probability of attempting colonization, if yes, colonize the tree
            if(state.random.nextBoolean(mpPshbColSuccess)){ //this agent successfully colonized the host tree in the cell
                //this agent successfully colonized the cell
                state.logWriter.addToFile("This agent successfully colonized a patch or a cell");
                //colonize a host by joining in the cell
                colonizeAHost(state, this.longitudeX, this.latitudeY);
                this.pshbHostCell.numColonizedAgents ++; //the cell has a new member!
                this.pshbStage = Stage.ADULTREPRO;
            } else {
                if(this.pshbRemainingTicks > 0){
                    state.logWriter.addToFile("This agent fail to colonize and disperse again."); //dispersal this step or next step???
                    dispersal(state);
                }
                else {
                    state.logWriter.addToFile("This agent fail to colonize and die.");
                    state.numDeathInADULTCOL ++;
                    state.numDeath ++;
                    death(state);
                }
            }
        } else { //if not attempt to colonize the tree because of the quality of the trees or reamining ticks
            if(this.pshbRemainingTicks > 0){
                state.logWriter.addToFile("This agent did not attempt to colonize and disperse again");
                dispersal(state);
            }
            else {
                state.logWriter.addToFile("This agent did not attempt to colonize and died");
                state.numDeathInADULTCOL ++;
                state.numDeath ++;
                death(state);
            }
        }
    }

    public void colonizeAHost(PSHBEnvironment state, double lon, double lat) throws TransformException {
        //convert lon and lat into veg map grid coordiantes
        try {
            vegGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, lon, lat)[0]; //convert lon to gridx in the veg map
            vegGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, lon, lat)[1]; //convert lat to gridy in the veg map
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        //**check if this cell is in a patch**
        //get the hostCell based on dispersal locations
        this.pshbHostCell = state.getVegCell(vegGridX, vegGridY);
        this.pshbHostCell.numColonizedAgents ++; //add one capacity
        //Make sure the cell belong to a patch (i.e., in our study area) before activating a cell
        //This way makes sure that only the cells within the patch got tracked
        if(state.getPatchID(state, vegGridX, vegGridY) != 0) { //this cell is beyond our study area and we don't need to track this cell
            //add this agent into the colonized cell, activate a new cell or join in a current active cell
            if(state.agentColonizedGrid.getObjectsAtLocation(vegGridX, vegGridY) == null){ //if there is no agent in this location
                Bag members = new Bag();
                PSHBVegCell newActiveCell = new PSHBVegCell(members, vegGridX, vegGridY, state.getPatchID(state,vegGridX, vegGridY));
                state.vegMapCell.put(String.join("-", String.valueOf(newActiveCell.vegGridX), String.valueOf(newActiveCell.vegGridY)), newActiveCell);
                newActiveCell.addCellMembers(this);
                state.agentColonizedGrid.setObjectLocation(newActiveCell, vegGridX, vegGridY);
                state.logWriter.addToFile("a new active cell at x = " + this.vegGridX + "   y = " + this.vegGridY);
            } else { //if there is already someone in this location
                PSHBVegCell joinCurrentCell = state.getVegCell(vegGridX, vegGridY);
                joinCurrentCell.addCellMembers(this);
                state.agentColonizedGrid.setObjectLocation(joinCurrentCell, vegGridX, vegGridY);
            }
        }
    }

    public void setPshbHostCell(PSHBVegCell pshbHostCell) {
        this.pshbHostCell = pshbHostCell;
    }

    /*
    *************************************************************************
    *                       REPRODUCTION
    * ***********************************************************************
     */
    public void reproduce(PSHBEnvironment state, PSHBAgent parent){
        double coordX_newborn = parent.longitudeX;
        double coordY_newborn = parent.latitudeY;
        for(int i=0; i< parent.pshbSpawn; i++) {
            state.pshbAgentID ++;
            PSHBAgent a = new PSHBAgent(state, coordX_newborn, coordY_newborn, state.pshbAgentID, Stage.LARVA); //create a newborn
            System.out.println("pshbAgentID = " + a.pshbAgentID + "has started his life at step  " + currentStep);
            a.dateData.put("birthday", currentStep);
            a.locationData.put("lonAtBirth", coordX_newborn);
            a.locationData.put("latAtBirth", coordY_newborn);
            state.logWriter.addToFile("the number of agents spawned = " + a.pshbSpawn);
        }
        state.numBirth += pshbSpawn;
    }

    /*
    ************************************************************************
    *                              DORMANCY
    * **********************************************************************
     */
    public boolean checkDormancy(PSHBEnvironment state) {
        if(state.week <16 && currentTemp < 15 || state.week > 30 && currentTemp < 15) {
            if(pshbStage == Stage.ADULTDISP) {
                pshbDorm = true;
                death(state);
            }else { //larva, preovi, and adultCol cease the action and go into dormancy
                pshbDorm = true;
            }
        } else {
            pshbDorm = false;
        }
        return pshbDorm;
    }

    /*
    **********************************************************************************
    *                                        Death
    * ********************************************************************************
     */
    public void death(PSHBEnvironment state){
        //get Death data
        this.dateData.put("dateOfDeath", currentStep); //record the date of death
        this.locationData.put("lonAtDeath", this.longitudeX); //record the longitude at death
        this.locationData.put("latAtDeath", this.latitudeY); //record the latitude at death
        this.stageData.put("deathStage", this.pshbStage); //record the death stage
        this.ageData.put("deathAge",this.pshbAge);
        String lifeHistoryInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", currentStep, this.pshbAgentID,
                this.dateData.get("birthday"), this.dateData.get("dateOfDeath"), this.locationData.get("lonAtBirth"),
                this.locationData.get("latAtBirth"), this.locationData.get("lonAtDeath"), this.locationData.get("latAtDeath"),
                this.stageData.get("deathStage"), this.ageData.get("deathAge"));
        state.outputWriter.addToFile(lifeHistoryInfo);
        //stop the event
        event.stop();
    }

}
