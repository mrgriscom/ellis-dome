package me.lsdo.sketches.util;

/**
 * Created by shen on 2016/09/17.
 *
 * Java-ising code Mel used.
 */

import processing.core.*;
import java.util.ArrayList;
import java.util.Random;
import me.lsdo.processing.util.*;

public // The Boid class

class Boid {

    public PVector location;
    public PVector velocity;
    PVector acceleration;
    public float size;
    float maxforce;    // Maximum steering force
    float maxspeed;    // Maximum speed
    int currentHue;

    int sat;
    int light = 75;
    int hueOffset;

    float sepWeight = 1.5f;
    float aliWeight = 1.0f;
    float cohWeight = 1.0f;

    BoidFlock flock;
    
    static final float BOID_SIZE = 6.0f; //how big the boids are
    static final int MAX_SPEED = 1; //how fast the boids can move
    static final int COLOUR_RANGE = 10; //hue, saturation and brightness values for visible boids can be current +- this

    int[] boidColor;

    private Random random;
    
    public Boid(float x, float y, int hue) {
	this(x, y, hue, PVector.random2D(), BOID_SIZE);
    }

    public Boid(float x, float y, int hue, PVector velocity, float size) {
        acceleration = new PVector(0, 0);
        this.velocity = velocity;
        location = new PVector(x, y);

        this.size = size;
        maxspeed = MAX_SPEED;
        maxforce = 0.03f;

        currentHue = hue;

        random = new Random(0);

        sat = 90 + (COLOUR_RANGE - random.nextInt(COLOUR_RANGE*2));
        hueOffset = (COLOUR_RANGE - random.nextInt(COLOUR_RANGE*2));
    }

    public PVector2 getLocation() {
	return new PVector2(location.x, location.y);
    }

    public void translate(PVector2 offset) {
	location.add(new PVector(offset.x, offset.y));
    }
    
    void run(ArrayList<Boid> boids) {
        flock(boids);
        update();
        borders();
        //render();
    }

    void setHue(int hue) {
        currentHue = hue;
    }

    void setBrightness(int brightness) {
        light = brightness;
        sat = brightness;
    }

    public void applyForce(PVector force) {
        // We could add mass here if we want A = F / M
        acceleration.add(force);
    }

    // We accumulate a new acceleration each time based on three rules
    void flock(ArrayList<Boid> boids) {
        PVector sep = separate(boids);   // Separation
        PVector ali = align(boids);      // Alignment
        PVector coh = cohesion(boids);   // Cohesion
        // Arbitrarily weight these forces
        sep.mult(sepWeight);
        ali.mult(aliWeight);
        coh.mult(cohWeight);
        // Add the force vectors to acceleration
        applyForce(sep);
        applyForce(ali);
        applyForce(coh);
    }

    // Method to update location
    void update() {

        // Update velocity
        velocity.add(acceleration);
        // Limit speed
        velocity.limit(maxspeed);
        location.add(velocity); // FIXME move to translate()

        // Reset accelertion to 0 each cycle
        acceleration.mult(0);
        //update color
    }

    // A method that calculates and applies a steering force towards a target
    // STEER = DESIRED MINUS VELOCITY
    PVector seek(PVector target) {
        PVector desired = PVector.sub(target, location);  // A vector pointing from the location to the target
        // Scale to maximum speed
        desired.normalize();
        desired.mult(maxspeed);

        // Above two lines of code below could be condensed with new PVector setMag() method
        // Not using this method until Processing.js catches up
        // desired.setMag(maxspeed);

        // Steering = Desired minus Velocity
        PVector steer = PVector.sub(desired, velocity);
        steer.limit(maxforce);  // Limit to maximum steering force
        return steer;
    }

    public int[] getColour() {
        int hue = currentHue + hueOffset;
        int[] col = new int[] {
                hue, sat, light, 100
        };
        return col;
    }

    float getRadius() {
	return size;
    }
    
    // Wraparound
    void borders() {
        if ((location.x) < -getRadius()-flock.fringe) location.x = flock.worldWidth+getRadius();
        if ((location.y) < -getRadius()-flock.fringe) location.y = flock.worldHeight+getRadius();
        if ((location.x) > ((flock.worldWidth+getRadius())+flock.fringe)) location.x = -getRadius();
        if ((location.y) > ((flock.worldHeight+getRadius())+flock.fringe)) location.y = -getRadius();
    }

    // Separation
    // Method checks for nearby boids and steers away
    PVector separate (ArrayList<Boid> boids) {
        float desiredseparation = 20.0f;
        PVector steer = new PVector(0, 0, 0);
        int count = 0;
        // For every boid in the system, check if it's too close
        for (Boid other : boids) {
            float d = PVector.dist(location, other.location);
            // If the distance is greater than 0 and less than an arbitrary amount (0 when you are yourself)
            if ((d > 0) && (d < desiredseparation)) {
                // Calculate vector pointing away from neighbor
                PVector diff = PVector.sub(location, other.location);
                diff.normalize();
                diff.div(d);        // Weight by distance
                steer.add(diff);
                count++;            // Keep track of how many
            }
        }
        // Average -- divide by how many
        if (count > 0) {
            steer.div((float)count);
        }

        // As long as the vector is greater than 0
        if (steer.mag() > 0) {
            // First two lines of code below could be condensed with new PVector setMag() method
            // Not using this method until Processing.js catches up
            // steer.setMag(maxspeed);

            // Implement Reynolds: Steering = Desired - Velocity
            steer.normalize();
            steer.mult(maxspeed);
            steer.sub(velocity);
            steer.limit(maxforce);
        }
        return steer;
    }

    // Alignment
    // For every nearby boid in the system, calculate the average velocity
    PVector align (ArrayList<Boid> boids) {
        float neighbordist = 50;
        PVector sum = new PVector(0, 0);
        int count = 0;
        for (Boid other : boids) {
            float d = PVector.dist(location, other.location);
            if ((d > 0) && (d < neighbordist)) {
                sum.add(other.velocity);
                count++;
            }
        }
        if (count > 0) {
            sum.div((float)count);
            // First two lines of code below could be condensed with new PVector setMag() method
            // Not using this method until Processing.js catches up
            // sum.setMag(maxspeed);

            // Implement Reynolds: Steering = Desired - Velocity
            sum.normalize();
            sum.mult(maxspeed);
            PVector steer = PVector.sub(sum, velocity);
            steer.limit(maxforce);
            return steer;
        } else {
            return new PVector(0, 0);
        }
    }

    // Cohesion
    // For the average location (i.e. center) of all nearby boids, calculate steering vector towards that location
    PVector cohesion (ArrayList<Boid> boids) {
        float neighbordist = 50;
        PVector sum = new PVector(0, 0);   // Start with empty vector to accumulate all locations
        int count = 0;
        for (Boid other : boids) {
            float d = PVector.dist(location, other.location);
            if ((d > 0) && (d < neighbordist)) {
                sum.add(other.location); // Add location
                count++;
            }
        }
        if (count > 0) {
            sum.div(count);
            return seek(sum);  // Steer towards the location
        } else {
            return new PVector(0, 0);
        }
    }

    public void render(PApplet app) {
        // Draw a triangle rotated in the direction of velocity
        float theta = velocity.heading2D() + app.radians(90);
        // heading2D() above is now heading() but leaving old syntax until Processing.js catches up

        int[] col = getColour();
        app.fill(col[0], col[1], col[2], col[3]);
        app.pushMatrix();
        app.translate(location.x, location.y);
        app.rotate(theta);
        app.beginShape(app.TRIANGLES);

        app.vertex(0, -size*2);
        app.vertex(-size, size*2);
        app.vertex(size, size*2);
        app.endShape();
        app.popMatrix();
    }


}

