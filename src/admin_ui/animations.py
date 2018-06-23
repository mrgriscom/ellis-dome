import os
import launch
import time
import threading
import Queue
import random
import csv
import uuid
import psutil
import settings
import playlist

# daemon that controls the currently running thing, and responsible for turning play intention into action (choosing a placement, initializing params, etc.)

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
            def to_float(field):
                if field in rec:
                    rec[field] = float(rec[field])
            to_float('x-offset')
            to_float('y-offset')
            to_float('rotation')
            to_float('scale')
            return rec

        return map(load_rec, r)

def apply_placement(params, placement):
    params['no_stretch'] = placement['aspect'] != 'stretch'
    params['wing_mode'] = placement['wing_mode']
    fields = {
        'x-offset': 'place_x',
        'y-offset': 'place_y',
        'rotation': 'place_rot',
        'scale': 'place_scale',
    }
    for k, v in fields.iteritems():
        if k in placement:
            params[v] = placement[k]

class ContentInvocation(object):
    def __init__(self, manager):
        self.manager = manager
        self.uuid = uuid.uuid4().hex
        self.info = {}
        self.processes = []
        self.timeout = None
        self.window_id = None
        self.params = {}

        # universal params
        if settings.audio_out:
            self.add_param(MasterVolumeParameter(self.manager))
        
    def running(self):
        return bool(self.info)

    def _bulk_notify_msgs(self):
        yield {'content': self.info}
        yield {'duration': self.timeout}
        yield {'type': 'params', 'params': [p.param for p in self.params.values()], 'invocation': self.uuid}
        for p in self.params.values():
            yield p.get_value()
        
    def notify(self, sub):
        for msg in self._bulk_notify_msgs():
            sub.notify(msg)

    def notify_all(self):
        for msg in self._bulk_notify_msgs():
            self.manager.notify(msg)
            
    def set_timeout(self, timeout):
        self.timeout = timeout
        self.manager.notify({'duration': self.timeout})
        print 'until', self.timeout

    def update_info(self, kv):
        self.info.update(kv)
        self.manager.notify({'content': self.info})
        
    def add_param(self, param):
        self.params[param.param['name']] = param
        self.manager.notify({'type': 'params', 'params': [param.param], 'invocation': self.uuid})
        self.manager.notify(param.get_value())
        
    def pids(self):
        return [p.pid for p in (self.processes or [])]
        
class PlayManager(threading.Thread):
    def __init__(self, broadcast_evt_func, callback_wrapper=None):
        threading.Thread.__init__(self)
        self.up = True
        self.queue = Queue.Queue()
        self.subscribers = []
        self.lock = threading.Lock()
        self.callback_wrapper = callback_wrapper
        self.broadcast_evt_func = broadcast_evt_func
        
        self.content = ContentInvocation(self)
        self.playlist = None
        self.default_duration = None
        self.background_audio_running = True
        self.audio_input = default_audio_input()
        self.wing_trim = 'flat'

        self.placements = load_placements()
        
    def subscribe(self, s):
        with self.lock:
            self.subscribers.append(s)
            self.content.notify(s)
            s.notify(self._playlist_json())
            # ping java world and force re-broadcast of its params
            self.broadcast_evt_func('_paraminfo', 'set')
            
    def unsubscribe(self, s):
        with self.lock:
            self.subscribers.remove(s)

    def notify(self, msg):
        # need this to capture the subscriber and decouple it from the loop variable
        def notify_func(s):
            return lambda: s.notify(msg)
            
        with self.lock:
            for s in self.subscribers:
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

    def update_content_info(self, kv):
        self.queue.put(lambda: self.content.update_info(kv))
        
    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            if self.content.timeout is not None and time.time() > self.content.timeout:
                self._stop_playback()

            try:
                event = self.queue.get(True, .01)
                event()
            except Queue.Empty:
                pass

            if not self.content.running():
                self._nothing_playing()

            self.update_background_audio()
        self._stop_all()
        
    def _play_content(self, content, duration=None):
        if self.content.running():
            self._stop_playback()

        self.content = ContentInvocation(self)
        self.content.info = content.to_json_info()

        params = dict(settings.default_sketch_properties)
        params.update(content.params)
        self.content.info['params'] = params
        
        placement = random.choice(list(self.get_available_placements(content)))
        print 'using placement %s' % placement['name']
        apply_placement(params, placement)

        for param_factory in content.server_side_parameters:
            self.content.add_param(param_factory(self))
        
        audio_config = {}
        if settings.audio_out:
            if not content.has_audio:
                # if we have speakers, mute the content unless told otherwise, so it doesn't
                # play over the background playlist
                audio_config['output_volume'] = 0.
                # if possible, have the sketch mute itself too upon boot to avoid any transients
                params['mute'] = True
            else:
                # setting seems sticky, so we need to explicitly say we want audio back
                audio_config['output_volume'] = 1.

            def audio_out_detect():
                self.content.add_param(OutputTrackParameter(self))
            audio_config['audio_out_detect_callback'] = audio_out_detect
        if content.sound_reactive:
            # also set param values
            audio_config['input_volume'] = content.volume_adjust
            audio_config['audio_input'] = self.audio_input

            self.content.add_param(AudioSensitivityParameter(self, audio_config['input_volume']))
            self.content.add_param(AudioSourceParameter(self))
        
        if content.sketch == 'screencast':
            gui_invocation = launch.launch_screencast(content.cmdline, params)
            self.content.processes = gui_invocation[1]
            self.content.window_id = gui_invocation[0]

            post_launch_hook = content.post_launch
            if post_launch_hook:
                post_launch_hook(self)
        else:
            if content.sketch == 'video':
                params['repeat'] = True
                if content.play_mode == 'shuffle':
                    params['skip'] = random.uniform(0, max(content.duration - duration, 0))
                elif content.play_mode == 'full':
                    params['repeat'] = False
                    self.content.info['sketch_controls_duration'] = True
                    duration = settings.sketch_controls_duration_failsafe_timeout
                    
            p = launch.launch_sketch(content.sketch, params)
            self.content.processes = [p]
        launch.AudioConfigThread(self.content.pids(), **audio_config).start()

        self.content.info['launched_at'] = time.time()
        self.notify({'content': self.content.info})
        print 'content started'

        if duration:
            self.content.set_timeout(time.time() + duration)

    def _set_playlist(self, playlist, duration=None):
        self.playlist = playlist
        self.default_duration = duration
        self.notify(self._playlist_json())

    def _playlist_json(self):
        if self.playlist:
            json = self.playlist.to_json()
            json['duration'] = self.default_duration
        else:
            json = None
        return {'playlist': json}
        
    def _extend_duration(self, duration, relnow):
        if self.content.timeout:
            base = time.time() if relnow else self.content.timeout
            self.content.set_timeout(base + duration)

    def _stop_playback(self):
        launch.terminate(self.content.processes)
        self.content = ContentInvocation(self)
        self.content.notify_all()
        print 'content stopped'

    def _stop_all(self):
        self._set_playlist(None)
        self._stop_playback()

    def _nothing_playing(self):
        if self.playlist:
            self._play_content(self.playlist.get_next(), self.default_duration)

    def _input_event(self, id, type, val):
        param = self.content.params.get(id)
        if not param:
            return
        param.handle_input_event(type, val)
        self.notify(param.get_value())
            
    def get_available_placements(self, content):
        for pl in self.placements:
            if 'wing_trim' in pl and pl['wing_trim'] != self.wing_trim:
                continue
            if content.stretch_aspect != (pl['aspect'] == 'stretch'):
                continue
            yield pl

    def update_background_audio(self):
        if not settings.audio_out:
            return
        
        play_background_audio = not self.content.info.get('has_audio', False)
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

    
# duplicate the java Parameter API so we can add UI parameters strictly from the server code.
# this implementation is much more stripped down
    
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
        launch.AudioConfigThread(self.manager.content.pids(), input_volume=sens).run_inline()

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
        launch.AudioConfigThread(self.manager.content.pids(), audio_input=val).run_inline()

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
        self.manager.content.info['has_audio'] = val
        self.manager.broadcast_evt_func('mute', 'set', self.from_bool(not val))
        launch.AudioConfigThread(self.manager.content.pids(), output_volume=1. if val else 0.).run_inline()

    def _update_value(self, val):
        val['value'] = self.from_bool(self.manager.content.info.get('has_audio', False))
