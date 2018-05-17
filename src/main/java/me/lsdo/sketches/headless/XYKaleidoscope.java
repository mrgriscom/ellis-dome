package me.lsdo.sketches.headless;

import me.lsdo.processing.*;

// A kaleidoscope that is not dependent on the triangular geometry of the dome; computed in
// abstract XY space so works on any mesh.

// TODO: make this a generic wrapper that can consume any XYAnimation and add a kaleidoscope effect on top

public class XYKaleidoscope extends XYAnimation {

    public XYKaleidoscope(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);
    }
    
    @Override
    protected int samplePoint(PVector2 p, double t) {
	return sampleBaseAnimation(p, t);
    }

    // future: make this abstract
    private int sampleBaseAnimation(PVector2 p, double t) {
        p = LayoutUtil.Vrot(p, t * (.5 + 3*.5*(Math.cos(.1213*t)+1)));
        p = LayoutUtil.Vmult(p, 1/(1 + 5*.5*(Math.cos(.3025*t)+1)));
        p = LayoutUtil.Vadd(p, LayoutUtil.V(2*Math.cos(.2*t), 0));
        return OpcColor.getHsbColor(
		MathUtil.fmod(p.x + .4081*t, 1.),
		.6,
                .5 * (Math.cos(40*p.x)+1));
    }
    
    @Override
    protected PVector2 toIntermediateRepresentation(PVector2 p) {
	p = LayoutUtil.Vmult(p, 3.2);
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
}

