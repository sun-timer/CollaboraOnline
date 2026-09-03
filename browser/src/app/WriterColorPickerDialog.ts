/*
 * Writer color picker dialog (iOS).
 *
 * 36-swatch grid (24 + 12 blocks, 6 columns) transcribed from Android
 * CalcFontColorPickerController + CalcFontColorCatalog (Figma 123:8867):
 * picking applies the color without closing; the caller closes on demand.
 */

class WriterColorPickerDialog {
	private readonly sheet: MobileAiSheet;
	private readonly onPick: (rgb: number) => void;
	private selectedIndex: number;

	constructor(
		title: string,
		initialRgb: number | null,
		onPick: (rgb: number) => void,
	) {
		this.onPick = onPick;
		this.sheet = new MobileAiSheet({ title });
		const flat = WriterColorPickerDialog.flatPalette();
		this.selectedIndex = initialRgb === null ? -1 : flat.indexOf(initialRgb);
		this.sheet.setBody(this.buildBody(flat));
	}

	open(): void {
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}

	private buildBody(flat: number[]): HTMLElement {
		const content = document.createElement('div');
		content.style.cssText =
			'display:flex;flex-direction:column;gap:12px;overflow-y:auto;';

		WriterEditorCatalog.CHAR_COLOR_BLOCKS.forEach((block) => {
			const card = document.createElement('div');
			card.style.cssText =
				'background:#fff;border:1px solid #E6E8EB;border-radius:12px;' +
				'padding:12px;';
			const grid = document.createElement('div');
			grid.style.cssText =
				'display:grid;grid-template-columns:repeat(6,minmax(0,1fr));' +
				'gap:12px;justify-items:center;';
			block.forEach((rgb) => {
				grid.appendChild(this.swatch(flat, rgb));
			});
			card.appendChild(grid);
			content.appendChild(card);
		});
		return content;
	}

	private swatch(flat: number[], rgb: number): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.setAttribute('aria-label', '颜色 #' + WriterColorPickerDialog.hex(rgb));
		button.style.cssText =
			'width:32px;height:32px;border-radius:50%;border:1px solid #d8dde3;' +
			'background:#' + WriterColorPickerDialog.hex(rgb) + ';cursor:pointer;';
		if (flat.indexOf(rgb) === this.selectedIndex) {
			button.style.boxShadow = 'inset 0 0 0 2px #1278D9';
		}
		button.onclick = () => {
			this.selectedIndex = flat.indexOf(rgb);
			this.onPick(rgb);
			// Move the selection ring to this swatch.
			document.querySelectorAll('[data-writer-swatch]').forEach((node) => {
				(node as HTMLElement).style.boxShadow = '';
			});
			button.style.boxShadow = 'inset 0 0 0 2px #1278D9';
		};
		button.setAttribute('data-writer-swatch', '1');
		return button;
	}

	private static flatPalette(): number[] {
		const out: number[] = [];
		WriterEditorCatalog.CHAR_COLOR_BLOCKS.forEach((block) =>
			block.forEach((rgb) => out.push(rgb)));
		return out;
	}

	private static hex(rgb: number): string {
		let text = rgb.toString(16).toUpperCase();
		while (text.length < 6) {
			text = '0' + text;
		}
		return text;
	}
}