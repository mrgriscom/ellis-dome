package me.lsdo.sketches.headless;

import java.util.*;
import me.lsdo.processing.*;

// Note: although this sets the mesh to black, it keeps doing so furiously every frame, just
// like any other animation.

public class Black extends PixelMeshAnimation<LedPixel> {

    public Black(PixelMesh<? extends LedPixel> mesh) {
        super(mesh);
    }

    @Override
    public int drawPixel(LedPixel c, double t) {
        return 0;
    }

}
