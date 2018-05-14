package me.lsdo.sketches.headless;

import java.util.*;
import me.lsdo.processing.*;

public class Black extends DomeAnimation<LedPixel> {

    public Black(PixelMesh<? extends LedPixel> dome) {
        super(dome);
    }

    @Override
    public int drawPixel(LedPixel c, double t) {
        return 0;
    }

}
