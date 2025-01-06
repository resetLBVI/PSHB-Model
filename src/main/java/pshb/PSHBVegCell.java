package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;
//ADD
public class PSHBVegCell implements Steppable {
    // vegetation cell's state variables
    Bag members; //create a bag to contain PSHB agents
    int vegGridX; //x location
    int vegGridY; //y location
    int patchID;
    int numColonizedAgents; // the capacity of the cell, the default is 5
    boolean deadVegetation; //when the cell is dead, it's true
    //scheduling
    Stoppable event;
    int week;

    //Constructor
    public PSHBVegCell(Bag members, int vegGridX, int vegGridY, int patchID) {
        this.members = members;
        this.vegGridX = vegGridX;
        this.vegGridY = vegGridY;
        this.patchID = patchID;
        this.numColonizedAgents = 0;
        this.deadVegetation = false;
        for(int i=0; i<members.numObjs; i++){
            PSHBAgent a = (PSHBAgent) members.objs[i];
            a.setPshbHostCell(this);
        }
    }

    @Override
    public void step(SimState state) {
        PSHBEnvironment eState = (PSHBEnvironment) state;
        this.week = (int)(eState.schedule.getSteps() % 52); //the week is from 0-51 in the current year
        if(numColonizedAgents >= 5 && this.week ==1) { //update in the second week of the year
            //collect impact data when a cell is dead - collect "year" "vegGridX" "vegGridY" "patchID"
            eState.impactDataWriter.addToFile(Integer.toString(this.vegGridX)); //record the dead cell location x
            eState.impactDataWriter.addToFile(Integer.toString(this.vegGridY)); //record the dead cell location y
            eState.impactDataWriter.addToFile(Integer.toString(this.patchID)); //record the dead cell patch ID
            death((PSHBEnvironment)state);
            this.deadVegetation = true;
        }
    }

    public void death(PSHBEnvironment state) {
        this.numColonizedAgents = 0;
        event.stop();
        members.clear();
    }

    /*
    **************************************************************************************
    *                                 Colonization in the CELL
    * ************************************************************************************
     */
    public boolean addCellMembers(PSHBAgent agent) {
        final boolean results = members.add(agent);
        if(results == true) {
            numColonizedAgents = members.numObjs;
            agent.setPshbHostCell(this);
            return true;
        } else {
            return false;
        }
    }

}
