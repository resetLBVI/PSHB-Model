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
import sim.util.gui.SimpleColorMap;

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
    FastValueGridPortrayal2D tempPortrayal = new FastValueGridPortrayal2D("Temperature Grid");
//    SparseGridPortrayal2D backgroundPortrayal = new SparseGridPortrayal2D();
    SparseGridPortrayal2D PSHBAgentPortrayal = new SparseGridPortrayal2D() {
        @Override
        public SimplePortrayal2D getPortrayalForObject(Object obj) {
            PSHBEnvironment eState = (PSHBEnvironment)state;
            Int2D loc = eState.agentDevelopGrid.getObjectLocation(obj);
            return new OvalPortrayal2D(Color.BLACK, 0.8);
        }
    };

    //Constructor
    public PSHBWorldWithUI(SimState state) { super(state);}
    public PSHBWorldWithUI(){super(new PSHBEnvironment(System.currentTimeMillis()));}

    @Override
    public void load(SimState state) {
        super.load(state);
        setupPortrayals();   // IMPORTANT: rebind tempGrid/agent grid after reset/load
    }

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

    @Override
    public void start() {
        super.start();
        this.setupPortrayals();
    }

    private void setupPortrayals() {
        PSHBEnvironment eState = (PSHBEnvironment) state;

//        // 0) Get load filename
//        String bgFileName;
//        try {
//            bgFileName = OutputWriter.getFileName("/RESET_PSHB_inputData/RESET_model_UI_background-2.jpg", true);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        Path bgPath = Paths.get(bgFileName).toAbsolutePath();
//        System.out.println("[BG] Using external file path: " + bgPath);
//
//        // Verify presence & readability
//        if (!Files.exists(bgPath)) {
//            throw new RuntimeException("[BG] File does not exist: " + bgPath);
//        }
//        if (!Files.isRegularFile(bgPath) || !Files.isReadable(bgPath)) {
//            throw new RuntimeException("[BG] File not readable: " + bgPath);
//        }
//
//        // 1) Load the background image from classpath and fully decode it
//        // Decode image fully (avoid lazy ImageIcon)
//        final BufferedImage bg;
//        try {
//            bg = ImageIO.read(bgPath.toFile());
//            if (bg == null) {
//                throw new IOException("ImageIO.read returned null (unsupported format?)");
//            }
//        } catch (IOException ex) {
//            throw new RuntimeException("[BG] Failed to decode image at " + bgPath, ex);
//        }
//        System.out.println("[BG] Loaded OK: " + bg.getWidth() + "x" + bg.getHeight());
//
//        // 2) Create a 1x1 grid and put a single token object in it
//        int W = eState.agentDisplayGrid.getWidth();
//        int H = eState.agentDisplayGrid.getHeight();
//
//        SparseGrid2D bgGrid = new SparseGrid2D(W, H);
//        Object token = new Object();
//        // Put token at the center so the image expands around it
//        bgGrid.setObjectLocation(token, W / 2, H / 2);
//
//        // 3) Tell the background portrayal about the field AND how to draw that token
//        backgroundPortrayal.setField(bgGrid);
//        // IMPORTANT: scale in ImagePortrayal2D is relative to a *cell*.
//        // Using W makes the image span ~the full width of the grid.
//        backgroundPortrayal.setPortrayalForAll(new ImagePortrayal2D(bg, (double) W));
        // 1) Temperature
        // 1) Temperature background layer (must be same grid used to compute tempGridX/Y)
        // ---- temp grid sanity + min/max ----
        if (eState.tempGrid == null) {
            throw new IllegalStateException("tempGrid is null. Did you create/fill it in PSHBEnvironment.start()?");
        }

        System.out.println("[UI] env=" + System.identityHashCode(eState) +
                " tempGrid=" + System.identityHashCode(eState.tempGrid));

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int w = eState.tempGrid.getWidth();
        int h = eState.tempGrid.getHeight();
        int nanCount = 0;
        int finiteCount = 0;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double v = eState.tempGrid.field[x][y];
                if (!Double.isFinite(v)) { nanCount++; continue; }
                finiteCount++;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }

        System.out.println("[TEMP MINMAX] min=" + min + " max=" + max +
                " finite=" + finiteCount + " nan=" + nanCount + " total=" + (w*h));

        // ---- robust fallback so colormap never goes crazy ----
        if (finiteCount == 0) {
            // nothing valid in the grid yet; pick a safe dummy range
            min = 0.0;
            max = 1.0;
        } else if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max) {
            // degenerate range (all same value) — expand slightly
            double center = min;
            min = center - 1.0;
            max = center + 1.0;
        }

        // ---- bind portrayal ----
        tempPortrayal.setField(eState.tempGrid);
        tempPortrayal.setMap(new SimpleColorMap(min, max, Color.BLACK, Color.WHITE));

        //set a color map so it's visible
        // 4) Agents layer
        PSHBAgentPortrayal.setField(eState.agentDevelopGrid);

        // 5) Attach layers in order: background first, then agents
        display.detachAll();
        display.attach(tempPortrayal, "Temperature", true); // 'true' = behind others
        display.attach(PSHBAgentPortrayal, "PSHB Agents");

        // 6) Misc display tweaks
        display.setClipping(false);
        display.setScale(1.0); // adjust to taste
        display.setBackdrop(Color.WHITE); // backdrop is drawn BEFORE layers; won't cover the image

        // 7) Refresh
        display.reset();
        display.repaint();

        System.out.println("Number of Agents in UI: " + eState.agentDevelopGrid.allObjects.numObjs);
    }


    public static void main(String[] args) {

        PSHBEnvironment env = new PSHBEnvironment(System.currentTimeMillis());
        PSHBWorldWithUI gui = new PSHBWorldWithUI(env);
        Console console = new Console(gui);
        console.setVisible(true);
    }


}
