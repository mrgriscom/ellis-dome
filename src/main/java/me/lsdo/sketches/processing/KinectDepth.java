package me.lsdo.sketches.processing;

import java.util.*;
import me.lsdo.sketches.util.*;
import processing.core.*;
import ddf.minim.analysis.*;
import ddf.minim.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.interactivity.*;
import me.lsdo.processing.util.*;
import org.openkinect.freenect.*;
import org.openkinect.processing.*;

public class KinectDepth extends PApplet {

    NumericParameter nearThresh;
    NumericParameter farThresh;
    NumericParameter gamma;
    float nearHue = 0;
    float farHue = 80;
    
    Kinect kinect;

    final int KINECT_WIDTH = 640;
    final int KINECT_HEIGHT = 480;
    
    ProcessingAnimation canvas;

    public void settings() {
        size(KINECT_WIDTH, KINECT_HEIGHT);
    }

    public void setup() {
	canvas = new ProcessingAnimation(this, Driver.makeGeometry()) {
		int[] depth;
		
		@Override
		public void captureFrame() {
		    depth = kinect.getRawDepth();
		}

		@Override
		public int getPixel(int x, int y) {
		    int i = x + y*kinect.width;
		    double rawDepth = depth[i];

		    if (rawDepth == 0) {
			return color(0, 0, 0);
		    } else {
			rawDepth = Math.min(Math.max(rawDepth, nearThresh.get()), farThresh.get());
			double k = 1. - (rawDepth - nearThresh.get()) / (farThresh.get() - nearThresh.get());
			k = Math.pow(k, gamma.get());
			float hue = farHue + (float)k * (nearHue - farHue);
			return color(hue, 100, 100);
		    }
		}

		@Override
		protected void postFrame(double t){}
	    };

        colorMode(HSB, 100);

	kinect = new Kinect(this);
	kinect.initDepth();
	kinect.enableColorDepth(true);
	kinect.enableMirror(true);

	nearThresh = new NumericParameter("nearthresh", "animation");
	nearThresh.min = 0;
	nearThresh.max = 2000;
	nearThresh.init(300);
	farThresh = new NumericParameter("farthresh", "animation");
	farThresh.min = 0;
	farThresh.max = 2000;
	farThresh.init(1200);
	gamma = new NumericParameter("gamma", "animation");
	gamma.min = 0;
	gamma.max = 1;
	gamma.init(.75);
    }
    
    public void draw() {
        canvas.draw();
    }
}
