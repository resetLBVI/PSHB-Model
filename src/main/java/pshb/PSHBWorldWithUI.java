package pshb;

import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.grid.FastValueGridPortrayal2D;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.gui.SimpleColorMap;

import javax.swing.*;
import java.awt.*;

/* #######################################################################################
 * This class includes:
 * (1) getName (2) getSimulationInspectedObject (3) init (4) quit (5) setupPortrayals
 * (6) start (7) main
 * #######################################################################################
 */

public class PSHBWorldWithUI extends GUIState {
    Display2D display; //create a display
    JFrame displayFrame; //create a display frame
    //    GeomVectorFieldPortrayal vegetationPortrayal = new GeomVectorFieldPortrayal();
    FastValueGridPortrayal2D temperatureGridPortrayal = new FastValueGridPortrayal2D("temperature grid");
    FastValueGridPortrayal2D vegGridPortrayal = new FastValueGridPortrayal2D("vegetation grid");
    SparseGridPortrayal2D PSHBAgentPortrayal = new SparseGridPortrayal2D();
    //    SparseGridPortrayal2D PSHBGroupPortrayal = new SparseGridPortrayal2D();
    //Constructor
    public PSHBWorldWithUI(SimState state) { super(state);}
    public PSHBWorldWithUI(){super(new PSHBEnvironment(System.currentTimeMillis()));}

    //methods
    public static String getName() {return "RESET: PSHB World";} //where do we use this?

    public Object getSimulationInspectedObject(){
        return this.state;
    } //figure out what is this for

    public void init (Controller controller){
        super.init(controller); //super from GUIState
        this.display = new Display2D(600, 600, this); //initially create a UI display
        this.display.attach(this.temperatureGridPortrayal, "temperature grids"); //attach the temperature grids
        this.display.attach(this.PSHBAgentPortrayal, "PSHBAgents"); // attach the PSHB agents
//        this.display.attach(this.PSHBGroupPortrayal, "PSHBGroups"); //attach the PSHB groups
        this.displayFrame = this.display.createFrame(); //create a display frame
        controller.registerFrame(this.displayFrame); //set-up display
        this.displayFrame.setVisible(true); //set-up display
    }

    public void quit() {
        super.quit();
        if(this.displayFrame != null) {
            this.displayFrame.dispose();
        } //if there is a frame, dispose it
        this.displayFrame = null;
        this.display = null;
    }

    public void start() {
        super.start();
        this.setupPortrayals();
    }

    private void setupPortrayals() {
        PSHBEnvironment eState = (PSHBEnvironment)state;
//        temperatureGridPortrayal.setField(eState.tempGrid);
        temperatureGridPortrayal.setField(eState.basicGrid.getGrid()); //connect the gridField in the environment and UI, get the grids to UI
        Color color = new Color(0, 0, 255, 0); //blue ,
        this.temperatureGridPortrayal.setMap(new SimpleColorMap(0, 100, Color.white, color));

        this.PSHBAgentPortrayal.setField(eState.agentDevlopGrid);
        this.PSHBAgentPortrayal.setPortrayalForAll(new OvalPortrayal2D(Color.RED, 0.7));
        //reschedule the display
        this.display.reset();
        this.display.setBackdrop(Color.WHITE); //set the color of the background
        this.display.repaint();
    }


    public static void main(String[] args) {
        PSHBWorldWithUI worldGUI = new PSHBWorldWithUI();
        Console console = new Console(worldGUI);
        console.setVisible(true);
    }


}
