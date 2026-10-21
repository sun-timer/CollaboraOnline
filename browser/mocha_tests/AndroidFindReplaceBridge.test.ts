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
			fire(name: string, event: any) { if (name === 'writersearchresult') statuses.push(event); if (name === 'commandresult' && listeners[name]) listeners[name](event); },
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
			// Core's real LOK callback omits the boolean value for a successful match.
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: {type: 'boolean'}});
			assert.equal(findCommands().length, 2);
			assert.equal(selectionMenuHides, hidesBeforeNextDispatch + 1, 'each queued dispatch refreshes selection-menu suppression');
			const secondSearch = findCommands()[1];
			assert.equal('SearchItem.SearchStartPointX' in secondSearch, false);
			assert.equal(statuses[0].status, 'found');
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: false, result: {type: 'void'}});
			assert.equal(statuses[1].status, 'notFound', 'Core reports an ordinary no-match as unsuccessful void');
			assert.equal(findCommands().length, 3, 'failed callbacks also release the queue');
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: false});
			assert.equal(statuses[2].status, 'notFound');
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
			assert.equal(statuses.length, 4, 'old-generation result cannot change current status');
			assert.equal(findCommands().length, 6);
			assert.equal(findCommands()[5]['SearchItem.SearchString'].value, 'beta');
			assert.equal('SearchItem.SearchStartPointX' in findCommands()[5], true);
			AndroidFindReplaceBridge.cancelPending();
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: false});
			assert.equal(statuses.length, 4, 'closing the sheet invalidates its in-flight result');

			const timers: Array<() => void> = [];
			(window as any).setTimeout = (callback: () => void) => { timers.push(callback); return timers.length; };
			AndroidFindReplaceBridge.find('slow');
			timers[timers.length - 1](); // report timeout while keeping the command in flight
			const beforeDelayedResult = findCommands().length;
			AndroidFindReplaceBridge.find('after-timeout');
			assert.equal(findCommands().length, beforeDelayedResult, 'timeout does not start a request that could steal a late completion');
			map.fire('commandresult', {commandName: '.uno:ExecuteSearch', success: true, result: false});
			assert.equal(findCommands().length, beforeDelayedResult + 1);
			assert.equal(findCommands()[beforeDelayedResult]['SearchItem.SearchString'].value, 'after-timeout');
			assert.equal(statuses.length, 5, 'timed-out completion does not report a result for the new query');
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
});
