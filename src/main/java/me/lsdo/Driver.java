package me.lsdo;

import me.lsdo.sketches.headless.*;
import me.lsdo.sketches.headless.kaleidoscope.*;
import me.lsdo.sketches.processing.*;
import me.lsdo.sketches.processing.video.*;
import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import me.lsdo.processing.geometry.dome.*;
import me.lsdo.processing.geometry.prometheus.*;
import processing.core.*;

import java.util.*;

// Launch point for all the supported animations

public class Driver
{
    public static final int FPS_CAP = 60;

    static Map<String, Class> processingSketches = new HashMap<String, Class>();
    static Map<String, Class> headlessSketches = new HashMap<String, Class>();

    static {
	processingSketches.put("pixelflock", PixelFlock.class);
	processingSketches.put("fft", LiveFFT.class);
	processingSketches.put("video", VideoPlayer.class);
	processingSketches.put("stream", VideoCapture.class);
	processingSketches.put("syphon", Syphon.class);
	processingSketches.put("spout", Spout.class);
	processingSketches.put("kinectdepth", KinectDepth.class);
	processingSketches.put("gif", AnimatedGif.class);
	processingSketches.put("ripple", Ripple.class);
	processingSketches.put("layouttest", LayoutTest.class);
	
	headlessSketches.put("black", Black.class);
	headlessSketches.put("cloud", Cloud.class);
	headlessSketches.put("dontknow", DontKnow.class);
	headlessSketches.put("gridtest", TriangularGridTest.class);
	headlessSketches.put("harmonics", Harmonics.class);
	headlessSketches.put("kaleidoscope", KLRainbow.class);
	headlessSketches.put("imgkaleidoscope", KLBackgroundImage.class);
	headlessSketches.put("moire", Moire.class);
	headlessSketches.put("rings", Rings.class);
	headlessSketches.put("screencast", Screencast.class);
	headlessSketches.put("tube", Tube.class);
	headlessSketches.put("twinkle", Twinkle.class);
	headlessSketches.put("binary", Binary.class);
	headlessSketches.put("fctest", FadecandyTest.class);
    }

    public static PixelMesh<DomePixel> makeDome() {
	return new Dome(new OPC());
    }

    public static PixelMesh<WingPixel> makePrometheus() {
	OPC[] opcs = Config.getConfig().makeOPCs(2);
	OPC opcLeft = opcs[0];
	OPC opcRight = opcs[1];
	return new Prometheus(opcLeft, opcRight);
    }

    public static PixelMesh<? extends LedPixel> makeGeometry() {
	String geom = Config.getConfig().geomType;
	if (geom.equals("lsdome")) {
	    return makeDome();
	} else if (geom.equals("prometheus")) {
	    return makePrometheus();
	} else {
	    throw new RuntimeException("can't happen");
	}
    }

    public static ProcessingAnimation makeCanvas(PApplet app) {
        return new ProcessingAnimation(app, Driver.makeGeometry());
    }

    public static void main(String[] args){
	String sketchName = (args.length > 0 ? args[0] : "");
	if (processingSketches.containsKey(sketchName)) {
	    RunProcessing(sketchName);
	} else if (headlessSketches.containsKey(sketchName)) {
	    RunAnimation(makeGeometry(), sketchName);
	} else {
	    List<String> sketches = new ArrayList<String>();
	    sketches.addAll(processingSketches.keySet());
	    sketches.addAll(headlessSketches.keySet());
	    Collections.sort(sketches);
	    System.out.println("available sketches:");
	    for (String s : sketches) {
		System.out.println(s);
	    }
	    throw new RuntimeException("unrecognized sketch '" + sketchName + "'");
	}
    }

    private static void RunProcessing(String name) {
	Class<PApplet> sketch = processingSketches.get(name);
	PApplet app;
	try {
	    app = sketch.newInstance();
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	app.runSketch(new String[] {sketch.getName()}, app);
	// if we modify the 'app' object, calling app.main() seems to reset it;
	// runSketch() still seems to do the trick, so use that unless a reason not to?
	//app.main(new String[] {sketch.getName()});
    }
    
    private static void RunAnimation(PixelMesh mesh, String name) {
	Class<PixelMeshAnimation> sketch = headlessSketches.get(name);
        PixelMeshAnimation animation;
	try {
	    animation = sketch.getConstructor(PixelMesh.class).newInstance(new Object[] {mesh});
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}

	System.out.println("Starting " + name);
	animation.run(FPS_CAP);
    }
}
