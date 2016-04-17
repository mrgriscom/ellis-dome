// Master template for sketches that output to fadecandy. Takes care of common initialization and
// provides some helper functions.

// Typically the .pde file will simply become:
/*

FadecandySketch driver = new SubclassOfFadecandySketch(this, <window size>);

void setup() {
  driver.init();
}

void draw() {
  driver.draw();
}

*/

import java.util.*;
import processing.core.*;

// S is the type of the state that is maintained and updated each frame.
public class FadecandySketch<S> {

    static final int COLOR_STEPS = 100;

    // Reference to processing sketch object
    PApplet app;

    // width and height of processing canvas, in pixels
    int width, height;

    // Reference to fadecandy controller
    OPC opc;

    // Positions of all the pixels in triangular grid coordinates (and in the order seen by
    // the fadecandy).
    ArrayList<DomeCoord> coords;

    // Mapping of pixel grid coordinates to xy locations (world coordinates, not screen
    // coordinates!)
    HashMap<DomeCoord, PVector> points;

    // Size of a single panel's pixel grid
    int panel_size;

    // Layout configuration of panels
    PanelLayout panel_config_mode;

    // Distance from center to farthest pixel, in panel lengths
    double radius;

    // Ongoing state to be updated each frame (such as positions, directions, etc.).
    S state;

    // Timestamp of the most recent completed frame.
    double last_t;

    FadecandySketch(PApplet app, int size_px) {
        this(app, size_px, size_px);
    }

    FadecandySketch(PApplet app, int width_px, int height_px) {
        this.app = app;
        this.width = width_px;
        this.height = height_px;
    }

    // Determine optimum screen width for panel display, such that pixels are packed in relatively
    // close together without actually overlapping. This should decrease aliasing (if the sketch
    // renders more smoothly at lower resolution), but will also increase quantization error (how
    // far a pixel's snapped-to-screen position is off from its true dome position) and blurriness
    // in a way that is undesirable for some sketches. Increase the spacing with
    // 'spacingMultiplier' (default: 1).
    // This function is completely useless for sketches that already do per-pixel rendering
    // directly (e.g., PointSampleSketch, PixelGridSketch)
    static int widthForPixelDensity(double spacingMultiplier) {
        double radius = getRadius();
        double pixelWidth = 2. * radius / LayoutUtil.pixelSpacing(Config.PANEL_SIZE);
        double baseSpacing = 2.;
        int dim = (int)Math.round(baseSpacing * pixelWidth * spacingMultiplier);
        System.out.println("dome pixel width: " + pixelWidth);
        System.out.println("screen width: " + dim);
        return dim;
    }

    // Get the radius of the panel display, in panel lengths
    static double getRadius() {
        return LayoutUtil.getPanelConfig(Config.PARTIAL_LAYOUT ?
                                         Config.FULL_PANEL_LAYOUT :
                                         Config.PANEL_LAYOUT).radius;
    }

    // Override this if you have more specific initialization to perform. Be sure to call
    // super.init()!
    void init() {
        app.size(width, height, app.P2D);

        String hostname = Config.getConfig().FADECANDY_HOST;
        int port = 7890;
        opc = new OPC(app, hostname, port);

        panel_size = Config.PANEL_SIZE;
        panel_config_mode = Config.PANEL_LAYOUT;
        LayoutUtil.PanelConfig config = LayoutUtil.getPanelConfig(panel_config_mode);
        coords = config.fill(panel_size);
        points = config.coordsToXy(coords);
        
        //LayoutUtil.generateOPCSimLayout(pixelLocationsInOrder(), app, "layout.json", true);
        
        radius = getRadius();
        registerScreenSamples();

        app.colorMode(app.HSB, COLOR_STEPS);
        
        state = initialState();
    }

    // **OVERRIDE** (optional)
    // Return the initial persistent state.
    S initialState() {
        return null;
    }

    // **OVERRIDE** (optional)
    // Update the persistent state from one frame to the next. t_delta is the time elapsed since the
    // previous frame.
    S updateState(S state, double t_delta) {
        return state;
    }

    ArrayList<PVector> pixelLocationsInOrder() {
        ArrayList<PVector> xy = new ArrayList<PVector>();
        for (DomeCoord c : coords) {
            xy.add(points.get(c));
        }
        return xy;
    }

    // For sampling from a rendered screen. Convert led positions from world coordinates to screen pixels and register with
    // the fadecandy(ies).
    void registerScreenSamples() {
        opc.registerLEDs(LayoutUtil.transform(pixelLocationsInOrder(), new LayoutUtil.Transform() {
                public PVector transform(PVector p) {
                    return xyToScreen(p);
                }
            }));
    }

    // Convert a screen pixel position to world coordinates.
    PVector screenToXy(PVector p) {
        return LayoutUtil.screenToXy(p, width, height, 2*radius, true);
    }

    // Inverse of screenToXy()
    PVector xyToScreen(PVector p) {
        return LayoutUtil.xyToScreen(p, width, height, 2*radius, true);
    }

    // Write a pixel value to the screen buffer.
    // TODO for sketches that only render pixels directly, is there a cost penalty for going
    // through the screen buffer?
    void setLED(int i, int rgb) {
        int px = opc.pixelLocations[i];
        if (px >= 0) {
            app.pixels[px] = rgb;
        }
    }

    void draw() {
        double t = app.millis() / 1000.;

        _updateState(t);
        beforeFrame(t);
        draw(t);
        afterFrame(t);

        // The HUD: think this space is (almost) never mapped to pixels.
        app.fill(0,0,100);
        app.text("opc @" + opc.host, 100, app.height - 10);
        app.text(String.format("%.1ffps", app.frameRate), 10, app.height - 10);
    }

    void _updateState(double t) {
        if (last_t > 0) {
            state = updateState(state, t - last_t);
        }
        last_t = t;
    }

    // **OVERRIDE** (optional)
    // A hook to be called before the frame is rendered.
    void beforeFrame(double t) { }

    // **OVERRIDE** (optional)
    // A hook to be called after the frame is rendered.
    void afterFrame(double t) { }

    // **OVERRIDE** this with your sketch's drawing code. 't' is the global clock in seconds.
    void draw(double t) {
        //System.out.println("nothing to draw");
    }

    // **OVERRIDE** to handle keyboard input.
    void keyPressed() { }

    // Helper function to generate a color. r/g/b all in the range [0, 1].
    int color(double r, double g, double b) {
        return app.color((float)(COLOR_STEPS * r), (float)(COLOR_STEPS * g), (float)(COLOR_STEPS * b));
    }

}