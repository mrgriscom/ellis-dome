package me.lsdo.sketches.headless;

import me.lsdo.processing.*;
import me.lsdo.processing.util.*;

/**
 * Created by shen on 2016/09/17.
 */
public class Moire extends XYAnimation {

    public Moire(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);
    }

    @Override
    protected int samplePoint(PVector2 p, double t) {
        double minRadius = .2;
        double rotPeriod = 7.3854;
        double hue = 0.1f*t % 1;
	// cycle sat 0 to 1 and back again; use period relatively prime with hue
        double sat = Math.abs(2*(0.1265f*t % 1) - 1);
	// scale to min sat > 0
	sat = .3 * sat + 1. * (1-sat);
	
        double radius = minRadius  *  moireCyclicValue(t, rotPeriod ) ;

        PVector2 center = LayoutUtil.Vrot(LayoutUtil.V(0, 0), 1);
        double dist = LayoutUtil.Vsub(p, center).mag();
        double k = cyclicValue(dist, radius);

        return OpcColor.getHsbColor(hue, sat, k);
    }

    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double moireCyclicValue(double x, double period) {
        double val = (Math.exp(Math.sin(x*x/2000.0*Math.PI)) - 0.36787944)*108.0;
        double variance = 0.001;

        return (variance*val);
    }

    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double cyclicValue(double x, double period) {
        return .5*(Math.cos(x / period * 2*Math.PI) + 1.);
    }
}
