package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;

//ADD
public class PSHBObserver implements Steppable {
    @Override
    public void step(SimState simState) {
        PSHBEnvironment eState = (PSHBEnvironment) simState; //Downcasting the PSHB Environment
        //collect population data
        if (PSHBEnvironment.DEBUG) System.out.println("==========Observer's currentWeek= " + eState.currentWeek + "========================================");
        if(eState.currentWeek == 51) { //get the data in the last week of the year
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
        if (PSHBEnvironment.DEBUG) System.out.println("currentWeek for population data : " + state.currentWeek);
        state.populationSize = state.agentDevelopGrid.getAllObjects().size();
        String popInfo = String.format("%s,%s,%s,%s,%s,%s,%s",
                state.currentYear, state.populationSize, state.numBirth, state.numDeath, state.numDeathInLARVA, state.numDeathInADULTDISP,
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
