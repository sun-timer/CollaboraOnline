'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { JSDOM } = require('jsdom');

function harness() {
  const dom = new JSDOM('<div id="clipboard-area" contenteditable="true"><span id="pre-space" aria-hidden="true">' + '\u00a0'.repeat(64) + '</span><img id="post-space"></div>');
  const window = dom.window;
  window.ThisIsTheiOSApp = true;
  window.mode = { isMobile: () => true };
  window.L = { Layer: { extend: methods => methods }, Browser: {} };
  const app = { idleHandler: { notifyActive() {} } };
  window.app = { console: { log() {} } };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/layer/marker/TextInput.js'), 'utf8'), { window, document: window.document, Node: window.Node, NodeFilter: window.NodeFilter, app });
  const input = Object.create(window.L.TextInput);
  const removals = [];
  input._textArea = window.document.getElementById('clipboard-area');
  input._map = { getDocType: () => 'text', uiManager: { isUIBlocked: () => false }, _debug: { logKeyboardEvents: false }, getWinId: () => 'test' };
  input._map.mention = { handleMentionInput() {} };
  input._lastContent = [];
  input.hasAccessibilitySupport = () => false;
  input._statusLog = () => {};
  input._removeTextContent = (before, after) => removals.push([before, after]);
  input._sendNewText = () => {};
  input._finishFormulabarEditing = () => {};
  input._sendCompositionEvent = () => {};
  input._iosBackspaceGuardLength = 64;
  input._iosBackspaceGuardBudget = 64;
  input._preSpaceChar = '<span id="pre-space" aria-hidden="true">' + '\u00a0'.repeat(64) + '</span>';
  input._postSpaceChar = '<img id="post-space">';
  input._initialContent = input._preSpaceChar + input._postSpaceChar;
  input._iosBackspaceGuardBeforeInputLength = 0;
  input._emptyArea = () => { throw new Error('must not rebuild the iOS editable during repeated deletion'); };
  const range = window.document.createRange();
  range.setStart(input._getIOSBackspaceGuardNode(), 64); range.collapse(true);
  window.getSelection().addRange(range);
  return { input, window, removals, dom };
}
function backspace(h, options = {}) {
  const event = new h.window.InputEvent('beforeinput', { inputType: 'deleteContentBackward', cancelable: true, ...options });
  h.input._onBeforeInput(event);
  const sel = h.window.getSelection();
  if (h.input._isIOSWriterBackspaceGuardActive() && !h.input._map.uiManager.isUIBlocked() &&
      sel.isCollapsed && !options.isComposing && !h.input._isComposing) {
    const node = sel.anchorNode, offset = sel.anchorOffset;
    if (node && node.nodeType === h.window.Node.TEXT_NODE && offset > 0) {
      node.deleteData(offset - 1, 1);
      const range = h.window.document.createRange(); range.setStart(node, offset - 1); range.collapse(true);
      sel.removeAllRanges(); sel.addRange(range);
    } else if (node && node.nodeType === h.window.Node.TEXT_NODE && offset === 0 &&
        node.parentNode && node.parentNode.id === 'pre-space' && node.previousSibling) {
      const guard = h.input._getIOSBackspaceGuardNode();
      if (guard && guard.length) {
        guard.deleteData(guard.length - 1, 1);
        const range = h.window.document.createRange(); range.setStart(guard, guard.length); range.collapse(true);
        sel.removeAllRanges(); sel.addRange(range);
      }
    }
  }
  if (h.input._isIOSWriterBackspaceGuardActive())
    h.input._onInput(new h.window.InputEvent('input', { inputType: 'deleteContentBackward', ...options }));
  return event;
}
test('held backspace consumes the guard, forwards one delete per consumed guard char, and replenishes same text node', () => {
  const h = harness(), sentinel = h.input._textArea.firstChild;
  for (let i = 0; i < 80; ++i) assert.equal(backspace(h).defaultPrevented, false);
  assert.deepEqual(h.removals, Array.from({ length: 80 }, () => [1, 0]));
  assert.equal(h.input._textArea.firstChild, sentinel);
  assert.ok(h.input._getIOSBackspaceGuardNode().length >= 32);
  assert.equal(h.input.getPlainTextContent(), '');
  h.dom.window.close();
});
test('composition and non-Writer paths do not consume or forward guard deletions', () => {
  for (const kind of ['composition', 'eventComposition', 'calc', 'ppt', 'android', 'a11y', 'blocked']) {
    const h = harness();
    if (kind === 'composition') h.input._isComposing = true;
    if (kind === 'calc') h.input._map.getDocType = () => 'spreadsheet';
    if (kind === 'ppt') h.input._map.getDocType = () => 'presentation';
    if (kind === 'android') h.window.ThisIsTheiOSApp = false;
    if (kind === 'a11y') h.input.hasAccessibilitySupport = () => true;
    if (kind === 'blocked') h.input._map.uiManager.isUIBlocked = () => true;
    const event = backspace(h, { isComposing: kind === 'eventComposition' });
    assert.equal(event.defaultPrevented, false, kind);
    assert.equal(h.removals.length, 0, kind);
    h.dom.window.close();
  }
});
test('typed Chinese is document text, deletes normally, then held delete continues through the guard', () => {
  const h = harness();
  const text = h.window.document.createTextNode('你说呢');
  h.input._getIOSBackspaceGuardNode().parentNode.appendChild(text);
  h.input._lastContent = Array.from('你说呢', ch => ch.codePointAt(0));
  const range = h.window.document.createRange();
  range.setStart(text, 3); range.collapse(true);
  h.window.getSelection().removeAllRanges(); h.window.getSelection().addRange(range);
  assert.equal(h.input.getPlainTextContent(), '你说呢');
  assert.equal(h.input._getSelectionStart(), 3);
  for (let i = 0; i < 3; i++) backspace(h);
  assert.equal(h.input.getPlainTextContent(), '');
  assert.deepEqual(h.removals.slice(0, 3), [[1, 0], [1, 0], [1, 0]]);
  for (let i = 0; i < 80; i++) backspace(h);
  assert.equal(h.removals.length, 83);
  assert.equal(h.input.getPlainTextContent(), '');
  h.dom.window.close();
});
test('Chinese inserted after consumed guard remains document text in same node and next sibling', () => {
  const h = harness(), guard = h.input._getIOSBackspaceGuardNode();
  guard.insertData(guard.length, '中');
  const range = h.window.document.createRange(); range.setStart(guard, guard.length); range.collapse(true);
  h.window.getSelection().removeAllRanges(); h.window.getSelection().addRange(range);
  assert.equal(h.input.getPlainTextContent(), '中');
  assert.equal(h.input._getSelectionStart(), 1);
  h.input._setSelectionRange(1, 1);
  assert.equal(h.input._getSelectionStart(), 1);
  const sibling = h.window.document.createTextNode('文');
  h.input._textArea.insertBefore(sibling, h.input._textArea.lastChild);
  const siblingRange = h.window.document.createRange(); siblingRange.setStart(sibling, 1); siblingRange.collapse(true);
  h.window.getSelection().removeAllRanges(); h.window.getSelection().addRange(siblingRange);
  assert.equal(h.input.getPlainTextContent(), '中文');
  assert.equal(h.input._getSelectionStart(), 2);
  const selection = h.window.document.createRange(); selection.setStart(sibling, 0); selection.setEnd(sibling, 1);
  h.window.getSelection().removeAllRanges(); h.window.getSelection().addRange(selection);
  const event = new h.window.InputEvent('beforeinput', { inputType: 'deleteContentBackward', cancelable: true });
  h.input._onBeforeInput(event);
  assert.equal(h.input._iosBackspaceGuardBeforeInputLength, 0);
  assert.equal(h.removals.length, 0);
  h.dom.window.close();
});
test('WebKit-normalized guard spaces followed by Chinese are not exposed as document text', () => {
  const h = harness(), guard = h.input._getIOSBackspaceGuardNode();
  guard.textContent = (' \u00a0'.repeat(32)) + '中文';
  h.input._iosBackspaceGuardBudget = 64;
  assert.equal(h.input.getPlainTextContent(), '中文');
  const range = h.window.document.createRange(); range.setStart(guard, 66); range.collapse(true);
  h.window.getSelection().removeAllRanges(); h.window.getSelection().addRange(range);
  assert.equal(h.input._getSelectionStart(), 2);
  h.dom.window.close();
});
test('reset after partial guard consumption restores the full hidden budget without leaking guard text', () => {
  const h = harness();
  for (let i = 0; i < 40; i++) backspace(h);
  assert.ok(h.input._iosBackspaceGuardBudget < 64);
  h.input.resetContent();
  assert.equal(h.input._iosBackspaceGuardBudget, 64);
  assert.equal(h.input.getPlainTextContent(), '');
  const guard = h.input._getIOSBackspaceGuardNode();
  guard.insertData(guard.length, 'NBSP user text\u00a0');
  assert.equal(h.input.getPlainTextContent(), 'NBSP user text\u00a0');
  h.dom.window.close();
});
test('a native edit that removes the complete guard is counted once after input and then restored', () => {
  const h = harness();
  h.input._emptyArea = () => h.input.resetContent();
  h.input._onBeforeInput(new h.window.InputEvent('beforeinput', { inputType: 'deleteContentBackward' }));
  assert.equal(h.removals.length, 0);
  h.input._textArea.removeChild(h.input._textArea.firstChild);
  h.input._onInput(new h.window.InputEvent('input', { inputType: 'deleteContentBackward' }));
  assert.deepEqual(h.removals, [[1, 0]]);
  assert.ok(h.input._hasPreSpace());
  assert.equal(h.input._iosBackspaceGuardBudget, 64);
  h.dom.window.close();
});
test('a native edit that empties the guard text but keeps its span forwards only one delete', () => {
  const h = harness();
  const guard = h.input._getIOSBackspaceGuardNode();
  h.input._onBeforeInput(new h.window.InputEvent('beforeinput', { inputType: 'deleteContentBackward' }));
  guard.deleteData(0, guard.length);
  h.input._onInput(new h.window.InputEvent('input', { inputType: 'deleteContentBackward' }));
  assert.deepEqual(h.removals, [[1, 0]]);
  assert.equal(h.input._getIOSBackspaceGuardNode().length, 64);
  assert.equal(h.input.getPlainTextContent(), '');
  h.dom.window.close();
});
