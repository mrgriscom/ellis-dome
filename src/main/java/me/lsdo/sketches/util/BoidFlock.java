package me.lsdo.sketches.util;

/**
 * Created by shen on 2016/09/17.
 */

import java.util.ArrayList;

public class BoidFlock {
    public ArrayList<Boid> boids; // An ArrayList for all the boids
    int currentHue = 60;

    int worldWidth;
    int worldHeight;
    int fringe;

    public static interface BoidManipulator {
	public void manipulate(Boid b);
    }
    BoidManipulator manipulator;
    
    public BoidFlock(int worldWidth, int worldHeight, int fringe, BoidManipulator manipulator) {
	this.worldWidth = worldWidth;
	this.worldHeight = worldHeight;
	this.fringe = fringe;

	this.manipulator = manipulator;
	
        boids = new ArrayList<Boid>(); // Initialize the ArrayList
    }

    public int totalWidth() { return worldWidth + 2*fringe; }
    public int totalHeight() { return worldHeight + 2*fringe; }
    
    public void run() {
        for (Boid b : boids) {
            b.run(boids);  // Passing the entire list of boids to each boid individually
	}
	if (manipulator != null) {
	    for (Boid b : boids) {
		manipulator.manipulate(b);
	    }
	}
    }

    public void cycleHue() {
        int newHue = currentHue + 1;
        if (newHue > 100) {
            newHue = newHue - 100;
        }
        currentHue=newHue;
        for (Boid b : boids) {
            b.setHue(currentHue);
        }
    }

    public void setBrightness(int brightness) {
        for (Boid b : boids) {
            b.setBrightness(brightness);
        }
    }

    public void scatterFlock() {
        for (Boid b : boids) {
            b.sepWeight = 10.0f;
            b.aliWeight= 0.0f;
            b.cohWeight = 0.0f;
            b.maxspeed = 2 * Boid.MAX_SPEED;
        }
    }

    public void collectFlock() {
        for (Boid b : boids) {
            b.sepWeight = 1.5f;
            b.aliWeight= 1.0f;
            b.cohWeight = 1.0f;
            b.maxspeed = Boid.MAX_SPEED;
        }
    }

    public void addBoid(Boid b) {
        boids.add(b);
	b.flock = this;
    }
}
