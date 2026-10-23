/*
 * iOS Calc validation transactions over the existing Core JSDialog protocol.
 * Macro catalog harvest intercepts MacroSelectorDialog only while a browse is pending.
 */

interface CalcEditorMacroItem {
	documentScope: boolean;
	scopeLabel: string;
	library: string;
	module: string;
	name: string;
	uri: string;
	description: string;
}

interface CalcMacroHarvestScope {
	row: number;
	documentScope: boolean;
}

interface CalcMacroHarvestNode {
	row: number;
	name: string;
}

type CalcMacroHarvestStage = 'idle' | 'scope' | 'library' | 'select';

interface CalcEditorDialogContext {
	map: any;
	docLayer: any;
	socket: any;
	documentSessionId: string | null;
}

function calcEditorDialogContext(): CalcEditorDialogContext | null {
	const app = (window as any).app;
	const map = app?.map;
	return (window as any).ThisIsTheiOSApp && map?.getDocType?.() === 'spreadsheet' &&
		map._docLayer && app.socket?.sendMessage
		? { map, docLayer: map._docLayer, socket: app.socket,
			documentSessionId: typeof NativeBridge !== 'undefined' ? NativeBridge.getInstance().getDocumentSessionId() : null }
		: null;
}

function calcEditorSameDialogContext(context: CalcEditorDialogContext | null): boolean {
	const current = calcEditorDialogContext();
	return !!(context && current && context.map === current.map &&
		context.docLayer === current.docLayer && context.socket === current.socket &&
		context.documentSessionId === current.documentSessionId);
}

interface CalcValidationRequest {
	id: number;
	context: CalcEditorDialogContext;
	windowId: number | null;
	phase: 'waiting' | 'help' | 'error' | 'ready' | 'closing' | 'abandoned';
	controls: Map<string, NativeDialogControl>;
	target: CalcEditorValidationState;
	callback: ((loaded: boolean) => void) | null;
	timer: number;
}

class CalcEditorValidationBridge {
	private static request: CalcValidationRequest | null = null;
	private static nextRequestId = 0;
	private static retiredWindows = new Map<number, CalcEditorDialogContext>();

	private static harvestActive = false;
	private static harvestContext: CalcEditorDialogContext | null = null;
	private static harvestCancelled = false;
	private static harvestWindowId = -1;
	private static harvestStartedAt = 0;
	private static harvestTimer = 0;
	private static harvestCallbacks: Array<(items: CalcEditorMacroItem[]) => void> = [];
	private static harvestItems: CalcEditorMacroItem[] = [];
	private static harvestUris = new Set<string>();
	private static harvestStage: CalcMacroHarvestStage = 'idle';
	private static harvestScopes: CalcMacroHarvestScope[] = [];
	private static harvestScopeIndex = 0;
	private static harvestLibraries: CalcMacroHarvestNode[] = [];
	private static harvestLibraryIndex = 0;
	private static harvestModules: CalcMacroHarvestNode[] = [];
	private static harvestModuleIndex = 0;
	private static harvestSelectingModule = false;
	private static harvestCategoryEntries: any[] = [];

	static isPending(): boolean {
		return !!CalcEditorValidationBridge.request && !!calcEditorDialogContext();
	}

	/** Call before AndroidNativeDialogRouter: that router drops validation updates. */
	static handleDialogMessage(message: any): boolean {
		if (!calcEditorDialogContext() || !message || message.id === undefined) {
			return false;
		}
		if (typeof CalcEditorHyperlinkDialog !== 'undefined' &&
			CalcEditorHyperlinkDialog.handleDocumentTargetMessage(message)) {
			return true;
		}
		const controls: NativeDialogControl[] = [];
		const flatten = (node: any) => {
			if (!node) return;
			if (node.id && node.type) {
				controls.push({ ...node, text: node.text ?? node.label });
			}
			if (Array.isArray(node.children)) node.children.forEach(flatten);
		};
		if (message.control) flatten(message.control);
		if (Array.isArray(message.children)) message.children.forEach(flatten);
		const windowId = Number(message.id);
		if (!Number.isInteger(windowId) || windowId < 0) return false;
		const action = message.action === 'close' ? 'dismiss' :
			message.action === 'update' ? 'update' : 'show';
		const payload: NativeDialogPayload = {
			action, windowId, dialogId: message.dialogid, controls,
		};
		const retired = CalcEditorValidationBridge.retiredWindows.get(windowId);
		if (retired && !calcEditorSameDialogContext(retired)) {
			CalcEditorValidationBridge.retiredWindows.delete(windowId);
		} else if (retired) {
			if (action === 'dismiss') CalcEditorValidationBridge.retiredWindows.delete(windowId);
			else if (action === 'show') CalcEditorValidationBridge.dismissWindow(windowId);
			return true;
		}
		const request = CalcEditorValidationBridge.request;
		const validation = message.dialogid === 'ValidationDialog' ||
			controls.some((control) => control.id === 'allow');
		if (request && (request.windowId === windowId || validation)) {
			if (message.action && action === 'show') return true;
			CalcEditorValidationBridge.handlePayload(payload);
			return true;
		}
		if (CalcEditorValidationBridge.harvestActive &&
			(message.dialogid === 'MacroSelectorDialog' ||
				CalcEditorValidationBridge.harvestWindowId === windowId)) {
			if (message.action && action === 'show') return true;
			CalcEditorValidationBridge.handleMacroPayload(payload);
			return true;
		}
		return false;
	}

	static isHarvestPending(): boolean {
		return !!calcEditorDialogContext() && CalcEditorValidationBridge.harvestActive;
	}

	/** Enumerate the real macro selector without showing the Core dialog. */
	static loadMacroCatalog(
		sendUno: (command: string) => void,
		onLoaded: (items: CalcEditorMacroItem[]) => void,
	): () => void {
		const context = calcEditorDialogContext();
		if (CalcEditorValidationBridge.harvestActive &&
			!calcEditorSameDialogContext(CalcEditorValidationBridge.harvestContext)) {
			CalcEditorValidationBridge.finishHarvest(false);
		}
		if (!context || CalcEditorValidationBridge.harvestActive) {
			onLoaded([]);
			return () => undefined;
		}
		CalcEditorValidationBridge.harvestContext = context;
		CalcEditorValidationBridge.harvestCancelled = false;
		CalcEditorValidationBridge.harvestCallbacks = [onLoaded];
		CalcEditorValidationBridge.harvestActive = true;
		CalcEditorValidationBridge.harvestWindowId = -1;
		CalcEditorValidationBridge.harvestStartedAt = Date.now();
		CalcEditorValidationBridge.harvestItems = [];
		CalcEditorValidationBridge.harvestUris = new Set<string>();
		CalcEditorValidationBridge.harvestStage = 'idle';
		CalcEditorValidationBridge.harvestScopes = [];
		CalcEditorValidationBridge.harvestScopeIndex = 0;
		CalcEditorValidationBridge.harvestLibraries = [];
		CalcEditorValidationBridge.harvestLibraryIndex = 0;
		CalcEditorValidationBridge.harvestModules = [];
		CalcEditorValidationBridge.harvestModuleIndex = 0;
		CalcEditorValidationBridge.harvestSelectingModule = false;
		CalcEditorValidationBridge.harvestCategoryEntries = [];
		CalcEditorValidationBridge.armHarvestTimer();
		const validation = CalcEditorValidationBridge.request;
		if (validation?.phase === 'ready' && validation.windowId !== null &&
			calcEditorSameDialogContext(validation.context)) {
			// Validation remains open for this transaction. Open its nested script
			// selector; a top-level RunMacro command would be blocked by the modal.
			const id = validation.windowId;
			CalcEditorValidationBridge.selectValidationTab(id, 2);
			CalcEditorValidationBridge.sendDialogEvent(id, 'tsbshow', 'change', 'true', 'checkbox');
			CalcEditorValidationBridge.sendDialogEvent(id, 'actionCB', 'selected', '3;宏', 'combobox');
			CalcEditorValidationBridge.sendDialogEvent(id, 'browseBtn', 'click', '', 'pushbutton');
		} else {
			sendUno('.uno:RunMacro');
		}
		return () => {
			if (CalcEditorValidationBridge.harvestContext !== context) return;
			CalcEditorValidationBridge.harvestCallbacks = [];
			CalcEditorValidationBridge.harvestCancelled = true;
			if (CalcEditorValidationBridge.harvestTimer) window.clearTimeout(CalcEditorValidationBridge.harvestTimer);
			CalcEditorValidationBridge.harvestTimer = 0;
			if (CalcEditorValidationBridge.harvestWindowId >= 0) {
				CalcEditorValidationBridge.finishHarvest(true);
			}
		};
	}

	static handleMacroPayload(payload: NativeDialogPayload): void {
		if (!CalcEditorValidationBridge.harvestActive) return;
		if (!calcEditorSameDialogContext(CalcEditorValidationBridge.harvestContext)) {
			CalcEditorValidationBridge.finishHarvest(false);
			return;
		}
		if (CalcEditorValidationBridge.harvestWindowId < 0) {
			if (payload.action !== 'show') return;
			CalcEditorValidationBridge.harvestWindowId = payload.windowId;
		}
		if (payload.windowId !== CalcEditorValidationBridge.harvestWindowId) return;
		if (payload.action === 'dismiss') {
			CalcEditorValidationBridge.finishHarvest(false);
		} else if (CalcEditorValidationBridge.harvestCancelled) {
			CalcEditorValidationBridge.finishHarvest(true);
		} else if (payload.action === 'show' && CalcEditorValidationBridge.harvestStage === 'idle') {
			CalcEditorValidationBridge.onMacroShow(payload);
		} else if (payload.action === 'update') {
			CalcEditorValidationBridge.onMacroUpdate(payload);
		}
	}

	static loadCurrent(
		target: CalcEditorValidationState,
		sendUno: (command: string) => void,
		onLoaded: (loaded: boolean) => void,
	): () => void {
		const context = calcEditorDialogContext();
		const previous = CalcEditorValidationBridge.request;
		if (previous && !calcEditorSameDialogContext(previous.context)) {
			CalcEditorValidationBridge.clearRequest(previous);
		} else if (previous && previous.phase === 'ready') {
			CalcEditorValidationBridge.cancelRequest(previous);
		}
		// No request token exists in JSDialog. Never send a second request while
		// a cancelled/timed-out request is still waiting for its first window ID.
		if (!context || CalcEditorValidationBridge.request) {
			onLoaded(false);
			return () => undefined;
		}
		const request: CalcValidationRequest = {
			id: ++CalcEditorValidationBridge.nextRequestId, context, windowId: null,
			phase: 'waiting', controls: new Map(), target, callback: onLoaded, timer: 0,
		};
		CalcEditorValidationBridge.request = request;
		request.timer = window.setTimeout(() => {
			if (CalcEditorValidationBridge.request === request) {
				CalcEditorValidationBridge.cancelRequest(request);
			}
		}, 10000);
		sendUno('.uno:Validation');
		return () => {
			if (CalcEditorValidationBridge.request === request && request.phase !== 'closing') {
				CalcEditorValidationBridge.cancelRequest(request);
			}
		};
	}

	static apply(state: CalcEditorValidationState, _sendUno: (command: string) => void): boolean {
		const request = CalcEditorValidationBridge.request;
		if (!request || request.phase !== 'ready' || request.windowId === null ||
			!calcEditorSameDialogContext(request.context)) return false;
		request.phase = 'closing';
		CalcEditorValidationBridge.applyValidationState(request.windowId, { ...state });
		// Keep a window tombstone so queued updates cannot seed the next rule.
		CalcEditorValidationBridge.retireRequest(request);
		return true;
	}

	static dismissWindow(windowId: number): void {
		if (!calcEditorDialogContext()) return;
		CalcEditorValidationBridge.sendResponse(windowId, 'cancel', 0);
		CalcEditorValidationBridge.releaseDialogWindow(windowId);
	}

	private static releaseDialogWindow(windowId: number): void {
		const router = (window as any).AndroidNativeDialogRouter;
		if (router && typeof router.unregister === 'function' && windowId >= 0) {
			router.unregister(windowId);
		}
	}

	private static clearRequest(request: CalcValidationRequest): void {
		if (request.timer) window.clearTimeout(request.timer);
		request.timer = 0;
		request.callback = null;
		if (CalcEditorValidationBridge.request === request) {
			CalcEditorValidationBridge.request = null;
		}
	}

	private static retireRequest(request: CalcValidationRequest): void {
		if (request.windowId !== null) {
			CalcEditorValidationBridge.retiredWindows.set(request.windowId, request.context);
		}
		CalcEditorValidationBridge.clearRequest(request);
	}

	private static cancelRequest(request: CalcValidationRequest): void {
		const callback = request.callback;
		request.callback = null;
		if (request.timer) window.clearTimeout(request.timer);
		request.timer = 0;
		request.phase = 'abandoned';
		if (request.windowId !== null || !calcEditorSameDialogContext(request.context)) {
			CalcEditorValidationBridge.retireRequest(request);
			if (request.windowId !== null && calcEditorSameDialogContext(request.context)) {
				CalcEditorValidationBridge.dismissWindow(request.windowId);
			}
		}
		callback?.(false);
	}

	static handlePayload(payload: NativeDialogPayload): void {
		const request = CalcEditorValidationBridge.request;
		if (!request || payload.windowId < 0) return;
		if (!calcEditorSameDialogContext(request.context)) {
			CalcEditorValidationBridge.clearRequest(request);
			return;
		}
		if (request.windowId === null) {
			if (payload.action !== 'show') return;
			request.windowId = payload.windowId;
		}
		if (request.windowId !== payload.windowId) return;
		if (payload.action === 'dismiss') {
			const callback = request.callback;
			CalcEditorValidationBridge.clearRequest(request);
			callback?.(false);
			return;
		}
		if (request.phase === 'abandoned') {
			CalcEditorValidationBridge.cancelRequest(request);
			return;
		}
		if (request.phase === 'ready' || request.phase === 'closing') return;
		(payload.controls || []).forEach((control) => request.controls.set(control.id, control));
		if (!request.controls.has('allow')) return;
		if (!['inputhelp_text', 'tsbhelp', 'title'].every((id) => request.controls.has(id))) {
			if (request.phase !== 'help') {
				request.phase = 'help';
				CalcEditorValidationBridge.selectValidationTab(request.windowId, 1);
			}
			return;
		}
		if (!['actionCB', 'errorMsg', 'erroralert_title', 'tsbshow'].every((id) => request.controls.has(id))) {
			if (request.phase !== 'error') {
				request.phase = 'error';
				CalcEditorValidationBridge.selectValidationTab(request.windowId, 2);
			}
			return;
		}
		CalcEditorValidationBridge.parseValidationPayload(
			{ ...payload, controls: Array.from(request.controls.values()) }, request.target,
		);
		request.phase = 'ready';
		if (request.timer) window.clearTimeout(request.timer);
		request.timer = 0;
		const callback = request.callback;
		request.callback = null;
		callback?.(true);
	}

	private static selectValidationTab(windowId: number, index: number): void {
		CalcEditorValidationBridge.sendDialogEvent(windowId, 'tabcontrol', 'selecttab', String(index), 'tabcontrol');
	}

	private static applyValidationState(
		windowId: number,
		state: CalcEditorValidationState,
	): void {
		const selectList = (controlId: string, index: number) => {
			const text = CalcEditorValidationCatalog.comboTextFor(controlId, index);
			CalcEditorValidationBridge.sendDialogEvent(
				windowId,
				controlId,
				'selected',
				index + ';' + text,
				'combobox',
			);
		};
		const modifyEntry = (controlId: string, value: string) => {
			CalcEditorValidationBridge.sendDialogEvent(
				windowId,
				controlId,
				'change',
				value == null ? '' : value,
				'edit',
			);
		};
		const check = (controlId: string, on: boolean) => {
			CalcEditorValidationBridge.sendDialogEvent(
				windowId,
				controlId,
				'change',
				on ? 'true' : 'false',
				'checkbox',
			);
		};
		const selectTab = (index: number) => {
			CalcEditorValidationBridge.sendDialogEvent(
				windowId,
				'tabcontrol',
				'selecttab',
				String(index),
				'tabcontrol',
			);
		};

		selectTab(0);
		selectList('allow', state.allowIndex);
		if (CalcEditorValidationCatalog.needsDataOperator(state.allowIndex)) {
			selectList('data', state.dataIndex);
		}
		if (CalcEditorValidationCatalog.isListAllow(state.allowIndex)) {
			modifyEntry('minlist', state.listEntries);
			check('allowempty', state.allowEmpty);
			check('showlist', true);
			check('sortascend', state.sortAscending);
			check('casesens', state.caseSensitive);
			check('showlist', state.showDropdownList);
		} else if (CalcEditorValidationCatalog.isRangeAllow(state.allowIndex)) {
			modifyEntry('min', state.minValue);
			check('allowempty', state.allowEmpty);
			check('showlist', true);
			check('sortascend', state.sortAscending);
			check('casesens', state.caseSensitive);
			check('showlist', state.showDropdownList);
		} else if (CalcEditorValidationCatalog.isCustomAllow(state.allowIndex)) {
			modifyEntry('min', state.minValue);
			check('allowempty', state.allowEmpty);
			check('casesens', state.caseSensitive);
		} else if (state.allowIndex !== 0) {
			modifyEntry('min', state.minValue);
			if (CalcEditorValidationCatalog.needsBetweenValues(state.dataIndex)) {
				modifyEntry('max', state.maxValue);
			}
			check('allowempty', state.allowEmpty);
		}

		selectTab(1);
		check('tsbhelp', state.showInputHelp);
		modifyEntry('title', state.inputHelpTitle);
		modifyEntry('inputhelp_text', state.inputHelpText);

		selectTab(2);
		// Core ignores edits to insensitive controls. Populate retained values
		// while enabled, then restore the user's final warning/action switches.
		check('tsbshow', true);
		selectList('actionCB', 0);
		modifyEntry('erroralert_title', state.errorTitle);
		modifyEntry('errorMsg', state.errorMessage);
		selectList('actionCB', state.errorActionIndex);
		if (state.errorActionIndex === 3) modifyEntry('erroralert_title', state.macroUrl);
		check('tsbshow', state.showErrorAlert);

		CalcEditorValidationBridge.sendResponse(windowId, 'ok', 1);
	}

	private static parseValidationPayload(
		payload: NativeDialogPayload,
		target: CalcEditorValidationState,
	): void {
		const controls = payload.controls || [];
		target.allowIndex = CalcEditorValidationBridge.optListIndex(controls, 'allow', 0);
		target.dataIndex = CalcEditorValidationBridge.optListIndex(controls, 'data', 0);
		target.errorActionIndex = CalcEditorValidationBridge.optListIndex(
			controls,
			'actionCB',
			0,
		);
		target.minValue = CalcEditorValidationBridge.optEntryText(controls, 'min');
		target.maxValue = CalcEditorValidationBridge.optEntryText(controls, 'max');
		target.listEntries = CalcEditorValidationBridge.optEntryText(controls, 'minlist');
		target.inputHelpTitle = CalcEditorValidationBridge.optEntryText(controls, 'title');
		target.inputHelpText = CalcEditorValidationBridge.optEntryText(
			controls,
			'inputhelp_text',
		);
		target.errorTitle = CalcEditorValidationBridge.optEntryText(
			controls,
			'erroralert_title',
		);
		if (target.errorActionIndex === 3) {
			target.macroUrl = target.errorTitle;
			target.errorTitle = '';
		} else {
			target.macroUrl = '';
		}
		target.errorMessage = CalcEditorValidationBridge.optEntryText(controls, 'errorMsg');
		target.allowEmpty = CalcEditorValidationBridge.optCheck(controls, 'allowempty', true);
		target.showDropdownList = CalcEditorValidationBridge.optCheck(controls, 'showlist', true);
		target.sortAscending = CalcEditorValidationBridge.optCheck(controls, 'sortascend', true);
		target.caseSensitive = CalcEditorValidationBridge.optCheck(controls, 'casesens', false);
		target.showInputHelp = CalcEditorValidationBridge.optCheck(controls, 'tsbhelp', false);
		target.showErrorAlert = CalcEditorValidationBridge.optCheck(controls, 'tsbshow', true);
	}

	private static findControl(
		controls: NativeDialogControl[],
		controlId: string,
	): NativeDialogControl | undefined {
		return controls.find((c) => c.id === controlId);
	}

	private static optListIndex(
		controls: NativeDialogControl[],
		controlId: string,
		fallback: number,
	): number {
		const control = CalcEditorValidationBridge.findControl(controls, controlId);
		if (!control) {
			return fallback;
		}
		const selected = control.selectedEntries;
		if (Array.isArray(selected) && selected.length > 0) {
			const n = Number.parseInt(String(selected[0]).trim(), 10);
			if (!Number.isNaN(n)) {
				return n;
			}
		}
		if (control.text && Array.isArray(control.entries)) {
			const index = control.entries.findIndex((entry: any) =>
				(typeof entry === 'string' ? entry : entry?.text) === control.text);
			if (index >= 0) return index;
		}

		return fallback;
	}

	private static optEntryText(
		controls: NativeDialogControl[],
		controlId: string,
	): string {
		const control = CalcEditorValidationBridge.findControl(controls, controlId);
		if (!control) {
			return '';
		}
		return control.text || '';
	}

	private static optCheck(
		controls: NativeDialogControl[],
		controlId: string,
		fallback: boolean,
	): boolean {
		const control = CalcEditorValidationBridge.findControl(controls, controlId);
		if (!control || control.checked === undefined) {
			return fallback;
		}
		return control.checked === true || String(control.checked) === 'true';
	}

	private static sendDialogEvent(
		windowId: number,
		controlId: string,
		cmd: string,
		data: string,
		type: string,
	): void {
		const context = calcEditorDialogContext();
		const socket = context?.socket;
		if (!socket || typeof socket.sendMessage !== 'function' || windowId < 0) {
			return;
		}
		const body = JSON.stringify({ id: controlId, cmd, data, type });
		socket.sendMessage('dialogevent ' + windowId + ' ' + body);
	}

	private static sendResponse(
		windowId: number,
		buttonId: string,
		responseCode: number,
	): void {
		CalcEditorValidationBridge.sendDialogEvent(
			windowId,
			buttonId,
			'click',
			String(responseCode),
			'responsebutton',
		);
	}

	private static armHarvestTimer(): void {
		if (CalcEditorValidationBridge.harvestTimer) {
			window.clearTimeout(CalcEditorValidationBridge.harvestTimer);
		}
		CalcEditorValidationBridge.harvestTimer = window.setTimeout(() => {
			CalcEditorValidationBridge.harvestTimer = 0;
			if (!CalcEditorValidationBridge.harvestActive) {
				return;
			}
			if (CalcEditorValidationBridge.harvestWindowId >= 0) {
				CalcEditorValidationBridge.finishHarvest(true);
				return;
			}
			if (Date.now() - CalcEditorValidationBridge.harvestStartedAt < 20000) {
				CalcEditorValidationBridge.armHarvestTimer();
				return;
			}
			CalcEditorValidationBridge.harvestCancelled = true;
			const callbacks = CalcEditorValidationBridge.harvestCallbacks.splice(0);
			callbacks.forEach((callback) => callback([]));
		}, 8000);
	}

	private static onMacroShow(payload: NativeDialogPayload): void {
		CalcEditorValidationBridge.harvestWindowId = payload.windowId;
		const categories = CalcEditorValidationBridge.findControl(
			payload.controls || [],
			'categories',
		);
		const entries = CalcEditorValidationBridge.controlEntries(categories);
		CalcEditorValidationBridge.harvestCategoryEntries = entries;
		CalcEditorValidationBridge.harvestScopes =
			CalcEditorValidationBridge.collectScopes(entries);
		if (!CalcEditorValidationBridge.harvestScopes.length) {
			CalcEditorValidationBridge.finishHarvest(true);
			return;
		}
		const documentIndex = CalcEditorValidationBridge.harvestScopes.findIndex(
			(scope) => scope.documentScope,
		);
		if (documentIndex > 0) {
			const documentScope = CalcEditorValidationBridge.harvestScopes.splice(
				documentIndex,
				1,
			)[0];
			CalcEditorValidationBridge.harvestScopes.unshift(documentScope);
		}
		CalcEditorValidationBridge.harvestScopeIndex = 0;
		CalcEditorValidationBridge.beginScope();
	}

	private static onMacroUpdate(payload: NativeDialogPayload): void {
		for (const control of payload.controls || []) {
			if (control.id === 'categories') {
				CalcEditorValidationBridge.handleCategoriesUpdate(control);
			} else if (control.id === 'commands') {
				CalcEditorValidationBridge.handleCommandsUpdate(control);
			}
		}
	}

	private static handleCategoriesUpdate(control: NativeDialogControl): void {
		const entries = CalcEditorValidationBridge.controlEntries(control);
		if (entries.length) {
			CalcEditorValidationBridge.harvestCategoryEntries = entries;
		}
		if (CalcEditorValidationBridge.harvestStage === 'scope') {
			const scope =
				CalcEditorValidationBridge.harvestScopes[
					CalcEditorValidationBridge.harvestScopeIndex
				];
			const root = scope
				? CalcEditorValidationBridge.findTreeNode(entries, scope.row)
				: null;
			const children =
				root && Array.isArray(root.children) && root.children.length
					? root.children
					: CalcEditorValidationBridge.nonScopeEntries(entries);
			CalcEditorValidationBridge.harvestLibraries =
				CalcEditorValidationBridge.toNodes(children);
			CalcEditorValidationBridge.harvestLibraryIndex = 0;
			CalcEditorValidationBridge.beginLibrary();
			return;
		}
		if (CalcEditorValidationBridge.harvestStage === 'library') {
			const library =
				CalcEditorValidationBridge.harvestLibraries[
					CalcEditorValidationBridge.harvestLibraryIndex
				];
			const node = library
				? CalcEditorValidationBridge.findTreeNode(entries, library.row)
				: null;
			let children: any[] = [];
			if (node && Array.isArray(node.children)) {
				children = node.children;
			} else if (library && !node) {
				children = entries.filter(
					(entry) => CalcEditorValidationBridge.entryRow(entry) !== library.row,
				);
			}
			CalcEditorValidationBridge.harvestModules =
				CalcEditorValidationBridge.toNodes(children);
			CalcEditorValidationBridge.harvestModuleIndex = 0;
			CalcEditorValidationBridge.selectCurrentMacroNode();
		}
	}

	private static handleCommandsUpdate(control: NativeDialogControl): void {
		if (CalcEditorValidationBridge.harvestStage !== 'select') {
			return;
		}
		const scope =
			CalcEditorValidationBridge.harvestScopes[
				CalcEditorValidationBridge.harvestScopeIndex
			];
		const library =
			CalcEditorValidationBridge.harvestLibraries[
				CalcEditorValidationBridge.harvestLibraryIndex
			];
		if (!scope || !library) {
			CalcEditorValidationBridge.advanceAfterCommands();
			return;
		}
		const moduleName = CalcEditorValidationBridge.harvestSelectingModule
			? CalcEditorValidationBridge.harvestModules[
					CalcEditorValidationBridge.harvestModuleIndex
				]?.name || ''
			: '';
		const scopeLabel = scope.documentScope ? '我的宏' : '应用程序的宏';
		for (const entry of CalcEditorValidationBridge.controlEntries(control)) {
			const name = CalcEditorValidationBridge.entryText(entry);
			if (!name || name === '<dummy>') {
				continue;
			}
			const uri = CalcEditorValidationBridge.buildMacroUri(
				library.name,
				moduleName,
				name,
				scope.documentScope,
			);
			if (CalcEditorValidationBridge.harvestUris.has(uri)) {
				continue;
			}
			CalcEditorValidationBridge.harvestUris.add(uri);
			CalcEditorValidationBridge.harvestItems.push({
				documentScope: scope.documentScope,
				scopeLabel: scopeLabel,
				library: library.name,
				module: moduleName,
				name: name,
				uri: uri,
				description: CalcEditorValidationBridge.entryDescription(entry),
			});
		}
		CalcEditorValidationBridge.advanceAfterCommands();
	}

	private static beginScope(): void {
		const scope =
			CalcEditorValidationBridge.harvestScopes[
				CalcEditorValidationBridge.harvestScopeIndex
			];
		if (!scope) {
			CalcEditorValidationBridge.finishHarvest(true);
			return;
		}
		CalcEditorValidationBridge.harvestLibraries = [];
		CalcEditorValidationBridge.harvestLibraryIndex = 0;
		CalcEditorValidationBridge.harvestModules = [];
		CalcEditorValidationBridge.harvestModuleIndex = 0;
		const root = CalcEditorValidationBridge.findTreeNode(
			CalcEditorValidationBridge.harvestCategoryEntries,
			scope.row,
		);
		const children =
			root && Array.isArray(root.children) ? root.children : [];
		if (children.length) {
			CalcEditorValidationBridge.harvestLibraries =
				CalcEditorValidationBridge.toNodes(children);
			CalcEditorValidationBridge.beginLibrary();
			return;
		}
		CalcEditorValidationBridge.harvestStage = 'scope';
		CalcEditorValidationBridge.sendTreeViewEvent(
			CalcEditorValidationBridge.harvestWindowId,
			'categories',
			'expand',
			scope.row,
		);
	}

	private static beginLibrary(): void {
		if (
			CalcEditorValidationBridge.harvestLibraryIndex >=
			CalcEditorValidationBridge.harvestLibraries.length
		) {
			CalcEditorValidationBridge.advanceScope();
			return;
		}
		const library =
			CalcEditorValidationBridge.harvestLibraries[
				CalcEditorValidationBridge.harvestLibraryIndex
			];
		CalcEditorValidationBridge.harvestModules = [];
		CalcEditorValidationBridge.harvestModuleIndex = 0;
		const node = CalcEditorValidationBridge.findTreeNode(
			CalcEditorValidationBridge.harvestCategoryEntries,
			library.row,
		);
		const children =
			node && Array.isArray(node.children) ? node.children : [];
		if (children.length) {
			CalcEditorValidationBridge.harvestModules =
				CalcEditorValidationBridge.toNodes(children);
			CalcEditorValidationBridge.selectCurrentMacroNode();
			return;
		}
		CalcEditorValidationBridge.harvestStage = 'library';
		CalcEditorValidationBridge.sendTreeViewEvent(
			CalcEditorValidationBridge.harvestWindowId,
			'categories',
			'expand',
			library.row,
		);
	}

	private static selectCurrentMacroNode(): void {
		const library =
			CalcEditorValidationBridge.harvestLibraries[
				CalcEditorValidationBridge.harvestLibraryIndex
			];
		if (!library) {
			CalcEditorValidationBridge.advanceScope();
			return;
		}
		CalcEditorValidationBridge.harvestSelectingModule =
			CalcEditorValidationBridge.harvestModules.length > 0 &&
			CalcEditorValidationBridge.harvestModuleIndex <
				CalcEditorValidationBridge.harvestModules.length;
		const row = CalcEditorValidationBridge.harvestSelectingModule
			? CalcEditorValidationBridge.harvestModules[
					CalcEditorValidationBridge.harvestModuleIndex
				].row
			: library.row;
		CalcEditorValidationBridge.harvestStage = 'select';
		CalcEditorValidationBridge.sendTreeViewEvent(
			CalcEditorValidationBridge.harvestWindowId,
			'categories',
			'select',
			row,
		);
	}

	private static advanceAfterCommands(): void {
		if (CalcEditorValidationBridge.harvestSelectingModule) {
			CalcEditorValidationBridge.harvestModuleIndex += 1;
			if (
				CalcEditorValidationBridge.harvestModuleIndex <
				CalcEditorValidationBridge.harvestModules.length
			) {
				CalcEditorValidationBridge.selectCurrentMacroNode();
				return;
			}
		}
		CalcEditorValidationBridge.harvestLibraryIndex += 1;
		CalcEditorValidationBridge.harvestModules = [];
		CalcEditorValidationBridge.harvestModuleIndex = 0;
		CalcEditorValidationBridge.beginLibrary();
	}

	private static advanceScope(): void {
		CalcEditorValidationBridge.harvestScopeIndex += 1;
		if (
			CalcEditorValidationBridge.harvestScopeIndex >=
			CalcEditorValidationBridge.harvestScopes.length
		) {
			CalcEditorValidationBridge.finishHarvest(true);
			return;
		}
		CalcEditorValidationBridge.beginScope();
	}

	private static finishHarvest(sendCancel: boolean): void {
		if (!CalcEditorValidationBridge.harvestActive) {
			return;
		}
		CalcEditorValidationBridge.harvestActive = false;
		CalcEditorValidationBridge.harvestStage = 'idle';
		if (CalcEditorValidationBridge.harvestTimer) {
			window.clearTimeout(CalcEditorValidationBridge.harvestTimer);
			CalcEditorValidationBridge.harvestTimer = 0;
		}
		const windowId = CalcEditorValidationBridge.harvestWindowId;
		const context = CalcEditorValidationBridge.harvestContext;
		if (windowId >= 0 && context) CalcEditorValidationBridge.retiredWindows.set(windowId, context);
		if (sendCancel && windowId >= 0 && calcEditorSameDialogContext(context)) {
			CalcEditorValidationBridge.sendResponse(windowId, 'cancel', 0);
		}
		const items = CalcEditorValidationBridge.harvestItems.slice();
		const callbacks = CalcEditorValidationBridge.harvestCallbacks.splice(0);
		CalcEditorValidationBridge.harvestContext = null;
		CalcEditorValidationBridge.harvestWindowId = -1;
		if (calcEditorSameDialogContext(context) && !CalcEditorValidationBridge.harvestCancelled) {
			callbacks.forEach((callback) => callback(items));
		}
	}

	private static collectScopes(entries: any[]): CalcMacroHarvestScope[] {
		const scopes: CalcMacroHarvestScope[] = [];
		for (const entry of entries) {
			const text = CalcEditorValidationBridge.entryText(entry);
			const row = CalcEditorValidationBridge.entryRow(entry);
			if (row < 0 || !text) {
				continue;
			}
			scopes.push({
				row: row,
				documentScope: CalcEditorValidationBridge.isDocumentScopeText(text),
			});
		}
		return scopes;
	}

	private static nonScopeEntries(entries: any[]): any[] {
		const scopeRows = new Set(
			CalcEditorValidationBridge.harvestScopes.map((scope) => scope.row),
		);
		return entries.filter((entry) => {
			const row = CalcEditorValidationBridge.entryRow(entry);
			return row >= 0 && !scopeRows.has(row) && !!CalcEditorValidationBridge.entryText(entry);
		});
	}

	private static toNodes(entries: any[]): CalcMacroHarvestNode[] {
		const nodes: CalcMacroHarvestNode[] = [];
		for (const entry of entries) {
			const row = CalcEditorValidationBridge.entryRow(entry);
			const name = CalcEditorValidationBridge.entryText(entry);
			if (row < 0 || !name || name === '<dummy>') {
				continue;
			}
			nodes.push({ row: row, name: name });
		}
		return nodes;
	}

	private static controlEntries(control: NativeDialogControl | undefined): any[] {
		if (!control || !Array.isArray(control.entries)) {
			return [];
		}
		return control.entries;
	}

	private static findTreeNode(entries: any[] | undefined, row: number): any | null {
		if (!entries) {
			return null;
		}
		for (const entry of entries) {
			if (!entry) {
				continue;
			}
			if (CalcEditorValidationBridge.entryRow(entry) === row) {
				return entry;
			}
			const child = CalcEditorValidationBridge.findTreeNode(entry.children, row);
			if (child) {
				return child;
			}
		}
		return null;
	}

	private static entryRow(entry: any): number {
		if (!entry || entry.row === undefined || entry.row === null) {
			return -1;
		}
		const row = Number(entry.row);
		return Number.isFinite(row) ? row : -1;
	}

	private static entryText(entry: any): string {
		if (!entry) {
			return '';
		}
		if (typeof entry.text === 'string' && entry.text) {
			return entry.text;
		}
		if (Array.isArray(entry.columns)) {
			for (const column of entry.columns) {
				if (column && typeof column.text === 'string' && column.text) {
					return column.text;
				}
			}
		}
		return '';
	}

	private static entryDescription(entry: any): string {
		if (!entry) {
			return '';
		}
		if (typeof entry.description === 'string') {
			return entry.description;
		}
		if (Array.isArray(entry.columns) && entry.columns.length > 1) {
			const column = entry.columns[1];
			if (column && typeof column.text === 'string') {
				return column.text;
			}
		}
		return '';
	}

	private static isDocumentScopeText(text: string): boolean {
		const value = text || '';
		return (
			!value.includes('应用程序的宏') &&
			!value.includes('Application') &&
			!value.toLowerCase().includes('share')
		);
	}

	private static buildMacroUri(
		library: string,
		moduleName: string,
		name: string,
		documentScope: boolean,
	): string {
		const location = documentScope ? 'document' : 'application';
		let script = library || '';
		if (moduleName) {
			script += '.' + moduleName;
		}
		script += '.' + name;
		return (
			'vnd.sun.star.script:' +
			script +
			'?language=Basic&location=' +
			location
		);
	}

	private static sendTreeViewEvent(
		windowId: number,
		controlId: string,
		cmd: string,
		row: number,
	): void {
		const context = calcEditorDialogContext();
		const socket = context?.socket;
		if (!socket || typeof socket.sendMessage !== 'function' || windowId < 0) {
			return;
		}
		const body = JSON.stringify({
			id: controlId,
			cmd: cmd,
			data: row,
			type: 'treeview',
		});
		socket.sendMessage('dialogevent ' + windowId + ' ' + body);
	}
}
