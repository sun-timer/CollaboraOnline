/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { stripTypeScriptTypes } = require('node:module');
const { JSDOM } = require('jsdom');

const nameCommand = '.uno:CharFontName';
const sizeCommand = '.uno:FontHeight';

function harness(docType) {
	const dom = new JSDOM('<!doctype html><div id="document-container"></div>');
	const { window } = dom;
	const { document } = window;
	const commands = [];
	const state = { [nameCommand]: 'Liberation Serif', [sizeCommand]: '14 pt' };
	const listeners = new Map();
	const layer = { _selectedPart: 0 };
	const map = {
		_docLayer: layer,
		getDocType: () => docType,
		isEditMode: () => true,
		stateChangeHandler: { getItemValue: (command) => state[command] },
		sendUnoCommand: (command) => commands.push(command),
		on(types, listener) {
			for (const type of types.split(' ')) {
				if (!listeners.has(type)) listeners.set(type, new Set());
				listeners.get(type).add(listener);
			}
		},
		off(types, listener) {
			for (const type of types.split(' ')) listeners.get(type)?.delete(listener);
		},
		fire(type, message) {
			for (const listener of [...listeners.get(type) || []]) listener(message);
		},
	};
	window.app = { map, file: { textCursor: { rectangle: { height: 14 } } } };
	const context = vm.createContext({
		window, document, console,
		MobileDocTheme: { applyFromMap() {} },
		WriterEditorIcons: { get: () => '' },
		WriterCharPanelIcons: {},
	});
	for (const name of [
		'WriterEditorCatalog', 'ImpressEditorCatalog', 'WriterEditorController',
		'WriterEditorSheet', 'WriterEditorInlineSubpage', 'WriterEditorChooseDialog',
		'WriterEditorPanel', 'ImpressEditorPanel',
	]) {
		const source = fs.readFileSync(path.resolve(__dirname, '../src/app', name + '.ts'), 'utf8');
		vm.runInContext(stripTypeScriptTypes(source, { mode: 'transform' }), context);
	}
	window.ThisIsTheiOSApp = true;
	const panel = vm.runInContext(
		docType === 'text' ? 'WriterEditorPanel.mount()' : 'ImpressEditorPanel.mount()',
		context,
	);
	panel.open();
	const row = (feature) => document.querySelector(`.writer-function-picker-row[aria-label="${feature}"]`);
	const value = (feature) => row(feature).querySelector(
		'.writer-function-picker-row__value, .writer-function-value-box__text',
	).textContent;
	const open = (feature) => row(feature).click();
	const selected = () => document.querySelector('.writer-editor-option-row--selected')?.getAttribute('aria-label');
	const choose = async (label) => {
		document.querySelector(`.writer-editor-option-row[aria-label="${label}"]`).click();
		await new Promise((resolve) => window.setTimeout(resolve, 0));
	};
	const reportState = (commandName, value) => {
		state[commandName] = value;
		map.fire('commandstatechanged', { commandName, state: value });
	};
	return { window, document, map, layer, panel, commands, value, open, selected, choose, reportState,
		close() { panel.close(); dom.window.close(); } };
}

for (const docType of ['text', 'presentation']) {
	test(`${docType}: first font pick survives delayed and stale UNO state updates`, async () => {
		const h = harness(docType);
		try {
			assert.equal(h.value('字体'), 'Liberation Serif');
			h.open('字体');
			assert.equal(h.selected(), 'Liberation Serif');
			await h.choose('Arial');
			assert.equal(h.commands[0],
				'.uno:CharFontName {"CharFontName.FamilyName":{"type":"string","value":"Liberation Sans"}}');
			assert.equal(h.value('字体'), 'Arial');
			await new Promise((resolve) => setTimeout(resolve, 1050));
			h.reportState(sizeCommand, '16 pt');
			h.reportState(nameCommand, 'Liberation Serif');
			assert.equal(h.value('字体'), 'Arial');
			h.reportState(nameCommand, 'Liberation Sans');
			assert.equal(h.value('字体'), 'Arial');
			h.open('字体');
			assert.equal(h.selected(), 'Arial');
			await h.choose('Liberation Mono');
			assert.equal(h.value('字体'), 'Liberation Mono');
			h.map.fire('commandresult', { commandName: '.uno:Undo', success: true });
			assert.equal(h.value('字体'), 'Liberation Sans');
			h.open('字体');
			await h.choose('Arial');
			h.document.querySelector('#document-container').dispatchEvent(
				new h.window.Event('pointerdown', { bubbles: true }),
			);
			h.reportState(nameCommand, 'Liberation Serif');
			assert.equal(h.value('字体'), 'Liberation Serif');
		} finally {
			h.close();
		}
	});
}

test('Writer size keeps its picked label while the caret resizes and a prior pick arrives', async () => {
	const h = harness('text');
	try {
		assert.equal(h.value('字号'), '四号');
		h.open('字号');
		assert.equal(h.selected(), '四号');
		await h.choose('三号');
		assert.equal(h.value('字号'), '三号');
		h.open('字号');
		await h.choose('小二');
		assert.equal(h.commands[1], '.uno:FontHeight {"FontHeight.Height":{"type":"float","value":"18pt"}}');
		h.window.app.file.textCursor.rectangle.height = 18;
		h.reportState(sizeCommand, '16.0 pt');
		assert.equal(h.value('字号'), '小二');
		h.reportState(sizeCommand, 18);
		assert.equal(h.value('字号'), '小二');
		h.reportState(sizeCommand, '14pt');
		assert.equal(h.value('字号'), '四号');
		h.open('字号');
		await h.choose('小二');
		h.document.dispatchEvent(new h.window.KeyboardEvent('keydown', { key: 'ArrowRight' }));
		assert.equal(h.value('字号'), '四号');
	} finally {
		h.close();
	}
});

test('Impress font pick is scoped to the visible slide and open panel', async () => {
	const h = harness('presentation');
	try {
		h.open('字体');
		await h.choose('Arial');
		h.layer._selectedPart++;
		h.map.fire('updateparts');
		assert.equal(h.value('字体'), 'Liberation Serif');
		h.open('字体');
		await h.choose('Arial');
		h.panel.close();
		h.panel.open();
		assert.equal(h.value('字体'), 'Liberation Serif');
	} finally {
		h.close();
	}
});
