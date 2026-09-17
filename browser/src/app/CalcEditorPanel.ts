/*
 * iOS Calc edit-mode function panel (常用 / 文件 / 插入 / 布局 / 数据 / 审阅).
 */

class CalcEditorPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly tabBar: HTMLDivElement;
	private readonly tabScroll: HTMLDivElement;
	private readonly content: HTMLDivElement;
	private activeTab: CalcEditorTab = 'default';
	private readonly subDialogs: { close(): void }[] = [];
	private readonly inlineSubpage: WriterEditorInlineSubpage;
	private readonly controller = WriterEditorController.getInstance();
	private readonly pickerLabels: Record<string, string> = {
		'font-name': '宋体',
		'font-size': '四号',
		'paper-orientation': '纵向',
		'print-area': 'A4',
	};
	private fontColorRgb = 0x000000;
	private backgroundColorRgb = 0xffffff;
	private borderColorRgb = 0x000000;
	private readonly calcToggleState: Record<string, boolean> = {
		'negative-red': true,
		'thousands-sep': false,
		'vertical-stack': false,
		'wrap-text': false,
		'grid-lines': true,
	};

	private constructor() {
		this.sheet = new WriterEditorSheet('', undefined, { editMode: true });
		const panelRoot = document.createElement('div');
		panelRoot.className = 'writer-function-panel';

		this.tabBar = document.createElement('div');
		this.tabBar.className = 'writer-function-tab-bar';
		this.tabScroll = document.createElement('div');
		this.tabScroll.className = 'writer-function-tab-scroll';
		const divider = document.createElement('div');
		divider.className = 'writer-function-tab-divider';
		divider.setAttribute('aria-hidden', 'true');
		const actions = document.createElement('div');
		actions.className = 'writer-function-tab-actions';
		[
			{ icon: 'ai-sparkle', aria: 'AI功能', handler: () => this.openAiFeatures() },
			{ icon: 'keyboard', aria: '键盘', handler: () => this.showKeyboard() },
			{ icon: 'collapse', aria: '收起', handler: () => this.close() },
		].forEach((action) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-action-btn';
			button.setAttribute('aria-label', action.aria);
			const icon = WriterEditorIcons.get(action.icon);
			if (icon) {
				button.innerHTML = icon;
			}
			button.onclick = action.handler;
			actions.appendChild(button);
		});
		this.tabBar.appendChild(this.tabScroll);
		this.tabBar.appendChild(divider);
		this.tabBar.appendChild(actions);
		panelRoot.appendChild(this.tabBar);

		this.content = document.createElement('div');
		this.content.className = 'writer-function-content';
		panelRoot.appendChild(this.content);

		this.sheet.setBody(panelRoot);
		this.inlineSubpage = new WriterEditorInlineSubpage(this.sheet.body, [
			this.content,
		]);
	}

	private subHost(): WriterEditorInlineSubpageHost {
		return this.inlineSubpage;
	}

	static mount(): CalcEditorPanel | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolCalcEditorPanel;
		if (existing instanceof CalcEditorPanel) {
			return existing;
		}
		const panel = new CalcEditorPanel();
		(window as any).__coolCalcEditorPanel = panel;
		return panel;
	}

	open(): void {
		MobileDocTheme.applyFromMap();
		this.syncToggleStateFromDocument();
		this.renderTabs();
		this.renderContent();
		this.sheet.open();
	}

	close(): void {
		const children = this.subDialogs.splice(0);
		children.forEach((dialog) => dialog.close());
		this.inlineSubpage.clearSubpages();
		this.sheet.close();
	}

	private openAiFeatures(): void {
		this.close();
		const panel = (window as any).__coolWriterAiPanel;
		if (panel && typeof panel.openOperationSheet === 'function') {
			panel.openOperationSheet();
		}
	}

	private showKeyboard(): void {
		this.close();
		const map = (window as any).app && (window as any).app.map;
		if (map && typeof map.focus === 'function') {
			map.focus(true);
		}
	}

	private renderTabs(): void {
		this.tabScroll.replaceChildren();
		CalcEditorCatalog.TABS.forEach((tab) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.textContent = tab.label;
			button.setAttribute('aria-label', tab.label);
			const active = tab.id === this.activeTab;
			button.className =
				'writer-function-tab' + (active ? ' writer-function-tab--active' : '');
			button.onclick = () => {
				this.activeTab = tab.id;
				this.renderTabs();
				this.renderContent();
			};
			this.tabScroll.appendChild(button);
		});
	}

	private renderContent(): void {
		this.content.replaceChildren();
		const features = CalcEditorCatalog.getFeatures(this.activeTab);
		if (this.activeTab === 'insert') {
			this.renderActionList(features);
			return;
		}
		if (this.activeTab === 'file' || this.activeTab === 'data' || this.activeTab === 'review') {
			this.renderActionList(features);
			return;
		}
		if (this.activeTab === 'layout') {
			this.renderLayoutTab(features);
			return;
		}
		this.renderDefaultTab(features);
	}

	private renderActionList(features: CalcEditorFeature[]): void {
		const stack = document.createElement('div');
		stack.className = 'writer-function-stack';
		const group = document.createElement('div');
		group.className = 'writer-function-action-group';
		const listFeatures = features.filter((f) => f.kind !== 'section');
		listFeatures.forEach((feature, index) => {
			group.appendChild(this.createActionRow(feature));
			if (index + 1 < listFeatures.length) {
				group.appendChild(this.createRowDivider());
			}
		});
		stack.appendChild(group);
		this.content.appendChild(stack);
	}

	private renderLayoutTab(features: CalcEditorFeature[]): void {
		const stack = document.createElement('div');
		stack.className = 'writer-function-stack writer-function-stack--calc-layout';
		features.forEach((feature) => {
			if (feature.kind === 'iconValueRow') {
				stack.appendChild(this.wrapCalcLayoutRow(this.createIconValueRow(feature)));
				return;
			}
			if (feature.kind === 'toggle' && feature.id === 'grid-lines') {
				stack.appendChild(this.createGridLinesSwitchRow(feature));
			}
		});
		this.content.appendChild(stack);
	}

	private wrapCalcLayoutRow(row: HTMLElement): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-layout-row-wrap';
		wrap.appendChild(row);
		const divider = document.createElement('div');
		divider.className = 'writer-function-calc-hairline';
		divider.setAttribute('aria-hidden', 'true');
		wrap.appendChild(divider);
		return wrap;
	}

	private renderDefaultTab(features: CalcEditorFeature[]): void {
		const stack = document.createElement('div');
		stack.className = 'writer-function-stack';
		features.forEach((feature) => {
			switch (feature.kind) {
				case 'section':
					stack.appendChild(this.createSectionHeader(feature.label));
					break;
				case 'dialog':
					if (feature.id === 'font-name') {
						stack.appendChild(this.createCalcFontNameRow());
					}
					break;
				case 'splitPicker':
					stack.appendChild(this.createFontSizeColorRow());
					break;
				case 'charTools':
					stack.appendChild(this.createCharToolGrid());
					break;
				case 'iconGrid':
					if (feature.grid) {
						stack.appendChild(this.createIconGrid(feature));
					}
					break;
				case 'stepperPair':
					stack.appendChild(this.createStepperPair(feature.stepperPairId));
					break;
				case 'togglePair':
					stack.appendChild(this.createTogglePair(feature.togglePairId));
					break;
				case 'colorPickerPair':
					stack.appendChild(this.createColorPickerPair());
					break;
				case 'command':
					if (feature.id === 'merge-cells') {
						stack.appendChild(this.createFlatCalcActionRow(feature));
						stack.appendChild(this.createCalcHairlineDivider());
					}
					break;
				case 'toggle':
					stack.appendChild(this.createCalcToggleCard(feature));
					break;
				default:
					break;
			}
		});
		this.content.appendChild(stack);
	}

	private createSectionHeader(title: string): HTMLHeadingElement {
		const header = document.createElement('h3');
		header.className =
			'writer-function-section-title writer-function-section-title--calc';
		header.textContent = title;
		return header;
	}

	private createRowDivider(): HTMLDivElement {
		const divider = document.createElement('div');
		divider.className = 'writer-function-action-row__divider';
		return divider;
	}

	private createActionRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-action-row writer-function-action-row--grouped';
		row.setAttribute('aria-label', feature.label);
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-action-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-action-row__label';
		label.textContent = feature.label;
		row.appendChild(label);
		if (feature.kind === 'submenu') {
			const chevron = document.createElement('span');
			chevron.className = 'writer-function-action-row__chevron';
			chevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
			row.appendChild(chevron);
		}
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createCalcFontNameRow(): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-value-picker-row';
		row.setAttribute('aria-label', '字体');
		const value = document.createElement('span');
		value.className = 'writer-function-calc-value-picker-row__value';
		value.textContent = this.pickerLabels['font-name'];
		row.appendChild(value);
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-calc-value-picker-row__chevron';
		chevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
		row.appendChild(chevron);
		row.onclick = () => this.openFontNameDialog();
		return row;
	}

	private createFlatCalcActionRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-flat-action-row';
		row.setAttribute('aria-label', feature.label);
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-calc-flat-action-row__icon';
			iconWrap.innerHTML = WriterEditorIcons.get(feature.icon);
			row.appendChild(iconWrap);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-calc-flat-action-row__label';
		label.textContent = feature.label;
		row.appendChild(label);
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createCalcHairlineDivider(): HTMLDivElement {
		const divider = document.createElement('div');
		divider.className = 'writer-function-calc-hairline';
		divider.setAttribute('aria-hidden', 'true');
		return divider;
	}

	private createPickerRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-picker-row';
		row.setAttribute('aria-label', feature.label);
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-picker-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);
		}
		const title = document.createElement('span');
		title.className = 'writer-function-picker-row__title';
		title.textContent = feature.label;
		row.appendChild(title);
		const value = document.createElement('span');
		value.className = 'writer-function-picker-row__value';
		const key = feature.id === 'font-name' ? 'font-name' : feature.id;
		value.textContent = this.pickerLabels[key] || feature.label;
		row.appendChild(value);
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-picker-row__chevron';
		chevron.textContent = '›';
		row.appendChild(chevron);
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createFontSizeColorRow(): HTMLDivElement {
		const row = document.createElement('div');
		row.className = 'writer-function-split-picker-row';

		const sizeButton = document.createElement('button');
		sizeButton.type = 'button';
		sizeButton.className = 'writer-function-split-picker-row__cell';
		sizeButton.setAttribute('aria-label', '字号');
		const sizeValue = document.createElement('span');
		sizeValue.className = 'writer-function-split-picker-row__value';
		sizeValue.textContent = this.pickerLabels['font-size'];
		sizeButton.appendChild(sizeValue);
		const sizeChevron = document.createElement('span');
		sizeChevron.className = 'writer-function-split-picker-row__chevron';
		sizeChevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
		sizeButton.appendChild(sizeChevron);
		sizeButton.onclick = () => this.openFontSizeDialog();

		const colorButton = document.createElement('button');
		colorButton.type = 'button';
		colorButton.className = 'writer-function-split-picker-row__cell';
		colorButton.setAttribute('aria-label', '字体颜色');
		const swatch = document.createElement('span');
		swatch.className = 'writer-function-split-picker-row__swatch';
		swatch.style.backgroundColor = CalcEditorPanel.rgbCss(this.fontColorRgb);
		colorButton.appendChild(swatch);
		const colorLabel = document.createElement('span');
		colorLabel.className = 'writer-function-split-picker-row__label';
		colorLabel.textContent = '字体颜色';
		colorButton.appendChild(colorLabel);
		const colorChevron = document.createElement('span');
		colorChevron.className = 'writer-function-split-picker-row__chevron';
		colorChevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
		colorButton.appendChild(colorChevron);
		colorButton.onclick = () => this.openFontColorDialog();

		row.appendChild(sizeButton);
		row.appendChild(colorButton);
		return row;
	}

	private static rgbCss(rgb: number): string {
		let hex = rgb.toString(16).toUpperCase();
		while (hex.length < 6) {
			hex = '0' + hex;
		}
		return '#' + hex;
	}

	private createCharToolGrid(): HTMLDivElement {
		const container = document.createElement('div');
		container.className =
			'writer-function-char-tools writer-function-char-tools--calc';
		const appendRow = (tools: typeof CalcEditorCatalog.CHAR_TOOLS_ROW1) => {
			const row = document.createElement('div');
			row.className =
				'writer-function-char-tools__row writer-function-char-tools__row--calc';
			tools.forEach((tool) => {
				const button = document.createElement('button');
				button.type = 'button';
				button.className = 'writer-function-char-tools__btn';
				button.setAttribute('aria-label', tool.label);
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-char-tools__icon';
				const icon = WriterEditorIcons.get(tool.icon);
				if (icon) {
					iconWrap.innerHTML = icon;
				}
				button.appendChild(iconWrap);
				button.onclick = () => this.sendUno(tool.unocmd);
				row.appendChild(button);
			});
			container.appendChild(row);
		};
		appendRow(CalcEditorCatalog.CHAR_TOOLS_ROW1);
		appendRow(CalcEditorCatalog.CHAR_TOOLS_ROW2);
		return container;
	}

	private createCalcGridChip(
		cell: CalcEditorGridCell,
		mode: 'format' | 'iconCompact',
		gridFeatureId?: string,
	): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		const iconHtml =
			cell.icon && WriterEditorIcons.has(cell.icon)
				? WriterEditorIcons.get(cell.icon)
				: '';
		if (mode === 'iconCompact') {
			button.className = 'writer-function-calc-icon-cell';
		} else {
			button.className =
				'writer-function-chip writer-function-chip--calc' +
				(iconHtml ? '' : ' writer-function-chip--text-only');
		}
		button.setAttribute('aria-label', cell.label);
		if (iconHtml) {
			const iconWrap = document.createElement('span');
			iconWrap.className =
				mode === 'iconCompact'
					? 'writer-function-calc-icon-cell__icon'
					: 'writer-function-chip__icon';
			iconWrap.innerHTML = iconHtml;
			button.appendChild(iconWrap);
		} else if (mode === 'iconCompact') {
			const text = document.createElement('span');
			text.className = 'writer-function-calc-icon-cell__text';
			text.textContent = cell.label.slice(0, 2);
			button.appendChild(text);
		}
		if (mode === 'format') {
			const label = document.createElement('span');
			label.className = 'writer-function-chip__label';
			label.textContent = cell.label;
			button.appendChild(label);
		}
		button.onclick = () => {
			if (gridFeatureId === 'border-styles' && cell.icon) {
				CalcEditorBorder.applyPreset(cell.icon, this.borderColorRgb);
				return;
			}
			if (cell.unocmd) {
				this.sendUno(cell.unocmd);
			}
		};
		return button;
	}

	private static calcGridRowSizes(
		cellCount: number,
		cols: number,
		rowCols?: number[],
	): number[] {
		if (rowCols && rowCols.length) {
			const sizes: number[] = [];
			let offset = 0;
			for (const count of rowCols) {
				if (offset >= cellCount) {
					break;
				}
				sizes.push(Math.min(count, cellCount - offset));
				offset += count;
			}
			while (offset < cellCount) {
				const n = Math.min(cols, cellCount - offset);
				sizes.push(n);
				offset += n;
			}
			return sizes;
		}
		const sizes: number[] = [];
		for (let offset = 0; offset < cellCount; offset += cols) {
			sizes.push(Math.min(cols, cellCount - offset));
		}
		return sizes;
	}

	private static gridMaxCols(cols: number, rowCols?: number[]): number {
		let max = cols > 0 ? cols : 5;
		if (rowCols) {
			rowCols.forEach((n) => {
				if (n > max) {
					max = n;
				}
			});
		}
		return max;
	}

	private createIconGrid(feature: CalcEditorFeature): HTMLDivElement {
		const cells = feature.grid || [];
		const cols = feature.gridCols || 5;
		const rowCols = feature.gridRowCols;
		const mode = feature.gridMode || 'format';
		const wrap = document.createElement('div');
		wrap.className =
			mode === 'iconCompact'
				? 'writer-function-calc-icon-grid'
				: 'writer-function-chip-grid writer-function-chip-grid--calc';
		let outer: HTMLDivElement = wrap;
		if (feature.wrapInCard) {
			const card = document.createElement('div');
			card.className = 'writer-function-calc-icon-grid__card';
			card.appendChild(wrap);
			outer = card;
		}
		const rowSizes = CalcEditorPanel.calcGridRowSizes(
			cells.length,
			cols,
			rowCols,
		);
		const maxCols =
			mode === 'iconCompact'
				? CalcEditorPanel.gridMaxCols(cols, rowCols)
				: 0;
		let offset = 0;
		rowSizes.forEach((rowCount) => {
			const row = document.createElement('div');
			row.className =
				mode === 'iconCompact'
					? 'writer-function-calc-icon-grid__row'
					: 'writer-function-chip-grid__row writer-function-chip-grid__row--calc';
			if (mode !== 'iconCompact') {
				row.style.setProperty('--writer-function-grid-cols', String(rowCount));
			} else {
				row.style.setProperty('--writer-function-grid-cols', String(maxCols));
			}
			for (let i = 0; i < rowCount && offset < cells.length; i++, offset++) {
				row.appendChild(this.createCalcGridChip(cells[offset], mode, feature.id));
			}
			if (mode === 'iconCompact') {
				for (let pad = rowCount; pad < maxCols; pad++) {
					const spacer = document.createElement('span');
					spacer.className = 'writer-function-calc-icon-grid__spacer';
					row.appendChild(spacer);
				}
			}
			wrap.appendChild(row);
		});
		return outer;
	}

	private createCalcCommandRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-command-row';
		row.setAttribute('aria-label', feature.label);
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-calc-command-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-calc-command-row__label';
		label.textContent = feature.label;
		row.appendChild(label);
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createCalcToggleCard(feature: CalcEditorFeature): HTMLButtonElement {
		const id = feature.id;
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-toggle-card';
		const label = document.createElement('span');
		label.className = 'writer-function-calc-toggle-card__label';
		label.textContent = feature.label;
		const indicator = document.createElement('span');
		indicator.className = 'writer-function-calc-toggle-card__indicator';
		const sync = () => {
			const on = !!this.calcToggleState[id];
			const iconKey = on ? 'calc-toggle-checked' : 'calc-toggle-unchecked';
			indicator.innerHTML = WriterEditorIcons.get(iconKey);
		};
		if (this.calcToggleState[id] === undefined) {
			this.calcToggleState[id] = !!feature.defaultOn;
		}
		sync();
		row.appendChild(label);
		row.appendChild(indicator);
		row.onclick = () => {
			this.calcToggleState[id] = !this.calcToggleState[id];
			sync();
			if (feature.unocmd) {
				this.sendUno(feature.unocmd);
			}
		};
		return row;
	}

	private createTogglePair(
		pairId: CalcEditorFeature['togglePairId'],
	): HTMLDivElement {
		const row = document.createElement('div');
		row.className = 'writer-function-calc-toggle-pair';
		if (pairId === 'stack_wrap_toggles') {
			row.appendChild(
				this.createCalcToggleCard({
					id: 'vertical-stack',
					label: '纵向排列',
					tab: 'default',
					icon: '',
					kind: 'toggle',
					unocmd: '.uno:StackCharacterLeftToRight',
				}),
			);
			row.appendChild(
				this.createCalcToggleCard({
					id: 'wrap-text',
					label: '文本换行',
					tab: 'default',
					icon: '',
					kind: 'toggle',
					unocmd: '.uno:WrapText',
				}),
			);
		} else {
			row.appendChild(
				this.createCalcToggleCard({
					id: 'negative-red',
					label: '负值显示为红色',
					tab: 'default',
					icon: '',
					kind: 'toggle',
					unocmd: '.uno:NegativeNumberRed',
					defaultOn: true,
				}),
			);
			row.appendChild(
				this.createCalcToggleCard({
					id: 'thousands-sep',
					label: '千位分隔符',
					tab: 'default',
					icon: '',
					kind: 'toggle',
					unocmd: '.uno:NumberFormatThousands',
				}),
			);
		}
		return row;
	}

	private createStepperColumn(
		title: string,
		preview: string,
		incCmd: string,
		decCmd: string,
	): HTMLDivElement {
		const col = document.createElement('div');
		col.className = 'writer-function-calc-stepper-col';
		const heading = document.createElement('div');
		heading.className = 'writer-function-calc-stepper-col__title';
		heading.textContent = title;
		const card = document.createElement('div');
		card.className = 'writer-function-calc-stepper-card';
		const value = document.createElement('span');
		value.className = 'writer-function-calc-stepper-card__value';
		value.textContent = preview;
		const buttons = document.createElement('div');
		buttons.className = 'writer-function-calc-stepper-card__buttons';
		const minus = document.createElement('button');
		minus.type = 'button';
		minus.className = 'writer-function-calc-stepper-card__btn';
		minus.innerHTML = WriterEditorIcons.get('calc-stepper-minus');
		minus.onclick = () => this.sendUno(decCmd);
		const plus = document.createElement('button');
		plus.type = 'button';
		plus.className = 'writer-function-calc-stepper-card__btn';
		plus.innerHTML = WriterEditorIcons.get('calc-stepper-plus');
		plus.onclick = () => this.sendUno(incCmd);
		buttons.appendChild(minus);
		buttons.appendChild(plus);
		card.appendChild(value);
		card.appendChild(buttons);
		col.appendChild(heading);
		col.appendChild(card);
		return col;
	}

	private createStepperPair(
		pairId: CalcEditorFeature['stepperPairId'],
	): HTMLDivElement {
		const row = document.createElement('div');
		row.className = 'writer-function-calc-stepper-pair';
		if (pairId === 'indent_steppers') {
			row.appendChild(
				this.createStepperColumn(
					'缩进',
					'0 点(pt)',
					'.uno:IncrementIndent',
					'.uno:DecrementIndent',
				),
			);
			row.appendChild(
				this.createStepperColumn(
					'文本方向',
					'0 °',
					'.uno:TextDirection',
					'.uno:TextDirection',
				),
			);
		} else {
			row.appendChild(
				this.createStepperColumn(
					'小数位数',
					'0.0 1',
					'.uno:NumberFormatIncDecimals',
					'.uno:NumberFormatDecDecimals',
				),
			);
			row.appendChild(
				this.createStepperColumn(
					'前导零',
					'0.0 1',
					'.uno:LeadingZeroes',
					'.uno:LeadingZeroes',
				),
			);
		}
		return row;
	}

	private createColorPickerHalf(
		id: string,
		title: string,
		iconKey: string,
		rgb: number,
		onPick: () => void,
	): HTMLButtonElement {
		const cell = document.createElement('button');
		cell.type = 'button';
		cell.className = 'writer-function-calc-color-half';
		const swatch = document.createElement('span');
		swatch.className = 'writer-function-calc-color-half__swatch';
		if (WriterEditorIcons.has(iconKey)) {
			swatch.innerHTML = WriterEditorIcons.get(iconKey);
		} else {
			swatch.style.backgroundColor = CalcEditorPanel.rgbCss(rgb);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-calc-color-half__label';
		label.textContent = title;
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-calc-color-half__chevron';
		chevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
		cell.appendChild(swatch);
		cell.appendChild(label);
		cell.appendChild(chevron);
		cell.onclick = onPick;
		cell.setAttribute('aria-label', title);
		return cell;
	}

	private createColorPickerPair(): HTMLDivElement {
		const row = document.createElement('div');
		row.className = 'writer-function-calc-color-pair';
		row.appendChild(
			this.createColorPickerHalf(
				'bg_color',
				'背景颜色',
				'calc-color-bg-preview',
				this.backgroundColorRgb,
				() => this.openBackgroundColorDialog(),
			),
		);
		row.appendChild(
			this.createColorPickerHalf(
				'border_color',
				'边框颜色',
				'calc-color-border-preview',
				this.borderColorRgb,
				() => this.openBorderColorDialog(),
			),
		);
		return row;
	}

	private createIconValueRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-icon-value-row';
		row.setAttribute('aria-label', feature.label);
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-calc-icon-value-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-calc-icon-value-row__label';
		label.textContent = feature.label;
		row.appendChild(label);
		const value = document.createElement('span');
		value.className = 'writer-function-calc-icon-value-row__value';
		value.textContent =
			this.pickerLabels[feature.id] || feature.valueLabel || '';
		row.appendChild(value);
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-calc-icon-value-row__chevron';
		chevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
		row.appendChild(chevron);
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createGridLinesSwitchRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-switch-row';
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-calc-switch-row__icon';
			iconWrap.innerHTML = WriterEditorIcons.get(feature.icon);
			row.appendChild(iconWrap);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-calc-switch-row__label';
		label.textContent = feature.label;
		row.appendChild(label);
		const indicator = document.createElement('span');
		indicator.className = 'writer-function-calc-switch-row__switch';
		const sync = () => {
			const on = !!this.calcToggleState['grid-lines'];
			indicator.innerHTML = WriterEditorIcons.get(
				on ? 'calc-switch-on' : 'calc-switch-off',
			);
		};
		sync();
		row.appendChild(indicator);
		row.onclick = () => {
			this.calcToggleState['grid-lines'] =
				!this.calcToggleState['grid-lines'];
			sync();
			if (feature.unocmd) {
				this.sendUno(feature.unocmd);
			}
		};
		return row;
	}

	private onFeature(feature: CalcEditorFeature): void {
		if (feature.id === 'merge-cells') {
			this.openMergeCellsDialog();
			return;
		}
		if (feature.kind === 'submenu' && feature.submenu) {
			this.openSubmenuPage(feature.label, feature.submenu);
			return;
		}
		if (feature.kind === 'save') {
			this.mobileSave();
			this.close();
			return;
		}
		if (feature.id === 'data-validation') {
			this.openDataValidationDialog();
			return;
		}
		if (feature.id === 'spell-check') {
			this.deferUnoAfterClose('.uno:SpellDialog');
			return;
		}
		if (feature.kind === 'export') {
			this.controller.exportAs('pdf');
			this.close();
			return;
		}
		if (feature.kind === 'print') {
			if (typeof (window as any).postMobileMessage === 'function') {
				(window as any).postMobileMessage('PRINT');
			}
			this.close();
			return;
		}
		if (feature.kind === 'dialog' || feature.kind === 'iconValueRow') {
			this.openDialog(feature);
			return;
		}
		if (feature.unocmd) {
			this.sendUno(feature.unocmd);
		}
	}

	private openSubmenuPage(title: string, items: CalcEditorFeature[]): void {
		const stack = document.createElement('div');
		stack.className = 'writer-function-stack';
		const group = document.createElement('div');
		group.className = 'writer-function-action-group';
		items.forEach((item, index) => {
			group.appendChild(this.createSubmenuActionRow(item));
			if (index + 1 < items.length) {
				group.appendChild(this.createRowDivider());
			}
		});
		stack.appendChild(group);
		this.subHost().pushSubpage(title, stack);
	}

	private createSubmenuActionRow(feature: CalcEditorFeature): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-action-row writer-function-action-row--grouped';
		row.setAttribute('aria-label', feature.label);
		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-action-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);
		}
		const label = document.createElement('span');
		label.className = 'writer-function-action-row__label';
		label.textContent = feature.label;
		row.appendChild(label);
		row.onclick = () => {
			this.subHost().popSubpage();
			this.onFeature(feature);
		};
		return row;
	}

	private openDialog(feature: CalcEditorFeature): void {
		switch (feature.dialog) {
			case 'fontName':
				this.openFontNameDialog();
				break;
			case 'fontSize':
				this.openFontSizeDialog();
				break;
			case 'fontColor':
				this.openFontColorDialog();
				break;
			case 'backgroundColor':
				this.openBackgroundColorDialog();
				break;
			case 'borderColor':
				this.openBorderColorDialog();
				break;
			case 'chart':
				this.presentSub(
					new WriterEditorChartDialog(
						this.controller,
						this.subHost(),
						() => this.close(),
					),
				);
				break;
			case 'comment':
				this.presentSub(
					new WriterEditorCommentDialog(
						this.controller,
						this.subHost(),
						() => this.close(),
					),
				);
				break;
			case 'calcShape':
				this.presentSub(
					new CalcEditorShapeDialog(
						(cmd) => this.sendUno(cmd),
						this.subHost(),
						() => this.close(),
					),
				);
				break;
			case 'shape':
				this.presentSub(new WriterEditorShapeDialog(this.controller, this.subHost()));
				break;
			case 'dataValidation':
				this.openDataValidationDialog();
				break;
			case 'saveAs':
				this.openSaveAsDialog();
				break;
			case 'paperOrientation':
				this.openPaperOrientationDialog();
				break;
			case 'printArea':
				this.openPrintAreaDialog();
				break;
			case 'hyperlink':
				this.presentSub(
					new CalcEditorHyperlinkDialog(
						this.controller,
						this.subHost(),
						() => this.close(),
					),
				);
				break;
			case 'image':
				this.close();
				this.controller.requestNativeImagePicker();
				break;
			default:
				break;
		}
	}

	private openFontNameDialog(): void {
		const options: WriterChooseOption[] = this.getFontOptions().map((name) => ({
			label: name,
			value: name,
		}));
		this.presentSub(
			new WriterEditorChooseDialog(
				'字体',
				options,
				(option) => {
					this.pickerLabels['font-name'] = option.label;
					this.controller.applyFontName(WriterEditorCatalog.aliasFont(option.value));
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openFontSizeDialog(): void {
		const table = WriterEditorCatalog.CHAR_HEIGHT_CN;
		const options = Object.keys(table).map((label) => ({ label, value: table[label] }));
		this.presentSub(
			new WriterEditorChooseDialog(
				'字号',
				options,
				(option) => {
					this.pickerLabels['font-size'] = option.label;
					this.controller.applyFontSize(option.value);
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openFontColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog(
				'字体颜色',
				this.fontColorRgb,
				(rgb) => {
					this.fontColorRgb = rgb;
					this.sendUno(
						'.uno:Color {"Color.Color":{"type":"long","value":' +
							rgb +
							'}}',
					);
				},
				this.subHost(),
			),
		);
	}

	private openBackgroundColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog(
				'背景颜色',
				this.backgroundColorRgb,
				(rgb) => {
					this.backgroundColorRgb = rgb;
					this.sendUno(
						'.uno:BackgroundColor {"BackgroundColor.Color":{"type":"long","value":' +
							rgb +
							'}}',
					);
				},
				this.subHost(),
			),
		);
	}

	private openBorderColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog(
				'边框颜色',
				this.borderColorRgb,
				(rgb) => {
					this.borderColorRgb = rgb;
					this.sendUno(CalcEditorBorder.buildBorderColorUnoCommand(rgb));
				},
				this.subHost(),
			),
		);
	}

	private openSaveAsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'ODF 电子表格 (.ods)', value: 'ods' },
			{ label: 'Excel (.xlsx)', value: 'xlsx' },
			{ label: 'CSV (.csv)', value: 'csv' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'另存为',
				options,
				(option) => {
					this.controller.saveAs(option.value);
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openPrintAreaDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: '设置打印区域', value: 'define' },
			{ label: '清除打印区域', value: 'clear' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'打印区域',
				options,
				(option) => {
					if (option.value === 'clear') {
						this.sendUno('.uno:ResetAttributes');
					} else {
						this.sendUno('.uno:DefinePrintArea');
					}
					if (this.activeTab === 'layout') {
						this.renderContent();
					}
				},
				this.pickerLabels['print-area'],
				this.subHost(),
			),
		);
	}

	private openMergeCellsDialog(): void {
		const options: WriterChooseOption[] = [
			{
				label: '合并内容',
				value: '.uno:MergeCells?MoveContents:bool=true',
			},
			{
				label: '合并单元格',
				value: '.uno:MergeCells?MoveContents:bool=false',
			},
			{
				label: '合并相同单元格',
				value: '.uno:MergeCells?MoveContents:bool=false',
			},
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'合并单元格',
				options,
				(option) => {
					this.close();
					this.sendUno(option.value);
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openPaperOrientationDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: '纵向', value: 'portrait' },
			{ label: '横向', value: 'landscape' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'纸张方向',
				options,
				(option) => {
					this.pickerLabels['paper-orientation'] = option.label;
					const cmd =
						option.value === 'landscape'
							? '.uno:Orientation?isLandscape:bool=true'
							: '.uno:Orientation?isLandscape:bool=false';
					this.sendUno(cmd);
					if (this.activeTab === 'layout') {
						this.renderContent();
					}
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private getFontOptions(): string[] {
		const values = this.controller.getCommandValues('.uno:CharFontName');
		const names = values ? Object.keys(values).filter((name) => !!name) : [];
		return names.length ? names : WriterEditorCatalog.FONT_FALLBACK_OPTIONS;
	}

	private presentSub(dialog: { open(): void; close(): void }): void {
		this.subDialogs.push(dialog);
		dialog.open();
	}

	private mobileSave(): void {
		const message = 'save dontTerminateEdit=1 dontSaveIfUnmodified=1';
		if (typeof (window as any).postMobileMessage === 'function') {
			(window as any).postMobileMessage(message);
			return;
		}
		const socket = (window as any).app && (window as any).app.socket;
		if (socket && typeof socket.sendMessage === 'function') {
			socket.sendMessage(message);
		}
	}

	private openDataValidationDialog(): void {
		this.presentSub(
			new CalcEditorDataValidationDialog(
				(cmd) => this.sendUno(cmd),
				this.subHost(),
			),
		);
	}

	private deferUnoAfterClose(command: string): void {
		this.close();
		window.setTimeout(() => this.sendUno(command), 280);
	}

	private syncToggleStateFromDocument(): void {
		const controller = this.controller;
		const read = (cmd: string, fallback: boolean) => {
			const checked = controller.isCommandChecked(cmd, fallback);
			return checked;
		};
		this.calcToggleState['grid-lines'] = read('.uno:ToggleSheetGrid', true);
		this.calcToggleState['negative-red'] = read('.uno:NegativeNumberRed', true);
		this.calcToggleState['thousands-sep'] = read('.uno:NumberFormatThousands', false);
		this.calcToggleState['vertical-stack'] = read(
			'.uno:StackCharacterLeftToRight',
			false,
		);
		this.calcToggleState['wrap-text'] = read('.uno:WrapText', false);
	}

	private sendUno(command: string): void {
		const map = (window as any).app && (window as any).app.map;
		if (map && typeof map.sendUnoCommand === 'function') {
			map.sendUnoCommand(command);
			return;
		}
		if ((window as any).app && (window as any).app.socket) {
			(window as any).app.socket.sendMessage('uno ' + command);
		}
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mount = () => {
		try {
			if (CalcEditorPanel.mount()) {
				return;
			}
		} catch (_e) {
			/* WriterEditorInlineSubpage / sheet deps may still be parsing */
		}
		window.setTimeout(mount, 0);
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		window.setTimeout(mount, 0);
	}
}
