/*
 * Chart-type picker for Writer / Calc / Impress function panels.
 *
 * Layout transcribed from Android ChartTypePickerUi (Figma 258:10319):
 * section titles 饼图/线图/柱图, 3 equal columns per row, #F2F3F5 cards,
 * 72px-tall previews, title「图表」via inline subpage header.
 */

class WriterEditorChartDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private readonly onInserted?: () => void;

	private static readonly MAX_COLUMNS = 3;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.controller = controller;
		this.onInserted = onInserted;
		this.subpage = writerEditorMountSubpageDialog(
			'图表',
			this.buildBody(),
			host,
		);
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	private buildBody(): HTMLElement {
		const root = document.createElement('div');
		root.className = 'writer-function-chart-picker';

		WriterEditorCatalog.CHART_CATEGORIES.forEach((category, sectionIndex) => {
			if (sectionIndex > 0) {
				const sectionGap = document.createElement('div');
				sectionGap.className = 'writer-function-chart-picker__section-gap';
				sectionGap.setAttribute('aria-hidden', 'true');
				root.appendChild(sectionGap);
			}

			const title = document.createElement('div');
			title.className = 'writer-function-chart-picker__section-title';
			title.textContent = category.title;
			root.appendChild(title);

			const row = document.createElement('div');
			row.className = 'writer-function-chart-picker__row';
			if (sectionIndex > 0) {
				row.classList.add('writer-function-chart-picker__row--spaced');
			}
			for (let slot = 0; slot < WriterEditorChartDialog.MAX_COLUMNS; slot++) {
				const slotWrap = document.createElement('div');
				slotWrap.className = 'writer-function-chart-picker__slot';
				const type = category.types[slot];
				if (type) {
					slotWrap.appendChild(this.card(type.label, type.unoType));
				}
				row.appendChild(slotWrap);
			}
			root.appendChild(row);
		});
		return root;
	}

	private card(label: string, unoType: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-function-chart-picker__card';
		button.setAttribute('aria-label', label);

		const preview = document.createElement('span');
		preview.className = 'writer-function-chart-picker__preview';
		preview.innerHTML = WriterEditorChartPreviewIcons.get(unoType);
		button.appendChild(preview);

		const name = document.createElement('span');
		name.className = 'writer-function-chart-picker__label';
		name.textContent = label;
		button.appendChild(name);

		button.onclick = () => {
			this.controller.insertChart(unoType);
			this.subpage.close();
			if (this.onInserted) {
				this.onInserted();
			}
		};
		return button;
	}
}
