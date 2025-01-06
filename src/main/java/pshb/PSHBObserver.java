package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;

//ADD
public class PSHBObserver implements Steppable {
    @Override
    public void step(SimState simState) {
        //identify the year
        PSHBEnvironment eState = (PSHBEnvironment) simState;
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


    /*
     ****************************************************************************************
     *                                  Setters and Getters
     * ***************************************************************************************
     */
//    //population data collected annually
//    public int getNumBirth() {
//        return numBirth;
//    }
//
//    public void setNumBirth(int numBirth) {
//        this.numBirth += numBirth;
//    }
//
//    public int getNumDeath() {
//        return numDeath;
//    }
//
//    public void setNumDeath(int numDeath) {
//        this.numDeath += numDeath;
//    }
//
//    public int getNumDeathInLARVA() {
//        return numDeathInLARVA;
//    }
//
//    public void setNumDeathInLARVA(int numDeathInLARVA) {
//        this.numDeathInLARVA += numDeathInLARVA;
//    }
//
//    public int getNumDeathInADULTDISP() {
//        return numDeathInADULTDISP;
//    }
//
//    public void setNumDeathInADULTDISP(int numDeathInADULTDISP) {
//        this.numDeathInADULTDISP += numDeathInADULTDISP;
//    }
//
//    public int getNumDeathInADULTCOL() {
//        return numDeathInADULTCOL;
//    }
//
//    public void setNumDeathInADULTCOL(int numDeathInADULTCOL) {
//        this.numDeathInADULTCOL += numDeathInADULTCOL;
//    }
//    //impact on vegetation

}
