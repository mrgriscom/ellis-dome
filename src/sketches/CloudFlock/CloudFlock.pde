import ddf.minim.analysis.*;
import ddf.minim.*;

Minim minim;
AudioInput in;
FFT fft;
float[] fftFilter;
float decay = 0.97;
float minBrightness = 50;
float brightnessScale = 300;
BeatDetect beat;

FadecandySketch driver = new FadecandySketch(this, 250, 250);
Flock flock;
static final int START_HUE = 60;
static final int COLOUR_RANGE = 10; //hue can be current +- this
int currentHue;

int flockTime;
int flockWait = 500;
int hueTime;
int hueWait = 5000;

float xstart, xnoise, ystart, ynoise;   

void setup() {
  minim = new Minim(this);
  in = minim.getLineIn();
  fft = new FFT(in.bufferSize(), in.sampleRate());
  fftFilter = new float[fft.specSize()];

  beat = new BeatDetect();

  colorMode(HSB, 100);
  driver.init();
  hueTime = millis();
  flockTime = millis();


  smooth();
  background(0);
  frameRate(60);

  xstart = random(10);
  ystart = random(10);


  flock = new Flock();
  // Add an initial set of boids into the system
  for (int i = 0; i < 75; i++) {
    flock.addBoid(new Boid(width/2, height/2));
  }
}
void drawCloud() {
  background(0);  // clears backgound every frame

  xstart += 0.01;  // increments x/y noise start values
  ystart += 0.01;

  xnoise = xstart;
  ynoise = ystart;

  for (int y = 0; y <= height; y+=5) {
    ynoise += 0.1;                                    
    xnoise = xstart;
    for (int x = 0; x <= width; x+=5) {
      xnoise += 0.1;    
      drawCloudPoint(x, y, noise(xnoise, ynoise));
    }
  }
}

void draw() {
  drawCloud();
  flock.run();

  beat.detect(in.mix);
  beat.setSensitivity(flockWait*2);
  if ( beat.isOnset() ) {
    flock.scatterFlock();
    flockTime = millis();
  } else {
    if (millis() - flockTime >= flockWait) {
      flock.collectFlock();
      flockTime = millis();
    }
  }
  
  if (millis() - hueTime >= hueWait) {
      cycleHue();
      hueTime = millis();
    }
    
  
    driver.draw();
}

void cycleHue() {
  int newHue = currentHue + 1;
  if (newHue > 100) {
    newHue = newHue - 100;
  }
  currentHue=newHue;
}


void drawCloudPoint(float x, float y, float noiseFactor) {   
  pushMatrix();
  translate(x, y);
  rotate(noiseFactor * radians(540));
  float edgeSize = noiseFactor * 35;
  float alph = ((50 + (noiseFactor * 120))/255)*100;
  noStroke();
  fill(currentHue, 85, 65, alph);
  ellipse(0, 0, edgeSize, edgeSize/2);
  popMatrix();
}

