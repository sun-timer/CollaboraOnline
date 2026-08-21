/*
 * Android-style grouped AI operation sheet.
 */

type MobileAiOperationSelectHandler = (entry: MobileAiUiEntry) => void;

class MobileAiOperationSheet {
	private readonly sheet: MobileAiSheet;
	private readonly onSelect: MobileAiOperationSelectHandler;
	private readonly grid: HTMLDivElement;
	private readonly hint: HTMLDivElement;

	constructor(onSelect: MobileAiOperationSelectHandler) {
		this.onSelect = onSelect;
		this.sheet = new MobileAiSheet({ title: 'AI 功能' });
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

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

	open(): void {
		this.render();
		this.sheet.open();
	}

	private render(): void {
		const documentType = this.getDocumentType();
		const entries = MobileAiUiCatalog.getEntries(documentType);
		const selection = MobileAiBridge.getInstance().getSelectedText().trim();
		const viewportWidth = document.documentElement.clientWidth;
		this.grid.style.gridTemplateColumns =
			viewportWidth < 420
				? 'repeat(2,minmax(0,1fr))'
				: 'repeat(3,minmax(0,1fr))';
		this.hint.textContent = selection
			? `已选中 ${selection.length} 字`
			: '请先在文档中选择文本';
		this.hint.style.color = selection ? '#188038' : '#5f6368';
		this.grid.replaceChildren();

		let currentGroup = '';
		entries.forEach((entry) => {
			if (entry.group !== currentGroup) {
				currentGroup = entry.group;
				const title = document.createElement('h3');
				title.textContent = this.groupLabel(entry.group);
				title.style.cssText = 'grid-column:1/-1;margin:8px 0 0;font-size:18px;';
				this.grid.appendChild(title);
			}
			const button = document.createElement('button');
			button.type = 'button';
			button.textContent = entry.label;
			button.setAttribute('aria-label', entry.label);
			button.style.cssText =
				'min-height:72px;padding:8px;border:1px solid #d8dde3;border-radius:10px;' +
				'background:#fff;font:inherit;';
			const needsSelection = entry.selectionRequired && !selection;
			button.disabled = !entry.iosSupport || needsSelection;
			if (!entry.iosSupport) {
				button.title = 'iOS 尚未支持';
			} else if (needsSelection) {
				button.title = '请先选择文本';
			}
			button.onclick = () => this.onSelect(entry);
			this.grid.appendChild(button);
		});
	}

	private getDocumentType(): MobileAiDocumentType {
		const docType = (window as any).app?.map?.getDocType?.();
		if (docType === 'spreadsheet' || docType === 'presentation') {
			return docType;
		}
		return 'text';
	}

	private groupLabel(group: MobileAiUiGroup): string {
		const labels: { [key in MobileAiUiGroup]: string } = {
			assistant: 'AI 助手',
			writerGeneration: '文案生成',
			writerProcessing: '文案处理',
			other: '其他',
			calc: 'Calc AI',
			impress: 'Impress AI',
		};
		return labels[group];
	}
}
