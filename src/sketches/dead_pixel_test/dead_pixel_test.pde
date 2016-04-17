/*
 * A sketch to help debug dead pixels.
 *
 * Designated dead pixels will be red. If they've been set correctly (and are
 * really dead), you shouldn't see these pixels on the dome. Any pixels next to
 * a dead pixel will be green.
 */

FadecandySketch driver = new DeadPixelTest(this, 300);

void setup() {
  driver.init();
}

void draw() {
  driver.draw();
}
