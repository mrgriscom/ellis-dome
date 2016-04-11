/*
 * Fractal noise animation. Modified version of Micah Scott's code at 
 * https://github.com/scanlime/fadecandy/tree/master/examples/processing/grid24x8z_clouds
 */

CloudsSketch driver = new CloudsSketch(this, 300);

void setup() {
  driver.init();
}

void draw() {
  driver.draw();
}

void keyPressed(){
   driver.processKeyInput();
}


