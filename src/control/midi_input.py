"""
Interface with a MIDI control device and dump certain midi events (button presses, slider
movements, etc.) to a zero-mq pub/sub socket to be read by the processing process.
"""

# Note: pygame.pypm may be unreliable. Might have to patch pygame.midi to use system pypm instead (apt package python-portmidi) (and remove broken pypm.so)

# TODO this does not work with the new input framework

# tube bindings:
# speed jog_a
# hChecks browse
# hSkew jog_b
# hAsym pitch_a
# vAsym pitch_b
# vOffset pitch_inc/dec_a as discrete jog
# vHeight mixer
# reverse playpause_a
# speed sens headphone/sync_a as discrete jog (10)
# hShew sens headphone/sync_b as discrete jog (10)
# vHeightWarpMode playpause_b
# reset back

# layout bindings:
# xo jog_a
# yo jog_b
# rot pitch_a
# scale pitch_b
# stretch load_a toggle
# flap playpause_b
# flapAngle mixer
# wingMode playpause_a




import pygame
from pygame.locals import *
from pygame import midi
import threading
import time
import uuid
import zmq

pygame.init()
midi.init()
# workaround 100% cpu bug
pygame.mixer.quit()

class MIDIDevice(threading.Thread):
    def __init__(self, interface_name):
        threading.Thread.__init__(self)

        self.uid = uuid.uuid4().hex[:8]
        
        self.init_hardware(interface_name)
        self.subscribers = []

        self.lock = threading.Lock()
        self.up = True

    def init_hardware(self, interface_name):
        input_id, output_id = get_devices(interface_name)
        if input_id is None or output_id is None:
            raise IOError
        self.input = midi.Input(input_id)
        self.output = midi.Output(output_id)

    def destroy_hardware(self):
        self.input.close()
        self.output.close()

    def terminate(self):
        self.up = False

    def subscribe(self, sub):
        with self.lock:
            self.subscribers.append(sub)

    def unsubscribe(self, sub):
        with self.lock:
            self.subscribers.remove(sub)

    def broadcast(self, msg):
        with self.lock:
            for sub in self.subscribers:
                sub.received(msg)

    def do_action(self, func):
        func(self.output)

    def get_events(self):
        for ev in self.input.read(1000):
            data, timecode = ev
            yield self.parse_midi_event(data)

    def format_event_message(self, name, val):
        return '%s:%s:%s:%s' % (self.uid, self.device_type, name, val)
            
    def run(self):
        while self.up:
            events = filter(None, self.get_events())
            for ev in events:
                self.broadcast(self.format_event_message(*ev))
            time.sleep(0.01)

        self.destroy_hardware()

    def parse_midi_event(self, raw):
        raise RuntimeError('abstract method')

class DJ2GoDevice(MIDIDevice):

    device_type = 'dj2go'
    
    controls = {
         13: ('pitch_a', 'slider'),
         67: ('pitch_inc_a', 'button'),
         68: ('pitch_dec_a', 'button'),
         64: ('sync_a', 'button'),
        101: ('headphone_a', 'button'),
         51: ('cue_a', 'button'),
         59: ('playpause_a', 'button'),
          8: ('volume_a', 'slider'),
         23: ('volume_master', 'slider'),
         25: ('jog_a', 'jog'),
         75: ('load_a', 'button'),
         89: ('back', 'button'),
         14: ('pitch_b', 'slider'),
         69: ('pitch_inc_b', 'button'),
         70: ('pitch_dec_b', 'button'),
         71: ('sync_b', 'button'),
        102: ('headphone_b', 'button'),
         60: ('cue_b', 'button'),
         66: ('playpause_b', 'button'),
          9: ('volume_b', 'slider'),
         11: ('volume_headphone', 'slider'),
         24: ('jog_b', 'jog'),
         52: ('load_b', 'button'),
         90: ('enter', 'button'),
         26: ('browse', 'jog'),
         10: ('mixer', 'slider'),
    }

    def parse_midi_event(self, raw):
        action, id, val, _ = raw
        name, type = self.controls[id]
        if type == 'button':
            action = {
                0x90: 'press',
                0x80: 'release',
            }[action]
        elif type == 'slider':
            action = val / 127.
        elif type == 'jog':
            action = {
                127: 'dec',
                1: 'inc',
            }[val]
        else:
            raise RuntimeError('unrecognized control type [%s]' % type)

        return name, action
        
class MockDevice(MIDIDevice):

    device_type = 'mock'
    
    class OutputStub(object):
        def __getattr__(self, name):
            def method(*args, **kwargs):
                print name, args, kwargs
            return method

    def __init__(self):
        super(MockDevice, self).__init__(None)
        self.output = self.OutputStub()
        self.keycodes = {}

    def init_hardware(self, interface_name):
        scr = pygame.display.set_mode((80, 80))

    def destroy_hardware(self):
        pass

    def get_events(self):
        for ev in pygame.event.get():
            yield self.parse_midi_event(ev)

    def parse_midi_event(self, raw):
        try:
            state = {
                KEYDOWN: 'on',
                KEYUP: 'off',
                }[raw.type]
        except KeyError:
            return None

        if raw.key not in self.keycodes and hasattr(raw, 'unicode'):
            try:
                self.keycodes[raw.key] = r'`1234567890-=qwertyuiop[]\asdfghjkl;\'zxcvbnm,./'.index(raw.unicode) + 21
            except ValueError:
                self.keycodes[raw.key] = None
        note = self.keycodes[raw.key]
        
        return note, state

def get_devices(name):
    id_in, id_out = None, None
    for i in range(midi.get_count()):
        info = midi.get_device_info(i)
        if info[1] == name:
            if info[2]:
                id_in = i
            elif info[3]:
                id_out = i
    return id_in, id_out


class EchoHandler(object):
    def received(self, data):
        print data

class SocketHandler(object):
    def __init__(self, port):
        context = zmq.Context()
        self.socket = context.socket(zmq.PUB)
        self.socket.bind("tcp://*:%s" % port)
    
    def received(self, data):
        self.socket.send(data)

if __name__ == "__main__":

    try:
        device = DJ2GoDevice('Numark DJ2Go MIDI 1')
        print 'loaded device'
    except IOError:
        device = MockDevice()
        print 'using mock device'
    device.start()

    device.subscribe(EchoHandler())
    device.subscribe(SocketHandler(5556))

    try:
        while True:
            time.sleep(.01)
    except KeyboardInterrupt:
        device.terminate()
        print 'terminating...'
