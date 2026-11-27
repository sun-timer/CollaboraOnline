'use strict';
const assert = require('node:assert/strict'), fs = require('node:fs');
const path = require('node:path'), vm = require('node:vm'), ts = require('typescript');
const test = require('node:test');
function loadSource(file, context) {
  const source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.None },
  });
  vm.runInContext(outputText, context, { filename: file });
}
function harness(ios = false, type = 'presentation') {
  let selected = false, editable = true;
  const calls = [], timers = new Map(), listeners = new Map(); let id = 0;
  class Element { constructor(tagName) {this.tagName=tagName;} closest(){return this.tagName==='HANDLE' ? this : null;} }
  const canvas = new Element('CANVAS');
  const layer = { _docType: type, _selectedPart: 0, _postSelectTextEvent() {} };
  const app = { dpiScale: 1, pixelsToTwips: 1, map: { _docLayer: layer,
    getDocType: () => type, isEditMode: () => editable, isReadOnlyMode: () => !editable, fire() {} },
    file: { textCursor: { visible: true, rectangle: { x1: 100, y1: 200, x2: 100, y2: 220 } } },
    activeDocument: { activeView: { hasTextSelection: false },
      activeLayout: { documentToViewX: p => p.x, documentToViewY: p => p.y } },
    sectionContainer: { getCanvasBoundingClientRect: () => ({ x: 0, y: 0 }) },
    socket: { sendMessage: m => calls.push(m) } };
  const window = { ThisIsTheAndroidApp: false, ThisIsTheiOSApp: false, innerWidth: 1000, innerHeight: 1000,
    setTimeout: fn => {timers.set(++id, fn); return id;}, clearTimeout: n => timers.delete(n),
    postMobileMessage: m => calls.push(m) };
  const context = { window, app, HTMLElement:Element, document: { addEventListener:(name,fn)=>{
    if(!listeners.has(name)) listeners.set(name,[]); listeners.get(name).push(fn);
  }, querySelector: () => null, getElementById: id => id === 'document-canvas' ? canvas : null }, console, Date,
    MobileSelectionMagnifier: { install() {}, hide() {}, startAt() {}, updateAt() {} },
    GraphicSelection: { hasActiveSelection: () => type === 'presentation' },
    MobileSelectionEvents: { broadcastHide() {}, broadcastShow: (anchor, text, mode, hasSelection) => calls.push({ anchor, text, mode, hasSelection }) },
    cool: { SimplePoint: class { constructor(x, y) {this.x = x; this.y = y;} } } };
  vm.createContext(context);
  loadSource('app/AndroidSelectionMenu.ts', context);
  window.ThisIsTheAndroidApp = !ios; window.ThisIsTheiOSApp = ios;
  const m = window.AndroidSelectionMenu;
  m.hasVisibleTextSelection = () => selected;
  m.viewPointToDocumentTwips = (x, y) => ({x, y});
  m.isKeyboardOpen = () => true;
  m.startTextSelectionAt = () => calls.push('word_select');
  const menus = () => calls.filter(c => typeof c === 'object' || c.startsWith('SELECTIONMENU caret '));
  return {m, app, calls, menus, timers, listeners, Element, canvas, context,
    setSelected:v=>{selected=v;}, setEditable:v=>{editable=v;}};
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
    const target=tag === 'CANVAS' ? h.canvas : new h.Element(tag), point={clientX:100,clientY:210};
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

test('iOS Impress long-press drag shows the lens after the document text range is confirmed', () => {
  for (const thumbnail of [false, true]) {
    const h = harness(true);
    const lens = [];
    const target = thumbnail ? new h.Element('CANVAS') : h.canvas;
    h.context.MobileSelectionMagnifier.startAt = (...point) => lens.push(['start', ...point]);
    h.context.MobileSelectionMagnifier.updateAt = (...point) => lens.push(['update', ...point]);
    h.context.MobileSelectionMagnifier.hide = () => lens.push(['hide']);
    h.m.installIosLongPress();
    const emit = (type, x, y, down = true) => {
      const point = { clientX: x, clientY: y };
      for (const fn of h.listeners.get(type)) {
        fn({ type, target, touches: down ? [point] : [], changedTouches: [point] });
      }
    };
    emit('touchstart', 200, 210);
    if (thumbnail) {
      assert.equal(h.timers.size, 0);
      assert.deepEqual(lens, []);
      continue;
    }
    Array.from(h.timers.values()).at(-1)();
    assert.ok(h.calls.includes('word_select'));
    emit('touchmove', 202, 212);
    assert.deepEqual(lens.filter(([action]) => action !== 'hide'), []);
    lens.length = 0;
    h.m.selectionStartAcknowledged = true;
    h.app.activeDocument.activeView.hasTextSelection = true;
    emit('touchmove', 205, 215);
    emit('touchmove', 209, 215);
    assert.deepEqual(lens, [['start', 205, 215], ['update', 209, 215]]);
    emit('touchend', 209, 215, false);
    assert.deepEqual(lens.at(-1), ['hide']);
  }
});

test('iOS Impress caret and selection handles show the lens during their canvas drag', () => {
  for (const caret of [true, false]) {
    const h = harness(true);
    h.app.file.textCursor.visible = caret;
    h.app.activeDocument.activeView.hasTextSelection = !caret;
    h.app.CSections = { CursorHandler: { name: 'cursor handler' } };
    h.app.map._docLayer._postMouseEvent = () => {};
    h.app.map._docLayer.isCalcRTL = () => false;
    h.context.window.touch = { currentlyUsingTouchscreen: () => true };
    h.context.HTMLObjectSection = class {
      constructor(name) {
        this.name = name;
        this.position = [100, 200];
        this.myTopLeft = this.position;
        this.sectionProperties = { objectDiv: { style: {} } };
        this.containerObject = { isDraggingSomething: () => true, targetSection: name };
      }
      setPosition() {}
      stopPropagating() {}
    };
    loadSource('app/MobileSelectionEvents.ts', h.context);
    loadSource('canvas/sections/CursorHandler.ts', h.context);
    loadSource('canvas/sections/TextSelectionHandleSection.ts', h.context);
    const lens = [];
    const magnifier = h.context.window.MobileSelectionMagnifier;
    magnifier.startAt = (...point) => lens.push(['start', ...point]);
    magnifier.updateAt = (...point) => lens.push(['update', ...point]);
    magnifier.hide = () => lens.push(['hide']);
    const handle = vm.runInContext(
      caret ? 'new CursorHandler()' : 'new TextSelectionHandle("selection_end_handle", 30, 44, null)',
      h.context,
    );
    const event = (type, clientX) => ({
      type, stopPropagation() {},
      touches: [{ clientX, clientY: 244 }],
    });
    const point = { pX: 0, pY: 0 };
    handle.onMouseDown(point, event('touchstart', 100));
    if (caret) assert.deepEqual(lens, []);
    handle.onMouseMove(point, [5, 0], event('touchmove', 105));
    handle.onMouseMove(point, [9, 0], event('touchmove', 109));
    handle.onMouseUp(point, event('touchend', 109));
    assert.deepEqual(lens.slice(0, 2), [['start', caret ? 105 : 100, 222], ['update', caret ? 109 : 105, 222]]);
    assert.deepEqual(lens.at(-1), ['hide']);
  }
});

test('the lens enables Impress only in iOS text editing', () => {
  for (const ios of [true, false]) {
    const h = harness(ios);
    loadSource('app/MobileSelectionEvents.ts', h.context);
    const magnifier = h.context.window.MobileSelectionMagnifier;
    assert.equal(magnifier.isEnabled(), ios);
    h.app.file.textCursor.visible = false;
    assert.equal(magnifier.isEnabled(), false);
    h.app.activeDocument.activeView.hasTextSelection = true;
    assert.equal(magnifier.isEnabled(), ios);
    h.setEditable(false);
    assert.equal(magnifier.isEnabled(), false);
  }
});
