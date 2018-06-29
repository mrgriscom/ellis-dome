#include<SoftwareSerial.h>
#include <FastLED.h>

#ifdef __AVR__
#include <avr/power.h>
#endif

/**
   This part is for hardware related config. It allows you to spec the board
   you are using, along with the LED strand length/brightness. The brightness is
   typically used to limit max power draw: at 60mA per LED (full white), you can
   power at most 8 LEDs from the 500mA 5V pin on boards like the Trinket/Arduino
   Nano. As the strand gets longer, you should use brightness to limit max current
   draw. However, the typical pattern won't ever reach full white on all LEDs, so
   the actual max current varies. It's probably best established via direct
   measurement. An alternative reason to limit brightness is to improve battery
   life.

   Current configs:

 *  * Arduino Nano, use pin 6
 *  * Adafruit Trinket 5V 16Mhz, use pin 0
*/


#define STRAND_LENGTH 50

/**
    Pattern definition. The program cycles through a range on the wheel, and
    back again. This defines the boundaries. Note that wraparound for the full
    rainbow is not enabled. Would take special case code.
*/

#define HUE_START .333
#define HUE_END .833
#define SATURATION 255 * .8

#define MS_PER_BEAT 60000 / 138

CRGB eyes[4];
CRGB ants[STRAND_LENGTH];

void setup() {
  FastLED.addLeds<PIXIE, 6, RGB>(eyes, 4).setCorrection( TypicalLEDStrip );
  FastLED.addLeds<WS2811, 7, GRB>(ants, STRAND_LENGTH).setCorrection( TypicalLEDStrip ); //GRB
  FastLED.setBrightness( 255 );
}

void loop() {

  unsigned long t = millis();
  byte color = getClock(t, 2);

  byte val = (exp(sin(millis() / 2000.0 * PI)) - 0.36787944) * 108.0;
  val = val * 0.5 + 127;

  eyes[0] = CHSV(color, SATURATION, val);
  //eyes[0] = CRGB(0, 255, 0);
  eyes[1] = CHSV((color + 32) % 256, SATURATION, val);
  eyes[2] = CHSV(color, SATURATION, val);
  eyes[3] = CHSV((color + 32) % 256, SATURATION, val);

  byte pulse = inoise8(t / 4.) * .5;
  byte drift = getClock(t, 3);
  pulse += drift;
  if (pulse > 255)
    pulse -= 255;

  for (byte pix = 0; pix < STRAND_LENGTH; pix++) {
    // location of the pixel on a 0-RENDER_RANGE scale.
    byte dist = pix * 255 / STRAND_LENGTH;

    // messy, but some sort of least-of-3 distances, allowing wraping.
    byte delta = min(min(abs(dist - pulse), abs(dist - pulse + 256)), abs(dist - pulse - 255));
    // linear ramp up of brightness, for those within 1/8th of the reference point
    byte value = max(255 - 6 * delta, 64);

    // hue selection. Mainly driving by c, but with some small shifting along
    // the length of the strand.

    // sweep of a subset of the spectrum.
    byte left = 84;
    byte right = 212;
    float x = color / 255.;;// + pix * .5 / STRAND_LENGTH;
    if (x >= 1)
      x -= 1.;
    // sweeps the range. for x from 0 to 1, this function does this:
    // starts at (0, _right_), goes to (.5, _left_), then back to (1, _right)
    byte hue = abs(2 * (right - left) * x - right + left) + left;

    ants[pix] = CHSV(hue, SATURATION, value / 2);
  }
  // delay 20ms to give max 50fps. Could do something fancier here to try to
  // hit exactly 60fps (or whatever) if possible, but takinng another millis()
  // reading, but not sure if there would be a point to that.
  FastLED.show(); // display this frame
}


// Get a byte that cycles from 0-255, at a specified rate
// typically, assign mil using mills();
// rates, approximately (assuming 256ms in a second :P)
// 8: 4hz
// 7: 2hz
// 6: 1hz
// 5: 1/2hz
// 4: 1/4hz
// 3: 1/8hz
// 2: 1/16hz
// 1: 1/32hz
byte getClock(unsigned long mil, byte rate)
{
  return mil >> (8 - rate) % 256;
}
