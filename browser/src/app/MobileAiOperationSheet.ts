/*
 * Android-style grouped AI operation sheet (lolib_sheet_ai_operations.xml).
 */

type MobileAiOperationSelectHandler = (entry: MobileAiUiEntry) => void;

const WRITER_AI_ROWS: Array<Array<string | null>> = [
	['continue', 'outline', 'article_generate'],
	['polish', 'expand', 'condense'],
	['rewrite', 'translate', null],
	['text_extract', 'typeset', 'image_generate'],
	['format_batch', null, null],
];

const CALC_AI_ROWS: Array<Array<string | null>> = [
	['calc_formula', 'calc_cond_format', 'calc_data_analysis'],
	['calc_data_process', 'calc_chart', null],
];

const IMPRESS_AI_ROWS: Array<Array<string | null>> = [
	['impress_generate', 'continue', 'article_generate'],
	['expand', 'polish', 'condense'],
	['rewrite', 'text_extract', null],
	['image_generate', 'format_batch', null],
];

class MobileAiOperationSheet {
	private readonly sheet: MobileAiSheet;
	private readonly onSelect: MobileAiOperationSelectHandler;
	private readonly sections: HTMLDivElement;
	private readonly hint: HTMLDivElement;

	static resolveDocumentType(
		nativeDocumentType?: string,
		mapDocumentType?: string,
	): MobileAiDocumentType {
		if (
			nativeDocumentType === 'spreadsheet' ||
			nativeDocumentType === 'presentation' ||
			nativeDocumentType === 'text'
		) {
			return nativeDocumentType;
		}
		if (mapDocumentType === 'spreadsheet' || mapDocumentType === 'presentation') {
			return mapDocumentType;
		}
		return 'text';
	}

	constructor(onSelect: MobileAiOperationSelectHandler) {
		this.onSelect = onSelect;
		this.sheet = new MobileAiSheet({ title: 'AI功能' });
		const content = document.createElement('div');
		content.className = 'mobile-ai-op-root';

		this.hint = document.createElement('div');
		this.hint.className = 'mobile-ai-op-hint';
		content.appendChild(this.hint);

		this.sections = document.createElement('div');
		this.sections.className = 'mobile-ai-op-sections';
		content.appendChild(this.sections);
		this.sheet.setBody(content);
	}

	open(): void {
		this.render();
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}

	private render(): void {
		const documentType = this.getDocumentType();
		const map = (window as any).app?.map;
		const isReadOnly =
			typeof map?.isReadOnlyMode === 'function' && !!map.isReadOnlyMode();
		const entries = MobileAiUiCatalog.getOperationEntries(
			documentType,
			!isReadOnly,
		);
		const byType: { [taskType: string]: MobileAiUiEntry } = {};
		entries.forEach((entry) => {
			byType[entry.taskType] = entry;
		});

		const selection = MobileAiBridge.getInstance().getSelectedText().trim();
		this.updateHint(documentType, selection);

		this.sections.replaceChildren();

		if (documentType === 'spreadsheet') {
			this.renderCalcBlock(byType, selection);
			return;
		}
		if (documentType === 'presentation') {
			this.renderImpressBlock(byType, selection);
			return;
		}
		this.renderWriterBlock(byType, selection);
	}

	private renderWriterBlock(
		byType: { [taskType: string]: MobileAiUiEntry },
		selection: string,
	): void {
		this.appendSectionTitle('文案生成');
		this.appendRow(byType, WRITER_AI_ROWS[0], selection);
		this.appendSectionTitle('文案处理');
		this.appendRow(byType, WRITER_AI_ROWS[1], selection);
		this.appendRow(byType, WRITER_AI_ROWS[2], selection);
		this.appendSectionTitle('其他');
		this.appendRow(byType, WRITER_AI_ROWS[3], selection);
		this.appendRow(byType, WRITER_AI_ROWS[4], selection);
	}

	private renderCalcBlock(
		byType: { [taskType: string]: MobileAiUiEntry },
		selection: string,
	): void {
		this.appendRow(byType, CALC_AI_ROWS[0], selection);
		this.appendRow(byType, CALC_AI_ROWS[1], selection);
	}

	private renderImpressBlock(
		byType: { [taskType: string]: MobileAiUiEntry },
		selection: string,
	): void {
		IMPRESS_AI_ROWS.forEach((row) => {
			this.appendRow(byType, row, selection);
		});
	}

	private appendSectionTitle(text: string): void {
		const title = document.createElement('h3');
		title.className = 'mobile-ai-op-section-title';
		title.textContent = text;
		this.sections.appendChild(title);
	}

	private appendRow(
		byType: { [taskType: string]: MobileAiUiEntry },
		taskTypes: Array<string | null>,
		selection: string,
	): void {
		const row = document.createElement('div');
		row.className = 'mobile-ai-op-row';
		taskTypes.forEach((taskType) => {
			if (!taskType) {
				row.appendChild(this.spacerCell());
				return;
			}
			const entry = byType[taskType];
			if (!entry) {
				row.appendChild(this.spacerCell());
				return;
			}
			row.appendChild(this.createCard(entry, selection));
		});
		this.sections.appendChild(row);
	}

	private spacerCell(): HTMLDivElement {
		const spacer = document.createElement('div');
		spacer.className = 'mobile-ai-op-spacer';
		spacer.setAttribute('aria-hidden', 'true');
		return spacer;
	}

	private createCard(
		entry: MobileAiUiEntry,
		selection: string,
	): HTMLButtonElement {
		const label = this.sheetLabel(entry);
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'mobile-ai-operation-card';
		button.setAttribute('aria-label', label);
		const icon = document.createElement('span');
		icon.className = 'mobile-ai-operation-card__icon';
		icon.innerHTML = MobileAiOperationIcons.svgFor(entry.taskType);
		button.appendChild(icon);
		const text = document.createElement('span');
		text.className = 'mobile-ai-operation-card__label';
		text.textContent = label;
		button.appendChild(text);
		if (!entry.iosSupport) {
			button.disabled = true;
			button.title = 'iOS 尚未支持';
		}
		button.onclick = () => {
			if (!entry.iosSupport) {
				return;
			}
			if (entry.selectionRequired && !selection) {
				this.hint.textContent = '请先在文档中选择文本';
				this.hint.style.color = '#5f6368';
				return;
			}
			this.onSelect(entry);
		};
		return button;
	}

	private updateHint(documentType: MobileAiDocumentType, selection: string): void {
		if (documentType === 'spreadsheet') {
			this.hint.style.display = 'none';
			return;
		}
		if (documentType === 'presentation') {
			this.hint.style.display = 'none';
			return;
		}
		this.hint.style.display = '';
		this.hint.textContent = selection
			? `已选中 ${selection.length} 字`
			: '请先在文档中选择文本';
		this.hint.style.color = selection ? '#188038' : '#5f6368';
	}

	private sheetLabel(entry: MobileAiUiEntry): string {
		const documentType = this.getDocumentType();
		if (documentType === 'presentation') {
			if (entry.taskType === 'continue') {
				return '续写';
			}
			if (entry.taskType === 'text_extract') {
				return '文案提取';
			}
			if (entry.taskType === 'image_generate') {
				return 'AI图片';
			}
		}
		if (entry.taskType === 'continue') {
			return 'AI续写';
		}
		if (entry.taskType === 'typeset') {
			return 'AI排版';
		}
		return entry.label.replace(/ /g, '');
	}

	private getDocumentType(): MobileAiDocumentType {
		const nativeDocumentType = (window as any).MobileNativeDocumentType;
		const docType = (window as any).app?.map?.getDocType?.();
		return MobileAiOperationSheet.resolveDocumentType(
			nativeDocumentType,
			docType,
		);
	}
}
