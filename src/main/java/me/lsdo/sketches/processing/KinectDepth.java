// Daniel Shiffman
// All features test

// https://github.com/shiffman/OpenKinect-for-Processing
// http://shiffman.net/p5/kinect/

package me.lsdo.sketches.processing;

import org.openkinect.freenect.*;
import org.openkinect.processing.*;
import processing.core.*;
import me.lsdo.processing.*;

public class KinectDepth extends PApplet {

    CanvasSketch canvas;
    
    Kinect kinect;

    public void setup() {
	size(640, 640);

        Dome dome = new Dome();
        OPC opc = new OPC();
        canvas = new CanvasSketch(this, dome, opc);

	kinect = new Kinect(this);
	kinect.initDepth();
	kinect.enableColorDepth(true);
	kinect.enableMirror(true);

	//kinect.initVideo();
	//kinect.enableIR(true);
    }


    public void draw() {
	background(0);
	int offset = (640 - 480) / 2;
	image(kinect.getDepthImage(), 0, offset);
	//image(kinect.getVideoImage(), 0, offset);

	canvas.draw();
    }

}
