package me.lsdo.sketches.headless;

import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import me.lsdo.processing.interactivity.*;
import java.util.*;

import me.lsdo.processing.geometry.prometheus.*;

// Each pixel flashes its id in binary, for potential auto-registering using computer vision

public class Binary extends PixelMeshAnimation<LedPixel> {
    Map<LedPixel, Integer> pixelIds;
    int maxBits;

    NumericParameter period;
    // TODO baseline
    
    public Binary(PixelMesh<? extends LedPixel> mesh) {
	super(mesh);

	period = new NumericParameter("period", "animation");
	period.min = .05;
	period.max = 3;
	period.scale = NumericParameter.Scale.LOG;
	period.init(.1);
	
	maxBits = (int)Math.ceil(Math.log(mesh.coords.size()) / Math.log(2.));
	System.out.println(mesh.coords.size() + " pixels, " + maxBits + " bits");
	
	pixelIds = new HashMap<LedPixel, Integer>();
	for (int i = 0; i < mesh.coords.size(); i++) {
	    pixelIds.put(mesh.coords.get(i), i);
	}
    }
    
    @Override
    protected int drawPixel(LedPixel c, double t) {
	int phase = (int)Math.floor(Config.clock() / period.get()) % maxBits;
	phase = (maxBits - 1) - phase;

	int i = pixelIds.get(c);
	boolean active = ((i >> phase) & 0x01) > 0;
	
	return OpcColor.getHsbColor(0, 0, active ? 1 : 0);
    }

}
