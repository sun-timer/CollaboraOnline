/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
// Run directly with Node's native TypeScript stripping; this does not build the browser bundle.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { stripTypeScriptTypes } = require('node:module');

function harness(options = {}) {
	const commands = [];
	const sentMessages = [];
	const mobileMessages = [];
	const timers = new Map();
	let nextTimer = 1;
	const map = {
		getDocType: () => options.docType || 'presentation',
		isEditMode: () => options.editMode !== false,
		_docLayer: { _viewId: 3, _selectedPart: 1, _partWidthTwips: 16000, _partHeightTwips: 9000 },
	};
	if (options.withSendUno !== false) {
		map.sendUnoCommand = (command) => commands.push(command);
	}
	const window = {
		app: { map, socket: { sendMessage: (message) => sentMessages.push(message) } },
		postMobileMessage: (message) => mobileMessages.push(message),
		// NativeBridge is the live instance installed at window.NativeBridge.
		NativeBridge: { getDocumentSessionId: () => options.session || 'session' },
		setTimeout: (callback) => { const id = nextTimer++; timers.set(id, callback); return id; },
		clearTimeout: (id) => timers.delete(id),
	};
	const context = vm.createContext({ window, console });
	const source = fs.readFileSync(path.resolve(__dirname, '../src/app/ImpressBackgroundBridge.ts'), 'utf8');
	vm.runInContext(stripTypeScriptTypes(source), context);
	vm.runInContext('window.Bridge = ImpressBackgroundBridge;', context);
	return { window, map, commands, sentMessages, mobileMessages, timers, Bridge: window.Bridge };
}

test('gradient and hatch use their own page commands', () => {
	const h = harness();
	assert.equal(h.Bridge.apply('gradient'), true);
	assert.equal(h.Bridge.apply('hatch'), true);
	assert.equal(h.commands.length, 2);
	assert.ok(h.commands[0].startsWith('.uno:FillPageGradient '));
	assert.ok(h.commands[1].startsWith('.uno:FillPageHatch '));
});

test('falls back to the socket when the map has no sendUnoCommand', () => {
	const h = harness({ withSendUno: false });
	assert.equal(h.Bridge.apply('hatch'), true);
	assert.equal(h.sentMessages.length, 1);
	assert.ok(h.sentMessages[0].startsWith('uno .uno:FillPageHatch '));
});

test('refuses a non presentation document', () => {
	const h = harness({ docType: 'text' });
	assert.equal(h.Bridge.apply('gradient'), false);
	assert.deepEqual(h.commands, []);
});

test('color sends FillColor.Color and rejects an invalid rgb', () => {
	const h = harness();
	let reported;
	assert.equal(h.Bridge.applyColor(0x123456, (sent) => { reported = sent; }), true);
	assert.equal(reported, true);
	assert.deepEqual(h.commands, [
		'.uno:FillPageColor {"FillColor.Color":{"type":"long","value":1193046}}',
	]);
	assert.equal(h.Bridge.applyColor(-1), false);
	assert.equal(h.Bridge.applyColor(0x1000000), false);
	assert.equal(h.commands.length, 1);
});

test('none and master background are one native call', () => {
	const h = harness();
	let noneResult;
	let masterResult;
	assert.equal(h.Bridge.apply('none', undefined, (result) => { noneResult = result; }), true);
	assert.equal(h.Bridge.apply('master', undefined, (result) => { masterResult = result; }), true);
	assert.equal(h.mobileMessages.length, 2);
	const none = JSON.parse(h.mobileMessages[0].slice('IMPRESS_MASTER_BACKGROUND '.length));
	const master = JSON.parse(h.mobileMessages[1].slice('IMPRESS_MASTER_BACKGROUND '.length));
	assert.equal(none.visible, false);
	assert.equal(master.visible, true);
	assert.equal(none.documentSessionId, 'session');
	assert.equal(noneResult, undefined);
	h.Bridge.onNativeBackgroundResult({ ...none, success: true });
	assert.equal(noneResult, true);
	h.Bridge.onNativeBackgroundResult({ ...master, success: false });
	assert.equal(masterResult, false);
});

test('reads the installed NativeBridge instance session and rejects a changed session', () => {
	const h = harness();
	let applied;
	h.Bridge.apply('none', undefined, (result) => { applied = result; });
	const request = JSON.parse(h.mobileMessages[0].slice('IMPRESS_MASTER_BACKGROUND '.length));
	h.window.NativeBridge.getDocumentSessionId = () => 'new-session';
	h.Bridge.onNativeBackgroundResult({ ...request, success: true });
	assert.equal(applied, false);
});

test('native background acknowledgements reject stale document context and time out', () => {
	const h = harness();
	let result;
	h.Bridge.apply('none', undefined, (applied) => { result = applied; });
	const request = JSON.parse(h.mobileMessages[0].slice('IMPRESS_MASTER_BACKGROUND '.length));
	h.map._docLayer._selectedPart = 2;
	h.Bridge.onNativeBackgroundResult({ ...request, success: true });
	assert.equal(result, false);
	assert.equal(h.timers.size, 0);
	let timedOut;
	h.map._docLayer._selectedPart = 1;
	h.Bridge.apply('master', undefined, (applied) => { timedOut = applied; });
	const timer = [...h.timers.values()][0];
	timer();
	assert.equal(timedOut, false);
});

test('bitmap asks the native photo picker', () => {
	const h = harness();
	assert.equal(h.Bridge.apply('bitmap'), true);
	assert.ok(h.mobileMessages[0].startsWith('IMPRESS_BACKGROUND_PICK_IMAGE background-'));
});
