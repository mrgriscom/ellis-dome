package me.lsdo;

import me.lsdo.sketches.headless.*;
import me.lsdo.sketches.processing.*;
import me.lsdo.processing.*;
import processing.core.*;

import java.util.*;

public class Driver
{
    public static final int FPS_CAP = 60;

    static Map<String, Class> processingSketches = new HashMap<String, Class>();
    static Map<String, Class> headlessSketches = new HashMap<String, Class>();

    static {
	processingSketches.put("pixelflock", PixelFlock.class);
	processingSketches.put("particlefft", ParticleFFT.class);
	processingSketches.put("video", VideoPlayer.class);
	processingSketches.put("stream", VideoCapture.class);
	processingSketches.put("syphon", Syphon.class);
	processingSketches.put("spout", Spout.class);
	processingSketches.put("kinectdepth", KinectDepth.class);
	processingSketches.put("kinectflock", KinectFlock.class);

	headlessSketches.put("cloud", Cloud.class);
	headlessSketches.put("dontknow", DontKnow.class);
	headlessSketches.put("gridtest", GridTest.class);
	headlessSketches.put("harmonics", Harmonics.class);
	headlessSketches.put("kaleidoscope", Kaleidoscope.class);
	headlessSketches.put("moire", Moire.class);
	headlessSketches.put("pixeltest", PixelTest.class);
	headlessSketches.put("rings", Rings.class);
	headlessSketches.put("screencast", Screencast.class);
	headlessSketches.put("tube", Tube.class);
	headlessSketches.put("twinkle", Twinkle.class);
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

    public static CanvasSketch makeCanvas(PApplet app) {
        return new CanvasSketch(app, Driver.makeGeometry());
    }
    
    public static void main(String[] args){
	String sketchName = (args.length > 0 ? args[0] : "");
	if (processingSketches.containsKey(sketchName)) {
	    Class<PApplet> sketch = processingSketches.get(sketchName);
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
	} else if (headlessSketches.containsKey(sketchName)) {
	    RunAnimation(makeGeometry(), sketchName, 0);
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

    private static void RunAnimation(PixelMesh dome, String name, int duration)
    {
	Class<DomeAnimation> sketch = headlessSketches.get(name);
        DomeAnimation animation;	
	try {
	    animation = sketch.getConstructor(PixelMesh.class).newInstance(new Object[] {dome});
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}	

	System.out.println("Starting " + name);
        RunAnimation(animation, duration);
    }

    private static void RunAnimation(DomeAnimation animation, int duration)
    {
        double t = 0;
        while (t < duration || duration == 0)
        {
            t = Config.clock();
            animation.draw(t);
            double s = Config.clock();
            int ms = (int)((s - t) * 1000);
            try {
                Thread.sleep(Math.max((int)(1000./FPS_CAP) - ms, 0));
            } catch (InterruptedException ie) {
            }
        }
    }
}
