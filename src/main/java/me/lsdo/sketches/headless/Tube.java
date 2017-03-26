package me.lsdo.sketches.headless;

import me.lsdo.util.InputControl;
import java.io.*;
import me.lsdo.processing.*;

public class Tube extends XYAnimation {

    static final double DEFAULT_FOV = 120.;
    static final int DEFAULT_SUBSAMPLING = 4;

    double fov;  // Aperture from opposite ends of the display area, in degrees.

    // State variables for appearance of checker pattern
    double v_height = 1.; // Height of an on-off cycle
    int v_offset = 0;
    int h_checks = 4;
    double h_skew = 0;
    double v_asym = .5;
    double h_asym = .5;

    // State variables for motion through the tube
    double speed = 1.;
    double pos = 0;

    double v_height_baseline = 0;
    double h_skew_baseline = 0;

    double speed_sensitivity = 1.;
    double speed_sensitivity_incr = 2.;
    double hskew_sensitivity = 1.;
    double hskew_sensitivity_incr = 2.;

    boolean vheight_warp_mode = true;
    
    InputControl ctrl;
    
    public Tube(Dome dome, OPC opc) {
        this(dome, opc, DEFAULT_SUBSAMPLING, DEFAULT_FOV);
    }

    public Tube(Dome dome, OPC opc, int base_subsampling, double fov) {
        super(dome, opc, base_subsampling);
	this.fov = fov;
	initControl();
    }

    private void initControl() {
        ctrl = new InputControl();
	ctrl.init();

        ctrl.registerHandler("jog_a", new InputControl.InputHandler() {
		@Override
                public void jog(boolean pressed) {
                    boolean forward = pressed;

                    if (Math.abs(speed) > .02) {
                        final double SPEED_INC = 1. + (.01 * speed_sensitivity);
                        speed *= (forward == speed > 0 ? SPEED_INC : 1./SPEED_INC);
                    } else {
                        final double SPEED_STEP = .001 * speed_sensitivity;
                        speed += (forward ? 1 : -1) * SPEED_STEP;
                    }
                    System.out.println("speed: " + speed);
                }
            });
        ctrl.registerHandler("browse", new InputControl.InputHandler() {
		@Override
                public void jog(boolean pressed) {
                    h_checks += (pressed ? 1 : -1);
                    System.out.println("h-checks: " + h_checks);
                }
            });
        ctrl.registerHandler("jog_b", new InputControl.InputHandler() {
		@Override
                public void jog(boolean pressed) {
                    boolean forward = pressed;
		    double h_skew_prev = h_skew;
                    if (Math.abs(h_skew) > .02) {
                        final double SKEW_INC = 1. + (.01 * hskew_sensitivity);
                        h_skew *= (forward == h_skew > 0 ? SKEW_INC : 1./SKEW_INC);
                    } else {
                        final double SKEW_STEP = .001 * hskew_sensitivity;
                        h_skew += (forward ? 1 : -1) * SKEW_STEP;
                    }
		    final double REL_BASELINE = 1;
		    h_skew_baseline += (pos + REL_BASELINE) * (h_skew_prev - h_skew);
                    System.out.println("h-skew: " + h_skew);
                }
            });
        ctrl.registerHandler("pitch_a", new InputControl.InputHandler() {
		@Override
                public void slider(double val) {
                    h_asym = val;
		    System.out.println("h-asym: " + h_asym);
                }
            });
        ctrl.registerHandler("pitch_b", new InputControl.InputHandler() {
		@Override
                public void slider(double val) {
                    v_asym = val;
		    System.out.println("v-asym: " + v_asym);		    
                }
            });
        ctrl.registerHandler("pitch_inc_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
                        v_offset += 1;
			System.out.println("v-offset: " + v_offset);
                    }
                }
            });
        ctrl.registerHandler("pitch_dec_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
                        v_offset -= 1;
			System.out.println("v-offset: " + v_offset);
                    }
                }
            });
        ctrl.registerHandler("mixer", new InputControl.InputHandler() {
		@Override
                public void slider(double val) {
                    double HMIN = .2;
                    double HMAX = 8.;
		    double v_height_prev = v_height;
                    v_height = HMIN * Math.pow(HMAX / HMIN, val);

		    final double REL_BASELINE = 1.;
		    if (!vheight_warp_mode) {
		      // This is actually the correct formula, however:
		      v_height_baseline += (pos + REL_BASELINE - v_height_baseline) * (1 - v_height / v_height_prev);
		    } else {
  		      // this typo creates the cool hypnosis/warp effect.
		      v_height_baseline += (pos + REL_BASELINE - v_height_baseline) * (v_height / v_height_prev);
		    }

		    System.out.println("v-height: " + v_height);
                }
            });
        ctrl.registerHandler("playpause_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			speed = -speed;
                    }
                }
            });
        ctrl.registerHandler("headphone_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			speed_sensitivity *= speed_sensitivity_incr;
			System.out.println("speed sensitivity: " + speed_sensitivity);
                    }
                }
            });
        ctrl.registerHandler("sync_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			speed_sensitivity /= speed_sensitivity_incr;
			System.out.println("speed sensitivity: " + speed_sensitivity);
                    }
                }
            });
        ctrl.registerHandler("headphone_b", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			hskew_sensitivity *= hskew_sensitivity_incr;
			System.out.println("h-skew sensitivity: " + hskew_sensitivity);
                    }
                }
            });
        ctrl.registerHandler("sync_b", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			hskew_sensitivity /= hskew_sensitivity_incr;
			System.out.println("h-skew sensitivity: " + hskew_sensitivity);
                    }
                }
            });
        ctrl.registerHandler("playpause_b", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
		    vheight_warp_mode = !pressed;
                }
            });
        ctrl.registerHandler("back", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
		    if (pressed) {
			// reset
			speed = 1.;
			v_offset = 0;
			h_checks = 4;
			h_skew = 0;
			speed_sensitivity = 1;
			hskew_sensitivity = 1;
		    }
                }
            });
    }

    @Override
    protected double subsamplingBoost(PVector2 p) {
        return 1. / LayoutUtil.xyToPolar(p).x;
    }

    // Map xy position to uv coordinates on a cylinder.
    @Override
    protected PVector2 toIntermediateRepresentation(PVector2 p) {
        // This uses a planar projection, although the dome itself will be slightly curved.
        PVector2 polar = LayoutUtil.xyToPolar(p);
        return LayoutUtil.V(polar.y, 1. / Math.tan(Math.toRadians(.5*fov)) / polar.x);
    }

    @Override
    protected void preFrame(double t, double deltaT){
	ctrl.processInput();
	
        pos += speed * deltaT;
    }

    @Override
    protected int samplePointWithMotionBlur(PVector2 uv, double t, double jitterT) {
        double samplePos = this.pos + speed * jitterT;

        double u_unit = MathUtil.fmod(uv.x / (2*Math.PI), 1.);
        double dist = uv.y + samplePos;

        return checker(dist, u_unit);
    }

    int checker(double dist, double u_unit) {
        boolean v_on = (v_height > 0 ? MathUtil.fmod((dist - v_height_baseline) / v_height - v_offset * u_unit, 1.) < v_asym : false);
        boolean u_on = (MathUtil.fmod((u_unit + h_skew_baseline + h_skew * dist) * h_checks, 1.) < h_asym);
        boolean chk = u_on ^ v_on;
        return OpcColor.getHsbColor(MathUtil.fmod(u_unit + dist/10., 1.), .5, chk ? 1 : .05);
    }

}
