package me.lsdo.sketches.processing;

import processing.core.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.interactivity.*;
import me.lsdo.processing.util.*;

// Interactive test (mouse-responsive) to check XY fidelity.
// click to toggle horizontal/vertical line

public class LayoutTest extends PApplet {
    PImage dot;
    
    ProcessingAnimation canvas;

    BooleanParameter horiz;
    NumericParameter offset;
    NumericParameter size;
    BooleanParameter auto;
    
    public void settings() {
        size(300, 300, P3D);
    }
    
    public void setup()
    {
	canvas = Driver.makeCanvas(this);
        dot = loadImage("../../../res/img/dot.png");

	auto = new BooleanParameter("auto", "animation");
	auto.description = "mode";
	auto.trueCaption = "auto";
	auto.falseCaption = "manual";
	auto.init(true);
	    
	horiz = new BooleanParameter("line axis", "animation");
	horiz.trueCaption = "horizontal";
	horiz.falseCaption = "vertical";
	horiz.init(true);

	offset = new NumericParameter("offset", "animation");
	offset.min = 0;
	offset.max = 1;
	offset.init(0);

	size = new NumericParameter("width", "animation");
	size.min = .01;
	size.max = 2./3.;
	size.scale = NumericParameter.Scale.LOG;
	size.init(.1);
    }

    public void draw()
    {
        background(0);
	if (auto.get()) {
	    drawAutoLine();
	} else {
	    drawLine(horiz.get(), offset.get());
	}
	
	canvas.draw();
    }

    public void mousePressed() {
	horiz.toggle();
	mouseMoved();
    }

    public void mouseMoved() {
	auto.set(false);
	offset.set(horiz.get() ? (double)mouseY / height : (double)mouseX / width);
    }

    void drawLine(boolean horiz, double offset) {
	// stretch dot many times longer than the screen dimension to make it effectively a line
	float stretchFactor = 10;

	float screenDim = (horiz ? height : width);
	float longAxis = screenDim * stretchFactor;
	float longOffset = (screenDim - longAxis) / 2f;
	float shortAxis = (float)(size.get() * screenDim);
	float shortOffset = (float)offset * screenDim - shortAxis / 2f;

	if (horiz) {
	    image(dot, longOffset, shortOffset, longAxis, shortAxis);
	} else {
	    image(dot, shortOffset, longOffset, shortAxis, longAxis);	    
	}
    }

    void drawAutoLine() {
	double sweep_period = 3.; // s
	
	double t = Config.clock();
	boolean horiz = (int)Math.floor(t / sweep_period) % 2 == 0;
	double offset = MathUtil.fmod(t / sweep_period, 1.);
	double overscan = size.get()/2.;
	offset = -overscan * (1-offset) + (1+overscan) * offset;
	drawLine(horiz, offset);
    }
}

