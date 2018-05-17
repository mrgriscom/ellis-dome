package me.lsdo.sketches.processing;

import processing.core.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import spout.*;

// Spout only works on windows!
// Can't yet get this to build, hence the commented-out code below

public class Spout extends VideoBase {

    Spout client;
    PGraphics canvas;
    
    public void loadMedia() {
	//client = new Spout(this);
	// TODO how to connect to a sender?
	canvas = createGraphics(1, 1, PConstants.P2D); // spout will resize
    }

    public PImage nextFrame() {
	//canvas = client.receiveTexture(canvas);
	return canvas;
    }

}
