/*
 * AI typeset dialog — type selection, full-text extraction, HTML preview, insert.
 *
 * Phase A (V1): HTML output from cloud LLM, pasted at document end.
 */

class MobileAiTypesetDialog {
	private readonly controller: WriterAiController;
	private readonly sheet: MobileAiSheet;
	private readonly content: HTMLDivElement;
	private readonly selectPanel: HTMLDivElement;
	private readonly previewPanel: HTMLDivElement;
	private readonly status: HTMLDivElement;
	private readonly preview: HTMLDivElement;
	private readonly startButton: HTMLButtonElement;
	private readonly stopButton: HTMLButtonElement;
	private readonly copyButton: HTMLButtonElement;
	private readonly regenerateButton: HTMLButtonElement;
	private readonly applyButton: HTMLButtonElement;
	private readonly typeCards: HTMLButtonElement[] = [];
	private selectedType = 'paper';
	private extracting = false;
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = WriterAiController.getInstance();
		this.sheet = new MobileAiSheet({ title: 'AI 排版' });
		this.content = document.createElement('div');
		this.content.style.cssText =
			'display:flex;flex-direction:column;gap:12px;min-height:280px;';

		this.selectPanel = document.createElement('div');
		this.selectPanel.style.cssText =
			'display:flex;flex-direction:column;gap:12px;';
		const intro = document.createElement('p');
		intro.textContent = '选择排版类型，AI 将按模板格式化当前文档全文。';
		intro.style.cssText = 'margin:0;color:#5f6368;font-size:14px;line-height:1.5;';
		this.selectPanel.appendChild(intro);

		const grid = document.createElement('div');
		grid.style.cssText =
			'display:grid;grid-template-columns:1fr 1fr;gap:10px;';
		WriterAiCatalog.TYPESET_TYPES.forEach((item) => {
			const card = document.createElement('button');
			card.type = 'button';
			card.textContent = item.label;
			card.setAttribute('aria-pressed', item.key === this.selectedType ? 'true' : 'false');
			card.style.cssText =
				'min-height:56px;padding:12px;border-radius:12px;border:2px solid #d8dde3;' +
				'background:#fff;font-size:15px;font-weight:600;color:#303134;';
			card.onclick = () => this.selectType(item.key);
			this.typeCards.push(card);
			grid.appendChild(card);
		});
		this.selectPanel.appendChild(grid);
		this.startButton = this.createButton('开始排版');
		this.startButton.onclick = () => this.startTypeset();
		this.selectPanel.appendChild(this.startButton);
		this.content.appendChild(this.selectPanel);

		this.previewPanel = document.createElement('div');
		this.previewPanel.style.cssText =
			'display:none;flex-direction:column;gap:12px;';
		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		this.previewPanel.appendChild(this.status);
		this.preview = document.createElement('div');
		this.preview.setAttribute('aria-live', 'polite');
		this.preview.style.cssText =
			'min-height:180px;max-height:42dvh;overflow:auto;padding:16px;' +
			'border:1px solid #d8dde3;border-radius:8px;line-height:1.6;background:#fafafa;';
		this.previewPanel.appendChild(this.preview);

		const actions = document.createElement('div');
		actions.style.cssText = 'display:flex;flex-wrap:wrap;gap:8px;';
		this.stopButton = this.createButton('停止生成');
		this.stopButton.onclick = () => this.controller.cancel();
		actions.appendChild(this.stopButton);
		this.copyButton = this.createButton('复制');
		this.copyButton.onclick = () => this.controller.copy();
		actions.appendChild(this.copyButton);
		this.regenerateButton = this.createButton('重新生成');
		this.regenerateButton.onclick = () => this.regenerate();
		actions.appendChild(this.regenerateButton);
		this.applyButton = this.createButton('插入文档');
		this.applyButton.onclick = () => this.applyResult();
		actions.appendChild(this.applyButton);
		this.previewPanel.appendChild(actions);
		this.content.appendChild(this.previewPanel);

		this.sheet.setBody(this.content);
		this.unsubscribe = this.controller.subscribe(() => this.render());
		this.updateTypeSelection();
	}

	open(): void {
		this.sheet.open();
		this.showSelectPanel();
		this.render();
	}

	close(): void {
		this.controller.cancel();
		this.unsubscribe();
		this.sheet.close();
	}

	private selectType(typeKey: string): void {
		this.selectedType = typeKey;
		this.updateTypeSelection();
	}

	private updateTypeSelection(): void {
		WriterAiCatalog.TYPESET_TYPES.forEach((item, index) => {
			const card = this.typeCards[index];
			if (!card) {
				return;
			}
			const selected = item.key === this.selectedType;
			card.setAttribute('aria-pressed', selected ? 'true' : 'false');
			card.style.borderColor = selected ? '#1a73e8' : '#d8dde3';
			card.style.background = selected ? '#e8f0fe' : '#fff';
			card.style.color = selected ? '#1a73e8' : '#303134';
		});
	}

	private showSelectPanel(): void {
		this.selectPanel.style.display = 'flex';
		this.previewPanel.style.display = 'none';
	}

	private showPreviewPanel(): void {
		this.selectPanel.style.display = 'none';
		this.previewPanel.style.display = 'flex';
	}

	private startTypeset(): void {
		if (this.extracting) {
			return;
		}
		this.extracting = true;
		this.status.textContent = '正在提取文档全文...';
		this.showPreviewPanel();
		this.startButton.disabled = true;
		MobileAiDocumentExtractor.extractFullText()
			.then((fullText) => {
				this.extracting = false;
				this.controller.request('typeset', { typesetType: this.selectedType }, fullText);
				this.render();
			})
			.catch((error: Error) => {
				this.extracting = false;
				this.status.textContent =
					error && error.message ? error.message : '文档全文提取失败';
				this.startButton.disabled = false;
				this.showSelectPanel();
			});
	}

	private regenerate(): void {
		this.controller.regenerate();
	}

	private applyResult(): void {
		const state = this.controller.getState();
		const html = MobileAiResultRenderer.sanitizeTypesetHtml(state.preview);
		this.controller.accept(html);
	}

	private render(): void {
		const state = this.controller.getState();
		if (this.previewPanel.style.display !== 'none') {
			MobileAiResultRenderer.renderTypesetInto(this.preview, state.preview);
		}
		const active =
			this.extracting ||
			state.state === 'loading' ||
			state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		this.startButton.disabled = active;
		this.stopButton.disabled = !active || this.extracting;
		this.copyButton.disabled = !ready;
		this.regenerateButton.disabled = !ready || active;
		this.applyButton.disabled = !ready || active;
		if (this.extracting) {
			this.status.textContent = '正在提取文档全文...';
		} else if (state.error) {
			this.status.textContent = state.error;
		} else if (active) {
			this.status.textContent =
				state.state === 'streaming' ? 'AI 正在排版...' : 'AI 正在生成...';
		} else if (ready) {
			this.status.textContent = '排版完成，可预览后插入文档';
		} else {
			this.status.textContent = '';
		}
	}

	private createButton(label: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		return button;
	}
}
