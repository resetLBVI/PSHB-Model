package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;

//ADD
public class PSHBObserver implements Steppable {
    @Override
    public void step(SimState simState) {
        PSHBEnvironment eState = (PSHBEnvironment) simState; //Downcasting the PSHB Environment
        //collect population data
        if(eState.week % 51 == 0) { //get the data in the last week of the year
            collectPopData(eState);
            reset(eState);
        }

    }

    /*
     ****************************************************************************************
     *                                  Data Collection
     * ***************************************************************************************
     */
    public void collectPopData(PSHBEnvironment state) {
        //start writing
        System.out.println("week in Data Collection " + state.week);
        state.populationSize = state.agentDevlopGrid.getAllObjects().size();
        String popInfo = String.format("%s,%s,%s,%s,%s,%s,%s",
                state.year, state.populationSize, state.numBirth, state.numDeath, state.numDeathInLARVA, state.numDeathInADULTDISP,
                state.numDeathInADULTCOL);
        state.popSummaryWriter.addToFile(popInfo);
    }

    public void reset(PSHBEnvironment state) {
        state.numBirth = 0;
        state.numDeath = 0;
        state.numDeathInLARVA = 0;
        state.numDeathInADULTDISP = 0;
        state.numDeathInADULTCOL = 0;
    }


}
