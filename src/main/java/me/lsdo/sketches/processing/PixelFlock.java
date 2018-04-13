package me.lsdo.sketches.processing;

import me.lsdo.sketches.util.*;
import processing.core.*;
import ddf.minim.analysis.*;
import ddf.minim.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;

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

    CanvasSketch simple;

    public void settings() {
        size(300, 300);
    }
    
    public void setup() {
	simple = Driver.makeCanvas(this);
	
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
            flock.addBoid(new Boid(width/2, height/2, startHue, width, height));
        }
    }

    public void draw() {
        background(0);
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
