import os
import launch
import time
import threading
import Queue
import random

VIDEO_DIR = '/home/drew/lsdome-media/video'

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
    }
    yield {
        'sketch': 'dontknow',
    }
    yield {
        'sketch': 'moire',
    }
    yield {
        'sketch': 'rings',
    }
    yield {
        'sketch': 'tube',
        'interactive_params': {}
    }
    yield {
        'sketch': 'twinkle',
    }
    yield {
        'sketch': 'particlefft',
        'sound_reactive': True,
    }
    yield {
        'sketch': 'pixelflock',
        'sound_reactive': True,
    }
    yield {
        'name': 'projectm',
        'sketch': 'screencast',
        'cmd': 'projectM-pulseaudio',
        'no_stretch': True,
        'sound_reactive': True,
        'volume_adjust': 1.5,
    }
    yield {
        'name': 'hdmi-in',
        'sketch': 'stream',
        'camera': 'FHD Capture: FHD Capture',
        'no_stretch': True,
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
            'path': vid,
            'duration': duration,
            'shuffle': True,
        }
        # joan of arc require mirror mode

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
        
    def play(self, content, duration):
        self.queue.put(lambda: self._play_content(content, duration))

    def set_playlist(self, playlist, duration):
        self.queue.put(lambda: self._set_playlist(playlist, duration))
            
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

        self.playlist = None
        self._stop_playback()
            
    def _play_content(self, content, duration=None):
        if self.running_processes:
            self._stop_playback()

        params = dict(default_sketch_properties)
        params.update(content)
            
        if content['sketch'] == 'screencast':
            gui_invocation = launch.launch_screencast(content['cmd'], params)
            self.running_processes = gui_invocation[1]

            if content['name'] == 'projectm':
                # get off the default pattern
                launch.projectm_control(gui_invocation[0], 'next')
        else:
            if content['sketch'] == 'video':
                content['repeat'] = True
                if content['shuffle']:
                    content['skip'] = random.uniform(0, max(content['duration'] - duration, 0))
            p = launch.launch_sketch(content['sketch'], params)
            self.running_processes = [p]        
        # sound reactivity

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

    def _nothing_playing(self):
        if self.playlist:
            self._play_content(self.playlist.get_next(), self.default_duration)
        

#(no)stretch is really a part of placement (+xscale/yscale)
#wing_mode
#place_x
#place_y
#place_rot
#place_scale

#custom duration
