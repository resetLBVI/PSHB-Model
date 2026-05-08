package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;

/**
 * This PSHBVegCell class describe the vegetation cell's birth and death,
 */
public class
PSHBVegCell implements Steppable {
    // vegetation cell's state variables
    Bag members; //create a bag to contain PSHB agents
    int vegGridX; //x location
    int vegGridY; //y location
    int patchID; //the patch a veg cell belongs to
    int numColonizedAgents; // the current number of colonized agents should be less than 5 to keep the cell alive. The capacity of the cell is 5
    boolean deadVegetation; //when the cell is dead, it's true
    //scheduling
    Stoppable event; //schedule to stop the event

    //Constructor
    public PSHBVegCell(PSHBEnvironment state,Bag members, int vegGridX, int vegGridY, int patchID) {
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
        String activateInfo = String.format("%s,%s,%s,%s,%s,%s", state.currentYear, state.currentWeek,
                this.deadVegetation, this.vegGridX, this.vegGridY, patchID);
        state.impactWriter.addToFile(activateInfo);
    }

    @Override
    public void step(SimState state) {
        PSHBEnvironment eState = (PSHBEnvironment) state;
        if(numColonizedAgents >= 5 && eState.currentWeek ==1) { //update in the second week of the year (Section 3.3)
            //collect impact data when a cell is dead - collect "year" "vegGridX" "vegGridY" "patchID"
            numColonizedAgents = 0; //reset
            this.deadVegetation = true;
            String impactInfo = String.format("%s,%s,%s,%s,%s,%s", eState.currentYear, eState.currentWeek,
                    this.deadVegetation, this.vegGridX, this.vegGridY, this.patchID);
            eState.impactWriter.addToFile(impactInfo);
            death((PSHBEnvironment)state); //execute the death method
        }
    }

    /**
     * The death method
     * @param state
     */
    public void death(PSHBEnvironment state) {
        this.numColonizedAgents = 0;
        event.stop();
        // Sever back-references from each member agent before clearing
        for (int i = 0; i < members.numObjs; i++) {
            ((PSHBAgent) members.objs[i]).pshbHostCell = null;
        }
        members.clear();
        // Remove the cell itself from all environment collections so it can be GC'd
        state.agentColonizedGrid.remove(this);
        state.agentDevelopGrid.remove(this);
        state.vegMapCell.remove(String.join("-", String.valueOf(this.vegGridX), String.valueOf(this.vegGridY)));
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
