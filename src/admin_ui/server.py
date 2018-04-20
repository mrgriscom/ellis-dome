import sys
import os.path

import animations
import playlist

project_root = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, project_root)

from tornado.ioloop import IOLoop
import tornado.web as web
import tornado.gen as gen
from tornado.template import Template
import tornado.websocket as websocket
from optparse import OptionParser
import logging
import json

def web_path(*args):
    return os.path.join(project_root, *args)

class MainHandler(web.RequestHandler):
    def get(self):
        self.render('main.html', onload='init')

class WebSocketTestHandler(websocket.WebSocketHandler):
    def initialize(self, manager, static_data):
        self.manager = manager
        self.static_data = static_data

    def open(self):
        msg = {
            'type': 'init',
            'playlists': sorted([{'name': k} for k in self.static_data['playlists'].keys()], key=lambda e: e['name']),
            'contents': sorted([{'name': playlist.content_name(c), 'config': c} for c in self.static_data['contents']], key=lambda e: e['name']),
        }
        self.write_message(json.dumps(msg))
        #self.manager.subscribe(self)

    def on_message(self, message):
        data = json.loads(message)
        print 'incoming message:', data
        
        action = data.get('action')
        if action == 'stop_all':
            manager.stop_all()
        if action == 'stop_current':
            manager.stop_current()
        if action == 'play_content':
            manager.play([c for c in self.static_data['contents'] if playlist.content_name(c) == data['name']][0], data['duration'])
        if action == 'set_playlist':
            manager.set_playlist(self.static_data['playlists'][data['name']], data['duration'])
        if action == 'set_trim':
            manager.set_wing_trim(data['state'])
            
    def on_close(self):
        pass
        #self.manager.unsubscribe(self)

if __name__ == "__main__":

    parser = OptionParser()

    (options, args) = parser.parse_args()

    try:
        port = int(args[0])
    except IndexError:
        port = 8000
    ssl = None

    manager = animations.PlayManager()
    manager.start()

    static_data = {
        'playlists': playlist.load_playlists(),
        'contents': list(playlist.get_all_content()),
    }
    
    application = web.Application([
        (r'/', MainHandler),
        (r'/socket', WebSocketTestHandler, {'manager': manager, 'static_data': static_data}),
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

    manager.terminate()
    logging.info('shutting down...')
