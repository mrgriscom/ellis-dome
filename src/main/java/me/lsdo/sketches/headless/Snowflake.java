package me.lsdo.sketches.headless;

import me.lsdo.sketches.util.*;
import me.lsdo.processing.*;

/**
 * Created by shen on 2016/09/17.
 */
public class Snowflake extends XYAnimation{

    double oldradius = 0;
    Perlin perlin;

    public Snowflake(PixelMesh<? extends LedPixel> dome) {
        super(dome);
        perlin = new Perlin();
    }

    @Override
    protected int samplePoint(PVector2 p, double t) {
            double minRadius = .2;
            double rotPeriod = 7.3854;  //s
            double hue = 0.1*t;


            double radius = minRadius  *  snowflakeCyclicValue(t, rotPeriod ) ;

            if (oldradius <= 0)
            {
                oldradius = radius;
            }

            if (oldradius != 0)
            {
                radius = (radius + oldradius) / 2;
                oldradius = radius;

            }

            PVector2 center = LayoutUtil.Vrot(LayoutUtil.V(0, 0), 1);
            double dist = LayoutUtil.Vsub(p, center).mag();


            double k = constrain(hue, 0, 255);
            double n = cyclicValue(dist, radius) + 0.1*moireCyclicValue(dist, radius);
            k -= n;


            double saturation = constrain(Math.pow(1.15 * perlin.noise((float)(t * 0.122)), 2.5), 0, 1);


            return OpcColor.getHsbColor(MathUtil.fmod(saturation, 0.9), saturation, MathUtil.fmod(k, 0.9));

        }

    double snowflakeCyclicValue(double x, double period) {
        double val = (Math.exp(Math.sin(x/2000.0*Math.PI)))*10.0;
        double variance = 0.01;

        return (variance*val);
    }

    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double cyclicValue(double x, double period) {
        return .5*(Math.cos(x / period * 2*Math.PI) + 1.);
    }

    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double moireCyclicValue(double x, double period) {
        double val = (Math.exp(Math.sin(x*x/2000.0*Math.PI)) - 0.36787944)*108.0;
        double variance = 0.001;

        return (variance*val);
    }

    private double constrain(double x, double lower, double upper) {
        if (x < lower)
            return lower;
        if (x > upper)
            return upper;
        return x;
    }
}
