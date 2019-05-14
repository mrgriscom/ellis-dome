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
from tornado_http_auth import BasicAuthMixin, auth_required
from optparse import OptionParser
import logging
import json
import zmq
from datetime import datetime
import threading
import Queue
import time
import psutil
import itertools
import bisect

ID_COOKIE = 'user'
VALID_USER = 'valid'

def web_path(*args):
    return os.path.join(settings.py_root, *args)

class AuthenticationMixin(object):
    def get_current_user(self):
        return self.get_secure_cookie(ID_COOKIE) if settings.enable_security else VALID_USER

    def get_login_url(self):
        return self.reverse_url('login')

    @staticmethod
    def authenticate_hard_stop(handler):
        def _wrap(self, *args):
            if not self.current_user:
                self.set_status(403)
                self.finish()
                return
            else:
                handler(self, *args)
        return _wrap
    
class MainHandler(AuthenticationMixin, web.RequestHandler):
    @web.authenticated
    def get(self):
        self.render('main.html', onload='init', geom=settings.geometry, default_duration=settings.default_duration/60.)
        
class GamesHandler(AuthenticationMixin, web.RequestHandler):
    @web.authenticated
    def get(self, suffix):
        suffix = suffix[1:] if suffix else ''
        self.render('game.html', onload='init_game', search=json.dumps(suffix))

# DigestAuthMixin doesn't seem to work on chrome
class LoginHandler(BasicAuthMixin, web.RequestHandler):
    def prepare(self):
        # torpedo request before there's a chance of sending passwords in the clear
        assert settings.enable_security        
    
    @auth_required(realm='Protected', auth_func=lambda username: settings.login_password)
    def get(self):
        self.set_secure_cookie(ID_COOKIE, VALID_USER, path='/')
        self.redirect(self.get_argument('next'))

class WebsocketHandler(AuthenticationMixin, websocket.WebSocketHandler):
    def initialize(self, get_content, playlists={}):
        self.get_content = get_content
        self.playlists = playlists

    @AuthenticationMixin.authenticate_hard_stop
    def get(self, *args):
        # intercept and authenticate before websocket setup / protocol switch
        super(WebsocketHandler, self).get(*args)
        
    def open(self, *args):
        self.contents = self.get_content(*args)

        self.notify({'playlists': [pl.to_json() for pl in sorted(self.playlists.values(), key=lambda pl: pl.name)]})
        self.notify({'contents': [c.to_json_info() for c in sorted(self.contents.values(), key=lambda c: c.name)]})
        self.notify({'placements': [p.to_json(i) for i, p in enumerate(manager.placements)]})
        self.notify({'placement_modes': manager.placement_modes})
        manager.subscribe(self)
        self.notify(battery_thread.get_status())

    def on_message(self, message):
        data = json.loads(message)
        print 'incoming message:', datetime.now(), self.request.remote_ip, data

        action = data.get('action')
        if action == 'stop_all':
            manager.stop_all()
        if action == 'stop_current':
            manager.stop_current()
        if action == 'play_content':
            manager.play(self.contents[data['name']], data['duration'])
        if action == 'set_playlist':
            manager.set_playlist(self.playlists[data['name']], data['duration'])
        if action == 'set_placement_mode':
            manager.set_placement_mode(data['state'])
        if action == 'set_placement':
            manager.placements[data['ix']].activate(broadcast_event)
        if action == 'lock_placement':
            manager.lock_placement(data['ix'])
        if action == 'interactive':
            self.interactive(data['id'], data['sess'], data['type'], data.get('val'))
        if action == 'extend_duration':
            manager.extend_duration(data['duration'])
        if action == 'reset_duration':
            manager.extend_duration(data['duration'], True)
        if action == 'save_placement':
            start_placement_save(data['name'])

    def on_close(self):
        manager.unsubscribe(self)

    def interactive(self, id, session, control_type, val):
        if control_type in ('button', 'button-keepalive'):
            button_thread.handle(id, session, {True: 'press', False: 'release', None: 'keepalive'}[val])
        if control_type in ('set', 'slider', 'jog'):
            broadcast_event(id, control_type, val)

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
        # counter-intuitively set the pull side as the listener (zmq is agnostic about this)
        # so that all sockets from java-land are outbound
        self.socket.bind("tcp://*:%s" % settings.zmq_port_outbound)

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
            manager.extend_duration(duration, True, True)
        if msg['type'] == 'params':
            msg['invocation'] = manager.content.uuid
            self.broadcast(msg)
        if msg['type'] == 'param_value':
            self.broadcast(msg)
        if msg['type'] == 'aspect':
            manager.update_content_info({'aspect': msg['aspect']})
        if msg['type'] == 'placement':
            commit_placement_save(msg)

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

class QuietHoursThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.up = True
        self.last_volume = None
        self.param_id = animations.MasterVolumeParameter(None).param['name']
        self.validate_quiet_hours()

    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            param = self.get_param()
            get_val = lambda: param.get_value()['sliderPos']
            
            quiet = self.is_quiet_hours(datetime.now())
            muted = get_val() < 1e-3
            if quiet != muted:
                if quiet:
                    print 'muting for start of quiet hours', datetime.now()
                    self.last_volume = get_val()
                    broadcast_event(self.param_id, 'slider', 0.)
                else:
                    print 'unmuting for end of quiet hours', datetime.now()
                    broadcast_event(self.param_id, 'slider', self.last_volume)
                
            time.sleep(1.)

    def flatten_quiet_hours(self):
        return list(itertools.chain(*sorted(settings.quiet_hours)))
            
    def validate_quiet_hours(self):
        assert len(self.flatten_quiet_hours()) == len(set(self.flatten_quiet_hours()))
        assert self.flatten_quiet_hours() == sorted(self.flatten_quiet_hours())
            
    def is_quiet_hours(self, t):
        return bisect.bisect_right(self.flatten_quiet_hours(), t) % 2 == 1
            
    def get_param(self):
        # volume parameter should always be present
        return manager.content.params[self.param_id]
    
pending_placement_save = None
def start_placement_save(name):
    if not manager.content.running():
        return

    global pending_placement_save
    pending_placement_save = {'name': '%s (%s)' % (name, datetime.now().strftime('%m-%d %H:%M'))}
    broadcast_event('_placement', 'set')
def commit_placement_save(msg):
    global pending_placement_save
    if not pending_placement_save:
        return

    pending_placement_save['params'] = dict((pv['name'], pv['value']) for pv in msg['params'])

    aspect = '%.3f:1' % manager.content.info.get('aspect', 1)
    data = {
        'geometry': settings.geometry,
        'name': pending_placement_save['name'],
        'modes': 'custom',
        'aspect': 'stretch' if pending_placement_save['params'].get('stretch aspect') == 'yes' else aspect,
    }
    data.update(pending_placement_save['params'])
    if 'stretch aspect' in data:
        del data['stretch aspect']

    with open(os.path.join(settings.placements_dir, pending_placement_save['name']), 'w') as f:
        f.write(json.dumps(data))
    manager.add_placement(data)

    pending_placement_save = None

# this assumes fc config on hub matches local copy in repo
def validate_config():
    with open(playlist.fadecandy_config()) as f:
        config = json.load(f)
    if config['color']['whitepoint'] != [1, 1, 1]:
        print '*** NOT RUNNING AT FULL BRIGHTNESS ***'
    

if __name__ == "__main__":

    parser = OptionParser()

    (options, args) = parser.parse_args()

    try:
        port = int(args[0])
    except IndexError:
        port = 8000

    if settings.enable_security:
        assert settings.login_password, 'password not configured!'
        
    threads = []
    def add_thread(th):
        th.start()
        threads.append(th)

    manager = animations.PlayManager(broadcast_event, (lambda func: IOLoop.instance().add_callback(func)) if not settings.tornado_callbacks_hack else None)
    add_thread(manager)

    playlists = playlist.load_playlists()
    contents = playlist.all_content()

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

    if settings.audio_out:
        add_thread(QuietHoursThread())

    validate_config()
    
    application = web.Application([
        (r'/', MainHandler),
        web.URLSpec('/login', LoginHandler, name='login'),
        (r'/game(/.*)?', GamesHandler),
        (r'/socket/main', WebsocketHandler, {'playlists': playlists, 'get_content': lambda: contents}),
        (r'/socket/game/(.*)', WebsocketHandler, {'get_content': lambda query: playlist.load_games(query)}),
        (r'/(.*)', web.StaticFileHandler, {'path': web_path('static')}),
    ],
        template_path=web_path('templates'),
        cookie_secret=settings.cookie_secret,
        debug=False,
        #debug=True,
    )
    application.listen(port, ssl_options=settings.ssl_config if settings.enable_security else None)

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
