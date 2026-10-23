const assert = require('node:assert/strict');
const IOSCalcCsvImport = require('../src/app/IOSCalcCsvImport.js');

let now;
let timers;
let current;

function setup({ ios = true, loaded = false, url = 'file:///tmp/import.csv', docType = 'spreadsheet', noLayer = false } = {}) {
	IOSCalcCsvImport.resetForTests();
	timers = new Map();
	let nextTimer = 1;
	global.ThisIsTheiOSApp = ios;
	global.mode = { isMobile: () => true };
	const map = {
		_docLoaded: loaded,
		_docLayer: noLayer ? undefined : { _docType: docType },
		options: { doc: url },
		events: [],
		fire(name, event) { this.events.push({ name, event }); },
	};
	const socket = { messages: [], sendMessage(message) { this.messages.push(message); } };
	global.app = { map, socket };
	global.setTimeout = (fn, delay) => {
		const id = nextTimer++;
		timers.set(id, { fn, delay });
		return id;
	};
	global.clearTimeout = (id) => timers.delete(id);
	current = { map, socket };
	return current;
}

function dialog(id = 17, { checked = true, selectedIndex = 0, dialogid = 'TextImportCsvDialog' } = {}) {
	return {
		id,
		dialogid,
		responses: [{ id: 'ok', response: 1 }, { id: 'cancel', response: 0 }],
		children: [
			{ id: 'todetectseparator', type: 'radiobutton', checked },
			{ id: 'charset', type: 'combobox', selectedEntries: [selectedIndex], entries: ['UTF-8', 'Western', 'System', '- Automatic -'] },
		],
	};
}

function callbackLog() {
	const calls = [];
	return {
		calls,
		callback(a, b, c, d, e) { calls.push([a, b, c, d, e]); },
	};
}

function update(id = 17, { separatorChecked = true, selectedIndex = 3 } = {}) {
	return {
		id,
		dialogid: 'TextImportCsvDialog',
		action: 'update',
		children: [
			{ id: 'todetectseparator', type: 'radiobutton', checked: separatorChecked },
			{ id: 'charset', type: 'combobox', selectedEntries: [selectedIndex], entries: ['UTF-8', 'Western', 'System', '- Automatic -'] },
		],
	};
}

function test(name, fn) {
	fn();
	console.log(`ok - ${name}`);
}

test('initial dialog selects the core automatic encoding and waits for its update', () => {
	setup();
	const log = callbackLog();
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(), log.callback), true);
	assert.equal(log.calls.length, 1);
	assert.deepEqual(log.calls[0].slice(0, 4), ['combobox', 'selected', { id: 'charset' }, '3;- Automatic -']);
	assert.equal(IOSCalcCsvImport.handleMessage(update(), log.callback), true);
	assert.equal(log.calls.length, 2);
	assert.deepEqual(log.calls[1].slice(0, 4), ['responsebutton', 'click', { id: 'ok' }, 1]);
});

test('initial core auto settings skip redundant control changes and confirm once', () => {
	setup();
	const log = callbackLog();
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(24, { selectedIndex: 3 }), log.callback), true);
	assert.equal(log.calls.filter((call) => call[0] === 'combobox').length, 0);
	assert.equal(log.calls.filter((call) => call[0] === 'responsebutton').length, 1);
	IOSCalcCsvImport.handleMessage(update(24), log.callback);
	assert.equal(log.calls.filter((call) => call[0] === 'responsebutton').length, 1);
});

test('missing callback uses the existing JSDialog socket event protocol', () => {
	const { socket } = setup();
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(25, { selectedIndex: 3 }), undefined), true);
	assert.deepEqual(socket.messages, [
		'dialogevent 25 {"id":"ok","cmd":"click","data":"1","type":"responsebutton"}',
	]);
	assert.equal(global._firstDialogHandled, true);
});

test('separator selection precedes charset selection when core did not enable detection', () => {
	setup();
	const log = callbackLog();
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(18, { checked: false }), log.callback), true);
	assert.deepEqual(log.calls[0].slice(0, 4), ['radiobutton', 'change', { id: 'todetectseparator' }, true]);
	IOSCalcCsvImport.handleMessage({ id: 18, dialogid: 'TextImportCsvDialog', action: 'update', control: { id: 'drawingarea', type: 'drawingarea' } }, log.callback);
	assert.equal(log.calls.length, 1, 'an unrelated single-control update must not advance the flow');
	IOSCalcCsvImport.handleMessage({ id: 18, dialogid: 'TextImportCsvDialog', action: 'update', control: { id: 'todetectseparator', type: 'radiobutton', checked: true } }, log.callback);
	assert.deepEqual(log.calls[1].slice(0, 4), ['combobox', 'selected', { id: 'charset' }, '3;- Automatic -']);
	IOSCalcCsvImport.handleMessage({ id: 18, dialogid: 'TextImportCsvDialog', action: 'update', control: { id: 'charset', type: 'combobox', selectedEntries: [3], entries: ['UTF-8', 'Western', 'System', '- Automatic -'] } }, log.callback);
	assert.equal(log.calls.filter((call) => call[0] === 'responsebutton').length, 1);
});

test('serialized encoding ids take precedence over the final entry fallback', () => {
	const charset = {
		entries: [{ id: '0', text: 'UTF-8' }, { id: '32768', text: 'Automatic' }],
	};
	assert.deepEqual(IOSCalcCsvImport._automaticEncodingSelection(charset), { position: 1, text: 'Automatic' });
});

test('duplicate full payloads and updates confirm a window only once', () => {
	setup();
	const log = callbackLog();
	const full = dialog();
	IOSCalcCsvImport.handleMessage(full, log.callback);
	IOSCalcCsvImport.handleMessage(full, log.callback);
	IOSCalcCsvImport.handleMessage(update(), log.callback);
	IOSCalcCsvImport.handleMessage(full, log.callback);
	assert.equal(log.calls.filter((call) => call[0] === 'responsebutton').length, 1);
	assert.equal(log.calls.filter((call) => call[0] === 'combobox').length, 1);
});

test('close clears a pending window', () => {
	setup();
	const log = callbackLog();
	IOSCalcCsvImport.handleMessage(dialog(), log.callback);
	assert.equal(IOSCalcCsvImport.handleMessage({ id: 17, dialogid: 'TextImportCsvDialog', action: 'close' }, log.callback), true);
	assert.equal(IOSCalcCsvImport.pending, null);
});

test('same dialog id and window id pass through for Text to Columns after CSV load', () => {
	const { map } = setup();
	const log = callbackLog();
	IOSCalcCsvImport.handleMessage(dialog(27, { selectedIndex: 3 }), log.callback);
	assert.equal(IOSCalcCsvImport.handleMessage({ id: 27, dialogid: 'TextImportCsvDialog', action: 'close' }, log.callback), true);
	map._docLoaded = true;
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(27), log.callback), false);
});

test('missing controls leave the initial dialog to the normal router', () => {
	const { map } = setup();
	const log = callbackLog();
	const broken = dialog();
	broken.children = [];
	assert.equal(IOSCalcCsvImport.handleMessage(broken, log.callback), false);
	assert.equal(map.events.length, 0);
});

test('timeout releases the intercepted dialog back to the mobile wizard', () => {
	const { map } = setup();
	const log = callbackLog();
	IOSCalcCsvImport.handleMessage(dialog(19, { checked: false }), log.callback);
	const [id, timer] = [...timers.entries()][0];
	assert.equal(timer.delay, 5000);
	timers.delete(id);
	timer.fn();
	assert.equal(IOSCalcCsvImport.pending, null);
	assert.equal(map.events.at(-1).name, 'mobilewizard');
});

test('a missing close after confirmation releases an unloaded document safely', () => {
	const { map } = setup();
	const log = callbackLog();
	IOSCalcCsvImport.handleMessage(dialog(26, { selectedIndex: 3 }), log.callback);
	const [id, timer] = [...timers.entries()][0];
	timers.delete(id);
	timer.fn();
	assert.equal(IOSCalcCsvImport.pending, null);
	assert.equal(map.events.at(-1).name, 'mobilewizard');
});

test('session changes reset deduplication and abandon stale windows', () => {
	const { map } = setup();
	const log = callbackLog();
	IOSCalcCsvImport.handleMessage(dialog(20, { checked: false }), log.callback);
	map.options.doc = 'file:///tmp/next.csv';
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(21), log.callback), true);
	assert.equal(IOSCalcCsvImport.pending.windowId, 21);
});

test('the dialog may arrive before the Calc layer, and updates survive layer creation', () => {
	const { map } = setup({ noLayer: true });
	const log = callbackLog();
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(23), log.callback), true);
	map._docLayer = { _docType: 'spreadsheet' };
	map._docLoaded = true;
	assert.equal(IOSCalcCsvImport.handleMessage(update(23), log.callback), true);
	assert.equal(log.calls.filter((call) => call[0] === 'responsebutton').length, 1);
});

test('Android, non-CSV, loaded documents, and Text to Columns pass through', () => {
	for (const options of [
		{ ios: false },
		{ url: 'file:///tmp/book.ods' },
		{ loaded: true },
		{ docType: 'text' },
	]) {
		setup(options);
		assert.equal(IOSCalcCsvImport.handleMessage(dialog(), callbackLog().callback), false);
	}
	setup();
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(22), callbackLog().callback), true);
	assert.equal(IOSCalcCsvImport.handleMessage({ id: 22, dialogid: 'TextImportCsvDialog', action: 'close' }, callbackLog().callback), true);
	global.app.map._docLoaded = true;
	assert.equal(IOSCalcCsvImport.handleMessage(dialog(22), callbackLog().callback), false);
});

console.log('IOSCalcCsvImport regression tests passed');
