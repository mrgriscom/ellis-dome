package me.lsdo.sketches.processing;

import me.lsdo.sketches.util.*;
import processing.core.*;
import ddf.minim.analysis.*;
import ddf.minim.*;
import me.lsdo.processing.*;
import org.openkinect.freenect.*;
import org.openkinect.processing.*;

/**
 * Created by shen on 2016/09/17.
 */
public class KinectFlock extends PApplet {

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

    CanvasSketch simple;

    Kinect kinect;

    int[] depth;
    PImage display;
    int thresh = 700;

    public PVector kinectAvg;

    Boid.BoidManipulator repulsor = new Boid.BoidManipulator() {
	    public void manipulate(Boid b) {
		if (kinectAvg != null) {
		    double x = (b.location.x - 150) / 150.;
		    double y = -(b.location.y - 150) / 150.;
		    x -= kinectAvg.x;
		    y -= kinectAvg.y;
		    double len = Math.pow(x*x+y*y, .5);
		    double normx = x / len;
		    double normy = y / len;
		    double strength = .1 / Math.max(len * len, .01);

		    // whirlpool
		    //location.add(new PVector((float)(strength * normy), (float)(strength * -normx)));
		    // repulse
		    PVector motionOffset = new PVector((float)(strength * normx), (float)(strength * normy));
		    b.location.add(motionOffset);
		}
	    }
	};
    
    public void setup() {
        size(300, 300);

        simple = new CanvasSketch(this, new Dome(), new OPC());

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
            flock.addBoid(new Boid(width/2, height/2, startHue, width, height, repulsor));
        }

	kinect = new Kinect(this);
	kinect.initDepth();
	kinect.enableColorDepth(true);
	kinect.enableMirror(true);
      	display = createImage(kinect.width, kinect.height, RGB);
    }

    public void draw() {
        background(0);


	depth = kinect.getRawDepth();
	display.loadPixels();
	int xsum = 0;
	int ysum = 0;
	int n = 0;
	for (int x = 0; x < kinect.width; x++) {
	    for (int y = 0; y < kinect.height; y++) {
		int i = x + y*kinect.width;
		int rawDepth = depth[i];
		if (rawDepth < thresh) {
		    display.pixels[i] = color(0, 50, 50);
		    xsum += x;
		    ysum += y;
		    n += 1;
		} else {
		    display.pixels[i] = color(0, 0, 0);
		}
	    }
	}
	display.updatePixels();
	if (n == 0) {
	    kinectAvg = null;
	} else {
	    double avgX = (float)xsum / n / 640. * 2 - 1;
	    double avgY = -((float)ysum / n / 480. * 2 - 1) * 480 / 640;
	    kinectAvg = new PVector((float)avgX, (float)avgY);
	}
	System.out.println(kinectAvg);

	
	image(display, 0, 37.5f, 300, 225);
	

	flock.run();

        for (Boid b : flock.boids)
            render(b);



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

        simple.draw();
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

}
