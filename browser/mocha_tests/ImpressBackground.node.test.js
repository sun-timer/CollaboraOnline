/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { stripTypeScriptTypes } = require('node:module');
const { JSDOM } = require('jsdom');

function harness() {
	const dom = new JSDOM('<!doctype html><body></body>');
	const { window } = dom;
	const commands = [], messages = [], rectangles = [];
	const map = {
		getDocType: () => 'presentation', isEditMode: () => true,
		_docLayer: { _viewId: 7, _selectedPart: 2, _partWidthTwips: 16000, _partHeightTwips: 9000 },
		sendUnoCommand: command => commands.push(command),
	};
	window.app = { map };
	window.postMobileMessage = message => messages.push(message);
	window.HTMLCanvasElement.prototype.getContext = () => ({ fillRect: (...rect) => rectangles.push(rect) });
	window.HTMLCanvasElement.prototype.toDataURL = () => 'data:image/png;base64,cGF0dGVybg==';
	window.NativeBridge = { getDocumentSessionId: () => 'deck-1' };
	const timers = new Map();
	let nextTimer = 1;
	window.setTimeout = (callback) => {
		const id = nextTimer++;
		timers.set(id, callback);
		return id;
	};
	window.clearTimeout = (id) => timers.delete(id);
	const context = vm.createContext({ window, document: window.document, NativeBridge: window.NativeBridge });
	for (const name of ['ImpressEditorCatalog', 'ImpressBackgroundBridge']) {
		vm.runInContext(stripTypeScriptTypes(fs.readFileSync(path.resolve(__dirname, '../src/app', name + '.ts'), 'utf8')), context);
	}
	return { window, map, commands, messages, rectangles, bridge: window.ImpressBackgroundBridge, close: () => dom.window.close() };
}

test('solid fill uses the formal FillColor parameter and does not send a style-only command', () => {
	const h = harness();
	try {
		h.bridge.apply('color', 0x12abcd);
		assert.equal(h.commands.length, 1);
		assert.equal(h.commands[0], '.uno:FillPageColor {"FillColor.Color":{"type":"long","value":1223629}}');
	} finally { h.close(); }
});

test('gradient and hatch supply real fill data with Core member names', () => {
	const h = harness();
	try {
		h.bridge.apply('gradient'); h.bridge.apply('hatch');
		const gradient = JSON.parse(h.commands[0].substring('.uno:FillPageGradient '.length));
		const colors = JSON.parse(gradient.FillPageGradientJSON.value);
		assert.notEqual(colors.startcolor, colors.endcolor);
		assert.equal(colors.style, 'LINEAR');
		const hatch = JSON.parse(h.commands[1].substring('.uno:FillPageHatch '.length));
		assert.equal(hatch['FillPageHatch.Distance'].value, 200);
		assert.equal(hatch['FillPageHatch.Angle'].value, 450);
		assert.equal(hatch['FillPageHatch.Style'].type, 'short');
		assert.equal(h.messages.length, 0);
	} finally { h.close(); }
});

test('none and inherited background use one native reset, including after a solid color', () => {
	const h = harness();
	try {
		for (const kind of ['none', 'master', 'master', 'none']) {
			h.bridge.apply('color', 0xff0000);
			h.commands.length = 0;
			h.bridge.apply(kind);
			assert.equal(h.commands.length, 0, 'no independent UNO reset can race the native edit');
			const payload = JSON.parse(h.messages.pop().substring('IMPRESS_MASTER_BACKGROUND '.length));
			assert.equal(typeof payload.requestId, 'string');
			assert.equal(payload.viewId, 7);
			assert.equal(payload.part, 2);
			assert.equal(payload.visible, kind === 'master');
			assert.equal(payload.documentSessionId, 'deck-1');
		}
	} finally { h.close(); }
});

test('bitmap picker imports a page background, never a foreground image or a native Core dialog', () => {
	const h = harness();
	try {
		h.bridge.apply('bitmap');
		const token = h.messages.pop().substring('IMPRESS_BACKGROUND_PICK_IMAGE '.length);
		assert.equal(h.commands.length, 0);
		h.bridge.completeImagePicker(token, 'aW1hZ2U=');
		assert.equal(h.messages.pop(), 'insertfile name=slide_background.png type=selectbackground data=aW1hZ2U=');
		h.bridge.completeImagePicker(token, 'aW1hZ2U=');
		assert.equal(h.messages.length, 0);
	} finally { h.close(); }
});

test('cancel, replacement background, changed slide, replaced document and read-only reject pending images', () => {
	for (const scenario of ['cancel', 'choice', 'slide', 'document', 'readonly', 'wrong-token']) {
		const h = harness();
		try {
			h.bridge.apply('bitmap');
			let token = h.messages.pop().substring('IMPRESS_BACKGROUND_PICK_IMAGE '.length);
			let data = 'aW1hZ2U=';
			if (scenario === 'cancel') data = '';
			if (scenario === 'choice') h.bridge.apply('gradient');
			if (scenario === 'slide') h.map._docLayer._selectedPart++;
			if (scenario === 'document') h.map._docLayer = { ...h.map._docLayer };
			if (scenario === 'readonly') h.map.isEditMode = () => false;
			if (scenario === 'wrong-token') token += '-stale';
			h.bridge.completeImagePicker(token, data);
			assert.equal(h.messages.length, 0, scenario);
		} finally { h.close(); }
	}
});

test('pattern paints actual repeated content and imports through the page background channel', () => {
	const h = harness();
	try {
		h.bridge.apply('pattern');
		assert.ok(h.rectangles.length > 100);
		assert.deepEqual(h.rectangles[0], [0, 0, 1024, 576]);
		assert.equal(h.messages.pop(), 'insertfile name=slide_background.png type=selectbackground data=cGF0dGVybg==');
		assert.equal(h.commands.length, 0);
	} finally { h.close(); }
});

test('non-Impress and read-only documents cannot receive background commands', () => {
	const h = harness();
	try {
		h.map.getDocType = () => 'text'; h.bridge.apply('gradient');
		h.map.getDocType = () => 'presentation'; h.map.isEditMode = () => false; h.bridge.apply('bitmap');
		assert.equal(h.commands.length, 0); assert.equal(h.messages.length, 0);
	} finally { h.close(); }
});
