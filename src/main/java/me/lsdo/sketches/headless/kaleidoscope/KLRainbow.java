package me.lsdo.sketches.headless.kaleidoscope;

import me.lsdo.processing.*;
import me.lsdo.processing.util.*;

public class KLRainbow extends KaleidoscopeBase {

    public KLRainbow(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);
    }
    
    protected int sampleBaseAnimation(PVector2 p, double t) {
	return OpcColor.getHsbColor(
		MathUtil.fmod(p.x + .4081*t, 1.),
		.6,
                .5 * (Math.cos(40*p.x)+1));
    }
}

