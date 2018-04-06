package me.lsdo.sketches.headless;

import me.lsdo.processing.*;
import java.util.*;

public class Harmonics extends DomeAnimation<DomePixel> {

    public static final double BASELINE_CYCLE_LENGTH = 30. / 9;  // sec/panel
    
    Map<TriCoord, Integer> panelCount;
    double cycleLength;
    
    int permutations[][] = {
	{0, 1, 2},
	{0, 2, 1},
	{1, 0, 2},
	{1, 2, 0},
	{2, 0, 1},
	{2, 1, 0}
    };
    
    public Harmonics(Dome dome) {
	super(dome);

	// Iteration order matters. Currently follows the order visible to the fadecandy.
	// If this is radially symmetric, it yields nice patterns.
	panelCount = new HashMap<TriCoord, Integer>();
	for (DomePixel c : dome.coords) {
	    if (!panelCount.containsKey(c.panel)) {
		panelCount.put(c.panel, panelCount.size());
	    }
	}
	cycleLength = Config.getSketchProperty("cycle", BASELINE_CYCLE_LENGTH) * panelCount.size();
    }
    
    private double cycle(double t, double period, double min, double max) {
	return .5*(min+max) - .5*(max-min) * Math.cos(t / period * (2*Math.PI));
    }

    private double lum(double t, double period) {
	double k =  cycle(t, period, 0, 1);
	// gamma correction handled by fadecandy?
	return k;
    }
    
    @Override
    public int drawPixel(DomePixel c, double t) {
	int panelId = panelCount.get(c.panel);

	int round = (int)Math.floor(t / cycleLength);
	int mode = 0;
	int permutation = (round) % 6;
	
	double[] channels = new double[3];
	for (int ch = 0; ch < channels.length; ch++) {
	    int harmonic = (mode == 0 ?
			    ch * panelCount.size() + panelId + 1 :
			    panelId * channels.length + ch + 1);
	    channels[ch] = 255. * lum(t, cycleLength / harmonic);
	}

	int permu[] = permutations[permutation];
	return OpcColor.getRgbColor(
				    (int)channels[permu[0]],
				    (int)channels[permu[1]],
				    (int)channels[permu[2]]);
    }
}

