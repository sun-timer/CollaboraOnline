/*
 * Local format-batch sheet (no AI network).
 *
 * Reads the current text selection through the shared browser bridges,
 * applies the checked FormatBatchProcessor rules, and replaces the
 * selection via the LOKit paste path (same adapter as AI results).
 */

class MobileAiFormatBatchDialog {
	private readonly sheet: MobileAiSheet;
	private readonly rules: boolean[];
	private readonly applyButton: HTMLButtonElement;
	private readonly status: HTMLDivElement;

	constructor() {
		this.rules = new Array(FormatBatchProcessor.RULE_COUNT).fill(false);
		this.sheet = new MobileAiSheet({ title: '格式批量处理' });
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

		FormatBatchProcessor.RULE_LABELS.forEach((label, index) => {
			const row = document.createElement('label');
			row.style.cssText = 'display:flex;align-items:center;gap:8px;';
			const box = document.createElement('input');
			box.type = 'checkbox';
			box.setAttribute('aria-label', label);
			box.onchange = () => {
				this.rules[index] = box.checked;
				this.updateApplyEnabled();
			};
			const text = document.createElement('span');
			text.textContent = label;
			row.appendChild(box);
			row.appendChild(text);
			content.appendChild(row);
		});

		this.applyButton = document.createElement('button');
		this.applyButton.type = 'button';
		this.applyButton.textContent = '应用';
		this.applyButton.disabled = true;
		this.applyButton.onclick = () => this.apply();
		content.appendChild(this.applyButton);

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		content.appendChild(this.status);

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}

	private updateApplyEnabled(): void {
		this.applyButton.disabled = !this.rules.some((enabled) => enabled);
	}

	private apply(): void {
		const selection = this.getSelectedText();
		if (!selection) {
			this.status.textContent = '请先选择要处理的文字';
			return;
		}
		const processed = FormatBatchProcessor.process(selection, this.rules);
		try {
			const clip = (window as any).app?.map?._clip;
			if (!clip || typeof clip.pastePlainText !== 'function') {
				this.status.textContent = '无法写入文档';
				return;
			}
			clip.pastePlainText(processed);
			this.applyButton.textContent = '已应用';
			window.setTimeout(() => {
				this.applyButton.textContent = '应用';
			}, 1200);
		} catch (_error) {
			this.status.textContent = '应用失败';
		}
	}

	private getSelectedText(): string {
		try {
			const docLayer = (window as any).app?.map?._docLayer;
			if (
				docLayer &&
				typeof docLayer._selectedTextContent === 'string' &&
				docLayer._selectedTextContent
			) {
				return docLayer._selectedTextContent;
			}
			const clip = (window as any).app?.map?._clip;
			if (
				clip &&
				typeof clip._selectionPlainTextContent === 'string' &&
				clip._selectionPlainTextContent
			) {
				return clip._selectionPlainTextContent;
			}
		} catch (_error) {
			// Selection is best effort and must not break document editing.
		}
		return '';
	}
}

if (typeof window !== 'undefined') {
	(window as any).MobileAiFormatBatchDialog = MobileAiFormatBatchDialog;
}