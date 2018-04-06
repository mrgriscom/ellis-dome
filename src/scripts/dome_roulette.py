#!/usr/bin/python

from subprocess import Popen
import sys
import os.path
import time
import random

src_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))

def load_sketches():
    VIDEO_DIR = '/home/drew/Videos/lsdome'

    sketches = {
        'cloud': None,
        'dontknow': None,
        'harmonics': None,
        'kaleidoscope': None,
        'noire': None,
        'particlefft': None,
        
        #'pixelflock': None,
        
        'pixeltest': None,
        'rings': None,
        'tube': None,
        'twinkle': None,
        'video': [{'path': os.path.join(VIDEO_DIR, p), 'repeat': 'true'} for p in os.listdir(VIDEO_DIR)],
        'kinectflock': None,
    }
    for k, v in sketches.iteritems():
        if v is None:
            v = [{}]
        for params in v:
            yield (k, params)

def run_sketch(sketch_config, secs):
    sketch, params = sketch_config
    print 'running', sketch, params

    befcmd = None
    aftcmd = None

    # special handling for music video
    if params.get('path', '').endswith('the_knife-we_share_our_mothers_health.mp4'):
        #return
        secs = 225
        befcmd = 'audacious --pause'
        aftcmd = 'audacious --play'

    with open(os.path.join(src_dir, 'sketch.properties'), 'w') as f:
        f.write('\n'.join('%s=%s' % (k, v) for k, v in params.iteritems()))

    if befcmd:
        os.popen(befcmd)

    p = Popen([os.path.join(src_dir, 'build/install/lsdome/bin/lsdome'), sketch])
    time.sleep(secs)
    p.kill()

    if aftcmd:
        os.popen(aftcmd)


if __name__ == "__main__":

    time_per_sketch = int(sys.argv[1])

    sketches = list(load_sketches())
    from pprint import pprint
    pprint(sketches)

    last_sketch = None

    while True:
        new_sketch = random.choice(sketches)
        if new_sketch == last_sketch:
            continue
        run_sketch(new_sketch, time_per_sketch)
        last_sketch = new_sketch
