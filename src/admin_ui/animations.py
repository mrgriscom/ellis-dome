import os
import launch
import time
import threading
import Queue
import random
import csv
import psutil
import settings

def default_sketch_properties():
    return {
        'dynamic_subsampling': 1,
    }

def load_placements(path=None):
    placements_config_path = os.path.join(settings.py_root, 'placements.csv')

    if path is None:
        path = placements_config_path

    with open(path) as f:
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

class PlayManager(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.up = True
        self.queue = Queue.Queue()
        self.subscribers = []
        self.lock = threading.Lock()

        self.running_content = None
        self.running_processes = None
        self.content_timeout = None
        self.window_id = None
        self.background_audio_running = True

        self.playlist = None
        self.default_duration = None

        self.placements = load_placements()
        self.wing_trim = 'flat'

    def subscribe(self, s):
        with self.lock:
            self.subscribers.append(s)
        s.notify({'content': self.running_content})
        s.notify({'playlist': repr(self.playlist)})
        s.notify({'duration': self.content_timeout})
            
    def unsubscribe(self, s):
        with self.lock:
            self.subscribers.remove(s)

    def notify(self, msg):
        with self.lock:
            subs = list(self.subscribers)
        for s in subs:
            s.notify(msg)
            
    # play content immediately, after which normal playlist will resume
    def play(self, content, duration):
        self.queue.put(lambda: self._play_content(content, duration))

    # set playlist, which will start after current content finishes (or
    # immediately if nothing playing)
    def set_playlist(self, playlist, duration):
        self.queue.put(lambda: self._set_playlist(playlist, duration))

    def extend_duration(self, duration, relnow=False):
        self.queue.put(lambda: self._extend_duration(duration, relnow))
        
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

            self.update_background_audio()
        self._stop_all()

    def _play_content(self, content, duration=None):
        if self.running_processes:
            self._stop_playback()

        # allow modification to content w/o modifying original
        content = dict(content)
        params = dict(default_sketch_properties())
        params.update(content.get('params', {}))
        content['params'] = params

        placement = random.choice(list(self.get_available_placements(content)))
        print 'using placement %s' % placement['name']
        apply_placement(params, placement)

        audio_config = {}
        if not content.get('has_audio', False) and settings.audio_out:
            # if we have speakers, mute the content unless told otherwise, so it doesn't
            # play over the background playlist. (if no speakers, it doesn't really
            # matter, so don't)
            audio_config['output_volume'] = 0.
            # if possible, have the sketch mute itself too upon boot to avoid any transients
            params['mute'] = True
        else:
            # setting seems sticky, so we need to explicitly say we want audio back
            audio_config['output_volume'] = 1.
        if content.get('sound_reactive'):
            audio_config['input_volume'] = content.get('volume_adjust', 1.)
            audio_config['audio_input'] = default_audio_input()
        
        if content['sketch'] == 'screencast':
            gui_invocation = launch.launch_screencast(content['cmd'], params)
            self.running_processes = gui_invocation[1]
            self.window_id = gui_invocation[0]

            if content['name'] == 'projectm':
                # get off the default pattern
                launch.projectm_control(gui_invocation[0], 'next')
        else:
            if content['sketch'] == 'video':
                params['repeat'] = True
                if content['playmode'] == 'shuffle':
                    params['skip'] = random.uniform(0, max(content['duration'] - duration, 0))
                elif content['playmode'] == 'full':
                    params['repeat'] = False
                    content['sketch_controls_duration'] = True
                    duration = settings.sketch_controls_duration_failsafe_timeout
            p = launch.launch_sketch(content['sketch'], params)
            self.running_processes = [p]
        launch.AudioConfigThread([p.pid for p in self.running_processes], **audio_config).start()

        content['launched_at'] = time.time()
        self.running_content = content
        self.notify({'content': self.running_content})
        print 'content started'

        if duration:
            self.content_timeout = time.time() + duration
            self.notify({'duration': self.content_timeout})
            print 'until', self.content_timeout

    def _set_playlist(self, playlist, duration):
        self.playlist = playlist
        self.notify({'playlist': repr(playlist)})
        self.default_duration = duration

    def _extend_duration(self, duration, relnow):
        if self.content_timeout:
            if relnow:
                self.content_timeout = time.time() + duration
            else:
                self.content_timeout += duration
            self.notify({'duration': self.content_timeout})
            print 'until', self.content_timeout

    def _stop_playback(self):
        launch.terminate(self.running_processes)
        self.running_content = None
        self.running_processes = None
        self.content_timeout = None
        self.window_id = None
        self.notify({'content': self.running_content})
        self.notify({'duration': self.content_timeout})
        print 'content stopped'

    def _stop_all(self):
        self.playlist = None
        self.notify({'playlist': repr(self.playlist)})
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

    def update_background_audio(self):
        if not settings.audio_out:
            return
        
        play_background_audio = not (self.running_content or {}).get('has_audio', False)
        if play_background_audio != self.background_audio_running:
            background_audio(play_background_audio)
        self.background_audio_running = play_background_audio

# todo: make this selectable in UI
def default_audio_input():
    sources = launch.get_audio_sources()
    monitors = [s for s in sources if 'monitor' in s]
    mics = [s for s in sources if s not in monitors]
    return (monitors if settings.audio_out else mics)[0]
    
def background_audio(enable):
    try:
        audio_player_instance = [p for p in psutil.process_iter() if p.name() == 'audacious'][0]
    except IndexError:
        audio_player_instance = None

    # do nothing if media player not already running, otherwise we may unintentionally launch it
    if not audio_player_instance:
        return
    
    command = 'play' if enable else 'pause'
    os.popen('audacious --%s &' % command)
