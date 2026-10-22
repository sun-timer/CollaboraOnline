/* Restricted iOS Calc character formatting; native owns model access and undo. */
type CalcTextFormatOperation = 'highlight' | 'superscript' | 'subscript';
interface CalcTextFormatRange {
	sheet: number;
	col1: number;
	row1: number;
	col2: number;
	row2: number;
}
interface CalcTextFormatResult {
	requestId: string;
	documentSessionId: string;
	success: boolean;
	message?: string;
	changed?: number;
}

class CalcTextFormatBridge {
	private static observedMap: any = null;
	private static address: string | null = null;
	private static addressPart = -1;
	private static serial = 0;
	private static pending: { [id: string]: {
		documentSessionId: string; map: any; timer: number;
		part: number; viewId: number; range: CalcTextFormatRange;
		operation: CalcTextFormatOperation; value: number | boolean;
	} } = {};
	private static readonly onAddress = (event: any) => {
		CalcTextFormatBridge.address = typeof event?.address === 'string' ? event.address : null;
		CalcTextFormatBridge.addressPart = CalcTextFormatBridge.part();
	};
	private static readonly onParts = () => {
		if (CalcTextFormatBridge.addressPart !== CalcTextFormatBridge.part()) {
			CalcTextFormatBridge.address = null;
		}
	};

	static observeSelection(): void {
		if (!CalcEditorFormat.isActive()) return;
		const map = (window as any).app.map;
		if (map === CalcTextFormatBridge.observedMap) return;
		const old = CalcTextFormatBridge.observedMap;
		old?.off?.('celladdress', CalcTextFormatBridge.onAddress);
		old?.off?.('updateparts', CalcTextFormatBridge.onParts);
		CalcTextFormatBridge.observedMap = map;
		CalcTextFormatBridge.address = null;
		map.on?.('celladdress', CalcTextFormatBridge.onAddress);
		map.on?.('updateparts', CalcTextFormatBridge.onParts);
	}

	private static part(): number {
		const map = (window as any).app?.map;
		return typeof map?.getCurrentPartNumber === 'function'
			? map.getCurrentPartNumber() : map?._docLayer?._selectedPart;
	}

	private static selection(): CalcTextFormatRange | null {
		const part = CalcTextFormatBridge.part();
		if (!Number.isInteger(part) || part < 0) return null;
		// aria-label is populated by the core celladdress event, unlike editable value.
		const address = CalcTextFormatBridge.addressPart === part && CalcTextFormatBridge.address !== null
			? CalcTextFormatBridge.address
			: document.querySelector('#addressInput input')?.getAttribute('aria-label');
		const match = address?.trim().match(/^\$?([A-Z]+)\$?([1-9]\d*)(?::\$?([A-Z]+)\$?([1-9]\d*))?$/i);
		if (!match) return null;
		const column = (s: string) => s.toUpperCase().split('').reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;
		const col1 = column(match[1]), row1 = Number(match[2]) - 1;
		const col2 = column(match[3] || match[1]), row2 = Number(match[4] || match[2]) - 1;
		if (![col1, row1, col2, row2].every((n) => Number.isSafeInteger(n) && n >= 0)) return null;
		return { sheet: part, col1: Math.min(col1, col2), row1: Math.min(row1, row2),
			col2: Math.max(col1, col2), row2: Math.max(row1, row2) };
	}

	static apply(operation: CalcTextFormatOperation, value: number | boolean): boolean {
		if (!CalcEditorFormat.isActive()) return false;
		if (!['highlight', 'superscript', 'subscript'].includes(operation)) return false;
		const map = (window as any).app.map;
		if (typeof map.isEditMode === 'function' && !map.isEditMode()) return false;
		if (operation === 'highlight' ?
			(typeof value !== 'number' || !Number.isInteger(value) || value < -1 || value > 0xffffff) :
			(typeof value !== 'boolean')) return false;
		if (map._docLayer?.insertMode === true) {
			// Calc editsh implements super/sub as toggles and ignores bool arguments.
			if (operation !== 'highlight') return CalcEditorFormat.send(
				operation === 'superscript' ? '.uno:SuperScript' : '.uno:SubScript',
			);
			return CalcEditorFormat.send('.uno:CharBackColor', {
				'CharBackColor.Color': { type: 'long', value },
			});
		}
		CalcTextFormatBridge.observeSelection();
		const range = CalcTextFormatBridge.selection();
		if (!range) {
			CalcEditorFormat.notify('请选择连续单元格');
			return false;
		}
		const viewId = map._docLayer?._viewId;
		if (!Number.isInteger(viewId) || viewId < 0 || typeof NativeBridge === 'undefined' ||
			typeof (window as any).postMobileMessage !== 'function') {
			CalcEditorFormat.notify('当前无法应用单元格字符格式');
			return false;
		}
		const documentSessionId = NativeBridge.getInstance().getDocumentSessionId();
		const requestId = 'calc-text-format-' + Date.now() + '-' + (++CalcTextFormatBridge.serial);
		const timer = window.setTimeout(() => {
			const pending = CalcTextFormatBridge.pending[requestId];
			delete CalcTextFormatBridge.pending[requestId];
			if (pending && CalcTextFormatBridge.isCurrent(pending)) {
				CalcEditorFormat.notify('应用字符格式超时，请检查选区后重试');
			}
		}, 10000);
		CalcTextFormatBridge.pending[requestId] = {
			documentSessionId, map, timer, part: range.sheet, viewId, range, operation, value,
		};
		try {
			(window as any).postMobileMessage('CALC_TEXT_FORMAT ' + JSON.stringify({
				requestId, documentSessionId, viewId, part: range.sheet, range, operation, value,
				toggle: operation !== 'highlight',
			}));
			return true;
		} catch (_error) {
			window.clearTimeout(timer);
			delete CalcTextFormatBridge.pending[requestId];
			CalcEditorFormat.notify('无法发送单元格字符格式请求');
			return false;
		}
	}

	private static isCurrent(pending: { documentSessionId: string; map: any; part: number; viewId: number }): boolean {
		return CalcEditorFormat.isActive() && pending.map === (window as any).app.map &&
			pending.viewId === (window as any).app.map._docLayer?._viewId &&
			pending.part === CalcTextFormatBridge.part() &&
			pending.documentSessionId === NativeBridge.getInstance().getDocumentSessionId();
	}

	static onResult(result: CalcTextFormatResult): void {
		const pending = CalcTextFormatBridge.pending[result?.requestId];
		if (!pending || result.documentSessionId !== pending.documentSessionId) return;
		window.clearTimeout(pending.timer);
		delete CalcTextFormatBridge.pending[result.requestId];
		if (!CalcTextFormatBridge.isCurrent(pending)) return;
		if (result.success !== true) {
			CalcEditorFormat.notify(result.message || '无法为所选内容应用字符格式');
			return;
		}
		const range = CalcTextFormatBridge.selection();
		if (range && JSON.stringify(range) === JSON.stringify(pending.range)) {
			pending.map.fire?.('calctextformatresult', result);
		}
		if (result.message) CalcEditorFormat.notify(result.message);
	}
}

if (typeof window !== 'undefined') {
	(window as any).CalcTextFormatBridge = CalcTextFormatBridge;
}
