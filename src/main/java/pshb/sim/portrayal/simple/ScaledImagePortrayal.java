package pshb.sim.portrayal.simple;

import sim.portrayal.DrawInfo2D;
import sim.portrayal.SimplePortrayal2D;

import java.awt.*;

public class ScaledImagePortrayal extends SimplePortrayal2D {
    private static final long serialVersionUID = 1L;
    private final Image image;

    public ScaledImagePortrayal(Image image) {
        this.image = image;
    }

    public void draw(Object object, Graphics2D graphics, DrawInfo2D info) {
        if(image == null) return;
        //Draw image caled to fill the full visible region (i.e., Display2D panel)
        //info.clip holds the current viewport
        Rectangle clip = graphics.getClipBounds();
        graphics.drawImage(image, clip.x, clip.y, clip.width, clip.height, null);
    }

    public boolean hitObject(Object object, DrawInfo2D info) {  return false;   }

 }
