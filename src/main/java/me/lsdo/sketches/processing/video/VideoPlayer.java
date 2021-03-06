package me.lsdo.sketches.processing.video;

import processing.core.*;
import processing.video.*;
import me.lsdo.Driver;
import me.lsdo.processing.*;
import me.lsdo.processing.interactivity.*;
import me.lsdo.processing.util.*;
import java.util.Arrays;

// Play video from a file.

// Keyboard controls:
// p: play/plause
// .: ff 5 sec
// ,: rewind 5 sec
// Also controllable from web UI.

// TODO support contrast stretch?

public class VideoPlayer extends VideoBase {

    //final String DEMO_VIDEO = "res/mov/bm2014_mushroom_cloud.mp4";
    final String DEMO_VIDEO = "res/mov/trexes_thunderdome.mp4";
    
    static final double[] skips = {5, 60};

    Movie mov;
    boolean repeat;
    BooleanParameter playing;
    BooleanParameter[] skipActions;
    NumericParameter timeline;
    BooleanParameter mute;
    boolean updateFreezeFrame = false;
    
    public void loadMedia() {
	String path = Config.getSketchProperty("path", DEMO_VIDEO);
	if (path.isEmpty()) {
	    throw new RuntimeException("must specify video path in sketch.properties!");
	}
	repeat = Config.getSketchProperty("repeat", true);
	double startAt = Config.getSketchProperty("skip", 0.);
	
        mov = new Movie(this, path);
	// begin playback so we have access to duration
	mov.play();

	mute = new BooleanParameter("mute", "hidden") {
		@Override
		public void onTrue() {
		    mov.volume(0);
		}

		@Override
		public void onFalse() {
		    mov.volume(1);
		}
	    };
	mute.init(Config.getSketchProperty("mute", false));
	
	playing = new BooleanParameter("playing", "animation") {
		@Override
		public void onTrue() {
		    mov.play();
		    updateFreezeFrame = false;
		    updateRemainingTime();
		}

		@Override
		public void onFalse() {
		    mov.pause();
		    timeline.set((double)mov.time());
		    updateRemainingTime();
		}
	    };
	playing.trueCaption = "play";
	playing.falseCaption = "pause";

	skipActions = new BooleanParameter[2*skips.length];
	int i = 0;
	for (final double skip : skips) {
	    for (final boolean forward : new boolean[] {true, false}) {
		String sskip = "" + skip;
		final int iskip = (int)Math.round(skip);
		if (skip == iskip) {
		    sskip = "" + iskip;
		}
		BooleanParameter skipAction = new BooleanParameter((forward ? "\u23e9" : "\u23ea") + " " + sskip + "s", "animation") {
			@Override
			public void onTrue() {
			    relJump((forward ? 1 : -1) * skip);
			}
		    };
		skipAction.affinity = BooleanParameter.Affinity.ACTION;
		skipAction.init(false);
		skipActions[i] = skipAction;
		i++;
	    }
	}

	// timeline is only updated on discrete actions, not continuously as the video
	// plays, to avoid flooding the client with updates
	timeline = new NumericParameter("progress", "animation") {
		@Override
		public void onSet() {
		    jump(get());
		}
	    };
	timeline.min = 0;
	timeline.max = mov.duration();
	
	if (repeat) {
	    mov.loop();
	}
	playing.init(true);
	timeline.init(startAt);

        System.out.println("duration: " + mov.duration());
    }

    public PImage nextFrame() {
	if (mov.available()) {
	    mov.read();

	    if (updateFreezeFrame) {
		mov.pause();
		updateFreezeFrame = false;
	    }
	}
	return mov;
    }

    public void relJump(double t) {
	t += mov.time();
	if (repeat) {
	    t = MathUtil.fmod(t, mov.duration());
	} else {
            t = Math.max(0, Math.min(mov.duration(), t));
	}
	timeline.set(t);
    }
    
    public void jump(double t) {
	if (t == mov.time()) {
	    return;
	}
	
	mov.jump((float)t);
	updateRemainingTime();
	
	if (!playing.get()) {
	    updateFreezeFrame = true;
	    mov.play();
	}
	
	System.out.println(String.format("%.2f / %.2f", t, mov.duration()));
    }

    public void updateRemainingTime() {
	if (!repeat) {
	    InputControl.DurationControlJson msg = new InputControl.DurationControlJson();
	    msg.duration = (playing.get() ? mov.duration() - mov.time() : -1);
	    canvas.ctrl.broadcast(msg);
	}
    }
    
    public void keyPressed() {
        if (this.key == '.') {
	    skipActions[0].trigger();
        } else if (this.key == ',') {
	    skipActions[1].trigger();
        } else if (this.key == 'p') {
	    playing.toggle();
        }
    }

}
