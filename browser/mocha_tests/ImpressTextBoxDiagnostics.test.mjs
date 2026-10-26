import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const sourcePath = new URL('../../ios/Mobile/DocumentViewController.mm', import.meta.url);
const source = await readFile(sourcePath, 'utf8');
const script = source.match(/R"IMPTEXTBOXDIAG\(([\s\S]*?)\)IMPTEXTBOXDIAG"/);
assert.ok(script, 'iOS text-box diagnostic injection should be present');

function setup({ debugThrows = false } = {}) {
	const records = [];
	const commands = [];
	const timers = new Map();
	const handlers = new Map();
	let nextTimer = 1;
	const events = {
		on(name, callback) {
			if (!handlers.has(name)) handlers.set(name, new Set());
			handlers.get(name).add(callback);
		},
		off(name, callback) { handlers.get(name)?.delete(callback); },
		fire(name, detail) {
			for (const callback of handlers.get(name) || []) callback({ detail });
		},
	};
	const transport = { readyState: 1, msgInflight: 2 };
	const socket = {
		socket: transport,
		sendMessage(message) { commands.push(['sendMessage', message]); this._doSend(message); },
		_enqueueMessageForReconnect(message) { commands.push(['queue', message]); },
		_doSend(message) { commands.push(['nativeSend', message]); },
	};
	const map = {
		_docLayer: { _docType: 'presentation' },
		uiManager: { isUIBlocked: () => false },
		isEditMode: () => true,
		sendUnoCommand(command) {
			commands.push(['sendUnoCommand', command]);
			socket.sendMessage('uno ' + command);
		},
	};
	const panel = {
		close() { commands.push(['close']); },
		onFeature(feature) {
			commands.push(['featureHandler', feature.id]);
			this.close();
			map.sendUnoCommand(feature.unocmd);
		},
	};
	const selection = {
		onMessage(message) { commands.push(['selection', message]); },
		hasActiveSelection: () => true,
	};
	const app = { map, socket, events, idleHandler: { _active: true }, definitions: { graphicSelection: selection } };
	const window = {
		app,
		__coolImpressEditorPanel: panel,
		postMobileDebug(message) {
			if (debugThrows) throw new Error('debug bridge unavailable');
			records.push(JSON.parse(message.slice('impress_textbox_diag '.length)));
		},
		setTimeout(callback, delay) {
			const id = nextTimer++;
			timers.set(id, { callback, delay });
			return id;
		},
		clearTimeout(id) { timers.delete(id); },
	};
	const context = vm.createContext({ window, app, Date, JSON, Object });
	vm.runInContext(script[1], context, { filename: 'IMPTEXTBOXDIAG.js' });
	return { records, commands, timers, handlers, events, panel, map, socket, selection, window };
}

const feature = { id: 'insert-textbox', unocmd: '.uno:Text?CreateDirectly:bool=true' };
{
	const h = setup();
	const originalClose = h.panel.close;
	const originalSendUno = h.map.sendUnoCommand;
	const originalSendMessage = h.socket.sendMessage;
	const originalSelection = h.selection.onMessage;
	h.panel.onFeature(feature);
	assert.deepEqual(h.commands.map((item) => item[0]), [
		'featureHandler', 'close', 'sendUnoCommand', 'sendMessage', 'nativeSend',
	]);
	const featureRecord = h.records.find((record) => record.stage === 'feature');
	assert.equal(featureRecord.state.socketReadyState, 1);
	assert.equal(featureRecord.state.inflight, 2);
	assert.ok(h.records.findIndex((r) => r.stage === 'panel_close_enter') < h.records.findIndex((r) => r.stage === 'sendUno_gate_enter'));
	assert.ok(h.records.findIndex((r) => r.stage === 'doSend_enter') < h.records.findIndex((r) => r.stage === 'sendMessage_result'));
	h.events.fire('TextCursorVisibility', { visible: true });
	h.selection.onMessage('graphicselection:private payload');
	assert.equal(h.records.find((record) => record.stage === 'text_cursor_visibility').visible, true);
	assert.equal(h.records.find((record) => record.stage === 'graphic_selection').hasSelection, true);
	assert.ok(!h.records.some((record) => JSON.stringify(record).includes('private payload')));
	// Re-enter before the original response window ends; finish must safely
	// restore the first operation before instrumenting the second one.
	assert.doesNotThrow(() => h.panel.onFeature(feature));
	assert.equal(h.timers.size, 1);
	[...h.timers.values()][0].callback();
	assert.equal(h.timers.size, 0);
	assert.equal(h.handlers.get('TextCursorVisibility').size, 0);
	assert.equal(h.panel.close, originalClose);
	assert.equal(h.map.sendUnoCommand, originalSendUno);
	assert.equal(h.socket.sendMessage, originalSendMessage);
	assert.equal(h.selection.onMessage, originalSelection);
	assert.equal(h.records.at(-1).stage, 'window_end');
	// A subsequent operation after cleanup must install fresh wrappers safely.
	assert.doesNotThrow(() => h.panel.onFeature(feature));
}

{
	const h = setup({ debugThrows: true });
	assert.doesNotThrow(() => h.panel.onFeature(feature));
	assert.deepEqual(h.commands.map((item) => item[0]), [
		'featureHandler', 'close', 'sendUnoCommand', 'sendMessage', 'nativeSend',
	]);
	assert.equal(h.timers.size, 1);
}

console.log('Impress text-box diagnostic tests passed');
