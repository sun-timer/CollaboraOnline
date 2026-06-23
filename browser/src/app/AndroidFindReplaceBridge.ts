/*
 * Android native find/replace sheet bridge.
 * Wraps ExecuteSearch without using the web MobileSearchBar UI.
 */

interface AndroidFindReplaceOptions {
	ignoreCase?: boolean;
	caseSensitive?: boolean;
	wholeWord?: boolean;
}

class AndroidFindReplaceBridge {
	private static searchFlags = 0;
	private static lastQuery = '';
	private static lastReplace = '';
	private static suppressSelectionMenu = false;
	private static suppressSelectionMenuTimer = 0;
	private static readonly suppressSelectionMenuTimeoutMs = 5000;

	private static readonly CMD_FIND = 0;
	private static readonly CMD_REPLACE = 2;
	private static readonly CMD_REPLACE_ALL = 3;

	private static readonly FLAG_MATCH_CASE = 0x00000001;
	private static readonly FLAG_WHOLE_WORD = 0x00000010;

	static setOptions(options: AndroidFindReplaceOptions): void {
		let flags = 0;
		if (options.caseSensitive) {
			flags |= AndroidFindReplaceBridge.FLAG_MATCH_CASE;
		}
		if (options.wholeWord) {
			flags |= AndroidFindReplaceBridge.FLAG_WHOLE_WORD;
		}
		AndroidFindReplaceBridge.searchFlags = flags;
	}

	static find(query: string): void {
		AndroidFindReplaceBridge.lastQuery = query || '';
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

	static findPrevious(): void {
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
		AndroidFindReplaceBridge.lastQuery = query || '';
		AndroidFindReplaceBridge.lastReplace = replaceWith || '';
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

	/** Skip native selection menu for the next search-induced textselection. */
	static suppressSelectionMenuForSearch(): void {
		AndroidFindReplaceBridge.suppressSelectionMenu = true;
		if (AndroidFindReplaceBridge.suppressSelectionMenuTimer) {
			window.clearTimeout(AndroidFindReplaceBridge.suppressSelectionMenuTimer);
		}
		AndroidFindReplaceBridge.suppressSelectionMenuTimer = window.setTimeout(() => {
			AndroidFindReplaceBridge.suppressSelectionMenuTimer = 0;
			AndroidFindReplaceBridge.clearSuppressSelectionMenu();
		}, AndroidFindReplaceBridge.suppressSelectionMenuTimeoutMs);
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
		AndroidFindReplaceBridge.suppressSelectionMenuForSearch();
		const map = app.map;
		if (!map || !map._docLayer || !app.activeDocument || !app.activeDocument.activeLayout) {
			return;
		}

		if (
			map._docLayer._searchResults &&
			text !== map._docLayer._searchTerm
		) {
			map._docLayer._clearSearchResults();
		}

		map.fire('clearselection');

		let searchStartPointX = app.activeDocument.activeLayout.viewedRectangle.x1;
		let searchStartPointY = app.activeDocument.activeLayout.viewedRectangle.y1;
		if (map._docLayer._lastSearchResult && expand) {
			const strTwips =
				map._docLayer._lastSearchResult.twipsRectangles.match(/\d+/g);
			if (strTwips != null) {
				searchStartPointX = parseInt(strTwips[0], 10);
				searchStartPointY = parseInt(strTwips[1], 10);
			}
			app.searchService.resetSelection();
		}

		const searchCmd = {
			'SearchItem.SearchString': { type: 'string', value: text },
			'SearchItem.ReplaceString': { type: 'string', value: replaceString },
			'SearchItem.Backward': { type: 'boolean', value: backward },
			'SearchItem.SearchStartPointX': { type: 'long', value: searchStartPointX },
			'SearchItem.SearchStartPointY': { type: 'long', value: searchStartPointY },
			'SearchItem.Command': { type: 'long', value: command },
			'SearchItem.SearchFlags': {
				type: 'long',
				value: AndroidFindReplaceBridge.searchFlags,
			},
		};

		app.socket.sendMessage('uno .uno:ExecuteSearch ' + JSON.stringify(searchCmd));
	}
}

(window as any).AndroidFindReplaceBridge = AndroidFindReplaceBridge;
