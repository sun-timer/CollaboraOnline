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
	private fontColorRgb: number | null = null;
	private backgroundColorRgb: number | null = null;
	private borderColorRgb: number | null = null;
	private numberFormat: CalcNumberFormatState | null = null;
	private rotation: number | null = null;
	private numberFormatPending = false;
	private numberFormatTimer: number | null = null;
	private numberFormatDirty = false;
	private numberFormatSelectionKey: string | null = null;
	private colorOverrides: { [command: string]: number } = {};
	private colorOverrideTimers: { [command: string]: number } = {};
	private rotationOverride: number | null = null;
	private stackOverride: boolean | null = null;
	private formatOverrideTimer: number | null = null;
	private stateMap: any = null;
	private observedSelectionKey: string | null = null;
	private readonly onDocumentState = (event: any) => {
		if (!CalcEditorFormat.isActive()) return;
		if (event?.commandName && ![
			'.uno:Color', '.uno:BackgroundColor', '.uno:FrameLineColor', '.uno:CharBackColor',
			'.uno:NumberFormat', '.uno:NumberFormatIncDecimals', '.uno:NumberFormatDecDecimals',
			'.uno:AlignmentRotation', '.uno:AlignmentStacked', '.uno:WrapText', '.uno:ToggleSheetGrid',
			'.uno:Bold', '.uno:Italic', '.uno:Underline', '.uno:Strikeout', '.uno:Shadowed',
			'.uno:SuperScript', '.uno:SubScript', '.uno:InsertMode',
			'.uno:FreezePanes', '.uno:FreezePanesRow', '.uno:FreezePanesColumn',
		].includes(event.commandName)) return;
		if (event?.commandName === '.uno:NumberFormat' && this.numberFormatPending) {
			this.finishNumberFormatWrite();
			if (this.numberFormatDirty) this.scheduleNumberFormatWrite(0);
			else {
				this.numberFormat = CalcEditorFormat.numberFormat() || this.numberFormat;
				this.numberFormatSelectionKey = null;
				CalcFormatStateBridge.request();
			}
		}
		const numberFormatReadback = CalcEditorFormat.numberFormat();
		if (event?.states?.['.uno:NumberFormat'] !== undefined && this.numberFormatPending &&
			this.numberFormat && numberFormatReadback &&
			CalcEditorPanel.sameNumberFormat(this.numberFormat, numberFormatReadback)) {
			this.finishNumberFormatWrite();
			if (this.numberFormatDirty) this.scheduleNumberFormatWrite(0);
			else this.numberFormatSelectionKey = null;
		}
		const command = event?.commandName;
		if (command && this.colorOverrides[command] !== undefined &&
			CalcEditorFormat.color(command) === this.colorOverrides[command]) this.clearColorOverride(command);
		if (event?.commandName === '.uno:AlignmentRotation' &&
			CalcEditorFormat.number('.uno:AlignmentRotation') === this.rotationOverride) this.clearFormatOverride('rotation');
		if (event?.commandName === '.uno:AlignmentStacked' &&
			CalcEditorFormat.checked('.uno:AlignmentStacked') === this.stackOverride) this.clearFormatOverride('stack');
		if (!event?.commandName && event?.states) {
			if (event.states['.uno:AlignmentRotation'] === this.rotationOverride) this.clearFormatOverride('rotation');
			if (event.states['.uno:AlignmentStacked'] === this.stackOverride) this.clearFormatOverride('stack');
		}
		this.syncToggleStateFromDocument();
		this.refreshStateControls();
	};
	private readonly onSelectionChanged = () => {
		const selectionKey = CalcFormatStateBridge.selectionKey();
		if (selectionKey === this.observedSelectionKey) {
			CalcFormatStateBridge.request();
			return;
		}
		this.observedSelectionKey = selectionKey;
		if (this.numberFormatTimer !== null) window.clearTimeout(this.numberFormatTimer);
		this.numberFormatTimer = null;
		this.numberFormatDirty = false;
		this.finishNumberFormatWrite();
		this.clearFormatOverride('rotation');
		this.clearFormatOverride('stack');
		Object.keys(this.colorOverrideTimers).forEach((command) => this.clearColorOverride(command));
		this.colorOverrides = {};
		this.syncToggleStateFromDocument();
		this.refreshStateControls();
		CalcFormatStateBridge.request();
	};
	private readonly calcToggleState: Record<string, boolean | undefined> = {
		'negative-red': undefined,
		'thousands-sep': undefined,
		'vertical-stack': undefined,
		'wrap-text': undefined,
		'grid-lines': undefined,
	};
	private closeTimer: number | null = null;
	private closeCallbacks: Array<() => void> = [];
	private pendingHyperlinkDismissal: CalcEditorHyperlinkDialog | null = null;
	private mergeChooser: { close(): void } | null = null;

	private constructor() {
		this.sheet = new WriterEditorSheet('', undefined, { editMode: true });
		this.sheet.root.onclick = (event) => {
			if (event.target === this.sheet.root) this.close();
		};
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

	/** iOS Calc: custom 互联网/邮件/文档 sheet. Returns false for other apps or doc types. */
	static openInsertHyperlink(): boolean {
		if (!(window as any).ThisIsTheiOSApp) {
			return false;
		}
		const map = (window as any).app && (window as any).app.map;
		if (!map || typeof map.getDocType !== 'function' || map.getDocType() !== 'spreadsheet') {
			return false;
		}
		const panel = CalcEditorPanel.mount();
		if (!panel) {
			return false;
		}
		const hyperlinkContext = calcEditorDialogContext();
		const hyperlinkText = panel.controller.getSelectedText();
		const hyperlinkSelection = CalcEditorHyperlinkDialog.captureSelection(hyperlinkContext);
		panel.open();
		panel.openHyperlinkDialog(hyperlinkContext, hyperlinkText, hyperlinkSelection);
		return true;
	}

	/** iOS Calc: custom 条件/输入帮助/错误警告 sheet (Android CalcDataValidationController). */
	static openDataValidation(): boolean {
		if (!(window as any).ThisIsTheiOSApp) {
			return false;
		}
		const map = (window as any).app && (window as any).app.map;
		if (!map || typeof map.getDocType !== 'function' || map.getDocType() !== 'spreadsheet') {
			return false;
		}
		const panel = CalcEditorPanel.mount();
		if (!panel) {
			return false;
		}
		panel.open();
		panel.openDataValidationDialog();
		return true;
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
		if (!CalcEditorFormat.isActive()) return;
		if (this.closeTimer !== null) {
			window.clearTimeout(this.closeTimer);
			this.closeTimer = null;
			this.closeCallbacks = [];
			const pendingDialog = this.pendingHyperlinkDismissal;
			this.pendingHyperlinkDismissal = null;
			if (pendingDialog) {
				const index = this.subDialogs.indexOf(pendingDialog);
				if (index >= 0) this.subDialogs.splice(index, 1);
				pendingDialog.close();
			}
		}
		this.sheet.root.style.visibility = '';
		this.sheet.root.style.pointerEvents = '';
		MobileDocTheme.applyFromMap();
		CalcTextFormatBridge.observeSelection();
		CalcFormatStateBridge.observeSelection();
		CalcFormatStateBridge.request();
		this.subscribeDocumentState();
		this.syncToggleStateFromDocument();
		this.renderTabs();
		this.renderContent();
		this.refreshStateControls();
		this.sheet.open();
	}

	close(onClosed?: () => void): void {
		if (onClosed) this.closeCallbacks.push(onClosed);
		if (this.closeTimer !== null) {
			return;
		}
		if (this.numberFormatDirty && this.numberFormat && this.numberFormatSelectionKey &&
			this.numberFormatSelectionKey === CalcFormatStateBridge.selectionKey()) {
			if (this.numberFormatTimer !== null) window.clearTimeout(this.numberFormatTimer);
			this.numberFormatTimer = null;
			CalcFormatStateBridge.invalidate();
			if (CalcEditorFormat.setNumberFormat(this.numberFormat)) this.numberFormatDirty = false;
		}
		this.numberFormatSelectionKey = null;
		Object.keys(this.colorOverrideTimers).forEach((command) => this.clearColorOverride(command));
		this.colorOverrides = {};
		this.clearFormatOverride('rotation');
		this.clearFormatOverride('stack');
		this.unsubscribeDocumentState();
		const chooser = this.mergeChooser;
		this.mergeChooser = null;
		// Hide now so the half-height sheet does not stay on screen, but
		// detach it after the click that called close() has finished.
		this.sheet.root.style.visibility = 'hidden';
		this.sheet.root.style.pointerEvents = 'none';
		this.closeTimer = window.setTimeout(() => {
			this.closeTimer = null;
			const children = this.subDialogs.splice(0);
			children.forEach((dialog) => dialog.close());
			this.inlineSubpage.clearSubpages();
			this.sheet.close();
			if (chooser) {
				chooser.close();
			}
			this.recoverDocumentCanvas();
			this.pendingHyperlinkDismissal = null;
			const callbacks = this.closeCallbacks.splice(0);
			callbacks.forEach((callback) => callback());
		}, 0);
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
		this.refreshStateControls();
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
		swatch.dataset.calcColor = 'font';
		swatch.style.backgroundColor = CalcEditorFormat.fontColorCss(this.fontColorRgb);
		colorButton.appendChild(swatch);
		const colorLabel = document.createElement('span');
		colorLabel.className = 'writer-function-split-picker-row__label';
		colorLabel.textContent = '字体颜色';
		colorButton.insertBefore(colorLabel, swatch);
		const colorChevron = document.createElement('span');
		colorChevron.className = 'writer-function-split-picker-row__chevron';
		colorChevron.innerHTML = WriterEditorIcons.get('calc-row-chevron');
		colorButton.appendChild(colorChevron);
		colorButton.onclick = () => this.openFontColorDialog();

		row.appendChild(sizeButton);
		row.appendChild(colorButton);
		return row;
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
				button.dataset.calcCharacter = tool.unocmd;
				button.onmousedown = (event) => event.preventDefault();
				button.onclick = () => {
					if (tool.id === 'highlight') {
						this.openHighlightColorDialog();
					} else if (tool.id === 'super' || tool.id === 'sub') {
						const operation = tool.id === 'super' ? 'superscript' : 'subscript';
						CalcTextFormatBridge.apply(operation, true);
					} else {
						this.sendUno(tool.unocmd);
					}
				};
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
				CalcEditorBorder.applyPreset(cell.icon, this.borderColorRgb !== null && this.borderColorRgb >= 0 ? this.borderColorRgb : 0);
				return;
			}
			if (cell.unocmd) {
				this.sendUno(cell.unocmd);
			}
		};
		if (cell.unocmd.startsWith('.uno:FreezePanes')) {
			button.dataset.calcFreeze = cell.unocmd.split(' ')[0];
		}
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
		row.dataset.calcToggle = id;
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
		sync();
		row.appendChild(label);
		row.appendChild(indicator);
		row.onclick = () => {
			const on = this.calcToggleState[id] !== true;
			if (id === 'negative-red' || id === 'thousands-sep') {
				this.updateNumberFormat(id === 'negative-red' ? { negativeRed: on } : { thousands: on });
			} else if (id === 'vertical-stack') {
				CalcFormatStateBridge.invalidate();
				this.calcToggleState['vertical-stack'] = on;
				this.stackOverride = on;
				this.refreshStateControls();
				CalcEditorFormat.send('.uno:AlignmentStacked', { AlignmentStacked: { type: 'boolean', value: on } });
				this.scheduleFormatStateReadback();
			} else if (feature.unocmd) {
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
					unocmd: '.uno:AlignmentStacked',
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
		incCmd: string | (() => void),
		decCmd: string | (() => void),
		stateKey?: string,
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
		if (stateKey) value.dataset.calcValue = stateKey;
		const buttons = document.createElement('div');
		buttons.className = 'writer-function-calc-stepper-card__buttons';
		const minus = document.createElement('button');
		minus.type = 'button';
		minus.className = 'writer-function-calc-stepper-card__btn';
		minus.innerHTML = WriterEditorIcons.get('calc-stepper-minus');
		minus.setAttribute('aria-label', '减少' + title);
		minus.onclick = () => typeof decCmd === 'string' ? this.sendUno(decCmd) : decCmd();
		const plus = document.createElement('button');
		plus.type = 'button';
		plus.className = 'writer-function-calc-stepper-card__btn';
		plus.innerHTML = WriterEditorIcons.get('calc-stepper-plus');
		plus.setAttribute('aria-label', '增加' + title);
		plus.onclick = () => typeof incCmd === 'string' ? this.sendUno(incCmd) : incCmd();
		if (stateKey) {
			minus.dataset.calcStepper = stateKey;
			minus.dataset.calcDelta = '-1';
			plus.dataset.calcStepper = stateKey;
			plus.dataset.calcDelta = '1';
		}
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
					'—',
					() => this.changeRotation(1),
					() => this.changeRotation(-1),
					'rotation',
				),
			);
		} else {
			row.appendChild(
				this.createStepperColumn(
					'小数位数',
					'—',
					() => this.changeNumberFormatField('precision', 1),
					() => this.changeNumberFormatField('precision', -1),
					'precision',
				),
			);
			row.appendChild(
				this.createStepperColumn(
					'前导零',
					'—',
					() => this.changeLeadingZeroes(1),
					() => this.changeLeadingZeroes(-1),
					'leadingZeroes',
				),
			);
		}
		return row;
	}

	private createColorPickerHalf(
		id: string,
		title: string,
		iconKey: string,
		rgb: number | null,
		onPick: () => void,
	): HTMLButtonElement {
		const cell = document.createElement('button');
		cell.type = 'button';
		cell.className = 'writer-function-calc-color-half';
		const swatch = document.createElement('span');
		swatch.className = 'writer-function-calc-color-half__swatch';
		swatch.dataset.calcColor = id === 'bg_color' ? 'background' : 'border';
		if (WriterEditorIcons.has(iconKey)) {
			swatch.innerHTML = WriterEditorIcons.get(iconKey);
		} else {
			swatch.style.backgroundColor = CalcEditorFormat.colorCss(rgb);
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
		if (feature.id === 'insert-hyperlink') {
			const context = calcEditorDialogContext();
			const selectedText = this.controller.getSelectedText();
			const selection = CalcEditorHyperlinkDialog.captureSelection(context);
			this.openHyperlinkDialog(context, selectedText, selection);
			return;
		}
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
				this.openHyperlinkDialog();
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
				(rgb) => this.applyColor('.uno:Color', rgb,
					'.uno:Color {"Color.Color":{"type":"long","value":' + rgb + '}}'),
				this.subHost(),
			),
		);
	}

	private openHighlightColorDialog(): void {
		const initialColor = CalcEditorFormat.highlightColor();
		this.presentSub(new WriterColorPickerDialog(
			'荧光颜色', initialColor,
			(rgb) => { CalcTextFormatBridge.apply('highlight', rgb); }, this.subHost(),
		));
	}

	private openBackgroundColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog(
				'背景颜色',
				this.backgroundColorRgb,
				(rgb) => this.applyColor('.uno:BackgroundColor', rgb,
					'.uno:BackgroundColor {"BackgroundColor.Color":{"type":"long","value":' + rgb + '}}'),
				this.subHost(),
			),
		);
	}

	private openBorderColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog(
				'边框颜色',
				this.borderColorRgb,
				(rgb) => this.applyColor('.uno:FrameLineColor', rgb,
					CalcEditorBorder.buildBorderColorUnoCommand(rgb)),
				this.subHost(),
			),
		);
	}

	private applyColor(command: string, rgb: number, unoCommand: string): void {
		this.sendUno(unoCommand);
		this.clearColorOverride(command);
		this.colorOverrides[command] = rgb;
		this.colorOverrideTimers[command] = window.setTimeout(() => {
			this.clearColorOverride(command);
			this.syncToggleStateFromDocument();
			this.refreshStateControls();
		}, 1000);
		if (command === '.uno:Color') this.fontColorRgb = rgb;
		else if (command === '.uno:BackgroundColor') this.backgroundColorRgb = rgb;
		else this.borderColorRgb = rgb;
		this.refreshStateControls();
		CalcFormatStateBridge.request();
	}

	private clearColorOverride(command: string): void {
		const timer = this.colorOverrideTimers[command];
		if (timer !== undefined) window.clearTimeout(timer);
		delete this.colorOverrideTimers[command];
		delete this.colorOverrides[command];
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
		// Android dismisses the function panel, then shows merge options on
		// their own sheet. Keeping the chooser inside this panel leaves the
		// 50dvh sheet blank after the command and crashes 收起 on detach.
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
		this.close();
		window.setTimeout(() => {
			if (this.mergeChooser) {
				this.mergeChooser.close();
				this.mergeChooser = null;
			}
			const dialog = new WriterEditorChooseDialog(
				'合并单元格',
				options,
				(option) => {
					window.setTimeout(() => {
						this.sendUno(option.value);
						this.recoverDocumentCanvas();
					}, 200);
				},
			);
			this.mergeChooser = dialog;
			dialog.open();
		}, 0);
	}

	private recoverDocumentCanvas(): void {
		const map = (window as any).app && (window as any).app.map;
		if (map && typeof map.invalidateSize === 'function') {
			map.invalidateSize(false);
		}
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

	private openHyperlinkDialog(
		context: CalcEditorDialogContext | null = calcEditorDialogContext(),
		selectedText = this.controller.getSelectedText(),
		selection: CalcHyperlinkSelectionSnapshot | null = CalcEditorHyperlinkDialog.captureSelection(context),
	): void {
		const lifecycle: CalcHyperlinkPanelLifecycle = {
			closePanel: (onClosed) => {
				const index = this.subDialogs.indexOf(dialog);
				if (index >= 0) this.subDialogs.splice(index, 1);
				this.pendingHyperlinkDismissal = dialog;
				this.close(onClosed);
			},
			restorePanel: () => {
				this.open();
				if (!this.subDialogs.includes(dialog)) this.subDialogs.push(dialog);
				dialog.restorePreservingDraft();
			},
		};
		const dialog = new CalcEditorHyperlinkDialog(
			this.controller,
			this.subHost(),
			undefined,
			lifecycle,
		);
		this.subDialogs.push(dialog);
		dialog.open(context, selectedText, selection);
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
		if (!CalcEditorFormat.isActive()) return;
		this.fontColorRgb = this.colorOverrides['.uno:Color'] ?? CalcEditorFormat.color('.uno:Color');
		this.backgroundColorRgb = this.colorOverrides['.uno:BackgroundColor'] ?? CalcEditorFormat.color('.uno:BackgroundColor');
		this.borderColorRgb = this.colorOverrides['.uno:FrameLineColor'] ?? CalcEditorFormat.color('.uno:FrameLineColor');
		if (!this.numberFormatPending && !this.numberFormatDirty)
			this.numberFormat = CalcEditorFormat.numberFormat();
		this.rotation = this.rotationOverride !== null ? this.rotationOverride :
			CalcEditorFormat.number('.uno:AlignmentRotation');
		this.calcToggleState['grid-lines'] = CalcEditorFormat.checked('.uno:ToggleSheetGrid');
		this.calcToggleState['negative-red'] = this.numberFormat?.negativeRed;
		this.calcToggleState['thousands-sep'] = this.numberFormat?.thousands;
		this.calcToggleState['vertical-stack'] = this.stackOverride !== null ? this.stackOverride :
			CalcEditorFormat.checked('.uno:AlignmentStacked');
		this.calcToggleState['wrap-text'] = CalcEditorFormat.checked('.uno:WrapText');
	}

	private subscribeDocumentState(): void {
		const map = (window as any).app.map;
		if (this.stateMap === map) return;
		this.unsubscribeDocumentState();
		this.stateMap = map;
		this.observedSelectionKey = CalcFormatStateBridge.selectionKey();
		map.on('commandstatechanged', this.onDocumentState);
		map.on('calctextformatresult', this.onDocumentState);
		map.on('calcformatstatechanged', this.onDocumentState);
		map.on('celladdress', this.onSelectionChanged);
		map.on('updateparts', this.onSelectionChanged);
	}

	private unsubscribeDocumentState(): void {
		this.stateMap?.off('commandstatechanged', this.onDocumentState);
		this.stateMap?.off('calctextformatresult', this.onDocumentState);
		this.stateMap?.off('calcformatstatechanged', this.onDocumentState);
		this.stateMap?.off('celladdress', this.onSelectionChanged);
		this.stateMap?.off('updateparts', this.onSelectionChanged);
		this.stateMap = null;
		this.finishNumberFormatWrite();
		this.numberFormatDirty = false;
		this.numberFormatSelectionKey = null;
	}

	private finishNumberFormatWrite(): void {
		this.numberFormatPending = false;
		if (this.numberFormatTimer !== null) window.clearTimeout(this.numberFormatTimer);
		this.numberFormatTimer = null;
	}

	private updateNumberFormat(changes: Partial<CalcNumberFormatState>): void {
		if (!this.numberFormat) return;
		CalcFormatStateBridge.invalidate();
		if (!this.numberFormatDirty && !this.numberFormatPending)
			this.numberFormatSelectionKey = CalcFormatStateBridge.selectionKey?.() || null;
		this.numberFormat = CalcEditorFormat.mergeNumberFormat(this.numberFormat, changes);
		this.numberFormatDirty = true;
		this.refreshStateControls();
		if (!this.numberFormatPending) this.scheduleNumberFormatWrite(120);
	}

	private scheduleNumberFormatWrite(delay: number): void {
		if (this.numberFormatTimer !== null) window.clearTimeout(this.numberFormatTimer);
		this.numberFormatTimer = window.setTimeout(() => {
			this.numberFormatTimer = null;
			if (!this.numberFormat || !this.numberFormatDirty || this.numberFormatPending) return;
			if (!this.numberFormatSelectionKey ||
				this.numberFormatSelectionKey !== CalcFormatStateBridge.selectionKey()) {
				this.numberFormatDirty = false;
				this.numberFormatSelectionKey = null;
				this.numberFormat = CalcEditorFormat.numberFormat();
				this.refreshStateControls();
				return;
			}
			CalcFormatStateBridge.invalidate();
			if (!CalcEditorFormat.setNumberFormat(this.numberFormat)) return;
			this.numberFormatDirty = false;
			this.numberFormatPending = true;
			this.numberFormatTimer = window.setTimeout(() => {
				this.finishNumberFormatWrite();
				if (this.numberFormatDirty) this.scheduleNumberFormatWrite(0);
				else {
					this.numberFormat = CalcEditorFormat.numberFormat() || this.numberFormat;
				this.numberFormatSelectionKey = null;
					CalcFormatStateBridge.request();
				}
				this.refreshStateControls();
			}, 1800);
			this.refreshStateControls();
		}, delay);
	}

	private changeLeadingZeroes(delta: number): void {
		if (!this.numberFormat) return;
		const leadingZeroes = Math.max(0, Math.min(20, this.numberFormat.leadingZeroes + delta));
		this.updateNumberFormat({ leadingZeroes });
	}

	private static sameNumberFormat(a: CalcNumberFormatState, b: CalcNumberFormatState): boolean {
		return a.thousands === b.thousands && a.negativeRed === b.negativeRed &&
			a.precision === b.precision && a.leadingZeroes === b.leadingZeroes && a.natNum12 === b.natNum12;
	}

	private changeNumberFormatField(field: 'precision', delta: number): void {
		if (!this.numberFormat) return;
		const precision = Math.max(0, Math.min(20, this.numberFormat[field] + delta));
		if (precision !== this.numberFormat[field]) this.updateNumberFormat({ [field]: precision });
	}

	private changeRotation(delta: number): void {
		if (this.rotation === null) return;
		CalcFormatStateBridge.invalidate();
		const value = ((this.rotation + delta * 100) % 36000 + 36000) % 36000;
		this.rotation = value;
		this.rotationOverride = value;
		this.refreshStateControls();
		CalcEditorFormat.send('.uno:AlignmentRotation', { AlignmentRotation: { type: 'long', value } });
		this.scheduleFormatStateReadback();
	}

	private scheduleFormatStateReadback(): void {
		if (this.formatOverrideTimer !== null) window.clearTimeout(this.formatOverrideTimer);
		window.setTimeout(() => CalcFormatStateBridge.request(), 250);
		this.formatOverrideTimer = window.setTimeout(() => {
			this.formatOverrideTimer = null;
			this.clearFormatOverride('rotation');
			this.clearFormatOverride('stack');
			CalcFormatStateBridge.request();
			this.syncToggleStateFromDocument();
			this.refreshStateControls();
		}, 1200);
	}

	private clearFormatOverride(key: 'rotation' | 'stack'): void {
		if (key === 'rotation') this.rotationOverride = null;
		else this.stackOverride = null;
		if (this.rotationOverride === null && this.stackOverride === null && this.formatOverrideTimer !== null) {
			window.clearTimeout(this.formatOverrideTimer);
			this.formatOverrideTimer = null;
		}
	}

	private refreshStateControls(): void {
		if (!CalcEditorFormat.isActive()) return;
		const colors: { [key: string]: number | null } = {
			font: this.fontColorRgb, background: this.backgroundColorRgb, border: this.borderColorRgb,
		};
		this.content.querySelectorAll<HTMLElement>('[data-calc-color]').forEach((node) => {
			const rgb = colors[node.dataset.calcColor || ''];
			const css = node.dataset.calcColor === 'font' ? CalcEditorFormat.fontColorCss(rgb) : CalcEditorFormat.colorCss(rgb);
			const path = node.querySelector('path');
			if (path) path.setAttribute('fill', css);
			else node.style.backgroundColor = css;
			node.classList.toggle('calc-format-state-unknown', rgb === null);
			node.setAttribute('aria-label', rgb === null ? '颜色未确定' : rgb === -1 ? '自动或透明颜色' : css);
		});
		this.content.querySelectorAll<HTMLButtonElement>('[data-calc-toggle]').forEach((button) => {
			const id = button.dataset.calcToggle || '';
			const value = this.calcToggleState[id];
			button.setAttribute('aria-pressed', value === undefined ? 'mixed' : String(value));
			const indicator = button.querySelector('.writer-function-calc-toggle-card__indicator');
			if (indicator) indicator.innerHTML = WriterEditorIcons.get(value === true ? 'calc-toggle-checked' : 'calc-toggle-unchecked');
			button.disabled = (id === 'negative-red' || id === 'thousands-sep') && !this.numberFormat;
		});
		this.content.querySelectorAll<HTMLElement>('[data-calc-value]').forEach((node) => {
			const key = node.dataset.calcValue;
			const value = key === 'rotation' ? this.rotation :
				key === 'precision' ? this.numberFormat?.precision : this.numberFormat?.leadingZeroes;
			node.textContent = value === null || value === undefined ? '—' :
				key === 'rotation' ? String(value / 100) + ' °' : String(value);
		});
		this.content.querySelectorAll<HTMLButtonElement>('[data-calc-stepper]').forEach((button) => {
			const key = button.dataset.calcStepper;
			const delta = Number(button.dataset.calcDelta);
			button.disabled = key === 'rotation' ? this.rotation === null :
				key === 'leadingZeroes' ? !this.numberFormat ||
					(delta < 0 && this.numberFormat.leadingZeroes <= 0) || (delta > 0 && this.numberFormat.leadingZeroes >= 20) :
				!this.numberFormat || (delta < 0 && this.numberFormat.precision <= 0) ||
				(delta > 0 && this.numberFormat.precision >= 20);
		});
		this.content.querySelectorAll<HTMLButtonElement>('[data-calc-character]').forEach((button) => {
			const command = button.dataset.calcCharacter || '';
			const operation = command === '.uno:SuperScript' ? 'superscript' : command === '.uno:SubScript' ? 'subscript' : null;
			const checked = operation && (window as any).app.map._docLayer?.insertMode !== true
				? undefined : CalcEditorFormat.checked(command);
			if (command !== '.uno:CharBackColor') {
				button.setAttribute('aria-pressed', checked === undefined ? 'mixed' : String(checked));
				button.classList.toggle('calc-character-tool--active', checked === true);
			}
			// Native formats text in a cell selection even when edit-shell slots are disabled.
			button.disabled = !operation && command !== '.uno:CharBackColor' && CalcEditorFormat.state(command) === 'disabled';
		});
		this.content.querySelectorAll<HTMLButtonElement>('[data-calc-freeze]').forEach((button) => {
			const command = button.dataset.calcFreeze || '';
			const state = CalcEditorFormat.state(command);
			if (command === '.uno:FreezePanes') {
				button.setAttribute('aria-pressed', String(state === 'true' || state === true));
			} else {
				const match = typeof state === 'string' ? state.match(/^(-?\d+)(?:\s*\/\s*|,)(\d+)$/) : null;
				const part = (window as any).app.map.getCurrentPartNumber?.();
				if (match && Number(match[2]) === part) button.setAttribute('aria-pressed', String(Number(match[1]) > 0));
				else button.removeAttribute('aria-pressed');
			}
			button.disabled = state === 'disabled';
		});
	}

	private sendUno(command: string): void {
		if (!CalcEditorFormat.isActive()) return;
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
