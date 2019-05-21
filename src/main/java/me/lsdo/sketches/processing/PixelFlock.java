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

enum BoidHarassmentMode {
    REPEL,
    WHIRLPOOL,
    BLACKHOLE
}

/**
 * Created by shen on 2016/09/17.
 */
public class PixelFlock extends PApplet {

    final int NUM_BOIDS = 75;
    final int OUT_OF_WINDOW_FRINGE = 20;

    Minim minim;
    AudioInput in;
    FFT fft;
    float[] fftFilter;
    float decay = 0.97f;
    float minBrightness = 50f;
    float brightnessScale = 300;
    BeatDetect beat;

    BoidFlock flock;
    static final int startHue = 60;

    int time;
    int wait = 100;

    ProcessingAnimation canvas;

    class BoidBehavior {
	BoidFlock.BoidManipulator getManipulator() { return null; }
	void update() {}
    }
    BoidBehavior behavior;

    public void settings() {
        size(300, 300);
    }

    public void setup() {
	canvas = Driver.makeCanvas(this);

	if (Config.getSketchProperty("kinect", false)) {
	    behavior = new KinectBoidBehavior(this);
	} else {
	    behavior = new BoidBehavior();
	}

        minim = new Minim(this);
        in = minim.getLineIn();
        fft = new FFT(in.bufferSize(), in.sampleRate());
        fftFilter = new float[fft.specSize()];

        beat = new BeatDetect();

        colorMode(HSB, 100);
        time = millis();
        flock = new BoidFlock(width, height, OUT_OF_WINDOW_FRINGE, behavior.getManipulator());
        // Add an initial set of boids into the system
        for (int i = 0; i < NUM_BOIDS; i++) {
            flock.addBoid(new Boid(width/2, height/2, startHue));
        }
    }

    public void draw() {
        background(0);
	behavior.update();
	flock.run();

        for (Boid b : flock.boids) {
            b.render(this);
	}

        for (int i = 0; i < fftFilter.length; i++) {
            fftFilter[i] = max(fftFilter[i] * decay, log(1 + fft.getBand(i)));
        }


        fft.forward(in.mix);
        for (int i = 0; i < fftFilter.length; i += 3) {
            int brightness = (int) (minBrightness + (brightnessScale*fftFilter[i]));
            if (brightness > 100) {
                brightness = 100;
            }
            flock.setBrightness(brightness);
        }

        beat.detect(in.mix);
        if ( beat.isOnset() ) {
            flock.cycleHue();
            flock.scatterFlock();
            time = millis();
        } else {
            if (millis() - time >= wait) {
                flock.collectFlock();
                time = millis();
            }
        }

        canvas.draw();
    }

    class KinectBoidBehavior extends BoidBehavior {
	Kinect kinect;
	int[] depth;
	PImage display;
	NumericParameter depthThresh;
	int excludeWindowWidth = 200;
	int highlightWindowWidth = 60;
	public List<PVector2> kinectKeyPoints;

	int kinectCeiling;
	int kinectFloor;
	
	EnumParameter<BoidHarassmentMode> mode;
	NumericParameter strength;

	// search window to rule out spurious depth 'spikes'
	int denoiseRadius = 2;
	// at least this percentage of values within the window must register
	// as above-threshold in order to count
	double denoiseThreshold = .4;
	
	PVector2 screenToXy(PVector2 loc) {
	    return LayoutUtil.screenToXy(loc, width, height, 2., false);
	}

	float aspectOffsetFactor() {
	    return (1 - (float)kinect.height / kinect.width);
	}

	PVector2 kinectToXy(PVector2 loc) {
	    // this assumes a square render window and kinect feed with >1 aspect ratio
	    PVector2 xy = LayoutUtil.screenToXy(loc, kinect.width, kinect.width, 2., false);
	    // correction to center the kinect display while maintaining aspect ratio
	    xy.y -= aspectOffsetFactor();
	    return xy;
	}

	PVector2 xyToKinect(PVector2 xy) {
	    xy = new PVector2(xy.x, xy.y + aspectOffsetFactor());
	    return LayoutUtil.xyToScreen(xy, kinect.width, kinect.width);
	}

	BoidFlock.BoidManipulator repulsor = new BoidFlock.BoidManipulator() {
		public void manipulate(Boid b) {
		    for (PVector2 kinectRef : kinectKeyPoints) {
			PVector2 boid = screenToXy(b.getLocation());
			PVector2 kref = kinectToXy(kinectRef);

			PVector2 diff = PVector2.sub(boid, kref);
			double len = diff.mag();
			PVector2 norm = PVector2.mult(diff, 1 / (float)len);

			// cap strength by enforcing a min distance
			len = Math.max(len, .1);

			PVector2 dir = norm;
			double weight = 1;
			double pow;
			switch (mode.get()) {
			case REPEL:
			    pow = 2;
			    break;
			case BLACKHOLE:
			    weight = -1;
			    pow = 2;
			    break;
			case WHIRLPOOL:
			    dir = new PVector2(norm.y, -norm.x);
			    weight = 7;
			    pow = 1;
			    break;
			default: throw new RuntimeException();
			}

			double force = 1e-4 * strength.get() * weight * Math.pow(len, -pow);
			PVector2 offset = PVector2.mult(dir, (float)force);
			// convert back to boid (screen) coordinate space
			// need to do delta because coord systems don't share origin
			PVector2 boidOffset = PVector2.sub(LayoutUtil.xyToScreen(offset, width, height),
							   LayoutUtil.xyToScreen(new PVector2(0, 0), width, height));
			b.translate(boidOffset);
		    }
		}
	    };

	KinectBoidBehavior(PApplet app) {
	    kinectCeiling = Config.getSketchProperty("kinect_ceiling", -1);
	    kinectFloor = Config.getSketchProperty("kinect_floor", -1);
	    
	    strength = new NumericParameter("strength", "animation");
	    strength.min = 0;
	    strength.max = 20;
	    strength.init(10);

	    mode = new EnumParameter<BoidHarassmentMode>("harassment mode", "animation", BoidHarassmentMode.class);
	    String modeProp = Config.getSketchProperty("mode", "");
	    BoidHarassmentMode defaultMode;
	    if (!modeProp.isEmpty()) {
		defaultMode = mode.enumByName(modeProp);
	    } else {
		// choose randomly
		defaultMode = mode.values()[(int)(Math.random() * mode.values().length)];
	    }
	    mode.init(defaultMode);

            depthThresh = new NumericParameter("depththresh", "animation");
            depthThresh.min = kinectCeiling;
            depthThresh.max = kinectFloor;
            depthThresh.init(Config.getSketchProperty("kinect_activation", kinectCeiling));

	    kinect = new Kinect(app);
	    kinect.initDepth();
	    kinect.enableColorDepth(true);
	    kinect.enableMirror(true);
	    display = app.createImage(kinect.width, kinect.height, RGB);
	}

	BoidFlock.BoidManipulator getManipulator() {
	    return repulsor;
	}

	void update() {
	    depth = kinect.getRawDepth();
	    kinectKeyPoints = new ArrayList<PVector2>();
	    while (true) {
		PVector2 closest = null;
		int closestDepth = 0;

		for (int x = 0; x < kinect.width; x++) {
		    for (int y = 0; y < kinect.height; y++) {
			int i = x + y*kinect.width;
			int rawDepth = depth[i];
			if (KinectDepth.kinectOOB(rawDepth) || rawDepth > depthThresh.get()) {
			    continue;
			}

			boolean excluded = false;
			for (PVector2 keyPoint : kinectKeyPoints) {
			    if (Math.abs(keyPoint.x - x) < excludeWindowWidth / 2 &&
				Math.abs(keyPoint.y - y) < excludeWindowWidth / 2) {
				excluded = true;
				break;
			    }
			}
			if (excluded) {
			    continue;
			}

			if (closest != null && rawDepth >= closestDepth) {
			    continue;
			}

			int numOverThreshNeighbors = 0;
			for (int xo = -denoiseRadius; xo <= denoiseRadius + 1; xo++) {
			    for (int yo = -denoiseRadius; yo <= denoiseRadius + 1; yo++) {
                                int nx = x+xo;
                                int ny = y+yo;
                                if (nx < 0 || nx >= kinect.width || ny < 0 || ny >= kinect.height) {
                                    continue;
                                }
				int neighborDepth = depth[nx + ny*kinect.width];
				if (!KinectDepth.kinectOOB(neighborDepth) && neighborDepth <= depthThresh.get()) {
				    numOverThreshNeighbors++;
				}
			    }
			}
			if (numOverThreshNeighbors / Math.pow(2*denoiseRadius + 1, 2.) < denoiseThreshold) {
			    continue;
			}

			closest = new PVector2(x, y);
			closestDepth = rawDepth;
		    }
		}
		if (closest != null) {
		    kinectKeyPoints.add(closest);
		} else {
		    break;
		}
	    }

	    /*
	    for (PVector2 keyPoint : kinectKeyPoints) {
	        System.out.println(keyPoint);
	    }
	    if (kinectKeyPoints.size() > 0) {
	        System.out.println("----- " + System.currentTimeMillis());
	    }
	    */

	    display.loadPixels();
	    for (int x = 0; x < kinect.width; x++) {
		for (int y = 0; y < kinect.height; y++) {
		    int i = x + y*kinect.width;
		    int rawDepth = depth[i];

		    boolean active = false;
		    for (PVector2 keyPoint : kinectKeyPoints) {
			float dx = keyPoint.x - x;
			float dy = keyPoint.y - y;
			if (dx*dx + dy*dy < highlightWindowWidth * highlightWindowWidth) {
			    active = true;
			    break;
			}
		    }

		    double nearThresh = kinectCeiling;
		    double farThresh = kinectFloor;
		    double lum = (KinectDepth.kinectOOB(rawDepth) ? 0. : 1. - Math.max(rawDepth - nearThresh, 0.) / (farThresh - nearThresh));
		    if (active) {
			display.pixels[i] = color(0, 100, (int)(100*lum));
		    } else {
			display.pixels[i] = color(50, 30, (int)(100*lum));
		    }
		}
	    }
	    display.updatePixels();

	    PVector2 offset = LayoutUtil.xyToScreen(kinectToXy(new PVector2(0, 0)), width, height);
	    image(display, offset.x, offset.y, width, (float)width * kinect.height / kinect.width);
	}
    }

}
