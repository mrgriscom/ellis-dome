package me.lsdo.sketches.processing;

import processing.core.*;
import processing.video.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import java.util.Arrays;

// Play video from a camera.

public class VideoCapture extends VideoBase {

    Capture cam;
    
    public PImage loadMedia() {
	String device = Config.getSketchProperty("camera", "");
	String[] availableDevices = Capture.list();
	boolean found = false;
	for (String s : availableDevices) {
	    if (device.equals(s)) {
		found = true;
		break;
	    }
	}
	if (!found) {
	    System.out.println("available devices:");
	    Arrays.sort(availableDevices);
	    for (String s : availableDevices) {
		System.out.println(s);
	    }
	    throw new RuntimeException(device.isEmpty() ?
				       "must specify camera identifier in sketch.properties!" :
				       "camera device \"" + device + "\" not available!");
	}
	
        cam = new Capture(this, device);
	cam.start();

	return cam;
    }

    public void captureEvent(Capture c) {
	c.read(); 
    } 
    
}

