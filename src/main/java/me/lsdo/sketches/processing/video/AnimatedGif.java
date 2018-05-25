package me.lsdo.sketches.processing.video;

import gifAnimation.*;
import processing.core.*;
import processing.video.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import java.util.Arrays;

public class AnimatedGif extends VideoBase {

    final String DEMO_GIF = "res/gifs/8.gif";
    
    Gif animGif;
    
    public void loadMedia() {
	String path = Config.getSketchProperty("path", DEMO_GIF);
	if (path.isEmpty()) {
	    throw new RuntimeException("must specify gif path in sketch.properties!");
	}
	animGif = new Gif(this, path);
	animGif.loop();
    }

    public PImage nextFrame() {
	return animGif;
    }
    
}

