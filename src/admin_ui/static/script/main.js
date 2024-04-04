

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
    this.current_placement_mode = ko.observable();
    this.locked_placement = ko.observable();

    var model = this;
    this.local_now = ko.observable(new Date());
    setInterval(function() {
	model.local_now(new Date());
    }, 1000);
    this.server_clock_offset = ko.observable(0.);
    this.set_server_now = function(server_now) {
      model.server_clock_offset(server_now - model.local_now());
    }
    this.now = ko.computed(function() {
      return new Date(model.local_now().getTime() + model.server_clock_offset());
    });
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

    this.locked_placement_name = ko.computed(function() {
	return model.locked_placement() != null ? model.placements()[model.locked_placement()].name() : null;
    });

    this.setMode = function(e) {
	if (model.placementModes.indexOf(e) == 0) {
	    e = null;
	}
	CONN.send(JSON.stringify({action: 'set_placement_mode', state: e}));
    }

    this.unlockPlacement = function(e) {
	CONN.send(JSON.stringify({action: 'lock_placement', ix: null}));
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
    this.available = ko.observable(false);

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
    this.available = ko.observable(false);

    this.load = function(data) {
	this.name(data.name);
	this.ix(data.ix);
    }

    this.set = function() {
	CONN.send(JSON.stringify({action: 'set_placement', ix: this.ix()}));
    }

    this.lock = function() {
	CONN.send(JSON.stringify({action: 'lock_placement', ix: this.ix()}));
    }
}

function getDuration() {
    return $('#mins').val() * 60;
}

function connect(model, mode) {
    var secure = window.location.protocol.startsWith('https');
    if (secure && window.location.host.startsWith('localhost')) {
	alert("chrome doesn't support secure websockets to 'localhost'; use an actual IP address");
    }

    var conn = new WebSocket((secure ? 'wss' : 'ws') + '://' + window.location.host + '/socket/' + mode);
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
	    model.playlists($.map(data.playlists, function (e) {
		var pl = new PlaylistModel();
		pl.load(e);
		return pl;
	    }));
	} else if (data.type == "contents") {
	    model.contents($.map(data.contents, function (e) {
		var c = new ContentModel();
		c.load(e);
		return c;
	    }));
	} else if (data.type == "placements") {
	    model.placements($.map(data.placements, function (e) {
		var p = new PlacementModel();
		p.load(e);
		return p;
	    }));
	} else if (data.type == "placement_modes") {
	    model.placementModes(['all placements'].concat(data.placement_modes));
	} else if (data.type == "placement_mode") {
	    model.current_placement_mode(data.placement_mode || 'all placements');
	    _.each(model.placements(), function(e, i) {
		e.available(data.placements.indexOf(e.ix()) >= 0);
	    });
	} else if (data.type == "content") {
	    model.current_content(data.content.name);
	    model.content_launch_time(new Date(data.content.launched_at * 1000));
	    model.aspect_ratio(data.content.aspect);
	} else if (data.type == "playlist") {
	    var playlist = data.playlist || {items: []};
	    model.current_playlist(playlist.name);
	    model.default_duration(playlist.duration / 60.);

	    _.each(model.contents(), function(e) {
		e.available(playlist.items.indexOf(e.name()) >= 0);
	    });
	} else if (data.type == "locked_placement") {
	    model.locked_placement(data.locked_placement);
	} else if (data.type == "duration") {
	    model.current_timeout(data.duration ? new Date(data.duration * 1000) : null);
            if (data.server_now) {
                model.set_server_now(new Date(data.server_now * 1000));
            }
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
    _init('main');
}

function init_game() {
    _init('game/' + SEARCH);
}

function _init(mode) {
    var model = new AdminUIModel();
    ko.applyBindings(model);
    connect(model, mode);
    MODEL = model;

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
	CONN.send(JSON.stringify({action: 'save_placement', name: name}));
	alert('reload the page to access the saved placement');
    });
}

SLIDER_MIN = -100;
SLIDER_MAX = 100;
function bindSlider(sel, id, relative, rel_sens) {
    var mid = .5 * (SLIDER_MIN + SLIDER_MAX);
    var $e = $(sel);
    $e.slider({min: SLIDER_MIN, max: SLIDER_MAX});
    $e.slider('value', mid);

    // TODO
    // click-anywhere-on-slider-bar to move handle is disabled in our css
    // it would be nice if we could re-enable this functionality JUST through dbl-click

    $e.lastVal = $e.slider('value');
    $e.on('slide', function(evt, ui) {
	var cur = ui.value;
	if (relative) {
	    var diff = cur - $e.lastVal;
	    sendEvent(id, 'jog', diff / (rel_sens || 1.));
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
    $(sel).click(function() {
	sendEvent(id, 'set', subval);
    });
}

function bindClickButton(sel, id, subval) {
    $(sel).click(function() {
	buttonAction(id, true);
	buttonAction(id, false);
    });
}

function bindPressReleaseButton(sel, id) {
    var offcolor = '#ffc';
    var oncolor = '#35d';
    $(sel).css('background', offcolor);
    var set_state = function(on) {
        $(sel).css('background', on ? oncolor : offcolor);
	buttonAction(id, on);
    }

    $(sel).mousedown(function() { set_state(true); });
    $(sel).bind('touchstart', function(e) {
	e.preventDefault();
        set_state(true);
    });
    $(sel).mouseup(function() { set_state(false); });
    $(sel).bind('touchend', function(e) {
	e.preventDefault();
        set_state(false);
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
        // clear any active keepalives just in case press triggered twice w/o release in between
	clearInterval(BUTTON_KEEPALIVES[id]);
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
	display: 'global_display',
	quiet: 'quiet_controls',
    }[param.category] || 'controls'));

    var $container = $('<div class="control" />');
    $section.append($container);

    PARAMS[param.name] = {param: param, e: $container};

    if (param.isAction || param.isMomentary) {
	var $button = $('<button />');
	$container.append($button);
	$button.text(param.description || param.name);
        if (param.isMomentary) {
	    bindPressReleaseButton($button, param.name);
        } else {
	    bindClickButton($button, param.name);
        }
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
	var relSens = (param.isInt ? 4. : null);
	PARAMS[param.name].$slider = bindSlider($slider, param.name, !param.isBounded, relSens);
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
        var numVal = +val.value;
	$e.find('#value').text(isNaN(numVal) ? val.value : numVal.toPrecision(3));
	if (param.isBounded) {
	    var k = val.sliderPos;
	    var sliderVal = SLIDER_MIN * (1 - k) + SLIDER_MAX * k;
	    P.$slider.slider('value', sliderVal);
	}
    }
}
