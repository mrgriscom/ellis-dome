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

    // actual physical limits of kinect depth perception (value is
    // technically [0,2^11), but real world kinect range is less)
    // (very approx)
    public static final int KINECT_MIN = 300;
    public static final int KINECT_MAX = 2000;
    
    NumericParameter nearThresh;
    NumericParameter farThresh;
    NumericParameter gamma;
    float nearHue = 0;
    float farHue = 80;

    BooleanParameter debug;
    
    abstract class KinectBufferAnimation extends WindowAnimation {
	Kinect kinect;
	public KinectBufferAnimation(Kinect kinect, PixelMesh<? extends LedPixel> mesh){
	    super(mesh);
	    this.kinect = kinect;
	    initViewport(kinect.width, kinect.height);
	}
    }    
    WindowAnimation canvas;

    public void settings() {
	// Note: nothing is ever rendered to the processing window -- we sample
	// from the kinect buffers directly. In fact the only reason this is a
	// processing sketch is so we can use the processing kinect libs.
	size(100, 100);
    }

    public void setup() {
        colorMode(HSB, 100);

	Kinect kinect = new Kinect(this);
	kinect.initDepth();
	kinect.enableColorDepth(true);
	kinect.enableMirror(true);

	canvas = new KinectBufferAnimation(kinect, Driver.makeGeometry()) {
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
		    } else if (debug.get()) {
			return (rawDepth <= nearThresh.get() ?
				color(0, 50, 100) :
				color(50, 100, 20));
		    } else {
			rawDepth = Math.min(Math.max(rawDepth, nearThresh.get()), farThresh.get());
			double k = 1. - (rawDepth - nearThresh.get()) / (farThresh.get() - nearThresh.get());
			k = Math.pow(k, gamma.get());
			float hue = farHue + (float)k * (nearHue - farHue);
			return color(hue, 100, 100);
		    }
		}
	    };
	
	nearThresh = new NumericParameter("nearthresh", "animation");
	nearThresh.min = KINECT_MIN;
	nearThresh.max = KINECT_MAX;
	nearThresh.init(Config.getSketchProperty("kinect_ceiling", KINECT_MIN));
	farThresh = new NumericParameter("farthresh", "animation");
	farThresh.min = KINECT_MIN;
	farThresh.max = KINECT_MAX;
	farThresh.init(Config.getSketchProperty("kinect_floor", KINECT_MAX));
	gamma = new NumericParameter("gamma", "animation");
	gamma.min = 0;
	gamma.max = 1;
	gamma.init(.75);

	debug = new BooleanParameter("debug", "animation");
	debug.init(false);
    }

    public void draw() {
        canvas.draw(millis() / 1000.);
    }
}
