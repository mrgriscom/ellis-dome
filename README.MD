# Limitless Slip Dome

[![Build Status](https://travis-ci.org/shen-tian/lsdome.svg?branch=master)](https://travis-ci.org/shen-tian/lsdome)

Project for Afrika Burn 2016+. Visualizations for a 2D mesh of addressible LED pixels.

This is a library of animations. It uses the associated `lsdome-processing`
[library](https://github.com/shen-tian/lsdome-processing).

### Build

1. Follow the build steps for [the parent library](https://github.com/shen-tian/lsdome-processing).

2. Clone this repo and edit `build.gradle`; update `local_processing_install` and `local_processing_user_dir` to the relevant corrent paths. You may also use the published parent library by setting `use_local_lsdome_processing_lib = false`, but be warned this is often extremely out of date.

3. Install Processing video support. At the time of writing, do not use the packaged Processing video library -- it uses a very old video plugin that is extremely hard to get working on current versions of Ubuntu.

    ```
    cd ~/processing-3.3.7/modes/java/libraries/
    git clone https://github.com/processing/processing-video video
    cd video
    ````

    Edit `build.properties`, replacing `../processing/` with `<home dir>/processing-3.3.7/`

    Run `ant`

4. Install Processing Kinect support. Install from the Processing IDE: 'Sketch' menu -> Import Library -> Add Library -> "Open Kinect for Processing"

5. Build:

   `./gradlew installDist`

6. Install the OpenPixelControl simulator. This creates a model of the pixel layout in 3D space, allowing you to see what the visualization output will look like:

   ```
   git clone https://github.com/zestyping/openpixelcontrol.git
   sudo apt-get install build-essential mesa-common-dev freeglut3-dev
   make
   ```
   
   Then to run:

   `bin/gl_server -l <lsdome repo>/src/config/simulator_layouts/<relevant layout>.json -p 7890`

7. Launch visualizations:

    `./build/install/lsdome/bin/lsdome <animation name>`

8. Set up interactive control. Some animations support on-the-fly configuration via MIDI input. To set up:

    ```
    sudo apt-get install libzmq5 python-pypm
    pip install pygame zmq
    ```

    Unfortunately, pygame midi support is broken, so we must patch:

    ```
    sudo mv /usr/local/lib/python2.7/dist-packages/pygame/pypm.so /usr/local/lib/python2.7/dist-packages/pygame/pypm.so.broken
    sudo ln -s /usr/lib/python2.7/dist-packages/pypm.x86_64-linux-gnu.so /usr/local/lib/python2.7/dist-packages/pygame/pypm.so
    ```

    To launch the input daemon:
    
    `python src/control/input.py`

    Currently only the Numark DJ2Go controller is supported, but if it's connected and everything is working right, you should see something like:

    ```
    loaded device
    f0fe308b:dj2go:jog_a:inc
    ```

    If you see anything about 'mock device', it didn't work.

### Useful sketches

Some visualizations are generic front-ends for other functionality, and must be configured. Sketches can be configured in `sketch.properties`.

- `video` -- Video playback.

  - `path` -- path of video to play
  - `repeat` -- `true`/`false` whether to loop the video



screencast X not wayland

sketch details:
screencast
video
stream



subsampling
no_stretch
