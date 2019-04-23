package me.lsdo.sketches.processing;

import java.util.*;
import me.lsdo.sketches.util.*;
import processing.core.*;
import ddf.minim.analysis.*;
import ddf.minim.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import org.openkinect.freenect.*;
import org.openkinect.processing.*;

/**
 * Created by shen on 2016/09/17.
 */
public class PixelFlock extends PApplet {

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
	Boid makeBoid(float x, float y, int hue, int boxWidth, int boxHeight) {
	    return new Boid(x, y, hue, boxWidth, boxHeight);
	}
	
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
        flock = new BoidFlock();
        // Add an initial set of boids into the system
        for (int i = 0; i < 75; i++) {
            flock.addBoid(behavior.makeBoid(width/2, height/2, startHue, width, height));
        }
    }
    
    public void draw() {
        background(0);
	behavior.update();
	flock.run();

        for (Boid b : flock.boids) {
            render(b);
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

    void render(Boid b) {


        // Dra w a triangle rotated in the direction of velocity
        float theta = b.velocity.heading2D() + radians(90);
        // heading2D() above is now heading() but leaving old syntax until Processing.js catches up

        int[] col = b.getColour();
        fill(col[0], col[1], col[2], col[3]);
        pushMatrix();
        translate(b.location.x, b.location.y);
        rotate(theta);
        beginShape(TRIANGLES);

        vertex(0, -b.r*2);
        vertex(-b.r, b.r*2);
        vertex(b.r, b.r*2);
        endShape();
        popMatrix();

    }

    class KinectBoidBehavior extends BoidBehavior {
	Kinect kinect;
	int[] depth;
	PImage display;
	int depthThresh;
	int excludeWindowWidth = 200;
	int highlightWindowWidth = 100;
	public List<PVector> kinectKeyPoints;

	Boid.BoidManipulator repulsor = new Boid.BoidManipulator() {
		public PVector kinectScreenToXy(PVector p) {
		    double x = (p.x - 150) / 150.;
		    double y = -(p.y - 150) / 150.;
		    return new PVector((float)x, (float)y);
		}
		
		// this seems to repulse boids but not in the right places
		public void manipulate(Boid b) {
		    for (PVector kinectRef : kinectKeyPoints) {
			PVector boid = kinectScreenToXy(b.location);
			PVector knect = kinectScreenToXy(kinectRef);
			
			double x = boid.x - knect.x;
			double y = boid.y - knect.y;
			double len = Math.pow(x*x+y*y, .5);
			double normx = x / len;
			double normy = y / len;
			double strength = .4 /*.1*/ / Math.max(len * len, .01);
			
			// whirlpool
			//location.add(new PVector((float)(strength * normy), (float)(strength * -normx)));
			// repulse
			PVector motionOffset = new PVector((float)(strength * normx), (float)(strength * normy));
			b.location.add(motionOffset);
		    }
		}
	    };
	
	KinectBoidBehavior(PApplet app) {
	    depthThresh = Config.getSketchProperty("maxdepth", 750);	
	    kinect = new Kinect(app);
	    kinect.initDepth();
	    kinect.enableColorDepth(true);
	    kinect.enableMirror(true);
	    display = app.createImage(kinect.width, kinect.height, RGB);
	}

	Boid makeBoid(float x, float y, int hue, int boxWidth, int boxHeight) {
	    return new Boid(x, y, hue, boxWidth, boxHeight, repulsor);
	}
	
	void update() {
	    depth = kinect.getRawDepth();
	    kinectKeyPoints = new ArrayList<PVector>();
	    while (true) {
		PVector closest = null;
		int closestDepth = 0;
		
		for (int x = 0; x < kinect.width; x++) {
		    for (int y = 0; y < kinect.height; y++) {
			int i = x + y*kinect.width;
			int rawDepth = depth[i];
			if (rawDepth == 0 || rawDepth > depthThresh) {
			    continue;
			}
			
			boolean excluded = false;
			for (PVector keyPoint : kinectKeyPoints) {
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
			
			closest = new PVector(x, y);
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
	    for (PVector keyPoint : kinectKeyPoints) {
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
		    for (PVector keyPoint : kinectKeyPoints) {
			float dx = keyPoint.x - x;
			float dy = keyPoint.y - y;
			if (dx*dx + dy*dy < highlightWindowWidth * highlightWindowWidth) {
			    active = true;
			    break;
			}
		    }
		    
		    double lum = (rawDepth == 0 || rawDepth > 2000 ? 0. : 1. - Math.max(rawDepth-600., 0.) / (900. - 600));
		    if (active) {
			display.pixels[i] = color(0, 100, (int)(100*lum));
		    } else {
			display.pixels[i] = color(50, 30, (int)(100*lum));
		    }
		}
	    }
	    display.updatePixels();
	    
	    image(display, 0, 37.5f, 300, 225);
	}
    }
    
}
