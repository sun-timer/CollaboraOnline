/*
 * iOS Browser Writer document-editing function panel.
 *
 * A thin DOM facade over WriterEditorCatalog + WriterEditorController,
 * mirroring WriterAiPanel. It renders the five editor tabs (常用/文件/插入/
 * 布局/审阅), aligned with Android FunctionPanelController / Figma 192-5683.
 * Dispatches features through
 * controller. Dialog-kind features are gated off until their native/web
 * dialog lands (same pattern as b0d7fb's iosSupport gate).
 */

class WriterEditorPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly tabBar: HTMLDivElement;
	private readonly divider: HTMLDivElement;
	private readonly actions: HTMLDivElement;
	private readonly tabScroll: HTMLDivElement;
	private readonly content: HTMLDivElement;
	/** Fallback display strings for picker rows; live CO state wins when present. */
	private readonly pickerLabels: { [key: string]: string } = {
		style: '正文',
		'font-name': '字体',
		'font-size': '四号',
		page_margins: WriterEditorCatalog.MARGIN_PRESETS[0].label,
		paper_size: 'A4',
		paper_orientation: '纵向',
	};
	/** Value text nodes of the formatter rows, keyed by feature id. */
	private readonly pickerValueNodes: { [key: string]: HTMLElement } = {};
	private readonly controller: WriterEditorController;
	private activeTab: WriterEditorTab = 'default';
	/** Toggle inputs in the review tab, keyed by .uno command. */
	private readonly reviewToggleInputs: { [command: string]: HTMLInputElement } =
		{};
	private onReviewStateBound: ((event: any) => void) | null = null;
	private watermarkText = '水印文本';
	private watermarkFont = '';
	private watermarkAngle = 45;
	private watermarkTransparency = 50;
	private watermarkEnabled = false;
	private watermarkToggleInput: HTMLInputElement | null = null;
	/** Picker/dialog sheets opened from this panel, closed together with it. */
	private readonly subDialogs: { close(): void }[] = [];
	private readonly inlineSubpage: WriterEditorInlineSubpage;
	private static readonly SUPPORTED_DIALOGS: WriterEditorDialogType[] = [
		'fontName',
		'fontSize',
		'table',
		'margins',
		'shape',
		'style',
		'watermark',
		'paperSize',
		'image',
		'saveAs',
		'chart',
		'comment',
	];

	/** Commands whose current state feeds the formatter rows (Map.StateChanges). */
	private static readonly VALUE_COMMANDS: { [featureId: string]: string } = {
		style: '.uno:StyleApply',
		'font-name': '.uno:CharFontName',
		'font-size': '.uno:FontHeight',
	};

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.sheet = new WriterEditorSheet(
			'',
			() => this.unsubscribeReviewState(),
			{
				editMode: true,
			},
		);

		const panelRoot = document.createElement('div');
		panelRoot.className = 'writer-function-panel';

		this.tabBar = document.createElement('div');
		this.tabBar.className = 'writer-function-tab-bar';
		this.tabScroll = document.createElement('div');
		this.tabScroll.className = 'writer-function-tab-scroll';
		this.divider = document.createElement('div');
		this.divider.className = 'writer-function-tab-divider';
		this.divider.setAttribute('aria-hidden', 'true');
		this.actions = document.createElement('div');
		this.actions.className = 'writer-function-tab-actions';
		const actionButtons: Array<{
			icon: string;
			aria: string;
			handler: () => void;
		}> = [
			{
				icon: 'ai-sparkle',
				aria: 'AI功能',
				handler: () => this.openAiFeatures(),
			},
			{
				icon: 'keyboard',
				aria: '键盘',
				handler: () => this.showKeyboard(),
			},
			{ icon: 'collapse', aria: '收起', handler: () => this.close() },
		];
		actionButtons.forEach((action) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-action-btn';
			button.setAttribute('aria-label', action.aria);
			const icon = WriterEditorIcons.get(action.icon);
			if (icon) {
				button.innerHTML = icon;
			}
			button.onclick = action.handler;
			this.actions.appendChild(button);
		});
		this.tabBar.appendChild(this.tabScroll);
		this.tabBar.appendChild(this.divider);
		this.tabBar.appendChild(this.actions);
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

	static mount(): WriterEditorPanel | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolWriterEditorPanel;
		if (existing instanceof WriterEditorPanel) {
			return existing;
		}
		const panel = new WriterEditorPanel();
		(window as any).__coolWriterEditorPanel = panel;
		return panel;
	}

	open(): void {
		MobileDocTheme.applyFromMap();
		this.renderTabs();
		this.renderContent();
		this.subscribeReviewState();
		this.sheet.open();
	}

	/** Closes the panel and any picker/dialog sheets opened from it. */
	close(): void {
		this.unsubscribeReviewState();
		const children = this.subDialogs.splice(0);
		children.forEach((dialog) => dialog.close());
		this.inlineSubpage.clearSubpages();
		this.sheet.close();
	}

	private presentSub(dialog: { open(): void; close(): void }): void {
		this.subDialogs.push(dialog);
		dialog.open();
	}
	private renderTabs(): void {
		this.tabScroll.replaceChildren();
		WriterEditorCatalog.TABS.forEach((tab) => {
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

	private renderContent(): void {
		this.content.replaceChildren();
		Object.keys(this.reviewToggleInputs).forEach((command) => {
			delete this.reviewToggleInputs[command];
		});
		switch (this.activeTab) {
			case 'default':
				this.renderCommonTab();
				break;
			case 'file':
				this.renderFileTab();
				break;
			case 'insert':
				this.renderInsertTab();
				break;
			case 'layout':
				this.renderLayoutTab();
				break;
			case 'review':
				this.renderReviewTab();
				break;
		}
		this.refreshReviewToggles();
	}

	/** Android buildTabs() common tab: SECTION + PICKER rows, then PARAGRAPH chips. */
	private renderCommonTab(): void {
		const stack = this.createStack();
		const features = WriterEditorCatalog.getFeatures('default');
		features.forEach((feature) => {
			if (feature.group !== 'format') {
				return;
			}
			stack.appendChild(this.createSectionHeader(feature.label));
			stack.appendChild(
				this.createPickerRow(feature, feature.id === 'font-size'),
			);
		});
		stack.appendChild(
			this.createSectionHeader(WriterEditorCatalog.GROUP_LABELS.paragraph),
		);
		stack.appendChild(
			this.createParagraphChipGrid(
				features.filter((f) => f.group === 'paragraph'),
			),
		);
		this.content.appendChild(stack);
	}

	private renderFileTab(): void {
		const stack = this.createStack();
		const group = document.createElement('div');
		group.className = 'writer-function-action-group';
		const features = WriterEditorCatalog.getFeatures('file');
		features.forEach((feature, index) => {
			group.appendChild(this.createActionRow(feature, true));
			if (index + 1 < features.length) {
				group.appendChild(this.createRowDivider());
			}
		});
		stack.appendChild(group);
		this.content.appendChild(stack);
	}

	private renderInsertTab(): void {
		const grid = document.createElement('div');
		grid.className =
			'writer-function-chip-grid writer-function-chip-grid--insert';
		const features = WriterEditorCatalog.getInsertGridFeatures();
		this.appendChipGridRows(
			grid,
			features,
			(feature) => this.onFeature(feature),
			{ textOnly: true },
		);
		this.content.appendChild(grid);
	}

	private renderLayoutTab(): void {
		const stack = this.createStack();
		const watermark = WriterEditorCatalog.getFeature('watermark');
		if (watermark) {
			stack.appendChild(this.createWatermarkRow(watermark));
			stack.appendChild(this.createLayoutSectionSpacer());
		}
		const group = document.createElement('div');
		group.className = 'writer-function-picker-group';
		const layoutPickers: Array<{
			id: string;
			icon: string;
			label: string;
			pickerKey: string;
			handler: () => void;
		}> = [
			{
				id: 'margins',
				icon: 'margins',
				label: '页边距',
				pickerKey: 'page_margins',
				handler: () => this.openMarginsDialog(),
			},
			{
				id: 'paper-size',
				icon: 'paper-size',
				label: '纸张大小',
				pickerKey: 'paper_size',
				handler: () => this.openPaperSizeDialog(),
			},
			{
				id: 'orientation',
				icon: 'orientation',
				label: '纸张方向',
				pickerKey: 'paper_orientation',
				handler: () => this.openOrientationDialog(),
			},
		];
		layoutPickers.forEach((picker, index) => {
			group.appendChild(
				this.createGroupedPickerRow(
					picker.icon,
					picker.label,
					this.pickerLabels[picker.pickerKey],
					picker.handler,
				),
			);
			if (index + 1 < layoutPickers.length) {
				const divider = document.createElement('div');
				divider.className = 'writer-function-picker-group__divider';
				divider.setAttribute('aria-hidden', 'true');
				group.appendChild(divider);
			}
		});
		stack.appendChild(group);
		this.content.appendChild(stack);
	}

	private renderReviewTab(): void {
		const stack = this.createStack();
		WriterEditorCatalog.getFeatures('review').forEach((feature) => {
			if (feature.kind === 'toggle') {
				stack.appendChild(this.createToggleRow(feature));
			} else {
				stack.appendChild(this.createActionRow(feature));
				stack.appendChild(this.createRowDivider());
			}
		});
		this.content.appendChild(stack);
	}

	private createStack(): HTMLDivElement {
		const stack = document.createElement('div');
		stack.className = 'writer-function-stack';
		return stack;
	}

	private createSectionHeader(title: string): HTMLHeadingElement {
		const header = document.createElement('h3');
		header.className = 'writer-function-section-title';
		header.textContent = title;
		return header;
	}

	private createRowSpacer(px: number): HTMLDivElement {
		const spacer = document.createElement('div');
		spacer.className = 'writer-function-row-spacer';
		spacer.style.height = px + 'px';
		spacer.setAttribute('aria-hidden', 'true');
		return spacer;
	}

	private createRowDivider(): HTMLDivElement {
		const divider = document.createElement('div');
		divider.className = 'writer-function-action-group__divider';
		divider.setAttribute('aria-hidden', 'true');
		return divider;
	}

	private createLayoutSectionSpacer(): HTMLDivElement {
		const spacer = document.createElement('div');
		spacer.className = 'writer-function-layout-spacer';
		spacer.setAttribute('aria-hidden', 'true');
		return spacer;
	}

	private createWatermarkRow(feature: WriterEditorFeature): HTMLElement {
		const row = document.createElement('div');
		row.className = 'writer-function-watermark-row';

		const iconWrap = document.createElement('span');
		iconWrap.className = 'writer-function-watermark-row__icon';
		const icon = WriterEditorIcons.get(feature.icon);
		if (icon) {
			iconWrap.innerHTML = icon;
		}
		row.appendChild(iconWrap);

		const label = document.createElement('span');
		label.className = 'writer-function-watermark-row__label';
		label.textContent = feature.label;
		row.appendChild(label);

		const toggle = document.createElement('input');
		toggle.type = 'checkbox';
		toggle.className = 'writer-function-toggle-row__input';
		toggle.setAttribute('aria-label', '启用水印');
		toggle.checked = this.watermarkEnabled;
		toggle.onchange = () => {
			this.watermarkEnabled = toggle.checked;
			if (toggle.checked) {
				this.applyCurrentWatermark();
			} else {
				this.controller.applyWatermark(
					'',
					this.watermarkAngle,
					this.watermarkTransparency,
					this.watermarkFont || undefined,
				);
			}
		};
		this.watermarkToggleInput = toggle;
		row.appendChild(toggle);

		const settingsBtn = document.createElement('button');
		settingsBtn.type = 'button';
		settingsBtn.className = 'writer-function-watermark-row__chevron';
		settingsBtn.setAttribute('aria-label', '水印设置');
		settingsBtn.textContent = '›';
		settingsBtn.onclick = () => this.openWatermarkDialog();
		row.appendChild(settingsBtn);

		return row;
	}

	private applyCurrentWatermark(): void {
		this.controller.applyWatermark(
			this.watermarkText,
			this.watermarkAngle,
			this.watermarkTransparency,
			this.watermarkFont || undefined,
		);
	}

	private createPickerRow(
		feature: WriterEditorFeature,
		fontSizeLayout: boolean,
	): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-picker-row';
		row.setAttribute('aria-label', feature.label);

		const iconWrap = document.createElement('span');
		iconWrap.className = 'writer-function-picker-row__icon';
		const icon = WriterEditorIcons.get(feature.icon);
		if (icon) {
			iconWrap.innerHTML = icon;
		}
		row.appendChild(iconWrap);

		const display = this.displayValue(feature.id);
		const value = document.createElement('span');
		if (fontSizeLayout) {
			const label = document.createElement('span');
			label.className = 'writer-function-picker-row__label';
			label.textContent = '大小';
			row.appendChild(label);

			const valueBox = document.createElement('span');
			valueBox.className = 'writer-function-value-box';
			value.className = 'writer-function-value-box__text';
			value.textContent = display;
			valueBox.appendChild(value);
			const arrow = document.createElement('span');
			arrow.className = 'writer-function-value-box__arrow';
			arrow.textContent = '▾';
			valueBox.appendChild(arrow);
			row.appendChild(valueBox);
		} else {
			value.className = 'writer-function-picker-row__value';
			value.textContent = display;
			row.appendChild(value);
			const chevron = document.createElement('span');
			chevron.className = 'writer-function-picker-row__chevron';
			chevron.textContent = '›';
			row.appendChild(chevron);
		}
		this.pickerValueNodes[feature.id] = value;

		if (this.isFeatureGated(feature)) {
			row.disabled = true;
			row.title = '即将支持';
		}
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	/** Current value for a formatter row: CO state first, then the last pick. */
	private displayValue(featureId: string): string {
		const command = WriterEditorPanel.VALUE_COMMANDS[featureId];
		const state = command ? this.controller.getCommandState(command) : '';
		if (state) {
			if (featureId === 'font-size') {
				return WriterEditorCatalog.charHeightLabelFor(state) || state;
			}
			return state;
		}
		return this.pickerLabels[featureId] || '';
	}

	/** Re-reads the formatter rows in place (called on command state changes). */
	private refreshPickerValues(): void {
		Object.keys(this.pickerValueNodes).forEach((featureId) => {
			this.pickerValueNodes[featureId].textContent =
				this.displayValue(featureId);
		});
	}

	private createGroupedPickerRow(
		iconKey: string,
		labelText: string,
		valueText: string,
		onClick: () => void,
	): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-grouped-picker-row';
		const iconWrap = document.createElement('span');
		iconWrap.className = 'writer-function-picker-row__icon';
		const icon = WriterEditorIcons.get(iconKey);
		if (icon) {
			iconWrap.innerHTML = icon;
		}
		row.appendChild(iconWrap);
		const label = document.createElement('span');
		label.className = 'writer-function-grouped-picker-row__label';
		label.textContent = labelText;
		row.appendChild(label);
		const value = document.createElement('span');
		value.className = 'writer-function-grouped-picker-row__value';
		value.textContent = valueText;
		row.appendChild(value);
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-picker-row__chevron';
		chevron.textContent = '›';
		row.appendChild(chevron);
		row.onclick = onClick;
		return row;
	}

	private isParagraphAlignmentSelected(feature: WriterEditorFeature): boolean {
		if (!feature.id.startsWith('align-')) {
			return false;
		}
		const cmd = feature.unocmd || '';
		return cmd ? this.controller.isCommandChecked(cmd, false) : false;
	}

	private createParagraphChipGrid(
		features: WriterEditorFeature[],
	): HTMLDivElement {
		const grid = document.createElement('div');
		grid.className = 'writer-function-chip-grid';
		this.appendChipGridRows(grid, features, (feature) =>
			this.onFeature(feature),
		);
		return grid;
	}

	private appendChipGridRows(
		grid: HTMLDivElement,
		features: WriterEditorFeature[],
		onClick: (feature: WriterEditorFeature) => void,
		options?: { textOnly?: boolean },
	): void {
		const textOnly = options?.textOnly === true;
		const cols = 3;
		for (let rowStart = 0; rowStart < features.length; rowStart += cols) {
			const row = document.createElement('div');
			row.className = 'writer-function-chip-grid__row';
			for (
				let i = rowStart;
				i < Math.min(rowStart + cols, features.length);
				i++
			) {
				const feature = features[i];
				const chip = document.createElement('button');
				chip.type = 'button';
				chip.className =
					'writer-function-chip' +
					(textOnly ? ' writer-function-chip--text-only' : '') +
					(this.isParagraphAlignmentSelected(feature)
						? ' writer-function-chip--selected'
						: '');
				chip.setAttribute('aria-label', feature.label);
				if (!textOnly) {
					const iconWrap = document.createElement('span');
					iconWrap.className = 'writer-function-chip__icon';
					const icon = WriterEditorIcons.get(feature.icon);
					if (icon) {
						iconWrap.innerHTML = icon;
					}
					chip.appendChild(iconWrap);
				}
				const label = document.createElement('span');
				label.className = 'writer-function-chip__label';
				label.textContent = feature.label;
				chip.appendChild(label);
				const gated = this.isFeatureGated(feature);
				chip.disabled = gated;
				if (gated) {
					chip.title = '即将支持';
				}
				chip.onclick = () => onClick(feature);
				row.appendChild(chip);
			}
			grid.appendChild(row);
		}
	}

	private isFeatureGated(feature: WriterEditorFeature): boolean {
		const isDialog = feature.kind === 'dialog';
		const dialogReady =
			isDialog &&
			!!feature.dialog &&
			WriterEditorPanel.SUPPORTED_DIALOGS.indexOf(feature.dialog) >= 0;
		return isDialog && !dialogReady;
	}

	private createActionRow(
		feature: WriterEditorFeature,
		inGroup = false,
	): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className =
			'writer-function-action-row' +
			(inGroup ? ' writer-function-action-row--grouped' : '');
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
		if (this.isFeatureGated(feature)) {
			row.disabled = true;
			row.title = '即将支持';
		}
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createToggleRow(feature: WriterEditorFeature): HTMLElement {
		const command = feature.unocmd || '';
		const row = document.createElement('div');
		row.className = 'writer-function-toggle-row';

		if (feature.icon) {
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-toggle-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);
		}

		const label = document.createElement('span');
		label.textContent = feature.label;
		label.className = 'writer-function-toggle-row__label';
		row.appendChild(label);

		const toggle = document.createElement('input');
		toggle.type = 'checkbox';
		toggle.className = 'writer-function-toggle-row__input';
		toggle.setAttribute('aria-label', feature.label);
		toggle.checked = this.controller.isCommandChecked(
			command,
			!!feature.defaultOn,
		);
		if (command) {
			this.reviewToggleInputs[command] = toggle;
		}
		toggle.onchange = () => {
			this.controller.runToggle(feature, toggle.checked);
		};
		row.appendChild(toggle);
		return row;
	}

	private refreshReviewToggles(): void {
		Object.keys(this.reviewToggleInputs).forEach((command) => {
			const input = this.reviewToggleInputs[command];
			const feature = WriterEditorCatalog.FEATURES.find(
				(candidate) =>
					candidate.unocmd === command && candidate.kind === 'toggle',
			);
			input.checked = this.controller.isCommandChecked(
				command,
				feature ? !!feature.defaultOn : false,
			);
		});
	}

	private subscribeReviewState(): void {
		if (this.onReviewStateBound) {
			return;
		}
		const map = (window as any).app && (window as any).app.map;
		if (!map || typeof map.on !== 'function') {
			return;
		}
		const onState = (event: any) => {
			if (!event || typeof event.commandName !== 'string') {
				return;
			}
			if (event.commandName in this.reviewToggleInputs) {
				this.reviewToggleInputs[event.commandName].checked =
					event.state === 'true';
			}
			this.refreshPickerValues();
		};
		this.onReviewStateBound = onState;
		map.on('commandstatechanged', onState);
	}

	private unsubscribeReviewState(): void {
		if (!this.onReviewStateBound) {
			return;
		}
		const map = (window as any).app && (window as any).app.map;
		if (map && typeof map.off === 'function') {
			map.off('commandstatechanged', this.onReviewStateBound);
		}
		this.onReviewStateBound = null;
		Object.keys(this.reviewToggleInputs).forEach((command) => {
			delete this.reviewToggleInputs[command];
		});
	}

	private onFeature(feature: WriterEditorFeature): void {
		if (feature.kind === 'dialog') {
			const dialog = feature.dialog || '';
			if (dialog === 'fontName') {
				this.openFontNameDialog();
			} else if (dialog === 'fontSize') {
				this.openFontSizeDialog();
			} else if (dialog === 'table') {
				this.openTableDialog();
			} else if (dialog === 'margins') {
				this.openMarginsDialog();
			} else if (dialog === 'shape') {
				this.openShapeDialog();
			} else if (dialog === 'style') {
				this.openStyleDialog();
			} else if (dialog === 'watermark') {
				this.openWatermarkDialog();
				return;
			} else if (dialog === 'paperSize') {
				this.openPaperSizeDialog();
			} else if (dialog === 'chart') {
				this.openChartDialog();
			} else if (dialog === 'image') {
				this.openImageDialog();
			} else if (dialog === 'saveAs') {
				this.openSaveAsDialog();
			} else if (dialog === 'comment') {
				this.openCommentDialog();
			}
			return;
		}
		if (feature.kind === 'toggle') {
			return;
		}
		if (feature.kind === 'findReplace') {
			this.openFindReplaceDialog();
			return;
		}
		if (feature.kind === 'export' && (window as any).ThisIsTheiOSApp) {
			this.openExportFormatDialog();
			return;
		}
		const result = this.controller.run(feature);
		if (
			result.dispatched === 'unocmd' ||
			result.dispatched === 'save' ||
			result.dispatched === 'export'
		) {
			this.sheet.close();
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
					this.refreshPickerValues();
					this.controller.applyFontName(
						WriterEditorCatalog.aliasFont(option.value),
					);
				},
				this.pickerLabels['font-name'],
				this.subHost(),
			),
		);
	}

	private openFontSizeDialog(): void {
		const table = WriterEditorCatalog.CHAR_HEIGHT_CN;
		const options = Object.keys(table).map((label) => ({
			label,
			value: table[label],
		}));
		this.presentSub(
			new WriterEditorChooseDialog(
				'字号',
				options,
				(option) => {
					this.pickerLabels['font-size'] = option.label;
					this.refreshPickerValues();
					this.controller.applyFontSize(option.value);
				},
				this.pickerLabels['font-size'],
				this.subHost(),
			),
		);
	}

	private openOrientationDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: '纵向', value: 'portrait' },
			{ label: '横向', value: 'landscape' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'纸张方向',
				options,
				(option) => {
					this.pickerLabels.paper_orientation = option.label;
					this.refreshPickerValues();
					this.controller.applyPageOrientation(option.value === 'landscape');
				},
				this.pickerLabels.paper_orientation,
				this.subHost(),
			),
		);
	}

	private openFindReplaceDialog(): void {
		this.presentSub(new WriterFindReplaceDialog(true));
	}

	private openTableDialog(): void {
		this.presentSub(
			new WriterEditorInsertTableDialog(this.controller, this.subHost()),
		);
	}

	private openMarginsDialog(): void {
		const options: WriterChooseOption[] =
			WriterEditorCatalog.MARGIN_PRESETS.map((preset) => ({
				label: preset.label,
				value: [preset.left, preset.right, preset.top, preset.bottom].join(':'),
			}));
		this.presentSub(
			new WriterEditorChooseDialog(
				'页边距',
				options,
				(option) => {
					this.pickerLabels.page_margins = option.label;
					this.refreshPickerValues();
					const parts = option.value.split(':').map((part) => parseInt(part, 10));
					if (parts.length === 4 && parts.every((part) => !isNaN(part))) {
						this.controller.applyMargins(
							parts[0],
							parts[1],
							parts[2],
							parts[3],
						);
					}
				},
				this.pickerLabels.page_margins,
				this.subHost(),
			),
		);
	}

	private openShapeDialog(): void {
		this.presentSub(
			new WriterEditorShapeDialog(this.controller, this.subHost()),
		);
	}

	private openChartDialog(): void {
		this.presentSub(
			new WriterEditorChartDialog(this.controller, this.subHost()),
		);
	}

	private openStyleDialog(): void {
		const values = this.controller.getCommandValues('.uno:StyleApply');
		const styles =
			values && Array.isArray(values.ParagraphStyles)
				? (values.ParagraphStyles as string[])
				: [];
		if (!styles.length) {
			return;
		}
		const options: WriterChooseOption[] =
			WriterEditorCatalog.reorderStyleOptions(styles);
		this.presentSub(
			new WriterEditorChooseDialog(
				'样式',
				options,
				(option) => {
					this.pickerLabels.style = option.label;
					this.refreshPickerValues();
					this.controller.applyStyle(option.value);
				},
				this.pickerLabels.style,
				this.subHost(),
			),
		);
	}

	private openWatermarkDialog(): void {
		const fontOptions = this.getFontOptions().map((name) =>
			WriterEditorCatalog.aliasFont(name),
		);
		const dialog = new WriterEditorWatermarkDialog(
			this.controller,
			fontOptions,
			{
				text: this.watermarkText,
				angle: this.watermarkAngle,
				transparency: this.watermarkTransparency,
				font: this.watermarkFont || undefined,
				onApplied: (payload) => {
					this.watermarkText = payload.text;
					this.watermarkFont = payload.font;
					this.watermarkAngle = payload.angle;
					this.watermarkTransparency = payload.transparency;
					this.watermarkEnabled = payload.enabled;
					if (this.watermarkToggleInput) {
						this.watermarkToggleInput.checked = payload.enabled;
					}
				},
			},
			this.subHost(),
		);
		this.presentSub(dialog);
	}

	private getFontOptions(): string[] {
		const values = this.controller.getCommandValues('.uno:CharFontName');
		const names = values ? Object.keys(values).filter((name) => !!name) : [];
		return names.length ? names : WriterEditorCatalog.FONT_FALLBACK_OPTIONS;
	}

	private openPaperSizeDialog(): void {
		const options: WriterChooseOption[] = WriterEditorCatalog.PAPER_FORMATS.map(
			(preset) => ({
				label: preset.label,
				value: preset.value,
			}),
		);
		options.push({ label: '自定义尺寸…', value: 'custom' });
		this.presentSub(
			new WriterEditorChooseDialog(
				'纸张大小',
				options,
				(option) => {
					if (option.value === 'custom') {
						this.presentSub(
							new WriterEditorPaperSizeDialog(
								this.controller,
								this.subHost(),
							),
						);
						return;
					}
					this.pickerLabels.paper_size = option.label;
					this.refreshPickerValues();
					this.controller.applyPaperFormat(option.value);
				},
				this.pickerLabels.paper_size,
				this.subHost(),
			),
		);
	}

	private openImageDialog(): void {
		this.presentSub(
			new WriterEditorImageDialog(this.controller, this.subHost()),
		);
	}

	private openCommentDialog(): void {
		this.presentSub(
			new WriterEditorCommentDialog(this.controller, this.subHost()),
		);
	}

	private openSaveAsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'ODF 文本文档 (.odt)', value: 'odt' },
			{ label: 'Word 文档 (.docx)', value: 'docx' },
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: '纯文本 (.txt)', value: 'txt' },
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

	private openExportFormatDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: 'ODF 文本文档 (.odt)', value: 'odt' },
			{ label: 'Word 文档 (.docx)', value: 'docx' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'导出为',
				options,
				(option) => {
					this.controller.exportAs(option.value);
				},
				undefined,
				this.subHost(),
			),
		);
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	// bundle.js is loaded with defer, so readyState is already "interactive"
	// while this file is still evaluating. Never mount synchronously here:
	// WriterEditorSheet (and Leaflet/Map after it) must finish parsing first.
	const mount = () => {
		try {
			WriterEditorPanel.mount();
		} catch (_e) {
			window.setTimeout(mount, 0);
		}
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		window.setTimeout(mount, 0);
	}
}
