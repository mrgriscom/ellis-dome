import java.util.Arrays;
import java.util.Properties;
import processing.core.*;
import processing.video.*;

// Play video from file. Will size the projection area to the smallest bounding rectangle for the pixels.
// Aspect ratio is not preserved (support for such is a TODO). Dynamic contrast stretch is supported.

// Keyboard controls:
// p: play/plause
// .: ff 5 sec
// ,: rewind 5 sec
// >: ff 60 sec
// <: rewind 60 sec

enum VideoSizing {
    STRETCH_TO_FIT,
    // TODO support preserving aspect ratio
    //PRESERVE_ASPECT_GROW,
    //PRESERVE_ASPECT_SHRINK
}

public class VideoPlayer extends FadecandySketch<Object> implements OPC.FramePostprocessor {

    static final double[] skips = {5, 60};

    String filename;
    Movie mov;
    boolean playing;
    VideoSizing sizeMode;
    boolean contrastStretch;
    boolean repeat;

    // 'projection' rectangle to get more of the video area to overlap the panels
    double px0;
    double pw;
    double py0;
    double ph;
    boolean sizeModeInitialized = false;

    public VideoPlayer(PApplet app, int size_px, String filename) {
        this(app, size_px, filename, VideoSizing.STRETCH_TO_FIT, false);
    }

    public VideoPlayer(PApplet app, int size_px, String filename, VideoSizing sizeMode, boolean contrastStretch) {
        super(app, size_px);
        this.filename = filename;
        this.sizeMode = sizeMode;
        this.contrastStretch = contrastStretch;
    }

    void configureSketch(Properties props) {
        filename = props.getProperty("path", filename);
        repeat = props.getProperty("repeat", "false").equals("true");
    }

    void init() {
        super.init();
        mov = new Movie(app, filename);
        opc.framePostprocessor = this;

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

    void setProjectionArea() {
        double xmin = radius;
        double xmax = -radius;
        double ymin = radius;
        double ymax = -radius;
        for (PVector p : points.values()) {
            xmin = Math.min(xmin, p.x);
            xmax = Math.max(xmax, p.x);
            ymin = Math.min(ymin, p.y);
            ymax = Math.max(ymax, p.y);
        }
        double margin = .5*LayoutUtil.pixelSpacing(panel_size);
        xmin -= margin;
        xmax += margin;
        ymin -= margin;
        ymax += margin;

        PVector p0 = xyToScreen(LayoutUtil.V(xmin, ymax));
        PVector pdiag = LayoutUtil.Vsub(xyToScreen(LayoutUtil.V(xmax, ymin)), p0);
        px0 = p0.x;
        py0 = p0.y;
        pw = pdiag.x;
        ph = pdiag.y;
    }

    void initializeViewport() {
        // If we want to do stuff with aspect ratio we'd do it here.
        System.out.println("viewport aspect ratio: " + (pw/ph));
        System.out.println("original video aspect ratio: " + ((double)mov.width/mov.height));

        sizeModeInitialized = true;
    }

    void draw(double t) {
        if (!sizeModeInitialized) {
            // Video dimensions aren't available until we actually draw a frame.
            app.image(mov, 0, 0, width, height);
            app.background(0);
            initializeViewport();
        }
        app.image(mov, (float)px0, (float)py0, (float)pw, (float)ph);
    }

    void keyPressed() {
        int dir = 0;
        int iskip = -1;
        if (app.key == '.') {
            dir = 1;
            iskip = 0;
        } else if (app.key == ',') {
            dir = -1;
            iskip = 0;
        } else if (app.key == '>') {
            dir = 1;
            iskip = 1;
        } else if (app.key == '<') {
            dir = -1;
            iskip = 1;
        } else if (app.key == 'p') {
            if (playing) {
                mov.pause();
            } else {
                mov.play();
            }
            playing = !playing;
        }

        if (dir != 0) {
            double t = Math.max(0, Math.min(mov.duration(), mov.time() + dir * skips[iskip]));
            mov.jump((float)t);
            System.out.println(String.format("%.2f / %.2f", t, mov.duration()));
        }
    }

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

}
