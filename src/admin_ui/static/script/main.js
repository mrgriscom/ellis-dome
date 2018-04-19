
function clock() {
    return new Date().getTime() / 1000.;
}


function init() {
    var that = this;

    this.conn = new WebSocket('ws://' + window.location.host + '/socket');
    this.conn.onopen = function () {
    };
    this.conn.onerror = function (error) {
        console.log('websocket error ' + error);
    };
    this.conn.onmessage = function (e) {
	console.log('receiving msg');
        var data = JSON.parse(e.data);
	console.log(data);
    };

    setInterval(function() {
	console.log('sending msg');
	that.conn.send(JSON.stringify({'a': 55}));
    }, 5000);
}
