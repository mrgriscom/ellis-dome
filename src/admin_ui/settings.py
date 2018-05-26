import os.path
import ConfigParser

py_root = os.path.dirname(os.path.abspath(__file__))
repo_root = reduce(lambda a, b: os.path.dirname(a), xrange(2), py_root)

placements_dir = os.path.join(py_root, 'placements')
playlists_dir = os.path.join(py_root, 'playlists')

def load_java_settings(path):
    from StringIO import StringIO
    mock_section = 'dummy'
    with open(path) as f:
        content = '[%s]\n' % mock_section + f.read()
    config = ConfigParser.ConfigParser()
    config.readfp(StringIO(content))
    globals().update(config.items(mock_section))
load_java_settings(os.path.join(repo_root, 'config.properties'))
