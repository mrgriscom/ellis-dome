// Pull from arbitrary window elsewhere on the screen.

FadecandySketch driver = new ScreenScraper(this, FadecandySketch.widthForPixelDensity(2.));

void setup() {
  driver.init();
}

void draw() {
  driver.draw();
}
