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
	processingSketches.put("livevideo", VideoCapture.class);
	//processingSketches.put("kinectdepth", KinectDepth.class);
	processingSketches.put("kinectflock", KinectFlock.class);

	headlessSketches.put("cloud", Cloud.class);
	headlessSketches.put("dontknow", DontKnow.class);
	headlessSketches.put("gridtest", GridTest.class);
	headlessSketches.put("harmonics", Harmonics.class);
	headlessSketches.put("kaleidoscope", Kaleidoscope.class);
	headlessSketches.put("noire", Noire.class);
	headlessSketches.put("pixeltest", PixelTest.class);
	headlessSketches.put("rings", Rings.class);
	headlessSketches.put("screencast", Screencast.class);
	headlessSketches.put("snowflake", Snowflake.class);
	headlessSketches.put("tube", Tube.class);
	headlessSketches.put("twinkle", Twinkle.class);
    }

    public static PixelMesh<DomePixel> makeDome() {
	return new Dome(new OPC());
    }
    
    public static PixelMesh<WingPixel> makePrometheus() {
	// TODO add checking properties for 2nd opc server
	OPC opcLeft = new OPC();
	OPC opcRight = new OPC(opcLeft.getHost(), opcLeft.getPort() + 1);
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
	if (args.length > 0 && processingSketches.containsKey(args[0])) {
	    Class<PApplet> sketch = processingSketches.get(args[0]);
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
	} else {
	    // Headless
	    PixelMesh<? extends LedPixel> mesh = makeGeometry();
	    if (args.length > 0) {
		RunAnimation(mesh, args[0], 0);
	    } else {
		// TODO this should probably be replaced by last year's python launcher
		// script, which can also shuffle parameters within each sketch, and
		// avoid running the same sketch twice in a row.
		System.out.println("shuffle mode");

		Random random = new Random();
		Set<String> excludeFromShuffle = new HashSet<String>(Arrays.asList(new String[] {
			    "gridtest",
			    "screencast"
			}));
		Set<String> sketches = headlessSketches.keySet();
		sketches.removeAll(excludeFromShuffle);
		List<String> animations = new ArrayList<String>(sketches);
		while (true) {
		    RunAnimation(mesh, animations.get(random.nextInt(animations.size())), 60);
		}
	    }
	}
    }

    private static void RunAnimation(PixelMesh dome, String name, int duration)
    {
	if (!headlessSketches.containsKey(name)) {
	    throw new RuntimeException("animation [" + name + "] not recognized");
	}

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
        long start = System.currentTimeMillis();
        double t = 0;
        while (t < duration || duration == 0)
        {
            t = (System.currentTimeMillis() - start) / 1000d;
            animation.draw(t);
            double s = (System.currentTimeMillis() - start) / 1000d;
            int ms = (int)((s - t) * 1000);
            try {
                Thread.sleep(Math.max((int)(1000./FPS_CAP) - ms, 0));
            } catch (InterruptedException ie) {
            }
        }
    }
}
