// Daniel Shiffman
// All features test

// https://github.com/shiffman/OpenKinect-for-Processing
// http://shiffman.net/p5/kinect/

package me.lsdo.sketches.processing;

import org.openkinect.freenect.*;
import org.openkinect.processing.*;
import processing.core.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;

// This sketch is mostly for debugging. It should probably be incorporated into VideoCapture

public class KinectDepth extends PApplet {

    CanvasSketch canvas;
    
    Kinect kinect;

    public void setup() {
	size(640, 640);
	canvas = Driver.makeCanvas(this);

	kinect = new Kinect(this);
	kinect.initDepth();
	//	kinect.enableColorDepth(true);
	kinect.enableColorDepth(false);
	kinect.enableMirror(true);

	//kinect.initVideo();
	//kinect.enableIR(true);
    }


    public void draw() {
	background(0);
	int offset = (640 - 480) / 2;
	image(kinect.getDepthImage(), 0, offset);
	//image(kinect.getVideoImage(), 0, offset);

	int[] depth = kinect.getRawDepth();
	int maxdepth = 0;
	for (int x = 0; x < kinect.width; x++) {
	    for (int y = 0; y < kinect.height; y++) {
		int i = x + y*kinect.width;
		int rawDepth = depth[i];
		if (rawDepth < 2000) {
		    maxdepth = Math.max(maxdepth, rawDepth);
		}
	    }
	}
	System.out.println(maxdepth);

	
	canvas.draw();
    }

}
