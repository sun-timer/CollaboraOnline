'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');
const source = fs.readFileSync(path.join(__dirname, '../src/app/AndroidSelectionMenu.ts'), 'utf8');

function harness(type = 'text', editable = true, options = {}) {
  const events = {}, docs = {}, timers = new Map(), calls = [], focusCalls = [];
  let next = 0;
  const layer = { _docType: type };
  const map = { _docLayer: layer, getDocType: () => type, isEditMode: () => editable, isReadOnlyMode: () => !editable,
    on: (name, fn) => { docs[name] = fn; }, sendUnoCommand: cmd => calls.push(cmd),
    focus: accept => focusCalls.push(accept), _textInput: {} };
  const app = { map, file: { textCursor: { visible: false, rectangle: { x1: 0, y1: 0, x2: 0, y2: 20 } } },
    pixelsToTwips: 1, events: { on: (name, fn) => { events[name] = fn; } } };
  const document = { addEventListener: (name, fn) => { docs[name] = fn; }, hidden: false };
  const window = { ThisIsTheAndroidApp: false, ThisIsTheiOSApp: false,
    setTimeout: fn => { const id = ++next; timers.set(id, fn); return id; },
    clearTimeout: id => timers.delete(id), postMobileMessage: msg => calls.push(msg) };
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.None } }).outputText;
  const context = { window, document, app, console, Date, MobileSelectionMagnifier:{hide(){}}, TextSelections:{isActive:()=>false,deactivate(){}}, MobileSelectionEvents:{broadcastHide(){}}, setTimeout: window.setTimeout, clearTimeout: window.clearTimeout };
  if (options.textSelections) context.TextSelections = options.textSelections;
  if (options.graphicSelection) context.GraphicSelection = options.graphicSelection;
  vm.runInNewContext(code, context);
  if (options.ios) window.ThisIsTheiOSApp = true;
  else window.ThisIsTheAndroidApp = true;
  const menu = window.AndroidSelectionMenu;
  return { menu, app, map, layer, calls, focusCalls, events, docs, timers, setEditable: v => { editable = v; } };
}
test('Writer line-end and blank-line caret matches current and new caret paths', () => {
  const h = harness(); const m = h.menu;
  const caret = h.app.file.textCursor; caret.visible = true;
  const point = { x: 200, y: 10 };
  assert.equal(m.isCaretAtTarget(caret.rectangle, point), true);
  m.beginTextInputRequest(point); m.resolveTextInputTap();
  assert.ok(h.calls.some(x => x.startsWith('SHOWTEXTIME ')));
  h.calls.length = 0; caret.rectangle = { x1: 10, y1: 100, x2: 10, y2: 120 };
  m.beginTextInputRequest(point); m.resolveTextInputTap();
  caret.rectangle = { x1: 0, y1: 0, x2: 0, y2: 20 };
  m.onTextCaretUpdate(true);
  assert.ok(h.calls.some(x => x.startsWith('SHOWTEXTIME ')));
});
test('edit button reuses caret or waits for document end caret', () => {
  const h = harness(); h.app.file.textCursor.visible = true;
  h.menu.beginWriterEditKeyboard(41);
  assert.deepEqual(h.calls, ['SHOWWRITEREDITIME 41']);
  h.calls.length = 0; h.app.file.textCursor.visible = false;
  h.menu.beginWriterEditKeyboard(42);
  assert.deepEqual(h.calls, ['.uno:GoToEndOfDoc']);
  h.app.file.textCursor.visible = true; h.menu.onTextCaretUpdate(true);
  assert.deepEqual(h.calls, ['.uno:GoToEndOfDoc', 'SHOWWRITEREDITIME 42']);
  h.menu.onTextCaretUpdate(true); assert.equal(h.calls.length, 2);
});
test('touch, permission, document changes and timeout cancel pending request', () => {
  for (const kind of ['touch', 'permission', 'document', 'timeout']) {
    const h = harness(); h.menu.installInputGestureHooks(); h.menu.installTextInputLifecycleHooks();
    h.menu.beginWriterEditKeyboard(55);
    if (kind === 'touch') h.docs.pointerdown({ type: 'pointerdown', pointerType: 'mouse' });
    if (kind === 'permission') { h.setEditable(false); h.events.updatepermission(); h.setEditable(true); }
    if (kind === 'document') h.docs.closeapp();
    if (kind === 'timeout') [...h.timers.values()][0]();
    h.app.file.textCursor.visible = true; h.menu.onTextCaretUpdate(true);
    assert.deepEqual(h.calls, ['.uno:GoToEndOfDoc'], kind);
  }
});
test('Calc, Impress and readonly do not request keyboard', () => {
  for (const [type, editable] of [['spreadsheet', true], ['presentation', true], ['text', false]]) {
    const h = harness(type, editable); h.app.file.textCursor.visible = true;
    h.menu.beginWriterEditKeyboard(66); assert.deepEqual(h.calls, []);
  }
});

test('ordinary Writer tap exits previous long-press selection before confirming keyboard', () => {
  const h=harness();h.app.file.textCursor.visible=true;
  h.menu.pendingLongPressSelection=true;h.menu.selectionGestureComplete=true;
  h.menu.beginTextInputTap({x:0,y:10});h.menu.resolveTextInputTap();
  assert.equal(h.menu.pendingLongPressSelection,false);
  assert.ok(h.calls.some(x=>x.startsWith('SHOWTEXTIME ')));
});

// 空文档单击正文中间：内核把插入点留在空段落起始处（0,0），与点击处相距很远。
test('empty Writer document tap far from the caret raises the keyboard', () => {
  const h = harness(); const m = h.menu;
  h.app.file.textCursor.visible = true;
  m.beginTextInputTap({ x: 200, y: 600 });
  m.onTextCaretUpdate(true);
  m.resolveTextInputTap();
  assert.ok(h.calls.some(x => x.startsWith('SHOWTEXTIME ')), 'click in the blank body must request the IME');
});

// 半页文档单击正文下方空白：内核把插入点夹到文末，同样可能远离点击处。
test('Writer half page blank tap moves the caret far away and still raises the keyboard', () => {
  const h = harness(); const m = h.menu;
  const caret = h.app.file.textCursor; caret.visible = true;
  caret.rectangle = { x1: 10, y1: 900, x2: 10, y2: 920 };
  m.beginTextInputTap({ x: 260, y: 2100 });
  m.onTextCaretUpdate(true);
  m.resolveTextInputTap();
  assert.ok(h.calls.some(x => x.startsWith('SHOWTEXTIME ')));
});

test('Calc and Impress keep the caret geometry rule for taps far from the cursor', () => {
  for (const type of ['spreadsheet', 'presentation']) {
    const h = harness(type); const m = h.menu;
    const caret = h.app.file.textCursor; caret.visible = true;
    caret.rectangle = { x1: 10, y1: 900, x2: 10, y2: 920 };
    m.beginTextInputTap({ x: 260, y: 2100 });
    m.onTextCaretUpdate(true);
    m.resolveTextInputTap();
    assert.equal(h.calls.some(x => x.startsWith('SHOWTEXTIME ')), false, type);
  }
});

test('Writer blank tap does not steal the keyboard from an image selection', () => {
  const h = harness('text', true, { graphicSelection: { hasActiveSelection: () => true } });
  const m = h.menu;
  h.app.file.textCursor.visible = true;
  m.beginTextInputTap({ x: 200, y: 600 });
  m.onTextCaretUpdate(true);
  m.resolveTextInputTap();
  assert.equal(h.calls.some(x => x.startsWith('SHOWTEXTIME ')), false);
});

test('Writer blank tap keeps the keyboard closed for an existing text selection', () => {
  const selection = { isActive: () => true, deactivate() {},
    getStartRectangle: () => ({ pX1: 0, pY1: 0, pX2: 90, pY2: 20 }),
    getEndRectangle: () => ({ pX1: 200, pY1: 0, pX2: 290, pY2: 20 }) };
  const h = harness('text', true, { textSelections: selection });
  const m = h.menu;
  h.app.file.textCursor.visible = true;
  m.beginTextInputTap({ x: 200, y: 600 });
  m.onTextCaretUpdate(true);
  m.resolveTextInputTap();
  assert.equal(h.calls.some(x => x.startsWith('SHOWTEXTIME ')), false);
});

// 空白处单击时插入光标本就可见，内核不会再发 cursorvisible；不能因此不弹键盘。
test('Writer blank tap raises the keyboard without any caret reply from Core', () => {
  const h = harness(); const m = h.menu;
  const caret = h.app.file.textCursor; caret.visible = true;
  caret.rectangle = { x1: 0, y1: 0, x2: 0, y2: 20 };
  m.beginTextInputTap({ x: 400, y: 1800 });
  m.resolveTextInputTap();
  assert.ok(h.calls.some(x => x.startsWith('SHOWTEXTIME ')));
});

// iOS 只有在单击手势内同步 focus 才稳定拉起软键盘。
test('iOS Writer blank tap focuses inside the tap gesture', () => {
  const h = harness('text', true, { ios: true }); const m = h.menu;
  const caret = h.app.file.textCursor; caret.visible = true;
  m.beginTextInputTap({ x: 400, y: 1800 });
  assert.deepEqual(h.focusCalls, [true]);
});

test('iOS Calc and Impress taps do not focus inside the gesture', () => {
  for (const type of ['spreadsheet', 'presentation']) {
    const h = harness(type, true, { ios: true }); const m = h.menu;
    h.app.file.textCursor.visible = true;
    m.beginTextInputTap({ x: 400, y: 1800 });
    assert.deepEqual(h.focusCalls, [], type);
  }
});

test('iOS read-only Writer tap does not focus inside the gesture', () => {
  const h = harness('text', false, { ios: true }); const m = h.menu;
  h.app.file.textCursor.visible = true;
  m.beginTextInputTap({ x: 400, y: 1800 });
  assert.deepEqual(h.focusCalls, []);
});
