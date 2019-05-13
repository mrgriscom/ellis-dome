package me.lsdo.sketches.headless.kaleidoscope;

import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import me.lsdo.processing.geometry.dome.DomeLayoutUtil;

// A triangular kaleidoscope effect that works in XY-space and is not dependent on any particular
// dome panel geometry or layout.

// TODO: be able to feed in other animations as input?

public abstract class KaleidoscopeBase extends XYAnimation {

    // relative speed of effect
    double speed;
    // sizing of triangular segments
    // 1. == 6 triangles on the 24-panel dome
    // 2. == each panel is a triangle on the 24-panel dome
    // 3.2 == nice default size for prometheus
    double scale;
    double source_scale;
    
    public KaleidoscopeBase(PixelMesh<? extends LedPixel> mesh) {
	this(mesh, XYAnimation.DEFAULT_BASE_SUBSAMPLING);
    }
    
    public KaleidoscopeBase(PixelMesh<? extends LedPixel> mesh, int defaultSubsampling) {
	super(mesh, defaultSubsampling);
	speed = Config.getSketchProperty("speed", 1.);
	scale = Config.getSketchProperty("scale", 1.);
	source_scale = Config.getSketchProperty("source_scale", 1.);
    }
    
    @Override
    protected int samplePoint(PVector2 p, double t) {
	t *= speed;

	// spin the kaleidoscope and wander around the canvas
	// TODO: should be more parameterizeable; also, this tends to get pretty crazy pretty quickly
	// NOTE: no canvas wraparound performed
        p = LayoutUtil.Vrot(p, t * (.5 + 3*.5*(Math.cos(.1213*t)+1)));
        p = LayoutUtil.Vmult(p, 1/(1 + 5*.5*(Math.cos(.3025*t)+1)));
        p = LayoutUtil.Vadd(p, LayoutUtil.V(2*Math.cos(.2*t), 0));

	p = LayoutUtil.Vmult(p, source_scale);
	return sampleBaseAnimation(p, t);
    }

    protected abstract int sampleBaseAnimation(PVector2 p, double t);

    // map xy pixels to their position on the 'base' triangle frame, which will then be
    // reflected/rotated to create the kaleidoscope effect
    @Override
    protected PVector2 toIntermediateRepresentation(PVector2 p) {
	p = LayoutUtil.Vmult(p, scale);
	
	PVector2 axial = DomeLayoutUtil.xyToAxial(p);

	int rowU = (int)Math.floor(axial.x);
	int rowV = (int)Math.floor(axial.y);
	int rowW = (int)Math.floor(-(axial.x + axial.y + 1e-6));
	
	boolean flip = MathUtil.mod(rowU + rowV + rowW, 2) == 0; 

	axial = LayoutUtil.V(MathUtil.fmod(axial.x, 1.), MathUtil.fmod(axial.y, 1.));
	axial = LayoutUtil.Vsub(axial, !flip ? LayoutUtil.V(1/3.,1/3.) : LayoutUtil.V(2/3.,2/3.));
	p = DomeLayoutUtil.axialToXy(axial);

	int pos = MathUtil.mod(rowU - rowV, 3);
	int rot = MathUtil.mod(!flip ? 2*pos : 1-2*pos, 6);
	p = LayoutUtil.Vrot(p, -rot*Math.PI/3);
	if (flip) {
	    p = LayoutUtil.V(-p.x, p.y);
	}
	p = LayoutUtil.Vadd(p, DomeLayoutUtil.axialToXy(LayoutUtil.V(1/3.,1/3.)));

	return p;
    }
    
    /**
     * original pixel-based logic
    public int drawPixel(DomePixel c, double t) {
        int pos = MathUtil.mod(c.panel.u - c.panel.v, 3);
        int rot = MathUtil.mod(c.panel.getOrientation() == TriCoord.PanelOrientation.A ? 2*pos : 1-2*pos, 6);
        boolean flip = (MathUtil.mod(rot, 2) == 1);
        TriCoord basePx = c.pixel.rotate(rot);
        if (flip) {
            basePx = basePx.flip(TriCoord.Axis.U);
        }

        return mesh.getColor(new DomePixel(basePanel, basePx));
    }
    */
}
