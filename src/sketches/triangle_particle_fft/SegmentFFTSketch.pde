import java.util.*;
import processing.core.*;

public class SegmentFFTSketch extends FadecandySketch<Object> {

    int numOfBands;
    
    PImage dot;
    PImage colors;
    PImage colors1;
    PImage colors2;
    PImage colors3;

    float spin = 0.001f;
    float radiansPerBucket = (float)Math.PI/180f;
    float decay = 0.97f;
    float opacity = 10.f;
    float minSize = 0.1f;
    float sizeScale = 0.2f;

    // raw input
    float[] fftBands;
    
    // modified
    float[] fftFilter;

    SegmentFFTSketch(PApplet app, int bands, int size_px) {
        super(app, size_px);
        
        numOfBands = bands;
    }

    void init() {
        super.init();
        
        fftBands = new float[numOfBands];
        fftFilter = new float[numOfBands];

        dot = loadImage("dot.png");
        colors1 = loadImage("colors.png");
        colors2 = loadImage("colors2.png");
        colors3 = loadImage("colors3.png");

        colors = colors1;
    }

    void draw(double t) {
        
        background(0);
        
        for (int i = 0; i < fftFilter.length; i++) {
            fftFilter[i] = max(fftFilter[i] * decay, log(1 + fftBands[i]));
        }

        for (int i = 0; i < fftFilter.length; i += 3) {   
            color rgb = colors.get(int(map(i, 0, fftFilter.length-1, 0, colors.width-1)), colors.height/2);
            tint(rgb, fftFilter[i] * opacity);
            blendMode(REPLACE);

            float size = height * (minSize + sizeScale * fftFilter[i]);
            PVector center = new PVector(width * (fftFilter[i] * 0.2), 0);
            
            float angle = (float)(t * 1000 * spin + i * radiansPerBucket);
            
            center.rotate(angle);
            center.add(new PVector(width * 0.5, height * 0.5));
            
            fill(rgb);
            noStroke();

            arc(width/2, height/2, size, size, angle - size/200, angle + size/200, PIE);
            arc(width/2, height/2, size, size, PI + angle - size/200, PI + angle + size/200, PIE);
            //image(dot, center.x - size/2, center.y - size/2, size, size);
        }
    }
}
