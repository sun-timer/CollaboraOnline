/*
 * Browser helpers for Calc AI selection context.
 *
 * Cell addresses follow the same A1 encoding Android uses with
 * app.calc.cellAddress. Large selections are sampled like Android LOActivity.
 */

class CalcAiContext {
	static readonly MAX_ROWS_BEFORE_SAMPLE = 200;
	static readonly SAMPLE_ROW_COUNT = 20;

	static columnToLetters(column: number): string {
		let index = column;
		let letters = '';
		while (index >= 0) {
			letters = String.fromCharCode(65 + (index % 26)) + letters;
			index = Math.floor(index / 26) - 1;
		}
		return letters;
	}

	static getActiveCellAddress(): string {
		try {
			const address = (window as any).app?.calc?.cellAddress;
			if (
				!address ||
				typeof address.x !== 'number' ||
				typeof address.y !== 'number'
			) {
				return '';
			}
			return CalcAiContext.columnToLetters(address.x) + (address.y + 1);
		} catch (_error) {
			return '';
		}
	}

	static getSelectedRange(): string {
		// aria-label is maintained by the core celladdress callback; value may
		// contain an unsubmitted address typed by the user.
		const layer = (window as any).app?.map?._docLayer;
		const selected = layer?._calcAiSelection;
		const address = (selected?.part === layer?._selectedPart ? selected.address : '') ||
			document.querySelector('#addressInput input')?.getAttribute('aria-label')?.trim() || '';
		return /^\$?[A-Z]+\$?[1-9]\d*(?::\$?[A-Z]+\$?[1-9]\d*)?$/i.test(address)
			? address : CalcAiContext.getActiveCellAddress();
	}

	static chartRange(range: string): string {
		if (range.includes('.')) return range;
		const layer = (window as any).app?.map?._docLayer;
		const name = layer?._partNames?.[layer._selectedPart];
		return typeof name === 'string' ? "$'" + name.replace(/'/g, "''") + "'." + range : range;
	}

	static scope(): string {
		return JSON.stringify([NativeBridge.getInstance().getDocumentSessionId(), (window as any).app?.map?._docLayer?._selectedPart]);
	}

	static snapshot(): string {
		const app = (window as any).app;
		return JSON.stringify([NativeBridge.getInstance().getDocumentSessionId(),
			app?.map?._docLayer?._selectedPart, CalcAiContext.getSelectedRange()]);
	}

	static getSelectedCellData(): string {
		let raw = '';
		try {
			raw = MobileAiBridge.getInstance().getSelectedText() || '';
		} catch (_error) {
			raw = '';
		}
		return CalcAiContext.sampleCellData(raw);
	}

	static sampleCellData(raw: string): string {
		if (typeof raw !== 'string' || raw.length === 0) {
			return '';
		}
		const lines = raw.split(/\r?\n/);
		if (lines.length <= CalcAiContext.MAX_ROWS_BEFORE_SAMPLE) {
			return raw;
		}
		const sample = lines.slice(0, CalcAiContext.SAMPLE_ROW_COUNT).join('\n');
		return (
			'（选中区域共 ' +
			lines.length +
			' 行，以下仅展示前 ' +
			CalcAiContext.SAMPLE_ROW_COUNT +
			' 行样例）\n' +
			sample
		);
	}

	static buildFormulaContext(): { [key: string]: any } {
		const cellAddress = CalcAiContext.getActiveCellAddress();
		return cellAddress ? { cellAddress } : {};
	}

	private static usedRangeHint(): string {
		const layer = (window as any).app?.map?._docLayer;
		if (!Number.isInteger(layer?._lastRow) || !Number.isInteger(layer?._lastColumn)) return '';
		return '当前工作表已用区域：A1:' + CalcAiContext.columnToLetters(layer._lastColumn) + (layer._lastRow + 1) +
			'。整列选区不代表需要生成整列数据。以下是选区数据：\n';
	}

	static buildAnalysisContext(): { [key: string]: any } {
		return {
			cellRange: CalcAiContext.getSelectedRange(),
			cellData: CalcAiContext.usedRangeHint() + CalcAiContext.getSelectedCellData(),
		};
	}
}
