package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;

public class PSHBTimer implements Steppable {
    @Override
    public void step(SimState simState) {
        PSHBEnvironment eState = (PSHBEnvironment)simState; //downcasting the PSHB environment
        //update the timer
        eState.updateYear();
        eState.updateWeek();
        eState.rollToWeekForTempMaps(eState.currentWeek);
        // Flush buffered output once per week so low-volume files (e.g. impact) are visible mid-run.
        eState.flushWriters();
        System.out.println("Update -> week: " + eState.currentWeek + "  year: " + eState.currentYear);
    }
}
