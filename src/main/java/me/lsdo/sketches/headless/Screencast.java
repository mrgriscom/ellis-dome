package me.lsdo.sketches.headless;

import processing.core.*;
import me.lsdo.processing.*;
import java.awt.*;
import java.awt.image.*;
import org.bytedeco.javacv.*;
import org.bytedeco.javacpp.*;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import java.nio.*;
import java.io.*;

public class Screencast extends WindowAnimation {

    static final int SUBSAMPLING = 8;
    
    ScreenGrabber grabber;
    
    public Screencast(PixelMesh<? extends LedPixel> dome) {
	super(dome, Config.getSketchProperty("subsampling", SUBSAMPLING));

	int width = Config.getSketchProperty("width", 512);
	// If height omitted, set equal to width and force no_stretch. Otherwise,
	// x- and y-axes will independently stretch to match the dome viewport
	// bounding box.
	int height = Config.getSketchProperty("height", -1);

	// Top-left screen coordinate of the screengrab area.
	int xo = Config.getSketchProperty("xoffset", 200);
	int yo = Config.getSketchProperty("yoffset", 200);

	// Align dome axis to horizontal or vertical axis of screen.
	// TODO: this belongs in the higher-level dome transform
	boolean alignHorizontal = Config.getSketchProperty("align_horiz", true);

	// Force 1:1 pixels rather than stretching asymmetrically to fit viewport.
	boolean preserveAspect = Config.getSketchProperty("no_stretch", false);

	// Further shrink the screencap window by these factors, in order to get more
	// of the window corners to fall within the dome's renderable area.
	double xscale = Config.getSketchProperty("xscale", 1.);
	double yscale = Config.getSketchProperty("yscale", 1.);

	// Try to match viewport to a GUI window, specified by either window title or
	// pid. Not all apps support matching by pid. Title match is prefix-based.
	// First matching window is used. Will hang and keep searching until a matching
	// window is found. Will not readjust viewport if window is moved.
	String title = Config.getSketchProperty("title", "");
	int pid = Config.getSketchProperty("pid", 0);
	if (!title.isEmpty() || pid > 0) {
	    PVector2 windowPlacement[] = getWindowPlacement(title, pid);
	    width = (int)windowPlacement[1].x;
	    height = (int)windowPlacement[1].y;
	    xo = (int)windowPlacement[0].x;
	    yo = (int)windowPlacement[0].y;
	    System.out.println(String.format("%dx%d+%d,%d", width, height, xo, yo));
	}

	if (!alignHorizontal) {
	    // TODO does this belong here?
	    dome.transform = dome.transform.compoundTransform(new LayoutUtil.Transform() {
		    public PVector2 transform(PVector2 p) {
			return LayoutUtil.Vrot(p, Math.PI / 2.);
		    }
		});
	}

	if (height <= 0) {
	    height = width;
	    preserveAspect = true;
	}
	initViewport(width, height, preserveAspect, xscale, yscale);
	initGrabber(width, height, xo, yo);
    }

    private void initGrabber(int width, int height, int xo, int yo) {      
	String grabberName = Config.getSketchProperty("grabber", "opencv");
	if (grabberName.equals("robot")) {
	    grabber = new RobotGrabber(width, height, xo, yo);
	} else if (grabberName.equals("opencv")) {
	    grabber = new OpenCvGrabber(width, height, xo, yo);
	} else {
	    throw new RuntimeException("unknown grabber type: " + grabberName);
	}
    }

    @Override
    public void captureFrame() {
	grabber.captureFrame();	
    }
    
    @Override
    public int getPixel(int x, int y) {
	return grabber.getPixel(x, y);
    }
    
    public static abstract class ScreenGrabber {
	int width;
	int height;
	int x0;
	int y0;

	public ScreenGrabber(int width, int height, int xo, int yo) {
	    this.width = width;
	    this.height = height;
	    this.x0 = xo;
	    this.y0 = yo;
	}
	
	public abstract void captureFrame();
	public abstract int getPixel(int x, int y);
    }

    // simple and portable, but slow as balls
    public static class RobotGrabber extends ScreenGrabber {
	Robot robot;
	BufferedImage frame;

	public RobotGrabber(int width, int height, int xo, int yo) {
	    super(width, height, xo, yo);

	    try {
		robot = new Robot();
	    } catch (AWTException e) {
		throw new RuntimeException(e);
	    }
	}
	
	public void captureFrame() {
	    frame = robot.createScreenCapture(new Rectangle(x0, y0, width, height));
	}
	
	public int getPixel(int x, int y) {
	    return frame.getRGB(x, y);
	}
    }

    // decent framerate, but still has a slight delay
    public static class OpenCvGrabber extends ScreenGrabber {
	FFmpegFrameGrabber grabber;
	OpenCVFrameConverter.ToIplImage converter;
	IplImage img;
	ByteBuffer bytes;

	public OpenCvGrabber(int width, int height, int xo, int yo) {
	    super(width, height, xo, yo);

	    // Don't know the appropriate settings/formats for other OSes, but I think they
	    // theoretically are supported with the right incantations.
	    grabber = new FFmpegFrameGrabber(":0.0+" + x0 + "," + y0);
	    grabber.setFormat("x11grab");
	    grabber.setImageWidth(width);
	    grabber.setImageHeight(height);
	    try {
		grabber.start();
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	    converter = new OpenCVFrameConverter.ToIplImage();
	}
	
	public void captureFrame() {
	    org.bytedeco.javacv.Frame f;
	    try {
		f = grabber.grab();
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	    img = converter.convert(f);
	    bytes = img.getByteBuffer();
	}
	
	public int getPixel(int x, int y) {
	    int ix = img.nChannels() * (width * y + x);
	    int b = bytes.get(ix) & 0xFF; 
	    int g = bytes.get(ix + 1) & 0xFF;
	    int r = bytes.get(ix + 2) & 0xFF;
	    return (r << 16) | (g << 8) | b;
	}
    }

    // depends on x11
    private PVector2[] getWindowPlacement(String targetTitle, int targetPid) {
	final int POLL_INTERVAL = 300;  // ms
	
	while(true) {
	    try {
		Process p = Runtime.getRuntime().exec("wmctrl -l -G -p");
		p.waitFor();

		BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = "";
		while ((line = reader.readLine()) != null) {
		    String[] parts = line.split(" +");
		    if (parts.length < 9) {
			continue;
		    }

		    int pid = Integer.parseInt(parts[2]);
		    int xo = Integer.parseInt(parts[3]);
		    int yo = Integer.parseInt(parts[4]);
		    int width = Integer.parseInt(parts[5]);
		    int height = Integer.parseInt(parts[6]);
		    StringBuilder sb = new StringBuilder();
		    for (int i = 8; i < parts.length; i++) {
			sb.append(parts[i]);
			if (i < parts.length - 1) {
			    sb.append(" ");
			}
		    }
		    String title = sb.toString();
		    
		    if ((targetPid > 0 && pid == targetPid) ||
			(!targetTitle.isEmpty() && title.toLowerCase().startsWith(targetTitle.toLowerCase()))) {
			System.out.println("found window");
			return new PVector2[] {LayoutUtil.V(xo, yo), LayoutUtil.V(width, height)};
		    }
		}

		System.out.println("window not found");
		Thread.sleep(POLL_INTERVAL);
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	}
    }
    
}

