## Limitless Slip Dome

Project for Afrika Burn 2016. A dome style gazebo with around 1500 - 2800 WS2812B LEDs, controlled via Fadecandies.

### From project submission:

The piece will be a net with 1500-2000 LEDs, about 3-4m in diameter suspended on a frame 2m off of the ground. Since the LEDs are fully addressable (NeoPixels!), they can be used to run various visualisations at night. It works really well with sound too, so I'd like to add some speakers (not a dance party level. More like ambient++). Its footprint should be around 5x5m.

### Install

1. Download Processing 2 (As of time of writing, this should be 2.2.1). Unpack this into a
folder (`~\processing-2.2.1`) for rest of the document.
2. Open Processing IDE. Do Tools -> Install processing-java. Do it for individual or something
ensure that it's in the processing folder.
3. Ensure java JDK is installed. (i.e. javac is there)
4. `git clone` blah blah.

To build, goto root folder, then

    ./src/scripts/launchsketch.sh ~/processing-2.2.1 pixel_test
    
This should yield magic.

### Simulator

Grab this: 

    git clone https://github.com/zestyping/openpixelcontrol.git
    
And maybe some dependcies. on ubuntu:

    sudo apt-get install mesa-common-dev freeglut3-dev

Build the thing:
   
    make
    
Then, grab `13panel-gl_server.json` from our lsdome `src/config/` and  put it in `openpixelcontrol/layouts`.
    
    bin/gl_server -l /13panel-gl_server.json -p 7890
    
to start the simulator.

