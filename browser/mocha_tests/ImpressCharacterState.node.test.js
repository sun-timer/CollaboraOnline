/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { stripTypeScriptTypes } = require('node:module');
const { JSDOM } = require('jsdom');
const commands = ['.uno:Bold', '.uno:Italic', '.uno:Underline', '.uno:Strikeout'];

function harness(surface = 'quick') {
	const dom = new JSDOM('<!doctype html><body></body>');
	const { window } = dom;
	const messages = [], events = {};
	const selection = { id: '100', active: true, text: false, session: 'deck-1' };
	const core = Object.fromEntries(commands.map(command => [command, 'false']));
	const map = {
		getDocType: () => 'presentation', focus() {},
		_docLayer: { _viewId: 0, _selectedPart: 1 },
		stateChangeHandler: { getItemValue: command => core[command] },
		on(name, listener) { (events[name] ||= new Set()).add(listener); },
		off(name, listener) { events[name]?.delete(listener); },
		fire(name, detail) { for (const listener of [...events[name] || []]) listener(detail); },
	};
	window.ThisIsTheiOSApp = true;
	window.app = { map, file: { textCursor: { visible: false } } };
	window.postMobileMessage = message => messages.push(message);
	const context = vm.createContext({ window, document: window.document, console,
		MobileDocTheme: { applyFromMap() {} },
		WriterCharPanelIcons: { bold: '<svg/>', italic: '<svg/>' },
		CalcEditorFormat: { isActive: () => false },
		GraphicSelection: { hasActiveSelection: () => selection.active,
			get extraInfo() { return { id: selection.id }; } },
		TextSelections: { isActive: () => selection.text },
		NativeBridge: { getInstance: () => ({ getDocumentSessionId: () => selection.session }) },
	});
	for (const name of ['WriterEditorController', 'WriterQuickActionBar', 'ImpressEditorPanel'])
		vm.runInContext(stripTypeScriptTypes(fs.readFileSync(path.resolve(__dirname, '../src/app', name + '.ts'), 'utf8')), context);
	vm.runInContext('window.Bar = WriterQuickActionBar; window.Panel = ImpressEditorPanel; window.Controller = WriterEditorController;', context);
	let bar;
	if (surface === 'quick') {
		bar = new window.Bar();
		bar.setActions(commands.map(command => ({ label: command, command, iconHtml: '', onTap() {} })));
	} else {
		bar = Object.create(window.Panel.prototype);
		Object.assign(bar, { grid: window.document.createElement('div'), activeTab: 'default',
			subDialogs: [], slideBackgroundRequest: 0, sheet: { open() {}, close() {} },
			inlineSubpage: { clearSubpages() {} }, refreshCommandState() {}, renderTabs() {},
			renderGrid() {
				this.grid.replaceChildren(this.createCharToolGrid(commands.map(command => ({
					id: 'char-' + command.slice(5).toLowerCase(), label: command, unocmd: command,
					kind: 'charTool', group: 'char-tools-1',
				}))));
			},
		});
		window.document.body.appendChild(bar.grid);
	}
	const request = () => {
		const message = messages.pop();
		assert.ok(message?.startsWith('IMPRESS_SHAPE_FORMAT_STATE '), message);
		return JSON.parse(message.substring('IMPRESS_SHAPE_FORMAT_STATE '.length));
	};
	const reply = (payload, values = commands.map(() => 'true')) => window.ImpressCharacterStateBridge.onResult({
		...payload, success: true, states: Object.fromEntries(commands.map((command, index) => [command, values[index]])),
	});
	const active = () => [...window.document.querySelectorAll('button')].map(button =>
		button.classList.contains(surface === 'quick' ? 'writer-quick-action-bar__button--active' :
			'writer-function-char-tools__btn--active'));
	return { window, map, bar, selection, core, messages, request, reply, active,
		close() { bar.close(); dom.window.close(); } };
}

for (const surface of ['quick', 'common']) {
test(surface + ': a newly opened document hydrates all four highlights from persisted shape state', () => {
	const h = harness(surface);
	try {
		h.bar.open();
		assert.deepEqual(h.active(), [false, false, false, false]);
		const payload = h.request();
		assert.equal(payload.selectionId, '100');
		h.reply(payload);
		assert.deepEqual(h.active(), [true, true, true, true]);
		assert.equal(h.messages.length, 0, 'a state query must not issue a formatting command');
	} finally { h.close(); }
});

test(surface + ': disabled/default draw-shell state cannot erase verified highlights on the same box', () => {
	const h = harness(surface);
	try {
		h.bar.open(); h.reply(h.request());
		for (const command of commands) {
			h.core[command] = 'disabled';
			h.map.fire('commandstatechanged', { commandName: command, state: 'disabled' });
		}
		assert.deepEqual(h.active(), [true, true, true, true]);
		h.reply(h.request(), ['false', 'true', 'true', 'true']);
		assert.deepEqual(h.active(), [false, true, true, true], 'a real model change still updates the buttons');
	} finally { h.close(); }
});

test(surface + ': switching boxes rejects the previous request and does not reuse the previous highlights', () => {
	const h = harness(surface);
	try {
		h.bar.open(); const old = h.request();
		h.selection.id = '200'; h.map.fire('impresscharacterselectionchanged');
		const current = h.request();
		h.reply(old);
		assert.deepEqual(h.active(), [false, false, false, false]);
		h.reply(current, ['false', 'true', 'false', 'true']);
		assert.deepEqual(h.active(), [false, true, false, true]);
	} finally { h.close(); }
});

test(surface + ': closing/reopening a document creates a fresh scoped query instead of retaining old button state', () => {
	const h = harness(surface);
	try {
		h.bar.open(); h.reply(h.request()); h.bar.close();
		h.selection.session = 'deck-2';
		h.map._docLayer = { ...h.map._docLayer };
		h.bar.open();
		assert.deepEqual(h.active(), [false, false, false, false]);
		h.reply(h.request(), ['true', 'false', 'true', 'false']);
		assert.deepEqual(h.active(), [true, false, true, false]);
	} finally { h.close(); }
});

test(surface + ': text editing uses cursor/range state and stale whole-box results cannot override it', () => {
	const h = harness(surface);
	try {
		h.bar.open(); const old = h.request();
		h.selection.text = true;
		h.core['.uno:Bold'] = 'true';
		h.map.fire('commandstatechanged', { commandName: '.uno:Bold', state: 'true' });
		h.reply(old);
		assert.deepEqual(h.active(), [true, false, false, false]);
		assert.equal(h.messages.length, 0);
	} finally { h.close(); }
});

test(surface + ': an old state read cannot overwrite a newer successful format operation', () => {
	const h = harness(surface);
	try {
		h.bar.open(); const old = h.request();
		h.window.ImpressCharacterStateBridge.onFormatResult({ ...old, command: '.uno:Bold', success: true, enabled: true });
		assert.equal(h.active()[0], true);
		const fresh = h.request();
		h.reply(old, commands.map(() => 'false'));
		assert.equal(h.active()[0], true);
		h.reply(fresh, ['true', 'mixed', 'false', 'true']);
		assert.deepEqual(h.active(), [true, false, false, true]);
	} finally { h.close(); }
});
}

test('opening the common panel reuses the quick bar snapshot and keeps both surfaces synchronized', () => {
	const h = harness('common');
	const quick = new h.window.Bar();
	quick.setActions(commands.map(command => ({ label: command, command, iconHtml: '', onTap() {} })));
	try {
		quick.open(); h.reply(h.request());
		quick.close(); h.bar.open();
		assert.deepEqual(h.active(), [true, true, true, true]);
		h.reply(h.request(), ['false', 'true', 'false', 'true']);
		assert.deepEqual(h.active(), [false, true, false, true]);
		h.bar.renderGrid();
		assert.deepEqual(h.active(), [false, true, false, true], 'rebuilding the common tab keeps verified state');
		h.bar.close();
		h.core['.uno:Bold'] = 'true'; h.selection.text = true;
		h.map.fire('commandstatechanged', { commandName: '.uno:Bold', state: 'true' });
		assert.deepEqual(h.active(), [false, true, false, true], 'a closed panel has no live subscriptions');
		h.bar.open();
		assert.deepEqual(h.active(), [true, false, false, false]);
		assert.equal(h.messages.length, 0, 'opening during text editing must not query or format a whole box');
	} finally { quick.close(); h.close(); }
});
