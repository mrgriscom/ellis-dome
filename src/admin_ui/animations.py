import os
import launch
import time
import threading
import Queue
import random
import csv

VIDEO_DIR = '/home/drew/lsdome-media/video'

audio_source = 'alsa_input.pci-0000_00_1f.3.analog-stereo'

def default_sketch_properties():
    return {
        'dynamic_subsampling': 1,
    }

# screencast: window title
# all window-based: no_stretch (but mostly video/screencast)
# all window-based: xscale/yscale

def get_all_content():
    yield {
        'sketch': 'cloud',
        'aspect': '1:1',
    }
    yield {
        'sketch': 'dontknow',
        'aspect': '1:1',
    }
    yield {
        'sketch': 'moire',
        'aspect': '1:1',
    }
    yield {
        'sketch': 'rings',
        'aspect': '1:1',
    }
    yield {
        'sketch': 'tube',
        'aspect': '1:1',
        'interactive_params': {}
    }
    yield {
        'sketch': 'twinkle',
    }
    yield {
        'sketch': 'particlefft',
        'aspect': '1:1',
        'sound_reactive': True,
    }
    yield {
        'sketch': 'pixelflock',
        'aspect': '1:1',
        'sound_reactive': True,
    }
    yield {
        'name': 'projectm',
        'sketch': 'screencast',
        'cmd': 'projectM-pulseaudio',
        'sound_reactive': True,
        'volume_adjust': 1.5,
    }
    yield {
        'name': 'hdmi-in',
        'sketch': 'stream',
        'aspect': 'stretch',
        'camera': 'FHD Capture: FHD Capture',
    }
    for content in load_videos():
        yield content

def load_videos():
    vids = [f.strip() for f in os.popen('find "%s" -type f' % VIDEO_DIR).readlines()]
    for vid in vids:
        try:
            duration = int(os.popen('mediainfo --Inform="Video;%%Duration%%" "%s"' % vid).readlines()[0].strip())/1000.
        except RuntimeError:
            print 'could not read duration of %s' % vid
            duration = 0

        yield {
            'name': 'video:%s' % os.path.relpath(vid, VIDEO_DIR),
            'sketch': 'video',
            'aspect': 'stretch',
            'path': vid,
            'duration': duration,
            'shuffle': True,
        }
        # joan of arc require mirror mode

def load_placements():
    placements_config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'placements.csv')
    with open(placements_config_path) as f:
        r = csv.DictReader(f)

        def load_rec(rec):
            for k, v in rec.items():
                if v == '':
                    del rec[k]
            rec['stretch'] = {'y': True, 'n': False}[rec['stretch']]
            def to_float(field):
                if field in rec:
                    rec[field] = float(rec[field])
            to_float('xo')
            to_float('yo')
            to_float('rot')
            to_float('scale')
            return rec
            
        return map(load_rec, r)
        
def apply_placement(params, placement):
    params['no_stretch'] = not placement['stretch']
    params['wing_mode'] = placement['wing_mode']
    fields = {
        'xo': 'place_x',
        'yo': 'place_y',
        'rot': 'place_rot',
        'scale': 'place_scale',
    }
    for k, v in fields.iteritems():
        if k in placement:
            params[v] = placement[k]
    
class Playlist(object):
    def __init__(self, choices):
        self.choices = list(choices)
        self.last_played = None

    def _all_choices_except_last_played(self):
        for choice in self.choices:
            if choice['content'] == self.last_played and len(self.choices) > 1:
                continue
            yield choice
        
    def get_next(self):
        total_likelihood = sum(choice['likelihood'] for choice in self._all_choices_except_last_played())
        rand = random.uniform(0, total_likelihood)
        cumulative_likelihood = 0
        choice = None
        for ch in self._all_choices_except_last_played():
            cumulative_likelihood += ch['likelihood']
            if cumulative_likelihood > rand:
                choice = ch['content']
                break
        self.last_played = choice
        return choice

class PlayManager(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.up = True
        self.queue = Queue.Queue()

        self.running_content = None
        self.running_processes = None
        self.content_timeout = None

        self.playlist = None
        self.default_duration = None

        self.placements = load_placements()
        self.wing_trim = 'flat'

    # play content immediately, after which normal playlist will resume
    def play(self, content, duration):
        self.queue.put(lambda: self._play_content(content, duration))

    # set playlist, which will start after current content finishes (or
    # immediately if nothing playing)
    def set_playlist(self, playlist, duration):
        self.queue.put(lambda: self._set_playlist(playlist, duration))

    def set_wing_trim(self, trim):
        def set_trim():
            self.wing_trim = trim
        self.queue.put(set_trim)
        
    # terminate the current content; playlist will start something else, if loaded
    def stop_current(self):
        self.queue.put(lambda: self._stop_playback())

    # terminate the current content and don't run anything new
    def stop_all(self):
        self.queue.put(lambda: self._stop_all())
        
    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            if self.content_timeout is not None and time.time() > self.content_timeout:
                self._stop_playback()
            
            try:
                event = self.queue.get(True, .01)
                event()
            except Queue.Empty:
                pass

            if self.running_content is None:
                self._nothing_playing()
        self._stop_all()
            
    def _play_content(self, content, duration=None):
        if self.running_processes:
            self._stop_playback()

        params = dict(default_sketch_properties())
        params.update(content)

        placement = random.choice(list(self.get_available_placements(content)))
        print 'using placement %s' % placement['name']
        apply_placement(params, placement)
        
        if content['sketch'] == 'screencast':
            gui_invocation = launch.launch_screencast(content['cmd'], params)
            self.running_processes = gui_invocation[1]

            if content['name'] == 'projectm':
                # get off the default pattern
                launch.projectm_control(gui_invocation[0], 'next')
        else:
            if content['sketch'] == 'video':
                params['repeat'] = True
                if content['shuffle']:
                    params['skip'] = random.uniform(0, max(content['duration'] - duration, 0))
            p = launch.launch_sketch(content['sketch'], params)
            self.running_processes = [p]

        if content.get('sound_reactive'):
            launch.init_soundreactivity([p.pid for p in self.running_processes],
                                        audio_source,
                                        content.get('volume_adjust', 1.))

        self.running_content = content
        print 'content started'

        if duration:
            self.content_timeout = time.time() + duration
            print 'until', self.content_timeout

    def _set_playlist(self, playlist, duration):
        self.playlist = playlist
        self.default_duration = duration
            
    def _stop_playback(self):
        launch.terminate(self.running_processes)
        self.running_content = None
        self.running_processes = None
        self.content_timeout = None
        print 'content stopped'
        self._nothing_playing()

    def _stop_all(self):
        self.playlist = None
        self._stop_playback()
        
    def _nothing_playing(self):
        if self.playlist:
            self._play_content(self.playlist.get_next(), self.default_duration)

    def get_available_placements(self, content):
        for pl in self.placements:
            if 'wing_trim' in pl and pl['wing_trim'] != self.wing_trim:
                continue
            if 'aspect' in content:
                stretch = {'stretch': True, '1:1': False}[content['aspect']]
                if stretch != pl['stretch']:
                    continue
            yield pl
    


#custom duration
