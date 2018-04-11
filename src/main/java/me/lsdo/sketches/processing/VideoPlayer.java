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
    // Do nothing special; use the default pixel transform
    NONE,
    // Warp the video frame to match the mesh's viewport
    STRETCH_TO_FIT
}

public class VideoPlayer extends PApplet {

    //final String DEMO_VIDEO = "res/mov/bm2014_mushroom_cloud.mp4";
    final String DEMO_VIDEO = "res/mov/trexes_thunderdome.mp4";
    
    CanvasSketch simple;

    static final double[] skips = {5};

    Movie mov;
    boolean playing;
    VideoSizing sizeMode;
    boolean initialized = false;
    
    public void setup() {

	// ideally just want to match the video resolution, but that's not accessible
	// until the first frame is drawn, and processing window resizing is really sketchy
        size(600, 600);

	simple = Driver.makeCanvas(this);
	
	String path = Config.getSketchProperty("path", DEMO_VIDEO);
	if (path.isEmpty()) {
	    throw new RuntimeException("must specify video path in sketch.properties!");
	}
	boolean repeat = Config.getSketchProperty("repeat", true);
	
        mov = new Movie(this, path);

	boolean preserveAspect = Config.getSketchProperty("no_stretch", false);
	this.sizeMode = preserveAspect ? VideoSizing.NONE : VideoSizing.STRETCH_TO_FIT;

	if (repeat) {
	    mov.loop();
	} else {
	    mov.play();
	}
        playing = true;
        System.out.println("duration: " + mov.duration());
        // TODO some event when playback has finished?
    }

    public void draw() {
	if (!initialized) {
	    setVideoDimensions();
	}
	image(mov, 0, 0, width, height);
	simple.draw();
    }

    void setVideoDimensions() {
	if (mov.width == 0 || mov.height == 0) {
	    System.out.println("video dimensions not readay");
	    return;
	}	
	final double aspectRatio = (double)mov.width / mov.height;
	System.out.println(mov.width + "x" + mov.height + " " + aspectRatio + ":1");

	if (sizeMode == VideoSizing.NONE) {
	    // contract the x-axis to get back to a 1:1 aspect ratio (since processing doesn't
	    // know the video dimensions at launch and can't size the window appropriately)
	    simple.dome.transform = simple.dome.transform.compoundTransform(new LayoutUtil.Transform() {
		    public PVector2 transform(PVector2 p) {
			return LayoutUtil.V(p.x / aspectRatio, p.y);
		    }
		});
	} else if (sizeMode == VideoSizing.STRETCH_TO_FIT) {
	    simple.dome.transform = simple.dome.stretchToViewport(width, height);
	}
	
	initialized = true;
	simple.transformChanged();
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
