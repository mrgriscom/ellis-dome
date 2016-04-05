import processing.core.*;

public class CloudsSketch extends PointSampleSketch<PVector, CloudsState> {

    int mode;

    boolean hud;
    double noiseScale=0.02;

    CloudsSketch(PApplet app, int size_px) {
        super(app, size_px);

        mode = 0;
    }

    CloudsState initialState() {
        return new CloudsState();
    }

    CloudsState updateState(CloudsState state, double delta_t) {
        double t = last_t + delta_t;
        double speed = 0.06;
        double zspeed = 3.;
        double angle = Math.sin(t);
        //double angle = t;

        state.dx += Math.cos(angle) * speed * delta_t;
        state.dy += Math.sin(angle) * speed * delta_t;
        state.dz += (noise(t * 0.014) - 0.5) * zspeed * delta_t;
        return state;
    }

    int samplePoint(PVector p, double t, double t_jitter) {
        switch (mode) {
        case 0:
            return drawCloud(p, t);
        case 1:
            return drawRing(p, t);
        case 2:
            return drawDot(p, t);
        case 3:
            return drawMoire(p, t);
        default:
            throw new RuntimeException();
        }
    }

    void beforeFrame(double t) {
        hud = false;
        if (app.keyPressed) {
            hud = (app.key == 'v' || app.key == 'V');
            if (app.key == 'c') {
                mode = 0;
            }
            if (app.key == 'r') {
                mode = 1;
            }
            if (app.key == 'd') {
                mode = 2;
            }
            if (app.key == 'm') {
                mode = 3;
            }
        }
    }

    void afterFrame(double t) {
        if (hud) {
            // Show the FPS
            int txtSize = 16;
            app.textSize(txtSize);
            app.fill(0, 100, 100);
            app.text((int)app.frameRate + "fps", 2, (txtSize + 2)); 
            app.text("dx " + state.dx, 2, (txtSize + 2) * 2);
            app.text("dy " + state.dy, 2, (txtSize + 2) * 3);
        }
    }

    int drawCloud(PVector p, double t) {
        // Noise patterns are symmetrical around the origin and it looks weird. Move origin to corner.
        p = LayoutUtil.Vadd(p, LayoutUtil.V(1, 1));

        double z = .08 * t;
        double hue = .1 * t;
        double scale = .75;
        
        double n = fractalNoise(state.dx + p.x*scale, state.dy + p.y*scale, z) - 0.75;
        double m = fractalNoise(state.dx + p.x*scale, state.dy + p.y*scale, z + 10.0) - 0.75;
                    
        return color(
                     MathUtil.fmod(hue + .8 * m, 1.), 
                     1. - constrain(Math.pow(3.0 * n, 3.5), 0, 0.9), 
                     constrain(Math.pow(3.0 * n, 1.5), 0, 0.9)
                     );
    }

    int drawRing(PVector p, double t) {
        // Noise patterns are symmetrical around the origin and it looks weird. Move origin to corner.
        p = LayoutUtil.Vadd(p, LayoutUtil.V(1, 1));

        double z = .08 * t;
        double hue = .1 * t;
        double scale = .75;
        
        double saturation = constrain(Math.pow(1.15 * noise(t * 0.122), 2.5), 0, 1);
        double spacing = noise(t * 0.124) * 15.;
        
        double centerx = noise(t *  0.125) * 2.5;
        double centery = noise(t * -0.125) * 2.5;
        
        double dist = Math.sqrt(Math.pow(p.x - centerx, 2) + Math.pow(p.y - centery, 2));
        double pulse = (Math.sin(state.dz + dist * spacing) - 0.3) * 0.3;
                    
        double n = fractalNoise(state.dx + p.x*scale + pulse, state.dy + p.y*scale, z) - 0.75;
        double m = fractalNoise(state.dx + p.x*scale, state.dy + p.y*scale, z + 10.0) - 0.75;
                    
        return color(
                     MathUtil.fmod(hue + .4 * m, 1.), 
                     saturation,
                     constrain(Math.pow(3.0 * n, 1.5), 0, 0.9)
                     );
    }

    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double cyclicValue(double x, double period) {
        return .5*(Math.cos(x / period * 2*Math.PI) + 1.);
    }

    int drawDot(PVector p, double t) {
        double minRadius = .2;
        double maxRadius = .5;
        double pulsePeriod = 1.5;  //s
        double radialPeriod = 20;  //s
        // Make this relatively prime with radialPeriod to ensure the dot doesn't fall into well-worn tracks.
        double rotPeriod = 7.3854;  //s
        double hue = 0.;
        double sat = 0.;

        double radius = minRadius + (maxRadius - minRadius) * cyclicValue(t, pulsePeriod);
        PVector center = LayoutUtil.Vrot(LayoutUtil.V(cyclicValue(t, radialPeriod), 0), t/rotPeriod * 2*Math.PI);
        double dist = LayoutUtil.Vsub(p, center).mag();
        double k = (dist > radius ? 0. : cyclicValue(dist, 2*radius));

        return color(hue, sat, k);
    }


    // Return a value from 1 to 0 and back gain as x moves from 0 to 'period'
    double moireCyclicValue(double x, double period) {
      
        double val = (Math.exp(Math.sin(x*x/2000.0*Math.PI)) - 0.36787944)*108.0;
      
        double variance = 0.001;
        
      
        return (variance*val);//(Math.cos(x / period * 2*Math.PI)));
    }

    int drawMoire(PVector p, double t) {
        double minRadius = .2;
        double maxRadius = .5;
        double radialPeriod = 20;  //s
        double rotPeriod = 7.3854;  //s
        double hue = 0.1*t;
        double sat = 0.1*t;
        double z = 1 ;
        

       // double scale = .75;
       // double n = fractalNoise(state.dx + p.x*scale, state.dy + p.y*scale, z) - 0.75;

        double radius =     minRadius  *  moireCyclicValue(t, rotPeriod )  ;//+ n*0.0001*Math.sin(t);
                        
        PVector center = LayoutUtil.Vrot(LayoutUtil.V(cyclicValue(t, radialPeriod), 0), t/rotPeriod * 2*Math.PI);
        double dist = LayoutUtil.Vsub(p, center).mag();
        double k = cyclicValue(dist, radius);

        return color(hue, sat, k);
    }


    double fractalNoise(double x, double y, double z) {
        double r = 0;
        double amp = 1.0;
        for (int octave = 0; octave < 4; octave++) {
            r += app.noise((float)x, (float)y, (float)z) * amp;
            amp /= 2;
            x *= 2;
            y *= 2;
            z *= 2;
        }
        return r;
    }

    double noise(double x) {
        return app.noise((float)x);
    }

    double constrain(double x, double min, double max) {
        return app.constrain((float)x, (float)min, (float)max);
    }

}

