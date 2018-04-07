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

    // 'projection' rectangle to get more of the video area to overlap the panels
    double px0;
    double pw;
    double py0;
    double ph;
    boolean sizeModeInitialized = false;

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
        this.sizeMode = VideoSizing.STRETCH_TO_FIT;

	cam.start();

        setProjectionArea();	
    }

    public void draw() {
        if (!sizeModeInitialized) {
            // Video dimensions aren't available until we actually draw a frame.
            image(cam, 0, 0, width, height);
            background(0);
            initializeViewport();
        }
        image(cam, (float)px0, (float)py0, (float)pw, (float)ph);

        simple.draw();
    }


    public void captureEvent(Capture c) {
	c.read(); 
    } 

    void setProjectionArea() {
	PVector2 viewport[] = simple.getDome().getViewport();
	PVector2 p0 = LayoutUtil.xyToScreen(viewport[0], this.width, this.height, 2*simple.getDome().getRadius(), true);
        PVector2 p1 = LayoutUtil.xyToScreen(LayoutUtil.Vadd(viewport[0], viewport[1]), this.width, this.height, 2*simple.getDome().getRadius(), true);
        px0 = p0.x;
        py0 = p1.y;
        pw = p1.x - p0.x;
        ph = p0.y - p1.y;

	System.out.println(px0 + " " + py0 + " " + pw + " " + ph);
    }

    void initializeViewport() {
        // If we want to do stuff with aspect ratio we'd do it here.
        System.out.println("viewport aspect ratio: " + (pw/ph));
        System.out.println("original video aspect ratio: " + ((double)cam.width/cam.height));

        sizeModeInitialized = true;
    }

    
}

