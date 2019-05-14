import os.path
import ConfigParser
from datetime import datetime

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
# designated quiet times when the audio will be forcibly muted (and can't be
# overridden). mainly here for when the dome will be unattended. less useful
# for silent burns as those don't have a defined end time due to conditions
# and delays, but those mostly occur near sunset (when we'll be present) or
# sunrise (when we don't care about turning sound back on after).
# also, this has no effect on generator noise...
quiet_hours = [
    (datetime(2019, 4, 29, 7, 0), datetime(2019, 4, 29, 11, 0)),
    (datetime(2019, 4, 30, 7, 0), datetime(2019, 4, 30, 11, 0)),
    (datetime(2019, 5,  1, 7, 0), datetime(2019, 5,  1, 11, 0)),
    (datetime(2019, 5,  2, 7, 0), datetime(2019, 5,  2, 11, 0)),
]

kinect = False
# kinect depth value for the closest distance we care about (used for cutting off color ramps, etc.)
kinect_ceiling = 750
# kinect depth value for the farthest distance we care about, typically the ground
kinect_floor = 960
# kinect depth threshold to trigger things, typically reachable by raising a limb from kinect_floor
kinect_activation = 850

media_path = '/home/drew/lsdome-media/'
roms_path = '/home/drew/roms/'

default_duration = 150

# when the sketch controls its own duration, if it sets a duration of
# 'indefinite', use this instead
sketch_controls_duration_failsafe_timeout = 300

default_sketch_properties = {
    'dynamic_subsampling': 1,
}

opc_simulator_path = '/home/drew/dev/lsdome/openpixelcontrol/bin/gl_server'

# in some installs tornado callbacks don't seem to work correctly; set this
# to true to disable them (this could in theory break things, but in practice
# it seems to work fine).
tornado_callbacks_hack = False

enable_security = True
# if access is compromised, change BOTH of these (password for new logins, secret to invalidate existing)
login_password = None
cookie_secret = None
assert not login_password and not cookie_secret, 'set only in localsettings.py'

ssl_config = {
    'certfile': os.path.join(os.path.dirname(__file__), 'private/ssl/selfsigned.crt'),
    'keyfile': os.path.join(os.path.dirname(__file__), 'private/ssl/selfsigned.key'),
}

try:
    from localsettings import *
except ImportError:
    pass
