'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const source = fs.readFileSync(path.join(__dirname, '../src/control/Permission.js'), 'utf8');
function harness(type = 'text', android = true) {
  const calls = [], timers = [];
  let methods;
  const window = { ThisIsTheAndroidApp: android, ThisIsTheiOSApp: !android,
    L: { Map: { include: value => { methods = value; } } },
    mode: { isMobile: () => false, isTablet: () => true },
    postMobileMessage: msg => calls.push(msg) };
  const app = {};
  vm.runInNewContext(source, { window, app, $: () => ({ hide() {} }),
    setTimeout: fn => timers.push(fn), console });
  const map = Object.assign({}, methods, { wopi: { BaseFileName: 'test.docx' }, options: {},
    _getFileExtension: () => 'docx', getDocType: () => type,
    _enterEditMode: () => calls.push('enter'), fire() {}, focus: accept => calls.push('focus:' + accept) });
  app.map = map;
  return { window, calls, timers, map };
}
test('browser Writer pencil requests keyboard without native-only pending flag', () => {
  const h = harness();
  h.map._proceedEditMode();
  assert.deepEqual(h.calls, ['enter', 'focus:false', 'REQUESTWRITEREDITIME']);
  assert.equal(h.timers.length, 0);
});
test('native Writer Edit follows the same single request after the final blur', () => {
  const h = harness(); h.window.__androidWriterEditKeyboardPending = true;
  h.map._proceedEditMode();
  assert.deepEqual(h.calls, ['enter', 'focus:false', 'REQUESTWRITEREDITIME']);
  assert.equal(h.timers.length, 0);
});
test('Calc and Impress keep keyboard hidden on edit entry', () => {
  for (const type of ['spreadsheet', 'presentation']) {
    const h = harness(type); h.map._proceedEditMode();
    assert.deepEqual(h.calls, ['enter', 'focus:false']);
    assert.equal(h.timers.length, 1); h.timers[0]();
    assert.deepEqual(h.calls, ['enter', 'focus:false', 'focus:false']);
  }
});
test('iOS permission entry does not receive Android keyboard messages', () => {
  const h = harness('text', false); h.map._proceedEditMode();
  assert.deepEqual(h.calls, ['enter', 'focus:false']);
  assert.equal(h.timers.length, 1);
});
