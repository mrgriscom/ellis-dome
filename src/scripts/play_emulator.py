import sys
import os.path
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../admin_ui'))
import launch

#sudo apt-get install retroarch libretro-bsnes-mercury-performance libretro-gambatte libretro-nestopia libretro-genesisplusgx libretro-picodrive libretro-mupen64plus


rom = sys.argv[1]
_, procs = launch.launch_screencast(**launch.launch_emulator(rom))
try:
    while True:
        time.sleep(.01)
except KeyboardInterrupt:
    launch.terminate(procs)
