package pshb;

import pshb.Utils.OutputWriter;
import pshb.sim.portrayal.simple.ScaledImagePortrayal;
import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.SimplePortrayal2D;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.Int2D;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/* #######################################################################################
 * This class includes:
 * (1) getName (2) getSimulationInspectedObject (3) init (4) quit (5) setupPortrayals
 * (6) start (7) main
 * #######################################################################################
 */

public class PSHBWorldWithUI extends GUIState {
    Display2D display; //create a display
    JFrame displayFrame; //create a display frame
    SparseGridPortrayal2D backgroundPortrayal = new SparseGridPortrayal2D(); //create a background portrayal in the UI
    SparseGridPortrayal2D PSHBAgentPortrayal = new SparseGridPortrayal2D() {
        @Override
        public SimplePortrayal2D getPortrayalForObject(Object obj) {
            PSHBEnvironment eState = (PSHBEnvironment) state;
            Int2D loc = eState.agentGrid.getObjectLocation(obj);
            System.out.println("Drawn agent at: " + loc);
            return new OvalPortrayal2D(Color.BLACK, 6);
        }
    }; //create an agent portrayal in the UI
    //Constructor
    public PSHBWorldWithUI(SimState state) { super(state);}
    public PSHBWorldWithUI(){super(new PSHBEnvironment(System.currentTimeMillis()));}

    //methods
    public static String getName() {return "RESET: PSHB World";} //where do we use this?

    public Object getSimulationInspectedObject(){   return this.state;  }

    public void init (Controller controller){
        super.init(controller); //super from GUIState
        this.display = new Display2D(800, 800, this); //initially create a UI display
        this.displayFrame = display.createFrame(); //create a display frame
        controller.registerFrame(this.displayFrame); //set-up display
        this.displayFrame.setVisible(true); //set-up display
    }

    public void quit() {
        super.quit();
        if(this.displayFrame != null) {
            this.displayFrame.dispose();
        } //if there is a frame, dispose it
        this.displayFrame = null; //set the frame as null
        this.display = null; //set the display as null
    }

    public void start() {
        super.start();
        this.setupPortrayals();
    }

    private void setupPortrayals() {
        PSHBEnvironment eState = (PSHBEnvironment)state;
        String bgFileName; //background file name
        try {
            bgFileName = OutputWriter.getFileName("/RESET_PSHB_inputData/RESET_model_UI_background.jpg");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //load the image
        Image img = new ImageIcon(bgFileName).getImage();
        System.out.println("image loaded: " + (img != null));
        //custom background portrayal using the background image
        //Assign portrayal
        backgroundPortrayal.setField(eState.backgroundGrid);
        backgroundPortrayal.setPortrayalForAll(new ScaledImagePortrayal(img));

        //agent portrayal (showing the agents as red dots
        PSHBAgentPortrayal.setField(eState.agentGrid);
        //remove this since it's not used
//        PSHBAgentPortrayal.setPortrayalForClass(PSHBAgent.class, new OvalPortrayal2D(Color.BLACK, 0.2));

        //attach portrayal to display
        display.detachAll();
        display.attach(backgroundPortrayal, "Image Layer", true);
        display.attach(PSHBAgentPortrayal, "PSHB Agent");
        System.out.println("Number of Agents in UI: " + eState.agentGrid.allObjects.numObjs);

        display.setClipping(false);
        display.setScale(0.2); //or smaller if zoomed out
        this.display.setBackdrop(Color.WHITE); //set the color of the background
        //reschedule the display
        this.display.reset();
        this.display.repaint();
    }


    public static void main(String[] args) {
        PSHBWorldWithUI worldGUI = new PSHBWorldWithUI();
        Console console = new Console(worldGUI);
        console.setVisible(true);
    }


}
