package me.lsdo.sketches.headless;

import java.io.*;
import me.lsdo.processing.*;
import me.lsdo.processing.interactivity.*;
import me.lsdo.processing.util.*;

public class Tube extends XYAnimation {

    static final double DEFAULT_FOV = 120.;
    static final int DEFAULT_SUBSAMPLING = 4;

    double fov;  // Aperture from opposite ends of the display area, in degrees.

    class SensitivityParameter extends NumericParameter {
	NumericParameter param;
	
	public SensitivityParameter(String name, String category, NumericParameter param) {
	    super(name, category);
	    this.param = param;
	    this.scale = NumericParameter.Scale.LOG;
	}

	@Override
	public void onSet() {
	    param.setSensitivity(get());
	}
    }    
    abstract class RelativeChangeNumericParameter extends NumericParameter {
	final double REL_BASELINE = 1;
	double baseline = 0;

	public RelativeChangeNumericParameter(String name, String category) {
	    super(name, category);
	}

	@Override
	public void onChange(Double prev) {
	    baseline += baselineAdjustment(prev);
	}

	abstract double baselineAdjustment(double prev);
    }    
    
    // State variables for motion through the tube
    double pos = 0;
    NumericParameter speed;
    SensitivityParameter speedSensitivity;

    // State variables for appearance of checker pattern
    // Height of an on-off cycle
    RelativeChangeNumericParameter vHeight;
    NumericParameter.Integer vOffset;
    NumericParameter.Integer hChecks;
    RelativeChangeNumericParameter hSkew;
    SensitivityParameter hSkewSensitivity;
    NumericParameter vAsym;
    NumericParameter hAsym;

    BooleanParameter vHeightWarpMode;
    BooleanParameter reverseAction;
    BooleanParameter resetAction;
    
    public Tube(PixelMesh<? extends LedPixel> mesh) {
        this(mesh, DEFAULT_SUBSAMPLING, DEFAULT_FOV);
    }

    public Tube(PixelMesh<? extends LedPixel> mesh, int base_subsampling, double fov) {
        super(mesh, base_subsampling);
	this.fov = fov;

	speed = new NumericParameter("speed", "animation") {
		@Override
		public void _step(boolean forward, double sens) {
		    if (Math.abs(get()) > .02) {
			stepLog(forward == get() > 0, sens);
		    } else {
			stepLinear(forward, .1 * sens);
		    }
		}
	    };
	speed.verbose = true;
	speedSensitivity = new SensitivityParameter("speed sensitivity", "animation", speed);
	speedSensitivity.verbose = true;
	speedSensitivity.setSensitivity(.05);
	speed.init(1.);
	speedSensitivity.init(.01);
	
	vHeight = new RelativeChangeNumericParameter("v-height", "animation") {
	    @Override
	    double baselineAdjustment(double prev) {
		if (!vHeightWarpMode.get()) {
		    // This is actually the correct formula, however:
		    return (pos + REL_BASELINE - baseline) * (1 - get() / prev);
		} else {
		    // this typo creates the cool hypnosis/warp effect.
		    return (pos + REL_BASELINE - baseline) * (get() / prev);
		}
	    }
	};
	vHeight.verbose = true;
	vHeight.min = .2;
	vHeight.max = 8.;
	vHeight.scale = NumericParameter.Scale.LOG;
	vHeight.init(1.);

	vOffset = new NumericParameter.Integer("v-offset", "animation");
	vOffset.verbose = true;
	vOffset.init(0);

	hChecks = new NumericParameter.Integer("h-checks", "animation");
	hChecks.verbose = true;
	hChecks.init(4);
	
	hSkew = new RelativeChangeNumericParameter("h-skew", "animation") {
	    @Override
	    double baselineAdjustment(double prev) {
		return (pos + REL_BASELINE) * (prev - get());
	    }
	};
	hSkew.verbose = true;
	hSkewSensitivity = new SensitivityParameter("h-skew sensitivity", "animation", hSkew);
	hSkewSensitivity.verbose = true;
	hSkewSensitivity.setSensitivity(.05);
	hSkew.init(0.);
	hSkewSensitivity.init(.001);

	vAsym = new NumericParameter("v-asym", "animation");
	vAsym.verbose = true;
	vAsym.min = 0.;
	vAsym.max = 1.;
	vAsym.init(.5);
	hAsym = new NumericParameter("h-asym", "animation");
	hAsym.verbose = true;
	hAsym.min = 0.;
	hAsym.max = 1.;
	hAsym.init(.5);

	vHeightWarpMode = new BooleanParameter("v-height warp", "animation");
	vHeightWarpMode.invertPress = true;
	vHeightWarpMode.verbose = true;
	vHeightWarpMode.init(true);

	reverseAction = new BooleanParameter("reverse", "animation") {
		@Override
		public void onTrue() {
		    speed.set(-speed.get());
		}
	    };
	reverseAction.affinity = BooleanParameter.Affinity.ACTION;
	reverseAction.init(false);

	resetAction = new BooleanParameter("reset", "animation") {
		@Override
		public void onTrue() {
		    speed.reset();
		    speedSensitivity.reset();
		    vOffset.reset();
		    hChecks.reset();
		    hSkew.reset();
		    hSkewSensitivity.reset();
		}
	    };
	resetAction.affinity = BooleanParameter.Affinity.ACTION;
	resetAction.init(false);
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
        boolean v_on = (vHeight.get() > 0 ? MathUtil.fmod((dist - vHeight.baseline) / vHeight.get() - vOffset.getInternal() * u_unit, 1.) < vAsym.get() : false);
        boolean u_on = (MathUtil.fmod((u_unit + hSkew.baseline + hSkew.get() * dist) * hChecks.getInternal(), 1.) < hAsym.get());
        boolean chk = u_on ^ v_on;
        return OpcColor.getHsbColor(MathUtil.fmod(u_unit + dist/10., 1.), .5, chk ? 1 : .05);
    }

}
