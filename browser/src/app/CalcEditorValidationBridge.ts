/*
 * Intercept ValidationDialog on iOS Calc panel; read/write via dialogevent (Android parity).
 */

class CalcEditorValidationBridge {
	private static pendingRead: CalcEditorValidationState | null = null;
	private static pendingReadCallback: (() => void) | null = null;
	private static pendingWrite: CalcEditorValidationState | null = null;

	static isPending(): boolean {
		return (
			CalcEditorValidationBridge.pendingRead !== null ||
			CalcEditorValidationBridge.pendingWrite !== null
		);
	}

	static loadCurrent(
		target: CalcEditorValidationState,
		sendUno: (command: string) => void,
		onLoaded: () => void,
	): void {
		CalcEditorValidationBridge.pendingRead = target;
		CalcEditorValidationBridge.pendingReadCallback = onLoaded;
		sendUno('.uno:Validation');
	}

	static apply(
		state: CalcEditorValidationState,
		sendUno: (command: string) => void,
	): void {
		CalcEditorValidationBridge.pendingWrite = { ...state };
		sendUno('.uno:Validation');
	}

	static handlePayload(payload: NativeDialogPayload): void {
		const windowId = payload.windowId;
		if (windowId < 0) {
			return;
		}
		if (CalcEditorValidationBridge.pendingWrite) {
			const write = CalcEditorValidationBridge.pendingWrite;
			CalcEditorValidationBridge.pendingWrite = null;
			CalcEditorValidationBridge.applyValidationState(windowId, write);
			return;
		}
		if (CalcEditorValidationBridge.pendingRead) {
			const target = CalcEditorValidationBridge.pendingRead;
			const callback = CalcEditorValidationBridge.pendingReadCallback;
			CalcEditorValidationBridge.pendingRead = null;
			CalcEditorValidationBridge.pendingReadCallback = null;
			CalcEditorValidationBridge.parseValidationPayload(payload, target);
			CalcEditorValidationBridge.sendResponse(windowId, 'cancel', 2);
			if (callback) {
				callback();
			}
		}
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

		selectList('allow', state.allowIndex);
		if (CalcEditorValidationCatalog.needsDataOperator(state.allowIndex)) {
			selectList('data', state.dataIndex);
		}
		if (CalcEditorValidationCatalog.isListAllow(state.allowIndex)) {
			modifyEntry('minlist', state.listEntries);
			check('allowempty', state.allowEmpty);
			check('showlist', state.showDropdownList);
			check('sortascend', state.sortAscending);
			check('casesens', state.caseSensitive);
		} else if (CalcEditorValidationCatalog.isRangeAllow(state.allowIndex)) {
			modifyEntry('min', state.minValue);
			check('allowempty', state.allowEmpty);
			check('showlist', state.showDropdownList);
		} else if (CalcEditorValidationCatalog.isCustomAllow(state.allowIndex)) {
			modifyEntry('min', state.minValue);
			check('allowempty', state.allowEmpty);
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
		check('tsbshow', state.showErrorAlert);
		selectList('actionCB', state.errorActionIndex);
		if (state.errorActionIndex === 3) {
			modifyEntry('erroralert_title', state.macroUrl);
		} else if (state.errorActionIndex !== 4) {
			modifyEntry('erroralert_title', state.errorTitle);
			modifyEntry('errorMsg', state.errorMessage);
		}

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
		if (control.text) {
			const allow = CalcEditorValidationCatalog.ALLOW_OPTIONS.find(
				(o) => o.label === control.text,
			);
			if (allow) {
				return allow.index;
			}
			const data = CalcEditorValidationCatalog.DATA_OPTIONS.find(
				(o) => o.label === control.text,
			);
			if (data) {
				return data.index;
			}
			const err = CalcEditorValidationCatalog.ERROR_ACTION_OPTIONS.find(
				(o) => o.label === control.text,
			);
			if (err) {
				return err.index;
			}
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
		return !!control.checked;
	}

	private static sendDialogEvent(
		windowId: number,
		controlId: string,
		cmd: string,
		data: string,
		type: string,
	): void {
		const socket = (window as any).app && (window as any).app.socket;
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
}
