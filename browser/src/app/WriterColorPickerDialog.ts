/*
 * Function-panel color picker (Calc / Impress font & fill).
 *
 * Matches Android CalcFontColorPickerController: inline subpage, F2F3F5 cards,
 * 6-column rows with 20px row gap, check overlay on selection; pick does not close.
 */

interface WriterColorPickerEntry {
	index: number;
	rgb: number;
}

class WriterColorPickerDialog {
	private readonly subpage: { open(): void; close(): void };
	private selectedIndex: number | null;
	private readonly checkNodes: HTMLSpanElement[] = [];

	constructor(
		title: string,
		initialRgb: number | null,
		onPick: (rgb: number) => void,
		host?: WriterEditorInlineSubpageHost | null,
		blocks?: WriterColorPickerEntry[][],
	) {
		const catalog =
			blocks ||
			WriterColorPickerDialog.blocksFromCalcPalette();
		this.selectedIndex = WriterColorPickerDialog.indexForRgb(
			catalog,
			initialRgb,
		);
		const body = this.buildBody(catalog, (entry) => {
			this.selectedIndex = entry.index;
			this.refreshChecks(catalog);
			onPick(entry.rgb);
		});
		this.subpage = writerEditorMountSubpageDialog(title, body, host);
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	private buildBody(
		blocks: WriterColorPickerEntry[][],
		onClick: (entry: WriterColorPickerEntry) => void,
	): HTMLElement {
		const root = document.createElement('div');
		root.className = 'writer-function-color-picker';

		blocks.forEach((block, blockIndex) => {
			if (blockIndex > 0) {
				const gap = document.createElement('div');
				gap.className = 'writer-function-color-picker__block-gap';
				gap.setAttribute('aria-hidden', 'true');
				root.appendChild(gap);
			}
			const card = document.createElement('div');
			card.className = 'writer-function-color-picker__card';
			const cols = 6;
			for (let i = 0; i < block.length; i += cols) {
				const row = document.createElement('div');
				row.className = 'writer-function-color-picker__row';
				if (i > 0) {
					row.classList.add('writer-function-color-picker__row--spaced');
				}
				for (let j = i; j < Math.min(i + cols, block.length); j++) {
					row.appendChild(
						this.swatchCell(block[j], onClick),
					);
				}
				card.appendChild(row);
			}
			root.appendChild(card);
		});
		return root;
	}

	private swatchCell(
		entry: WriterColorPickerEntry,
		onClick: (entry: WriterColorPickerEntry) => void,
	): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-function-color-picker__cell';
		button.setAttribute(
			'aria-label',
			'颜色 #' + WriterColorPickerDialog.hex(entry.rgb),
		);

		const swatch = document.createElement('span');
		swatch.className = 'writer-function-color-picker__swatch';
		swatch.style.backgroundColor =
			'#' + WriterColorPickerDialog.hex(entry.rgb);
		button.appendChild(swatch);

		const check = document.createElement('span');
		check.className = 'writer-function-color-picker__check';
		check.setAttribute('aria-hidden', 'true');
		const light = WriterColorPickerDialog.isLightSwatch(entry.rgb);
		check.style.color = light ? '#333333' : '#ffffff';
		check.innerHTML =
			'<svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';
		const selected =
			this.selectedIndex !== null && this.selectedIndex === entry.index;
		check.hidden = !selected;
		this.checkNodes.push(check);
		button.appendChild(check);

		button.onclick = () => onClick(entry);
		return button;
	}

	private refreshChecks(blocks: WriterColorPickerEntry[][]): void {
		let nodeIndex = 0;
		blocks.forEach((block) => {
			block.forEach((entry) => {
				const check = this.checkNodes[nodeIndex];
				if (check) {
					const selected =
						this.selectedIndex !== null &&
						this.selectedIndex === entry.index;
					check.hidden = !selected;
				}
				nodeIndex++;
			});
		});
	}

	private static blocksFromCalcPalette(): WriterColorPickerEntry[][] {
		let index = 1;
		return WriterEditorCatalog.CHAR_COLOR_BLOCKS.map((block) =>
			block.map((rgb) => ({ index: index++, rgb })),
		);
	}

	private static indexForRgb(
		blocks: WriterColorPickerEntry[][],
		rgb: number | null,
	): number | null {
		if (rgb === null) {
			return null;
		}
		for (const block of blocks) {
			for (const entry of block) {
				if (entry.rgb === rgb) {
					return entry.index;
				}
			}
		}
		return null;
	}

	private static hex(rgb: number): string {
		let text = (rgb & 0xffffff).toString(16).toUpperCase();
		while (text.length < 6) {
			text = '0' + text;
		}
		return text;
	}

	private static isLightSwatch(rgb: number): boolean {
		const r = (rgb >> 16) & 0xff;
		const g = (rgb >> 8) & 0xff;
		const b = rgb & 0xff;
		return 0.299 * r + 0.587 * g + 0.114 * b > 186;
	}
}

class ImpressSolidColorPickerDialog extends WriterColorPickerDialog {
	constructor(
		title: string,
		initialRgb: number | null,
		onPick: (rgb: number) => void,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		super(title, initialRgb, onPick, host, ImpressSolidColorCatalog.BLOCKS);
	}
}
