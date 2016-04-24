from subprocess import Popen
import sys
import os.path
import time
import random

src_dir = os.path.dirname(os.path.dirname(__file__))

def sketch_dir(sketch):
    return os.path.join(src_dir, 'sketches', sketch)

def load_sketches():
    VIDEO_DIR = '/home/drew/dome/video'

    sketches = {
        'CloudFlock': None,
        'dream_all': [{'mode': i} for i in (0, 1, 3, 4, 5)],
        'grid_test': None,
        'kscopevomitcomet': None,
        'PixelFlock': None,
        'pixel_test': None,
        'triangle_particle_fft': None,
        'tube': None,
        'twinkle': None,
        'video_player': [{'path': os.path.join(VIDEO_DIR, p), 'repeat': 'true'} for p in os.listdir(VIDEO_DIR)],
    }
    for k, v in sketches.iteritems():
        if v is None:
            v = [{}]
        for params in v:
            yield (k, params)

def run_sketch(sketch_config, secs, processing_dir):
    sketch, params = sketch_config
    print 'running', sketch, params

    with open(os.path.join(sketch_dir(sketch), 'sketch.properties'), 'w') as f:
        f.write('\n'.join('%s=%s' % (k, v) for k, v in params.iteritems()))

    p = Popen('%s %s %s' % (os.path.join(src_dir, 'scripts', 'launchsketch.sh'), processing_dir, sketch), shell=True)
    time.sleep(secs)

    # killing happens in launchscript so we don't hang during recompile time
    #Popen('killall -9 %s' % os.path.join(processing_dir, 'java', 'bin', 'java'), shell=True).wait()

if __name__ == "__main__":
 
    processing_dir = sys.argv[1]
    time_per_sketch = int(sys.argv[2])

    sketches = list(load_sketches())
    from pprint import pprint
    pprint(sketches)

    for sketch, _ in sketches:
        assert os.path.exists(sketch_dir(sketch))
    while True:
        run_sketch(random.choice(sketches), time_per_sketch, processing_dir)
