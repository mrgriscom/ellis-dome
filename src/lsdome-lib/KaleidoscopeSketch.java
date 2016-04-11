// Sketch template for 'kaleidoscopes', where you render just one panel's worth of pixels and
// they are mirrored to all the other panels.

import java.util.*;
import processing.core.*;

public abstract class KaleidoscopeSketch extends PixelGridSketch<Object> {

    static final TriCoord basePanel = new TriCoord(CoordType.PANEL, 0, 0, -1);;

    KaleidoscopeSketch(PApplet app, int size_px) {
        super(app, size_px);
    }

    void beforeFrame(double t) {
        for (DomeCoord c : coords) {
            if (c.panel.equals(basePanel)) {
                pixelColors.put(c, getBasePixel(c.pixel, t));
            }
        }
    }
    
    // **OVERRIDE** this to set the color of a pixel. It will be called for all the pixels
    // in one panel and then mirrored to the other panels. xyForBasePixel() could be helpful
    // to mapped the supplied pixel to an xy point.
    abstract int getBasePixel(TriCoord c, double t);
    
    int drawPixel(DomeCoord c, double t) {
        int pos = MathUtil.mod(c.panel.u - c.panel.v, 3);
        int rot = MathUtil.mod(c.panel.getOrientation() == PanelOrientation.A ? 2*pos : 1-2*pos, 6);
        boolean flip = (MathUtil.mod(rot, 2) == 1);
        TriCoord basePx = c.pixel.rotate(rot);
        if (flip) {
            basePx = basePx.flip(Axis.U);
        }
        return pixelColors.get(new DomeCoord(basePanel, basePx));
    }

    DomeCoord baseDomeCoord(TriCoord c) {
        return new DomeCoord(basePanel, c);
    }

    PVector xyForBasePixel(TriCoord c) {
        return points.get(baseDomeCoord(c));
    }

}