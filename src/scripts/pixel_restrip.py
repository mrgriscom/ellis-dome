import json
import math
import csv
import sys
import collections

with open('dev/lsdome/lsdome/src/config/simulator_layouts/prometheus_wing.orig.port.json') as f:
    points = json.load(f)

def vadd(a, b):
    return (a[0] + b[0], a[1] + b[1])

def vsub(a, b):
    return (a[0] - b[0], a[1] - b[1])

def vmult(v, s):
    return (s*v[0], s*v[1])

def vnorm(v):
    return (v[0]**2 + v[1]**2)**.5

def dotp(a, b):
    return a[0]*b[0] + a[1]*b[1]

def to_strips():
    strip = []
    spacers = 0

    def stripinfo():
        return {
            'start': strip[0],
            'end': strip[-1],
            'num_px': len(strip),
            'num_spacer': spacers,
        }
    
    for pt in points:
        x, y, z = pt['point']
        if z != 0:
            if strip:
                yield stripinfo()
                strip = []
                spacers = 0
            spacers += 1
            continue
        if len(strip) >= 2:
            path = vsub(strip[-1], strip[0])
            newpx = vsub((x, y), strip[0])
            cos = dotp(path, newpx) / vnorm(path) / vnorm(newpx)
            if 1 < abs(cos) < 1+1e-6:
                cos = 1 if cos > 0 else -1

            if cos < 1-1e-6:
                yield stripinfo()
                strip = []
                spacers = 0
        strip.append((x, y))
    yield stripinfo()
            
strips = list(to_strips())

with open(sys.argv[1]) as f:
    r = csv.DictReader(f)
    data = list(r)

def to_strands():
    strand = []
    strand_dir = None

    def _strand():
        return strand if strand_dir == 'start' else list(reversed(strand))
    
    for i, row in enumerate(data):
        if row['strand']:
            if strand:
                yield _strand()
                strand = []
            strand_dir = row['strand']
        strand.append(i)
    yield _strand()

strands = list(to_strands())

def reposition_pixels(old_strip, num_new_px, in_space_for=None):
    OLD_PITCH_MM = 142.5
    NEW_PITCH_MM = 149.5
    
    if not in_space_for:
        in_space_for = num_new_px
    
    path = vsub(old_strip['end'], old_strip['start'])
    old_len = vnorm(path)
    vdir = vmult(path, 1./old_len)
    old_pitch = old_len / (old_strip['num_px']-1)
    new_pitch = NEW_PITCH_MM / OLD_PITCH_MM * old_pitch * (float(in_space_for) / num_new_px)
    return [vadd(old_strip['start'], vmult(vdir, new_pitch * i)) for i in range(num_new_px)]
        
def new_strands():
    logical_strip_ix = 0
    for i, strand in enumerate(strands):
        new_px = []
        for physical_strip_ix in strand:
            strip = strips[logical_strip_ix]
            assert strip['num_spacer'] == 0 or physical_strip_ix == strand[0]
            csvrow = data[physical_strip_ix]
            
            new_px.extend([None]*strip['num_spacer'])
            new_px.extend(reposition_pixels(strip, int(csvrow['newpx']), int(csvrow['in space for'])))
            
            logical_strip_ix += 1
        yield new_px

out_strands = list(new_strands())

def gen_fcconfig():
    FCS = [8, 8, 8, 8, 8, 6, 7]
    assert sum(FCS) == len(out_strands)

    fadecandies = []
    strandlens = list(map(len, out_strands))
    for num_strands in FCS:
        fadecandies.append(strandlens[:num_strands])
        strandlens = strandlens[num_strands:]

    count = [0]
    def gen_device(fc):
        dev = {}
        dev['type'] = 'fadecandy'
        dev['serial'] = 'SERIAL'
        dev['map'] = []

        for i, strandlen in enumerate(fc):
            dev['map'].append([0, count[0], i*64, strandlen])
            count[0] += strandlen

        return dev
    
    config = {}
    config["listen"] = [None, 7890]
    config["verbose"] = True
    config["color"] = {"gamma": 2.5, "whitepoint": [1, 1, 1]}
    config['devices'] = list(map(gen_device, fadecandies))
    return config

def gen_layout():
    def _points():
        for strand in out_strands:
            for p in strand:
                if p is None:
                    yield [-3.5714285714285716, -5, 1]
                else:
                    yield [p[0], p[1], 0]
    return [{'point': coord} for coord in _points()]


# this is really sketchy but pprint gives a much more readable file than json formatter
def pretty_json(data):
    import pprint
    json_data = pprint.pformat(data, sort_dicts=False)
    replacements = {
        "'": '"',
        'None': 'null',
        'True': 'true',
        'False': 'false',
    }
    for k, v in replacements.items():
        json_data = json_data.replace(k, v)

    assert json.loads(json_data) == data
    return json_data

with open('/tmp/fcconfig.json', 'w') as f:
    f.write(pretty_json(gen_fcconfig()) + '\n')

with open('/tmp/layout.json', 'w') as f:
    f.write(pretty_json(gen_layout()) + '\n')
