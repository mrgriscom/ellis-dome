/*
 * Simple Open Pixel Control client for Processing,
 * designed to sample each LED's color from some point on the canvas.
 *
 * Micah Elizabeth Scott, 2013
 * This file is released into the public domain.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import processing.core.*;

public class OPC implements Runnable
{
  PApplet app;
  Thread thread;
  Socket socket;
  OutputStream output, pending;
  String host;
  int port;

  int[] pixelLocations;
  int[] pixelBuffer;
  byte[] packetData;
  byte firmwareConfig;
  String colorCorrection;
  boolean enableShowLocations;
  FramePostprocessor framePostprocessor;

  OPC(PApplet parent, String host, int port)
  {
    this.host = host;
    this.port = port;
    thread = new Thread(this);
    thread.start();
    this.enableShowLocations = Config.DEBUG;
    this.app = parent;
    app.registerDraw(this);
  }

  // Mark the screen xy coordinates in 'points' as pixels 0..(n-1)
  void registerLEDs(ArrayList<PVector> points) {
      // TODO map a dome pixel to several screen pixels to support subsampling?
      pixelLocations = new int[points.size()];
      pixelBuffer = new int[pixelLocations.length];
      for (int i = 0; i < pixelLocations.length; i++) {
          PVector p = points.get(i);
          if (p == null) {
              pixelLocations[i] = -1;
              continue;
          }

          int x = (int)Math.floor(p.x);
          int y = (int)Math.floor(p.y);
          if (x >= 0 && x < app.width && y >= 0 && y < app.height) {
              pixelLocations[i] = x + app.width * y;
          } else {
              pixelLocations[i] = -1;
          }
      }
  }

  // Should the pixel sampling locations be visible? This helps with debugging.
  // Showing locations is enabled by default. You might need to disable it if our drawing
  // is interfering with your processing sketch, or if you'd simply like the screen to be
  // less cluttered.
  void showLocations(boolean enabled)
  {
    enableShowLocations = enabled;
  }

  // Enable or disable dithering. Dithering avoids the "stair-stepping" artifact and increases color
  // resolution by quickly jittering between adjacent 8-bit brightness levels about 400 times a second.
  // Dithering is on by default.
  void setDithering(boolean enabled)
  {
    if (enabled)
      firmwareConfig &= ~0x01;
    else
      firmwareConfig |= 0x01;
    sendFirmwareConfigPacket();
  }

  // Enable or disable frame interpolation. Interpolation automatically blends between consecutive frames
  // in hardware, and it does so with 16-bit per channel resolution. Combined with dithering, this helps make
  // fades very smooth. Interpolation is on by default.
  void setInterpolation(boolean enabled)
  {
    if (enabled)
      firmwareConfig &= ~0x02;
    else
      firmwareConfig |= 0x02;
    sendFirmwareConfigPacket();
  }

  // Put the Fadecandy onboard LED under automatic control. It blinks any time the firmware processes a packet.
  // This is the default configuration for the LED.
  void statusLedAuto()
  {
    firmwareConfig &= 0x0C;
    sendFirmwareConfigPacket();
  }    

  // Manually turn the Fadecandy onboard LED on or off. This disables automatic LED control.
  void setStatusLed(boolean on)
  {
    firmwareConfig |= 0x04;   // Manual LED control
    if (on)
      firmwareConfig |= 0x08;
    else
      firmwareConfig &= ~0x08;
    sendFirmwareConfigPacket();
  } 

  // Set the color correction parameters
  void setColorCorrection(float gamma, float red, float green, float blue)
  {
    colorCorrection = "{ \"gamma\": " + gamma + ", \"whitepoint\": [" + red + "," + green + "," + blue + "]}";
    sendColorCorrectionPacket();
  }

  // Set custom color correction parameters from a string
  void setColorCorrection(String s)
  {
    colorCorrection = s;
    sendColorCorrectionPacket();
  }

  // Send a packet with the current firmware configuration settings
  void sendFirmwareConfigPacket()
  {
    if (pending == null) {
      // We'll do this when we reconnect
      return;
    }

    byte[] packet = new byte[9];
    packet[0] = 0;          // Channel (reserved)
    packet[1] = (byte)0xFF; // Command (System Exclusive)
    packet[2] = 0;          // Length high byte
    packet[3] = 5;          // Length low byte
    packet[4] = 0x00;       // System ID high byte
    packet[5] = 0x01;       // System ID low byte
    packet[6] = 0x00;       // Command ID high byte
    packet[7] = 0x02;       // Command ID low byte
    packet[8] = firmwareConfig;

    try {
      pending.write(packet);
    } 
    catch (Exception e) {
      dispose();
    }
  }

  // Send a packet with the current color correction settings
  void sendColorCorrectionPacket()
  {
    if (colorCorrection == null) {
      // No color correction defined
      return;
    }
    if (pending == null) {
      // We'll do this when we reconnect
      return;
    }

    byte[] content = colorCorrection.getBytes();
    int packetLen = content.length + 4;
    byte[] header = new byte[8];
    header[0] = 0;          // Channel (reserved)
    header[1] = (byte)0xFF; // Command (System Exclusive)
    header[2] = (byte)(packetLen >> 8);
    header[3] = (byte)(packetLen & 0xFF);
    header[4] = 0x00;       // System ID high byte
    header[5] = 0x01;       // System ID low byte
    header[6] = 0x00;       // Command ID high byte
    header[7] = 0x01;       // Command ID low byte

    try {
      pending.write(header);
      pending.write(content);
    } 
    catch (Exception e) {
      dispose();
    }
  }

  // An option to modify an entire frame's worth of pixels before they're sent to the hardware,
  // such as to perform color balancing / contrast equilization, etc.
  public static interface FramePostprocessor {
    // FIXME this will include out-of-frame pixels set to black
    void postProcessFrame(int[] pixelBuffer);
  }

  // Automatically called at the end of each draw().
  // This handles the automatic Pixel to LED mapping.
  // If you aren't using that mapping, this function has no effect.
  // In that case, you can call setPixelCount(), setPixel(), and writePixels()
  // separately.
  public void draw()
  { 
    if (pixelLocations == null) {
      // No pixels defined yet
      return;
    }
    if (output == null) {
      return;
    }

    int numPixels = pixelLocations.length;
    setPixelCount(numPixels);
    app.loadPixels();

    for (int i = 0; i < numPixels; i++) {
      int pixelLocation = pixelLocations[i];
      pixelBuffer[i] = (pixelLocation != -1 ? app.pixels[pixelLocation] : 0);
    }
    if (framePostprocessor != null) {
      framePostprocessor.postProcessFrame(pixelBuffer);
    }
    for (int i = 0; i < numPixels; i++) {
        int pixel = pixelBuffer[i];
        setPixel(i, pixel);
        if (enableShowLocations) {
            // FIXME this doesn't seem to work?
            int pixelLocation = pixelLocations[i];
            app.pixels[pixelLocation] = 0xFFFFFF ^ pixel;
        }
    }

    writePixels();

    if (enableShowLocations) {
      app.updatePixels();
    }
  }

  // Change the number of pixels in our output packet.
  // This is normally not needed; the output packet is automatically sized
  // by draw() and by setPixel().
  void setPixelCount(int numPixels)
  {
    int numDeadPixels = Config.DEAD_PIXELS.length;
    numPixels -= numDeadPixels;

    int numBytes = 3 * numPixels;
    int packetLen = 4 + numBytes;
    if (packetData == null || packetData.length != packetLen) {
      // Set up our packet buffer
      packetData = new byte[packetLen];
      packetData[0] = 0;  // Channel
      packetData[1] = 0;  // Command (Set pixel colors)
      packetData[2] = (byte)(numBytes >> 8);
      packetData[3] = (byte)(numBytes & 0xFF);
    }
  }

    // Return whether this pixel is marked dead.
    static boolean isDeadPixel(int i) {
        return Arrays.binarySearch(Config.DEAD_PIXELS, i) >= 0;
    }

    // Return number of dead pixels before this pixel.
    static int deadPixelOffset(int i) {
        assert !isDeadPixel(i);
        int offset = Arrays.binarySearch(Config.DEAD_PIXELS, i);
        offset = -(offset + 1);
        return offset;
    }

  // Directly manipulate a pixel in the output buffer. This isn't needed
  // for pixels that are mapped to the screen.
  void setPixel(int number, int c)
  {
      if (isDeadPixel(number)) {
          return;
      } else {
          number -= deadPixelOffset(number);
      }

    int offset = 4 + number * 3;
    if (packetData == null || packetData.length < offset + 3) {
      setPixelCount(number + 1);
    }

    packetData[offset] = (byte) (c >> 16);
    packetData[offset + 1] = (byte) (c >> 8);
    packetData[offset + 2] = (byte) c;
  }

  // Read a pixel from the output buffer. If the pixel was mapped to the display,
  // this returns the value we captured on the previous frame.
  int getPixel(int number)
  {
    int offset = 4 + number * 3;
    if (packetData == null || packetData.length < offset + 3) {
      return 0;
    }
    return (packetData[offset] << 16) | (packetData[offset + 1] << 8) | packetData[offset + 2];
  }

  // Transmit our current buffer of pixel values to the OPC server. This is handled
  // automatically in draw() if any pixels are mapped to the screen, but if you haven't
  // mapped any pixels to the screen you'll want to call this directly.
  void writePixels()
  {
    if (packetData == null || packetData.length == 0) {
      // No pixel buffer
      return;
    }
    if (output == null) {
      return;
    }

    try {
      output.write(packetData);
    } 
    catch (Exception e) {
      dispose();
    }
  }

  void dispose()
  {
    // Destroy the socket. Called internally when we've disconnected.
    // (Thread continues to run)
    if (output != null) {
      System.out.println("Disconnected from OPC server");
    }
    socket = null;
    output = pending = null;
  }

  public void run()
  {
    // Thread tests server connection periodically, attempts reconnection.
    // Important for OPC arrays; faster startup, client continues
    // to run smoothly when mobile servers go in and out of range.
    for (;; ) {

      if (output == null) { // No OPC connection?
        try {              // Make one!
          socket = new Socket(host, port);
          socket.setTcpNoDelay(true);
          pending = socket.getOutputStream(); // Avoid race condition...
          System.out.println("Connected to OPC server");
          sendColorCorrectionPacket();        // These write to 'pending'
          sendFirmwareConfigPacket();         // rather than 'output' before
          output = pending;                   // rest of code given access.
          // pending not set null, more config packets are OK!
        } 
        catch (ConnectException e) {
          dispose();
        } 
        catch (IOException e) {
          dispose();
        }
      }

      // Pause thread to avoid massive CPU load
      try {
        thread.sleep(500);
      }
      catch(InterruptedException e) {
      }
    }
  }
}

