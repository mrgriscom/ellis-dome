package me.lsdo.sketches.headless;

import java.util.*;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;

// Note: although this sets the mesh to black, it keeps doing so furiously every frame, just
// like any other animation.

public class Black extends PixelMeshAnimation<LedPixel> {

    public Black(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);
    }

    @Override
    public int drawPixel(LedPixel c, double t) {
        return OpcColor.getRgbColor(0, 0, 0);
    }

}
