#!/usr/bin/python

import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../admin_ui'))
import settings
import playlist
import launch

num_opcs = {
    'prometheus': 2,
}.get(settings.geometry, 1)

fcconfigs = playlist.fadecandy_config()
layouts = [fcconfigs[k].replace('/fadecandy/', '/simulator_layouts/') for k in sorted(fcconfigs.keys())]

for i in xrange(num_opcs):
    layout = layouts[0 if len(layouts) == 0 else i]
    cmd = '%s -l "%s" -p %d &' % (settings.opc_simulator_path, layout, int(settings.opcport) + i)
    # note: can't set always-on-top for additional simulator windows since the windows can't be searched by pid :-/
    launch.launch_external(cmd, 'OPC')
