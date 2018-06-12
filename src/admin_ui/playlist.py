import os
import os.path
import random
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

        # if true, stretch content to fit the viewport
        self.stretch_aspect = kwargs.get('stretch_aspect', False)

        ## audio settings ##
        # true if content responds to audio input
        self.sound_reactive = kwargs.get('sound_reactive', False)
        # if sound_reactive, false if content can still be shown without audio input available
        self.sound_required = kwargs.get('sound_required', True)
        # relative volume adjustment for audio input (to give more/less responsiveness)
        self.volume_adjust = kwargs.get('volume_adjust', 1.)
        # true if sketch has audio out (that we actually want to hear)
        self.has_audio = kwargs.get('has_audio', False)

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
        except RuntimeError:
            print 'could not read duration of %s' % vid
            duration = 0
        return duration
            
_all_content = None
def all_content():
    global _all_content
    if not _all_content:
        _all_content = [
            Content('black', 'black (note: keeps running and using cpu)', manual=True),
            Content('cloud'),
            Content('dontknow'),
            Content('moire'),
            Content('rings'),
            Content('tube'),
            Content('twinkle'),
            # TODO original kaleidoscope for dome
            Content('xykaleidoscope'),
            Content('particlefft', sound_reactive=True),
            Content('pixelflock', sound_reactive=True, sound_required=False),
            Content('screencast', 'projectm', cmdline='projectM-pulseaudio', sound_reactive=True, volume_adjust=1.5,
                    server_side_parameters=projectm_parameters(),
                    post_launch=lambda manager: projectm_control(manager, 'next'), # get off the default pattern
            ),
            Content('stream', 'hdmi-in', manual=True, stretch_aspect=True, params={
                'camera': 'FHD Capture: FHD Capture',
            })
        ]
        _all_content.extend(load_videos())
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
            
        yield Content('video', 'video:%s' % os.path.relpath(vid, VIDEO_DIR), stretch_aspect=True, params={
            'path': vid,
        }, **args)

def projectm_control(mgr, command):
    interaction = {
        'next': 'key r',
        'toggle-lock': 'key l',
    }[command]
    launch.gui_interaction(mgr.window_id, interaction)

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
            return (all_content()[parts[0]], float(parts[1]) if len(parts) > 1 else 1.)
        yield Playlist(name, dict(map(parse_entry, entries)))
