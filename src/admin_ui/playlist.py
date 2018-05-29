import os
import os.path
import random
import settings

VIDEO_DIR = '/home/drew/lsdome-media/video'
if not os.path.exists(VIDEO_DIR):
    assert False, 'media dir %s not found' % VIDEO_DIR

# screencast: window title
# all window-based: no_stretch (but mostly video/screencast)
# all window-based: xscale/yscale

def get_all_content():
    yield {
        'sketch': 'black (note: keeps running and using cpu)',
        'manual': True,
    }
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
    }
    yield {
        'sketch': 'twinkle',
    }
    yield {
        'sketch': 'xykaleidoscope',
        'aspect': '1:1',
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
        'sound_required': False,
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
        'params': {
            'camera': 'FHD Capture: FHD Capture',
        },
        'manual': True,
    }
    for content in load_videos():
        yield content

def load_videos():
    vids = [f.strip() for f in os.popen('find "%s" -type f' % VIDEO_DIR).readlines()]
    for vid in vids:
        try:
            duration = float(os.popen('mediainfo --Inform="Video;%%Duration%%" "%s"' % vid).readlines()[0].strip())/1000.
        except RuntimeError:
            print 'could not read duration of %s' % vid
            duration = 0

        yield {
            'name': 'video:%s' % os.path.relpath(vid, VIDEO_DIR),
            'sketch': 'video',
            'aspect': 'stretch',
            'params': {
                'path': vid,
            },
            'duration': duration,
            'playmode': 'shuffle',
            'has_audio': 'knife' in vid, # play audio for the knife music vid; useful for testing
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

def content_name(c):
    return c.get('name', c['sketch'])

def load_playlists():
    all_content = list(get_all_content())
    def _playlists():
        yield ('(almost) everything', almost_everything_playlist(all_content))
        yield ('no sound-reactive', non_sound_reactive_playlist(all_content))

        for playlist in load_playlist_files(all_content):
            yield playlist
    return dict(_playlists())

def almost_everything_playlist(all_content):
    return equal_opportunity_playlist(c for c in all_content if not c.get('manual'))

# remove sound reactive content
def non_sound_reactive_playlist(all_content):
    contents = [e['content'] for e in almost_everything_playlist(all_content).choices]
    return equal_opportunity_playlist(c for c in contents
                                      if not c.get('sound_reactive') or not c.get('sound_required', True))

def equal_opportunity_playlist(contents):
    return Playlist({'content': c, 'likelihood': 1} for c in contents)

def load_playlist_files(all_content):
    content_by_name = dict((content_name(c), c) for c in all_content)
    assert len(content_by_name) == len(all_content), 'content names not unique'

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
            return {'content': content_by_name[parts[0]], 'likelihood': float(parts[1])}
        yield (name, Playlist(map(parse_entry, entries)))
