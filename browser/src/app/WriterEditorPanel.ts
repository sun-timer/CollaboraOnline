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
		'trackChanges',
		'chart',
	];

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.sheet = new WriterEditorSheet('功能');

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

		this.tabBar = document.createElement('div');
		this.tabBar.style.cssText =
			'display:flex;gap:4px;border-bottom:1px solid #d8dde3;padding-bottom:8px;';
		content.appendChild(this.tabBar);

		this.hint = document.createElement('div');
		this.hint.style.cssText =
			'padding:10px;border-radius:8px;text-align:center;background:#e6ebf2;' +
			'color:#5f6368;font-size:14px;';
		content.appendChild(this.hint);

		this.grid = document.createElement('div');
		this.grid.style.cssText =
			'display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;';
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
		this.sheet.open();
	}

	private renderTabs(): void {
		this.tabBar.replaceChildren();
		WriterEditorCatalog.TABS.forEach((tab) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.textContent = tab.label;
			button.setAttribute('aria-label', tab.label);
			const active = tab.id === this.activeTab;
			button.style.cssText =
				'flex:1;padding:10px 4px;background:none;border:none;border-bottom:4px solid ' +
				(active ? '#1278D9' : 'transparent') +
				';color:' + (active ? '#1278D9' : '#5f6368') +
				';font:inherit;font-size:15px;cursor:pointer;';
			button.onclick = () => {
				this.activeTab = tab.id;
				this.renderTabs();
				this.renderGrid();
			};
			this.tabBar.appendChild(button);
		});

		const divider = document.createElement('div');
		divider.style.cssText = 'width:1px;align-self:flex-end;height:28px;background:#d8dde3;';
		this.tabBar.appendChild(divider);

		const actionButtons: Array<{ icon: string; aria: string; handler: () => void }> = [
			{ icon: 'find-replace', aria: '查找', handler: () => this.openFindReplaceDialog() },
			{ icon: 'arrow-down', aria: '收起', handler: () => this.sheet.close() },
		];
		actionButtons.forEach((action) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.setAttribute('aria-label', action.aria);
			button.style.cssText =
				'width:44px;height:44px;display:flex;align-items:center;justify-content:center;' +
				'background:none;border:none;cursor:pointer;flex-shrink:0;';
			const icon = WriterEditorIcons.get(action.icon);
			if (icon) {
				button.innerHTML = icon;
			}
			button.onclick = action.handler;
			this.tabBar.appendChild(button);
		});
	}

	private renderGrid(): void {
		const selection = this.controller.getSelectedText().trim();
		const viewportWidth = document.documentElement.clientWidth;
		this.grid.style.gridTemplateColumns =
			viewportWidth < 420
				? 'repeat(2,minmax(0,1fr))'
				: 'repeat(3,minmax(0,1fr))';
		this.hint.textContent = selection
			? `已选中 ${selection.length} 字`
			: '在文档中选中文字后可编辑';
		this.hint.style.color = selection ? '#188038' : '#5f6368';
		this.grid.replaceChildren();

		const features = WriterEditorCatalog.getFeatures(this.activeTab);
		let currentGroup = '';
		features.forEach((feature) => {
			if (feature.group !== currentGroup) {
				currentGroup = feature.group || '';
				const title = document.createElement('h3');
				title.textContent = this.groupLabel(feature.group || '');
				title.style.cssText = 'grid-column:1/-1;margin:8px 0 0;font-size:18px;';
				this.grid.appendChild(title);
			}
			const button = document.createElement('button');
			button.type = 'button';
			button.setAttribute('aria-label', feature.label);
			button.style.cssText =
				'display:flex;flex-direction:column;align-items:center;justify-content:center;' +
				'gap:8px;min-height:96px;padding:12px 8px;border:none;border-radius:24px;' +
				'background:#f2f3f5;font:inherit;cursor:pointer;';
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				const iconWrap = document.createElement('span');
				iconWrap.style.cssText =
					'width:24px;height:24px;display:flex;align-items:center;justify-content:center;color:#1278D9;';
				iconWrap.innerHTML = icon;
				button.appendChild(iconWrap);
			}
			const tileLabel = document.createElement('span');
			tileLabel.textContent = feature.label;
			tileLabel.style.cssText = 'font-size:14px;color:#101010;text-align:center;';
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
			} else if (dialog === 'trackChanges') {
				this.openTrackChangesDialog();
			}
			return;
		}
		if (feature.kind === 'findReplace') {
			this.openFindReplaceDialog();
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
		this.sheet.close();
		new WriterEditorChooseDialog('字体', options, (option) => {
			this.controller.applyFontName(WriterEditorCatalog.aliasFont(option.value));
		}).open();
	}

	private openFontSizeDialog(): void {
		const table = WriterEditorCatalog.CHAR_HEIGHT_CN;
		const options = Object.keys(table).map((label) => ({ label, value: table[label] }));
		this.sheet.close();
		new WriterEditorChooseDialog('字号', options, (option) => {
			this.controller.applyFontSize(option.value);
		}).open();
	}

	private openFindReplaceDialog(): void {
		this.sheet.close();
		new WriterFindReplaceDialog(this.controller).open();
	}

	private openTableDialog(): void {
		this.sheet.close();
		new WriterEditorInsertTableDialog(this.controller).open();
	}

	private openMarginsDialog(): void {
		const options: WriterChooseOption[] = WriterEditorCatalog.MARGIN_PRESETS.map((preset) => ({
			label: preset.label,
			value: [preset.left, preset.right, preset.top, preset.bottom].join(':'),
		}));
		this.sheet.close();
		new WriterEditorChooseDialog('页边距', options, (option) => {
			const parts = option.value.split(':').map((part) => parseInt(part, 10));
			if (parts.length === 4 && parts.every((part) => !isNaN(part))) {
				this.controller.applyMargins(parts[0], parts[1], parts[2], parts[3]);
			}
		}).open();
	}

	private openShapeDialog(): void {
		this.sheet.close();
		new WriterEditorShapeDialog(this.controller).open();
	}

	private openChartDialog(): void {
		this.sheet.close();
		new WriterEditorChartDialog(this.controller).open();
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
		this.sheet.close();
		new WriterEditorChooseDialog('样式', options, (option) => {
			this.controller.applyStyle(option.value);
		}).open();
	}

	private openWatermarkDialog(): void {
		const fontOptions = this.getFontOptions()
			.map((name) => WriterEditorCatalog.aliasFont(name));
		this.sheet.close();
		new WriterEditorWatermarkDialog(this.controller, fontOptions).open();
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
		this.sheet.close();
		new WriterEditorChooseDialog('纸张大小', options, (option) => {
			if (option.value === 'custom') {
				new WriterEditorPaperSizeDialog(this.controller).open();
				return;
			}
			this.controller.applyPaperFormat(option.value);
		}).open();
	}

	private openTrackChangesDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: '开启追踪修订', value: 'on' },
			{ label: '关闭追踪修订', value: 'off' },
		];
		this.sheet.close();
		new WriterEditorChooseDialog('追踪修订', options, (option) => {
			this.controller.trackChanges(option.value === 'on');
		}).open();
	}

	private openImageDialog(): void {
		const input = document.createElement('input');
		input.type = 'file';
		input.accept = 'image/*';
		const onImageSelected = () => {
			const file = input.files && input.files[0];
			if (!file) {
				return;
			}
			const reader = new FileReader();
			reader.onload = () => {
				const bytes = new Uint8Array(reader.result as ArrayBuffer);
				let str = '';
				for (let i = 0; i < bytes.length; i++) {
					str += String.fromCharCode(bytes[i]);
				}
				this.controller.insertImage(file.name, window.btoa(str));
			};
			reader.readAsArrayBuffer(file);
		};
		input.onchange = onImageSelected;
		this.sheet.close();
		input.click();
	}

	private openSaveAsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'ODF 文本文档 (.odt)', value: 'odt' },
			{ label: 'Word 文档 (.docx)', value: 'docx' },
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: '纯文本 (.txt)', value: 'txt' },
		];
		this.sheet.close();
		new WriterEditorChooseDialog('另存为', options, (option) => {
			this.controller.saveAs(option.value);
		}).open();
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
