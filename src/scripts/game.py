import sys
import os.path
import os
from subprocess import Popen

#sudo apt-get install retroarch libretro-bsnes-mercury-performance libretro-gambatte libretro-nestopia libretro-genesisplusgx

roms_path = '/home/shen/roms/'
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

with open('/home/shen/prometheus/lsdome/sketch.properties', 'w') as f:
    f.write('title=retroarch\n')
Popen('./build/install/lsdome/bin/lsdome screencast', shell=True, cwd='/home/shen/prometheus/lsdome/')
os.popen('retroarch -L %s "%s"' % (core, rom))
