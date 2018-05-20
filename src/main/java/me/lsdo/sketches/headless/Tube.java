package me.lsdo.sketches.headless;

import java.io.*;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;

public class Tube extends XYAnimation {

    static final double DEFAULT_FOV = 120.;
    static final int DEFAULT_SUBSAMPLING = 4;

    double fov;  // Aperture from opposite ends of the display area, in degrees.

    // State variables for motion through the tube
    double pos = 0;
    NumericParameter speed = new NumericParameter() {
	    @Override
	    public void step(boolean forward) {
		if (Math.abs(value) > .02) {
		    stepLog(forward == value > 0, sensitivity);
		} else {
		    stepLinear(forward, .1 * sensitivity);
		}
	    }

	    @Override
	    public void onChange(double prev) {
		System.out.println("speed: " + value);
	    }
	};
    NumericParameter speedSensitivity = new NumericParameter() {
	    @Override
	    public void onChange(double prev) {
		speed.setSensitivity(value);
		System.out.println("speed sensitivity: " + value);
	    }
	};

    // State variables for appearance of checker pattern
    // Height of an on-off cycle
    class VertHeightParameter extends NumericParameter {
	    double baseline = 0;
	    
	    @Override
	    public void onChange(double prev) {
		final double REL_BASELINE = 1.;
		if (!vheight_warp_mode) {
		    // This is actually the correct formula, however:
		    baseline += (pos + REL_BASELINE - baseline) * (1 - value / prev);
		} else {
		    // this typo creates the cool hypnosis/warp effect.
		    baseline += (pos + REL_BASELINE - baseline) * (value / prev);
		}

		System.out.println("v-height: " + value);
	    }
	};
    VertHeightParameter vHeight = new VertHeightParameter();
    
    int v_offset = 0;
    int h_checks = 4;
    double h_skew = 0;
    double v_asym = .5;
    double h_asym = .5;

    double h_skew_baseline = 0;

    double hskew_sensitivity = 1.;
    double hskew_sensitivity_incr = 2.;

    boolean vheight_warp_mode = true;
    
    public Tube(PixelMesh<? extends LedPixel> mesh) {
        this(mesh, DEFAULT_SUBSAMPLING, DEFAULT_FOV);
    }

    public Tube(PixelMesh<? extends LedPixel> mesh, int base_subsampling, double fov) {
        super(mesh, base_subsampling);
	this.fov = fov;

	speed.init(1.);
	
	speedSensitivity.scale = NumericParameter.Scale.LOG;
	speedSensitivity.init(.01);
	speedSensitivity.setSensitivity(.05);

	vHeight.min = .2;
	vHeight.max = 8.;
	vHeight.scale = NumericParameter.Scale.LOG;
	vHeight.init(1.);
	
    }

    public void registerHandlers(InputControl ctrl) {
	super.registerHandlers(ctrl);

	// speed, jog_a, jog
        ctrl.registerHandler("jog_a", new InputControl.InputHandler() {
		@Override
                public void jog(boolean pressed) {
		    speed.step(pressed);
                }
            });
	// # h-checks, browse, jog
        ctrl.registerHandler("browse", new InputControl.InputHandler() {
		@Override
                public void jog(boolean pressed) {
                    h_checks += (pressed ? 1 : -1);
                    System.out.println("h-checks: " + h_checks);
                }
            });
	// h-skew, jog_b, jog
        ctrl.registerHandler("jog_b", new InputControl.InputHandler() {
		@Override
                public void jog(boolean pressed) {
                    boolean forward = pressed;
		    double h_skew_prev = h_skew;
		    final double SKEW_STEP = .001 * hskew_sensitivity;
		    h_skew += (forward ? 1 : -1) * SKEW_STEP;
		    final double REL_BASELINE = 1;
		    h_skew_baseline += (pos + REL_BASELINE) * (h_skew_prev - h_skew);
                    System.out.println("h-skew: " + h_skew);
                }
            });
	// h-asym, pitch_a, slider
        ctrl.registerHandler("pitch_a", new InputControl.InputHandler() {
		@Override
                public void slider(double val) {
                    h_asym = val;
		    System.out.println("h-asym: " + h_asym);
                }
            });
	// v-asym, pitch_b, slider
        ctrl.registerHandler("pitch_b", new InputControl.InputHandler() {
		@Override
                public void slider(double val) {
                    v_asym = val;
		    System.out.println("v-asym: " + v_asym);		    
                }
            });
	// v-offset, pitch_inc/dec_a, slider (but should be jog)
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
	// v-height, mixer, slider
        ctrl.registerHandler("mixer", new InputControl.InputHandler() {
		@Override
                public void slider(double val) {
		    vHeight.setSlider(val);
                }
            });
	// reverse, playpause_a, button (press)
        ctrl.registerHandler("playpause_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			speed.set(-speed.get());
                    }
                }
            });
	// speed sensitivity, headphone/sync_a, buttons (should be jog)
        ctrl.registerHandler("headphone_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			speedSensitivity.increment(10);
                    }
                }
            });
        ctrl.registerHandler("sync_a", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
                    if (pressed) {
			speedSensitivity.increment(-10);
                    }
                }
            });
	// hskew sensitivity, headphone/sync_b, buttons (should be jog)
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
	// warp mode, playpause_b, button (hold -- need to be in tandem with v-height)
        ctrl.registerHandler("playpause_b", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
		    vheight_warp_mode = !pressed;
                }
            });
	// reset, back, button (press)
        ctrl.registerHandler("back", new InputControl.InputHandler() {
		@Override
                public void button(boolean pressed) {
		    if (pressed) {
			// reset
			speed.reset();
			speedSensitivity.reset();
			v_offset = 0;
			h_checks = 4;
			h_skew = 0;
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
        // This uses a planar projection, although the mesh itself is likely not totally flat.
        PVector2 polar = LayoutUtil.xyToPolar(p);
        return LayoutUtil.V(polar.y, 1. / Math.tan(Math.toRadians(.5*fov)) / polar.x);
    }

    @Override
    protected void preFrame(double t, double deltaT){
        pos += speed.get() * deltaT;
    }

    @Override
    protected int samplePointWithMotionBlur(PVector2 uv, double t, double jitterT) {
        double samplePos = this.pos + speed.get() * jitterT;

        double u_unit = MathUtil.fmod(uv.x / (2*Math.PI), 1.);
        double dist = uv.y + samplePos;

        return checker(dist, u_unit);
    }

    int checker(double dist, double u_unit) {
        boolean v_on = (vHeight.get() > 0 ? MathUtil.fmod((dist - vHeight.baseline) / vHeight.get() - v_offset * u_unit, 1.) < v_asym : false);
        boolean u_on = (MathUtil.fmod((u_unit + h_skew_baseline + h_skew * dist) * h_checks, 1.) < h_asym);
        boolean chk = u_on ^ v_on;
        return OpcColor.getHsbColor(MathUtil.fmod(u_unit + dist/10., 1.), .5, chk ? 1 : .05);
    }

}
