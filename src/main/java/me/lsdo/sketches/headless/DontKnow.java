package me.lsdo.sketches.headless;

import me.lsdo.sketches.util.*;
import me.lsdo.processing.*;

/**
 * Created by shen on 2016/09/17.
 */
public class DontKnow extends XYAnimation {

    private double dx;
    private double dy;

    private double last_t;

    Perlin perlin;

    public DontKnow(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);

        dx = dy = 0;

        perlin = new Perlin();
    }

    @Override
    protected void preFrame(double t, double deltaT) {
        double delta_t = t - last_t;
        last_t = t;

        double speed = 0.06;
        double angle = Math.sin(t);

        dx += Math.cos(angle) * speed * delta_t;
        dy += Math.sin(angle) * speed * delta_t;

    }

    @Override
    protected int samplePoint(PVector2 p, double t) {
        double minRadius = .2;
        double rotPeriod = 7.3854;
        double hue = 0.1 * t;


        double scale = .9;
        double z = scale;
        double n = fractalNoise(dx + p.x * scale, dy + p.y * scale, z);


        PVector2 center = LayoutUtil.Vrot(LayoutUtil.V(1, 0), 0);
        double dist = LayoutUtil.Vsub(p, center).mag();

        double sqr = Math.pow(n, 2);
        return OpcColor.getHsbColor(
                constrain(MathUtil.fmod(hue + n, 0.9), 0, 1),
                constrain(hue * (1. - constrain(sqr, 0, 0.9)), 0, 1),
                constrain(cyclicValue(dist, hue * (1 - constrain(sqr, 0, 0.9))), 0, 1));
    }

    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double moireCyclicValue(double x, double period) {
        double val = (Math.exp(Math.sin(x * x / 2000.0 * Math.PI)) - 0.36787944) * 108.0;
        double variance = 0.001;

        return (variance * val);
    }

    private double fractalNoise(double x, double y, double z) {
        double r = 0;
        double amp = 1.0;
        for (int octave = 0; octave < 4; octave++) {
            r += perlin.noise((float) x, (float) y, (float) z) * amp;
            amp /= 2;
            x *= 2;
            y *= 2;
            z *= 2;
        }
        return r;
    }

    private double noise(double x) {
        return perlin.noise((float) x);
    }


    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double cyclicValue(double x, double period) {
        return .5 * (Math.cos(x / period * 2 * Math.PI) + 1.);
    }

    private double constrain(double x, double lower, double upper) {
        if (x < lower)
            return lower;
        if (x > upper)
            return upper;
        return x;
    }

}

