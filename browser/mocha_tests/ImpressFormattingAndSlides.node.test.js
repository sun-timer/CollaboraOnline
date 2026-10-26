/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
// Node 24+ runs source with native type stripping; no browser build required.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { stripTypeScriptTypes } = require('node:module');
const { JSDOM } = require('jsdom');

function formattingHarness() {
	const dom = new JSDOM('<!doctype html><body></body>');
	const { window } = dom;
	const states = {};
	const commands = [];
	const messages = [];
	const selection = { graphic: true, text: false };
	window.postMobileMessage = message => messages.push(message);
	window.app = { file: { textCursor: { visible: false } }, map: {
		getDocType: () => 'presentation',
		_docLayer: { _viewId: 0, _selectedPart: 0 },
		stateChangeHandler: { getItemValue: command => states[command] },
		sendUnoCommand: command => commands.push(command),
	} };
	const context = vm.createContext({ window, document: window.document,
		WriterCharPanelIcons: { bold: '<svg/>', italic: '<svg/>' }, console,
		GraphicSelection: { hasActiveSelection: () => selection.graphic, extraInfo: { id: '12345' } },
		TextSelections: { isActive: () => selection.text },
		NativeBridge: { getInstance: () => ({ getDocumentSessionId: () => 'ppt-session' }) } });
	for (const name of ['WriterEditorController', 'ImpressEditorPanel']) {
		const source = fs.readFileSync(path.resolve(__dirname, '../src/app', name + '.ts'), 'utf8');
		vm.runInContext(stripTypeScriptTypes(source), context);
	}
	vm.runInContext('window.Panel = ImpressEditorPanel; window.Controller = WriterEditorController;', context);
	window.ThisIsTheiOSApp = true;
	const panel = Object.create(window.Panel.prototype);
	panel.grid = window.document.createElement('div');
	window.document.body.appendChild(panel.grid);
	const features = ['Bold', 'Italic', 'Underline', 'Strikeout'].map(name => ({
		id: 'char-' + name.toLowerCase(), label: name, kind: 'charTool',
		tab: 'default', group: 'char-tools-1', unocmd: '.uno:' + name,
	}));
	panel.grid.appendChild(panel.createCharToolGrid(features));
	return { window, panel, commands, states, messages, selection, close: () => dom.window.close() };
}

test('both PPT entry points route a selected text box through the native formatter', () => {
 const h = formattingHarness();
 try {
  const controller = new h.window.Controller();
  for (const name of ['Bold', 'Italic', 'Underline', 'Strikeout']) {
   controller.run({ id: 'char-quick', kind: 'command', unocmd: '.uno:' + name });
   const button = h.panel.grid.querySelector('[aria-label="' + name + '"]');
   if (button) button.click();
   for (const message of h.messages.splice(0)) {
    assert.ok(message.startsWith('IMPRESS_SHAPE_FORMAT '));
    const body = JSON.parse(message.substring('IMPRESS_SHAPE_FORMAT '.length));
    assert.equal(body.action, undefined);
    assert.deepEqual(body, {
     command: '.uno:' + name, selectionId: '12345',
     viewId: 0, part: 0, documentSessionId: 'ppt-session',
    });
   }
  }
  assert.equal(h.commands.length, 0);
 } finally { h.close(); }
});

test('a text caret still uses the native formatter, without a shape id', () => {
 const h = formattingHarness();
 try {
  for (const textSelected of [false, true]) {
   h.selection.text = textSelected;
   h.window.app.file.textCursor.visible = !textSelected;
   h.panel.grid.querySelector('[aria-label="Bold"]').click();
   const message = h.messages.pop();
   assert.equal(h.commands.length, 0);
   assert.ok(message.startsWith('IMPRESS_SHAPE_FORMAT '));
   assert.equal(JSON.parse(message.substring('IMPRESS_SHAPE_FORMAT '.length)).selectionId, '');
  }
 } finally { h.close(); }
});

test('common-panel character buttons retain their original appearance', () => {
 const h = formattingHarness();
 try {
  h.states['.uno:Bold'] = 'true';
  const button = h.panel.grid.querySelector('[aria-label="Bold"]');
  button.click();
  assert.equal(button.className, 'writer-function-char-tools__btn');
  assert.equal(button.getAttribute('aria-pressed'), null);
 } finally { h.close(); }
});

test('Writer, Calc, Android, and unrelated commands retain their dispatch', () => {
 const h = formattingHarness();
 try {
  for (const [ios, docType, command] of [
   [false, 'presentation', '.uno:Bold'], [true, 'text', '.uno:Bold'],
   [true, 'spreadsheet', '.uno:Italic'], [true, 'presentation', '.uno:LeftPara'],
  ]) {
   h.window.ThisIsTheiOSApp = ios;
   h.window.app.map.getDocType = () => docType;
   assert.equal(h.window.Controller.applyImpressShapeCharacter(command), false);
   assert.equal(h.messages.length, 0);
  }
 } finally { h.close(); }
});

function slideHarness(android = false) {
	const dom = new JSDOM('<!doctype html><head><meta name="previewSmile" content=""></head><body><div id="sorter"><div></div></div></body>');
	const { window } = dom;
	const messages = [];
	const actions = [];
	const map = {
		on() {}, off() {},
		setPart: part => actions.push(['setPart', part]),
		selectPart: (...args) => actions.push(['selectPart', ...args]),
		duplicatePage: () => actions.push(['copy']), deletePage: () => actions.push(['delete']),
		_permission: 'edit', isEditMode: () => true, getDocType: () => 'presentation',
		_docLayer: { _selectedPart: 1, _parts: 3 },
		stateChangeHandler: { getItemValue: () => 'false' },
		_clip: { clearSelection() {}, setTextSelectionType() {} },
		getPreview: () => ({ width: 98, height: 55 }),
	};
	const app = { map, file: { fileBasedView: false }, impress: { hasOverviewPage: false }, isReadOnly: () => false };
	window.ThisIsTheiOSApp = !android;
	window.ThisIsTheAndroidApp = android;
	window.mode = { isDesktop: () => false, isTablet: () => false };
	window.postMobileMessage = message => messages.push(message);
	window.L = {
		Control: { extend: prototype => prototype }, control: { attachTooltipEventListener() {} },
		DomUtil: {
			create(tag, className, parent) { const el = window.document.createElement(tag); el.className = className; parent.appendChild(el); return el; },
			setStyle(el, name, value) { el.style[name] = value; },
		},
		DomEvent: {
			on(el, type, fn, owner) { el.addEventListener(type, owner ? fn.bind(owner) : fn); },
			preventDefault(event) { event.preventDefault(); }, stopPropagation(event) { event.stopPropagation(); },
			stop(event) { event.preventDefault(); event.stopPropagation(); },
		},
	};
	function Hammer() { this.on = () => this; }
	const context = vm.createContext({ window, document: window.document, app, Hammer, _: value => value, console });
	vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../src/control/Control.PartsPreview.js'), 'utf8'), context);
	const preview = Object.create(window.L.Control.PartsPreview);
	Object.assign(preview, { _map: map, _idNum: 0, _partsPreviewCont: window.document.querySelector('#sorter'),
		_addDnDHandlers() {}, _bindAndroidLongPressDnD() {},
		_setPart() { map._docLayer._selectedPart = 0; },
	});
	const img = preview._createPreview(0, 'hash');
	return { window, app, map, preview, img, messages, actions, close: () => dom.window.close() };
}

test('iOS first tap selects the slide; second tap shows the custom two-row menu', () => {
 const h = slideHarness();
 try {
  h.img.click();
  assert.equal(h.map._docLayer._selectedPart, 0);
  assert.equal(h.window.document.querySelector('[role="menu"]'), null);
  h.img.click();
  const rows = Array.from(h.window.document.querySelectorAll('[role="menuitem"]'));
  assert.deepEqual(rows.map(b => b.getAttribute('aria-label')), ['复制', '删除']);
  assert.equal(h.messages.length, 0);
  rows[0].click();
  assert.deepEqual(h.actions, [['setPart', 0], ['selectPart', 0, 1, false], ['copy']]);
  assert.equal(h.window.document.querySelector('.ios-impress-slide-overlay'), null);
 } finally { h.close(); }
});

test('Android keeps its existing native menu payload', () => {
 const h = slideHarness(true);
 try {
  h.img.click(); h.img.click();
  assert.equal(h.messages.length, 1);
  const payload = JSON.parse(h.messages[0].substring('SLIDE_THUMBNAIL_POPUP show '.length));
  assert.equal(payload.partIndex, 0);
  assert.equal(payload.canCopy, true);
  assert.equal(payload.canDelete, true);
  assert.equal(h.window.document.querySelector('[role="menu"]'), null);
 } finally { h.close(); }
});

test('iOS slide buttons escape the shared dialog button chrome', () => {
 const h = slideHarness();
 try {
  const css = fs.readFileSync(path.resolve(__dirname, '../css/btns.css'), 'utf8');
  const selector = css.slice(css.indexOf(':not(.main-nav) .button-secondary'), css.indexOf('{', css.indexOf(':not(.main-nav) .button-secondary'))).trim();
  // A normal dialog button must still match; only our custom controls opt out.
  const container = h.window.document.createElement('div');
  const ordinary = h.window.document.createElement('button');
  container.appendChild(ordinary);
  h.window.document.body.appendChild(container);
  assert.equal(ordinary.matches(selector), true);
  h.preview._showIOSSlidePopup(h.img);
  const menuButtons = Array.from(h.window.document.querySelectorAll('[role="menuitem"]'));
  assert.equal(menuButtons.length, 2);
  for (const button of menuButtons) assert.equal(button.matches(selector), false);
  h.preview._showIOSSlideDeleteConfirm(0, h.map._docLayer);
  const confirmButtons = Array.from(h.window.document.querySelectorAll('[role="alertdialog"] button'));
  assert.equal(confirmButtons.length, 3);
  for (const button of confirmButtons) assert.equal(button.matches(selector), false);
 } finally { h.close(); }
});

test('deletion requires confirmation and never uses native view-controller dismissal', () => {
 const h = slideHarness();
 try {
  for (const action of ['close', 'cancel', 'confirm']) {
   h.preview._showIOSSlidePopup(h.img);
   h.window.document.querySelector('[aria-label="删除"]').click();
   assert.equal(h.actions.length, 0);
   const buttons = h.window.document.querySelectorAll('.ios-impress-slide-confirm__actions button');
   if (action === 'close') h.window.document.querySelector('[aria-label="关闭"]').click();
   else buttons[action === 'confirm' ? 1 : 0].click();
   if (action === 'confirm') assert.deepEqual(h.actions, [['setPart', 0], ['selectPart', 0, 1, false], ['delete']]);
   else assert.equal(h.actions.length, 0);
   assert.equal(h.messages.length, 0);
  }
 } finally { h.close(); }
});

test('slide popup protects the last slide, overview slide, read-only and master views', () => {
 const h = slideHarness();
 try {
  h.map._docLayer._parts = 1;
  h.preview._showIOSSlidePopup(h.img);
  assert.equal(h.window.document.querySelector('[aria-label="删除"]').disabled, true);
  h.preview._hideIOSSlidePopup();
  h.app.impress.hasOverviewPage = true;
  h.preview._showIOSSlidePopup(h.img);
  assert.equal(h.window.document.querySelector('[aria-label="复制"]').disabled, true);
  assert.equal(h.window.document.querySelector('[aria-label="删除"]').disabled, true);
  h.preview._hideIOSSlidePopup();
  h.app.isReadOnly = () => true;
  h.preview._showIOSSlidePopup(h.img);
  assert.equal(h.window.document.querySelector('[role="menu"]'), null);
  h.app.isReadOnly = () => false;
  h.map.stateChangeHandler.getItemValue = () => 'true';
  h.preview._showIOSSlidePopup(h.img);
  assert.equal(h.window.document.querySelector('[role="menu"]'), null);
 } finally { h.close(); }
});

test('stale selection and a slide-count change cannot trigger an invalid delete', () => {
 const h = slideHarness();
 try {
  const oldLayer = h.map._docLayer;
  h.map._docLayer = { ...oldLayer };
  h.preview._runIOSSlideAction(0, true, oldLayer);
  assert.equal(h.actions.length, 0);
  h.map._docLayer = oldLayer;
  oldLayer._parts = 1;
  h.preview._runIOSSlideAction(0, true, oldLayer);
  assert.equal(h.actions.length, 0);
 } finally { h.close(); }
});
