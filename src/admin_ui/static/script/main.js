
function clock() {
    return new Date().getTime() / 1000.;
}

function AdminUIModel() {
    this.playlists = ko.observableArray();
    this.contents = ko.observableArray();
    this.placements = ko.observableArray();
    this.wingTrims = ko.observableArray(['raised', 'flat']);
    this.ac_power = ko.observable();
    this.current_playlist = ko.observable();
    this.current_content = ko.observable();
    this.current_timeout = ko.observable();
    
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
      this.ac_power(data.ac_power ? '' : 'LAPTOP ON BATTERY POWER')
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
	} else if (data.type == "content") {
	    model.current_content(JSON.stringify(data.content));
	} else if (data.type == "playlist") {
	    model.current_playlist(data.playlist);
	} else if (data.type == "duration") {
	    model.current_timeout(data.duration);
	} else if (data.type == "params") {
	    initParams(data.params);
	}
    };
    CONN = this.conn;

    $('#stopall').click(function() {
	CONN.send(JSON.stringify({action: 'stop_all'}));
    });
    $('#stopcurrent').click(function() {
	CONN.send(JSON.stringify({action: 'stop_current'}));
    });
    $('#extend').click(function() {
	CONN.send(JSON.stringify({action: 'extend_duration', duration: getDuration()}));
    });

    bindButton('#projectm-next', 'projectm-next');
    bindSlider('#audio-sens', 'audio-sens', false);

    $('#saveplacement').click(function() {
	var name = $('#saveas').val();
	if (name.length == 0) {
	    alert('name required');
	    return;
	}
	
	sendEvent('saveplacement', 'raw', name);
	alert('reload the page');
    });
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

function bindRadioButton(sel, id, subval) {
    $(sel).mousedown(function() {
	sendEvent(id, 'set', subval);
    });
    $(sel).bind('touchstart', function(e) {
	e.preventDefault();
	sendEvent(id, 'set', subval);
    });
}

function bindButton(sel, id) {
    $(sel).mousedown(function() {
	buttonAction(id, true);
    });
    $(sel).bind('touchstart', function(e) {
	e.preventDefault();
	buttonAction(id, true);
    });
    $(sel).mouseup(function() {
	buttonAction(id, false);
    });
    $(sel).bind('touchend', function(e) {
	e.preventDefault();
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

function initParams(params) {
    $.each(PARAMS, function(k, v) {
	if (v.source == params.source) {
	    v.e.remove();
	    delete PARAMS[k];
	}
    });
    
    $.each(params, function(i, e) {
	e.source = params.source;
	initParam(e);
    });
}

PARAMS = {};
function initParam(param) {
    var $section = $('#' + ({
	placement: 'placement_controls',
	mesh_effects: 'effects_controls',
    }[param.category] || 'controls'));
    
    var $container = $('<div class="control" />');
    var $title = $('<div />');
    if (!param.isAction) {
	$title.text(param.description || param.name);
    }
    $container.append($title);
    $section.append($container);

    PARAMS[param.name] = {param: param, e: $container};
    
    var $control = $('<div />');
    $container.append($control);

    if (param.isAction) {
	var $button = $('<button />');
	$button.text(param.description || param.name);
	$control.append($button);
	bindButton($button, param.name);
    } else if (param.isEnum) {
	for (var i = 0; i < param.values.length; i++) {
	    var value = param.values[i];
	    var caption = param.captions[i];
	    
	    var $button = $('<button />');
	    $button.text(caption);
	    $control.append($button);
	    bindRadioButton($button, param.name, value);
	}
    } else if (param.isNumeric) {
	bindSlider($control, param.name, !param.isBounded);
	// isint
    }
}
