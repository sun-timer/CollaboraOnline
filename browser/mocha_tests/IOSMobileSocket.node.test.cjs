const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../js/global.js'), 'utf8');
const transport = source.slice(
	source.indexOf('global.proxySocketCounter = 0;'),
	source.indexOf('global.iterateCSSImages'),
);

function openSocket() {
	let now = 0;
	const requests = [];
	class Request {
		constructor() {
			this.listeners = {};
			requests.push(this);
		}
		open() {}
		send() {}
		addEventListener(event, listener) {
			this.listeners[event] = listener;
		}
		finish(status, responseText = '') {
			this.status = status;
			this.responseText = responseText;
			this.response = Uint8Array.of(0).buffer;
			this.listeners.load.call(this);
			this.listeners.loadend.call(this);
		}
	}
	const global = { app: { console: { debug() {} } } };
	const context = {
		global, window: { ThisIsTheiOSApp: true, ThisIsAMobileApp: true },
		performance: { now: () => now },
		XMLHttpRequest: Request, TextDecoder, clearInterval() {}, clearTimeout() {},
	};
	const socket = vm.runInNewContext(transport + '\nnew MobileSocket("/document")', context);
	socket.onopen = () => {};
	requests[0].finish(200, 'mobile');
	return { socket, requests, advanceTo: (time) => { now = time; } };
}

test('iOS reads after prolonged idle and drains notifications received during the request', () => {
	const { socket, requests, advanceTo } = openSocket();
	let delivered = 0;
	socket.parseIncomingArray = () => delivered++;
	advanceTo(31000);
	socket.doSend();
	advanceTo(31010);
	socket.doSend();
	assert.equal(socket.readyState, 1);
	assert.equal(requests.length, 2);
	requests[1].finish(200);
	assert.equal(requests.length, 3);
	requests[2].finish(200);
	assert.equal(delivered, 2);
	assert.equal(socket.msgInflight, 0);
});

test('iOS closes on an actual overdue read and ignores its late response', () => {
	const { socket, requests, advanceTo } = openSocket();
	let closes = 0;
	socket.onclose = () => closes++;
	socket.parseIncomingArray = () => assert.fail('a stale response must not reach the editor');
	advanceTo(31000);
	socket.doSend();
	advanceTo(51001);
	socket.doSend();
	assert.equal(socket.readyState, 3);
	assert.equal(closes, 1);
	requests[1].finish(200);
	assert.equal(socket.msgInflight, 0);
	assert.equal(requests.length, 2);
});
