package me.lsdo.sketches.processing;

import processing.core.*;
import processing.video.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import java.util.Arrays;

public abstract class VideoBase extends PApplet {

    CanvasSketch simple;
    PImage media;
    boolean initialized = false;

    public void settings() {
	// ideally just want to match the video resolution, but that's not accessible
	// until the first frame is drawn, and processing window resizing is really sketchy
        size(600, 600);
    }
    
    public void setup() {
        simple = Driver.makeCanvas(this);
	media = loadMedia();

        // TODO some event when playback has finished?
    }

    public abstract PImage loadMedia();
    
    public void draw() {
	if (!initialized) {
	    setVideoDimensions();
	}
	image(media, 0, 0, width, height);
	simple.draw();
    }

    void setVideoDimensions() {
	if (media.width == 0 || media.height == 0) {
	    System.out.println("video dimensions not readay");
	    return;
	}	
	final double aspectRatio = (double)media.width / media.height;
	System.out.println(media.width + "x" + media.height + " " + aspectRatio + ":1");

	boolean preserveAspect = Config.getSketchProperty("no_stretch", false);
	simple.initViewport(width, height, preserveAspect);
	if (preserveAspect) {
	    // contract the x-axis to get back to a 1:1 aspect ratio (since processing doesn't
	    // know the video dimensions at launch and can't size the window appropriately)
	    simple.windowTransform = new LayoutUtil.Transform() {
		    public PVector2 transform(PVector2 p) {
			return LayoutUtil.V(p.x / aspectRatio, p.y);
		    }
		};
	}
	
	initialized = true;
	simple.transformChanged();
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
