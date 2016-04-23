import java.util.*;
import processing.core.*;
import java.awt.*;
import java.awt.image.*;

public class ScreenScraper extends PixelGridSketch<Object> {

    Map<DomeCoord, PVector> xyCoords;
    Robot robot;
    BufferedImage frame;

    public ScreenScraper(PApplet app, int size_px) {
        super(app, size_px);
    }

    void init() {
        super.init();

        xyCoords = new HashMap<DomeCoord, PVector>();
        for (DomeCoord c : coords) {
            xyCoords.put(c, xyToScreen(points.get(c)));
        }

        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
    }

    void beforeFrame(double t) {
        frame = robot.createScreenCapture(new Rectangle(200, 200, height, width));
    }

    int drawPixel(DomeCoord c, double t) {
        PVector xy = xyCoords.get(c);
        return frame.getRGB((int)Math.floor(xy.x), (int)Math.floor(xy.y));
    }

}