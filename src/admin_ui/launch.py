import os.path
import subprocess as sp
import contextlib
import time
import sys
import math
import psutil
from pulsectl import Pulse

src_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

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

    with open(os.path.join(src_dir, 'sketch.properties'), 'w') as f:
        f.write('\n'.join('%s=%s' % (k, to_prop(v)) for k, v in params.iteritems() if valid_prop(v)) + '\n')

    p = sp.Popen([os.path.join(src_dir, 'build/install/lsdome/bin/lsdome'), name], cwd=src_dir)
    return p

def launch_screencast(cmd, params, timeout=5):
    """Launch a GUI window and screencast it to the pixel mesh
    cmd - command to launch gui program
    params - sketch properties; notably, 'title' is used in detecting the GUI window
      if it can't be found via process id
    timeout - abort if gui window can't be found after this many seconds
    returns tuple(window id, list of relevant processes)
    """
    # launch the content process/window
    content = sp.Popen(cmd, shell=True)

    # find the window that was launched; this is not instantaneous, so retry
    window_search_retry_interval = .2
    window_search_total_time = timeout
    window = None
    for i in xrange(int(math.ceil(window_search_total_time / window_search_retry_interval))):
        # actually gui process may be a child process, which continue to spawn over time
        processes = get_process_descendants(content.pid)
        windows = get_desktop_windows(set(p.pid for p in processes), params.get('title'))
        if windows:
            if len(windows) > 1:
                print 'more than one matching window found!'
            window = windows[0]
            break
        time.sleep(window_search_retry_interval)
    print 'process ids:', [p.pid for p in processes]
    if window is None:
        print 'could not detect window; aborting...'
        if 'title' not in params:
            print 'window may not report a pid, so specify a title filter'
        terminate(processes)
        return None

    # make window always on top
    os.popen('wmctrl -i -r %d -b add,above' % window['wid'])
    
    if window['pid']:
        params['pid'] = window['pid']
    sketch = launch_sketch('screencast', params)
    processes.append(sketch)
    
    return (window['wid'], processes)    
# TODO: detect if content window terminates and report back

def init_soundreactivity(pids, source, volume, timeout=2.):
    retry_interval = .1
    initialized = False
    for i in xrange(int(math.ceil(timeout / retry_interval))):
        try:
            set_audio_source(pids, source)
            set_audio_source_volume(pids, volume)
            initialized = True
            break
        except RuntimeError:
            pass
        time.sleep(retry_interval)
    if not initialized:
        print 'could not initialize audio settings'
        
def projectm_control(wid, command):
    interaction = {
        'next': 'key r',
        'toggle-lock': 'key l',
    }[command]
    os.popen('xdotool windowactivate --sync %d && xdotool %s' % (wid, interaction))

def get_audio_sources():
    with contextlib.closing(Pulse('lsdome-admin')) as client:
        return [s.name for s in client.source_list()]

def _get_one(matches, type_name):
    if not matches:
        raise RuntimeError('no matching %s!' % type_name)
    if len(matches) > 1:
        print 'more than one matching %s!' % type_name, matches
    return matches[0]

def _get_pulse_client_for_pids(client, pids):
    return _get_one([l for l in client.source_output_list() if int(l.proplist.get('application.process.id', '0')) in pids], 'listener')

def set_audio_source(pids, source):
    with contextlib.closing(Pulse('lsdome-admin')) as client:
        listener = _get_pulse_client_for_pids(client, pids).index
        source = _get_one([s for s in client.source_list() if s.name == source], 'source').index
    os.popen('pactl move-source-output %d %d' % (listener, source))

def set_audio_source_volume(pids, vol):
    # vol 1. = max hardware volume w/o software scaling; >1 allowed
    with contextlib.closing(Pulse('lsdome-admin')) as client:
        listener = _get_pulse_client_for_pids(client, pids).index
    os.popen('pactl set-source-output-volume %d %d' % (listener, int(2**16 * vol)))
    
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


if __name__ == "__main__":

    test = sys.argv[1]

    p = launch_sketch('video', {'path': '/home/drew/lsdome-media/video/the_knife-we_share_our_mothers_health.mp4'})
    time.sleep(20)
    terminate([p])
