/* Reads UNO states that the shared status list does not initialize for iOS Calc. */
interface CalcFormatStateResult {
	requestId: string;
	documentSessionId: string;
	success: boolean;
	viewId: number;
	part: number;
	range: { sheet: number; col1: number; row1: number; col2: number; row2: number };
	states?: { [command: string]: string | number | boolean };
}

class CalcFormatStateBridge {
	private static map: any = null;
	private static address: string | null = null;
	private static addressDomSnapshot: string | null = null;
	private static part = -1;
	private static generation = 0;
	private static observedSelectionKey: string | null = null;
	private static serial = 0;
	private static values: { [command: string]: string | number | boolean } = {};
	private static pending: any = null;
	private static readonly requestTimeoutMs = 2500;
	private static readonly onAddress = (event: any) => {
		CalcFormatStateBridge.address = typeof event?.address === 'string' ? event.address : null;
		CalcFormatStateBridge.addressDomSnapshot = document.querySelector('#addressInput input')
			?.getAttribute('aria-label') || null;
		CalcFormatStateBridge.selectionChanged();
	};
	private static readonly onParts = () => CalcFormatStateBridge.selectionChanged();

	static observeSelection(): void {
		if (!CalcEditorFormat.isActive()) return;
		const map = (window as any).app.map;
		if (map === CalcFormatStateBridge.map) return;
		CalcFormatStateBridge.map?.off?.('celladdress', CalcFormatStateBridge.onAddress);
		CalcFormatStateBridge.map?.off?.('updateparts', CalcFormatStateBridge.onParts);
		CalcFormatStateBridge.map = map;
		CalcFormatStateBridge.address = null;
		CalcFormatStateBridge.addressDomSnapshot = null;
		CalcFormatStateBridge.part = CalcFormatStateBridge.currentPart();
		CalcFormatStateBridge.observedSelectionKey = CalcFormatStateBridge.selectionKey();
		CalcFormatStateBridge.values = {};
		map.on?.('celladdress', CalcFormatStateBridge.onAddress);
		map.on?.('updateparts', CalcFormatStateBridge.onParts);
	}

	static state(command: string): string | number | boolean | undefined {
		return CalcFormatStateBridge.values[command];
	}

	static handles(command: string): boolean {
		return ['.uno:NumberFormat', '.uno:AlignmentRotation', '.uno:AlignmentStacked'].includes(command);
	}

	static selectionKey(): string | null {
		if (!CalcEditorFormat.isActive() || typeof NativeBridge === 'undefined') return null;
		const map = (window as any).app.map;
		const range = CalcFormatStateBridge.selection();
		const viewId = map._docLayer?._viewId;
		if (!range || !Number.isInteger(viewId) || viewId < 0) return null;
		return [NativeBridge.getInstance().getDocumentSessionId(), viewId, range.sheet,
			range.col1, range.row1, range.col2, range.row2].join(':');
	}

	static request(): void {
		if (!CalcEditorFormat.isActive() || typeof NativeBridge === 'undefined' ||
			typeof (window as any).postMobileMessage !== 'function') return;
		CalcFormatStateBridge.observeSelection();
		const map = (window as any).app.map;
		if (CalcFormatStateBridge.pending) window.clearTimeout(CalcFormatStateBridge.pending.timer);
		CalcFormatStateBridge.pending = null;
		const range = CalcFormatStateBridge.selection();
		const viewId = map._docLayer?._viewId;
		if (!range || !Number.isInteger(viewId) || viewId < 0) return;
		const requestId = 'calc-format-state-' + Date.now() + '-' + (++CalcFormatStateBridge.serial);
		const generation = CalcFormatStateBridge.generation;
		const documentSessionId = NativeBridge.getInstance().getDocumentSessionId();
		const payload = { requestId, documentSessionId, viewId, part: range.sheet, range };
		const timer = window.setTimeout(() => {
			const pending = CalcFormatStateBridge.pending;
			if (!pending || pending.requestId !== requestId) return;
			CalcFormatStateBridge.pending = null;
			CalcFormatStateBridge.values = {};
			map.fire?.('calcformatstatechanged', { states: {}, error: 'timeout' });
		}, CalcFormatStateBridge.requestTimeoutMs);
		CalcFormatStateBridge.pending = { ...payload, map, generation, timer };
		try {
			(window as any).postMobileMessage('CALC_FORMAT_STATE ' + JSON.stringify(payload));
		} catch (_error) {
			window.clearTimeout(timer);
			CalcFormatStateBridge.pending = null;
			CalcFormatStateBridge.values = {};
			map.fire?.('calcformatstatechanged', { states: {}, error: 'send-failed' });
		}
	}

	static invalidate(): void {
		CalcFormatStateBridge.generation++;
		if (CalcFormatStateBridge.pending) window.clearTimeout(CalcFormatStateBridge.pending.timer);
		CalcFormatStateBridge.pending = null;
	}

	static onResult(result: CalcFormatStateResult): void {
		const pending = CalcFormatStateBridge.pending;
		if (!pending || pending.requestId !== result?.requestId ||
			pending.documentSessionId !== result.documentSessionId || pending.viewId !== result.viewId ||
			pending.part !== result.part || !CalcFormatStateBridge.sameRange(pending.range, result.range)) return;
		window.clearTimeout(pending.timer);
		CalcFormatStateBridge.pending = null;
		const liveRange = CalcFormatStateBridge.selection();
		const liveMap = (window as any).app?.map;
		const liveViewId = liveMap?._docLayer?._viewId;
		const liveSession = NativeBridge.getInstance().getDocumentSessionId();
		if (result.success !== true || pending.map !== liveMap ||
			pending.generation !== CalcFormatStateBridge.generation ||
			pending.documentSessionId !== liveSession || pending.viewId !== liveViewId ||
			pending.part !== CalcFormatStateBridge.currentPart() ||
			!liveRange || !CalcFormatStateBridge.sameRange(pending.range, liveRange)) {
			CalcFormatStateBridge.values = {};
			pending.map.fire?.('calcformatstatechanged', { states: {}, error: 'stale-or-failed' });
			return;
		}
		CalcFormatStateBridge.values = {};
		const states = result.states;
		if (!states || typeof states !== 'object' || Array.isArray(states)) {
			pending.map.fire?.('calcformatstatechanged', { states: {}, error: 'invalid-states' });
			return;
		}
		const numberFormat = states['.uno:NumberFormat'];
		const rotation = states['.uno:AlignmentRotation'];
		const stacked = states['.uno:AlignmentStacked'];
		if ((numberFormat !== undefined && typeof numberFormat !== 'string') ||
			(rotation !== undefined && (typeof rotation !== 'number' || !Number.isSafeInteger(rotation))) ||
			(stacked !== undefined && typeof stacked !== 'boolean')) {
			CalcFormatStateBridge.values = {};
			pending.map.fire?.('calcformatstatechanged', { states: {}, error: 'invalid-state-type' });
			return;
		}
		Object.keys(states).forEach((key) => {
			const value = states[key];
			if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean')
				CalcFormatStateBridge.values[key] = value;
		});
		pending.map.fire?.('calcformatstatechanged', { states: CalcFormatStateBridge.values });
	}

	private static sameRange(a: any, b: any): boolean {
		return !!a && !!b && ['sheet', 'col1', 'row1', 'col2', 'row2'].every((key) =>
			Number.isSafeInteger(a[key]) && Number.isSafeInteger(b[key]) && a[key] === b[key]);
	}

	private static selectionChanged(): void {
		const nextKey = CalcFormatStateBridge.selectionKey();
		if (nextKey === CalcFormatStateBridge.observedSelectionKey) return;
		CalcFormatStateBridge.observedSelectionKey = nextKey;
		CalcFormatStateBridge.generation++;
		if (CalcFormatStateBridge.pending) window.clearTimeout(CalcFormatStateBridge.pending.timer);
		CalcFormatStateBridge.pending = null;
		CalcFormatStateBridge.part = CalcFormatStateBridge.currentPart();
		CalcFormatStateBridge.values = {};
		CalcFormatStateBridge.map?.fire?.('calcformatstatechanged', { states: {} });
		CalcFormatStateBridge.request();
	}

	private static currentPart(): number {
		const map = (window as any).app?.map;
		return typeof map?.getCurrentPartNumber === 'function'
			? map.getCurrentPartNumber() : map?._docLayer?._selectedPart;
	}

	private static selection(): { sheet: number; col1: number; row1: number; col2: number; row2: number } | null {
		const part = CalcFormatStateBridge.currentPart();
		if (!Number.isInteger(part) || part < 0) return null;
		const domAddress = document.querySelector('#addressInput input')?.getAttribute('aria-label') || null;
		const address = CalcFormatStateBridge.part === part && CalcFormatStateBridge.address !== null &&
			domAddress === CalcFormatStateBridge.addressDomSnapshot
			? CalcFormatStateBridge.address : domAddress ||
				(CalcFormatStateBridge.part === part ? CalcFormatStateBridge.address : null);
		const match = address?.trim().match(/^\$?([A-Z]+)\$?([1-9]\d*)(?::\$?([A-Z]+)\$?([1-9]\d*))?$/i);
		if (!match) return null;
		const column = (s: string) => s.toUpperCase().split('').reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;
		const col1 = column(match[1]), row1 = Number(match[2]) - 1;
		const col2 = column(match[3] || match[1]), row2 = Number(match[4] || match[2]) - 1;
		if (![col1, row1, col2, row2].every((n) => Number.isSafeInteger(n) && n >= 0)) return null;
		return { sheet: part, col1: Math.min(col1, col2), row1: Math.min(row1, row2),
			col2: Math.max(col1, col2), row2: Math.max(row1, row2) };
	}
}

if (typeof window !== 'undefined') {
	(window as any).CalcFormatStateBridge = CalcFormatStateBridge;
}
