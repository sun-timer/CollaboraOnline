import assert from 'node:assert/strict';

globalThis.app = { definitions: {} };
const debugLines = [];
globalThis.window = {
	app: { console: { debug: (line) => debugLines.push(line) } },
	errorMessages: {
		websocketproxyfailure: 'proxy',
		websocketgenericfailure: 'generic',
		storage: { loadfailed: 'load failed' },
	},
};

await import('../src/app/Socket.ts');
const Socket = app.definitions.Socket;

function makeSocket() {
	const events = [];
	const instance = Object.create(Socket.prototype);
	instance._map = { _docLoadedOnce: false, fire: (...args) => events.push(args) };
	instance.ReconnectCount = 0;
	instance.parseServerCmd = () => ({ errorCmd: 'internal', errorKind: 'unauthorized' });
	instance._buildUnauthorizedMessage = () => 'unauthorized';
	return { instance, events };
}

{
	const { instance, events } = makeSocket();
	instance._onSocketClose();
	assert.match(debugLines.at(-1), /code=1006 reason= wasClean=false/);
	assert.equal(events[1][1].args.errorType, 'websocketproxyfailure');
}

{
	const { instance, events } = makeSocket();
	instance._onSocketClose({ code: 4001, reason: 'error:unauthorized', wasClean: true });
	assert.match(debugLines.at(-1), /code=4001 reason=error:unauthorized wasClean=true/);
	assert.equal(events[1][1].args.errorType, 'websocketunauthorized');
}

console.log('Socket close event tests passed');
