import sys
import os.path
import os

import settings
import animations
import playlist
import launch

from tornado.ioloop import IOLoop
import tornado.web as web
import tornado.gen as gen
from tornado.template import Template
import tornado.websocket as websocket
from optparse import OptionParser
import logging
import json
import zmq
import threading
import Queue
import time
import psutil

def web_path(*args):
    return os.path.join(settings.py_root, *args)

class MainHandler(web.RequestHandler):
    def get(self):
        self.render('main.html', onload='init', geom=settings.geometry)

class WebSocketTestHandler(websocket.WebSocketHandler):
    def initialize(self):
        pass

    def open(self):
        #self.placements = list(placements)
        #ix = len(placements)
        #for f in os.listdir(settings.placements_dir):
        #    if f.startswith('.'):
        #        continue
        #    try:
        #        preset = animations.load_placements(os.path.join(settings.placements_dir, f))[0]
        #        preset['ix'] = ix
        #        ix += 1
        #        placements.append(preset)
        #    except:
        #        print 'error loading preset'
        self.notify({'playlists': [{'name': pl.name} for pl in sorted(playlists.values(), key=lambda pl: pl.name)]})
        self.notify({'contents': [{'name': c.name} for c in sorted(contents.values(), key=lambda c: c.name)]})
        self.notify({'placements': placements})
        manager.subscribe(self)
        self.notify(battery_thread.get_status())
        
    def on_message(self, message):
        data = json.loads(message)
        print 'incoming message:', data

        action = data.get('action')
        if action == 'stop_all':
            manager.stop_all()
        if action == 'stop_current':
            manager.stop_current()
        if action == 'play_content':
            manager.play(contents[data['name']], data['duration'])
        if action == 'set_playlist':
            manager.set_playlist(playlists[data['name']], data['duration'])
        if action == 'set_trim':
            manager.set_wing_trim(data['state'])
        if action == 'set_placement':
            self.set_placement(placements[data['ix']])
        if action == 'interactive':
            self.interactive(data['id'], data['sess'], data['type'], data.get('val'))
        if action == 'extend_duration':
            manager.extend_duration(data['duration'])
        if action == 'reset_duration':
            manager.extend_duration(data['duration'], True)

    def on_close(self):
        manager.unsubscribe(self)
        
    def set_placement(self, placement):
        broadcast_event('xo', 'set', placement.get('xo', 0))
        broadcast_event('yo', 'set', placement.get('yo', 0))
        broadcast_event('rot', 'set', placement.get('rot', 0))
        broadcast_event('scale', 'set', placement.get('scale', 1))
        broadcast_event('wingmode', 'set', placement['wing_mode'])
        broadcast_event('stretch', 'set', 'yes' if placement['stretch'] else 'no')

    def interactive(self, id, session, control_type, val):
        if control_type in ('button', 'button-keepalive'):
            button_thread.handle(id, session, {True: 'press', False: 'release', None: 'keepalive'}[val])
        if control_type in ('set', 'slider', 'jog'):
            broadcast_event(id, control_type, val)
        #if control_type == 'raw':
        #    assert False, 'fixme'
        #    if id == 'saveplacement':
        #        from datetime import datetime
        #        val += ' ' + datetime.now().strftime('%m-%d %H%M')
        #    broadcast_event(id, '~' + val)

    def notify(self, msg):
        assert type(msg) == type({}) and (len(msg) == 1 or 'type' in msg), msg
        if 'type' not in msg:
            key = msg.keys()[0]
            msg = {'type': key, key: msg[key]}
        self.write_message(json.dumps(msg))
            
keepalive_timeout = 5.
class ButtonPressManager(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.up = True
        self.queue = Queue.Queue()

        self.presses = {}
        self.active = set()

    def handle(self, id, session, val):
        self.queue.put((id, session, val))

    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            try:
                id, session, val = self.queue.get(True, .01)
                if val in ('press', 'keepalive'):
                    self.presses[(id, session)] = time.time()
                elif val == 'release':
                    try:
                        del self.presses[(id, session)]
                    except KeyError:
                        pass
            except Queue.Empty:
                pass

            expired = []
            for k, v in self.presses.iteritems():
                if time.time() > v + keepalive_timeout:
                    expired.append(k)
            for e in expired:
                del self.presses[k]
            pressed = set(k[0] for k in self.presses.keys())
            for id in (pressed | self.active):
                is_pressed = id in pressed
                is_active = id in self.active
                if is_pressed and not is_active:
                    # send press
                    print 'press', id
                    broadcast_event(id, 'press')
                elif not is_pressed and is_active:
                    # send release
                    print 'release', id
                    broadcast_event(id, 'release')
            self.active = pressed

def broadcast_event(id, type, val=None):
    manager.input_event(id, type, val)
        
    evt = {
        'name': id,
        'eventType': type,
    }
    if val is not None:
        evt['value'] = str(val)        
    zmq_send(json.dumps(evt))

class ZMQListener(threading.Thread):
    def __init__(self, context):
        threading.Thread.__init__(self)
        self.up = True

        self.socket = context.socket(zmq.PULL)
        self.socket.connect("tcp://localhost:%s" % settings.zmq_port_outbound)
        
    def broadcast(self, msg):
        manager.notify(msg)
        
    def handle(self, msg):
        try:
            msg = json.loads(msg)
        except ValueError:
            return
            
        if msg['type'] == 'duration':
            duration = msg['duration']
            if duration < 0 or duration is None:
                duration = settings.sketch_controls_duration_failsafe_timeout
            if (manager.running_content or {}).get('sketch_controls_duration', False):
                manager.extend_duration(duration, True)
        if msg['type'] == 'params':
            msg['source'] = 'processing'
            self.broadcast(msg)
        if msg['type'] == 'param_value':
            self.broadcast(msg)

            
    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            try:
                msg = self.socket.recv(flags=zmq.NOBLOCK)
                self.handle(msg)
            except zmq.Again as e:
                time.sleep(.01)

NOTIF_INTERVAL = 120
class BatteryMonitorThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.up = True
        self.last_notif = None
        self.last_status = self.get_status()
        
    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            status = self.get_status()
            power_source_change = (status['battery_power'] != self.last_status['battery_power'])
            self.last_status = status            
            
            due_for_update = (self.last_notif is None or time.time() - self.last_notif > NOTIF_INTERVAL)
            immediate_update = power_source_change
            if due_for_update or immediate_update:
                manager.notify(status)
                self.last_notif = time.time()
            
            time.sleep(1.)

    def get_status(self):
        info = psutil.sensors_battery()
        status = {
            'type': 'battery',
            'battery_power': not info.power_plugged,
            'battery_charge': info.percent / 100.,
        }
        if status['battery_power']:
            secs = info.secsleft
            if secs >= 0:
                status['remaining_minutes'] = secs / 60.
        return status

if __name__ == "__main__":

    parser = OptionParser()

    (options, args) = parser.parse_args()

    try:
        port = int(args[0])
    except IndexError:
        port = 8000
    ssl = None

    threads = []
    def add_thread(th):
        th.start()
        threads.append(th)
    
    manager = animations.PlayManager(lambda func: IOLoop.instance().add_callback(func))
    add_thread(manager)

    playlists = playlist.load_playlists()
    contents = playlist.all_content()
    placements = animations.load_placements()
    for i, e in enumerate(placements):
        e['ix'] = i

    context = zmq.Context()
    socket = context.socket(zmq.PUB)
    socket.bind("tcp://*:%s" % settings.zmq_port_inbound)
    def zmq_send(msg):
        socket.send(msg.encode('utf8'))

    zmqlisten = ZMQListener(context)
    add_thread(zmqlisten)
        
    button_thread = ButtonPressManager()
    add_thread(button_thread)

    battery_thread = BatteryMonitorThread()
    add_thread(battery_thread)
    
    application = web.Application([
        (r'/', MainHandler),
        (r'/socket', WebSocketTestHandler),
        (r'/(.*)', web.StaticFileHandler, {'path': web_path('static')}),
    ], template_path=web_path('templates'))
    application.listen(port, ssl_options=ssl)

    try:
        IOLoop.instance().start()
    except KeyboardInterrupt:
        pass
    except Exception, e:
        print e
        raise

    for th in threads:
        th.terminate()
    logging.info('shutting down...')
