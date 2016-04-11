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
static final int startHue = 60;

int time;
int wait = 100;

void setup() {
  minim = new Minim(this);
  in = minim.getLineIn();
  fft = new FFT(in.bufferSize(), in.sampleRate());
  fftFilter = new float[fft.specSize()];

  beat = new BeatDetect();

  colorMode(HSB, 100);
  driver.init();
  time = millis();
  flock = new Flock();
  // Add an initial set of boids into the system
  for (int i = 0; i < 75; i++) {
    flock.addBoid(new Boid(width/2, height/2, startHue));
  }
}

void draw() {
  background(0);
  flock.run();

  for (int i = 0; i < fftFilter.length; i++) {
    fftFilter[i] = max(fftFilter[i] * decay, log(1 + fft.getBand(i)));
  }


  fft.forward(in.mix);
  for (int i = 0; i < fftFilter.length; i += 3) {   
    int brightness = (int) (minBrightness + (brightnessScale*fftFilter[i]));
    if (brightness > 100) {
      brightness = 100;
    }
    flock.setBrightness(brightness);
  }

  beat.detect(in.mix);
  if ( beat.isOnset() ) {
    flock.cycleHue();
    flock.scatterFlock();
    time = millis();
  } else {
    if (millis() - time >= wait){
      flock.collectFlock();
       time = millis();
    }
  }
  driver.draw();
}

