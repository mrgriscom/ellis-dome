import os
import launch
import time
import threading
import Queue
import random
import csv
import psutil
import settings
import playlist

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
    def __init__(self, callback_wrapper=None):
        threading.Thread.__init__(self)
        self.up = True
        self.queue = Queue.Queue()
        self.subscribers = []
        self.lock = threading.Lock()
        self.callback_wrapper = callback_wrapper

        self.running_content = None
        self.running_processes = None
        self.content_timeout = None
        self.window_id = None
        self.background_audio_running = True
        self.params = []

        self.playlist = None
        self.default_duration = None

        self.placements = load_placements()
        self.wing_trim = 'flat'
        self.audio_input = default_audio_input
        
    def subscribe(self, s):
        with self.lock:
            self.subscribers.append(s)
        s.notify({'content': self.running_content})
        s.notify({'playlist': repr(self.playlist)})
        s.notify({'duration': self.content_timeout})
        # TODO change source to invocation uuid
        s.notify({'type': 'params', 'params': [p.param for p in self.params], 'source': 'admin'})
            
    def unsubscribe(self, s):
        with self.lock:
            self.subscribers.remove(s)

    def notify(self, msg):
        with self.lock:
            subs = list(self.subscribers)

        # need this to capture the subscriber and decouple it from the loop variable
        def notify_func(s):
            return lambda: s.notify(msg)
            
        for s in subs:
            wrapper = self.callback_wrapper or (lambda func: func())
            wrapper(notify_func(s))
            
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

    def input_event(self, id, type, val):
        self.queue.put(lambda: self._input_event(id, type, val))
        
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
        def _server_config():
            return playlist.content_server_config.get(playlist.content_name(content), {})
        
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

        server_params = list(_server_config().get('server_parameters', []))
        
        audio_config = {}
        if settings.audio_out:
            # TODO make persistent (not sketch based)
            server_params.append(MasterVolumeParameter(self))
            if not content.get('has_audio', False):
                # if we have speakers, mute the content unless told otherwise, so it doesn't
                # play over the background playlist
                audio_config['output_volume'] = 0.
                # if possible, have the sketch mute itself too upon boot to avoid any transients
                params['mute'] = True
            else:
                # setting seems sticky, so we need to explicitly say we want audio back
                audio_config['output_volume'] = 1.

            def audio_out_detect(detected):
                params = []
                if detected:
                    params.append(OutputTrackParameter(self))
                self.params.extend(params)
                self.notify({'type': 'params', 'params': [p.param for p in params], 'source': 'admin:audio-out'})
            # we only detect lack of audio out based on a timeout, so immediately clear out result of previous check
            audio_out_detect(False)
            audio_config['audio_out_detect_callback'] = lambda: audio_out_detect(True)
        if content.get('sound_reactive'):
            # also set param values
            audio_config['input_volume'] = content.get('volume_adjust', 1.)
            audio_config['audio_input'] = self.audio_input

            server_params.append(AudioSensitivityParameter(self, audio_config['input_volume']))
            server_params.append(AudioSourceParameter(self))
        
        if content['sketch'] == 'screencast':
            gui_invocation = launch.launch_screencast(content['cmd'], params)
            self.running_processes = gui_invocation[1]
            self.window_id = gui_invocation[0]

            post_launch_hook = _server_config().get('post_launch_hook')
            if post_launch_hook:
                post_launch_hook(self)
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

        self.params.extend(server_params) 
        self.notify({'type': 'params', 'params': [p.param for p in server_params], 'source': 'admin'})

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
        self.params = []
        self.notify({'content': self.running_content})
        self.notify({'duration': self.content_timeout})
        self.notify({'type': 'params', 'params': [], 'source': 'admin'})
        print 'content stopped'

    def _stop_all(self):
        self.playlist = None
        self.notify({'playlist': repr(self.playlist)})
        self._stop_playback()

    def _nothing_playing(self):
        if self.playlist:
            self._play_content(self.playlist.get_next(), self.default_duration)

    def _input_event(self, id, type, val):
        param = [p for p in self.params if p.param['name'] == id]
        if not param:
            return
        param = param[0]
        param.handle_input_event(type, val)
        self.notify(param.get_value())
            
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

class Parameter(object):
    def __init__(self, manager):
        self.manager = manager
        self.param = self.param_def()

    def param_def(self):
        raise RuntimeError('abstract method')
        
    def handle_input_event(self, type, val):
        raise RuntimeError('abstract method')

    def get_value(self):
        val = {
            'type': 'param_value',
            'name': self.param['name'],
        }
        self._update_value(val)
        return val

    def _update_value(self, val):
        raise RuntimeError('abstract method')        

    @staticmethod
    def to_bool(val):
        return {'yes': True, 'no': False}[val]

    @staticmethod
    def from_bool(val):
        return 'yes' if val else 'no'
    
class AudioSensitivityParameter(Parameter):
    MIN_SENS = .3
    MAX_SENS = 3.

    def __init__(self, manager, sens):
        Parameter.__init__(self, manager)
        self.value = sens
    
    def param_def(self):
        return {
            'name': 'audio sensitivity',
            'category': 'audio',
            'isNumeric': True,
            'isBounded': True,
        }

    def handle_input_event(self, type, val):
        if type != 'slider':
            return
        sens = self.MIN_SENS * (1-val) + self.MAX_SENS * val
        self.value = sens
        launch.AudioConfigThread([p.pid for p in self.manager.running_processes], input_volume=sens).run_inline()

    def _update_value(self, val):
        val.update({
            'value': '%d%%' % (100. * self.value),
            'sliderPos': (self.value - self.MIN_SENS) / (self.MAX_SENS - self.MIN_SENS),
        })

class AudioSourceParameter(Parameter):
    def param_def(self):
        audio_sources = launch.get_audio_sources()
        return {
            'name': 'audio input',
            'category': 'audio',
            'isEnum': True,
            'values': audio_sources,
            'captions': audio_sources,
        }

    def handle_input_event(self, type, val):
        if type != 'set':
            return
        # make sticky
        self.manager.audio_input = val
        launch.AudioConfigThread([p.pid for p in self.manager.running_processes], audio_input=val).run_inline()

    def _update_value(self, val):
        val['value'] = self.manager.audio_input

class MasterVolumeParameter(Parameter):
    MAX_VOL = 1.5

    def param_def(self):
        return {
            'name': 'master volume',
            'category': 'audio',
            'isNumeric': True,
            'isBounded': True,
        }

    def handle_input_event(self, type, val):
        if type != 'slider':
            return
        vol = val * self.MAX_VOL
        launch.set_master_volume(vol)

    def _update_value(self, val):
        vol = launch.get_master_volume()
        val.update({
            'value': '%d%%' % (100. * vol),
            'sliderPos': vol / self.MAX_VOL,
        })

class OutputTrackParameter(Parameter):
    def param_def(self):
        return {
            'name': 'audio from',
            'category': 'audio',
            'isEnum': True,
            'values': ['yes', 'no'],
            'captions': ['content', 'background playlist (content muted)'],
        }

    def handle_input_event(self, type, val):
        if type != 'set':
            return
        val = self.to_bool(val)
        self.manager.running_content['has_audio'] = val
        import sys; sys.modules['__main__'].broadcast_event('mute', 'set', self.from_bool(not val))
        launch.AudioConfigThread([p.pid for p in self.manager.running_processes], output_volume=1. if val else 0.).run_inline()

    def _update_value(self, val):
        val['value'] = self.from_bool(self.manager.running_content['has_audio'])
