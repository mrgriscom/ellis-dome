import os
import os.path
import random
import re
import settings
import launch

# maintains library of all 'things' to play and management of playlists

VIDEO_DIR = os.path.join(settings.media_path, 'video')
if not os.path.exists(VIDEO_DIR):
    assert False, 'media dir %s not found' % VIDEO_DIR

class Content(object):
    def __init__(self, sketch, name=None, **kwargs):
        # sketch to run in the java build
        self.sketch = sketch
        # name of this content option; must be unique; defaults to sketch name
        self.name = (name or sketch)
        # custom params to provide to the sketch
        self.params = kwargs.get('params', {})
        # true if content is only meant to be run manually, not part of a playlist
        self.manual = kwargs.get('manual', False)
        # if sketch can only run on certain geometries, list of compatible geometries
        self.geometries = kwargs.get('geometries', None)

        # if true, stretch content to fit the viewport
        self.stretch_aspect = kwargs.get('stretch_aspect', False)
        # a function (placement -> bool) that overrides the built-in placement selection
        self.placement_filter = kwargs.get('placement_filter')

        ## audio settings ##
        # true if content responds to audio input
        self.sound_reactive = kwargs.get('sound_reactive', False)
        # if sound_reactive, false if content can still be shown without audio input available
        self.sound_required = kwargs.get('sound_required', True)
        # relative volume adjustment for audio input (to give more/less responsiveness)
        self.volume_adjust = kwargs.get('volume_adjust', 1.)
        # true if sketch has audio out (that we actually want to hear)
        self.has_audio = kwargs.get('has_audio', False)

        # true if content can use kinect
        self.kinect_enabled = kwargs.get('kinect_enabled', False)
        # if kinect_enabled, false if content still works without connect
        self.kinect_required = kwargs.get('kinect_required', True)

        self.server_side_parameters = kwargs.get('server_side_parameters', [])

        ## sketch-dependent parameters ##

        # video
        # length of video in seconds -- set automatically
        self.duration = self.get_video_duration()
        # how to play the video:
        # - 'shuffle': play a random excerpt for the specific runtime
        # - 'full': play the video start to finish
        self.play_mode = kwargs.get('play_mode', 'shuffle')

        # screencast
        # command to launch program to be cast
        self.cmdline = kwargs.get('cmdline')
        # a hook to further configure/interact with the program after it's launched
        self.post_launch = kwargs.get('post_launch')

        if set(kwargs.keys()) - set(self.__dict__):
            assert False, 'unrecognized arg'

    def get_video_duration(self):
        if not self.sketch == 'video':
            return

        vid = self.params['path']
        try:
            duration = float(os.popen('mediainfo --Inform="Video;%%Duration%%" "%s"' % vid).readlines()[0].strip())/1000.
        except (ValueError, RuntimeError):
            print 'could not read duration of %s' % vid
            duration = 0
        return duration

    def to_json_info(self):
        info = dict((k, getattr(self, k)) for k in ('name', 'sound_reactive', 'has_audio', 'kinect_enabled'))
        if self.play_mode == 'full':
            info['duration'] = self.duration
        return info

# placement filter that ensures crisp alignment with lsdome panel/pixel geometry
def pixel_exact(p):
    # forcing zero-rotation is sufficient for lsdome, and achieves the same spirit for prometheus (which
    # doesn't have a concept of 'pixel exact') while still allowing for some variation in wing overlap
    return getattr(p, 'rot', 0) == 0 and p.is_1to1
# like pixel_exact, but stretch to fit full canvas (so not 'exact', but still 'aligned')
def align_but_stretch(p):
    return getattr(p, 'rot', 0) == 0 and p.stretch

_all_content = None
def all_content():
    global _all_content
    if not _all_content:
        _all_content = [
            Content('colortest', '[util] color test (& set to black)', manual=True),  # NOTE: this sketch is referenced by name for go-dark functionality
            Content('gridtest', '[util] uvw grid test', geometries=['lsdome'], manual=True),
            Content('fctest', '[util] fc topology test', params=fadecandy_config()),
            Content('layouttest', '[util] cartesian test (mouse)', manual=True, placement_filter=pixel_exact),
            Content('binary', '[util] binary decomp', manual=True),
            Content('cloud'),
            #Content('dontknow'),
            Content('harmonics', geometries=['lsdome']),
            Content('moire'),
            #Content('rings'),
            Content('tube'),
            #Content('screencast', 'vc', manual=True, cmdline='ping 4.2.2.1',  params={'title': 'hangouts'}, has_audio=True),
            Content('twinkle'),
            Content('fft', sound_reactive=True),
            Content('pixelflock', sound_reactive=True, sound_required=False, kinect_enabled=True, kinect_required=False),
            Content('kinectdepth', 'kinectdepth', kinect_enabled=True,
                    placement_filter=align_but_stretch),
#            Content('screencast', 'projectm', cmdline='projectM-pulseaudio', sound_reactive=True, volume_adjust=1.5,
#                    server_side_parameters=projectm_parameters(),
#                    post_launch=lambda manager: projectm_control(manager, 'next'), # get off the default pattern
#            ),
            Content('screencast', 'butterchurn', cmdline='killall -9 chrome ; google-chrome --app=http://localhost:7999/demo.html', sound_reactive=True, volume_adjust=1.,
                    server_side_parameters=butterchurn_parameters(),
            ),
            Content('screencast', 'glava', cmdline='glava', sound_reactive=True, params={'title': 'glava'}),
            Content('screencast', 'matrix', cmdline='/usr/lib/xscreensaver/xmatrix -no-trace -delay 25000', params={'title': 'xmatrix'}),
            Content('stream', 'hdmi-in', manual=True, stretch_aspect=True, params={
                'camera': 'FHD Capture: FHD Capture',
            }),
            Content('stream', 'phonecam', manual=True, stretch_aspect=True,
                    server_side_parameters=droidcam_parameters(),
                    params={
                        'camera': 'Droidcam',
                    }),
            Content('kaleidoscope', geometries=['lsdome'], placement_filter=pixel_exact, params={'scale': 2.}),
            Content('kaleidoscope', geometries=['prometheus'], params={'scale': 3.2}),
            Content('imgkaleidoscope', 'hearts', geometries=['lsdome'], placement_filter=pixel_exact, params={
                'image': "res/img/hearts.jpg",
                'scale': 1.,
                'source_scale': 1.3,
                'speed': .25,
            }),
            Content('imgkaleidoscope', 'hearts', geometries=['prometheus'], params={
                'image': "res/img/hearts.jpg",
                'scale': 1.6,
                'source_scale': 1.3,
                'speed': .25,
            }),

            Content('video', 'video:chrissy_poi_zoom', geometries=['prometheus'], params={
                'path': os.path.join(VIDEO_DIR, 'hayley_chrissy_fire_spinning.mp4'),
            }, placement_filter=lambda p: p.name == 'Chrissy (04-29 12:36)'),
        ]
        _all_content.extend(load_videos())
        _all_content = [c for c in _all_content if not c.geometries or settings.geometry in c.geometries]
        _all_content = [c for c in _all_content if not (c.kinect_enabled and c.kinect_required) or settings.kinect]
        for c in _all_content:
            if c.kinect_enabled and settings.kinect and not c.placement_filter:
                # when kinect used, ensure display lines up with camera
                c.placement_filter = pixel_exact
        assert len(set(c.name for c in _all_content)) == len(_all_content), 'content names not unique'
        _all_content = dict((c.name, c) for c in _all_content)
    return _all_content

def load_videos():
    vids = [f.strip() for f in os.popen('find "%s" -type f' % VIDEO_DIR).readlines()]
    for vid in vids:
        # TODO placement restrictions? joan of arc require mirror mode?

        # do special things for certain videos -- should probably make this more maintainable
        args = {}
        if 'knife' in vid:
            args['play_mode'] = 'full'
        if any(k in vid for k in ('knife', 'flood')):
            args['has_audio'] = True
        if 'zz_vero' in vid:
            args['manual'] = True

        yield Content('video', 'video:%s' % os.path.relpath(vid, VIDEO_DIR), stretch_aspect=True, params={
            'path': vid,
        }, **args)

def fadecandy_config():
    if settings.geometry == 'lsdome':
        layouts = ['src/config/simulator_layouts/lsdome_%spanel.json' % settings.num_panels]
    elif settings.geometry == 'prometheus':
        layouts = filter(None, (getattr(settings, k, None) for k in ('layout', 'layout2')))
    fcconfigs = [os.path.join(settings.repo_root, layout.replace('/simulator_layouts/', '/fadecandy/')) for layout in layouts]
    return dict(zip(['fcconfig', 'fcconfig2'], fcconfigs))

def projectm_control(mgr, command):
    interaction = {
        'next': 'key r',
        'toggle-lock': 'key l',
    }[command]
    launch.gui_interaction(mgr.content.window_id, interaction)

def projectm_parameters():
    import animations
    class ProjectMNextPatternAction(animations.Parameter):
        def param_def(self):
            return {
                'name': 'next pattern',
                'isAction': True,
            }

        def handle_input_event(self, type, val):
            if type != 'press':
                return
            projectm_control(self.manager, 'next')

        def _update_value(self, val):
            pass
    return [ProjectMNextPatternAction]

def butterchurn_control(mgr, command):
    interaction = {
        'next': 'key h', # hard cut
        'prev': 'key p',
    }[command]
    launch.gui_interaction(mgr.content.window_id, interaction)

def butterchurn_parameters():
    import animations
    class ButterChurnNextPatternAction(animations.Parameter):
        def param_def(self):
            return {
                'name': 'next pattern',
                'isAction': True,
            }

        def handle_input_event(self, type, val):
            if type != 'press':
                return
            butterchurn_control(self.manager, 'next')

        def _update_value(self, val):
            pass
    class ButterChurnPrevPatternAction(animations.Parameter):
        def param_def(self):
            return {
                'name': 'prev pattern',
                'isAction': True,
            }

        def handle_input_event(self, type, val):
            if type != 'press':
                return
            butterchurn_control(self.manager, 'prev')

        def _update_value(self, val):
            pass
    return [
        ButterChurnNextPatternAction,
        ButterChurnPrevPatternAction,
    ]

def droidcam_parameters():
    import animations
    import time
    import subprocess as sp
    import threading

    IP_SUBNET = '192.168.1'
    DHCP_RANGE = list(xrange(100, 200))

    class DroidcamConnectThread(threading.Thread):
        def __init__(self, content, ip):
            threading.Thread.__init__(self)
            self.up = True
            self.content = content
            self.ip = ip

        def terminate(self):
            self.up = False

        def run(self):
            print 'connecting to %s' % self.ip
            # wait for port to open up before connecting
            # on mobile port will only open if droidcam app is open, which it won't be at same time as admin ui
            while self.up:
                try:
                    xmlscan = os.popen('nmap %s  -p 4747 -Pn -n --open -oX -' % self.ip).read()
                    import xml.etree.ElementTree as ET
                    ips = [e.find('address').get('addr') for e in ET.fromstring(xmlscan).findall('host')]
                except Exception as e:
                    print 'nmap error', e
                    ips = []
                if self.ip in ips:
                    print 'port is open'
                    break
                time.sleep(.1)

            if not self.up:
                return
            print 'killing existing droidcam instances'
            os.popen('killall -9 droidcam-cli').read()
            if not self.up:
                return
            print 'launching droidcam'
            # don't invoke via shell or else we just terminate the shell process at end and leave droidcam running
            p = sp.Popen(['/home/shen/droidcam/droidcam-cli', self.ip, '4747'], stdin=sp.PIPE)
            self.content.processes.append(p)
            self.content.droidcam = p

    class DroidcamConnectAction(animations.Parameter):
        def param_def(self):
            ips = ['.%d' % i for i in DHCP_RANGE]
            return {
                'name': 'phone ip',
                'isEnum': True,
                'values': ips,
                'captions': ips,
            }

        def stop_other_threads(self):
            existing_thread = getattr(self.manager.content, 'connect_thread', None)
            if existing_thread:
                print 'killing existing thread'
                existing_thread.terminate()
                existing_thread.join(5.)

        def handle_input_event(self, type, val):
            if type != 'set':
                return

            def cleanup():
                self.stop_other_threads()
                os.popen('killall -9 droidcam-cli').read()
            self.manager.content.cleanup = cleanup

            ip_addr = IP_SUBNET + val
            print 'droidcam %s' % ip_addr

            self.stop_other_threads()
            connect_thread = DroidcamConnectThread(self.manager.content, ip_addr)
            connect_thread.start()
            self.manager.content.connect_thread = connect_thread

        def _update_value(self, val):
            pass

    class DroidcamTorchAction(animations.Parameter):
        def param_def(self):
            return {
                'name': 'toggle torch',
                'isAction': True,
            }

        def handle_input_event(self, type, val):
            if type != 'press':
                return

            dc = getattr(self.manager.content, 'droidcam', None)
            if dc:
                try:
                    dc.stdin.write('L\n')
                except Exception as e:
                    print e

        def _update_value(self, val):
            pass

    return [DroidcamConnectAction, DroidcamTorchAction]

def game_content(rom):
    try:
        args = launch.launch_emulator(rom)
    except:
        return None
    name = os.path.splitext(os.path.relpath(os.path.abspath(rom), settings.roms_path))[0]
    return Content('screencast', name, cmdline=args['cmd'], params=args.get('params', {}), stretch_aspect=True, has_audio=True)

_games_content = None
def load_games(filt):
    def all_roms_path_files():
        for dirpath, _, filenames in os.walk(settings.roms_path):
            for f in filenames:
                yield os.path.join(dirpath, f)

    global _games_content
    if not _games_content:
        _games_content = filter(None, map(game_content, all_roms_path_files()))
        _games_content = dict((c.name, c) for c in _games_content)
        print len(_games_content), 'roms'

    if filt == 'favs':
        return filter_games_favorites(_games_content)
    else:
        return filter_games(_games_content, filt)

def filter_games(all_games, filt):
    def name_to_search_key(name):
        name = os.path.split(name)[1]
        name = name.split('(')[0]
        words = name.lower().split()
        words = [re.sub('[^a-z0-9]', '', w) for w in words]
        return filter(None, words)

    def match_key(query, key):
        return all(any(kw.startswith(qw) for kw in key) for qw in query)

    return dict((k, v) for k, v in all_games.iteritems() if match_key(name_to_search_key(filt), name_to_search_key(k)))

def filter_games_favorites(all_games):
    with open(settings.rom_favorites) as f:
        favs = set(os.path.splitext(g.strip())[0] for g in f.readlines())
    return dict((k, v) for k, v in all_games.iteritems() if k in favs)

class Playlist(object):
    def __init__(self, name, choices):
        self.name = name
        # a mapping of content to relative likelihood
        self.choices = choices if type(choices) == type({}) else dict((c, 1.) for c in choices)
        self.last_played = None

    def _all_choices_except_last_played(self):
        for choice in self.choices.keys():
            if choice == self.last_played and len(self.choices) > 1:
                continue
            yield choice

    def get_likelihood(self, choice):
        return self.choices[choice]

    # TODO reduce likelihood of previous N selections
    def get_next(self):
        total_likelihood = sum(self.get_likelihood(choice) for choice in self._all_choices_except_last_played())
        rand = random.uniform(0, total_likelihood)
        cumulative_likelihood = 0
        choice = None
        for ch in self._all_choices_except_last_played():
            cumulative_likelihood += self.get_likelihood(ch)
            if cumulative_likelihood > rand:
                choice = ch
                break
        self.last_played = choice
        return choice

    def to_json(self):
        return {
            'name': self.name,
            'items': sorted(c.name for c in self.choices.keys()),
        }

def load_playlists():
    base = Playlist('(almost) everything', (c for c in all_content().values() if not c.manual))
    nosound = Playlist('no sound-reactive', (c for c in base.choices.keys() if not c.sound_reactive or not c.sound_required))
    playlists = [base, nosound]
    playlists.extend(load_playlist_files())
    return dict((pl.name, pl) for pl in playlists)

def load_playlist_files():
    playlist_files = os.listdir(settings.playlists_dir)
    for filename in playlist_files:
        name, ext = os.path.splitext(filename)
        if ext != '.playlist':
            continue

        path = os.path.join(settings.playlists_dir, filename)
        with open(path) as f:
            entries = filter(None, (ln.strip() for ln in f.readlines()))

        def parse_entry(entry):
            parts = entry.split('|')
            try:
                return (all_content()[parts[0]], float(parts[1]) if len(parts) > 1 else 1.)
            except KeyError:
                print 'content "%s" not available for playlist "%s"' % (parts[0], name)
        yield Playlist(name, dict(filter(None, map(parse_entry, entries))))
