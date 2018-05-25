package me.lsdo.sketches.processing.video;

import processing.core.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import codeanticode.syphon.*;

// Syphon only works on mac!

public class Syphon extends VideoBase {

    SyphonClient client;
    PGraphics canvas;
    
    public void loadMedia() {
	System.out.println("Available Syphon servers:");
	System.out.println(SyphonClient.listServers());

	client = new SyphonClient(this);
	System.out.println("connected to " + client.getServerName());
    }

    public PImage nextFrame() {
	if (client.newFrame()) {
	    canvas = client.getGraphics(canvas);
	}
	return canvas;
    }

}
