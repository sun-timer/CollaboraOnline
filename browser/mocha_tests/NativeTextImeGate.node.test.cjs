'use strict';
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const assert = require('node:assert/strict'), test = require('node:test');
const java = fs.readFileSync(path.join(__dirname, '../../android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java'), 'utf8');
const method = java.slice(java.indexOf('private void showImeForConfirmedTextCursor('));
const start = method.indexOf('requestWebView.evaluateJavascript(');
const end = method.indexOf('result -> {', start);
const literals = method.slice(start, end).match(/"(?:\\.|[^"\\])*"/g);
const script = literals.map(s => JSON.parse(s)).join('').replace('isTextInputGenerationCurrent()', 'isTextInputGenerationCurrent(7)');
function run({ generation = 7, caret = true, edit = true, suppress = false } = {}) {
  const app = { map: { isEditMode: () => edit }, file: { textCursor: { visible: caret } } };
  const window = { app, AndroidSelectionMenu: { isTextInputGenerationCurrent: g => g === generation },
    __coolShouldSuppressDocumentKeyboard: () => suppress };
  return vm.runInNewContext(script, { app, window });
}
test('actual Java-generated IME gate is syntactically valid and accepts confirmed caret', () => {
  assert.doesNotThrow(() => new vm.Script(script));
  assert.equal(run(), true);
});
test('actual IME gate preserves stale, invisible, readonly and comment guards', () => {
  for (const args of [{ generation: 8 }, { caret: false }, { edit: false }, { suppress: true }])
    assert.equal(run(args), false);
});
