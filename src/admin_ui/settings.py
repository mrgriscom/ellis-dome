import os.path
import ConfigParser
from datetime import datetime, timedelta
from quiet import GoDark

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
audio_out = False

# designated quiet times to automatically turn off just audio or both audio
# and visuals. shut-off/re-enable happens once at the designated time (or first
# time the system is running afterward if offline during the designated time).
# audio/visuals can be re-enabled manually at any time during the window.
# re-enable at end of window will not happen if there is an overlapping quiet
# window. automatic re-enable can also be disabled in the UI, such as if a burn
# is running very late.
# note: this cannot turn off the generator but disabling lights should reduce
# generator load and noise
quiet_hours = []
"""
    GoDark(datetime(2019, 4, 29,  7,  0), timedelta(hours=4), just_audio=True, name='mon-thu quiet hours'),
    GoDark(datetime(2019, 4, 30,  7,  0), timedelta(hours=4), just_audio=True, name='mon-thu quiet hours'),
    GoDark(datetime(2019, 5,  1,  7,  0), timedelta(hours=4), just_audio=True, name='mon-thu quiet hours'),
    GoDark(datetime(2019, 5,  2,  7,  0), timedelta(hours=4), just_audio=True, name='mon-thu quiet hours'),
    GoDark(datetime(2019, 5,  1, 17, 30), timedelta(hours=3), name='!xam burn'),
    GoDark(datetime(2019, 5,  5, 19, 30), timedelta(hours=3), name='temple burn', just_audio=True),
]
"""
# auto-quiet (both audio and visuals) from sunrise to sunset.
auto_quiet_daytime = True
mins_before_sunrise = 10
mins_after_sunset = -45 # re-enable before sunset since we usually get it running again around then
latlon = (-32.52, 19.96)

kinect = False
# kinect depth value for the closest distance we care about (used for cutting off color ramps, etc.)
kinect_ceiling = 580 #750
# kinect depth value for the farthest distance we care about, typically the ground
kinect_floor = 980 #960
# kinect depth threshold to trigger things, typically reachable by raising a limb from kinect_floor
kinect_activation = 620 #850

media_path = '/home/drew/lsdome-media/'
roms_path = '/home/drew/roms/'
rom_favorites = '/home/drew/dev/emu/favorites'

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

uptime_log = os.path.join(repo_root, 'uptime.log')

try:
    from localsettings import *
except ImportError:
    pass
