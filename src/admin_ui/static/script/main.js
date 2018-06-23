
function clock() {
    return new Date().getTime() / 1000.;
}

function AdminUIModel() {
    this.playlists = ko.observableArray();
    this.contents = ko.observableArray();
    this.placements = ko.observableArray();
    this.placementModes = ko.observableArray();
    this.battery_status = ko.observable();
    this.battery_alert = ko.observable(false);
    this.current_playlist = ko.observable();
    this.default_duration = ko.observable();
    this.current_content = ko.observable();
    this.content_launch_time = ko.observable();
    this.current_timeout = ko.observable();
    this.aspect_ratio = ko.observable();

    var model = this;
    this.now = ko.observable(new Date());
    setInterval(function() {
	model.now(new Date());
    }, 1000);
    this.time_remaining = ko.computed(function() {
	if (!model.current_timeout()) {
	    return '\u221e';
	}

	var diff = Math.floor((model.current_timeout() - model.now()) / 1000.);
	if (diff < 0) {
	    if (diff >= -1) {
		diff = 0;
	    } else {
		return '(overdue)';
	    }
	}

	var m = Math.floor(diff / 60);
	var s = diff % 60;
	return m + 'm ' + s + 's';
    });
    
    this.setMode = function(e) {
	if (model.placementModes.indexOf(e) == 0) {
	    e = null;
	}
	CONN.send(JSON.stringify({action: 'set_placement_mode', state: e}));
    }
}

function PlaylistModel() {
    this.name = ko.observable();
    this.count = ko.observable();
    //this.items = ko.observableArray();

    this.load = function(data) {
	console.log(data);
	this.name(data.name);
	this.count(data.items.length);
    }

    this.play = function() {
	CONN.send(JSON.stringify({action: 'set_playlist', name: this.name(), duration: getDuration()}));
    }
}

function ContentModel() {
    this.name = ko.observable();

    this.load = function(data) {
	this.name(data.name);
    }

    this.play = function() {
	CONN.send(JSON.stringify({action: 'play_content', name: this.name(), duration: getDuration()}));
    }
}

function PlacementModel() {
    this.name = ko.observable();
    this.ix = ko.observable();

    this.load = function(data) {
	this.name(data.name);
	this.ix(data.ix);
    }

    this.set = function() {
	CONN.send(JSON.stringify({action: 'set_placement', ix: this.ix()}));
    }
}

function getDuration() {
    return $('#mins').val() * 60;
}

function connect(model) {
    var conn = new WebSocket('ws://' + window.location.host + '/socket');
    $('#connectionstatus').text('connecting to server...');

    var connectionLost = function() {
	$('#connectionstatus').text('connection to server lost; reload to reconnect');
	$('#ui').css('opacity', .65);
	$('#connectionstatus').css('font-weight', 'bold');
	$('button').prop('disabled', true);
	$.each(PARAMS, function(k, v) {
	    if (v.$slider) {
		v.$slider.slider("option", "disabled", true);
	    }
	});
	scrollTo(0, 0);
    }
    
    conn.onopen = function () {
	$('#connectionstatus').text('');
    };
    conn.onclose = function() {
	connectionLost();
    };
    conn.onerror = function (error) {
        console.log('websocket error ' + error);
	connectionLost();
    };
    conn.onmessage = function (e) {
	console.log('receiving msg');
        var data = JSON.parse(e.data);
	console.log(data);

	if (data.type == "playlists") {
	    $.each(data.playlists, function(i, e) {
		var pl = new PlaylistModel();
		pl.load(e);
		model.playlists.push(pl);
	    });
	} else if (data.type == "contents") {
	    $.each(data.contents, function(i, e) {
		var c = new ContentModel();
		c.load(e);
		model.contents.push(c);
	    });
	} else if (data.type == "placements") {
	    $.each(data.placements, function(i, e) {
		var p = new PlacementModel();
		p.load(e);
		model.placements.push(p);
	    });
	} else if (data.type == "placement_modes") {
	    model.placementModes.push('all placements');
	    model.placementModes(model.placementModes().concat(data.placement_modes));
	} else if (data.type == "content") {
	    model.current_content(data.content.name);
	    model.content_launch_time(new Date(data.content.launched_at * 1000));
	    model.aspect_ratio(data.content.aspect || 'n/a');
	} else if (data.type == "playlist") {
	    model.current_playlist(data.playlist ? data.playlist.name : null);
	    model.default_duration(data.playlist.duration / 60.);
	} else if (data.type == "duration") {
	    model.current_timeout(data.duration ? new Date(data.duration * 1000) : 'n/a');
	} else if (data.type == "params") {
	    initParams(data);
	} else if (data.type == "param_value") {
	    updateParamValue(data);
	} else if (data.type == "battery") {
	    var status = '';
	    if (data.battery_power || data.battery_charge < .99) {
		status += (data.battery_power ? 'ON BATTERY' : 'charging');
		status += ' ' + Math.floor(100. * data.battery_charge) + '%';
		if (data.remaining_minutes) {
		    status += ' (' + Math.floor(data.remaining_minutes) + ' min remaining)';
		}
	    }
	    model.battery_status(status);
	    model.battery_alert(data.battery_power);
	}
    };
    CONN = conn;
}

function init() {
    var model = new AdminUIModel();
    ko.applyBindings(model);
    connect(model);    

    $('#stopall').click(function() {
	CONN.send(JSON.stringify({action: 'stop_all'}));
    });
    $('#stopcurrent').click(function() {
	CONN.send(JSON.stringify({action: 'stop_current'}));
    });
    $('#extend').click(function() {
	CONN.send(JSON.stringify({action: 'extend_duration', duration: getDuration()}));
    });
    $('#resetduration').click(function() {
	CONN.send(JSON.stringify({action: 'reset_duration', duration: getDuration()}));
    });

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

SLIDER_MIN = -100;
SLIDER_MAX = 100;
function bindSlider(sel, id, relative) {
    var mid = .5 * (SLIDER_MIN + SLIDER_MAX);
    var $e = $(sel);
    $e.slider({min: SLIDER_MIN, max: SLIDER_MAX});
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
	    sendEvent(id, 'slider', (cur-SLIDER_MIN)/(SLIDER_MAX-SLIDER_MIN));
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
    return $e;
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

PARAMS = {};
function initParams(data) {
    // if invocation id is different, we're running a new sketch; clear out all
    // the parameters associated with the previous sketch
    $.each(PARAMS, function(k, v) {
	if (v.param.invocation != data.invocation) {
	    v.e.remove();
	    delete PARAMS[k];
	}
    });
    
    $.each(data.params, function(i, e) {
	e.invocation = data.invocation;
	initParam(e);
    });
}

function initParam(param) {
    if (param.category == 'hidden') {
	return;
    }

    // same param was re-sent; just easier to deal with here
    if (PARAMS[param.name]) {
	PARAMS[param.name].e.remove();
    }
    
    var $section = $('#' + ({
	placement: 'placement_controls',
	mesh_effects: 'effects_controls',
	audio: 'audio_controls',
    }[param.category] || 'controls'));
    
    var $container = $('<div class="control" />');
    $section.append($container);

    PARAMS[param.name] = {param: param, e: $container};
    
    if (param.isAction) {
	var $button = $('<button />');
	$container.append($button);
	$button.text(param.description || param.name);
	bindButton($button, param.name);
    } else if (param.isEnum) {
	$container.html('<div id="title" />');
	for (var i = 0; i < param.values.length; i++) {
	    var value = param.values[i];
	    var caption = param.captions[i];
	    
	    var $button = $('<button />');
	    $button.text(caption);
	    $container.append($button);
	    bindRadioButton($button, param.name, value);
	}
    } else if (param.isNumeric) {
	$container.html('<div id="value" style="float: right;" /><div id="title" />');
	var $slider = $('<div style="margin-top: 4px; margin-bottom: 2px;" />');
	$container.append($slider);
	PARAMS[param.name].$slider = bindSlider($slider, param.name, !param.isBounded);
	// isint
    }

    if (!param.isAction) {
	$container.find('#title').text(param.description || param.name);
    }
}

function updateParamValue(val) {
    var P = PARAMS[val.name];
    if (!P) {
	return;
    }
    var param = P.param;
    var $e = P.e;

    if (param.isAction) {
	// do nothing
    } else if (param.isEnum) {
	var $buttons = $e.find('button');
	for (var i = 0; i < param.values.length; i++) {
	    var $b = $($buttons[i]);
	    var value = param.values[i];
	    $b.css('font-weight', value == val.value ? 'bold' : 'normal');
	}
    } else if (param.isNumeric) {
	$e.find('#value').text(val.value);
	if (param.isBounded) {
	    var k = val.sliderPos;
	    var sliderVal = SLIDER_MIN * (1 - k) + SLIDER_MAX * k;
	    P.$slider.slider('value', sliderVal);
	}
    }
}
