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

    `ant`



run processing and install kinect library
./processing-3.3.7/processing
sketch -> import library -> add library
“open kinect for processing” -> install

opc clone
make
apt-get install build-essential + sudo apt-get install mesa-common-dev freeglut3-dev

run lsdome binary


commit diffs!
fix pixel/grid test

midi control
pip install pygame zmq
sudo apt-get install libzmq5 python-pypm
sudo mv /usr/local/lib/python2.7/dist-packages/pygame/pypm.so /tmp/
sudo ln -s /usr/lib/python2.7/dist-packages/pypm.x86_64-linux-gnu.so /usr/local/lib/python2.7/dist-packages/pygame/pypm.so
python src/control/input.py
loaded device
f0fe308b:dj2go:jog_a:inc
using mock device no


screencast X not wayland

sketch details:
screencast
video
stream



Work in progress: the project is split between two parts right now: 

* Headless animations that can run without an X environment
* Processing sketches that need a graphics environment to run.

#### Headless java app

The headless code is organised as a Java app. It's built by `gradle`.
Before running `gradle`, set `local_processing_install` in `build.gradle`
to the path of your processing installation.
`gradle installDist` builds and places it in the `\build\install\lsdome` folder.
Running the `./bin/lsdome` script launches it. It accepts these parameters:

    lsdome [cloud|rings|kaleidoscope|twinkle|pixeltest]
    
It's pretty self contained. Since these are headless, you do need an 
OPC server that's connected to see what's going on though. Use a `config.properties`
file to configure where the OPC serer is. Sample file:

    opchostname=192.168.1.123
    opcport=7890

### Dependencies

You must install Processing 2.2.1

On ubuntu:

    add-apt-repository ppa:mc3man/gstffmpeg-keep && apt-get update
    apt-get install gstreamer0.10-plugins-base gstreamer0.10-plugins-good gstreamer0.10-ffmpeg gstreamer0.10-pulseaudio gstreamer0.10-alsa

### Simulator

Grab this: 

    git clone https://github.com/zestyping/openpixelcontrol.git
    
And maybe some dependcies. on ubuntu:

    sudo apt-get install mesa-common-dev freeglut3-dev

Build the thing:
   
    make

Then run:

    bin/gl_server -l $LSDOME_REPO_PATH/src/config/simulator_layouts/opcsimulator_layout_24.json -p 7890
    
Use the layout file for the corresponding number of panels.
