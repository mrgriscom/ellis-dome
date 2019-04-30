// Daniel Shiffman
// All features test

// https://github.com/shiffman/OpenKinect-for-Processing
// http://shiffman.net/p5/kinect/

package me.lsdo.sketches.processing.video;

import org.openkinect.freenect.*;
import org.openkinect.processing.*;
import processing.core.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;

// Play various video streams directly from Kinect.
// This sketch is mostly for debugging.
// See KinectDepth.java for a similar concept but with more tunable parameters.

public class KinectVideo extends VideoBase {

    enum VideoType {
	DEPTH,
	VIDEO
    }
    
    enum VideoMode {
	DEPTH_GREYSCALE(VideoType.DEPTH, "colordepth"),
	DEPTH_COLOR(VideoType.DEPTH, "depth"),
	VIDEO_IR(VideoType.VIDEO, "ir"),
	VIDEO_COLOR(VideoType.VIDEO, "video");

	VideoType type;
	String setting;
	
	VideoMode(VideoType type, String setting) {
	    this.type = type;
	    this.setting = setting;
	}
    }
    
    Kinect kinect;
    VideoMode mode;
    
    public void loadMedia() {
	kinect = new Kinect(this);

	String modeStr = Config.getSketchProperty("kinectmode", VideoMode.DEPTH_COLOR.setting);
	for (VideoMode vm : VideoMode.values()) {
	    if (modeStr.equals(vm.setting)) {
		mode = vm;
		break;
	    }
	}
	if (mode == null) {
	    throw new RuntimeException("unrecognized kinect mode [" + modeStr + "]");
	}

	boolean mirror = Config.getSketchProperty("mirror", true);

	switch (mode.type) {
	case DEPTH:
	    kinect.initDepth();
	    kinect.enableColorDepth(mode == VideoMode.DEPTH_COLOR);
	    break;
	case VIDEO:
	    kinect.initVideo();
	    kinect.enableIR(mode == VideoMode.VIDEO_IR);
	    break;
	}
		
	kinect.enableMirror(mirror);
    }

    public PImage nextFrame() {
	switch (mode.type) {
	case DEPTH:
	    return kinect.getDepthImage();
	case VIDEO:
	    return kinect.getVideoImage();
	default:
	    return null;
	}
    }

}
