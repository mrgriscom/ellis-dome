package me.lsdo.sketches.headless;

import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import me.lsdo.processing.interactivity.*;
import java.util.*;

enum Color {
    BLACK(0, 0, 0),
    RED(0, 1, 1),
    GREEN(120, 1, 1),
    BLUE(240, 1, 1),
    YELLOW(60, 1, 1),
    CYAN(180, 1, 1),
    MAGENTA(300, 1, 1),
    GREY(0, 0, .5),
    WHITE(0, 0, 1);

    public final double h, s, v;
    private Color(double h, double s, double v) {
        this.h = h;
        this.s = s;
        this.v = v;
    }
}

// Set all pixels to a static test color (default: black). Note, still uses CPU despite static pattern.

public class ColorTest extends PixelMeshAnimation<LedPixel> {

    EnumParameter<Color> color;
    NumericParameter hue;
    NumericParameter val;
    NumericParameter sat;

    public ColorTest(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);

        color = new EnumParameter<Color>("color", "animation", Color.class) {
                public void onSet() {
                    ColorTest.this.hue.set(get().h);
                    ColorTest.this.sat.set(get().s);
                    ColorTest.this.val.set(get().v);
                }
            };

        hue = new NumericParameter("hue", "animation");
        hue.min = 0;
        hue.max = 360;
        val = new NumericParameter("brightness", "animation");
        val.min = 0;
        val.max = 1;
        sat = new NumericParameter("saturation", "animation");
        sat.min = 0;
        sat.max = 1;

        color.init(Color.BLACK);
    }

    @Override
    public int drawPixel(LedPixel c, double t) {
        int rgb = OpcColor.getHsbColor(MathUtil.fmod(hue.get() / 360., 1.), sat.get(), val.get());
        System.out.println(String.format("%06x", rgb & 0xFFFFFF));
        return rgb;
    }

}
