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

enum VideoSizing {
    STRETCH_TO_FIT,
    // TODO support preserving aspect ratio
    //PRESERVE_ASPECT_GROW,
    //PRESERVE_ASPECT_SHRINK
}

public class VideoPlayer extends PApplet {

    //final String DEMO_VIDEO = "res/mov/bm2014_mushroom_cloud.mp4";
    final String DEMO_VIDEO = "res/mov/trexes_thunderdome.mp4";
    
    CanvasSketch simple;

    static final double[] skips = {5};

    Movie mov;
    boolean playing;
    VideoSizing sizeMode;
    boolean contrastStretch;

    // 'projection' rectangle to get more of the video area to overlap the panels
    double px0;
    double pw;
    double py0;
    double ph;
    boolean sizeModeInitialized = false;

    public void setup() {

	// TODO size based on density? and lower subsampling
        size(300, 300);

	simple = Driver.makeCanvas(this);
	
	String path = Config.getSketchProperty("path", DEMO_VIDEO);
	if (path.isEmpty()) {
	    throw new RuntimeException("must specify video path in sketch.properties!");
	}
	boolean repeat = Config.getSketchProperty("repeat", true);
	
        mov = new Movie(this, path);
        this.sizeMode = VideoSizing.STRETCH_TO_FIT;
        this.contrastStretch = true;

	if (repeat) {
	    mov.loop();
	} else {
	    mov.play();
	}
        playing = true;
        System.out.println("duration: " + mov.duration());
        // TODO some event when playback has finished?

        setProjectionArea();	
    }

    public void draw() {
        if (!sizeModeInitialized) {
            // Video dimensions aren't available until we actually draw a frame.
            image(mov, 0, 0, width, height);
            background(0);
            initializeViewport();
        }
        image(mov, (float)px0, (float)py0, (float)pw, (float)ph);

        simple.draw();
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

    public void movieEvent(Movie m) {
	m.read(); 
    } 

    void setProjectionArea() {
	PVector2 viewport[] = simple.getDome().getViewport();
	PVector2 p0 = LayoutUtil.xyToScreen(viewport[0], this.width, this.height, 2*simple.getDome().getRadius(), true);
        PVector2 p1 = LayoutUtil.xyToScreen(LayoutUtil.Vadd(viewport[0], viewport[1]), this.width, this.height, 2*simple.getDome().getRadius(), true);
        px0 = p0.x;
        py0 = p1.y;
        pw = p1.x - p0.x;
        ph = p0.y - p1.y;

	System.out.println(px0 + " " + py0 + " " + pw + " " + ph);
    }

    void initializeViewport() {
        // If we want to do stuff with aspect ratio we'd do it here.
        System.out.println("viewport aspect ratio: " + (pw/ph));
        System.out.println("original video aspect ratio: " + ((double)mov.width/mov.height));

        sizeModeInitialized = true;
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
