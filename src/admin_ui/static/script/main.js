
function clock() {
    return new Date().getTime() / 1000.;
}

function AdminUIModel() {
    this.playlists = ko.observableArray();
    this.contents = ko.observableArray();
    this.placements = ko.observableArray();
    this.wingTrims = ko.observableArray(['raised', 'flat']);
    
    var model = this;
    this.load = function(data) {
	$.each(data.playlists, function(i, e) {
	    var pl = new PlaylistModel();
	    pl.load(e);
	    model.playlists.push(pl);
	});
	$.each(data.contents, function(i, e) {
	    var c = new ContentModel();
	    c.load(e);
	    model.contents.push(c);
	});
	$.each(data.placements, function(i, e) {
	    var p = new PlacementModel();
	    p.load(e);
	    model.placements.push(p);
	});
    }

    this.setTrim = function(e) {
	CONN.send(JSON.stringify({action: 'set_trim', state: e}));
    }
}

function PlaylistModel() {
    this.name = ko.observable()

    this.load = function(data) {
	console.log(data);
	this.name(data.name);
    }

    this.play = function() {
	CONN.send(JSON.stringify({action: 'set_playlist', name: this.name(), duration: getDuration()}));
    }
}

function ContentModel() {
    this.name = ko.observable()

    this.load = function(data) {
	this.name(data.name);
    }

    this.play = function() {
	CONN.send(JSON.stringify({action: 'play_content', name: this.name(), duration: getDuration()}));
    }
}

function PlacementModel() {
    this.name = ko.observable();
    this.stretch = ko.observable();
    this.ix = ko.observable();
    
    this.load = function(data) {
	this.name(data.name);
	this.stretch(data.stretch);
	this.ix(data.ix);
    }

    this.set = function() {
	CONN.send(JSON.stringify({action: 'set_placement', ix: this.ix()}));
    }
}

function getDuration() {
    return $('#mins').val() * 60;
}


function init() {
    var that = this;

    var model = new AdminUIModel();
    ko.applyBindings(model);
    
    this.conn = new WebSocket('ws://' + window.location.host + '/socket');
    this.conn.onopen = function () {
    };
    this.conn.onclose = function() {
	connectionLost();
    };
    this.conn.onerror = function (error) {
        console.log('websocket error ' + error);
	connectionLost();	
    };
    this.conn.onmessage = function (e) {
	console.log('receiving msg');
        var data = JSON.parse(e.data);
	console.log(data);

	if (data.type == "init") {
	    model.load(data);
	}
    };
    CONN = this.conn;
    
    $('#stopall').click(function() {
	CONN.send(JSON.stringify({action: 'stop_all'}));
    });
    $('#stopcurrent').click(function() {
	CONN.send(JSON.stringify({action: 'stop_current'}));
    });
    bindButton('#flap', 'flap');
    bindButton('#projectm-next', 'projectm-next');
    
    bindButton('#wingmode_unified', 'wingmode_unified');
    bindButton('#wingmode_mirror', 'wingmode_mirror');
    bindButton('#wingmode_flip', 'wingmode_flip');
    bindButton('#wingmode_rotate', 'wingmode_rotate');
    bindButton('#aspect_stretch', 'stretch_yes');
    bindButton('#aspect_preserve', 'stretch_no');

    bindSlider('#jog-xo', 'jog-xo', true);
    bindSlider('#jog-yo', 'jog-yo', true);
    bindSlider('#jog-rot', 'jog-rot', true);
    bindSlider('#jog-scale', 'jog-scale', true);
    bindSlider('#flap-angle', 'flap-angle', false);
}

function bindSlider(sel, id, relative) {
    var min = -100;
    var max = 100;
    var mid = .5 * (min + max);
    var $e = $(sel);
    $e.slider({min: min, max: max});
    $e.slider('value', mid);
    $e.lastVal = $e.slider('value');
    $e.on('slide', function(evt, ui) {
	var cur = ui.value;
	if (relative) {
	    var diff = cur - $e.lastVal;
	    for (var i = 0; i < Math.abs(diff); i++) {
		sendEvent(id, 'jog', diff > 0 ? 1 : -1);
	    }
	} else {
	    sendEvent(id, 'slider', (cur-min)/(max-min));
	}
	$e.lastVal = cur;
    });
    if (relative) {
	$e.on('slidechange', function(evt, ui) {
	    if (ui.value != mid) {
		$e.slider('value', mid);
		$e.lastVal = mid;
	    }
	});
    }
}

function bindButton(sel, id) {
    $(sel).mousedown(function() {
	buttonAction(id, true);
    });
    $(sel).on('touchstart', function() {
	buttonAction(id, true);
    });
    $(sel).mouseup(function() {
	buttonAction(id, false);
    });
    $(sel).on('touchend', function() {
	buttonAction(id, false);
    });
}

function connectionLost() {
    alert('connection to server lost; reload the page');
}

SESSION_ID = Math.floor(1000000000*Math.random());
function sendEvent(id, type, val) {
    CONN.send(JSON.stringify({action: 'interactive', sess: SESSION_ID, id: id, type: type, val: val}));
}

BUTTON_KEEPALIVES = {};
function buttonAction(id, pressed) {
    
    if (pressed) {
	sendEvent(id, 'button', true);
	BUTTON_KEEPALIVES[id] = setInterval(function() {
	    sendEvent(id, 'button-keepalive');
	}, 1000);
    } else {
	sendEvent(id, 'button', false);
	clearInterval(BUTTON_KEEPALIVES[id]);
    }
}
