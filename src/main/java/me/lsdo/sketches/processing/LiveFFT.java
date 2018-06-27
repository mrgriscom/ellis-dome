package me.lsdo.sketches.processing;

import processing.core.*;
import ddf.minim.analysis.*;
import ddf.minim.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.interactivity.*;
import me.lsdo.processing.util.*;

enum FFTMode {
    PARTICLE,
    SEGMENT
}

/**
 * Created by shen on 2016/09/16.
 */
public class LiveFFT extends PApplet {
    // Some real-time FFT! This visualizes music in the frequency domain using a
    // polar-coordinate particle system. Particle size and radial distance are modulated
    // using a filtered FFT. Color is sampled from an image.

    PImage dot;
    PImage colors;
    Minim minim;
    AudioInput in;
    FFT fft;
    float[] fftFilter;
    
    EnumParameter<FFTMode> mode;
    float spin = 0.001f;
    float radiansPerBucket;
    float decay;
    float opacity;
    float minSize = 0.1f;
    float sizeScale = 0.2f;
    float angleCover;
    
    ProcessingAnimation canvas;

    public void settings() {
        size(300, 300, P3D);
    }
    
    public void setup()
    {
	canvas = Driver.makeCanvas(this);

	mode = new EnumParameter<FFTMode>("mode", "animation", FFTMode.class) {
		@Override
		public void onSet() {
		    if (get() == FFTMode.PARTICLE) {
			radiansPerBucket = radians(2);
			decay = 0.97f;
			opacity = 40f;
		    } else if (get() == FFTMode.SEGMENT) {
			radiansPerBucket = (float)Math.PI/180f;
			decay = 0.9f;
			opacity = 10;
			angleCover = 500;
		    }
		}
	    };
	mode.verbose = true;
	mode.init(mode.enumByName(Config.getSketchProperty("render_mode", "particle")));
	
        minim = new Minim(this);
	// by default line-in is mirrored to line-out for some reason, which will
	// cause bad feedback effects if listening from output monitor
	AudioOutput out = minim.getLineOut();
	out.mute();
	
        // Small buffer size!
        in = minim.getLineIn();
        fft = new FFT(in.bufferSize(), in.sampleRate());
        fftFilter = new float[fft.specSize()];

        dot = loadImage("../../../res/img/dot.png");
        colors = loadImage("../../../res/img/colors.png");
    }

    public void draw()
    {
        background(0);

        fft.forward(in.mix);
        for (int i = 0; i < fftFilter.length; i++) {
            fftFilter[i] = max(fftFilter[i] * decay, log(1 + fft.getBand(i)));
        }

        for (int i = 0; i < fftFilter.length; i += 3) {
            int rgb = colors.get((int)(map(i, 0, fftFilter.length-1, 0, colors.width-1)), colors.height/2);
            tint(rgb, fftFilter[i] * opacity);
            blendMode(ADD);

            float angle = (float)(millis() * spin + i * radiansPerBucket);
            float size = height * (minSize + sizeScale * fftFilter[i]);
            PVector center = new PVector(width * (fftFilter[i] * 0.2f), 0f);
            center.rotate(angle);
            center.add(new PVector(width * 0.5f, height * 0.5f));

	    if (mode.get() == FFTMode.PARTICLE) {
		image(dot, center.x - size/2, center.y - size/2, size, size);
	    } else if (mode.get() == FFTMode.SEGMENT) {
		noFill();
		stroke(rgb);
		//strokeCap(SQUARE);
		//strokeWeight(2);
		//noStroke();

		arc(width/2, height/2, size, size, angle - size/angleCover, angle + size/angleCover);
		arc(width/2, height/2, size, size, PI + angle - size/angleCover, PI + angle + size/angleCover);
	    }
        }

	canvas.draw();
    }
}
