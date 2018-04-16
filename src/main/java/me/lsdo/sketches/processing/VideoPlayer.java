package me.lsdo.sketches.processing;

import processing.core.*;
import processing.video.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import java.util.Arrays;

// Play video from file. Will size the projection area to the smallest bounding rectangle for the pixels.
// Aspect ratio is not preserved (support for such is a TODO). Dynamic contrast stretch is supported.

// Keyboard controls:
// p: play/plause
// .: ff 5 sec
// ,: rewind 5 sec

// TODO support contrast stretch

public class VideoPlayer extends VideoBase {

    //final String DEMO_VIDEO = "res/mov/bm2014_mushroom_cloud.mp4";
    final String DEMO_VIDEO = "res/mov/trexes_thunderdome.mp4";
    
    static final double[] skips = {5};

    Movie mov;
    boolean playing;
    
    public void loadMedia() {
	String path = Config.getSketchProperty("path", DEMO_VIDEO);
	if (path.isEmpty()) {
	    throw new RuntimeException("must specify video path in sketch.properties!");
	}
	boolean repeat = Config.getSketchProperty("repeat", true);
	double startAt = Config.getSketchProperty("skip", 0.);
	if (repeat) {
	    System.out.println("note: skip only applies to the first play-through");
	}
	
        mov = new Movie(this, path);

	if (repeat) {
	    mov.loop();
	} else {
	    mov.play();
	}
        playing = true;

	if (startAt > 0) {
	    mov.jump((float)startAt);
	}
	
        System.out.println("duration: " + mov.duration());
        // TODO some event when playback has finished?
    }

    public PImage nextFrame() {
	if (mov.available()) {
	    mov.read();
	}
	return mov;
    }

    public void keyPressed() {
        int dir = 0;
        if (this.key == '.') {
            dir = 1;
        } else if (this.key == ',') {
            dir = -1;
        } else if (this.key == 'p') {
            if (playing) {
                mov.pause();
            } else {
                mov.play();
            }
            playing = !playing;
        }

        if (dir != 0) {
            double t = Math.max(0, Math.min(mov.duration(), mov.time() + dir * skips[0]));
            mov.jump((float)t);
            System.out.println(String.format("%.2f / %.2f", t, mov.duration()));
        }
    }

}

/*
  class ContrastStretch implements OPC.FramePostprocessor {
        final double BENCHMARK_PCTILE = .95;

        // FIXME pixels outside the video projection area should be excluded
        public void postProcessFrame(int[] pixelBuffer) {
            int numPixels = pixelBuffer.length;
            float[] lums = new float[numPixels];
            for (int i = 0; i < numPixels; i++) {
                int pixel = pixelBuffer[i];
                lums[i] = app.brightness(pixel);
            }
            Arrays.sort(lums);

            float lowlum = lums[(int)((1-BENCHMARK_PCTILE) * numPixels)];
            float highlum = lums[(int)(BENCHMARK_PCTILE * numPixels)];

            for (int i = 0; i < numPixels; i++) {
                int pixel = pixelBuffer[i];
                float h = app.hue(pixel);
                float s = app.saturation(pixel);
                float l = app.brightness(pixel);
                l = 100f * (l - lowlum) / (highlum - lowlum);
                pixelBuffer[i] = app.color(h, s, l);
            }
        }
    }
    OPC.FramePostprocessor _contrastStretch = new ContrastStretch();

    public void postProcessFrame(int[] pixelBuffer) {
        OPC.FramePostprocessor proc = null;
        if (contrastStretch) {
            proc = _contrastStretch;
        }
        if (proc != null) {
            proc.postProcessFrame(pixelBuffer);
        }
    }
*/
