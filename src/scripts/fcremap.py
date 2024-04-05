#!/usr/bin/python3

import sys
import json
import itertools
from datetime import datetime
import shutil
import pprint

FADECANDY_WHITELIST = set([
    'CMKNKSYEWMNISYZR',
    'CYWIZPCIOYXXVBMB',
    'EUPGNNZHPHNMMONL',
    'GACBIIMUSMXAYIPR',
    'JPBFMAEUBNQXULWX',
    'KOVSNNTTBZRDLLWL',
    'LRHMJFCCGGNWCAZJ',
    'LZHXRFVLNNERMVCF',
    'MAKMFRQSBRWHWPIB',
    'NDEHEAJZNXVRZLQD',
    'QCIXOEMIAULUGQRL',
    'RRABGQLNRLXXJFOF',
    'SKAPSEQUWKVIGQHX',
    'SSYHEUCQCUZKCEHD',
    'THPSLLPHAUZGSGLD',
    'TPZWNNGUSWNSOIPL',
    'UIUYJXAUSONTBBYR',
    'VRPELDJTQEBSIYJV',
    'WASDCAYSKWXCYGZP',
    'WAYSRTWSKAPFPPGJ',
    'WCGPEQSMGYRWUYDV',
    'WWOZWMQQAKZUCSXX',
    'XJITMQZHZXHVXFKD',
])
uuid_lens = set(map(len, FADECANDY_WHITELIST))
assert len(uuid_lens) == 1
UUID_LEN = list(uuid_lens)[0]

def is_valid_uuid(uuid):
    return len(uuid) == UUID_LEN and all(c.isalpha() for c in uuid)

def match_uuid(s):
    return [uuid for uuid in FADECANDY_WHITELIST if uuid.lower().startswith(s.lower())]

def uuid_for_spec(s, ix, current):
    if s == '.':
        try:
            return current[ix]
        except IndexError:
            # want to trigger the '# args dont match' error later
            return 'out of range'
        
    try:
        i = int(s)
        if i < 0 or i >= len(current):
            print('%d out of index range of current config' % i)
            return None
        return current[i]
    except:
        pass

    uuids = match_uuid(s)
    if len(uuids) == 1:
        return uuids[0]
    if not uuids:
        print('%s doesn\'t match any known uuid' % s)
    if len(uuids) > 1:
        print('%s is ambiguous: %s' % (s, ', '.join(sorted(uuids))))
    return None

def assign_new_uuids(new_spec, uuids):
    new_uuids = [uuid_for_spec(s, i, uuids) for i, s in enumerate(new_spec)]
    if any(not uuid for uuid in new_uuids):
        return
    
    print('new config:')
    for i, (new, old) in enumerate(itertools.zip_longest(new_uuids, uuids)):
        print('%d: %s %s' % (i, new or '--', '(unchanged)' if new == old else '(was %s)' % (old or '--')))
    print()

    if len(new_uuids) != len(uuids):
        print('new spec length (%d) doesn\'t match current config (%d)' % (len(new_uuids), len(uuids)))
        return
    if len(set(new_uuids)) != len(new_uuids):
        print('ids in new config are not unique')
        return
    if new_uuids == uuids:
        # not an error, but don't write new file
        return
    
    return new_uuids

def write_new_config(uuids, path):
    backup_path = '%s.bk%s' % (path, datetime.now().strftime('%y%m%d%H%M%S'))
    shutil.copyfile(path, backup_path)
    with open(path) as f_orig:
        config = f_orig.read()
        with open(backup_path) as f_bk:
            assert config == f_bk.read(), 'backup file content doesn\'t match'
    print('current config backed up to %s' % backup_path)

    config = json.loads(config)
    for dev, uuid in zip(config['devices'], uuids):
        dev['serial'] = uuid

    with open(path, 'w') as f:
        f.write(pretty_json(config))

# this is really sketchy but pprint gives a much more readable file than json formatter
def pretty_json(data):
    json_data = pprint.pformat(data)
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
    
if __name__ == '__main__':
    
    path = sys.argv[1]
    new_spec = sys.argv[2:]
    dry_run = True
    if new_spec and new_spec[-1] == '!':
        dry_run = False
        new_spec = new_spec[:-1]
    
    with open(path) as f:
        data = json.load(f)
    uuids = [dev['serial'] for dev in data['devices']]

    print('current config:')
    for i, uuid in enumerate(uuids):
        comment = ''
        if not is_valid_uuid(uuid):
            comment = '(not valid?)'
        elif uuid not in FADECANDY_WHITELIST:
            comment = '(not in list of known IDs)'
            assert is_valid_uuid(uuid)
            # add unknown id to whitelist so we can re-assign it by name
            FADECANDY_WHITELIST.add(uuid)
            
        print('%d: %s %s' % (i, uuid, comment))
    print()

    if not new_spec:
        print('specify new fadecandy uuids in order, separated by spaces')
        print('  .      = don\'t change')
        print('  number = use fadecandy in current position N (0-based)')
        print('  text   = use fadecandy id starting with string')
    else:
        new_uuids = assign_new_uuids(new_spec, uuids)
        if new_uuids:
            if dry_run:
                print("dry run only, append '!' to commit")
            else:
                write_new_config(new_uuids, path)
    
