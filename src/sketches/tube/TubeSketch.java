import java.io.*;
import processing.core.*;

public class TubeSketch extends PointSampleSketch<PVector, Double> {

    static final double DEFAULT_FOV = 120.;
    static final int DEFAULT_SUBSAMPLING = 4;

    double fov;  // Aperture from opposite ends of the display area, in degrees.
    double speed = 1.;

    double v_height = 1.; // Height of an on-off cycle
    int v_offset = 0;
    int h_checks = 4;
    double h_skew = 0;
    double v_asym = .5;
    double h_asym = .5;

    TubeSketch(PApplet app, double fov, int size_px, int subsampling, boolean temporal_jitter) {
        super(app, size_px, subsampling, temporal_jitter);
        this.fov = fov;
    }

    TubeSketch(PApplet app, int size_px, double fov) {
        this(app, fov, size_px, DEFAULT_SUBSAMPLING, true);
    }

    TubeSketch(PApplet app, int size_px, int subsampling, boolean temporal_jitter) {
        this(app, DEFAULT_FOV, size_px, subsampling, temporal_jitter);
    }

    TubeSketch(PApplet app, int size_px) {
        this(app, size_px, DEFAULT_FOV);
    }

    void init() {
        super.init();

        ctrl.registerHandler("jog_a", new InputControl.InputHandler() {
                public void jog(boolean pressed) {
                    boolean forward = pressed;

                    if (Math.abs(speed) > .02) {
                        final double SPEED_INC = 1.01;
                        speed *= (forward == speed > 0 ? SPEED_INC : 1./SPEED_INC);
                    } else {
                        final double SPEED_STEP = .001;
                        speed += (forward ? 1 : -1) * SPEED_STEP;
                    }
                    System.out.println(""+speed);
                }
            });
        ctrl.registerHandler("browse", new InputControl.InputHandler() {
                public void jog(boolean pressed) {
                    h_checks += (pressed ? 1 : -1);
                    System.out.println(h_checks);
                }
            });
        ctrl.registerHandler("jog_b", new InputControl.InputHandler() {
                public void jog(boolean pressed) {
                    boolean forward = pressed;
                    if (Math.abs(h_skew) > .02) {
                        final double SKEW_INC = 1.01;
                        h_skew *= (forward == h_skew > 0 ? SKEW_INC : 1./SKEW_INC);
                    } else {
                        final double SKEW_STEP = .001;
                        h_skew += (forward ? 1 : -1) * SKEW_STEP;
                    }
                }
            });
        ctrl.registerHandler("pitch_a", new InputControl.InputHandler() {
                public void slider(double val) {
                    h_asym = val;
                }
            });
        ctrl.registerHandler("pitch_b", new InputControl.InputHandler() {
                public void slider(double val) {
                    v_asym = val;
                }
            });
        ctrl.registerHandler("pitch_inc_a", new InputControl.InputHandler() {
                public void button(boolean pressed) {
                    if (pressed) {
                        v_offset += 1;
                    }
                }
            });
        ctrl.registerHandler("pitch_dec_a", new InputControl.InputHandler() {
                public void button(boolean pressed) {
                    if (pressed) {
                        v_offset -= 1;
                    }
                }
            });
        ctrl.registerHandler("mixer", new InputControl.InputHandler() {
                public void slider(double val) {
                    double HMIN = .2;
                    double HMAX = 8.;
                    v_height = HMIN * Math.pow(HMAX / HMIN, val);
                }
            });
    }

    double subsamplingBoost(PVector p) {
        return 1. / LayoutUtil.xyToPolar(p).x;
    }

    // Map xy position to uv coordinates on a cylinder.
    PVector toIntermediateRepresentation(PVector p) {
        // This uses a planar projection, although the dome itself will be slightly curved.
        PVector polar = LayoutUtil.xyToPolar(p);
        return LayoutUtil.V(polar.y, 1. / Math.tan(Math.toRadians(.5*fov)) / polar.x);
    }

    Double initialState() {
        return 0.;
    }

    Double updateState(Double pos, double delta_t) {
        return pos + speed * delta_t;
    }

    int samplePoint(PVector uv, double t, double t_jitter) {
        double pos0 = state;
        double pos = pos0 + speed * t_jitter;

        double u_unit = MathUtil.fmod(uv.x / (2*Math.PI), 1.);
        double dist = uv.y + pos;

        return checker(dist, u_unit);
    }

    int checker(double dist, double u_unit) {
        boolean v_on = (v_height > 0 ? MathUtil.fmod(dist / v_height - v_offset * u_unit, 1.) < v_asym : false);
        boolean u_on = (MathUtil.fmod((u_unit + h_skew * dist) * h_checks, 1.) < h_asym);
        boolean chk = u_on ^ v_on;
        return color(MathUtil.fmod(u_unit + dist/10., 1.), .5, chk ? 1 : .05);
    }

}
