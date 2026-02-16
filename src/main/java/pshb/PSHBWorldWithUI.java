package pshb;

// Java
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

// MASON
import sim.field.grid.SparseGrid2D;
import sim.portrayal.grid.FastValueGridPortrayal2D;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.portrayal.simple.ImagePortrayal2D;

// (optional, depending on your class)
import sim.display.Display2D;      // if you reference Display2D

import pshb.Utils.OutputWriter;
import sim.display.Console;
import sim.display.Controller;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.SimplePortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;


import org.geotools.coverage.grid.io.imageio.MaskOverviewProvider;
import sim.util.Int2D;

import javax.imageio.spi.IIORegistry;

import javax.swing.*;

/* #######################################################################################
 * This class includes:
 * (1) getName (2) getSimulationInspectedObject (3) init (4) quit (5) setupPortrayals
 * (6) start (7) main
 * #######################################################################################
 */

public class PSHBWorldWithUI extends GUIState {
    static {
        // Set early system properties (you already did this)
        System.setProperty("org.geotools.coverage.grid.io.imageio.mask.overviews.enabled", "false");

        // Force SPI cleanup — unregister the faulty provider
        try {
            IIORegistry registry = IIORegistry.getDefaultInstance();
            registry.deregisterServiceProvider(MaskOverviewProvider.class);
            System.out.println("✅ MaskOverviewProvider unregistered");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    Display2D display; //create a display
    JFrame displayFrame; //create a display frame
//    FastValueGridPortrayal2D temperatureGridPortrayal2D = new FastValueGridPortrayal2D("temperature grid");
    SparseGridPortrayal2D backgroundPortrayal = new SparseGridPortrayal2D();
    SparseGridPortrayal2D PSHBAgentPortrayal = new SparseGridPortrayal2D() {
        @Override
        public SimplePortrayal2D getPortrayalForObject(Object obj) {
            PSHBEnvironment eState = (PSHBEnvironment)state;
            Int2D loc = eState.agentDisplayGrid.getObjectLocation(obj);
            return new OvalPortrayal2D(Color.BLACK, 0.8);
        }
    };

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
        this.display = new Display2D(800, 800, this); //initially create a UI display
        this.displayFrame = this.display.createFrame(); //create a display frame
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
        PSHBEnvironment eState = (PSHBEnvironment) state;

        // 0) Get load filename
        String bgFileName;
        try {
            bgFileName = OutputWriter.getFileName("/RESET_PSHB_inputData/RESET_model_UI_background.jpg", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path bgPath = Paths.get(bgFileName).toAbsolutePath();
        System.out.println("[BG] Using external file path: " + bgPath);

        // Verify presence & readability
        if (!Files.exists(bgPath)) {
            throw new RuntimeException("[BG] File does not exist: " + bgPath);
        }
        if (!Files.isRegularFile(bgPath) || !Files.isReadable(bgPath)) {
            throw new RuntimeException("[BG] File not readable: " + bgPath);
        }

        // 1) Load the background image from classpath and fully decode it
        // Decode image fully (avoid lazy ImageIcon)
        final BufferedImage bg;
        try {
            bg = ImageIO.read(bgPath.toFile());
            if (bg == null) {
                throw new IOException("ImageIO.read returned null (unsupported format?)");
            }
        } catch (IOException ex) {
            throw new RuntimeException("[BG] Failed to decode image at " + bgPath, ex);
        }
        System.out.println("[BG] Loaded OK: " + bg.getWidth() + "x" + bg.getHeight());

        // 2) Create a 1x1 grid and put a single token object in it
        int W = eState.agentDisplayGrid.getWidth();
        int H = eState.agentDisplayGrid.getHeight();

        SparseGrid2D bgGrid = new SparseGrid2D(W, H);
        Object token = new Object();
        // Put token at the center so the image expands around it
        bgGrid.setObjectLocation(token, W / 2, H / 2);

        // 3) Tell the background portrayal about the field AND how to draw that token
        backgroundPortrayal.setField(bgGrid);
        // IMPORTANT: scale in ImagePortrayal2D is relative to a *cell*.
        // Using W makes the image span ~the full width of the grid.
        backgroundPortrayal.setPortrayalForAll(new ImagePortrayal2D(bg, (double) W));

        // 4) Agents layer
        PSHBAgentPortrayal.setField(eState.agentDisplayGrid);

        // 5) Attach layers in order: background first, then agents
        display.detachAll();
        display.attach(backgroundPortrayal, "Image Layer", true); // 'true' = behind others
        display.attach(PSHBAgentPortrayal, "PSHB Agents");

        // 6) Misc display tweaks
        display.setClipping(false);
        display.setScale(1.0); // adjust to taste
        display.setBackdrop(Color.WHITE); // backdrop is drawn BEFORE layers; won't cover the image

        // 7) Refresh
        display.reset();
        display.repaint();

        System.out.println("Number of Agents in UI: " + eState.agentDisplayGrid.allObjects.numObjs);
    }


    public static void main(String[] args) {

        PSHBWorldWithUI worldGUI = new PSHBWorldWithUI();
        Console console = new Console(worldGUI);
        console.setVisible(true);
    }


}
