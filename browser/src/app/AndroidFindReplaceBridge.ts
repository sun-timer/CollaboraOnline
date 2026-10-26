/*
 * Android native find/replace sheet bridge.
 * Wraps ExecuteSearch without using the web MobileSearchBar UI.
 */

interface AndroidFindReplaceOptions {
	caseSensitive?: boolean;
	wholeWord?: boolean;
}

class AndroidFindReplaceBridge {
	private static searchFlags = 0;
	private static lastQuery = '';
	private static lastReplace = '';
	private static caseSensitive = false;
	private static pendingResults: Array<{ query: string; generation: number; command: number; backward: boolean; replace: string; timedOut?: boolean }> = [];
	private static inFlight = false;
	private static inFlightRequest: { query: string; generation: number; command: number; backward: boolean; replace: string; timedOut?: boolean } | null = null;
	private static inFlightSearchResult: any = null;
	private static inFlightCommandResult: any = null;
	private static inFlightMap: any = null;
	private static listenedMap: any = null;
	private static commandResultListener: ((event: any) => void) | null = null;
	private static searchResultListener: ((event: any) => void) | null = null;
	private static resultTimeout = 0;
	private static scopeMap: any = null;
	private static generation = 0;
	private static searchStarted = false;
	private static suppressSelectionMenu = false;
	private static suppressSelectionMenuTimer = 0;
	private static readonly suppressSelectionMenuTimeoutMs = 5000;

	private static readonly CMD_FIND = 0;
	private static readonly CMD_REPLACE = 2;
	private static readonly CMD_REPLACE_ALL = 3;

	private static readonly FLAG_WHOLE_WORD = 0x00000010;

	static setOptions(options: AndroidFindReplaceOptions): void {
		let flags = 0;
		if (options.wholeWord) {
			flags |= AndroidFindReplaceBridge.FLAG_WHOLE_WORD;
		}
		const caseSensitive = !!options.caseSensitive;
		if (flags === AndroidFindReplaceBridge.searchFlags && caseSensitive === AndroidFindReplaceBridge.caseSensitive) {
			return;
		}
		// Highlights were computed with the previous options; drop them now so they do not linger until the next search.
		const layer = (window as any).app?.map?._docLayer;
		layer?._clearSearchResults?.();
		AndroidFindReplaceBridge.searchFlags = flags;
		AndroidFindReplaceBridge.caseSensitive = caseSensitive;
		AndroidFindReplaceBridge.generation++;
		AndroidFindReplaceBridge.searchStarted = false;
		AndroidFindReplaceBridge.pendingResults = [];
	}

	static find(query: string): void {
		const normalizedQuery = (query || '').trim();
		if (normalizedQuery !== AndroidFindReplaceBridge.lastQuery) { AndroidFindReplaceBridge.generation++; AndroidFindReplaceBridge.searchStarted = false; AndroidFindReplaceBridge.pendingResults = []; }
		AndroidFindReplaceBridge.lastQuery = normalizedQuery;
		AndroidFindReplaceBridge.executeSearch(
			AndroidFindReplaceBridge.lastQuery,
			false,
			'',
			AndroidFindReplaceBridge.CMD_FIND,
			true,
		);
	}

	static findNext(): void {
		if (!AndroidFindReplaceBridge.lastQuery) {
			return;
		}
		AndroidFindReplaceBridge.executeSearch(
			AndroidFindReplaceBridge.lastQuery,
			false,
			'',
			AndroidFindReplaceBridge.CMD_FIND,
			true,
		);
	}

	static findPrevious(query?: string): void {
		if (query !== undefined) {
			const normalizedQuery = query.trim();
			if (normalizedQuery !== AndroidFindReplaceBridge.lastQuery) { AndroidFindReplaceBridge.generation++; AndroidFindReplaceBridge.searchStarted = false; AndroidFindReplaceBridge.pendingResults = []; }
			AndroidFindReplaceBridge.lastQuery = normalizedQuery;
		}
		if (!AndroidFindReplaceBridge.lastQuery) {
			return;
		}
		AndroidFindReplaceBridge.executeSearch(
			AndroidFindReplaceBridge.lastQuery,
			true,
			'',
			AndroidFindReplaceBridge.CMD_FIND,
			true,
		);
	}

	static replace(replaceWith: string, replaceAll: boolean): void {
		AndroidFindReplaceBridge.lastReplace = replaceWith || '';
		const command = replaceAll
			? AndroidFindReplaceBridge.CMD_REPLACE_ALL
			: AndroidFindReplaceBridge.CMD_REPLACE;
		AndroidFindReplaceBridge.executeSearch(
			AndroidFindReplaceBridge.lastQuery,
			false,
			AndroidFindReplaceBridge.lastReplace,
			command,
			true,
		);
	}

	static replaceForQuery(
		query: string,
		replaceWith: string,
		replaceAll: boolean,
	): void {
		AndroidFindReplaceBridge.lastQuery = (query || '').trim();
		AndroidFindReplaceBridge.lastReplace = replaceWith || '';
		AndroidFindReplaceBridge.generation++;
		AndroidFindReplaceBridge.searchStarted = false;
		AndroidFindReplaceBridge.pendingResults = [];
		if (!AndroidFindReplaceBridge.lastQuery) {
			return;
		}
		const command = replaceAll
			? AndroidFindReplaceBridge.CMD_REPLACE_ALL
			: AndroidFindReplaceBridge.CMD_REPLACE;
		AndroidFindReplaceBridge.executeSearch(
			AndroidFindReplaceBridge.lastQuery,
			false,
			AndroidFindReplaceBridge.lastReplace,
			command,
			false,
		);
	}

	static onCommandResult(event: any, sourceMap?: any): void {
		if (!event || event.commandName !== '.uno:ExecuteSearch' || !AndroidFindReplaceBridge.inFlight) return;
		if (sourceMap && sourceMap !== AndroidFindReplaceBridge.inFlightMap) return;
		AndroidFindReplaceBridge.inFlightCommandResult = event;
		const pending = AndroidFindReplaceBridge.inFlightRequest;
		if (!pending) return;
		if (pending.command !== AndroidFindReplaceBridge.CMD_FIND) {
			AndroidFindReplaceBridge.finishRequest();
			return;
		}
		if (!AndroidFindReplaceBridge.isImpressMap(AndroidFindReplaceBridge.inFlightMap)) {
			let found: boolean | undefined;
			if (typeof event.result === 'boolean') found = event.result;
			else if (event.result && event.result.type === 'boolean' && typeof event.result.value === 'boolean') found = event.result.value;
			// Older Core's unoAnyToJson omits boolean values, so successful boolean means found.
			else if (event.success === true && event.result && event.result.type === 'boolean') found = true;
			else if (event.success === false && event.result && event.result.type === 'void') found = false;
			const status = found === undefined ? (event.success === false ? 'error' : undefined) : (found ? 'found' : 'notFound');
			AndroidFindReplaceBridge.finishRequest(status);
			return;
		}

		// ExecuteSearch's commandresult is not a reliable match/no-match signal for
		// Impress. Wait for CanvasTileLayer's map.search callback, which contains
		// the original phrase and the actual match count. A non-void failure is a
		// command error; failure + void can be the normal no-match path.
		if (event.success === false && !(event.result && event.result.type === 'void')) {
			AndroidFindReplaceBridge.finishRequest('error');
			return;
		}
		AndroidFindReplaceBridge.completeRequestIfReady();
	}

	static onSearchResult(event: any, sourceMap?: any): void {
		if (!AndroidFindReplaceBridge.inFlight || !event || sourceMap !== AndroidFindReplaceBridge.inFlightMap) return;
		if (!AndroidFindReplaceBridge.isImpressMap(sourceMap)) return;
		const pending = AndroidFindReplaceBridge.inFlightRequest;
		if (!pending || pending.command !== AndroidFindReplaceBridge.CMD_FIND || event.originalPhrase !== pending.query) return;
		if (AndroidFindReplaceBridge.inFlightSearchResult) return;
		const count = typeof event.count === 'number' ? event.count : Array.isArray(event.results) ? event.results.length : undefined;
		if (typeof count !== 'number' || !Number.isFinite(count)) return;
		AndroidFindReplaceBridge.inFlightSearchResult = { count };
		AndroidFindReplaceBridge.completeRequestIfReady();
	}

	private static completeRequestIfReady(): void {
		if (!AndroidFindReplaceBridge.inFlightSearchResult || !AndroidFindReplaceBridge.inFlightCommandResult) return;
		AndroidFindReplaceBridge.finishRequest(AndroidFindReplaceBridge.inFlightSearchResult.count > 0 ? 'found' : 'notFound');
	}

	private static finishRequest(status?: string): void {
		const pending = AndroidFindReplaceBridge.inFlightRequest;
		const sourceMap = AndroidFindReplaceBridge.inFlightMap;
		const current = !!pending && !pending.timedOut && pending.generation === AndroidFindReplaceBridge.generation && pending.query === AndroidFindReplaceBridge.lastQuery && sourceMap === app.map;
		AndroidFindReplaceBridge.inFlight = false;
		if (AndroidFindReplaceBridge.resultTimeout) window.clearTimeout(AndroidFindReplaceBridge.resultTimeout);
		AndroidFindReplaceBridge.resultTimeout = 0;
		AndroidFindReplaceBridge.inFlightRequest = null;
		AndroidFindReplaceBridge.inFlightSearchResult = null;
		AndroidFindReplaceBridge.inFlightCommandResult = null;
		AndroidFindReplaceBridge.inFlightMap = null;
		if (current && pending && status && pending.command === AndroidFindReplaceBridge.CMD_FIND) {
			if (app.map) app.map.fire('writersearchresult', { query: pending.query, status });
			const nativeHandler = (window as any).COOLMessageHandler;
			if (nativeHandler && typeof nativeHandler.onFindSearchResult === 'function') nativeHandler.onFindSearchResult(pending.query, status);
		}
		AndroidFindReplaceBridge.dispatchNext();
	}

	private static isImpressMap(map: any): boolean {
		const docType = map && map._docLayer && map._docLayer._docType;
		return docType === 'presentation' || docType === 'drawing';
	}

	static cancelPending(): void {
		AndroidFindReplaceBridge.generation++;
		AndroidFindReplaceBridge.pendingResults = [];
		AndroidFindReplaceBridge.searchStarted = false;
		AndroidFindReplaceBridge.lastQuery = '';
	}

	/** Skip native selection menu for the next search-induced textselection. */
	static suppressSelectionMenuForSearch(): void {
		AndroidFindReplaceBridge.suppressSelectionMenu = true;
		if (AndroidFindReplaceBridge.suppressSelectionMenuTimer) {
			window.clearTimeout(AndroidFindReplaceBridge.suppressSelectionMenuTimer);
		}
		AndroidFindReplaceBridge.suppressSelectionMenuTimer = window.setTimeout(
			() => {
				AndroidFindReplaceBridge.suppressSelectionMenuTimer = 0;
				AndroidFindReplaceBridge.clearSuppressSelectionMenu();
			},
			AndroidFindReplaceBridge.suppressSelectionMenuTimeoutMs,
		);
		const selectionMenu = (window as any).AndroidSelectionMenu;
		if (selectionMenu && typeof selectionMenu.hide === 'function') {
			selectionMenu.hide();
		}
	}

	static clearSuppressSelectionMenu(): void {
		AndroidFindReplaceBridge.suppressSelectionMenu = false;
		if (AndroidFindReplaceBridge.suppressSelectionMenuTimer) {
			window.clearTimeout(AndroidFindReplaceBridge.suppressSelectionMenuTimer);
			AndroidFindReplaceBridge.suppressSelectionMenuTimer = 0;
		}
	}

	static consumeSuppressSelectionMenu(): boolean {
		if (!AndroidFindReplaceBridge.suppressSelectionMenu) {
			return false;
		}
		const selectionMenu = (window as any).AndroidSelectionMenu;
		if (
			selectionMenu &&
			typeof selectionMenu.isLongPressGesturePending === 'function' &&
			selectionMenu.isLongPressGesturePending()
		) {
			AndroidFindReplaceBridge.clearSuppressSelectionMenu();
			return false;
		}
		AndroidFindReplaceBridge.clearSuppressSelectionMenu();
		return true;
	}

	private static executeSearch(
		text: string,
		backward: boolean,
		replaceString: string,
		command: number,
		expand: boolean,
	): void {
		if (!text && command === AndroidFindReplaceBridge.CMD_FIND) {
			return;
		}
		AndroidFindReplaceBridge.releaseStuckRequest();
		AndroidFindReplaceBridge.reconcileMapScope();
		AndroidFindReplaceBridge.suppressSelectionMenuForSearch();
		void expand;
		AndroidFindReplaceBridge.pendingResults.push({ query: text, generation: AndroidFindReplaceBridge.generation, command, backward, replace: replaceString });
		AndroidFindReplaceBridge.dispatchNext();
	}

	// Second-stage fallback. The 15s result timeout keeps inFlight set on purpose, so a late
	// commandresult cannot be mistaken for a newer request. If the user acts again and the Core
	// still has not answered, release the queue now and invalidate the older generation.
	private static releaseStuckRequest(): void {
		const stuck = AndroidFindReplaceBridge.inFlightRequest;
		if (!AndroidFindReplaceBridge.inFlight || !stuck || !stuck.timedOut) return;
		AndroidFindReplaceBridge.inFlight = false;
		AndroidFindReplaceBridge.inFlightRequest = null;
		AndroidFindReplaceBridge.inFlightSearchResult = null;
		AndroidFindReplaceBridge.inFlightCommandResult = null;
		AndroidFindReplaceBridge.inFlightMap = null;
		AndroidFindReplaceBridge.generation++;
		AndroidFindReplaceBridge.searchStarted = false;
		AndroidFindReplaceBridge.pendingResults = [];
	}

	private static dispatchNext(): void {
		AndroidFindReplaceBridge.reconcileMapScope();
		if (AndroidFindReplaceBridge.inFlight) return;
		while (AndroidFindReplaceBridge.pendingResults.length && AndroidFindReplaceBridge.pendingResults[0].generation !== AndroidFindReplaceBridge.generation) AndroidFindReplaceBridge.pendingResults.shift();
		const request = AndroidFindReplaceBridge.pendingResults.shift();
		if (!request) return;
		const map = app.map;
		if (!map || !map._docLayer || !app.activeDocument || !app.activeDocument.activeLayout || !app.socket) {
			AndroidFindReplaceBridge.dispatchNext();
			return;
		}
		if (AndroidFindReplaceBridge.listenedMap !== map && typeof map.on === 'function') {
			if (AndroidFindReplaceBridge.listenedMap && AndroidFindReplaceBridge.commandResultListener && typeof AndroidFindReplaceBridge.listenedMap.off === 'function') AndroidFindReplaceBridge.listenedMap.off('commandresult', AndroidFindReplaceBridge.commandResultListener);
			AndroidFindReplaceBridge.commandResultListener = (event: any) => AndroidFindReplaceBridge.onCommandResult(event, map);
			map.on('commandresult', AndroidFindReplaceBridge.commandResultListener);
			AndroidFindReplaceBridge.searchResultListener = (event: any) => AndroidFindReplaceBridge.onSearchResult(event, map);
			map.on('search', AndroidFindReplaceBridge.searchResultListener);
			AndroidFindReplaceBridge.listenedMap = map;
		}
		if (map._docLayer._searchResults && request.query !== map._docLayer._searchTerm) map._docLayer._clearSearchResults();
		const searchCmd: any = {
			'SearchItem.SearchString': { type: 'string', value: request.query },
			'SearchItem.ReplaceString': { type: 'string', value: request.replace },
			'SearchItem.Backward': { type: 'boolean', value: request.backward },
			'SearchItem.Command': { type: 'long', value: request.command },
			'SearchItem.TransliterateFlags': { type: 'long', value: AndroidFindReplaceBridge.caseSensitive ? 0 : 0x100 },
			'SearchItem.SearchFlags': { type: 'long', value: AndroidFindReplaceBridge.searchFlags },
		};
		if (!AndroidFindReplaceBridge.searchStarted) {
			const rect = app.activeDocument.activeLayout.viewedRectangle;
			searchCmd['SearchItem.SearchStartPointX'] = { type: 'long', value: rect.x1 };
			searchCmd['SearchItem.SearchStartPointY'] = { type: 'long', value: rect.y1 };
		}
		AndroidFindReplaceBridge.searchStarted = true;
		AndroidFindReplaceBridge.inFlight = true;
		AndroidFindReplaceBridge.inFlightRequest = request;
		AndroidFindReplaceBridge.inFlightSearchResult = null;
		AndroidFindReplaceBridge.inFlightCommandResult = null;
		AndroidFindReplaceBridge.inFlightMap = map;
		AndroidFindReplaceBridge.suppressSelectionMenuForSearch();
		app.socket.sendMessage('uno .uno:ExecuteSearch ' + JSON.stringify(searchCmd));
		AndroidFindReplaceBridge.resultTimeout = window.setTimeout(() => {
			if (!AndroidFindReplaceBridge.inFlight) return;
			const timedOut = AndroidFindReplaceBridge.inFlightRequest;
			const timedOutMap = AndroidFindReplaceBridge.inFlightMap;
			if (timedOut) timedOut.timedOut = true;
			AndroidFindReplaceBridge.pendingResults = [];
			AndroidFindReplaceBridge.resultTimeout = 0;
			if (timedOut && timedOut.generation === AndroidFindReplaceBridge.generation && timedOut.command === AndroidFindReplaceBridge.CMD_FIND && app.map === timedOutMap && app.map) {
				app.map.fire('writersearchresult', {query: timedOut.query, status: 'timeout'});
				const nativeHandler = (window as any).COOLMessageHandler;
				if (nativeHandler && typeof nativeHandler.onFindSearchResult === 'function') nativeHandler.onFindSearchResult(timedOut.query, 'timeout');
			}
		}, 15000);
	}

	private static reconcileMapScope(): void {
		const map = app.map || null;
		if (AndroidFindReplaceBridge.scopeMap === map) return;
		if (AndroidFindReplaceBridge.resultTimeout) window.clearTimeout(AndroidFindReplaceBridge.resultTimeout);
		AndroidFindReplaceBridge.resultTimeout = 0;
		AndroidFindReplaceBridge.generation++;
		AndroidFindReplaceBridge.pendingResults = [];
		AndroidFindReplaceBridge.inFlight = false;
		AndroidFindReplaceBridge.inFlightRequest = null;
		AndroidFindReplaceBridge.inFlightSearchResult = null;
		AndroidFindReplaceBridge.inFlightCommandResult = null;
		AndroidFindReplaceBridge.inFlightMap = null;
		AndroidFindReplaceBridge.searchStarted = false;
		if (AndroidFindReplaceBridge.listenedMap && AndroidFindReplaceBridge.commandResultListener && typeof AndroidFindReplaceBridge.listenedMap.off === 'function') AndroidFindReplaceBridge.listenedMap.off('commandresult', AndroidFindReplaceBridge.commandResultListener);
		if (AndroidFindReplaceBridge.listenedMap && AndroidFindReplaceBridge.searchResultListener && typeof AndroidFindReplaceBridge.listenedMap.off === 'function') AndroidFindReplaceBridge.listenedMap.off('search', AndroidFindReplaceBridge.searchResultListener);
		AndroidFindReplaceBridge.listenedMap = null;
		AndroidFindReplaceBridge.commandResultListener = null;
		AndroidFindReplaceBridge.searchResultListener = null;
		AndroidFindReplaceBridge.scopeMap = map;
	}


}

if (typeof window !== 'undefined') {
	(window as any).AndroidFindReplaceBridge = AndroidFindReplaceBridge;
}
