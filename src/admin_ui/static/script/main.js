
function clock() {
    return new Date().getTime() / 1000.;
}

function AdminUIModel() {
    this.playlists = ko.observableArray();
    this.contents = ko.observableArray();
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
}

function connectionLost() {
    alert('connection to server lost; reload the page');
}
