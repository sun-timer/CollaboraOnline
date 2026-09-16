/*
 * iOS Impress edit-mode function panel: 常用 / 文件 / 插入 / 切换 / 布局 / 审阅.
 * Reuses WriterEditorSheet chrome/tokens. Does not open PreviewFunctionSheet.
 */

class ImpressEditorPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly tabBar: HTMLDivElement;
	private readonly grid: HTMLDivElement;
	private activeTab: ImpressEditorTab = 'default';
	private readonly subDialogs: { close(): void }[] = [];
	private readonly inlineSubpage: WriterEditorInlineSubpage;
	private readonly pickerLabels: Record<string, string> = {
		'font-name': '宋体',
		'font-size': '四号',
	};
	private fontColorRgb = 0x000000;
	private slideBackgroundColorRgb = 0xffffff;
	private slideMasterSolidColorRgb = 0xffffff;
	private readonly controller = WriterEditorController.getInstance();

	private constructor() {
		this.sheet = new WriterEditorSheet('功能');
		const content = document.createElement('div');
		content.className = 'writer-function-panel';
		this.tabBar = document.createElement('div');
		this.tabBar.className = 'writer-function-tab-bar';
		content.appendChild(this.tabBar);
		this.grid = document.createElement('div');
		this.grid.className = 'writer-function-grid';
		content.appendChild(this.grid);
		this.sheet.setBody(content);
		this.inlineSubpage = new WriterEditorInlineSubpage(this.sheet.body, [content]);
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
		this.renderTabs();
		this.renderGrid();
		this.sheet.open();
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
		this.tabBar.replaceChildren();
		const track = document.createElement('div');
		track.className = 'writer-function-tab-track';
		const scroll = document.createElement('div');
		scroll.className = 'writer-function-tab-scroll';
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
			scroll.appendChild(button);
		});
		track.appendChild(scroll);
		this.tabBar.appendChild(track);
	}

	private renderGrid(): void {
		this.grid.replaceChildren();
		if (this.activeTab === 'default') {
			this.renderDefaultTab();
			return;
		}
		this.grid.className =
			this.activeTab === 'layout' || this.activeTab === 'transition'
				? 'writer-function-grid writer-function-grid--common'
				: 'writer-function-grid';
		const features = ImpressEditorCatalog.getFeatures(this.activeTab);
		features.forEach((feature) => {
			if (feature.kind === 'section') {
				const title = document.createElement('h3');
				title.textContent = feature.label;
				title.className = 'writer-function-section-title';
				this.grid.appendChild(title);
				return;
			}
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-tile';
			button.setAttribute('aria-label', feature.label);
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-tile__icon';
				iconWrap.innerHTML = icon;
				button.appendChild(iconWrap);
			}
			const tileLabel = document.createElement('span');
			tileLabel.textContent = feature.label;
			tileLabel.className = 'writer-function-tile__label';
			button.appendChild(tileLabel);
			button.onclick = () => this.onFeature(feature);
			this.grid.appendChild(button);
		});
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
				const title = document.createElement('h3');
				title.textContent = feature.label;
				title.className = 'writer-function-section-title';
				this.grid.appendChild(title);
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
			this.pickerLabels[feature.id] || feature.pickerDefault || feature.label;
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
			button.onclick = () => this.onFeature(feature, { keepPanelOpen: true });
			row.appendChild(button);
		});
		return row;
	}

	private onFeature(
		feature: ImpressEditorFeature,
		options?: { keepPanelOpen?: boolean },
	): void {
		if (feature.kind === 'dialog') {
			if (feature.dialog === 'image') {
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
			this.sendUno('.uno:Save');
			this.sheet.close();
			return;
		}
		if (feature.kind === 'export') {
			this.openExportDialog();
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
			this.applyTransition(feature.iconViewIndex || 0);
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
			new WriterColorPickerDialog('字体颜色', this.fontColorRgb, (rgb) => {
				this.fontColorRgb = rgb;
				this.applyImpressFontColor(rgb);
			}),
		);
	}

	private openHighlightColorDialog(): void {
		this.presentSub(
			new WriterColorPickerDialog('荧光颜色', null, (rgb) => {
				this.controller.applyHighlightColor(rgb);
			}),
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
				value: preset.paperFormat,
			}),
		);
		this.presentSub(
			new WriterEditorChooseDialog(
				'格式',
				options,
				(option) => {
					this.pickerLabels['slide-format'] = option.label;
					if (option.value === '11') {
						this.presentSub(
							new WriterEditorPaperSizeDialog(
								this.controller,
								this.subHost(),
							),
						);
						return;
					}
					this.controller.applyPaperFormat(option.value);
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
			if (entry.unocmd) {
				this.sendUno(entry.unocmd);
			}
			if (entry.afterSelect === 'colorPicker') {
				this.presentSub(
					new WriterColorPickerDialog(
						'纯色',
						this.slideMasterSolidColorRgb,
						(rgb) => {
							this.slideMasterSolidColorRgb = rgb;
							this.applyImpressMasterSolidColor(rgb);
							this.pickerLabels['slide-master'] = '纯色';
						},
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

	/** Android ImpressTransitionApplier: Sidebar iconview select+activate. */
	private applyTransition(iconViewIndex: number): void {
		const socket = (window as any).app?.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return;
		}
		socket.sendMessage('uno .uno:SidebarShow');
		socket.sendMessage('uno .uno:SlideChangeWindow');
		const map = (window as any).app?.map;
		if (map && map.sidebar && typeof map.sidebar.setupTargetDeck === 'function') {
			try {
				map.sidebar.setupTargetDeck('.uno:SlideChangeWindow');
			} catch (_err) {
				// Deck may be unavailable on first open; dialogevent still reaches Core.
			}
		}
		let windowId = -1;
		if ((window as any).sidebarId !== undefined && (window as any).sidebarId !== null) {
			windowId = (window as any).sidebarId;
		}
		const send = (cmd: string, data: string) => {
			socket.sendMessage(
				'dialogevent ' +
					windowId +
					' {"id":"transitions_icons", "cmd": "' +
					cmd +
					'", "data": "' +
					data +
					'", "type": "iconview"}',
			);
		};
		send('select', String(iconViewIndex));
		send('activate', String(iconViewIndex));
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

	private openExportDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: 'ODF 演示文稿 (.odp)', value: 'odp' },
			{ label: 'PowerPoint (.pptx)', value: 'pptx' },
		];
		this.presentSub(
			new WriterEditorChooseDialog(
				'导出为',
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
