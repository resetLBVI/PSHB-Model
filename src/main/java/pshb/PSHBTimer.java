package pshb;

import sim.engine.SimState;
import sim.engine.Steppable;

public class PSHBTimer implements Steppable {
    @Override
    public void step(SimState simState) {
        PSHBEnvironment eState = (PSHBEnvironment) simState; //downcasting the PSHB environment
        //update the timer
        eState.updateYear();
        eState.updateWeek();
        System.out.println("===================================================");
        System.out.println("Update Current week:" + eState.currentWeek);
        System.out.println("Update Current year:" + eState.currentYear);
        System.out.println("===================================================");
    }
}
