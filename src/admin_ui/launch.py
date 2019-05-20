import os.path
import subprocess as sp
import contextlib
import time
import sys
import math
import psutil
from pulsectl import Pulse
import uuid
import settings
import threading

# low-level util functions for launching things and controlling basic aspect of playback (audio, gui interaction, etc.)

def launch_sketch(name, params):
    """Launch a built-in sketch
    name - sketch name to pass to the java binary
    params - key-value set of sketch properties
    returns process object
    """
    def valid_prop(v):
        return v is not None and not hasattr(v, '__iter__')
    def to_prop(v):
        if type(v) == bool:
            return 'true' if v else 'false'
        return v

    with open(os.path.join(settings.repo_root, 'sketch.properties'), 'w') as f:
        f.write('\n'.join('%s=%s' % (k, to_prop(v)) for k, v in params.iteritems() if valid_prop(v)) + '\n')

    p = sp.Popen([os.path.join(settings.repo_root, 'build/install/lsdome/bin/lsdome'), name], cwd=settings.repo_root)
    return p

def launch_screencast(cmd, params):
    """Launch a GUI window and screencast it to the pixel mesh
    cmd - command to launch gui program
    params - sketch properties; notably, 'title' is used in detecting the GUI window
      if it can't be found via process id
    timeout - abort if gui window can't be found after this many seconds
    returns tuple(window id, list of relevant processes)
    """
    window, processes = launch_external(cmd, params.get('title'))
    if window['pid']:
        params['pid'] = window['pid']
    sketch = launch_sketch('screencast', params)
    processes.append(sketch)

    return (window['wid'], processes)
# TODO: detect if content window terminates and report back

def launch_external(cmd, title=None, timeout=5):
    # launch the content process/window
    content = sp.Popen(cmd, shell=True)

    # find the window that was launched; this is not instantaneous, so retry
    window_search_retry_interval = .2
    window_search_total_time = timeout
    window = None
    for i in xrange(int(math.ceil(window_search_total_time / window_search_retry_interval))):
        # the actual gui process may be among child processes, which continue to spawn over time
        processes = get_process_descendants(content.pid)
        windows = get_desktop_windows(set(p.pid for p in processes), title)
        if windows:
            if len(windows) > 1:
                print 'more than one matching window found!'
            window = windows[0]
            break
        time.sleep(window_search_retry_interval)
    print 'process ids:', [p.pid for p in processes]
    if window is None:
        print 'could not detect window; aborting...'
        if not 'title':
            print 'window may not report a pid, so specify a title filter'
        terminate(processes)
        return None, None

    # make window always on top
    os.popen('wmctrl -i -r %d -b add,above' % window['wid'])

    return (window, processes)

EMULATORS = {
    'mame': {'cmd': 'mame %s'},
}
RETROARCH_CORES = {
    'nes': '/usr/lib/libretro/nestopia_libretro.so',
    'snes': '/usr/lib/libretro/bsnes_mercury_performance_libretro.so',
    # don't use plus_gx due to viewport resize issue
    'genesis': '/usr/lib/libretro/picodrive_libretro.so',
    'n64': '/usr/lib/libretro/mupen64plus_libretro.so',
    'gameboy': '/usr/lib/libretro/gambatte_libretro.so',
    'gbc': '/usr/lib/libretro/gambatte_libretro.so',
    'gamegear': '/usr/lib/libretro/genesis_plus_gx_libretro.so',
}
EMULATORS.update((retrosys, {
    'cmd': 'retroarch -L %s %%s' % core,
    'params': {'title': 'retroarch'},
}) for retrosys, core in RETROARCH_CORES.iteritems())

# returns args to pass to launch_screencast
def launch_emulator(rom):
    rompath = os.path.abspath(rom)
    for k, v in EMULATORS.iteritems():
        if rompath.startswith(os.path.join(settings.roms_path, k)):
            args = dict(v) # copy
            args['cmd'] = args['cmd'] % ('"%s"' % rom)
            return args
    raise RuntimeError('don\'t recognize system')

def gui_interaction(wid, command):
    os.popen('xdotool windowactivate --sync %d && xdotool %s' % (wid, command))


def pulse_ctx():
    return contextlib.closing(Pulse('lsdome-admin:%s' % uuid.uuid4().hex))

def get_audio_sources():
    with pulse_ctx() as client:
        return [s.name for s in client.source_list()]

def get_default_output(client):
    return client.server_info().default_sink_name

def audio_output_obj(client, name):
    return AudioConfigThread.get_pulse_item(client.sink_list(), lambda e: e.name == name)

def get_master_volume():
    with pulse_ctx() as client:
        o = audio_output_obj(client, get_default_output(client))
        return o.volume.value_flat if o else None

def set_master_volume(vol):
    # vol 1. = max hardware volume w/o software scaling; >1 allowed
    with pulse_ctx() as client:
        o = audio_output_obj(client, get_default_output(client))
        if o is None:
            return False
        os.popen('pactl set-sink-volume %d %d' % (o.index, int(2**16 * vol)))
        return True

# configure the audio settings of the launched content. do this in a thread so as to
# not block launching -- audio config is not on the critical path so can be done async.
# the thread attempts configuration for a short timeout then gives up and dies.
class AudioConfigThread(threading.Thread):
    def __init__(self, pids, audio_input=None, input_volume=None, output_volume=None,
                 audio_out_detect_callback=None, timeout=5.):
        threading.Thread.__init__(self)
        self.pids = pids
        self.timeout = timeout

        self.audio_input = audio_input
        self.input_volume = input_volume
        self.output_volume = output_volume
        self.audio_out_detect_callback = audio_out_detect_callback

    def proc_input_id(self, client):
        return self.get_pulse_id(client.source_output_list(), self.pids_predicate())

    def proc_output_id(self, client):
        return self.get_pulse_id(client.sink_input_list(), self.pids_predicate())

    def audio_input_id(self, client):
        return self.get_pulse_id(client.source_list(), lambda e: e.name == self.audio_input)

    def set_audio_input(self, client, source_id):
        proc = self.proc_input_id(client)
        if proc is None:
            return False
        os.popen('pactl move-source-output %d %d' % (proc, source_id))
        return True

    def set_input_volume(self, client, vol):
        # vol 1. = max hardware volume w/o software scaling; >1 allowed
        proc = self.proc_input_id(client)
        if proc is None:
            return False
        os.popen('pactl set-source-output-volume %d %d' % (proc, int(2**16 * vol)))
        return True

    def set_output_volume(self, client, vol):
        # vol 1. = max hardware volume w/o software scaling; >1 allowed
        proc = self.proc_output_id(client)
        if proc is None:
            return False
        os.popen('pactl set-sink-input-volume %d %d' % (proc, int(2**16 * vol)))
        return True

    def run(self):
        self.run_inline(self.timeout)

    def run_inline(self, timeout=0):
        with pulse_ctx() as client:
            tasks = {}
            if self.input_volume is not None:
                tasks['input_volume'] = lambda: self.set_input_volume(client, self.input_volume)
            if self.output_volume is not None:
                tasks['output_volume'] = lambda: self.set_output_volume(client, self.output_volume)
            if self.audio_input:
                source_id = self.audio_input_id(client)
                if source_id is None:
                    print 'unknown input %s' % self.audio_input
                else:
                    tasks['input_device'] = lambda: self.set_audio_input(client, source_id)

            start = time.time()
            while tasks and (time.time() < start + self.timeout or self.timeout <= 0):
                for name, task in dict(tasks).iteritems():
                    if task():
                        del tasks[name]
                        if name == 'output_volume':
                            (self.audio_out_detect_callback or (lambda: None))()
                if timeout > 0:
                    time.sleep(.01)
                else:
                    break

            if 'input_volume' in tasks:
                print 'failed to set input volume'
            if 'input_device' in tasks:
                print 'failed to set input to %s' % self.audio_input
            # failure to set output volume not an error because we don't reliably know which content
            # produces audio (we only know audio that we care about)

    @staticmethod
    def get_pulse_item(items, pred):
        matches = filter(pred, items)
        if len(matches) > 1:
            print 'more than one matching %s!' % type(matches[0]), matches
        return matches[0] if matches else None

    @staticmethod
    def get_pulse_id(items, pred):
        item = AudioConfigThread.get_pulse_item(items, pred)
        return item.index if item else None

    def pids_predicate(self):
        return lambda e: int(e.proplist.get('application.process.id', '0')) in self.pids


def terminate(procs):
    """Kill each process in procs"""
    for p in (procs or []):
        try:
            p.kill()
        except psutil.NoSuchProcess:
            pass


def get_desktop_windows(pids, title=None):
    """Return an info record for each matching gui window
    pids - filter to gui windows belonging to any pid in 'pids'; if no windows match,
      then ignore (not all gui windows report their pid)
    title - filter to gui windows whose title starts with 'title', case insensitive
      and ignoring whitespace
    """
    lines = os.popen('wmctrl -lp').readlines()
    def parse_line(ln):
        tokens = ln.split()
        return {
            'wid': int(tokens[0], 16),
            'pid': int(tokens[2]) or None,
            'title': ' '.join(tokens[4:]),
        }
    windows = map(parse_line, lines)

    def norm_title(s):
        return ' '.join(s.split()).lower()
    def title_match(prefix, window):
        return norm_title(window['title']).startswith(norm_title(prefix))

    match = [w for w in windows if w['pid'] in pids]
    if title is not None:
        match = [w for w in (match or windows) if title_match(title, w)]
    return match

def get_process_descendants(pid):
    try:
        root = psutil.Process(pid)
    except psutil.NoSuchProcess:
        return []
    return [root] + root.children(recursive=True)
