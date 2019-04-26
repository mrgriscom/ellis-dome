package me.lsdo.sketches.headless;

import processing.core.*;
import me.lsdo.processing.*;
import me.lsdo.processing.interactivity.*;
import me.lsdo.processing.util.*;
import java.awt.*;
import java.awt.image.*;
import org.bytedeco.javacv.*;
import org.bytedeco.javacpp.*;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import java.nio.*;
import java.io.*;
import java.awt.*;

// Note: on linux, as of Ubuntu 17, this requires the Xorg window system, *not* wayland

public class Screencast extends WindowAnimation {

    static final int SUBSAMPLING = 8;
    
    ScreenGrabber grabber;

    String title;
    int pid;

    BooleanParameter retargetAction;
    
    public Screencast(PixelMesh<? extends LedPixel> mesh) {
	super(mesh, Config.getSketchProperty("subsampling", SUBSAMPLING));

	int width = Config.getSketchProperty("width", 512);
	// If height omitted, set equal to width and force no_stretch. Otherwise,
	// x- and y-axes will independently stretch to match the mesh viewport
	// bounding box.
	int height = Config.getSketchProperty("height", -1);

	// Top-left screen coordinate of the screengrab area.
	int xo = Config.getSketchProperty("xoffset", 200);
	int yo = Config.getSketchProperty("yoffset", 200);

	// Try to match viewport to a GUI window, specified by either window title or
	// pid. Not all apps support matching by pid. Title match is prefix-based.
	// First matching window is used. Will hang and keep searching until a matching
	// window is found. Will not readjust viewport if window is moved.
	title = Config.getSketchProperty("title", "");
	pid = Config.getSketchProperty("pid", 0);
	if (trackWindow()) {
	    initWindowCapture(getWindowPlacementUntilFound(title, pid));

	    retargetAction = new BooleanParameter("re-target window", "animation") {
		    @Override
		    public void onTrue() {
			PVector2[] extents = getWindowPlacement(title, pid);
			if (extents != null) {
			    initWindowCapture(extents);
			} else {
			    System.out.println("window not found");
			}
		    }
		};
	    retargetAction.affinity = BooleanParameter.Affinity.ACTION;
	    retargetAction.init(false);
	} else {
	    if (height <= 0) {
		height = width;
		stretchAspect.set(false);
	    }
	    initCapture(width, height, xo, yo);
	}
    }

    private boolean trackWindow() {
	return !title.isEmpty() || pid > 0;
    }

    private void initCapture(int width, int height, int xo, int yo) {
	System.out.println(String.format("%dx%d+%d,%d", width, height, xo, yo));

	Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
	int screenWidth = (int)dim.getWidth();
	int screenHeight = (int)dim.getHeight();

	int clippedXo = Math.max(xo, 0);
	int clippedYo = Math.max(yo, 0);
	int clippedWidth = Math.min(xo + width, screenWidth) - clippedXo;
	int clippedHeight = Math.min(yo + height, screenHeight) - clippedYo;
	if (xo != clippedXo || yo != clippedYo || width != clippedWidth || height != clippedHeight) {
	    xo = clippedXo;
	    yo = clippedYo;
	    width = clippedWidth;
	    height = clippedHeight;
	    System.out.println("NOTE: window is partially off-screen; clipping capture area to " + String.format("%dx%d+%d,%d", width, height, xo, yo));
	}
	
	initViewport(width, height);
	initGrabber(width, height, xo, yo);
    }

    private void initWindowCapture(PVector2[] extents) {
	int width = (int)extents[1].x;
	int height = (int)extents[1].y;
	int xo = (int)extents[0].x;
	int yo = (int)extents[0].y;
	initCapture(width, height, xo, yo);
    }
    
    private void initGrabber(int width, int height, int xo, int yo) {
	if (grabber != null) {
	    grabber.terminate();
	}
	
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
	public void terminate() {}
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
	    grabber.setOption("draw_mouse", "0");
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

	@Override
	public void terminate() {
	    try {
		grabber.stop();
	    } catch (Exception e) {
		System.out.println("failed to stop opencv grabber");
	    }
	}
    }

    // depends on x11
    private PVector2[] getWindowPlacementUntilFound(String targetTitle, int targetPid) {
	final int POLL_INTERVAL = 300;  // ms	
	while(true) {
	    PVector2[] placement = getWindowPlacement(targetTitle, targetPid);
	    if (placement != null) {
		return placement;
	    }
	    System.out.println("window not found");
	    try {
		Thread.sleep(POLL_INTERVAL);
	    } catch (InterruptedException e) { }
	}
    }
    private PVector2[] getWindowPlacement(String targetTitle, int targetPid) {
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

		String windowId = parts[0];
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
		    // xo/yo seems miscalculated in some environments, use a different command that works better
		    int[] xyo = getWindowPlacementForId(windowId);
		    xo = xyo[0];
		    yo = xyo[1];
		    return new PVector2[] {LayoutUtil.V(xo, yo), LayoutUtil.V(width, height)};
		}
	    }
	} catch (Exception e) { }
	return null;
    }
    private int[] getWindowPlacementForId(String windowId) {
	try {
	    Process p = Runtime.getRuntime().exec("xwininfo -id " + windowId);
	    p.waitFor();

	    final int NULL = -9999;
	    int xo = NULL;
	    int yo = NULL;
	    
	    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    String line = "";
	    while ((line = reader.readLine()) != null) {
		if (line.trim().startsWith("Absolute")) {
		    String[] parts = line.split(":");
		    int coord = Integer.parseInt(parts[1].trim());
		    if (parts[0].endsWith("X")) {
			xo = coord;
		    } else if (parts[0].endsWith("Y")) {
			yo = coord;
		    }
		}
	    }
	    if (xo != NULL && yo != NULL) {
		return new int[] {xo, yo};
	    }
	} catch (Exception e) { }
	return null;
    }
    
}

