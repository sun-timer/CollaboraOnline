/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterFindReplaceDialog', function () {
	it('uses find-only title in preview mode', function () {
		assert.equal(WriterFindReplaceDialog.titleForReplaceEnabled(false), '查找');
		assert.equal(WriterFindReplaceDialog.titleForReplaceEnabled(true), '查找替换');
	});

	it('parses replaceEnabled open option', function () {
		assert.equal(WriterFindReplaceDialog.isReplaceEnabledOption(undefined), false);
		assert.equal(WriterFindReplaceDialog.isReplaceEnabledOption({}), false);
		assert.equal(
			WriterFindReplaceDialog.isReplaceEnabledOption({ replaceEnabled: false }),
			false,
		);
		assert.equal(
			WriterFindReplaceDialog.isReplaceEnabledOption({ replaceEnabled: true }),
			true,
		);
	});
});

describe('PPT search bridge and result UI', function () {
	let previousWindow: any;
	let previousDocument: any;
	let previousApp: any;
	let dom: any;
	let messages: string[];
	let cleared: number;
	let searchHandlers: Array<(event: any) => void>;
	let commandListeners: Array<(event: any) => void>;
	let dialog: WriterFindReplaceDialog | null;

	beforeEach(function () {
		const JSDOM = require('jsdom').JSDOM;
		dom = new JSDOM('<!doctype html><html><body></body></html>', { pretendToBeVisual: true });
		previousWindow = (global as any).window;
		previousDocument = (global as any).document;
		previousApp = app;
		(global as any).window = dom.window;
		(global as any).document = dom.window.document;
		messages = [];
		cleared = 0;
		searchHandlers = [];
		commandListeners = [];
		dialog = null;
		const layer: any = {
			_clearSearchResults: () => {
				cleared++;
				layer._searchResults = null;
				layer._lastSearchResult = null;
			},
		};
		app = {
			map: {
				_docLayer: layer,
				isEditMode: () => true,
				on: (name: string, fn: (event: any) => void) => {
					if (name === 'writersearchresult') searchHandlers.push(fn);
					if (name === 'commandresult') commandListeners.push(fn);
				},
				off: (name: string, fn: (event: any) => void) => {
					if (name === 'writersearchresult') searchHandlers = searchHandlers.filter((item) => item !== fn);
					if (name === 'commandresult') commandListeners = commandListeners.filter((item) => item !== fn);
				},
				fire: (name: string, event: any) => {
					if (name === 'writersearchresult') searchHandlers.slice().forEach((fn) => fn(event));
					if (name === 'commandresult') commandListeners.slice().forEach((fn) => fn(event));
				},
			},
			socket: { sendMessage: (message: string) => messages.push(message) },
			activeDocument: { activeLayout: { viewedRectangle: { x1: 120, y1: 240 } } },
			searchService: { resetSelection: () => undefined },
		};
		(dom.window as any).app = app;
		(dom.window as any).AndroidFindReplaceBridge = AndroidFindReplaceBridge;
		AndroidFindReplaceBridge.setOptions({});
		cleared = 0;
	});

	afterEach(function () {
		dialog?.close();
		AndroidFindReplaceBridge.clearSuppressSelectionMenu();
		dom.window.close();
		app = previousApp;
		(global as any).window = previousWindow;
		(global as any).document = previousDocument;
	});

	// Simulates the Core answering the in-flight ExecuteSearch so the bridge queue can advance.
	function answerSearch(found = true): void {
		app.map.fire('commandresult', { commandName: '.uno:ExecuteSearch', success: true, result: { type: 'boolean', value: found } });
	}

	function payload(): any {
		const message = messages[messages.length - 1];
		assert.ok(message.startsWith('uno .uno:ExecuteSearch '));
		return JSON.parse(message.slice('uno .uno:ExecuteSearch '.length));
	}

	function openSearch(): any {
		dialog = new WriterFindReplaceDialog(true);
		dialog.open();
		return dialog as any;
	}

	it('sends case matching via transliteration and whole-word via SearchFlags on the actual bridge', function () {
		[false, true].forEach((caseSensitive) => [false, true].forEach((wholeWord) => {
			AndroidFindReplaceBridge.setOptions({ caseSensitive, wholeWord });
			AndroidFindReplaceBridge.find('Aa');
			const command = payload();
			assert.equal(command['SearchItem.TransliterateFlags'].value, caseSensitive ? 0 : 0x100);
			assert.equal(command['SearchItem.SearchFlags'].value, wholeWord ? 16 : 0);
			assert.equal(command['SearchItem.SearchStartPointX'].value, 120);
			assert.equal(command['SearchItem.SearchStartPointY'].value, 240);
			answerSearch();
		}));
	});

	it('clears stale cached matches only when a matching option changes', function () {
		AndroidFindReplaceBridge.setOptions({});
		assert.equal(cleared, 0);
		app.map._docLayer._searchResults = [{ match: 'aa' }];
		app.map._docLayer._lastSearchResult = { twipsRectangles: '1,2,3,4' };
		AndroidFindReplaceBridge.setOptions({ caseSensitive: true });
		assert.equal(cleared, 1);
		assert.equal(app.map._docLayer._searchResults, null);
		assert.equal(app.map._docLayer._lastSearchResult, null);
		AndroidFindReplaceBridge.setOptions({ caseSensitive: true });
		assert.equal(cleared, 1);
		AndroidFindReplaceBridge.setOptions({ caseSensitive: true, wholeWord: true });
		assert.equal(cleared, 2);
	});

	it('preserves matching flags for previous, replace and replace-all commands', function () {
		AndroidFindReplaceBridge.setOptions({ caseSensitive: true, wholeWord: true });
		AndroidFindReplaceBridge.find('Aa');
		answerSearch();
		AndroidFindReplaceBridge.findPrevious();
		assert.equal(payload()['SearchItem.Backward'].value, true);
		answerSearch();
		AndroidFindReplaceBridge.replaceForQuery('Aa', 'Bb', false);
		assert.equal(payload()['SearchItem.Command'].value, 2);
		answerSearch();
		AndroidFindReplaceBridge.replaceForQuery('Aa', 'Cc', true);
		const command = payload();
		assert.equal(command['SearchItem.Command'].value, 3);
		assert.equal(command['SearchItem.ReplaceString'].value, 'Cc');
		assert.equal(command['SearchItem.TransliterateFlags'].value, 0);
		assert.equal(command['SearchItem.SearchFlags'].value, 16);
	});

	it('shows no results for the current query and clears it when matches arrive', function () {
		const view = openSearch();
		view.findQueryInput.value = 'Aa';
		const status = dom.window.document.querySelector('.writer-find-sheet__status');
		assert.equal(status.style.display, 'none');
		app.map.fire('writersearchresult', { query: 'Aa', status: 'notFound' });
		assert.equal(status.style.display, '');
		assert.equal(status.textContent, '未查到到关键词');
		app.map.fire('writersearchresult', { query: 'Aa', status: 'found' });
		assert.equal(status.style.display, 'none');
		assert.equal(status.textContent, '');
	});

	it('ignores delayed results for an older query and detaches on close', function () {
		const view = openSearch();
		view.findQueryInput.value = 'current';
		app.map.fire('writersearchresult', { query: 'old', status: 'notFound' });
		assert.equal(dom.window.document.querySelector('.writer-find-sheet__status').style.display, 'none');
		assert.equal(searchHandlers.length, 1);
		dialog?.close();
		assert.equal(searchHandlers.length, 0);
	});

	it('hides old no-results status and clears cached matches when changing case options', function () {
		const view = openSearch();
		view.findQueryInput.value = 'Aa';
		app.map.fire('writersearchresult', { query: 'Aa', status: 'notFound' });
		const status = dom.window.document.querySelector('.writer-find-sheet__status');
		assert.equal(status.style.display, '');
		const toggle = dom.window.document.querySelector('input[type="checkbox"]');
		toggle.checked = true;
		toggle.dispatchEvent(new dom.window.Event('change'));
		assert.equal(cleared, 1);
		assert.equal(status.style.display, 'none');
		view.runFindNext(view.findQueryInput);
		assert.equal(payload()['SearchItem.TransliterateFlags'].value, 0);
	});

	it('uses the currently edited query for previous-result navigation', function () {
		const view = openSearch();
		view.findQueryInput.value = 'old';
		view.runFindNext(view.findQueryInput);
		answerSearch();
		view.findQueryInput.value = 'new';
		view.findQueryInput.dispatchEvent(new dom.window.Event('input'));
		view.runFindPrevious();
		assert.equal(payload()['SearchItem.SearchString'].value, 'new');
		assert.equal(payload()['SearchItem.Backward'].value, true);
	});
});
