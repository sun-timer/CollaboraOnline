/*
 * Copyright the Collabora Online contributors.
 * SPDX-License-Identifier: MPL-2.0
 *
 * Run directly with: node --test mocha_tests/IOSCalcTap.node.test.js
 * This exercises the real TypeScript state machine without producing browser
 * build artifacts or requiring the full Collabora test bundle.
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');
const { JSDOM } = require('jsdom');

const sourcePaths = [
	'../src/canvas/CanvasSectionObject.ts',
	'../src/canvas/CanvasSectionContainer.ts',
	'../src/canvas/sections/MouseControl.ts',
	'../src/canvas/sections/ShapeHandlesSection.ts',
	'../src/canvas/sections/ShapeHandleScalingSubSection.ts',
	'../src/app/AndroidSelectionMenu.ts',
	'../src/app/AndroidCalcCellMenu.ts',
	'../src/app/AndroidCalcTap.ts',
	'../src/app/MobileSelectionMenuIcons.ts',
	'../src/app/MobileSelectionEvents.ts',
	'../src/app/MobileSelectionMenu.ts',
].map((file) => path.resolve(__dirname, file));

function createHarness({ editable = true } = {}) {
	const source = fs.readFileSync(sourcePaths.find((file) => file.endsWith('/AndroidCalcTap.ts')), 'utf8');
	let now = 1000;
	class FakeDate extends Date {
		static now() { return now; }
	}
	const calls = { clicks: [], menu: [], keyboard: [] };
	const mouseSection = { name: 'mouse' };
	const docLayer = {
		_docType: 'spreadsheet',
		_selectedPart: 0,
		sheetGeometry: {
			getPart: () => 0,
			getCellFromPos: (point) => ({ x: Math.floor(point.x / 10), y: Math.floor(point.y / 10) }),
		},
	};
	const app = {
		dpiScale: 1,
		CSections: { MouseControl: { name: 'mouse' } },
		map: {
			_docLayer: docLayer,
			_textInput: { blur() {} },
			isEditMode: () => editable,
			on() {},
			off() {},
		},
		calc: { cellCursorVisible: false, cellCursorRectangle: null, cellAddress: null },
		sectionContainer: {
			getIOSCalcSectionAtClient: () => mouseSection,
			getCalcTapPositionAtClient: (x, y) => ({ x, y }),
			dispatchCalcTapAtClient: (x, y, count) => {
				calls.clicks.push({ x, y, count });
				return { x, y };
			},
			isDraggingSomething: () => false,
		},
		events: { on() {} },
	};
	const window = {
		ThisIsTheiOSApp: false,
		setTimeout,
		clearTimeout,
		AndroidCalcCellMenu: {
			dismiss: () => calls.menu.push('dismiss'),
			showForIOSTap: (...args) => calls.menu.push(['show', ...args]),
		},
	};
	const AndroidSelectionMenu = {
		beginTextInputRequest: (point) => calls.keyboard.push(['begin', point]),
		resolveTextInputTap: () => calls.keyboard.push(['resolve']),
	};
	const cool = { Point: class Point { constructor(x, y) { this.x = x; this.y = y; } } };
	const js = ts.transpileModule(source, {
		compilerOptions: { target: ts.ScriptTarget.ES5, module: ts.ModuleKind.None },
	}).outputText;
	vm.runInNewContext(js, {
		window, app, AndroidSelectionMenu, cool, Date: FakeDate,
		console, setTimeout, clearTimeout,
	});
	window.ThisIsTheiOSApp = true;
	return {
		Tap: window.AndroidCalcTap,
		calls,
		at: (time) => { now = time; },
		app,
	};
}

function createRealHarness() {
	let now = 1000;
	class FakeDate extends Date { static now() { return now; } }
	class SimplePoint {
		constructor(x, y) { this._x = x; this._y = y; }
		get x() { return this._x; }
		get y() { return this._y; }
		set x(value) { this._x = value; }
		set y(value) { this._y = value; }
		get pX() { return this._x * 15; }
		get pY() { return this._y * 15; }
		set pX(value) { this._x = value / 15; }
		set pY(value) { this._y = value / 15; }
		get cX() { return this.pX; }
		get cY() { return this.pY; }
		clone() { return new SimplePoint(this._x, this._y); }
		toArray() { return [this._x, this._y]; }
		static fromCorePixels([x, y]) { return new SimplePoint(x / 15, y / 15); }
	}
	class SimpleRectangle {
		constructor(x, y, width, height) { Object.assign(this, { x, y, width, height }); }
		static fromCorePixels(values) { return new SimpleRectangle(...values); }
		containsPoint([x, y]) { return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.height; }
	}
	const coreEvents = [];
	const shownMenus = [];
	const focusCalls = [];
	const selectionEvents = [];
	const docLayer = {
		_docType: 'spreadsheet', _selectedPart: 0,
		sheetGeometry: {
			getPart: () => 0,
			getCellFromPos: (point) => ({ x: Math.floor(point.x / 100), y: Math.floor(point.y / 100) }),
		},
		_postMouseEvent: (...args) => coreEvents.push(args),
	};
	const viewedRectangle = {
		pX1: 150, pY1: 30,
		clone() { return { ...this }; },
	};
	const app = {
		CSections: {
			MouseControl: { name: 'mouse', zIndex: 1, drawingOrder: 1, processingOrder: 1 },
			Overlays: { name: 'overlay' },
			ShapeHandlesSection: { name: 'shapes' },
			Scroll: { name: 'scroll' },
		},
		LOButtons: { left: 1 },
		dpiScale: 1, pixelsToTwips: 1 / 15, twipsToPixels: 15, roundedDpiScale: 1,
		activeDocument: { activeLayout: {
			viewedRectangle,
			canvasToDocumentPoint: (point) => ({ x: point.x, y: point.y }),
		} },
		file: { textCursor: { visible: false, rectangle: null } },
		calc: { cellCursorVisible: false, cellCursorRectangle: null, cellAddress: null },
		definitions: {},
		isRectangleVisibleInTheDisplayedArea: () => true,
		isXOrdinateInFrozenPane: () => false,
		isYOrdinateInFrozenPane: () => false,
		sectionContainer: null,
		idleHandler: { notifyActive() {} },
		map: {
			_docLayer: docLayer,
			getDocType: () => 'spreadsheet',
			isEditMode: () => true,
			isReadOnlyMode: () => false,
			focus: (force) => focusCalls.push(force),
			fire() {}, on() {}, off() {},
			sendUnoCommand: (...args) => coreEvents.push(args),
		},
		events: { on() {} },
	};
	const window = {
		ThisIsTheiOSApp: false, ThisIsTheAndroidApp: false,
		setTimeout, clearTimeout, addEventListener() {},
		MobileSelectionEvents: {
			broadcastShow: (...args) => shownMenus.push(args),
			broadcastHide: () => shownMenus.push(['hide']),
		},
	};
	const cool = {
		SimplePoint,
		SimpleRectangle,
		Point: class Point { constructor(x, y) { this.x = x; this.y = y; } },
	};
	const context = vm.createContext({
		window, app, cool, Date: FakeDate, console, setTimeout, clearTimeout,
		Util: { ensureValue(value) { if (!value) throw new Error('missing fixture'); } },
		WriterQuickActionBar: { closeAll() {} },
	});
	for (const file of sourcePaths.slice(0, 8)) {
		const source = fs.readFileSync(file, 'utf8');
		const js = ts.transpileModule(source, {
			compilerOptions: { target: ts.ScriptTarget.ES5, module: ts.ModuleKind.None },
		}).outputText;
		vm.runInContext(js, context, { filename: file });
	}
	window.ThisIsTheiOSApp = true;

	const canvas = { getBoundingClientRect: () => ({ left: 0, top: 0, x: 0, y: 0 }) };
	const container = Object.create(context.CanvasSectionContainer.prototype);
	Object.assign(container, {
		canvas, width: 300, height: 300, sections: [], sectionsByName: new Map(),
		windowSectionList: [], multiTouch: false, multiTouchEndSent: false,
		positionOnMouseDown: null, positionOnMouseUp: null, positionOnClick: null,
		positionOnDoubleClick: null, mousePosition: null, dragDistance: null,
		draggingSomething: false, sectionOnMouseDown: null, sectionUnderMouse: null,
		longPressTimer: null, touchEventInProgress: false, mouseIsInside: true,
		requestReDraw() {},
	});
	const Base = context.CanvasSectionObject.prototype;
	function makeSection(name, { position = [0, 0], size = [300, 300], documentObject = false } = {}) {
		const section = Object.create(Base);
		Object.assign(section, {
			name, position, size, myTopLeft: position.slice(), isLocated: true,
			showSection: true, isVisible: true, documentObject, interactable: true,
			windowSection: false, boundsList: [], sectionProperties: {},
			containerObject: container,
		});
		return section;
	}
	const mouse = Object.create(context.MouseControl.prototype);
	Object.assign(mouse, makeSection('mouse'), {
		name: 'mouse', myTopLeft: [0, 0], boundsList: [], currentPosition: new SimplePoint(0, 0),
		positionOnMouseDown: null, localPositionOnMouseDown: null, mouseDownSent: false,
		inSwipeAction: false,
	});
	mouse.boundsList = [mouse];
	// Like CanvasOverlay, this covers the grid above MouseControl, has no
	// pointer handlers and shares the tiles bound list with MouseControl.
	const overlay = makeSection('overlay');
	overlay.boundsList = [mouse, overlay];
	const graphic = Object.create(context.ShapeHandlesSection.prototype);
	Object.assign(graphic, makeSection('shapes', { position: [80, 80], size: [80, 80], documentObject: true }), {
		sectionProperties: { subSections: [], svg: null, closestX: null, closestY: null },
		checkHelperLinesAndSnapPoints() {},
	});
	const handle = Object.create(context.ShapeHandleScalingSubSection.prototype);
	let shapeCalculationCount = 0;
	Object.assign(handle, makeSection('shape-scale', { position: [145, 145], size: [12, 12], documentObject: true }), {
		sectionProperties: {
			parentHandlerSection: graphic,
			ownInfo: { id: '7' },
			cropModeEnabled: false,
			initialAngle: 0,
			distanceToCenter: 0,
		},
		calculateNewShapeRectangleProperties: () => {
			shapeCalculationCount++;
			return { center: { pX: 100, pY: 100 }, width: 80, height: 80, angleRadian: 0 };
		},
		getNewPosition: () => [1234, 5678],
		doWeKeepRatio: () => true,
	});
	graphic.sectionProperties.handleWidth = 12;
	graphic.sectionProperties.handleHeight = 12;
	graphic.calculateInitialAnglesOfShapeHandlers = () => {};
	graphic.sectionProperties.subSections = [handle];
	graphic.boundsList = [graphic, handle];
	handle.boundsList = [handle];
	const sections = new Map([
		['mouse', mouse], ['overlay', overlay], ['shapes', graphic], ['shape-scale', handle],
	]);
	container.sections = [mouse, overlay, graphic, handle];
	container.sectionsByName = sections;
	container.getSectionWithName = context.CanvasSectionContainer.prototype.getSectionWithName;
	app.sectionContainer = container;
	container.getDocumentAnchor = () => [0, 0];
	container.getCanvasBoundingClientRect = () => ({ x: 0, y: 0 });
	container.getCalcTapPositionAtClient = (x, y) => mouse.getNativeCalcCellTapPosition(
		cool.SimplePoint.fromCorePixels([x, y]),
	);
	container.dispatchCalcTapAtClient = (x, y, count) => mouse.dispatchNativeCalcCellTap(
		cool.SimplePoint.fromCorePixels([x, y]), count,
	);
	return {
		context, app, window, container, mouse, overlay, graphic, handle, canvas,
		coreEvents, shownMenus, selectionEvents, focusCalls,
		at: (value) => { now = value; },
		shapeCalculationCount: () => shapeCalculationCount,
		setHitSection: (section) => { container.findSectionContainingPoint = () => section; },
	};
}

function installRealSelectionMenu(h) {
	const dom = new JSDOM('<!doctype html><html><body><canvas id="document-canvas"></canvas></body></html>', {
		url: 'https://collabora.test/',
		pretendToBeVisual: true,
	});
	const win = dom.window;
	const previousWindow = h.window;
	win.AndroidCalcTap = previousWindow.AndroidCalcTap;
	win.AndroidCalcCellMenu = previousWindow.AndroidCalcCellMenu;
	win.AndroidSelectionMenu = previousWindow.AndroidSelectionMenu;
	const canvas = win.document.getElementById('document-canvas');
	canvas.getBoundingClientRect = () => ({ left: 0, top: 0, x: 0, y: 0, width: 300, height: 300 });
	h.container.canvas = canvas;
	h.canvas = canvas;
	canvas.ontouchstart = h.container.onTouchStart.bind(h.container);
	canvas.ontouchmove = h.container.onTouchMove.bind(h.container);
	canvas.ontouchend = h.container.onTouchEnd.bind(h.container);
	canvas.ontouchcancel = h.container.onTouchCancel.bind(h.container);
	// jsdom does not implement the HTMLCanvasElement on* touch-handler IDL
	// attributes, so register the same callbacks as DOM listeners for dispatch.
	canvas.addEventListener('touchstart', canvas.ontouchstart);
	canvas.addEventListener('touchmove', canvas.ontouchmove);
	canvas.addEventListener('touchend', canvas.ontouchend);
	canvas.addEventListener('touchcancel', canvas.ontouchcancel);
	h.context.window = win;
	h.context.document = win.document;
	h.context.CustomEvent = win.CustomEvent;
	h.context.Node = win.Node;
	win.ThisIsTheiOSApp = true;
	win.app = h.app;
	h.app.map.getDocType = () => 'spreadsheet';
	h.app.map.isEditMode = () => true;
	h.app.map.isReadOnlyMode = () => false;
	h.app.map.getDocType = () => 'spreadsheet';
	h.app.map._docLayer._docType = 'spreadsheet';
	h.app.sectionContainer.getDocumentAnchor = () => [0, 0];
	h.app.sectionContainer.getCanvasBoundingClientRect = () => ({ x: 0, y: 0 });
	h.app.activeDocument.activeLayout.canvasToDocumentPoint = (point) => point;
	h.context.app = h.app;
	h.context.window = win;
	win.requestAnimationFrame = (callback) => win.setTimeout(callback, 0);
	win.visualViewport = null;
	for (const file of sourcePaths.slice(-3)) {
		const source = fs.readFileSync(file, 'utf8');
		const js = ts.transpileModule(source, {
			compilerOptions: { target: ts.ScriptTarget.ES5, module: ts.ModuleKind.None },
		}).outputText;
		vm.runInContext(js, h.context, { filename: file });
	}
	h.window = win;
	h.dom = dom;
	h.context.MobileSelectionMenu.install();
	return win;
}

function touchEvent(type, target, touches, changedTouches = touches) {
	return {
		type, target, touches, changedTouches,
		stopPropagation() {}, preventDefault() {}, stopImmediatePropagation() {},
	};
}

function cellCursor(x = 11, y = 7) {
	const x1 = x - 3;
	const y1 = y - 3;
	return {
		pX1: (x1 * 15), pX2: (x + 3) * 15, pY1: y1 * 15, pY2: (y + 3) * 15, pWidth: 90, pHeight: 90,
		containsPoint: ([px, py]) => px >= x1 && px <= x + 3 && py >= y1 && py <= y + 3,
		toArray: () => [x1, y1, 6, 6],
		equals: (values) => values[0] === x1 && values[1] === y1,
	};
}

// A slow repeated tap waits out the double-tap window before its menu.
function waitForCellMenu() {
	return new Promise((resolve) => setTimeout(resolve, 400));
}

function selectedAddress(x = 0, y = 0) {
	return { x, y, toArray: () => [x, y] };
}

function dispatchDomTouch(harness, type, touches, changedTouches = touches) {
	const canvas = harness.canvas;
	if (!canvas?.dispatchEvent || !harness.window) return false;
	const event = new harness.window.Event(type, { bubbles: true, cancelable: true });
	Object.defineProperties(event, {
		touches: { value: touches },
		changedTouches: { value: changedTouches },
	});
	canvas.dispatchEvent(event);
	return true;
}

function runCellTouch(harness, x = 15, y = 5, startAt = 1000, { hit = harness.mouse, jitter = 0 } = {}) {
	const { container, canvas } = harness;
	harness.at(startAt);
	const touch = { identifier: 1, clientX: x, clientY: y };
	harness.setHitSection(hit);
	const sentStart = dispatchDomTouch(harness, 'touchstart', [touch], [touch]);
	if (!sentStart) container.onTouchStart(touchEvent('touchstart', canvas, [touch]));
	if (jitter) {
		const moved = { identifier: 1, clientX: x + jitter, clientY: y };
		const sentMove = dispatchDomTouch(harness, 'touchmove', [moved], [moved]);
		if (!sentMove) container.onTouchMove(touchEvent('touchmove', canvas, [moved]));
	}
	harness.at(startAt + 70);
	const sentEnd = dispatchDomTouch(harness, 'touchend', [], [touch]);
	if (!sentEnd) container.onTouchEnd(touchEvent('touchend', canvas, [], [touch]));
}

test('iOS Calc fast same-cell double tap enters the existing text-input request flow', () => {
	const { Tap, calls, at } = createHarness();
	assert.equal(Tap.dispatchIOSTapAt(15, 12), true);
	at(1200);
	assert.equal(Tap.dispatchIOSTapAt(16, 13), true);
	assert.deepEqual(calls.clicks.map(({ count }) => count), [1, 2]);
	assert.deepEqual(calls.keyboard.map(([kind]) => kind), ['begin', 'resolve']);
	assert.equal(calls.menu.some((entry) => Array.isArray(entry) && entry[0] === 'show'), false);
});

test('iOS Calc slow repeated tap requests the cell menu without a second core click', async () => {
	const { Tap, calls, at, app } = createHarness();
	Tap.dispatchIOSTapAt(15, 12);
	app.calc.cellCursorVisible = true;
	app.calc.cellCursorRectangle = { containsPoint: ([x, y]) => x === 15 && y === 12 };
	app.calc.cellAddress = { x: 1, y: 1 };
	at(1401);
	Tap.dispatchIOSTapAt(16, 13);
	assert.deepEqual(calls.clicks.map(({ count }) => count), [1]);
	assert.equal(calls.menu.some((entry) => Array.isArray(entry) && entry[0] === 'show'), false,
		'the menu must wait in case this is the first half of a double tap');
	await waitForCellMenu();
	assert.equal(calls.menu.some((entry) => Array.isArray(entry) && entry[0] === 'show'), true);
	assert.deepEqual(calls.keyboard, []);
});

test('read-only iOS Calc double tap never requests a text caret or keyboard', () => {
	const { Tap, calls, at } = createHarness({ editable: false });
	Tap.dispatchIOSTapAt(15, 12);
	at(1200);
	Tap.dispatchIOSTapAt(16, 13);
	assert.deepEqual(calls.clicks.map(({ count }) => count), [1]);
	assert.deepEqual(calls.keyboard, []);
});

test('iOS Calc touch movement beyond five CSS pixels and multitouch cancel tap identity', () => {
	const { Tap } = createHarness();
	const touch = (identifier, clientX, clientY) => ({ identifier, clientX, clientY });
	Tap.beginIOSTouch({ touches: [touch(1, 10, 20)], target: {} });
	assert.ok(Tap.iosDown);
	Tap.moveIOSTouch({ touches: [touch(1, 16, 20)] });
	assert.equal(Tap.iosDown, null);
	Tap.beginIOSTouch({ touches: [touch(1, 10, 20)], target: {} });
	Tap.moveIOSTouch({ touches: [touch(1, 10, 20), touch(2, 11, 20)] });
	assert.equal(Tap.iosDown, null);
});

test('real canvas touch lifecycle keeps tap identity through EMPTY then resolves slow menu from the cursor callback', async () => {
	const h = createRealHarness();
	const { container, canvas, app, window, shownMenus, coreEvents } = h;
	runCellTouch(h, 15, 5, 1000);
	assert.deepEqual(coreEvents.map((event) => event[0]), ['buttondown', 'buttonup']);
	assert.ok(window.AndroidCalcTap.iosLastCellTap, 'first physical tap should be remembered');

	// Match CanvasTileLayer._onCellCursorMsg EMPTY ordering, followed by the
	// later non-empty cursor message after the first tap's core response.
	app.calc.cellCursorVisible = false;
	window.AndroidCalcCellMenu.onIOSCellCursorCleared();
	window.AndroidCalcTap.onIOSCellCursor();
	window.AndroidCalcCellMenu.onIOSSelectionChanged();
	assert.ok(window.AndroidCalcTap.iosLastCellTap, 'transient EMPTY must not erase the first tap');
	app.calc.cellCursorVisible = true;
	app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	app.calc.cellAddress = selectedAddress();
	window.AndroidCalcTap.onIOSCellCursor();
	window.AndroidCalcCellMenu.onIOSSelectionChanged();

	runCellTouch(h, 16, 6, 1700);
	await waitForCellMenu();
	assert.deepEqual(coreEvents.map((event) => event[0]), ['buttondown', 'buttonup']);
	assert.equal(shownMenus.filter((event) => event.length === 3 && event[2] === 'calc').length, 1);
});

test('real canvas touch lifecycle fast double tap waits for and accepts the matching text caret', () => {
	const h = createRealHarness();
	const { container, canvas, app, window, coreEvents, focusCalls } = h;
	runCellTouch(h, 15, 5, 1000);
	app.calc.cellCursorVisible = false;
	window.AndroidCalcCellMenu.onIOSCellCursorCleared();
	window.AndroidCalcTap.onIOSCellCursor();
	app.calc.cellCursorVisible = true;
	app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	app.calc.cellAddress = selectedAddress();
	window.AndroidCalcTap.onIOSCellCursor();

	runCellTouch(h, 16, 6, 1200);
	assert.deepEqual(coreEvents.map((event) => [event[0], event[3]]), [
		['buttondown', 1], ['buttonup', 1], ['buttondown', 2], ['buttonup', 2],
	]);
	const request = window.AndroidSelectionMenu.pendingTextInputRequest;
	assert.ok(request);
	assert.equal(request.tapResolved, true);
	assert.equal(request.caretConfirmed, false, 'double tap alone must wait for the core caret');
	app.file.textCursor.visible = true;
	app.file.textCursor.rectangle = { x1: 11, y1: 35 / 15 };
	window.AndroidSelectionMenu.onTextCaretUpdate(true);
	assert.deepEqual(focusCalls, [true], 'matching caret confirmation should focus the iOS editor');
	assert.equal(window.AndroidSelectionMenu.pendingTextInputRequest, null);
});

test('iOS Calc slow second canvas touch opens the DOM menu and survives its compatibility click', async () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	h.app.calc.cellAddress = selectedAddress();
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(h, 16, 6, 1600);
	win.document.body.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
	await waitForCellMenu();

	const panel = win.document.querySelector('.fabric-selection-menu');
	assert.ok(panel, 'a compatibility click from the opening touch must not cancel the menu');
	assert.ok(panel.classList.contains('fabric-selection-menu--calc'));
	win.document.body.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
	assert.equal(win.document.querySelector('.fabric-selection-menu'), panel,
		'a late compatibility click must not dismiss the menu');
});

test('iOS Calc grid taps hit through the full-grid overlay section like real device touches', async () => {
	const menuHarness = createRealHarness();
	const win = installRealSelectionMenu(menuHarness);
	const viaOverlay = { hit: menuHarness.overlay, jitter: 3 };
	runCellTouch(menuHarness, 15, 5, 1000, viaOverlay);
	assert.ok(win.AndroidCalcTap.iosLastCellTap, 'an overlay hit is still a grid tap');
	menuHarness.app.calc.cellCursorVisible = true;
	menuHarness.app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	menuHarness.app.calc.cellAddress = selectedAddress();
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(menuHarness, 16, 6, 1600, viaOverlay);
	await waitForCellMenu();
	assert.ok(win.document.querySelector('.fabric-selection-menu'),
		'slow second tap through the overlay should open the floating menu');

	const inputHarness = createRealHarness();
	const inputWindow = installRealSelectionMenu(inputHarness);
	const overlayHit = { hit: inputHarness.overlay, jitter: 3 };
	runCellTouch(inputHarness, 15, 5, 1000, overlayHit);
	inputHarness.app.calc.cellCursorVisible = true;
	inputHarness.app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	inputHarness.app.calc.cellAddress = selectedAddress();
	inputWindow.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(inputHarness, 16, 6, 1200, overlayHit);
	assert.deepEqual(inputHarness.coreEvents.map((event) => [event[0], event[3]]), [
		['buttondown', 1], ['buttonup', 1], ['buttondown', 2], ['buttonup', 2],
	]);
	assert.ok(inputWindow.AndroidSelectionMenu.pendingTextInputRequest,
		'fast double tap through the overlay should request text input');
});

test('iOS Calc double tap on an already selected cell enters input instead of opening the menu', async () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	h.app.calc.cellAddress = selectedAddress();
	win.AndroidCalcTap.onIOSCellCursor();

	// Device log timing: the selected cell was tapped about a second earlier.
	runCellTouch(h, 16, 6, 2000);
	runCellTouch(h, 16, 6, 2180);
	assert.deepEqual(h.coreEvents.map((event) => [event[0], event[3]]), [
		['buttondown', 1], ['buttonup', 1], ['buttondown', 2], ['buttonup', 2],
	]);
	assert.ok(win.AndroidSelectionMenu.pendingTextInputRequest, 'double tap should request text input');
	await waitForCellMenu();
	assert.equal(win.document.querySelector('.fabric-selection-menu'), null,
		'the cancelled slow-tap menu must not appear after the double tap');
});

test('iOS Calc double tap still wins when the menu timer expires while the second finger is down', async () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
	h.app.calc.cellAddress = selectedAddress();
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(h, 16, 6, 2000);

	const touch = { identifier: 1, clientX: 16, clientY: 6 };
	h.setHitSection(h.mouse);
	h.at(2300);
	dispatchDomTouch(h, 'touchstart', [touch]);
	await waitForCellMenu();
	assert.equal(win.document.querySelector('.fabric-selection-menu'), null,
		'the timer must not open the menu while a touch is still deciding');
	h.at(2380);
	dispatchDomTouch(h, 'touchend', [], [touch]);
	assert.ok(win.AndroidSelectionMenu.pendingTextInputRequest,
		'second touch-down inside the window is a double tap');
});

test('iOS Calc range selection handle release opens the cell menu on the final range', async () => {
	function rangeHarness() {
		const h = createRealHarness();
		const win = installRealSelectionMenu(h);
		h.app.calc.cellCursorVisible = true;
		h.app.calc.cellCursorRectangle = cellCursor(11, 35 / 15);
		h.app.calc.cellAddress = selectedAddress();
		h.app.map._docLayer._cellSelectionArea = {
			pX1: 0, pY1: 0, pX2: 3000, pY2: 1500, pWidth: 3000, pHeight: 1500,
		};
		return { h, win };
	}
	const settled = rangeHarness();
	settled.win.AndroidCalcCellMenu.showAfterIOSRangeSelection();
	assert.equal(settled.win.document.querySelector('.fabric-selection-menu'), null,
		'the menu waits for core to report the final range');
	settled.win.AndroidCalcCellMenu.onIOSCellCursorCleared();
	await new Promise((resolve) => setTimeout(resolve, 250));
	assert.ok(settled.win.document.querySelector('.fabric-selection-menu'),
		'a transient EMPTY cursor must not cancel the range menu');

	const interrupted = rangeHarness();
	interrupted.win.AndroidCalcCellMenu.showAfterIOSRangeSelection();
	runCellTouch(interrupted.h, 200, 200, 1000);
	await new Promise((resolve) => setTimeout(resolve, 250));
	assert.equal(interrupted.win.document.querySelector('.fabric-selection-menu'), null,
		'a new touch before the range settles cancels its menu');
});

test('iOS Calc cell menu anchor keeps frozen pane coordinates after sheet scrolling', async () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	h.app.isXOrdinateInFrozenPane = (x) => x >= 0 && x < 200;
	h.app.isYOrdinateInFrozenPane = (y) => y >= 0 && y < 60;
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(4, 3.3);
	h.app.calc.cellAddress = selectedAddress();
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(h, 16, 6, 1600);
	await waitForCellMenu();
	const panel = win.document.querySelector('.fabric-selection-menu');
	assert.ok(panel, 'frozen cell second tap should create the floating menu');
	assert.equal(panel.style.left, '24px', 'frozen X origin should not subtract viewedRectangle.pX1');
	assert.equal(panel.style.top, '119px', 'frozen Y origin should not subtract viewedRectangle.pY1');
});

test('iOS Calc double tap during EMPTY waits for the matching new cell cursor before accepting its caret', () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = false;
	win.AndroidCalcCellMenu.onIOSCellCursorCleared();
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(h, 16, 6, 1200);
	const request = win.AndroidSelectionMenu.pendingTextInputRequest;
	assert.ok(request, 'fast second tap should enter the shared text-input request flow');
	assert.equal(request.calcCell, null, 'EMPTY must not capture the old cell as the requested target');

	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(14, 5.3);
	h.app.calc.cellAddress = selectedAddress(1, 0);
	h.app.file.textCursor.visible = true;
	h.app.file.textCursor.rectangle = { x1: request.targetPoint.x, y1: request.targetPoint.y };
	win.AndroidCalcTap.onIOSCellCursor();
	assert.ok(request.calcCell, 'the later cursor reply should bind the request to the tapped cell');
	assert.deepEqual(h.focusCalls, [true], 'the matching caret confirmation should focus the iOS editor');
	assert.equal(win.AndroidSelectionMenu.pendingTextInputRequest, null);
});

test('iOS Calc rejects an old cell cursor and a sheet switch while an EMPTY double-tap request waits', () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = false;
	win.AndroidCalcCellMenu.onIOSCellCursorCleared();
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(h, 16, 6, 1200);
	const request = win.AndroidSelectionMenu.pendingTextInputRequest;
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(50, 50);
	h.app.calc.cellAddress = selectedAddress(4, 4);
	h.app.file.textCursor.visible = true;
	h.app.file.textCursor.rectangle = { x1: 50, y1: 50 };
	win.AndroidCalcTap.onIOSCellCursor();
	win.AndroidSelectionMenu.onTextCaretUpdate(true);
	assert.equal(request.calcCell, null, 'a stale cursor outside the tap target cannot confirm the request');
	assert.deepEqual(h.focusCalls, []);

	h.app.map._docLayer._selectedPart = 1;
	h.app.calc.cellCursorRectangle = cellCursor(14, 5.3);
	h.app.calc.cellAddress = selectedAddress(1, 0);
	h.app.file.textCursor.rectangle = { x1: request.targetPoint.x, y1: request.targetPoint.y };
	win.AndroidCalcTap.onIOSCellCursor();
	win.AndroidSelectionMenu.onTextCaretUpdate(true);
	assert.equal(request.calcCell, null, 'a sheet switch cannot rebind a pending request');
	assert.deepEqual(h.focusCalls, []);
});

test('iOS Calc fast double tap does not bind a visible old-cell cursor to the new target', () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = cellCursor(50, 50);
	h.app.calc.cellAddress = selectedAddress(4, 4);
	runCellTouch(h, 16, 6, 1200);
	const request = win.AndroidSelectionMenu.pendingTextInputRequest;
	assert.ok(request);
	assert.equal(request.calcCell, null, 'the stale visible cell is outside the second tap target');

	h.app.calc.cellCursorRectangle = cellCursor(14, 5.3);
	h.app.calc.cellAddress = selectedAddress(1, 0);
	h.app.file.textCursor.visible = true;
	h.app.file.textCursor.rectangle = { x1: request.targetPoint.x, y1: request.targetPoint.y };
	win.AndroidCalcTap.onIOSCellCursor();
	assert.ok(request.calcCell, 'the new matching cursor should replace the stale target state');
	assert.deepEqual(h.focusCalls, [true]);
});

test('iOS Calc 350ms boundary, cross-cell tap, read-only guard, cancel and multitouch use DOM touch events', () => {
	const boundary = createRealHarness();
	installRealSelectionMenu(boundary);
	runCellTouch(boundary, 15, 5, 1000);
	runCellTouch(boundary, 16, 6, 1350);
	assert.deepEqual(boundary.coreEvents.map((event) => event[3]), [1, 1, 2, 2],
		'at exactly 350ms the second touch remains a double tap');

	const acrossCells = createRealHarness();
	const acrossWindow = installRealSelectionMenu(acrossCells);
	acrossCells.app.map._docLayer.sheetGeometry.getCellFromPos = (point) => ({
		x: Math.floor(point.x / 10), y: Math.floor(point.y / 10),
	});
	runCellTouch(acrossCells, 15, 5, 1000);
	runCellTouch(acrossCells, 200, 5, 1200);
	assert.deepEqual(acrossCells.coreEvents.map((event) => event[3]), [1, 1, 1, 1],
		'a different cell must dispatch a fresh single click');
	assert.equal(acrossWindow.document.querySelector('.fabric-selection-menu'), null);

	const readOnly = createRealHarness();
	const readOnlyWindow = installRealSelectionMenu(readOnly);
	readOnly.app.map.isEditMode = () => false;
	runCellTouch(readOnly, 15, 5, 1000);
	runCellTouch(readOnly, 16, 6, 1200);
	assert.deepEqual(readOnly.coreEvents.map((event) => event[3]), [1, 1]);
	assert.equal(readOnlyWindow.AndroidSelectionMenu.pendingTextInputRequest, null);

	const canceled = createRealHarness();
	const cancelWindow = installRealSelectionMenu(canceled);
	const firstTouch = { identifier: 1, clientX: 15, clientY: 5 };
	canceled.setHitSection(canceled.mouse);
	dispatchDomTouch(canceled, 'touchstart', [firstTouch]);
	dispatchDomTouch(canceled, 'touchcancel', [], [firstTouch]);
	assert.equal(cancelWindow.AndroidCalcTap.iosDown, null);
	assert.deepEqual(canceled.coreEvents, []);

	const pinch = createRealHarness();
	const pinchWindow = installRealSelectionMenu(pinch);
	const firstFinger = { identifier: 1, clientX: 15, clientY: 5 };
	const secondFinger = { identifier: 2, clientX: 16, clientY: 5 };
	pinch.setHitSection(pinch.mouse);
	dispatchDomTouch(pinch, 'touchstart', [firstFinger, secondFinger]);
	assert.equal(pinchWindow.AndroidCalcTap.iosDown, null);
	dispatchDomTouch(pinch, 'touchend', [], [firstFinger, secondFinger]);
	assert.deepEqual(pinch.coreEvents, []);
});

test('iOS Calc merged cell taps use the canonical cursor address across the merged range', async () => {
	const h = createRealHarness();
	const win = installRealSelectionMenu(h);
	runCellTouch(h, 15, 5, 1000);
	h.app.calc.cellCursorVisible = true;
	h.app.calc.cellCursorRectangle = {
		pX1: 0, pX2: 1500, pY1: 0, pY2: 900, pWidth: 1500, pHeight: 900,
		containsPoint: ([x, y]) => x >= 0 && x <= 100 && y >= 0 && y <= 60,
		toArray: () => [0, 0, 100, 60],
		equals: (value) => value[0] === 0 && value[1] === 0,
	};
	h.app.calc.cellAddress = selectedAddress(0, 0);
	win.AndroidCalcTap.onIOSCellCursor();
	runCellTouch(h, 80, 50, 1600);
	await waitForCellMenu();
	assert.deepEqual(h.coreEvents.filter((event) => event[0] === 'buttondown').map((event) => event[3]), [1],
		'a second tap inside the same merged cell should request the menu without another core click');
	assert.ok(win.document.querySelector('.fabric-selection-menu'));
});

test('real shape-handle touch cancels the 550ms text long-press and commits one MoveShapeHandle', async () => {
	const h = createRealHarness();
	const { container, canvas, handle, coreEvents, graphic } = h;
	let longPressCallbacks = 0;
	container.onMouseMove = () => { longPressCallbacks++; };
	h.setHitSection(handle);
	const down = { identifier: 1, clientX: 150, clientY: 150 };
	h.at(1000);
	container.onTouchStart(touchEvent('touchstart', canvas, [down]));
	assert.equal(container.longPressTimer, null, 'handle touch should stop the container timer');
	await new Promise((resolve) => setTimeout(resolve, 600));
	assert.equal(longPressCallbacks, 0, 'no text long-press callback should fire after 550ms');

	const moved = { identifier: 1, clientX: 170, clientY: 170 };
	h.at(1700);
	container.onTouchMove(touchEvent('touchmove', canvas, [moved]));
	// The dispatch route invokes the real ShapeHandlesSection fallback, which in
	// turn invokes this actual handle's moveHandlesOnDrag method.
	assert.equal(container.isDraggingSomething(), true);
	assert.equal(h.shapeCalculationCount(), 1);
	assert.deepEqual(handle.position, [94, 94], 'actual drag method should move the handle preview geometry');
	container.onTouchEnd(touchEvent('touchend', canvas, [], [moved]));
	const commits = coreEvents.filter((event) => event[0] === '.uno:MoveShapeHandle');
	assert.equal(commits.length, 1, 'one touch gesture should send one UNO commit');
	assert.deepEqual(JSON.parse(JSON.stringify(commits[0][1])), {
		HandleNum: { type: 'long', value: '7' },
		NewPosX: { type: 'long', value: 1234 },
		NewPosY: { type: 'long', value: 5678 },
	});
	assert.equal(graphic.iosCalcGesturePositions, null, 'touch-up should release the gesture snapshot');
});
