# Limitless Slip Dome

[![Build Status](https://travis-ci.org/shen-tian/lsdome.svg?branch=master)](https://travis-ci.org/shen-tian/lsdome)

Project for Afrika Burn 2016+. Visualizations for a 2D mesh of addressible LED pixels.

This is a library of animations. It uses the associated `lsdome-processing`
[library](https://github.com/mrgriscom/lsdome-processing).

### Build

1. Follow the build steps for [the parent library](https://github.com/mrgriscom/lsdome-processing).

2. Clone this repo and edit `build.gradle`; update `local_processing_install` and `local_processing_user_dir` to the relevant corrent paths. You may also use the published parent library by setting `use_local_lsdome_processing_lib = false`, but be warned this is often extremely out of date.

3. Install Processing video support. At the time of writing, do not use the packaged Processing video library -- it uses a very old video plugin that is extremely hard to get working on current versions of Ubuntu.

    ```
    cd ~/processing-3.3.7/modes/java/libraries/
    git clone https://github.com/processing/processing-video video
    cd video
    ````

    Edit `build.properties`, replacing `../processing/` with `<home dir>/processing-3.3.7/`

    Run `ant`

4. Install the full gamut of video plugins:

    ```
    sudo apt-get install gstreamer1.0-alsa gstreamer1.0-fluendo-mp3 gstreamer1.0-libav gstreamer1.0-plugins-bad gstreamer1.0-plugins-base gstreamer1.0-plugins-base-apps gstreamer1.0-plugins-good gstreamer1.0-plugins-ugly gstreamer1.0-pulseaudio gstreamer1.0-tools gstreamer1.0-x libgstreamer-plugins-bad1.0-0 libgstreamer-plugins-base1.0-0 libgstreamer-plugins-good1.0-0 libgstreamer1.0-0
    ````

5. Install Processing Gif support (do not use the version from the Processing IDE, as it does not support Processing 3.x):

    ```
    PROCESSING_WD=~/sketchbook  # linux
    PROCESSING_WD=~\Documents\Processing  # mac
    git clone https://github.com/01010101/GifAnimation.git $PROCESSING_WD/libraries/GifAnimation
    ```

6. Install additional Processing libraries from the Processing IDE: 'Sketch' menu -> Import Library... -> Add Library...:

- _Open Kinect_
- _Syphon_
- _Spout_

7. Build:

   `./gradlew installDist`

8. Install the OpenPixelControl simulator. This creates a model of the pixel layout in 3D space, allowing you to see what the visualization output will look like:

   ```
   git clone https://github.com/zestyping/openpixelcontrol.git
   sudo apt-get install build-essential mesa-common-dev freeglut3-dev
   make
   ```
   
   Then to run:

   `bin/gl_server -l <lsdome repo>/src/config/simulator_layouts/<relevant layout>.json -p 7890`

   or

   `./src/scripts/launch_simulator.py`

9. Launch visualizations:

    `./build/install/lsdome/bin/lsdome <animation name>`

10. Use the control UI:

    Required python dependencies:

    - tornado
    - zmq
    - psutil
    - pulsectl

    Launch `python src/admin_ui/server.py` and navigate to `http://localhost:8000/`

### Useful sketches

Some visualizations are generic front-ends for other functionality, and must be configured. Sketches can be configured in `sketch.properties`.

- `video` -- Video playback

  - `path` -- file path of video to play
  - `repeat` -- `true`/`false` whether to loop the video
  - `skip` -- begin playback this many seconds in

- `stream` -- Stream from a video input device

  - `camera` -- video device to stream

- `screencast` -- Map a rectangle of the desktop screen. Anything displayed in this rectangle will be shown on the pixels, even if it is an overlapping window or lock screen (mouse pointer should be ignored, though).

  - `title` -- window title; first matching window whose title starts with this prefix is capture and cast. If the window is later moved, the capture area does not change.
  - `pid` -- process ID of window to capture. Either this or `title` should be used, depending on which is easier for the application in question.

  Grabbing by window title/ID only works on linux, and requires the `wmctrl` program to be installed.

  Note that recent Ubuntu uses the 'Wayland' display system, which thwarts screen capture. You must start your login session with the 'Xorg' display system in order for screen capture to work.

- Generic settings:

  - subsampling -- how many samples to take for each LED pixel; this will gives things a smoother appearance and will prevent aliasing.
  - no_stretch -- if `false`, stretch the display window to fit as much as possible onto the LED mesh. If `true`, preserve a 1:1 aspect ratio rather than trying to squeeze in more content.

### Running in the field

See https://docs.google.com/document/d/1hHp4TiTqGZ-8Ikw_ZQ6Q-MDdQF0-5oHshonCKSjANCk/edit?usp=sharing for accumulated knowledge.
