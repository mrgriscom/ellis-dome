import sys
import os.path

import animations

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
    def initialize(self, manager):
        self.manager = manager

    def open(self):
        pass
        #self.manager.subscribe(self)

    def on_message(self, message):
        data = json.loads(message)
        print 'got message', data
        #self.manager.do_action(lambda o: func(o, data['note']))

        import time
        time.sleep(1.5)
        self.write_message(json.dumps({'b': 56}))
        
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

    application = web.Application([
        (r'/', MainHandler),
        (r'/socket', WebSocketTestHandler, {'manager': manager}),
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
