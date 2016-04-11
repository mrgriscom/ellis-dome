import java.util.*;
import processing.core.*;

public class VomitComet extends KaleidoscopeSketch {

    public VomitComet(PApplet app, int size_px) {
        super(app, size_px);
    }

    int getBasePixel(TriCoord c, double t) {
        PVector p = xyForBasePixel(c);
        p = LayoutUtil.Vrot(p, t * (.5 + 3*.5*(Math.cos(.1213*t)+1)));
        p = LayoutUtil.Vmult(p, 1/(1 + 5*.5*(Math.cos(.3025*t)+1)));
        p = LayoutUtil.Vadd(p, LayoutUtil.V(2*Math.cos(.2*t), 0));
        return color(MathUtil.fmod(p.x + .4081*t, 1.), .6, .5*(Math.cos(40*p.x)+1));
    }

}