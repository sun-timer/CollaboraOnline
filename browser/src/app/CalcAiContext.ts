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
		try {
			const input = document.querySelector(
				'#addressInput input',
			) as HTMLInputElement | null;
			const value = input && typeof input.value === 'string' ? input.value.trim() : '';
			if (value) {
				return value;
			}
		} catch (_error) {
			// Fall through to the active cell.
		}
		return CalcAiContext.getActiveCellAddress();
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

	static buildAnalysisContext(): { [key: string]: any } {
		return {
			cellRange: CalcAiContext.getSelectedRange(),
			cellData: CalcAiContext.getSelectedCellData(),
		};
	}
}
