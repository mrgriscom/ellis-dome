// Some real-time FFT! This visualizes music in the frequency domain using a
// polar-coordinate particle system. Particle size and radial distance are modulated
// using a filtered FFT. Color is sampled from an image.

import ddf.minim.analysis.*;
import ddf.minim.*;

ParticleFFTSketch particleDriver;
SegmentFFTSketch segmentDriver;

Minim minim;
AudioInput in;
FFT fft;

int mode;

void setup() {
    minim = new Minim(this); 

    in = minim.getLineIn();

    fft = new FFT(in.bufferSize(), in.sampleRate());

    particleDriver = new ParticleFFTSketch(this, fft.specSize(), 300);
    particleDriver.init();

    segmentDriver = new SegmentFFTSketch(this, fft.specSize(), 300);
    segmentDriver.init();

    mode = 1;
}

void draw()
{
    fft.forward(in.mix);

    for (int i = 0; i < fft.specSize (); i++) {
        particleDriver.fftBands[i] = fft.getBand(i);
        segmentDriver.fftBands[i] = fft.getBand(i);
    }

    if (mode == 1)
        particleDriver.draw();
    else
        segmentDriver.draw();
}

void keyPressed() {
    if (key == 'a')
        mode = 1;
    if (key == 'b')
        mode = 2;
}

