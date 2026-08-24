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
	private readonly sheet: MobileAiSheet;
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
	];

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.sheet = new MobileAiSheet({ title: '功能' });

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
			button.style.cssText =
				'flex:1;padding:8px 4px;background:none;border:none;border-bottom:2px solid ' +
				(tab.id === this.activeTab ? '#1a73e8' : 'transparent') +
				';color:' + (tab.id === this.activeTab ? '#1a73e8' : '#5f6368') +
				';font:inherit;font-size:15px;cursor:pointer;';
			button.onclick = () => {
				this.activeTab = tab.id;
				this.renderTabs();
				this.renderGrid();
			};
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
			button.textContent = feature.label;
			button.setAttribute('aria-label', feature.label);
			button.style.cssText =
				'min-height:72px;padding:8px;border:1px solid #d8dde3;border-radius:10px;' +
				'background:#fff;font:inherit;';

			const isDialog = feature.kind === 'dialog';
			const dialogReady =
				isDialog &&
				WriterEditorPanel.SUPPORTED_DIALOGS.indexOf(feature.dialog || '') >= 0;
			const gated = feature.kind === 'findReplace' || (isDialog && !dialogReady);
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
			}
			return;
		}
		if (feature.kind === 'findReplace') {
			return; // gated; the find/replace dialog opens in a later phase
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
		const values = this.controller.getCommandValues('.uno:CharFontName');
		const names = values ? Object.keys(values).filter((name) => !!name) : [];
		if (!names.length) {
			return;
		}
		const options = names.map((name) => ({ label: name, value: name }));
		this.sheet.close();
		new WriterEditorChooseDialog('字体', options, (option) => {
			this.controller.applyFontName(option.value);
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

	private openTableDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: '2 × 2', value: '2:2' },
			{ label: '3 × 3', value: '3:3' },
			{ label: '4 × 3', value: '4:3' },
			{ label: '4 × 5', value: '4:5' },
		];
		this.sheet.close();
		new WriterEditorChooseDialog('插入表格', options, (option) => {
			const parts = option.value.split(':');
			const columns = parseInt(parts[0], 10);
			const rows = parseInt(parts[1], 10);
			if (!isNaN(columns) && !isNaN(rows)) {
				this.controller.insertTable(columns, rows);
			}
		}).open();
	}

	private openMarginsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: '常规', value: '2540:2540:2540:2540' },
			{ label: '较窄', value: '1270:1270:1270:1270' },
			{ label: '较宽', value: '4191:4191:2540:2540' },
		];
		this.sheet.close();
		new WriterEditorChooseDialog('页边距', options, (option) => {
			const parts = option.value.split(':').map((part) => parseInt(part, 10));
			if (parts.length === 4 && parts.every((part) => !isNaN(part))) {
				this.controller.applyMargins(parts[0], parts[1], parts[2], parts[3]);
			}
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
	const mount = () => {
		WriterEditorPanel.mount();
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		mount();
	}
}
