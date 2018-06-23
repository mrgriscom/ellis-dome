import os.path
import ConfigParser

py_root = os.path.dirname(os.path.abspath(__file__))
repo_root = reduce(lambda a, b: os.path.dirname(a), xrange(2), py_root)

placements_dir = os.path.join(py_root, 'placements')
playlists_dir = os.path.join(py_root, 'playlists')

def load_java_settings(path):
    from StringIO import StringIO
    mock_section = 'dummy'
    with open(path) as f:
        content = '[%s]\n' % mock_section + f.read()
    config = ConfigParser.ConfigParser()
    config.readfp(StringIO(content))
    for k, v in config.items(mock_section):
        if v == 'true':
            v = True
        elif v == 'false':
            v = False
        globals()[k] = v
load_java_settings(os.path.join(repo_root, 'config.properties'))

# true if the installation has speakers
audio_out = True

media_path = '/home/drew/lsdome-media/'
roms_path = '/home/drew/roms/'

kinect = False

# when the sketch controls its own duration, if it sets a duration of
# 'indefinite', use this instead
sketch_controls_duration_failsafe_timeout = 300

default_sketch_properties = {
    'dynamic_subsampling': 1,
}

