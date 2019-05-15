package me.lsdo.sketches.headless;

import me.lsdo.processing.*;
import me.lsdo.processing.util.*;
import me.lsdo.processing.interactivity.*;
import java.util.*;
import java.io.*;
import com.google.gson.*;
import com.google.gson.stream.*;

public class FadecandyTest extends PixelMeshAnimation<LedPixel> {

    int FC_STRAND_LEN = 64;
    int FC_MAX_STRANDS = 8;
    
    public static enum PixelMode implements EnumParameter.CaptionedEnum {
	STATIC("full brightness"),
	RAMP(null),
	ANIMATE(null);

	public final String caption;
	PixelMode(String caption) {
	    this.caption = caption;
	}
	public String caption() {
	    return caption;
	}
    }

    static class FcServer {
	PhysicalPixel[] pixels;
	int numFcs;
    }
    
    static class PhysicalPixel {
	int opc;
	String fc_uuid;
	int fc;
	int strand;
	// pixel index within strand
	int ix;
	// pixel index for fadecandy
	int ixInFc;
    }

    List<FcServer> servers;
    HashMap<LedPixel, PhysicalPixel> pixels;

    DynamicEnumParameter<Integer> opcChannel;
    DynamicEnumParameter<Integer> fc;
    DynamicEnumParameter<Integer> strand;
    EnumParameter<PixelMode> pixelMode;
    
    public FadecandyTest(PixelMesh<LedPixel> mesh) {
	super(mesh);

	// parse fc configs for each fc server / opc channel
	servers = new ArrayList<FcServer>();
	for (int i = 0; i < mesh.opcs.size(); i++) {
	    servers.add(parseFcServer(i));
	}

	// verify that sizes match
	int[] pixelCounts = new int[servers.size()];
	for (LedPixel c : mesh._allPixels()) {
	    pixelCounts[mesh.getOpcChannel(c)] += 1;
	}
	boolean sizeMismatch = false;
	for (int i = 0; i < servers.size(); i++) {
	    int serverPixelCount = servers.get(i).pixels.length;
	    if (serverPixelCount != pixelCounts[i]) {
		System.out.println("server " + (i + 1) + ": mismatch between physical (" + serverPixelCount + ") and virtual (" + pixelCounts[i] + ") pixels");
		sizeMismatch = true;
	    }
	}
	if (sizeMismatch) {
	    throw new RuntimeException("config(s) don't match geometry");
	}

	// map virtual pixels to fc-controlled physical pixels
	pixels = new HashMap<LedPixel, PhysicalPixel>();
	pixelCounts = new int[servers.size()];
	for (LedPixel c : mesh._allPixels()) {
	    int serverIx = mesh.getOpcChannel(c);
	    PhysicalPixel px = servers.get(serverIx).pixels[pixelCounts[serverIx]];
	    pixels.put(c, px);
	    pixelCounts[serverIx] += 1;
	}

	if (servers.size() > 1) {
	    opcChannel = intEnumParam("opc channel", servers.size());
	}

	int maxFcs = 0;
	for (FcServer server : servers) {
	    maxFcs = Math.max(maxFcs, server.numFcs);
	}
	fc = intEnumParam("fadecandy", maxFcs);

	strand = intEnumParam("strand", 8);
	pixelMode = new EnumParameter<PixelMode>("pixels", "animation", PixelMode.class);
	pixelMode.init(PixelMode.ANIMATE);
    }

    DynamicEnumParameter<Integer> intEnumParam(String name, int n) {
	Integer[] vals = new Integer[n + 1];
	for (int i = 0; i < vals.length; i++) {
	    vals[i] = i - 1;
	}
	Map<Integer, String> captions = new HashMap<Integer, String>();
	captions.put(-1, "all");
	DynamicEnumParameter<Integer> param = new DynamicEnumParameter<Integer>(name, "animation", vals, captions);
	param.init(-1);
	return param;
    }
    
    @Override
    protected int drawPixel(LedPixel c, double t) {
	PhysicalPixel px = pixels.get(c);
	
	boolean active = true;
	if (!(opcChannel == null || opcChannel.get() == -1 || opcChannel.get() == px.opc)) {
	    active = false;
	}
	if (!(fc.get() == -1 || fc.get() == px.fc)) {
	    active = false;
	}
	if (!(strand.get() == -1 || strand.get() == px.strand)) {
	    active = false;
	}

	double hue = (double)px.fc / servers.get(px.opc).numFcs;
	// stagger to increase distinguishability
	double sat = (double)(4 * (px.strand % 2) + px.strand / 2) / (FC_MAX_STRANDS - 1);
        double min_sat = .25;
        double max_sat = 1.;
	sat = min_sat*(1-sat) + max_sat*sat;
	
	double inactive_bright = .1;
	double bright = inactive_bright;
	if (active) {
	    switch (pixelMode.get()) {
	    case STATIC:
		bright = 1.;
		break;
	    case RAMP:
		int static_ramp_len = 10;
		bright = (double)(px.ix % static_ramp_len + 1) / static_ramp_len;
		break;
	    case ANIMATE:
		double creep_speed = 20;
		double anim_ramp_len = 100;
		bright = MathUtil.fmod((px.ixInFc - creep_speed * t) / anim_ramp_len, 1.);
		break;
	    }
	}
	
        return OpcColor.getHsbColor(hue, sat, bright);
    }

    static class FcConfigJson {
	FcDeviceJson[] devices;
    }
    static class FcDeviceJson {
	String serial;
	int[][] map;
    }

    FcServer parseFcServer(int i) {
	String configPath = Config.getSketchProperty("fcconfig" + (i > 0 ? (i + 1) : ""),
						     Config.getSketchProperty("fcconfig", ""));
	if (configPath.isEmpty()) {
	    throw new RuntimeException("no fc config set!");
	}

	FcServer server = new FcServer();
	server.pixels = parseFcConfig(configPath, i);

	Set<Integer> fcIds = new HashSet<Integer>();
	for (PhysicalPixel px : server.pixels) {
	    fcIds.add(px.fc);
	}
	server.numFcs = fcIds.size();

	return server;
    }
    
    PhysicalPixel[] parseFcConfig(String path, int opc) {
	FcConfigJson config;
	try {
	    Gson gson = new Gson();
	    InputStream is = new BufferedInputStream(new FileInputStream(new File(path)));
	    JsonReader reader = new JsonReader(new InputStreamReader(is, "UTF-8"));
	    config = gson.fromJson(reader, FcConfigJson.class);
	} catch (IOException e) {
	    throw new RuntimeException("error reading fc config " + path);
	}

	Map<Integer, PhysicalPixel> pixels = new HashMap<Integer, PhysicalPixel>();
	for (int devIx = 0; devIx < config.devices.length; devIx++) {
	    FcDeviceJson fcdev = config.devices[devIx];
	    if (fcdev == null) {
		// can happen with trailing commas in the json
		continue;
	    }
	    // fc uuids may not be unique (in non-prod config), so prepend ix
	    String fcName = devIx + ":" + fcdev.serial;
	    for (int[] strip : fcdev.map) {
		int opcStart = strip[1];
		int fcStart = strip[2];
		int len = strip[3];

		for (int i = 0; i < len; i++) {
		    int opcPx = opcStart + i;
		    int fcPx = fcStart + i;
		    
		    PhysicalPixel px = new PhysicalPixel();
		    px.opc = opc;
		    px.fc_uuid = fcName;
		    px.strand = fcPx / 64;
		    px.ix = fcPx % 64;

		    if (pixels.put(opcPx, px) != null) {
			throw new RuntimeException("duplicate pixel for " + opcPx);
		    }
		}
	    }
	}

	int maxPixel = -1;
	for (int ix : pixels.keySet()) {
	    maxPixel = Math.max(maxPixel, ix);
	}
	PhysicalPixel[] pixelList = new PhysicalPixel[maxPixel + 1];
	for (int i = 0; i < pixelList.length; i++) {
	    PhysicalPixel px = pixels.get(i);
	    if (px == null) {
		throw new RuntimeException("opc pixels not contiguous at " + i);
	    }
	    pixelList[i] = px;
	}

	Map<String, Integer> fcOrder = new HashMap<String, Integer>();
	for (PhysicalPixel px : pixelList) {
	    int fcIx;
	    if (fcOrder.containsKey(px.fc_uuid)) {
		fcIx = fcOrder.get(px.fc_uuid);
		if (fcIx < fcOrder.size() - 1) {
		    System.out.println("warning: fadecandy pixels not contiguous");
		}
	    } else {
		fcIx = fcOrder.size();
		fcOrder.put(px.fc_uuid, fcIx);
	    }
	    px.fc = fcIx;
	}
	int[] fcCounts = new int[fcOrder.size()];
	for (PhysicalPixel px : pixelList) {
	    px.ixInFc = fcCounts[px.fc];
	    fcCounts[px.fc] += 1;
	}
	
	return pixelList;
    }
    
}
