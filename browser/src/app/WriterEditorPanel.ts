/*
 * iOS Browser Writer document-editing function panel.
 *
 * A thin DOM facade over WriterEditorCatalog + WriterEditorController,
 * mirroring WriterAiPanel. It renders the five editor tabs (常用/文件/插入/
 * 布局/审阅) as a grouped grid and dispatches features through the
 * controller. Dialog-kind features are gated off until their native/web
 * dialog lands (same pattern as b0d7fb's iosSupport gate).
 */

class WriterEditorPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly tabBar: HTMLDivElement;
	private readonly hint: HTMLDivElement;
	private readonly grid: HTMLDivElement;
	private readonly controller: WriterEditorController;
	private activeTab: WriterEditorTab = 'default';
	/** Toggle inputs in the review tab, keyed by .uno command. */
	private readonly reviewToggleInputs: { [command: string]: HTMLInputElement } = {};
	private onReviewStateBound: ((event: any) => void) | null = null;
	/** Picker/dialog sheets opened from this panel, closed together with it. */
	private readonly subDialogs: { close(): void }[] = [];
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

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.sheet = new WriterEditorSheet('功能', () => this.unsubscribeReviewState());

		const content = document.createElement('div');
		content.className = 'writer-function-panel';

		this.tabBar = document.createElement('div');
		this.tabBar.className = 'writer-function-tab-bar';
		content.appendChild(this.tabBar);

		this.hint = document.createElement('div');
		this.hint.className = 'writer-function-hint';
		content.appendChild(this.hint);

		this.grid = document.createElement('div');
		this.grid.className = 'writer-function-grid';
		content.appendChild(this.grid);

		this.sheet.setBody(content);
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
		this.renderTabs();
		this.renderGrid();
		this.subscribeReviewState();
		this.sheet.open();
	}

	/** Closes the panel and any picker/dialog sheets opened from it. */
	close(): void {
		this.unsubscribeReviewState();
		const children = this.subDialogs.splice(0);
		children.forEach((dialog) => dialog.close());
		this.sheet.close();
	}

	private presentSub(dialog: { open(): void; close(): void }): void {
		if (this.sheet.root.parentElement) {
			this.sheet.close();
		}
		this.subDialogs.push(dialog);
		dialog.open();
	}
	private renderTabs(): void {
		this.tabBar.replaceChildren();

		const track = document.createElement('div');
		track.className = 'writer-function-tab-track';
		const scroll = document.createElement('div');
		scroll.className = 'writer-function-tab-scroll';
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
				this.renderGrid();
			};
			scroll.appendChild(button);
		});
		track.appendChild(scroll);
		this.tabBar.appendChild(track);

		const divider = document.createElement('div');
		divider.className = 'writer-function-tab-divider';
		divider.setAttribute('aria-hidden', 'true');
		this.tabBar.appendChild(divider);

		const actions = document.createElement('div');
		actions.className = 'writer-function-tab-actions';
		const actionButtons: Array<{ icon: string; aria: string; handler: () => void }> = [
			{ icon: 'ai-sparkle', aria: 'AI功能', handler: () => this.openAiFeatures() },
			{ icon: 'keyboard', aria: '呼出键盘', handler: () => this.showKeyboard() },
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
			actions.appendChild(button);
		});
		this.tabBar.appendChild(actions);
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

	private renderGrid(): void {
		const selection = this.controller.getSelectedText().trim();
		const viewportWidth = document.documentElement.clientWidth;
		const isFileTab = this.activeTab === 'file';
		const isInsertTab = this.activeTab === 'insert';
		this.grid.className =
			'writer-function-grid' +
			(isInsertTab ? ' writer-function-grid--insert' : '') +
			(!isInsertTab && viewportWidth < 420 ? ' writer-function-grid--narrow' : '');
		this.hint.textContent = isFileTab
			? ''
			: selection
				? `已选中 ${selection.length} 字`
				: '在文档中选中文字后可编辑';
		this.hint.className =
			'writer-function-hint' +
			(isFileTab ? ' writer-function-hint--hidden' : '') +
			(selection ? ' writer-function-hint--selection' : '');
		this.grid.replaceChildren();
		Object.keys(this.reviewToggleInputs).forEach((command) => {
			delete this.reviewToggleInputs[command];
		});

		if (isFileTab) {
			this.renderFileList();
			return;
		}

		const features = WriterEditorCatalog.getFeatures(this.activeTab);
		let currentGroup = '';
		features.forEach((feature) => {
			if (feature.group !== currentGroup) {
				currentGroup = feature.group || '';
				const title = document.createElement('h3');
				title.textContent = this.groupLabel(feature.group || '');
				title.className = 'writer-function-grid__group-title';
				this.grid.appendChild(title);
			}
			if (feature.kind === 'toggle') {
				this.grid.appendChild(this.createToggleRow(feature));
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

			const isDialog = feature.kind === 'dialog';
			const dialogReady =
				isDialog &&
				!!feature.dialog && WriterEditorPanel.SUPPORTED_DIALOGS.indexOf(feature.dialog) >= 0;
			const gated = isDialog && !dialogReady;
			const needsSelection = !!feature.needsSelection && !selection;
			button.disabled = gated || needsSelection;
			if (gated) {
				button.title = '即将支持';
			} else if (needsSelection) {
				button.title = '请先选择文字';
			}
			button.onclick = () => this.onFeature(feature);
			this.grid.appendChild(button);
		});
		this.refreshReviewToggles();
	}

	private renderFileList(): void {
		this.grid.className = 'writer-function-list';
		const features = WriterEditorCatalog.getFeatures('file');
		features.forEach((feature, index) => {
			const row = document.createElement('button');
			row.type = 'button';
			row.className = 'writer-function-list-row';
			row.setAttribute('aria-label', feature.label);

			const iconWrap = document.createElement('span');
			iconWrap.className = 'writer-function-list-row__icon';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				iconWrap.innerHTML = icon;
			}
			row.appendChild(iconWrap);

			const label = document.createElement('span');
			label.className = 'writer-function-list-row__label';
			label.textContent = feature.label;
			row.appendChild(label);

			const isDialog = feature.kind === 'dialog';
			const dialogReady =
				isDialog &&
				!!feature.dialog &&
				WriterEditorPanel.SUPPORTED_DIALOGS.indexOf(feature.dialog) >= 0;
			const gated = isDialog && !dialogReady;
			row.disabled = gated;
			if (gated) {
				row.title = '即将支持';
			}
			row.onclick = () => this.onFeature(feature);
			this.grid.appendChild(row);

			if (index + 1 < features.length) {
				const divider = document.createElement('div');
				divider.className = 'writer-function-list-divider';
				divider.setAttribute('aria-hidden', 'true');
				this.grid.appendChild(divider);
			}
		});
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
				(candidate) => candidate.unocmd === command && candidate.kind === 'toggle',
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
				this.reviewToggleInputs[event.commandName].checked = event.state === 'true';
			}
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
		const options: WriterChooseOption[] = this.getFontOptions()
			.map((name) => ({ label: name, value: name }));
		this.presentSub(new WriterEditorChooseDialog('字体', options, (option) => {
			this.controller.applyFontName(WriterEditorCatalog.aliasFont(option.value));
		}));
	}

	private openFontSizeDialog(): void {
		const table = WriterEditorCatalog.CHAR_HEIGHT_CN;
		const options = Object.keys(table).map((label) => ({ label, value: table[label] }));
		this.presentSub(new WriterEditorChooseDialog('字号', options, (option) => {
			this.controller.applyFontSize(option.value);
		}));
	}

	private openFindReplaceDialog(): void {
		this.presentSub(new WriterFindReplaceDialog(this.controller));
	}

	private openTableDialog(): void {
		this.presentSub(new WriterEditorInsertTableDialog(this.controller));
	}

	private openMarginsDialog(): void {
		const options: WriterChooseOption[] = WriterEditorCatalog.MARGIN_PRESETS.map((preset) => ({
			label: preset.label,
			value: [preset.left, preset.right, preset.top, preset.bottom].join(':'),
		}));
		this.presentSub(new WriterEditorChooseDialog('页边距', options, (option) => {
			const parts = option.value.split(':').map((part) => parseInt(part, 10));
			if (parts.length === 4 && parts.every((part) => !isNaN(part))) {
				this.controller.applyMargins(parts[0], parts[1], parts[2], parts[3]);
			}
		}));
	}

	private openShapeDialog(): void {
		this.presentSub(new WriterEditorShapeDialog(this.controller));
	}

	private openChartDialog(): void {
		this.presentSub(new WriterEditorChartDialog(this.controller));
	}

	private openStyleDialog(): void {
		const values = this.controller.getCommandValues('.uno:StyleApply');
		const styles = values && Array.isArray(values.ParagraphStyles)
			? (values.ParagraphStyles as string[])
			: [];
		if (!styles.length) {
			return;
		}
		const options: WriterChooseOption[] =
			WriterEditorCatalog.reorderStyleOptions(styles);
		this.presentSub(new WriterEditorChooseDialog('样式', options, (option) => {
			this.controller.applyStyle(option.value);
		}));
	}

	private openWatermarkDialog(): void {
		const fontOptions = this.getFontOptions()
			.map((name) => WriterEditorCatalog.aliasFont(name));
		this.presentSub(new WriterEditorWatermarkDialog(this.controller, fontOptions));
	}

	private getFontOptions(): string[] {
		const values = this.controller.getCommandValues('.uno:CharFontName');
		const names = values ? Object.keys(values).filter((name) => !!name) : [];
		return names.length ? names : WriterEditorCatalog.FONT_FALLBACK_OPTIONS;
	}

	private openPaperSizeDialog(): void {
		const options: WriterChooseOption[] =
			WriterEditorCatalog.PAPER_FORMATS.map((preset) => ({
				label: preset.label,
				value: preset.value,
			}));
		options.push({ label: '自定义尺寸…', value: 'custom' });
		this.presentSub(new WriterEditorChooseDialog('纸张大小', options, (option) => {
			if (option.value === 'custom') {
				this.presentSub(new WriterEditorPaperSizeDialog(this.controller));
				return;
			}
			this.controller.applyPaperFormat(option.value);
		}));
	}

	private openImageDialog(): void {
		this.presentSub(new WriterEditorImageDialog(this.controller));
	}

	private openCommentDialog(): void {
		this.presentSub(new WriterEditorCommentDialog(this.controller));
	}

	private openSaveAsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'ODF 文本文档 (.odt)', value: 'odt' },
			{ label: 'Word 文档 (.docx)', value: 'docx' },
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: '纯文本 (.txt)', value: 'txt' },
		];
		this.presentSub(new WriterEditorChooseDialog('另存为', options, (option) => {
			this.controller.saveAs(option.value);
		}));
	}

	private openExportFormatDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: 'ODF 文本文档 (.odt)', value: 'odt' },
			{ label: 'Word 文档 (.docx)', value: 'docx' },
		];
		this.presentSub(new WriterEditorChooseDialog('导出为', options, (option) => {
			this.controller.exportAs(option.value);
		}));
	}

	private groupLabel(group: string): string {
		const labels: { [key: string]: string } = {
			history: '历史',
			format: '格式',
			paragraph: '段落',
			file: '文件',
			insert: '插入',
			page: '页面',
			review: '审阅',
		};
		return labels[group] || group;
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
