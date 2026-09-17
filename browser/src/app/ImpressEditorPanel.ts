/*
 * iOS Impress edit-mode function panel: 常用 / 文件 / 插入 / 切换 / 布局 / 审阅.
 * Reuses WriterEditorSheet chrome/tokens. Does not open PreviewFunctionSheet.
 */

class ImpressEditorPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly tabBar: HTMLDivElement;
	private readonly tabScroll: HTMLDivElement;
	private readonly grid: HTMLDivElement;
	private activeTab: ImpressEditorTab = 'default';
	private readonly subDialogs: { close(): void }[] = [];
	private readonly inlineSubpage: WriterEditorInlineSubpage;
	private readonly pickerLabels: Record<string, string> = {
		'slide-format': 'A4',
		'slide-orientation': '横向',
		'slide-background': '渐变',
		'slide-master': '默认',
		'font-name': '宋体',
		'font-size': '四号',
	};
	private selectedTransitionId = 'tr-box';
	private selectedLayoutId = 'layout-title';
	private fontColorRgb = 0x000000;
	private slideBackgroundColorRgb = 0xffffff;
	private slideMasterSolidColorRgb = 0xffffff;
	private readonly controller = WriterEditorController.getInstance();

	private constructor() {
		this.sheet = new WriterEditorSheet('', undefined, { editMode: true });
		const content = document.createElement('div');
		content.className = 'writer-function-panel';
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
		content.appendChild(this.tabBar);
		const scrollContent = document.createElement('div');
		scrollContent.className = 'writer-function-content';
		this.grid = document.createElement('div');
		this.grid.className = 'writer-function-grid';
		scrollContent.appendChild(this.grid);
		content.appendChild(scrollContent);
		this.sheet.setBody(content);
		this.inlineSubpage = new WriterEditorInlineSubpage(this.sheet.body, [
			scrollContent,
		]);
	}

	private subHost(): WriterEditorInlineSubpageHost {
		return this.inlineSubpage;
	}

	static mount(): ImpressEditorPanel | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolImpressEditorPanel;
		if (existing instanceof ImpressEditorPanel) {
			return existing;
		}
		const panel = new ImpressEditorPanel();
		(window as any).__coolImpressEditorPanel = panel;
		return panel;
	}

	open(): void {
		MobileDocTheme.applyFromMap();
		this.renderTabs();
		this.renderGrid();
		this.sheet.open();
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

	close(): void {
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
		ImpressEditorCatalog.TABS.forEach((tab) => {
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
				this.renderGrid();
			};
			this.tabScroll.appendChild(button);
		});
	}

	private renderGrid(): void {
		this.grid.replaceChildren();
		this.grid.className = 'writer-function-grid writer-function-grid--common';
		switch (this.activeTab) {
			case 'default':
				this.renderDefaultTab();
				return;
			case 'file':
			case 'review':
				this.renderActionTab(this.activeTab);
				return;
			case 'insert':
				this.renderInsertTab();
				return;
			case 'transition':
				this.renderTransitionTab();
				return;
			case 'layout':
				this.renderLayoutTab();
				return;
		}
	}

	private renderActionTab(tab: ImpressEditorTab): void {
		const stack = document.createElement('div');
		stack.className = 'writer-function-stack';
		const group = document.createElement('div');
		group.className = 'writer-function-action-group';
		const features = ImpressEditorCatalog.getFeatures(tab).filter(
			(f) => f.kind !== 'section',
		);
		features.forEach((feature, index) => {
			group.appendChild(this.createActionRow(feature));
			if (index + 1 < features.length) {
				const divider = document.createElement('div');
				divider.className = 'writer-function-action-group__divider';
				group.appendChild(divider);
			}
		});
		stack.appendChild(group);
		this.grid.appendChild(stack);
	}

	private createActionRow(feature: ImpressEditorFeature): HTMLButtonElement {
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
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private renderInsertTab(): void {
		const grid = document.createElement('div');
		grid.className =
			'writer-function-chip-grid writer-function-chip-grid--insert writer-function-chip-grid--impress-insert';
		const features = ImpressEditorCatalog.getFeatures('insert');
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
				chip.className = 'writer-function-chip';
				chip.setAttribute('aria-label', feature.label);
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-chip__icon';
				const icon = WriterEditorIcons.get(feature.icon);
				if (icon) {
					iconWrap.innerHTML = icon;
				}
				chip.appendChild(iconWrap);
				const label = document.createElement('span');
				label.className = 'writer-function-chip__label';
				label.textContent = feature.label;
				chip.appendChild(label);
				chip.onclick = () => this.onFeature(feature);
				row.appendChild(chip);
			}
			grid.appendChild(row);
		}
		this.grid.appendChild(grid);
	}

	private renderTransitionTab(): void {
		const stack = document.createElement('div');
		stack.className = 'writer-function-impress-transition-tab';

		const applyAll = document.createElement('button');
		applyAll.type = 'button';
		applyAll.className = 'writer-function-impress-apply-all-row';
		applyAll.setAttribute('aria-label', '应用到全部幻灯片');
		const applyIcon = document.createElement('span');
		applyIcon.className = 'writer-function-impress-apply-all-row__icon';
		const applySvg = WriterEditorIcons.get('impress-apply-transition-all');
		if (applySvg) {
			applyIcon.innerHTML = applySvg;
		}
		applyAll.appendChild(applyIcon);
		const applyLabel = document.createElement('span');
		applyLabel.className = 'writer-function-impress-apply-all-row__label';
		applyLabel.textContent = '应用到全部幻灯片';
		applyAll.appendChild(applyLabel);
		applyAll.onclick = () => {
			const feature = ImpressEditorCatalog.getFeature(this.selectedTransitionId);
			if (!feature || feature.iconViewIndex === undefined) {
				return;
			}
			this.applyTransition(feature.iconViewIndex, true);
		};
		stack.appendChild(applyAll);

		const wrap = document.createElement('div');
		wrap.className = 'writer-function-transition-grid';
		const features = ImpressEditorCatalog.getFeatures('transition');
		const cols = 6;
		for (let rowStart = 0; rowStart < features.length; rowStart += cols) {
			const row = document.createElement('div');
			row.className =
				'writer-function-transition-grid__row writer-function-transition-grid__row--six';
			for (
				let i = rowStart;
				i < Math.min(rowStart + cols, features.length);
				i++
			) {
				const feature = features[i];
				const cell = document.createElement('button');
				cell.type = 'button';
				cell.className = 'writer-function-transition-cell';
				if (feature.id === this.selectedTransitionId) {
					cell.classList.add('writer-function-transition-cell--active');
				}
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-transition-cell__icon';
				const icon = WriterEditorIcons.get(feature.icon);
				if (icon) {
					iconWrap.innerHTML = icon;
				}
				cell.appendChild(iconWrap);
				const caption = document.createElement('span');
				caption.className = 'writer-function-transition-cell__label';
				caption.textContent = feature.label;
				cell.appendChild(caption);
				cell.onclick = () => {
					this.selectedTransitionId = feature.id;
					this.onFeature(feature, { keepPanelOpen: true });
					this.renderGrid();
				};
				row.appendChild(cell);
			}
			wrap.appendChild(row);
		}
		stack.appendChild(wrap);
		this.grid.appendChild(stack);
	}

	private renderLayoutTab(): void {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-impress-layout-grid';
		const features = ImpressEditorCatalog.getFeatures('layout');
		const cols = 3;
		for (let rowStart = 0; rowStart < features.length; rowStart += cols) {
			const row = document.createElement('div');
			row.className = 'writer-function-impress-layout-grid__row';
			for (
				let i = rowStart;
				i < Math.min(rowStart + cols, features.length);
				i++
			) {
				const feature = features[i];
				const cell = document.createElement('button');
				cell.type = 'button';
				cell.className = 'writer-function-impress-layout-cell';
				if (feature.id === this.selectedLayoutId) {
					cell.classList.add('writer-function-impress-layout-cell--active');
				}
				cell.setAttribute('aria-label', feature.label);
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-impress-layout-cell__icon';
				const icon = WriterEditorIcons.get(feature.icon);
				if (icon) {
					iconWrap.innerHTML = icon;
				}
				cell.appendChild(iconWrap);
				const caption = document.createElement('span');
				caption.className = 'writer-function-impress-layout-cell__label';
				caption.textContent = feature.label;
				cell.appendChild(caption);
				cell.onclick = () => {
					this.selectedLayoutId = feature.id;
					this.onFeature(feature);
				};
				row.appendChild(cell);
			}
			wrap.appendChild(row);
		}
		this.grid.appendChild(wrap);
	}

	private renderDefaultTab(): void {
		this.grid.className = 'writer-function-grid writer-function-grid--common';
		const features = ImpressEditorCatalog.getFeatures('default');
		let chipHost: HTMLDivElement | null = null;
		let layoutBuffer: ImpressEditorFeature[] = [];
		let splitPickerParts: ImpressEditorFeature[] = [];
		let charToolBuffer: ImpressEditorFeature[] = [];

		const flushSplitPicker = () => {
			if (splitPickerParts.length !== 2) {
				splitPickerParts = [];
				return;
			}
			this.grid.appendChild(this.createSplitFontSizeColorRow(splitPickerParts));
			splitPickerParts = [];
		};

		const flushCharTools = () => {
			if (!charToolBuffer.length) {
				return;
			}
			this.grid.appendChild(this.createCharToolGrid(charToolBuffer));
			charToolBuffer = [];
		};

		const flushLayoutPreview = () => {
			if (!layoutBuffer.length) {
				return;
			}
			this.grid.appendChild(this.createLayoutPreviewRow(layoutBuffer));
			layoutBuffer = [];
		};

		features.forEach((feature) => {
			if (feature.kind === 'section') {
				flushCharTools();
				flushSplitPicker();
				flushLayoutPreview();
				if (feature.id === 'sec-layout') {
					this.grid.appendChild(this.createLayoutSectionHeader());
				} else {
					const title = document.createElement('h3');
					title.textContent = feature.label;
					title.className = 'writer-function-section-title';
					this.grid.appendChild(title);
				}
				return;
			}
			if (feature.row === 'layoutPreview') {
				layoutBuffer.push(feature);
				return;
			}
			flushLayoutPreview();
			if (feature.row === 'splitPicker') {
				splitPickerParts.push(feature);
				if (splitPickerParts.length === 2) {
					flushSplitPicker();
				}
				return;
			}
			flushSplitPicker();
			if (feature.row === 'charTool') {
				charToolBuffer.push(feature);
				return;
			}
			flushCharTools();
			if (feature.row === 'picker') {
				this.grid.appendChild(this.createPickerRow(feature));
				return;
			}
			if (feature.row === 'chip') {
				if (!chipHost) {
					chipHost = document.createElement('div');
					chipHost.className = 'writer-function-para-grid';
					this.grid.appendChild(chipHost);
				}
				chipHost.appendChild(this.createParagraphChip(feature));
			}
		});
		flushCharTools();
		flushSplitPicker();
		flushLayoutPreview();
	}

	private createSplitFontSizeColorRow(features: ImpressEditorFeature[]): HTMLDivElement {
		const sizeFeature =
			features.find((item) => item.splitPart === 'size') || features[0];
		const colorFeature =
			features.find((item) => item.splitPart === 'color') || features[1];
		const row = document.createElement('div');
		row.className = 'writer-function-split-picker-row';

		const sizeButton = document.createElement('button');
		sizeButton.type = 'button';
		sizeButton.className = 'writer-function-split-picker-row__cell';
		sizeButton.setAttribute('aria-label', sizeFeature.label);
		const sizeValue = document.createElement('span');
		sizeValue.className = 'writer-function-split-picker-row__value';
		sizeValue.textContent =
			this.pickerLabels['font-size'] || sizeFeature.pickerDefault || sizeFeature.label;
		sizeButton.appendChild(sizeValue);
		const sizeChevron = document.createElement('span');
		sizeChevron.className = 'writer-function-split-picker-row__chevron';
		sizeChevron.innerHTML = ImpressEditorPanel.pickerChevronRight();
		sizeButton.appendChild(sizeChevron);
		sizeButton.onclick = () => this.onFeature(sizeFeature);
		row.appendChild(sizeButton);

		const colorButton = document.createElement('button');
		colorButton.type = 'button';
		colorButton.className = 'writer-function-split-picker-row__cell';
		colorButton.setAttribute('aria-label', colorFeature.label);
		const swatch = document.createElement('span');
		swatch.className = 'writer-function-split-picker-row__swatch';
		swatch.style.backgroundColor = ImpressEditorPanel.rgbCss(this.fontColorRgb);
		colorButton.appendChild(swatch);
		const colorLabel = document.createElement('span');
		colorLabel.className = 'writer-function-split-picker-row__label';
		colorLabel.textContent = colorFeature.label;
		colorButton.appendChild(colorLabel);
		const colorChevron = document.createElement('span');
		colorChevron.className = 'writer-function-split-picker-row__chevron';
		colorChevron.innerHTML = ImpressEditorPanel.pickerChevronRight();
		colorButton.appendChild(colorChevron);
		colorButton.onclick = () => this.onFeature(colorFeature);
		row.appendChild(colorButton);
		return row;
	}

	private createCharToolGrid(features: ImpressEditorFeature[]): HTMLDivElement {
		const container = document.createElement('div');
		container.className = 'writer-function-char-tools';
		const row1 = document.createElement('div');
		row1.className = 'writer-function-char-tools__row writer-function-char-tools__row--fill';
		const row2 = document.createElement('div');
		row2.className = 'writer-function-char-tools__row';
		features.forEach((feature) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-char-tools__btn';
			button.setAttribute('aria-label', feature.label);
			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-char-tools__icon';
			iconWrap.innerHTML = ImpressCharToolIcons.iconFor(feature.id);
			button.appendChild(iconWrap);
			button.onclick = () => this.onFeature(feature, { keepPanelOpen: true });
			if (feature.group === 'char-tools-2') {
				row2.appendChild(button);
			} else {
				row1.appendChild(button);
			}
		});
		container.appendChild(row1);
		if (row2.childElementCount) {
			container.appendChild(row2);
		}
		return container;
	}

	private static rgbCss(rgb: number): string {
		let hex = rgb.toString(16).toUpperCase();
		while (hex.length < 6) {
			hex = '0' + hex;
		}
		return '#' + hex;
	}

	private static pickerChevronRight(): string {
		return (
			'<svg viewBox="0 0 16 16" width="16" height="16" fill="none" ' +
			'xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
			'<path d="M6 3.5L10.5 8 6 12.5" stroke="#80868B" stroke-width="1.5" ' +
			'stroke-linecap="round" stroke-linejoin="round"/></svg>'
		);
	}

	private createLayoutSectionHeader(): HTMLDivElement {
		const header = document.createElement('div');
		header.className = 'writer-function-section-header-row';
		const title = document.createElement('span');
		title.className = 'writer-function-section-title';
		title.textContent = '布局';
		const all = document.createElement('button');
		all.type = 'button';
		all.className = 'writer-function-section-header-row__link';
		all.textContent = '全部 >';
		all.onclick = () => {
			this.activeTab = 'layout';
			this.renderTabs();
			this.renderGrid();
		};
		header.appendChild(title);
		header.appendChild(all);
		return header;
	}

	private createPickerRow(feature: ImpressEditorFeature): HTMLButtonElement {
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
		const title = document.createElement('span');
		title.className = 'writer-function-picker-row__title';
		title.textContent = feature.label;
		row.appendChild(title);
		const value = document.createElement('span');
		value.className = 'writer-function-picker-row__value';
		value.textContent =
			this.pickerLabels[feature.id] ||
			feature.pickerDefault ||
			feature.label;
		row.appendChild(value);
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-picker-row__chevron';
		chevron.innerHTML = ImpressEditorPanel.pickerChevronRight();
		row.appendChild(chevron);
		row.onclick = () => this.onFeature(feature);
		return row;
	}

	private createParagraphChip(feature: ImpressEditorFeature): HTMLButtonElement {
		const chip = document.createElement('button');
		chip.type = 'button';
		chip.className = 'writer-function-para-chip';
		chip.setAttribute('aria-label', feature.label);
		const iconWrap = document.createElement('span');
		iconWrap.className = 'writer-function-para-chip__icon';
		const icon = WriterEditorIcons.get(feature.icon);
		if (icon) {
			iconWrap.innerHTML = icon;
		}
		chip.appendChild(iconWrap);
		const label = document.createElement('span');
		label.className = 'writer-function-para-chip__label';
		label.textContent = feature.label;
		chip.appendChild(label);
		chip.onclick = () => this.onFeature(feature, { keepPanelOpen: true });
		return chip;
	}

	private createLayoutPreviewRow(features: ImpressEditorFeature[]): HTMLDivElement {
		const row = document.createElement('div');
		row.className = 'writer-function-layout-preview';
		features.forEach((feature) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-layout-preview__item';
			button.setAttribute('aria-label', feature.label);
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-layout-preview__icon';
				iconWrap.innerHTML = icon;
				button.appendChild(iconWrap);
			}
			const tileLabel = document.createElement('span');
			tileLabel.textContent = feature.label;
			tileLabel.className = 'writer-function-layout-preview__label';
			button.appendChild(tileLabel);
			button.onclick = () => this.onFeature(feature);
			row.appendChild(button);
		});
		return row;
	}

	private onFeature(
		feature: ImpressEditorFeature,
		options?: { keepPanelOpen?: boolean },
	): void {
		if (feature.kind === 'stub') {
			if (feature.id === 'insert-more-fields') {
				(window as any).app?.console?.log(
					'ImpressEditorPanel: insert_more_fields todo (Android parity stub)',
				);
			}
			return;
		}
		if (feature.kind === 'dialog') {
			if (feature.dialog === 'chart') {
				this.presentSub(
					new WriterEditorChartDialog(this.controller, this.subHost()),
				);
			} else if (feature.dialog === 'image') {
				this.presentSub(
					new WriterEditorImageDialog(this.controller, this.subHost()),
				);
			} else if (feature.dialog === 'table') {
				this.presentSub(
					new WriterEditorInsertTableDialog(this.controller, this.subHost()),
				);
			} else if (feature.dialog === 'shape') {
				this.presentSub(
					new WriterEditorShapeDialog(this.controller, this.subHost()),
				);
			} else if (feature.dialog === 'comment') {
				this.presentSub(
					new WriterEditorCommentDialog(this.controller, this.subHost()),
				);
			} else if (feature.dialog === 'hyperlink') {
				this.presentSub(new ImpressEditorHyperlinkDialog(this.controller));
			} else if (feature.dialog === 'saveAs') {
				this.openSaveAsDialog();
			} else if (feature.dialog === 'slideFormat') {
				this.openSlideFormatDialog();
			} else if (feature.dialog === 'slideOrientation') {
				this.openSlideOrientationDialog();
			} else if (feature.dialog === 'slideBackground') {
				this.openSlideBackgroundDialog();
			} else if (feature.dialog === 'slideMaster') {
				this.openSlideMasterDialog();
			} else if (feature.dialog === 'fontName') {
				this.openFontNameDialog();
			} else if (feature.dialog === 'fontSize') {
				this.openFontSizeDialog();
			} else if (feature.dialog === 'fontColor') {
				this.openFontColorDialog();
			} else if (feature.dialog === 'highlightColor') {
				this.openHighlightColorDialog();
			}
			return;
		}
		if (feature.kind === 'findReplace') {
			this.presentSub(new WriterFindReplaceDialog(true));
			return;
		}
		if (feature.kind === 'save') {
			this.mobileSave();
			this.sheet.close();
			return;
		}
		if (feature.kind === 'export') {
			if (typeof (window as any).postMobileMessage === 'function') {
				(window as any).postMobileMessage(
					'downloadas name=export.pdf format=pdf',
				);
			}
			this.sheet.close();
			return;
		}
		if (feature.kind === 'print') {
			if (typeof (window as any).postMobileMessage === 'function') {
				(window as any).postMobileMessage('PRINT');
			}
			this.sheet.close();
			return;
		}
		if (feature.kind === 'queryCommand' && feature.unocmd) {
			this.sendUno(feature.unocmd + (feature.queryParams || ''));
			if (!options?.keepPanelOpen) {
				this.sheet.close();
			}
			return;
		}
		if (feature.tab === 'transition') {
			this.applyTransition(feature.iconViewIndex || 0, false);
			return;
		}
		if (feature.kind === 'charTool') {
			if (feature.dialog === 'highlightColor') {
				this.openHighlightColorDialog();
				return;
			}
			if (feature.unocmd) {
				this.sendUno(feature.unocmd);
			}
			return;
		}
		if (feature.kind === 'command' && feature.unocmd) {
			this.sendUno(feature.unocmd);
			if (!options?.keepPanelOpen) {
				this.sheet.close();
			}
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
					this.controller.applyFontName(
						WriterEditorCatalog.aliasFont(option.value),
					);
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
					this.applyImpressFontColor(rgb);
				},
				this.subHost(),
			),
		);
	}

	private openHighlightColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog(
				'荧光颜色',
				null,
				(rgb) => {
					this.controller.applyHighlightColor(rgb);
				},
				this.subHost(),
			),
		);
	}

	/** Impress Android uses `.uno:Color`, not Writer `.uno:FontColor`. */
	private applyImpressFontColor(rgb: number): void {
		const command =
			'.uno:Color {"Color.Color":{"type":"long","value":' + rgb + '}}';
		this.sendUno(command);
	}

	private getFontOptions(): string[] {
		const values = this.controller.getCommandValues('.uno:CharFontName');
		const names = values ? Object.keys(values).filter((name) => !!name) : [];
		return names.length ? names : WriterEditorCatalog.FONT_FALLBACK_OPTIONS;
	}

	private openSlideFormatDialog(): void {
		const options: WriterChooseOption[] = ImpressEditorCatalog.SLIDE_FORMATS.map(
			(preset) => ({
				label: preset.label,
				value: preset.paperFormat || preset.pageSizeUno || preset.label,
			}),
		);
		this.presentSub(
			new WriterEditorChooseDialog(
				'格式',
				options,
				(option) => {
					this.pickerLabels['slide-format'] = option.label;
					const preset = ImpressEditorCatalog.SLIDE_FORMATS.find(
						(entry) => entry.label === option.label,
					);
					if (preset?.pageSizeUno) {
						this.sendUno(preset.pageSizeUno);
						return;
					}
					if (option.value === '11') {
						this.presentSub(
							new WriterEditorPaperSizeDialog(
								this.controller,
								this.subHost(),
							),
						);
						return;
					}
					if (preset?.paperFormat) {
						this.controller.applyPaperFormat(preset.paperFormat);
					}
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openSlideOrientationDialog(): void {
		const options: WriterChooseOption[] =
			ImpressEditorCatalog.SLIDE_ORIENTATIONS.map((entry) => ({
				label: entry.label,
				value: entry.unocmd,
			}));
		this.presentSub(
			new WriterEditorChooseDialog(
				'方向',
				options,
				(option) => {
					this.pickerLabels['slide-orientation'] = option.label;
					this.sendUno(option.value);
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openSlideBackgroundDialog(): void {
		const options: WriterChooseOption[] =
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS.map((entry) => ({
				label: entry.label,
				value: entry.label,
			}));
		this.presentSub(
			new WriterEditorChooseDialog(
				'背景',
				options,
				(option) => {
			const entry = ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS.find(
				(item) => item.label === option.label,
			);
			if (!entry) {
				return;
			}
			this.pickerLabels['slide-background'] = entry.label;
			this.renderGrid();
			if (entry.unocmd) {
				this.sendUno(entry.unocmd);
			}
			if (entry.afterSelect === 'colorPicker') {
				this.presentSub(
					new WriterColorPickerDialog(
						'背景颜色',
						this.slideBackgroundColorRgb,
						(rgb) => {
							this.slideBackgroundColorRgb = rgb;
							this.applyImpressSlideBackgroundColor(rgb);
						},
						this.subHost(),
					),
				);
				return;
			}
			if (entry.afterSelect === 'imagePicker') {
				this.controller.requestNativeImagePicker();
			}
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private openSlideMasterDialog(): void {
		const options: WriterChooseOption[] = ImpressEditorCatalog.SLIDE_MASTER_OPTIONS.map(
			(entry) => ({
				label: entry.label,
				value: entry.label,
			}),
		);
		this.presentSub(
			new WriterEditorChooseDialog(
				'母版幻灯片',
				options,
				(option) => {
			const entry = ImpressEditorCatalog.SLIDE_MASTER_OPTIONS.find(
				(item) => item.label === option.label,
			);
			if (!entry) {
				return;
			}
			this.pickerLabels['slide-master'] = entry.label;
			this.renderGrid();
			if (entry.unocmd) {
				this.sendUno(entry.unocmd);
			}
			if (entry.afterSelect === 'colorPicker') {
				this.presentSub(
					new ImpressSolidColorPickerDialog(
						'纯色',
						this.slideMasterSolidColorRgb,
						(rgb) => {
							this.slideMasterSolidColorRgb = rgb;
							this.applyImpressMasterSolidColor(rgb);
							this.pickerLabels['slide-master'] = '纯色';
						},
						this.subHost(),
					),
				);
			}
				},
				undefined,
				this.subHost(),
			),
		);
	}

	private applyImpressSlideBackgroundColor(rgb: number): void {
		this.sendUno(ImpressEditorCatalog.buildFillPageStyleCommand(1));
		this.sendUno(ImpressEditorCatalog.buildBackgroundColorCommand(rgb));
	}

	private applyImpressMasterSolidColor(rgb: number): void {
		this.sendUno(ImpressEditorCatalog.buildBackgroundColorCommand(rgb));
	}

	private sendUno(command: string): void {
		const map = (window as any).app?.map;
		if (map && typeof map.sendUnoCommand === 'function') {
			map.sendUnoCommand(command);
			return;
		}
		const socket = (window as any).app?.socket;
		if (socket && typeof socket.sendMessage === 'function') {
			socket.sendMessage('uno ' + command);
		}
	}

	private mobileSave(): void {
		const message = 'save dontTerminateEdit=1 dontSaveIfUnmodified=1';
		if (typeof (window as any).postMobileMessage === 'function') {
			(window as any).postMobileMessage(message);
			return;
		}
		const socket = (window as any).app?.socket;
		if (socket && typeof socket.sendMessage === 'function') {
			socket.sendMessage(message);
		}
	}

	private applyTransition(iconViewIndex: number, applyToAll: boolean): void {
		ImpressTransitionBridge.apply(iconViewIndex, applyToAll);
	}

	private openSaveAsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'ODF 演示文稿 (.odp)', value: 'odp' },
			{ label: 'PowerPoint (.pptx)', value: 'pptx' },
			{ label: 'PDF (.pdf)', value: 'pdf' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'另存为',
				options,
				(option) => {
					if (typeof (window as any).postMobileMessage === 'function') {
						(window as any).postMobileMessage(
							'downloadas name=export.' +
								option.value +
								' format=' +
								option.value,
						);
					}
				},
				undefined,
				this.subHost(),
			),
		);
	}

}

/** Char tool icons (reuse WriterCharPanelIcons where available). */
class ImpressCharToolIcons {
	static iconFor(featureId: string): string {
		switch (featureId) {
			case 'char-bold':
				return WriterCharPanelIcons.bold;
			case 'char-italic':
				return WriterCharPanelIcons.italic;
			case 'char-underline':
				return WriterCharPanelIcons.underline;
			case 'char-strikeout':
				return WriterCharPanelIcons.strikeout;
			case 'char-highlight':
				return WriterCharPanelIcons.highlight;
			case 'char-shadow':
				return ImpressCharToolIcons.shadow;
			case 'char-superscript':
				return ImpressCharToolIcons.superscript;
			case 'char-subscript':
				return ImpressCharToolIcons.subscript;
			default:
				return WriterCharPanelIcons.bold;
		}
	}

	private static readonly shadow =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg">' +
		'<path d="M14 32h16" stroke="#101010" stroke-width="3" stroke-linecap="round"/>' +
		'<path d="M18 24h12" stroke="#101010" stroke-width="3" stroke-linecap="round"/>' +
		'<path d="M22 16h4" stroke="#101010" stroke-width="3" stroke-linecap="round"/>' +
		'<path d="M20 36h16" stroke="#80868B" stroke-width="2.5" stroke-linecap="round"/></svg>';

	private static readonly superscript =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg">' +
		'<text x="8" y="32" font-size="22" fill="#101010" font-family="sans-serif">X</text>' +
		'<text x="28" y="20" font-size="14" fill="#101010" font-family="sans-serif">2</text></svg>';

	private static readonly subscript =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg">' +
		'<text x="8" y="28" font-size="22" fill="#101010" font-family="sans-serif">X</text>' +
		'<text x="28" y="38" font-size="14" fill="#101010" font-family="sans-serif">2</text></svg>';
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mount = () => {
		try {
			if (ImpressEditorPanel.mount()) {
				return;
			}
		} catch (_e) {
			window.setTimeout(mount, 0);
			return;
		}
		window.setTimeout(mount, 250);
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		// Defer until later COOL_JS modules (e.g. WriterEditorInlineSubpage) finish parsing.
		window.setTimeout(mount, 0);
	}
}
