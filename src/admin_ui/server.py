import sys
import os.path
import os

import settings
import animations
import playlist
import launch
import quiet

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
from datetime import datetime, timedelta
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


class RebootHandler(AuthenticationMixin, web.RequestHandler):
    @web.authenticated
    def get(self):
        self.render('kill.html')

    @web.authenticated
    def post(self):
        # don't orphan running content (not necessary once we can persist state (content PIDs, etc.) across runs)
        manager.stop_all()
        time.sleep(3.)  # give time to terminate content

        pid = os.getpid()
        print 'self-terminating process %d' % pid
        psutil.Process(pid).kill()

        # no response returned

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
                try:
                    with open(settings.uptime_log, 'a') as f:
                        f.write('%s: %s\n' % (datetime.now(), status))
                except IOError:
                    print 'error logging uptime'
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
        self.last_run = None
        self.param_id = animations.QuietModeParameter(None).param['name']

    def terminate(self):
        self.up = False

    def run(self):
        while self.up:
            now = datetime.now()

            # a bit out of place, but this is actually the easiest place to handle this
            self.get_param().volume_param().reconcile()

            self.update_daytime_quiet_hours(now)

            periods = quiet.GoDarkSet(settings.quiet_hours)
            def exec_event(type, start_action, end_action):
                ev, skipped = periods.latest_event(type, now, self.last_run)
                if skipped:
                    print 'skipping %s of %s quiet period [%s] due to %s' % (skipped['type'], type, skipped['time'], skipped['status'])
                if ev:
                    if self.last_run or ev['type'] == 'start':
                        print now, '%s quiet period %s [%s]' % (type, ev['type'], ev['time'])
                    action = {'start': start_action, 'end': end_action}[ev['type']]
                    getattr(self.get_param(), action)()
            exec_event('audio', 'go_silent', 'resume_audio')
            exec_event('visual', 'go_dark', 'resume_display')

            # remove param once expired. active periods are regenerated on content reload,
            # but due to the nature of quiet hours, running content won't be changing often.
            # (note that param won't disappear from open UI sessions until refresh)
            # cache ref to dict to avoid race conditions if content changes
            cur_params = manager.content.params
            for k, v in cur_params.items():  # no iteritems due to deletion during iteration
                if isinstance(v, animations.QuietPeriodParameter) and v.period.expired(now):
                    del cur_params[k]

            self.last_run = now
            time.sleep(1.)

    def update_daytime_quiet_hours(self, now):
        if not settings.auto_quiet_daytime:
            return
        import astral

        def to_local_time(utc):
            return datetime.fromtimestamp((utc.replace(tzinfo=None) - datetime.utcfromtimestamp(0)).total_seconds())

        days_in_advance = 1
        d = self.last_run.date() + timedelta(days=1 + days_in_advance) if self.last_run else now.date()
        cutoff = now.date() + timedelta(days=days_in_advance)
        while d <= cutoff:
            try:
                suninfo = astral.Astral().sun_utc(d, *settings.latlon)
                sunrise = to_local_time(suninfo['sunrise'])
                sunset = to_local_time(suninfo['sunset'])
                start = sunrise - timedelta(minutes=getattr(settings, 'mins_before_sunrise', 0))
                end = sunset + timedelta(minutes=getattr(settings, 'mins_after_sunset', 0))
                if end > start:
                    #print 'adding daylight off period %s to %s' % (start, end)
                    self.add_period(quiet.GoDark(start, end-start, name='daylight hours'))
            except astral.AstralError:
                # arctic operation not supported
                pass
            d += timedelta(days=1)

    def add_period(self, p):
        settings.quiet_hours.append(p)
        # will be out of order in the list until content change; nothing we can do about this... meh
        manager.content.add_param(p.param(manager))

    def get_param(self):
        # quiet mode parameter is a universal param -- should always be present
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

    def _callback():
        # trap a ref to the main loop in a closure; IOLoop.instance() doesn't work from helper threads in tornado 5+
        main_loop = IOLoop.current()
        return lambda func: main_loop.add_callback(func)
    manager = animations.PlayManager(broadcast_event, _callback())
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
        (r'/reincarnate', RebootHandler),
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
