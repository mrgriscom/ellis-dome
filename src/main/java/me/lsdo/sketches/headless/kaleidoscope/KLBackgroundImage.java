package me.lsdo.sketches.headless.kaleidoscope;

import processing.core.*;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;

public class KLBackgroundImage extends KaleidoscopeBase {

    PApplet app;
    PImage img;

    public KLBackgroundImage(PixelMesh<? extends LedPixel> mesh) {
        super(mesh, 8);

	app = new PApplet();
	app.sketchPath(); // call to initialize
	img = app.loadImage("../../../" + Config.getSketchProperty("image", ""));
    }
    
    protected int sampleBaseAnimation(PVector2 p, double t) {
	// TODO preserve aspect ratio?
	int px = (int)(MathUtil.fmod(p.x, 1.) * img.width);
	int py = (int)(MathUtil.fmod(p.y, 1.) * img.height);
	return img.get(px, py);
    }
}

