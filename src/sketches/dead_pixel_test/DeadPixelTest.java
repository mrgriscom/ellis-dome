import java.util.*;
import processing.core.*;

public class DeadPixelTest extends PixelGridSketch<Object> {

    HashMap<DomeCoord, Integer> coordOrder;
    Set<Integer> adjacentPixels;

    public DeadPixelTest(PApplet app, int size_px) {
        super(app, size_px);
    }

    void init() {
        super.init();

        coordOrder = new HashMap<DomeCoord, Integer>();
        for (int i = 0; i < coords.size(); i++) {
            coordOrder.put(coords.get(i), i);
        }

        adjacentPixels = new HashSet<Integer>();
        for (int i = 0; i < Config.DEAD_PIXELS.length; i++) {
            int dead_px = Config.DEAD_PIXELS[i];
            for (int adj : new int[] {-1, 1}) {
                int k = dead_px + adj;
                if (k < 0 || k >= coords.size()) {
                    continue;
                } else if (OPC.isDeadPixel(k)) {
                    continue;
                } else {
                    adjacentPixels.add(k);
                }
            }
        }
    }

    int drawPixel(DomeCoord c, double t) {
        boolean blinkOn = MathUtil.fmod(t, 1.) < .75;
        if (!blinkOn) {
            return 0x0;
        }

        int i = coordOrder.get(c);
        if (Arrays.binarySearch(Config.DEAD_PIXELS, i) >= 0) {
            return color(0., 1., 1.);
        } else if (adjacentPixels.contains(i)) {
            return color(.5, 1., 1.);
        }
        return 0x0;
    }

}