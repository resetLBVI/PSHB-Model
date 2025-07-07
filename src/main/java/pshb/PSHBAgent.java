package pshb;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.geotools.api.referencing.operation.TransformException;
import pshb.Utils.Calculation;
import pshb.Utils.CoordinateConverter;
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
    boolean pshbMated; //indicate if the agent is mated or unmated
    int pshbLongev; //state an agent's longevity in adult stages
    int pshbLongevUsed; //countdown of an agent's longevity in the adult stages
    boolean pshbDorm; //a state variable indicates if the agent is during the dormancy
    Integer pshbAge; //indicate the current age
    //development
    DoubleGrid2D tempGrid; //set a temperature grid in the environment
    double currentTemp; //a state variable to get the current temperature
    //movement (dispersal)
    public double pshbDispDist; // dispersal distance (m)
    public double pshbDispDir; // dispersal direction (degree)
    public double pshbDispDirPre; //Direction moved previous tick (degree)
    ExponentialDistribution exponentialGenerator; //randomly generate a number for moving distance following an exponential distribution
    //colonization
    public double attemptingColonization; //prob of attempting colonization
    public double probBasedOnRemainingTicks; // the prob of attempting colonization first depends on the remaining ticks an agent has
    public int pshbRemainingTicks; //the remaining ticks to the end of the adult stage (i.e., to the death)
    double mpPshbVegMapPrHost; //the probability to host a tree in a specific cell (from veg Map_PrHost)
    double mpPshbColSuccess; //the probability successfully colonize a host tree (from veg Map_PrRepr)
    PSHBVegCell pshbHostCell; //an agent will claim a vegCell (a cell entity) only when they colonized a host
    //reproduction
    public int pshbSpawn; //number of eggs an agent lay
    //other schedule variables
    Stoppable event; //schedule ot stop the event
    Long currentStep; //identify current step
    //life history data collection
    Map<String, Double> locationData; //a hashmap to record the location data
    Map<String, Long> dateData; //a hashmap to record the related date data, e.g., birthday, death date, etc.
    Map<String, Enum> stageData; //a hashmap to record the stage when an event occurs. e.g., death stage
    Map<String, Integer> ageData; //a hashmap to record the age when an event occurs. e.g., death age



    public PSHBAgent(PSHBEnvironment state, double longitudeX, double latitudeY, int pshbAgentID, Stage stage) {
        super();
        this.pshbStage = stage; //state an agent's current stage
        this.pshbAgentID = pshbAgentID; //assign each agent a unique ID
        this.longitudeX = longitudeX; //longitude ie. pshbLong
        this.latitudeY = latitudeY; //latitude ie. pshbLat
        this.pshbDegDaysReq = (int) (398 + state.random.nextGaussian() * 17); //every agent has slightly different on development
        this.pshbDegDays = 0; //the counts of the degree-days
        this.pshbSonDegDays = 0; // the counts of son's degree-days
        this.tempGridX = CoordinateConverter.longitudeXToGridX(longitudeX, state.xllcornerTemp, state.tempCellSize);
        this.tempGridY = CoordinateConverter.latitudeYToGridY(latitudeY, state.yllcornerTemp, state.tempCellSize, state.nRowsTemp);
        this.vegGridX = 0; //the vegGridX is activated only when the agent colonized a cell
        this.vegGridY = 0; //the vegGridY is activated only when the agent colonized a cell
        this.pshbMated = false; //indicate if the agent is mated or unmated
        this.pshbLongev = state.random.nextInt(3) + 1; //state an agent's longevity in adult stages (in steps/weeks)
        this.pshbLongevUsed = 0; //countdown of an agent's longevity in the adult stages
        this.pshbDispDirPre = -1; //Direction moved previous tick (degree)
        this.pshbDispDir = -1; // dispersal direction (degree)
        this.pshbDispDist = 0; //dispersal distance (m)
        this.pshbRemainingTicks = this.pshbLongev; //the remaining ticks to the end of the adult stage (i.e., to the death)
//        this.exponentialGenerator = new ExponentialDistribution(state.mpPshbMove); //determine a number for moving distance
        this.pshbSpawn = Calculation.getPoisson(1/state.mpPshbSpawn); //number of eggs an agent lay follows a poisson dist.
        this.pshbAge = 0; //the current age
        this.pshbHostCell = null; //a vegCell (a cell entity) is claimed when they colonized a host
        //weekly data output
        locationData = new HashMap<>(); //collect location data
        dateData = new HashMap<>(); //collect date related data
        stageData = new HashMap<>(); //collect stage data
        ageData = new HashMap<>(); //collect age data
    }

    @Override
    public void step(SimState state) {
        long startTime = System.currentTimeMillis(); //Testing: test the efficiency of codes, start point
        PSHBEnvironment eState = (PSHBEnvironment) state; //Downcasting the PSHB Environment; Downcasting involves converting a superclass object to its subclass type.
        currentStep = eState.schedule.getSteps(); //get the current steps
        eState.logWriter.addToFile("========Agent " + this.pshbAgentID + " has started current step = " + currentStep +"==========="); //log current step
        this.tempGrid = (DoubleGrid2D) eState.weeklyTempGrids[eState.week].getGrid(); //get current temperature map
        this.currentTemp = this.tempGrid.get(this.tempGridX, this.tempGridY); //obtain current temperature from current temp map based on the locations
        eState.logWriter.addToFile("current week = " + eState.week + "   Max Temp = " + this.tempGrid.max() + "   this agent's stage " + this.pshbStage); //log basic information of current step
        long endTime = System.currentTimeMillis(); //Testing: test the code efficiency, end point
//        System.out.println("Step took " + (endTime - startTime) + " milliseconds");
        
        //Step 1: check dormancy before checking the stages
        //If the dormancy == true, check the stage for the agent
        //if the agent was in the LARVA or PREOVI stage, there is a chance this agent would die
        if(checkDormancy(eState)){ //if the dormancy is true
            if(this.pshbStage == Stage.LARVA || this.pshbStage == Stage.PREOVI){
                if(state.random.nextBoolean(2* eState.mpPshbMortLarva)){ //if the agent is randomly chosen to die
                    eState.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage); //record
                    eState.numDeathInLARVA ++; //death count in LARVA stage increased by one
                    eState.numDeath ++; //death count increased by one
                    long deathStart = System.currentTimeMillis(); //Testing: test how much time the death function costs
                    death(eState); //execute the death method
                    long deathEnd = System.currentTimeMillis(); //Testing: test how much time the death function costs
                    System.out.println("Death took " + (deathEnd - deathStart) + " milliseconds"); //testing: how long does the death function cost
                    return; //exit this method
                }
            } else { //if this agent was in ADULTDISP, ADULTCOL or ADULTREPRO and was under dormancy but not dead, return this step
                return; //exit this method
            }
        }
        //Step 2 : take actions, check the stages, and then perform the actions according to their stage
        takeAction(this.pshbStage, eState); //taking different actions based on the stages
        //Step 3: age increment
        this.pshbAge ++; //age increment in week
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
                performLarvaAction(state); //execute actions in the larva stage
                break;
            case ADULTDISP:
                performDISPAction(state); //execute actions in the Dispersal stage
                break;
            case ADULTCOL:
                performColonizationAction(state); //execute actions in the Colonization stage
                break;
            case ADULTREPRO:
                performReproductionAction(state); //execute actions in the Reproduction stage
            default: //print the "unknow stage" so we know there is something wrong
                System.out.println("Unknown stage");
                break;
        }
    }
    public void performLarvaAction(PSHBEnvironment state) {
        //randomly die when the agent is during larva stage and is not during dormancy
        if(state.random.nextBoolean(state.mpPshbMortLarva)){
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
            //record the death in the LARVA stage
            state.numDeathInLARVA ++; //death count in LARVA stage increased by one
            state.numDeath ++; //death count increased by one
            death(state); //execute the death method
        }
        //if not dead, start development action
        boolean d = development(state);
        //after development, the agent will go into the PREOVI stage within the same tick
        if(this.pshbStage == Stage.PREOVI || d == true){
            if(state.random.nextBoolean(state.mpPshbMortPreovi)){ //some portion of agents will die
                state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
                state.numDeathInLARVA ++; //death count in LARVA stage increased by one
                state.numDeath ++; //death count increased by one
                death(state); //execute the death method
            } else { //this agent doesn't die in the PREOVI stage
                if(state.random.nextBoolean(state.mpProbMate)){ //when the agent is mated==true
                    this.pshbMated = true; //state this agent is mated
                    this.pshbStage = Stage.ADULTDISP; //this agent is ready to move on to the DISP stage
                } //what if the agent in the PREOVI stage but not mated?
            }
        }
    }

    public void performDISPAction(PSHBEnvironment state) {
        //All agents in the dispersal stage suffer a random death
        if(state.random.nextBoolean(state.mpPshbMortAdultDisp)){ //some portion of agents will die
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage);
            state.numDeathInADULTDISP ++; //death count in DISPERSAL stage increased by one
            state.numDeath ++; //death count increased by one
            death(state); //execute the death method
        }
        //should I stay? Determine if this agent will stay in the cell
        if(state.random.nextBoolean(state.mpPshbShouldIStay)){ //yes, the agent wants to stay, but need to check if the cell is available
            if(state.getVegCell(this.vegGridX, this.vegGridY) == null || state.getVegCell(this.vegGridX, this.vegGridY).deadVegetation == false) {
                this.pshbStage = Stage.ADULTCOL; //the cell is available, stay in this cell and move forward into ADULTCOL stage
            } else { //the cell is not available
                dispersal(state); //dispersal anyway
                this.pshbStage = Stage.ADULTCOL;
            }
        } else { // not want to stay, dispersal anyway
            dispersal(state); //execute the dispersal sub-model
        }
        this.pshbLongevUsed ++; //the age is increased by one
    }

    public void performColonizationAction(PSHBEnvironment state) {
        //random death in colonization
        if(state.random.nextBoolean(state.mpPshbMortAdultCol)){ //random mortality at colonization stage
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage); //log IDs and which stage they died
            state.numDeathInADULTCOL ++; //death count in ADULTCOL stage increased by one
            state.numDeath ++; //death count increased by one
            death(state); //execute the death function
            return; //if this agent is dead, then return this method
        }
        try { //block contains code that might throw an exception (i.e., an error during runtime) to avoid crash
            colonization(state); //execute the colonization function
        } catch (TransformException e) {
            throw new RuntimeException(e); //signal that an exception should be thrown
        }
        this.pshbLongevUsed ++; //the adult age increased by one
    }

    public void performReproductionAction(PSHBEnvironment state) {
        if(this.pshbMated == true){ //when pshbMated == true, execute the reproduce() because it will reproduce female agents
            reproduce(state,this); //execute the reproduction function
            state.logWriter.addToFile("This agent " + pshbAgentID + "   finished his life cycle!"); //log the success
            death(state); //execute the death function
        } else { //when pshbMated == false, need to wait the sons developed and turn into be "mated"
            boolean md = maleDevelopment(state); //execute the male development when reproducing sons
            if(md == true){ pshbMated = true;} //if finishing the son's development, assign the pshbMated == true to the mother.
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
            if(currentTemp > 15 && currentTemp< 30){ //when current temperature is appropriate, which is 15-30 Celsius
                pshbDegDays = pshbDegDays + (int) currentTemp * 7; //update the degree-days
            }
            state.logWriter.addToFile("pshbDegDays after = " + pshbDegDays); //check if the degree days is really updated by comparing before and after
            return false; //return a value
        } else{ //when the pshbDegDays reaches the pshbDegDayReq, the development is finished
            pshbDegDays = 0; //reset the degree-days
            pshbStage = Stage.PREOVI; //ready to move on the next stage
            return true; //return a value
        }
    }

    public boolean maleDevelopment(PSHBEnvironment state){
        //log the start of son's development
        state.logWriter.addToFile("this mother " + this.pshbAgentID + "   start waiting her son to develop"); //start development
        state.logWriter.addToFile("the son's pshbDegDaysReq = " + this.pshbDegDaysReq); //the son's degree-days in the same as their mom
        state.logWriter.addToFile("this pshbSonDegDays before = " + this.pshbSonDegDays); //specify Son's Degree-Days before this development step
        boolean finished = false; //state if the development of male offspring has been finished
        //degree-day submodel
        if(pshbSonDegDays < pshbDegDaysReq){ //if the agent hasn't finished development
            if(currentTemp > 15 && currentTemp< 30){ //if the temperature is appropriate
                pshbSonDegDays = pshbSonDegDays + (int) currentTemp * 7; //update the degree-days of sons
            }
            state.logWriter.addToFile("pshbSonDegDays after = " + pshbDegDays); //log the degree days
        } else{ //if finishing the development
            pshbSonDegDays = 0; //reset the degree days of the sons
            finished = true; //define the son's development has been completed
        }
        return finished; //return a value
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
            state.logWriter.addToFile("this agent ID = " + this.pshbAgentID + "     lived for " + currentStep + "   days and died in " + this.pshbStage); //log the death
            state.numDeathInADULTDISP ++; //death count in DISPERSAL stage increased by one
            state.numDeath ++; //death count increased by one
            death(state);
        } else{ //start movement
            state.logWriter.addToFile("this agent " + this.pshbAgentID + "   has started movement"); //log the start of dispersal
            //find a new location, using the lon and lat system
            longitudeX = longitudeX + pshbDispDist * Math.cos(pshbDispDir); //new coordinate X
            latitudeY = latitudeY + pshbDispDist * Math.sin(pshbDispDir); //new coordinate Y
            // the location to the grid map (agentGrid)
            tempGridX = CoordinateConverter.longitudeXToGridX(longitudeX, state.xllcornerTemp, state.tempCellSize); //new grid X
            tempGridY = CoordinateConverter.latitudeYToGridY(latitudeY, state.yllcornerTemp, state.tempCellSize, state.nRowsTemp); //new grid Y
            tempGridX = state.tempGrid.stx(tempGridX); //converts a simulation-space x coordinate into a screen-space (pixel) X coordinate.
            tempGridY = state.tempGrid.sty(tempGridY); //same for the Y coordinate
            state.agentDevlopGrid.setObjectLocation(this, tempGridX, tempGridY); //set the agent at the location
            state.logWriter.addToFile("a new red dot at x = " + this.tempGridX + "   y = " + this.tempGridY); //log the location
            state.logWriter.addToFile("step = " + state.schedule.getSteps() + "    AFTER gridX = " + tempGridX + "     AFTER gridY = " + tempGridY); //check the location after movement
        }

    }

    /*
    **************************************************************************************
    *                           COLONIZATION
    * ************************************************************************************
     */

    public void colonization(PSHBEnvironment state) throws TransformException {
        //calculate the probability of attempting colonization
        this.pshbRemainingTicks = this.pshbLongev - this.pshbLongevUsed; //how many ticks remaining in the current date
        probBasedOnRemainingTicks = (5 - pshbRemainingTicks) * 0.2; //(1) first multiplicand, section 7.3, i.e.,(1 - 0.2 * pshbRemainingTicks)
        mpPshbVegMapPrHost = state.getVegMapPrHost(state, this.longitudeX, this.latitudeY); //(2) second multiplicand - from veg map PrHost
        attemptingColonization = probBasedOnRemainingTicks * this.mpPshbVegMapPrHost; //attempting to colonize = (1) * (2)
        //success probability of colonization is obtained from the PrRepr
        mpPshbColSuccess = state.getVegMapPrRepr(state, this.longitudeX, this.latitudeY); //from veg map PrRepr
        state.logWriter.addToFile("the attempting colonization probability = " + attemptingColonization);
        //attempt to colonize
        if(state.random.nextBoolean(attemptingColonization)){ //determine the probability of attempting colonization, if yes, colonize the tree
            if(state.random.nextBoolean(mpPshbColSuccess)){ //this agent successfully colonized the host tree in the cell
                //this agent successfully colonized the cell
                state.logWriter.addToFile("This agent successfully colonized a patch or a cell");
                //colonize a host by joining in the cell
                colonizeAHost(state, this.longitudeX, this.latitudeY); //join a vegCell enetity
                this.pshbHostCell.numColonizedAgents ++; //the cell has a new member!
                this.pshbStage = Stage.ADULTREPRO; //ready to move to next Stage (ADULTREPRO)
            } else {
                if(this.pshbRemainingTicks > 0){ //still got time for dispersal
                    state.logWriter.addToFile("This agent fail to colonize and disperse again."); //log the failure of the colonization
                    dispersal(state); //dispersal this step or next step???
                }
                else {
                    state.logWriter.addToFile("This agent fail to colonize and die."); //log
                    state.numDeathInADULTCOL ++; //death counts in COLONIZATION Stage increased by one
                    state.numDeath ++; //death counts increased by one
                    death(state); //execute the death function
                }
            }
        } else { //if not attempt to colonize the tree
            if(this.pshbRemainingTicks > 0){ //if there is still time for dispersal
                state.logWriter.addToFile("This agent did not attempt to colonize and disperse again"); //log
                dispersal(state); //execute the dispersal function again
            }
            else { //if there is no time for dispersal, the agent die
                state.logWriter.addToFile("This agent did not attempt to colonize and died");  //log
                state.numDeathInADULTCOL ++; //death count in COLONIZATION stage increased by one
                state.numDeath ++; //death count increase by one
                death(state); //execute the death function
            }
        }
    }

    public void  colonizeAHost(PSHBEnvironment state, double lon, double lat) throws TransformException {
        //convert lon and lat into veg map grid coordiantes
        try {
            vegGridX = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, lon, lat)[0]; //convert lon to gridx in the veg map
            vegGridY = CoordinateConverter.coordToGrid(state.crsPrHost, state.ggPrHost, lon, lat)[1]; //convert lat to gridy in the veg map
        } catch (TransformException e) { //catch the exception
            throw new RuntimeException(e);
        }
        //**check if this cell is in a patch** - this method hasn't been implemented
        //claim the hostCell based on dispersal locations
        this.pshbHostCell = state.getVegCell(vegGridX, vegGridY); //the HostCell is an entity of the model
        this.pshbHostCell.numColonizedAgents ++; //add one capacity
        //Make sure the cell belong to a patch (i.e., in our study area) before activating a cell
        //This way makes sure that only the cells within the patch got tracked
        if(state.getPatchID(state, vegGridX, vegGridY) != 0) { //this cell is within the RESET area
            //add this agent into the colonized cell, activate a new cell or join in a current active cell
            if(state.agentColonizedGrid.getObjectsAtLocation(vegGridX, vegGridY) == null){ //if there is no agent in this location
                Bag members = new Bag(); //create a bag to contain PSHB members
                PSHBVegCell newActiveCell = new PSHBVegCell(members, vegGridX, vegGridY, state.getPatchID(state,vegGridX, vegGridY)); //activate a new cell
                state.vegMapCell.put(String.join("-", String.valueOf(newActiveCell.vegGridX), String.valueOf(newActiveCell.vegGridY)), newActiveCell); //set the entity
                newActiveCell.addCellMembers(this); //add this member into the entity
                state.agentColonizedGrid.setObjectLocation(newActiveCell, vegGridX, vegGridY); //set the vegCell entity in the space
                state.logWriter.addToFile("a new active cell at x = " + this.vegGridX + "   y = " + this.vegGridY); //log
            } else { //if there is already someone in this location
                PSHBVegCell joinCurrentCell = state.getVegCell(vegGridX, vegGridY); //get the active cell
                joinCurrentCell.addCellMembers(this); //add this current agent into the cell
                state.agentColonizedGrid.setObjectLocation(joinCurrentCell, vegGridX, vegGridY); //set the entity in the space
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
        double coordX_newborn = parent.longitudeX; //define the newborn's x location as parents'
        double coordY_newborn = parent.latitudeY; //define the newborn's y location as parents'
        for(int i=0; i< parent.pshbSpawn; i++) { //loop for all the offspring
            state.pshbAgentID ++; //define an unique ID for the newborn
            PSHBAgent a = new PSHBAgent(state, coordX_newborn, coordY_newborn, state.pshbAgentID, Stage.LARVA); //create a newborn
            System.out.println("pshbAgentID = " + a.pshbAgentID + "has started his life at step  " + currentStep); //print this newborn in the console
            a.dateData.put("birthday", currentStep); //record the birthday
            a.locationData.put("lonAtBirth", coordX_newborn); //record the x location of the newborn
            a.locationData.put("latAtBirth", coordY_newborn); //record the y location of the newborn
        }
        state.numBirth += pshbSpawn;
    }

    /*
    ************************************************************************
    *                              DORMANCY
    * **********************************************************************
     */
    public boolean checkDormancy(PSHBEnvironment state) {
        if(state.week <16 && currentTemp < 15 || state.week > 30 && currentTemp < 15) { //during the dormancy time or low/high temp, go dormancy
            if(pshbStage == Stage.ADULTDISP) { //if during the DISPERSAL, the agent will die
                pshbDorm = true;
                death(state);
            }else { //larva, preovi, and adultCol cease the action and go into dormancy
                pshbDorm = true;
            }
        } else { //if not during dormancy, return a false value
            pshbDorm = false;
        }
        return pshbDorm; //return a yes/no value
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
        this.ageData.put("deathAge",this.pshbAge);//record the death age
        //create a String list that can store all the information
        String lifeHistoryInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", currentStep, this.pshbAgentID,
                this.dateData.get("birthday"), this.dateData.get("dateOfDeath"), this.locationData.get("lonAtBirth"),
                this.locationData.get("latAtBirth"), this.locationData.get("lonAtDeath"), this.locationData.get("latAtDeath"),
                this.stageData.get("deathStage"), this.ageData.get("deathAge"));
        state.outputWriter.addToFile(lifeHistoryInfo); //add the information into file
        //stop the event
        event.stop();
    }

}
