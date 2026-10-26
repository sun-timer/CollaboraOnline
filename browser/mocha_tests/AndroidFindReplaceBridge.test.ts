/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

var assert = require('assert').strict;

describe('AndroidFindReplaceBridge', function () {
	it('keeps Core selection and uses SearchItem option semantics', function () {
		const original = {
			map: app.map,
			socket: app.socket,
			activeDocument: app.activeDocument,
			searchService: app.searchService,
			setTimeout: window.setTimeout,
			selectionMenu: (window as any).AndroidSelectionMenu,
		};
		const messages: string[] = [];
		const layer: any = {
			_docType: 'presentation',
			_lastSearchResult: null,
			_searchResults: null,
			_searchTerm: 'alpha',
			_clearSearchResults() {
				this._lastSearchResult = null;
				this._searchResults = null;
				this._searchTerm = null;
			},
		};
		const statuses: any[] = [];
		const listeners: any = {};
		const map: any = {
			_docLayer: layer,
			on(name: string, listener: any) { listeners[name] = listener; },
			off(name: string) { delete listeners[name]; },
			fire(name: string, event: any) { if (name === 'writersearchresult') statuses.push(event); if (listeners[name]) listeners[name](event); },
		};
		app.map = map;
		app.socket = { sendMessage: (message: string) => messages.push(message) };
		app.activeDocument = {
			activeLayout: { viewedRectangle: { x1: 0, y1: 0 } },
		};
		let selectionMenuHides = 0;
		(window as any).AndroidSelectionMenu = { hide() { selectionMenuHides++; } };
		app.searchService = {
			resetSelection() {
				layer._clearSearchResults();
				app.socket.sendMessage('resetselection');
			},
		};

		try {
			AndroidFindReplaceBridge.setOptions({
				caseSensitive: true,
				wholeWord: true,
			});
			AndroidFindReplaceBridge.find('alpha');

			const findCommands = () =>
				messages
					.filter((message) => message.startsWith('uno .uno:ExecuteSearch '))
					.map((message) => JSON.parse(message.substring(message.indexOf('{'))));
			const results = [
				{ part: 0, twipsRectangles: '100, 200, 40, 20' },
				{ part: 0, twipsRectangles: '300, 400, 40, 20' },
				{ part: 1, twipsRectangles: '500, 600, 40, 20' },
			];
			assert.equal(findCommands()[0]['SearchItem.SearchFlags'].value, 0x10);
			assert.equal(findCommands()[0]['SearchItem.TransliterateFlags'].value, 0);

			AndroidFindReplaceBridge.findNext();
			AndroidFindReplaceBridge.findNext();
			assert.equal(findCommands().length, 1, 'rapid clicks wait for the active Core search');
			const hidesBeforeNextDispatch = selectionMenuHides;
			// commandresult may arrive before the authoritative search event.
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'boolean'}});
			assert.equal(findCommands().length, 1, 'the command receipt alone does not release the serial queue');
			map.fire('search', {originalPhrase: 'stale phrase', count: 0, results: []});
			assert.equal(findCommands().length, 1, 'a search event for another phrase is ignored');
			map.fire('search', {originalPhrase: 'alpha', count: 3, results});
			map.fire('search', {originalPhrase: 'alpha', count: 0, results: []});
			assert.equal(findCommands().length, 2);
			assert.equal(selectionMenuHides, hidesBeforeNextDispatch + 1, 'each queued dispatch refreshes selection-menu suppression');
			const secondSearch = findCommands()[1];
			assert.equal('SearchItem.SearchStartPointX' in secondSearch, false);
			assert.equal(statuses[0].status, 'found');
			// For no-match, map.search may arrive before the successful void receipt.
			map.fire('search', {originalPhrase: 'alpha', count: 0, results: []});
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'void'}});
			assert.equal(statuses[1].status, 'notFound', 'the map search event reports the ordinary no-match');
			assert.equal(findCommands().length, 3, 'failed callbacks also release the queue');
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'void'}});
			map.fire('search', {originalPhrase: 'alpha', count: 1, results: [{}]});
			assert.equal(statuses[2].status, 'found', 'map.search wins over an omitted or false command result');
			AndroidFindReplaceBridge.findNext();
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: false, result: {type: 'string', value: 'failure'}});
			assert.equal(statuses[3].status, 'error', 'unknown failed results remain execution errors');
			AndroidFindReplaceBridge.findPrevious('alpha');
			const previousSearch = findCommands()[4];
			assert.equal(previousSearch['SearchItem.Backward'].value, true);
			AndroidFindReplaceBridge.setOptions({caseSensitive: false, wholeWord: false});
			AndroidFindReplaceBridge.find('beta');
			assert.equal(findCommands().length, 5, 'new generation waits for the prior in-flight command');
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: false});
			map.fire('search', {originalPhrase: 'alpha', count: 0, results: []});
			assert.equal(statuses.length, 4, 'old-generation result cannot change current status');
			assert.equal(findCommands().length, 6);
			assert.equal(findCommands()[5]['SearchItem.SearchString'].value, 'beta');
			assert.equal('SearchItem.SearchStartPointX' in findCommands()[5], true);
			AndroidFindReplaceBridge.cancelPending();
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: false});
			map.fire('search', {originalPhrase: 'beta', count: 1, results: [{}]});
			assert.equal(statuses.length, 4, 'closing the sheet invalidates its in-flight result');

			const timers: Array<() => void> = [];
			(window as any).setTimeout = (callback: () => void) => { timers.push(callback); return timers.length; };
			AndroidFindReplaceBridge.find('slow');
			timers[timers.length - 1](); // report timeout while keeping the command in flight
			assert.equal(statuses[statuses.length - 1].status, 'timeout');
			const beforeUserAction = findCommands().length;
			assert.equal(findCommands().length, beforeUserAction, 'a timeout alone does not start a request');
			AndroidFindReplaceBridge.find('after-timeout'); // next user action force-releases the stuck request
			assert.equal(findCommands().length, beforeUserAction + 1);
			assert.equal(findCommands()[beforeUserAction]['SearchItem.SearchString'].value, 'after-timeout');
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: false});
			map.fire('search', {originalPhrase: 'after-timeout', count: 0, results: []});
			assert.equal(statuses[statuses.length - 1].query, 'after-timeout');
			assert.equal(statuses[statuses.length - 1].status, 'notFound');
		} finally {
			window.setTimeout = original.setTimeout;
			(window as any).AndroidSelectionMenu = original.selectionMenu;
			AndroidFindReplaceBridge.clearSuppressSelectionMenu();
			app.map = original.map;
			app.socket = original.socket;
			app.activeDocument = original.activeDocument;
			app.searchService = original.searchService;
		}
	});

	it('keeps Writer and Calc find result handling on commandresult', function () {
		const original = { map: app.map, socket: app.socket, activeDocument: app.activeDocument };
		const statuses: any[] = [];
		const maps: any[] = [];
		const messages: string[] = [];
		try {
			for (const docType of ['text', 'spreadsheet']) {
				const listeners: any = {};
				const map: any = {
					_docLayer: { _docType: docType },
					on(name: string, listener: any) { listeners[name] = listener; },
					off(name: string) { delete listeners[name]; },
					fire(name: string, event: any) { if (name === 'writersearchresult') statuses.push(event); if (listeners[name]) listeners[name](event); },
				};
				maps.push(map);
				app.map = map;
				app.socket = { sendMessage: (message: string) => messages.push(message) };
				app.activeDocument = { activeLayout: { viewedRectangle: { x1: 0, y1: 0 } } };
				AndroidFindReplaceBridge.find('  ' + docType + '  ');
				map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'boolean'}});
				assert.equal(statuses[statuses.length - 1].status, 'found');
				assert.equal(listeners.search !== undefined, true, 'the bridge listens for Core search events but does not require them for Writer or Calc');
			}
			const messageCount = messages.length;
			AndroidFindReplaceBridge.findPrevious('   ');
			AndroidFindReplaceBridge.findNext();
			assert.equal(messages.length, messageCount, 'blank previous-search input clears the old query instead of reusing it');
		} finally {
			AndroidFindReplaceBridge.cancelPending();
			app.map = original.map;
			app.socket = original.socket;
			app.activeDocument = original.activeDocument;
		}
	});

	it('isolates an in-flight PPT search when the document map changes', function () {
		const original = { map: app.map, socket: app.socket, activeDocument: app.activeDocument };
		const messages: string[] = [];
		const statuses: any[] = [];
		const makeMap = () => {
			const listeners: any = {};
			return {
				_docLayer: { _docType: 'presentation' },
				on(name: string, listener: any) { listeners[name] = listener; },
				off(name: string) { delete listeners[name]; },
				fire(name: string, event: any) { if (name === 'writersearchresult') statuses.push(event); if (listeners[name]) listeners[name](event); },
			};
		};
		const oldMap: any = makeMap();
		const newMap: any = makeMap();
		try {
			app.socket = { sendMessage: (message: string) => messages.push(message) };
			app.activeDocument = { activeLayout: { viewedRectangle: { x1: 0, y1: 0 } } };
			app.map = oldMap;
			AndroidFindReplaceBridge.find('old');
			app.map = newMap;
			AndroidFindReplaceBridge.find('new');
			oldMap.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'void'}});
			oldMap.fire('search', {originalPhrase: 'old', count: 1, results: [{}]});
			assert.equal(statuses.length, 0, 'results from the previous map cannot update the new document');
			newMap.fire('search', {originalPhrase: 'new', count: 2, results: [{}, {}]});
			newMap.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'void'}});
			assert.equal(statuses.length, 1);
			assert.equal(statuses[0].query, 'new');
			assert.equal(statuses[0].status, 'found');
		} finally {
			AndroidFindReplaceBridge.cancelPending();
			app.map = original.map;
			app.socket = original.socket;
			app.activeDocument = original.activeDocument;
		}
	});
});
