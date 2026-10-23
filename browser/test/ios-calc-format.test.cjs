const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const ts = require('typescript');
const { JSDOM } = require('jsdom');

const source = fs.readFileSync(path.join(__dirname, '../src/app/CalcEditorFormat.ts'), 'utf8');
const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2019 } }).outputText;
const sandbox = {
	window: {
		ThisIsTheiOSApp: true,
		app: {
			map: {
				getDocType: () => 'spreadsheet',
				stateChangeHandler: { getItemValue: () => undefined },
				isEditMode: () => true,
				sendUnoCommand: () => {},
			},
		},
		CalcFormatStateBridge: { handles: () => false, state: () => undefined },
	},
	globalThis: {},
};
vm.runInNewContext(js + '\nglobalThis.CalcEditorFormat = CalcEditorFormat;', sandbox);
const format = sandbox.globalThis.CalcEditorFormat;

assert.deepStrictEqual(format.numberFormat(), null, 'unknown document status must stay unknown');
assert.strictEqual(format.color('.uno:Color'), null, 'unknown color must not fall back to a picker default');

let status = { '.uno:NumberFormat': '1,0,4,2,1' };
sandbox.window.CalcFormatStateBridge = {
	handles: (command) => command === '.uno:NumberFormat',
	state: (command) => status[command],
};
assert.deepStrictEqual(JSON.parse(JSON.stringify(format.numberFormat())), {
	thousands: true, negativeRed: false, precision: 4, leadingZeroes: 2, natNum12: 1,
});

let sent;
sandbox.window.app.map.sendUnoCommand = (command, args) => { sent = { command, args }; };
assert.strictEqual(format.setNumberFormat({
	thousands: true, negativeRed: false, precision: 5, leadingZeroes: 2, natNum12: 1,
}), true);
assert.strictEqual(sent.command, '.uno:NumberFormat');
assert.strictEqual(sent.args.NumberFormat.value, '1,0,5,2,1,');
assert.strictEqual(format.checked('.uno:AlignmentStacked'), undefined);

let optimistic = { thousands: false, negativeRed: false, precision: 2, leadingZeroes: 1, natNum12: 0 };
optimistic = format.mergeNumberFormat(optimistic, { precision: optimistic.precision + 1 });
optimistic = format.mergeNumberFormat(optimistic, { precision: optimistic.precision + 1 });
// An unrelated callback reads document state, while the panel retains its queued target.
assert.strictEqual(format.numberFormat().precision, 4);
assert.strictEqual(optimistic.precision, 4, 'rapid increments must accumulate through unrelated state callbacks');
optimistic = format.mergeNumberFormat(optimistic, { leadingZeroes: optimistic.leadingZeroes + 1 });
assert.deepStrictEqual(JSON.parse(JSON.stringify(optimistic)), {
	thousands: false, negativeRed: false, precision: 4, leadingZeroes: 2, natNum12: 0,
});

console.log('iOS Calc format state tests passed');

function transpile(file) {
	return ts.transpileModule(fs.readFileSync(path.join(__dirname, file), 'utf8'), {
		compilerOptions: { target: ts.ScriptTarget.ES2019 },
	}).outputText;
}

function fakeClock(win) {
	let next = 0;
	const timers = new Map();
	win.setTimeout = (fn, delay) => { const id = ++next; timers.set(id, { fn, delay }); return id; };
	win.clearTimeout = (id) => timers.delete(id);
	return {
		timers,
		run(id) { const timer = timers.get(id); if (timer) { timers.delete(id); timer.fn(); } },
	};
}

// Exercise the panel's actual dirty/pending/selection handlers with a minimal DOM.
{
	const dom = new JSDOM('<div id="addressInput"><input aria-label="A1"></div>');
	const clock = fakeClock(dom.window);
	const map = {
		getDocType: () => 'spreadsheet', isEditMode: () => true, getCurrentPartNumber: () => 0,
		_docLayer: { _viewId: 7, _selectedPart: 0 }, stateChangeHandler: { getItemValue: () => undefined },
		on() {}, off() {}, fire() {}, sendUnoCommand() {},
	};
	let persisted = { thousands: false, negativeRed: false, precision: 2, leadingZeroes: 1, natNum12: 0 };
	const formatStates = {};
	const sentCommands = [];
	let selectionKey = 'session:7:0:0:0:0:0';
	const panelFormat = {
		isActive: () => true, color: () => null, numberFormat: () => persisted,
		number: (command) => formatStates[command],
		checked: (command) => formatStates[command], state: (command) => formatStates[command],
		send: (command, args) => { sentCommands.push({ command, args }); return true; },
		mergeNumberFormat: (state, changes) => ({ ...state, ...changes }),
		setNumberFormat: (value) => { persisted = value; return true; },
		colorCss: () => 'transparent', fontColorCss: () => 'transparent',
	};
	let invalidateCount = 0;
	dom.window.ThisIsTheiOSApp = true;
	dom.window.app = { map };
	map.sendUnoCommand = (command, args) => sentCommands.push({ command, args });
	const sandbox = {
		window: dom.window, document: dom.window.document, NativeBridge: { getInstance: () => ({ getDocumentSessionId: () => 'session' }) },
		CalcEditorFormat: panelFormat, CalcFormatStateBridge: {
			observeSelection() {}, request() {}, invalidate() { invalidateCount++; }, selectionKey: () => selectionKey,
		},
		CalcTextFormatBridge: { observeSelection() {} }, MobileDocTheme: { applyFromMap() {} },
		WriterEditorSheet: class {
			constructor() { this.root = dom.window.document.createElement('div'); this.body = dom.window.document.createElement('div'); }
			setBody(node) { this.body.appendChild(node); }
			open() {} close() {}
		},
		WriterEditorInlineSubpage: class { constructor() {} clearSubpages() {} },
		WriterEditorController: { getInstance: () => ({}) },
		WriterEditorIcons: { get: () => '' },
		CalcEditorCatalog: { TABS: [], getFeatures: () => [] },
		globalThis: {},
	};
	vm.runInNewContext(transpile('../src/app/CalcEditorPanel.ts') + '\nglobalThis.CalcEditorPanel = CalcEditorPanel;', sandbox);
	const panel = new sandbox.globalThis.CalcEditorPanel();
	panel.subscribeDocumentState();
	panel.numberFormat = { ...persisted };
	panel.updateNumberFormat({ precision: 3 });
	panel.updateNumberFormat({ precision: 4 });
	panel.onDocumentState({ commandName: '.uno:Bold' });
	assert.strictEqual(panel.numberFormat.precision, 4, 'unrelated state events must retain queued number format edits');
	assert.strictEqual(panel.numberFormatDirty, true);
	const queuedTimer = panel.numberFormatTimer;
	selectionKey = 'session:7:0:1:0:1:0';
	panel.onSelectionChanged();
	assert.strictEqual(panel.numberFormatDirty, false, 'selection changes must discard unsent number format edits');
	assert.strictEqual(clock.timers.has(queuedTimer), false, 'selection changes must cancel the debounce write');
	assert.strictEqual(persisted.precision, 2, 'discarded edits must not be written into the next cell');
	panel.numberFormat = { ...persisted };
	panel.updateNumberFormat({ precision: 3 });
	const dispatchTimer = panel.numberFormatTimer;
	const invalidationsBeforeDispatch = invalidateCount;
	clock.run(dispatchTimer);
	assert.strictEqual(invalidateCount, invalidationsBeforeDispatch + 1,
		'NumberFormat dispatch invalidates any reads started during the debounce window');
	assert.strictEqual(panel.numberFormatPending, true);
	assert.strictEqual(persisted.precision, 3);
	panel.updateNumberFormat({ precision: 4 });
	panel.onDocumentState({ commandName: '.uno:NumberFormat' });
	assert.strictEqual(panel.numberFormat.precision, 4, 'an older write acknowledgement must preserve later queued edits');
	assert.strictEqual(panel.numberFormatDirty, true);
	const followupTimer = panel.numberFormatTimer;
	selectionKey = 'session:7:0:2:0:2:0';
	panel.onSelectionChanged();
	assert.strictEqual(clock.timers.has(followupTimer), false, 'selection changes cancel a queued follow-up write');
	assert.strictEqual(persisted.precision, 3, 'the queued follow-up must not apply to the next selection');
	panel.numberFormat = { ...persisted };
	panel.updateNumberFormat({ precision: 4 });
	const invalidationsBeforeCloseFlush = invalidateCount;
	panel.close();
	assert.strictEqual(invalidateCount, invalidationsBeforeCloseFlush + 1,
		'close-time NumberFormat flush also invalidates pre-write reads');
	assert.strictEqual(persisted.precision, 4, 'closing on the captured selection must flush the latest queued edit');
	assert.strictEqual(panel.numberFormatDirty, false);
	const panel2 = new sandbox.globalThis.CalcEditorPanel();
	panel2.subscribeDocumentState();
	panel2.numberFormat = { ...persisted };
	panel2.updateNumberFormat({ precision: 5 });
	selectionKey = 'session:7:0:3:0:3:0';
	panel2.close();
	assert.strictEqual(persisted.precision, 4, 'close must discard queued edits after the target selection changes');
	const panel3 = new sandbox.globalThis.CalcEditorPanel();
	panel3.subscribeDocumentState();
	panel3.numberFormat = { ...persisted };
	panel3.updateNumberFormat({ precision: 5 });
	const repeatedSelectionTimer = panel3.numberFormatTimer;
	panel3.onSelectionChanged();
	assert.strictEqual(panel3.numberFormatDirty, true,
		'repeated same-cell notifications must not discard queued edits');
	assert.strictEqual(panel3.numberFormatTimer, repeatedSelectionTimer);
	const liveSelectionTimer = panel3.numberFormatTimer;
	selectionKey = 'session:7:0:2:0:2:0';
	clock.run(liveSelectionTimer);
	assert.strictEqual(persisted.precision, 4,
		'debounced format writes must recheck the live selection even when no selection event fired');
	assert.strictEqual(panel3.numberFormatDirty, false);
	selectionKey = 'session:7:0:0:0:0:0';
	const readbackPanel = new sandbox.globalThis.CalcEditorPanel();
	readbackPanel.subscribeDocumentState();
	readbackPanel.numberFormat = { ...persisted };
	readbackPanel.updateNumberFormat({ precision: 5 });
	clock.run(readbackPanel.numberFormatTimer);
	assert.strictEqual(readbackPanel.numberFormatPending, true);
	readbackPanel.onDocumentState({ states: { '.uno:NumberFormat': '0,0,5,1,0' } });
	assert.strictEqual(readbackPanel.numberFormatPending, false,
		'a matching native NumberFormat state readback must finish the pending write');
	const closingPanel = new sandbox.globalThis.CalcEditorPanel();
	const closeOrder = [];
	closingPanel.close(() => closeOrder.push('first'));
	closingPanel.close(() => closeOrder.push('second'));
	assert.deepStrictEqual(closeOrder, [], 'close callbacks must wait until panel detach completes');
	clock.run(closingPanel.closeTimer);
	assert.deepStrictEqual(closeOrder, ['first', 'second'],
		'repeated close callbacks must all run after detach in order');
	const controlsPanel = new sandbox.globalThis.CalcEditorPanel();
	controlsPanel.subscribeDocumentState();
	persisted = { thousands: false, negativeRed: false, precision: 4, leadingZeroes: 2, natNum12: 0 };
	controlsPanel.rotation = 35900;
	formatStates['.uno:AlignmentRotation'] = 35900;
	formatStates['.uno:AlignmentStacked'] = true;
	const controlRows = dom.window.document.createElement('div');
	controlRows.appendChild(controlsPanel.createStepperPair('decimal_steppers'));
	controlRows.appendChild(controlsPanel.createStepperPair('indent_steppers'));
	controlRows.appendChild(controlsPanel.createCalcToggleCard({
		id: 'vertical-stack', label: '纵向排列', tab: 'default', icon: '', kind: 'toggle',
		unocmd: '.uno:AlignmentStacked',
	}));
	controlsPanel.content.appendChild(controlRows);
	controlsPanel.syncToggleStateFromDocument();
	controlsPanel.refreshStateControls();
	assert.strictEqual(controlsPanel.content.querySelector('[data-calc-value="precision"]').textContent, '4',
		'precision control displays the actual integer count');
	assert.strictEqual(controlsPanel.content.querySelector('[data-calc-value="leadingZeroes"]').textContent, '2',
		'leading-zero control displays the actual integer count');
	controlsPanel.content.querySelector('[data-calc-stepper="precision"][data-calc-delta="1"]').click();
	controlsPanel.content.querySelector('[data-calc-stepper="precision"][data-calc-delta="1"]').click();
	assert.strictEqual(controlsPanel.numberFormat.precision, 6, 'real plus-button clicks accumulate from the document value');
	clock.run(controlsPanel.numberFormatTimer);
	assert.strictEqual(persisted.precision, 6, 'real precision clicks dispatch the merged NumberFormat');
	const anglePlus = controlsPanel.content.querySelector('[data-calc-stepper="rotation"][data-calc-delta="1"]');
	const angleMinus = controlsPanel.content.querySelector('[data-calc-stepper="rotation"][data-calc-delta="-1"]');
	anglePlus.click();
	assert.strictEqual(controlsPanel.rotation, 0, 'one degree increment wraps across 360 degrees');
	assert.strictEqual(sentCommands.at(-1).command, '.uno:AlignmentRotation');
	assert.strictEqual(sentCommands.at(-1).args.AlignmentRotation.value, 0,
		'rotation increments dispatch exactly 100 hundredths for one degree');
	controlsPanel.onDocumentState({ commandName: '.uno:AlignmentRotation' });
	assert.strictEqual(controlsPanel.rotation, 0,
		'a pre-write UNO state callback cannot clear the optimistic rotation value');
	formatStates['.uno:AlignmentRotation'] = 35900;
	controlsPanel.onDocumentState({ states: { '.uno:AlignmentRotation': 35900 } });
	assert.strictEqual(controlsPanel.rotation, 0,
		'a read that arrives before the UNO queue catches up cannot roll back the optimistic angle');
	clock.run(controlsPanel.formatOverrideTimer);
	assert.strictEqual(controlsPanel.rotation, 35900,
		'after the optimistic window expires, a fresh readback restores the undone document value');
	formatStates['.uno:AlignmentRotation'] = 0;
	controlsPanel.onDocumentState({ states: { '.uno:AlignmentRotation': 0 } });
	angleMinus.click();
	assert.strictEqual(controlsPanel.rotation, 35900, 'one degree decrement wraps across zero');
	assert.strictEqual(sentCommands.at(-1).args.AlignmentRotation.value, 35900);
	const verticalToggle = controlsPanel.content.querySelector('[data-calc-toggle="vertical-stack"]');
	assert.strictEqual(verticalToggle.getAttribute('aria-pressed'), 'true');
	verticalToggle.click();
	assert.strictEqual(sentCommands.at(-1).command, '.uno:AlignmentStacked');
	assert.strictEqual(sentCommands.at(-1).args.AlignmentStacked.type, 'boolean');
	assert.strictEqual(sentCommands.at(-1).args.AlignmentStacked.value, false,
		'canceling vertical arrangement sends an explicit false value');
	formatStates['.uno:AlignmentStacked'] = true;
	controlsPanel.onDocumentState({ states: { '.uno:AlignmentStacked': true } });
	assert.strictEqual(controlsPanel.content.querySelector('[data-calc-toggle="vertical-stack"]').getAttribute('aria-pressed'), 'false',
		'a readback that arrives before the UNO queue catches up keeps the optimistic toggle');
	clock.run(controlsPanel.formatOverrideTimer);
	assert.strictEqual(controlsPanel.content.querySelector('[data-calc-toggle="vertical-stack"]').getAttribute('aria-pressed'), 'true',
		'after the optimistic window expires, the fresh undo readback restores the document value');
	const reopenedPanel = new sandbox.globalThis.CalcEditorPanel();
	const reopenOrder = [];
	reopenedPanel.close(() => reopenOrder.push('stale'));
	reopenedPanel.open();
	assert.deepStrictEqual(reopenOrder, [], 'reopening during dismissal cancels its delayed dispatch callback');
	reopenedPanel.close(() => reopenOrder.push('current'));
	clock.run(reopenedPanel.closeTimer);
	assert.deepStrictEqual(reopenOrder, ['current'], 'a later close must not run the cancelled callback');
	dom.window.close();
}

// The native state bridge accepts a synchronous response and rejects stale selection tuples.
{
	const dom = new JSDOM('<div id="addressInput"><input aria-label="A1:B2"></div>');
	const listeners = {};
	const map = {
		getDocType: () => 'spreadsheet', getCurrentPartNumber: () => 3,
		_docLayer: { _viewId: 11, _selectedPart: 3 },
		on: (name, callback) => { listeners[name] = callback; }, off() {}, fire() {},
	};
	dom.window.ThisIsTheiOSApp = true;
	dom.window.app = { map };
	dom.window.NativeBridge = { getInstance: () => ({ getDocumentSessionId: () => 'session-A' }) };
	const sandbox = { window: dom.window, document: dom.window.document, NativeBridge: dom.window.NativeBridge,
		CalcEditorFormat: { isActive: () => true }, globalThis: {} };
	vm.runInNewContext(transpile('../src/app/CalcFormatStateBridge.ts') + '\nglobalThis.CalcFormatStateBridge = CalcFormatStateBridge;', sandbox);
	const bridge = sandbox.globalThis.CalcFormatStateBridge;
	let request;
	dom.window.postMobileMessage = (message) => {
		request = JSON.parse(message.slice('CALC_FORMAT_STATE '.length));
		// Native NSDictionary serialization may reorder keys in range.
		const range = request.range;
		bridge.onResult({ ...request, range: {
			row2: range.row2, col2: range.col2, row1: range.row1, col1: range.col1, sheet: range.sheet,
		}, success: true, states: {
			'.uno:NumberFormat': '1,0,4,2,1', '.uno:AlignmentRotation': 150, '.uno:AlignmentStacked': false,
		} });
	};
	bridge.request();
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), 150,
		'synchronous native response with reordered range fields must be accepted');
	assert.strictEqual(bridge.state('.uno:NumberFormat'), '1,0,4,2,1',
		'native NumberFormat must remain its comma-separated string');
	assert.strictEqual(bridge.state('.uno:AlignmentStacked'), false,
		'native vertical arrangement state must preserve explicit false');

	dom.window.postMobileMessage = (message) => { request = JSON.parse(message.slice('CALC_FORMAT_STATE '.length)); };
	bridge.request();
	const superseded = { ...request, success: true, states: { '.uno:AlignmentRotation': 900 } };
	bridge.invalidate();
	bridge.onResult(superseded);
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), 150,
		'invalidation keeps the last known value while rejecting the pre-write response');
	bridge.request();
	bridge.onResult({ ...request, success: true, states: { '.uno:AlignmentRotation': 150 } });
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), 150,
		'a fresh post-write readback can publish the current value');

	dom.window.postMobileMessage = (message) => { request = JSON.parse(message.slice('CALC_FORMAT_STATE '.length)); };
	bridge.request();
	const addressInput = dom.window.document.querySelector('#addressInput input');
	addressInput.focus();
	listeners.celladdress({ address: 'B2' });
	assert.strictEqual(bridge.selectionKey(), 'session-A:11:3:1:1:1:1',
		'core celladdress events stay authoritative while the address input is focused');
	const stale = { ...request, success: true, states: { '.uno:AlignmentRotation': 900 } };
	bridge.onResult(stale);
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), 900,
		'reading the selection key must not cancel an in-flight state request');
	bridge.request();
	const nextValid = { ...request, success: true, states: { '.uno:AlignmentRotation': 900 } };
	for (const mutate of [
		(r) => { r.documentSessionId = 'session-old'; },
		(r) => { r.viewId++; },
		(r) => { r.part++; },
		(r) => { r.range = { ...r.range, col1: r.range.col1 + 1 }; },
	]) {
		const response = { ...nextValid, range: { ...nextValid.range } };
		mutate(response);
		bridge.onResult(response);
		assert.strictEqual(bridge.state('.uno:AlignmentRotation'), 900, 'stale state must not replace current selection state');
	}
	bridge.onResult(nextValid);
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), 900);
	dom.window.postMobileMessage = (message) => { request = JSON.parse(message.slice('CALC_FORMAT_STATE '.length)); };
	bridge.request();
	bridge.onResult({ ...request, success: 'true', states: { '.uno:AlignmentStacked': 'false' } });
	assert.strictEqual(bridge.state('.uno:AlignmentStacked'), undefined,
		'boolean-like strings and non-boolean success values are rejected as invalid native JSON shapes');
	dom.window.postMobileMessage = (message) => { request = JSON.parse(message.slice('CALC_FORMAT_STATE '.length)); };
	bridge.request();
	bridge.onResult({ ...request, success: true, states: { '.uno:AlignmentStacked': 'false' } });
	assert.strictEqual(bridge.state('.uno:AlignmentStacked'), undefined,
		'.uno:AlignmentStacked must arrive as a JSON boolean');
	// Matching metadata is insufficient if the live editor selection changed without an event.
	dom.window.document.querySelector('#addressInput input').setAttribute('aria-label', 'C3');
	dom.window.postMobileMessage = (message) => { request = JSON.parse(message.slice('CALC_FORMAT_STATE '.length)); };
	bridge.request();
	const currentRequest = request;
	dom.window.document.querySelector('#addressInput input').setAttribute('aria-label', 'D4');
	bridge.onResult({ ...currentRequest, success: true, states: { '.uno:AlignmentRotation': 300 } });
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), undefined,
		'a result must be discarded when the current selection no longer matches');
	dom.window.close();
}

// A missing native reply returns controls to an explicit unknown state.
{
	const dom = new JSDOM('<div id="addressInput"><input aria-label="A1"></div>');
	let nextTimer = 0;
	const timers = new Map();
	dom.window.setTimeout = (fn, delay) => { const id = ++nextTimer; timers.set(id, { fn, delay }); return id; };
	dom.window.clearTimeout = (id) => timers.delete(id);
	const events = [];
	const map = {
		getDocType: () => 'spreadsheet', getCurrentPartNumber: () => 0,
		_docLayer: { _viewId: 11, _selectedPart: 0 }, on() {}, off() {},
		fire: (name, event) => events.push({ name, event }),
	};
	dom.window.ThisIsTheiOSApp = true;
	dom.window.app = { map };
	dom.window.NativeBridge = { getInstance: () => ({ getDocumentSessionId: () => 'session-timeout' }) };
	const sandbox = { window: dom.window, document: dom.window.document, NativeBridge: dom.window.NativeBridge,
		CalcEditorFormat: { isActive: () => true }, globalThis: {} };
	vm.runInNewContext(transpile('../src/app/CalcFormatStateBridge.ts') + '\nglobalThis.CalcFormatStateBridge = CalcFormatStateBridge;', sandbox);
	const bridge = sandbox.globalThis.CalcFormatStateBridge;
	dom.window.postMobileMessage = () => {};
	bridge.request();
	const timeout = [...timers.entries()].find(([, timer]) => timer.delay === 2500);
	assert(timeout, 'bridge must install a bounded native response timeout');
	timers.delete(timeout[0]); timeout[1].fn();
	assert.strictEqual(bridge.state('.uno:AlignmentRotation'), undefined);
	assert.strictEqual(events.at(-1).event.error, 'timeout', 'timeout must be diagnosable and clear pending state');
	dom.window.close();
}

console.log('iOS Calc panel and bridge lifecycle tests passed');

// An in-flight pre-write reply must never roll back the real panel controls.
{
	const dom = new JSDOM('<div id="addressInput"><input aria-label="A1"></div>');
	const listeners = {};
	const clock = fakeClock(dom.window);
	const commands = [];
	const map = {
		getDocType: () => 'spreadsheet', getCurrentPartNumber: () => 0,
		_docLayer: { _viewId: 9, _selectedPart: 0 }, stateChangeHandler: { getItemValue: () => undefined },
		isEditMode: () => true, sendUnoCommand: (command, args) => commands.push({ command, args }),
		on: (name, callback) => { listeners[name] = callback; }, off() {},
		fire: (name, event) => listeners[name]?.(event),
	};
	dom.window.ThisIsTheiOSApp = true;
	dom.window.app = { map };
	const nativeBridge = { getInstance: () => ({ getDocumentSessionId: () => 'session-panel' }) };
	const sandbox = {
		window: dom.window, document: dom.window.document, NativeBridge: nativeBridge,
		WriterEditorSheet: class {
			constructor() { this.root = dom.window.document.createElement('div'); this.body = dom.window.document.createElement('div'); }
			setBody(node) { this.body.appendChild(node); }
			open() {} close() {}
		},
		WriterEditorInlineSubpage: class { constructor() {} clearSubpages() {} },
		WriterEditorController: { getInstance: () => ({}) }, WriterEditorIcons: { get: () => '' },
		CalcEditorCatalog: { TABS: [], getFeatures: () => [] },
		CalcTextFormatBridge: { observeSelection() {} }, MobileDocTheme: { applyFromMap() {} },
		globalThis: {},
	};
	const program = transpile('../src/app/CalcEditorFormat.ts') +
		'\nglobalThis.CalcEditorFormat = CalcEditorFormat;' +
		transpile('../src/app/CalcFormatStateBridge.ts') +
		'\nglobalThis.CalcFormatStateBridge = CalcFormatStateBridge;' +
		transpile('../src/app/CalcEditorPanel.ts') +
		'\nglobalThis.CalcEditorPanel = CalcEditorPanel;';
	vm.runInNewContext(program, sandbox);
	const bridge = sandbox.globalThis.CalcFormatStateBridge;
	const panel = new sandbox.globalThis.CalcEditorPanel();
	panel.subscribeDocumentState();
	const rotationControls = panel.createStepperPair('indent_steppers');
	panel.content.appendChild(rotationControls);
	dom.window.postMobileMessage = () => {};
	bridge.request();
	let oldRequest = bridge.pending;
	bridge.onResult({ ...oldRequest, success: true, states: { '.uno:AlignmentRotation': 35900 } });
	assert.strictEqual(panel.rotation, 35900);
	bridge.request();
	oldRequest = bridge.pending;
	panel.content.querySelector('[data-calc-stepper="rotation"][data-calc-delta="1"]').click();
	assert.strictEqual(panel.rotation, 0);
	bridge.onResult({ ...oldRequest, success: true, states: { '.uno:AlignmentRotation': 35900 } });
	assert.strictEqual(panel.rotation, 0,
		'a pre-write state response cannot roll the optimistic angle back after a real control click');
	const readbackTimer = [...clock.timers.entries()].find(([, timer]) => timer.delay === 250);
	assert(readbackTimer, 'the write must schedule a fresh post-command state read');
	clock.timers.delete(readbackTimer[0]); readbackTimer[1].fn();
	let freshRequest = bridge.pending;
	bridge.onResult({ ...freshRequest, success: true, states: { '.uno:AlignmentRotation': 35900 } });
	assert.strictEqual(panel.rotation, 0,
		'a post-command read that beats the UNO write cannot roll the panel back');
	bridge.request();
	freshRequest = bridge.pending;
	bridge.onResult({ ...freshRequest, success: true, states: { '.uno:AlignmentRotation': 0 } });
	assert.strictEqual(panel.rotation, 0, 'the post-command state read reconciles the real panel');
	bridge.request();
	freshRequest = bridge.pending;
	bridge.onResult({ ...freshRequest, success: true, states: { '.uno:AlignmentRotation': 35900 } });
	assert.strictEqual(panel.rotation, 35900, 'an undo readback updates the real panel state');
	dom.window.close();
}

console.log('iOS Calc native format-to-panel integration tests passed');
