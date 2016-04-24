#!/bin/bash

# Usage: launchsketch.sh <path of processing install> <name of sketch>
# Launch a sketch in a failsafe manner.
# Example:
# ./src/scripts/launchsketch.sh ~/processing-2.2.1 pixel_test

PROCESSING_DIR=$1  # Directory of the processing install (containing core/, java/, lib/, etc.)
SKETCH_NAME=$2  # Name of sketch

SRC_DIR=$(cd $(dirname $0)/.. && pwd -P)  # src/ directory of lsdome repo 
SKETCH_DIR=$SRC_DIR/sketches/$SKETCH_NAME
SKETCH_LIB_DIR=$SKETCH_DIR/code

# Compile shared code.
JARFILE=$($SRC_DIR/scripts/buildlib.sh $PROCESSING_DIR)
mkdir -p $SKETCH_LIB_DIR
cp $JARFILE $SKETCH_LIB_DIR/lsdomeLib.jar

# Set up MIDI input.
MIDI_SOCKET=/tmp/pipe  # must match Config.java
rm -f $MIDI_SOCKET
mkfifo $MIDI_SOCKET
run-this-one unbuffer python $SRC_DIR/control/input.py > $MIDI_SOCKET &

# Launch the sketch.
killall -9 $PROCESSING_DIR/java/bin/java
$PROCESSING_DIR/processing-java --sketch=$SKETCH_DIR --run --output=$(mktemp -d) --force
