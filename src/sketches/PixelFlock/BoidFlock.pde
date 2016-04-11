// The Flock (a list of Boid objects)

class Flock {
  ArrayList<Boid> boids; // An ArrayList for all the boids
  int currentHue = 60;

  Flock() {
    boids = new ArrayList<Boid>(); // Initialize the ArrayList
  }

  void run() {
    for (Boid b : boids) {
      b.run(boids);  // Passing the entire list of boids to each boid individually
    }
  }

  void cycleHue() {
    int newHue = currentHue + 1;
    if (newHue > 100) {
      newHue = newHue - 100;
    }
    currentHue=newHue;
    for (Boid b : boids) {
      b.setHue(currentHue);
    }
  }

  void setBrightness(int brightness) {
    for (Boid b : boids) {
      b.setBrightness(brightness);
    }
  }

  void scatterFlock() {
    for (Boid b : boids) {
      b.sepWeight = 10.0;
      b.aliWeight= 0.0;
      b.cohWeight = 0.0;
      b.maxspeed = 2 * Boid.MAX_SPEED;
    }
  }

  void collectFlock() {
    for (Boid b : boids) {
      b.sepWeight = 1.5;
      b.aliWeight= 1.0;
      b.cohWeight = 1.0;
      b.maxspeed = Boid.MAX_SPEED;
    }
  }

  void addBoid(Boid b) {
    boids.add(b);
  }
}

