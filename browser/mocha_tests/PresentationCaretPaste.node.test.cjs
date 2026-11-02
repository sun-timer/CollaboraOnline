'use strict';
const assert = require('node:assert/strict'), fs = require('node:fs');
const path = require('node:path'), vm = require('node:vm'), ts = require('typescript');
const test = require('node:test');
const source = fs.readFileSync(path.join(__dirname, '../src/app/AndroidSelectionMenu.ts'), 'utf8');
function harness(ios = false, type = 'presentation') {
  let selected = false, editable = true;
  const calls = [], timers = new Map(), listeners = new Map(); let id = 0;
  class Element { constructor(tagName) {this.tagName=tagName;} closest(){return this.tagName==='HANDLE' ? this : null;} }
  const layer = { _docType: type, _selectedPart: 0 };
  const app = { dpiScale: 1, pixelsToTwips: 1, map: { _docLayer: layer,
    getDocType: () => type, isEditMode: () => editable, isReadOnlyMode: () => !editable, fire() {} },
    file: { textCursor: { visible: true, rectangle: { x1: 100, y1: 200, x2: 100, y2: 220 } } },
    activeDocument: { activeLayout: { documentToViewX: p => p.x, documentToViewY: p => p.y } },
    sectionContainer: { getCanvasBoundingClientRect: () => ({ x: 0, y: 0 }) },
    socket: { sendMessage: m => calls.push(m) } };
  const window = { ThisIsTheAndroidApp: false, ThisIsTheiOSApp: false, innerWidth: 1000, innerHeight: 1000,
    setTimeout: fn => {timers.set(++id, fn); return id;}, clearTimeout: n => timers.delete(n),
    postMobileMessage: m => calls.push(m) };
  const context = { window, app, HTMLElement:Element, document: { addEventListener:(name,fn)=>{
    if(!listeners.has(name)) listeners.set(name,[]); listeners.get(name).push(fn);
  }, querySelector: () => null, getElementById: () => null }, console, Date,
    MobileSelectionMagnifier: { install() {}, hide() {}, startAt() {} }, GraphicSelection: { hasActiveSelection: () => type === 'presentation' },
    MobileSelectionEvents: { broadcastHide() {}, broadcastShow: (anchor, text, mode, hasSelection) => calls.push({ anchor, text, mode, hasSelection }) },
    cool: { SimplePoint: class { constructor(x, y) {this.x = x; this.y = y;} } } };
  vm.runInNewContext(ts.transpileModule(source, {compilerOptions:{ target:ts.ScriptTarget.ES2020, module:ts.ModuleKind.None }}).outputText, context);
  window.ThisIsTheAndroidApp = !ios; window.ThisIsTheiOSApp = ios;
  const m = window.AndroidSelectionMenu;
  m.hasVisibleTextSelection = () => selected;
  m.viewPointToDocumentTwips = (x, y) => ({x, y});
  m.isKeyboardOpen = () => true;
  m.startTextSelectionAt = () => calls.push('word_select');
  const menus = () => calls.filter(c => typeof c === 'object' || c.startsWith('SELECTIONMENU caret '));
  return {m, app, calls, menus, timers, listeners, Element, setSelected:v=>{selected=v;}, setEditable:v=>{editable=v;}};
}
for (const ios of [false, true]) {
  const platform = ios ? 'iOS' : 'Android';
  test(platform + ' long press at insertion caret shows existing no-selection paste menu on release', () => {
    const h = harness(ios); const before = {...h.app.file.textCursor.rectangle};
    h.m.onLongPressAt(100, 210);
    assert.equal(h.menus().length, 0);
    assert.equal(h.m.nativeSelectionDragActive, false);
    h.m.finishTextSelectionDrag(100, 210);
    assert.equal(h.menus().length, 1);
    assert.ok(!h.calls.includes('word_select'));
    assert.ok(!h.calls.some(x => typeof x === 'string' && (x === 'resetselection' || x.startsWith('uno '))));
    assert.deepEqual(h.app.file.textCursor.rectangle, before);
    if (ios) assert.equal(h.menus()[0].hasSelection, false);
    h.m.finishTextSelectionDrag(100, 210); assert.equal(h.menus().length, 1);
  });
  test(platform + ' long press away from caret retains text selection', () => {
    const h = harness(ios); h.m.onLongPressAt(200, 210);
    assert.ok(h.calls.includes('word_select')); assert.equal(h.menus().length, 0);
  });
  test(platform + ' drag and cancelled gesture do not open paste menu', () => {
    for (const action of ['drag', 'cancel', 'releaseMoved', 'lifecycle']) {
      const h = harness(ios); h.m.onLongPressAt(100, 210);
      if (action === 'drag') h.m.updateTextSelectionEndAt(150, 210);
      if (action === 'cancel') h.m.cancelGesture();
      if (action === 'lifecycle') h.m.cancelTextInputRequest('zoom');
      h.m.finishTextSelectionDrag(150, 210); assert.equal(h.menus().length, 0);
    }
  });
  test(platform + ' changed caret, sheet or document invalidates delayed menu', () => {
    for (const action of ['caret', 'part', 'document']) {
      const h = harness(ios); h.m.onLongPressAt(100, 210);
      if (action === 'caret') h.app.file.textCursor.rectangle.x1 = 120;
      if (action === 'part') h.app.map._docLayer._selectedPart = 1;
      if (action === 'document') h.app.map._docLayer = {_docType:'presentation',_selectedPart:0};
      h.m.finishTextSelectionDrag(); assert.equal(h.menus().length, 0);
    }
  });
  test(platform + ' readonly, no caret and existing text range cannot open caret paste menu', () => {
    for (const action of ['readonly', 'noCaret', 'selected']) {
      const h = harness(ios);
      if (action === 'readonly') h.setEditable(false);
      if (action === 'noCaret') h.app.file.textCursor.visible = false;
      if (action === 'selected') h.setSelected(true);
      h.m.onLongPressAt(100, 210); h.m.finishTextSelectionDrag(); assert.equal(h.menus().length, 0);
    }
  });
}
test('Writer retains its existing long-press selection behavior', () => {
  const h = harness(false, 'text');h.m.onLongPressAt(100, 210);
  assert.ok(h.calls.includes('word_select'));assert.equal(h.menus().length,0);
});
test('iOS real touch listeners reach paste menu from canvas and caret handle', () => {
  for (const tag of ['CANVAS','HANDLE']) {
    const h=harness(true); h.m.installInputGestureHooks(); h.m.installIosLongPress();
    const target=new h.Element(tag), point={clientX:100,clientY:210};
    const emit=(type,touches)=>{for(const fn of h.listeners.get(type)||[]) fn({type,target,touches,changedTouches:[point]});};
    emit('touchstart',[point]);
    // Last scheduled timer is the actual 600ms iOS long-press callback.
    Array.from(h.timers.values()).at(-1)();
    assert.equal(h.menus().length,0);assert.ok(h.m.presentationCaretMenuGesture);
    emit('touchend',[]);
    assert.equal(h.menus().length,1);assert.equal(h.menus()[0].hasSelection,false);
    assert.ok(!h.calls.includes('word_select'));
  }
});
