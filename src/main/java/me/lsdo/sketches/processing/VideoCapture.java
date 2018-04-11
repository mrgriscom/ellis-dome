package me.lsdo.sketches.processing;

import processing.core.*;
import processing.video.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import java.util.Arrays;

// Play video from a camera.
// A lot of overlap with VideoPlayer. TODO: possibly combine into a shared parent class.

public class VideoCapture extends PApplet {

    CanvasSketch simple;

    Capture cam;
    VideoSizing sizeMode;
    boolean initialized = false;
    
    public void setup() {

	// TODO size based on density? and lower subsampling
        size(600, 600);

	simple = Driver.makeCanvas(this);
	
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

	boolean preserveAspect = Config.getSketchProperty("no_stretch", false);
	this.sizeMode = preserveAspect ? VideoSizing.NONE : VideoSizing.STRETCH_TO_FIT;

	cam.start();
    }

    public void draw() {
	if (!initialized) {
	    setVideoDimensions();
	}
	image(cam, 0, 0, width, height);
	simple.draw();
    }

    void setVideoDimensions() {
	if (cam.width == 0 || cam.height == 0) {
	    System.out.println("video dimensions not readay");
	    return;
	}	
	final double aspectRatio = (double)cam.width / cam.height;
	System.out.println(cam.width + "x" + cam.height + " " + aspectRatio + ":1");

	if (sizeMode == VideoSizing.NONE) {
	    // contract the x-axis to get back to a 1:1 aspect ratio (since processing doesn't
	    // know the video dimensions at launch and can't size the window appropriately)
	    simple.dome.transform = simple.dome.transform.compoundTransform(new LayoutUtil.Transform() {
		    public PVector2 transform(PVector2 p) {
			return LayoutUtil.V(p.x / aspectRatio, p.y);
		    }
		});
	} else if (sizeMode == VideoSizing.STRETCH_TO_FIT) {
	    simple.dome.transform = simple.dome.stretchToViewport(width, height);
	}
	
	initialized = true;
	simple.transformChanged();
    }

    public void captureEvent(Capture c) {
	c.read(); 
    } 
    
}

