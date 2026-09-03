/*
 * Writer chart-type picker dialog (iOS).
 *
 * Sectioned grid of chart cards (Figma 258:10319 via Android
 * ChartTypePickerUi: 72px preview, 3 columns, section titles 饼图/线图/柱图).
 * Selecting a card inserts via `.uno:InsertObjectChart` + chart2 template.
 */

class WriterEditorChartDialog {
	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '插入图表' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:16px;overflow-y:auto;';

		const viewportWidth = document.documentElement.clientWidth;
		const columns = viewportWidth < 420 ? 2 : 3;
		WriterEditorCatalog.CHART_CATEGORIES.forEach((category) => {
			const title = document.createElement('div');
			title.textContent = category.title;
			title.style.cssText = 'font-size:14px;color:#80868B;';
			content.appendChild(title);

			const grid = document.createElement('div');
			grid.style.cssText =
				'display:grid;grid-template-columns:repeat(' + columns + ',minmax(0,1fr));' +
				'gap:8px;';
			category.types.forEach((type) => {
				grid.appendChild(this.card(type.label, type.unoType));
			});
			content.appendChild(grid);
		});

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}

	private card(label: string, unoType: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.setAttribute('aria-label', '插入' + label);
		button.style.cssText =
			'display:flex;flex-direction:column;align-items:center;' +
			'padding:8px 6px 10px;border:1px solid #E6E8EB;border-radius:12px;' +
			'background:#fff;cursor:pointer;';
		const preview = document.createElement('span');
		preview.style.cssText =
			'width:100%;height:72px;display:flex;align-items:center;justify-content:center;';
		preview.innerHTML = WriterEditorChartPreview.get(unoType);
		button.appendChild(preview);
		const name = document.createElement('span');
		name.textContent = label;
		name.style.cssText = 'font-size:12px;color:#101010;text-align:center;margin-top:4px;';
		button.appendChild(name);
		button.onclick = () => {
			this.controller.insertChart(unoType);
			this.sheet.close();
		};
		return button;
	}
}

/** Chart preview art (parameterised per type). */
class WriterEditorChartPreview {
	static get(unoType: string): string {
		switch (unoType) {
			case 'pie':
				return WriterEditorChartPreview.pie(false, false);
			case 'pie-rounded':
				return WriterEditorChartPreview.pie(true, false);
			case 'pie-exploded':
				return WriterEditorChartPreview.pie(false, true);
			case 'line':
				return WriterEditorChartPreview.line(false);
			case 'line-curve':
				return WriterEditorChartPreview.line(true);
			case 'column':
				return WriterEditorChartPreview.column(false);
			case 'bar':
				return WriterEditorChartPreview.bar();
			case 'column-stacked':
				return WriterEditorChartPreview.column(true);
			default:
				return '';
		}
	}

	private static svg(inner: string): string {
		return '<svg viewBox="0 0 96 48" width="100%" height="48" xmlns="http://www.w3.org/2000/svg">' +
			inner + '</svg>';
	}

	private static pie(rounded: boolean, exploded: boolean): string {
		// Three slices with a separation gap when exploded; ring when rounded.
		const colors = ['#5B9BD5', '#ED7D31', '#A9A9A9'];
		if (exploded) {
			const cx = [36, 50, 58];
			const cy = [25, 24, 24];
			const pieces = [
				'M36,24 L26,8 A24,24 0 0 1 52,8 Z',
				'M56,24 L50,10 A22,22 0 0 1 74,30 Z',
				'M52,32 L64,44 A20,20 0 0 1 34,38 Z',
			];
			return WriterEditorChartPreview.svg(pieces.map((d, i) =>
				'<path d="' + d + '" fill="' + colors[i] + '"/>').join(''));
		}
		if (rounded) {
			return WriterEditorChartPreview.svg(
				'<circle cx="48" cy="25" r="16" fill="none" stroke="#5B9BD5" stroke-width="8"/>' +
				'<circle cx="60" cy="18" r="16" fill="none" stroke="#ED7D31" stroke-width="8" stroke-dasharray="20 80" stroke-dashoffset="0"/>');
		}
		return WriterEditorChartPreview.svg(
			'<path d="M48,25 L48,9 A16,16 0 1 1 36,33 Z" fill="#5B9BD5"/>' +
			'<path d="M48,25 L60,21 A16,16 0 0 1 48,41 Z" fill="#ED7D31"/>');
	}

	private static line(curve: boolean): string {
		const points = '8,38 26,28 44,32 62,16 88,10';
		const path = curve
			? 'M8,38 C20,28 30,32 44,32 C56,32 56,18 62,16 C72,12 78,12 88,10'
			: 'M8,38 L26,28 L44,32 L62,16 L88,10';
		return WriterEditorChartPreview.svg(
			'<path d="' + path + '" fill="none" stroke="#5B9BD5" stroke-width="2.5" stroke-linecap="round"/>' +
			'<circle cx="8" cy="38" r="2.5" fill="#ED7D31"/>' +
			'<circle cx="44" cy="32" r="2.5" fill="#ED7D31"/>' +
			'<circle cx="88" cy="10" r="2.5" fill="#ED7D31"/>');
	}

	private static column(stacked: boolean): string {
		if (stacked) {
			return WriterEditorChartPreview.svg(
				'<rect x="14" y="20" width="14" height="28" fill="#5B9BD5"/>' +
				'<rect x="14" y="10" width="14" height="12" fill="#ED7D31"/>' +
				'<rect x="41" y="26" width="14" height="22" fill="#5B9BD5"/>' +
				'<rect x="41" y="16" width="14" height="12" fill="#ED7D31"/>' +
				'<rect x="68" y="12" width="14" height="36" fill="#5B9BD5"/>' +
				'<rect x="68" y="4" width="14" height="10" fill="#ED7D31"/>');
		}
		return WriterEditorChartPreview.svg(
			'<rect x="14" y="20" width="14" height="28" fill="#5B9BD5"/>' +
			'<rect x="41" y="10" width="14" height="38" fill="#ED7D31"/>' +
			'<rect x="68" y="26" width="14" height="22" fill="#A9A9A9"/>');
	}

	private static bar(): string {
		return WriterEditorChartPreview.svg(
			'<rect x="18" y="8" width="34" height="9" fill="#5B9BD5"/>' +
			'<rect x="34" y="20" width="44" height="9" fill="#ED7D31"/>' +
			'<rect x="24" y="32" width="30" height="9" fill="#A9A9A9"/>');
	}
}