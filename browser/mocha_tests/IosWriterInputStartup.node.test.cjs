'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');
const ts = require('typescript');
const { JSDOM } = require('jsdom');
const inputSource = fs.readFileSync(__dirname + '/../src/layer/marker/TextInput.js', 'utf8');
const mapSource = fs.readFileSync(__dirname + '/../src/map/Map.js', 'utf8');

// Run the actual Map methods without booting the canvas/socket/UI subsystems.
function mapMethod(name, context) {
  const ast = ts.createSourceFile('Map.js', mapSource, ts.ScriptTarget.Latest, true, ts.ScriptKind.JS);
  let found;
  function visit(node) {
    if (ts.isPropertyAssignment(node) && node.name.getText(ast) === name && ts.isFunctionExpression(node.initializer)) found = node.initializer;
    ts.forEachChild(node, visit);
  }
  visit(ast);
  assert.ok(found, 'Map method exists: ' + name);
  return vm.runInContext('(' + found.getText(ast) + ')', context);
}
function startup({ type = 'text', ios = true, a11y = false } = {}) {
  const dom = new JSDOM('<body></body>');
  const window = dom.window, document = window.document;
  window.ThisIsTheiOSApp = ios;
  window.enableAccessibility = a11y;
  window.prefs = { getBoolean: () => a11y };
  window.postMobileMessage = () => {};
  const app = { layoutingService: { appendLayoutingTask() {} }, events: { on() {} }, console: { log() {} }, idleHandler: { notifyActive() {} } };
  window.app = app;
  window.L = {
    Browser: {}, DomEvent: { on() {}, off() {} },
    DomUtil: { create(tag, className, parent) { const el = document.createElement(tag); el.className = className; if (parent) parent.appendChild(el); return el; } },
    Layer: { prototype: { initialize() {} }, extend(methods) { function Input() { this.initialize(); } Input.prototype = methods; return Input; } },
  };
  const context = vm.createContext({ window, document, app, _: x => x, Node: window.Node, NodeFilter: window.NodeFilter });
  vm.runInContext(inputSource, context);
  window.L.a11yTextInput = () => { const input = new window.L.TextInput(); input.hasAccessibilitySupport = () => true; return input; };
  const map = {
    _docLayer: null, uiManager: { isUIBlocked: () => false }, _debug: { logKeyboardEvents: false },
    getDocType() { return this._docLayer ? this._docLayer._docType : null; },
    setupCoreAccessibility() {}, on() {}, getContainer: () => document.body,
    addLayer(input) { assert.equal(this._docLayer, null, 'input is attached before the document layer'); input._map = this; input.getPane = () => document.body; input.onAdd(); },
  };
  map.createTextInput = mapMethod('createTextInput', context);
  map.initTextInput = mapMethod('initTextInput', context);
  map.initTextInput(type);
  return { dom, window, map, input: map._textInput };
}
test('first Writer status initializes the consumable input before a document layer exists', () => {
  const h = startup();
  try {
    assert.equal(h.map.getDocType(), null);
    const input = h.input;
    assert.equal(input._textArea.firstChild.tagName, 'SPAN');
    assert.equal(input._iosBackspaceGuardBudget, 64);
    assert.equal(input.getValue(), '');
    assert.equal(input._getIOSBackspaceGuardNode().length, 64);
    h.map._docLayer = { _docType: 'text' };
    input._setCursorPosition(0);
    const removals = [];
    input._removeTextContent = (before, after) => removals.push([before, after]);
    for (let i = 0; i < 80; i++) {
      input._onBeforeInput(new h.window.InputEvent('beforeinput', { inputType: 'deleteContentBackward' }));
      const guard = input._getIOSBackspaceGuardNode();
      const sel = h.window.getSelection();
      const offset = sel.anchorNode === guard ? sel.anchorOffset : guard.length;
      guard.deleteData(offset - 1, 1);
      const range = h.window.document.createRange(); range.setStart(guard, offset - 1); range.collapse(true);
      sel.removeAllRanges(); sel.addRange(range);
      input._onInput(new h.window.InputEvent('input', { inputType: 'deleteContentBackward' }));
    }
    assert.equal(removals.length, 80);
    assert.ok(removals.every(([before, after]) => before === 1 && after === 0));
    assert.equal(input.getValue(), '');
  } finally { h.dom.window.close(); }
});
test('startup keeps Calc, Impress, desktop and accessibility on their original input path', () => {
  for (const options of [{ type: 'spreadsheet' }, { type: 'presentation' }, { ios: false }, { a11y: true }]) {
    const h = startup(options);
    try { assert.equal(h.input._textArea.firstChild.tagName, 'IMG', JSON.stringify(options)); }
    finally { h.dom.window.close(); }
  }
});
