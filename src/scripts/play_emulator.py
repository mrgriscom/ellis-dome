import sys
import os.path
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../admin_ui'))
import launch

#sudo apt-get install retroarch libretro-bsnes-mercury-performance libretro-gambatte libretro-nestopia libretro-genesisplusgx

roms_path = '/home/drew/roms/'
cores = {
    'gameboy': '/usr/lib/libretro/gambatte_libretro.so',
    'nes': '/usr/lib/libretro/nestopia_libretro.so',
    'snes': '/usr/lib/libretro/bsnes_mercury_performance_libretro.so',
    'genesis': '/usr/lib/libretro/genesis_plus_gx_libretro.so',
}

rom = sys.argv[1]
rompath = os.path.abspath(rom)
core = None
for k, v in cores.iteritems():
    if rompath.startswith(os.path.join(roms_path, k)):
        core = v
        break
if not core:
    raise RuntimeError('don\'t recognize system')

_, procs = launch.launch_screencast('retroarch -L %s "%s"' % (core, rom), {'title': 'retroarch'})
try:
    while True:
        time.sleep(.01)
except KeyboardInterrupt:
    launch.terminate(procs)
