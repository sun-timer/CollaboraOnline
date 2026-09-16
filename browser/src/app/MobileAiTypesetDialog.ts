/*
 * AI typeset dialog — V2 docx template flow with V1 HTML fallback.
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
	private filling = false;
	private inserting = false;
	private paragraphs: string[] = [];
	private paragraphMode = false;
	private sections: { [key: string]: string } | null = null;
	private typesetImages: { [marker: string]: TypesetImagePayload } | null = null;
	private docxPath = '';
	private previewHtml = '';
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = WriterAiController.getInstance();
		this.sheet = new MobileAiSheet({
			title: 'AI 排版',
			presentation: 'writer',
			taskType: 'typeset',
		});
		this.content = document.createElement('div');
		this.content.className = 'mobile-ai-task-dialog';

		this.selectPanel = document.createElement('div');
		this.selectPanel.style.cssText =
			'display:flex;flex-direction:column;gap:12px;';
		const intro = document.createElement('p');
		intro.textContent = '选择排版类型，AI 将按 docx 模板格式化当前文档全文。';
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
		this.startButton = this.createButton('开始排版', true);
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
		this.applyButton = this.createButton('覆盖文档', true);
		this.applyButton.onclick = () => this.applyResult();
		actions.appendChild(this.applyButton);
		this.previewPanel.appendChild(actions);
		this.content.appendChild(this.previewPanel);

		this.sheet.setBody(this.content);
		this.unsubscribe = this.controller.subscribe(() => this.onControllerState());
		this.updateTypeSelection();
	}

	open(): void {
		this.sheet.open();
		this.resetResultState();
		this.showSelectPanel();
		this.render();
	}

	close(): void {
		this.controller.cancel();
		this.unsubscribe();
		this.sheet.close();
	}

	private resetResultState(): void {
		this.sections = null;
		this.typesetImages = null;
		this.docxPath = '';
		this.previewHtml = '';
		this.paragraphs = [];
		this.paragraphMode = false;
		this.filling = false;
		this.inserting = false;
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
		this.resetResultState();
		this.extracting = true;
		this.status.textContent = '正在提取文档全文...';
		this.showPreviewPanel();
		this.startButton.disabled = true;
		const map = (window as any).app?.map;
		if (map && typeof map.sendUnoCommand === 'function') {
			map.sendUnoCommand('.uno:Save');
		}
		window.setTimeout(() => {
			this.extractAndRequest()
				.catch((error: Error) => {
					this.extracting = false;
					this.status.textContent =
						error && error.message ? error.message : '文档全文提取失败';
					this.startButton.disabled = false;
					this.showSelectPanel();
				})
				.finally(() => {
					this.extracting = false;
					this.render();
				});
		}, 500);
	}

	private extractAndRequest(): Promise<void> {
		const bridge = TypesetBridge.getInstance();
		if (bridge.isAvailable()) {
			return bridge.extractStructured().then((result) => {
				this.paragraphs = result.paragraphs || [];
				this.typesetImages = result.images || null;
				this.paragraphMode = this.paragraphs.length > 1;
				const fullText = result.fullText || this.paragraphs.join('\n\n');
				if (!fullText.trim()) {
					throw new Error('文档全文提取失败');
				}
				this.controller.request(
					'typeset',
					{
						typesetType: this.selectedType,
						typesetVersion: 'v2',
						paragraphMode: this.paragraphMode,
					},
					fullText,
				);
			});
		}
		return MobileAiDocumentExtractor.extractFullText().then((fullText) => {
			this.controller.request(
				'typeset',
				{
					typesetType: this.selectedType,
					typesetVersion: 'v2',
				},
				fullText,
			);
		});
	}

	private regenerate(): void {
		this.resetResultState();
		this.controller.regenerate();
	}

	private onControllerState(): void {
		const state = this.controller.getState();
		if (state.state === 'ready' && state.preview && !this.sections && !this.filling) {
			this.processAiResult(state.preview);
			return;
		}
		this.render();
	}

	private processAiResult(raw: string): void {
		let sections = TypesetSectionParser.parse(raw);
		if (!sections && this.paragraphMode && this.paragraphs.length > 0) {
			sections = TypesetSectionParser.parseParagraphClassifications(
				raw,
				this.paragraphs,
			);
		}
		if (sections && TypesetBridge.getInstance().isAvailable()) {
			this.sections = sections;
			this.previewHtml = TypesetPreviewHtml.build(this.selectedType, sections);
			this.preview.innerHTML = this.previewHtml;
			this.fillTemplate(sections);
			return;
		}
		this.previewHtml = '';
		this.sections = null;
		MobileAiResultRenderer.renderTypesetInto(this.preview, raw);
		this.render();
	}

	private fillTemplate(sections: { [key: string]: string }): void {
		if (!TypesetBridge.getInstance().isAvailable()) {
			this.render();
			return;
		}
		this.filling = true;
		this.status.textContent = '正在填充 docx 模板...';
		this.render();
		TypesetBridge.getInstance()
			.fillTemplate(
				this.selectedType,
				sections,
				'',
				this.typesetImages || undefined,
			)
			.then((result) => {
				this.docxPath = result.docxPath || '';
				this.filling = false;
				this.render();
			})
			.catch((error: Error) => {
				this.filling = false;
				this.docxPath = '';
				this.status.textContent =
					(error && error.message ? error.message : '模板填充失败') +
					'，将使用 HTML 方式插入';
				this.render();
			});
	}

	private applyResult(): void {
		if (this.docxPath && TypesetBridge.getInstance().isAvailable()) {
			this.inserting = true;
			this.status.textContent = '正在写入排版文档...';
			this.render();
			TypesetBridge.getInstance()
				.insertDocument(this.docxPath)
				.then((ok) => {
					this.inserting = false;
					if (!ok) {
						this.status.textContent = '插入文档失败';
						this.render();
						return;
					}
					this.status.textContent = '排版文档已写入，正在重新加载...';
					this.sheet.close();
				})
				.catch(() => {
					this.inserting = false;
					this.status.textContent = '插入文档失败';
					this.render();
				});
			return;
		}
		const state = this.controller.getState();
		const html = this.previewHtml
			? this.previewHtml
			: MobileAiResultRenderer.sanitizeTypesetHtml(state.preview);
		this.controller.accept(html);
	}

	private render(): void {
		const state = this.controller.getState();
		if (
			this.previewPanel.style.display !== 'none' &&
			!this.previewHtml &&
			state.preview
		) {
			MobileAiResultRenderer.renderTypesetInto(this.preview, state.preview);
		}
		const active =
			this.extracting ||
			this.filling ||
			this.inserting ||
			state.state === 'loading' ||
			state.state === 'streaming';
		const ready =
			(state.state === 'ready' && !!state.preview && !this.filling) ||
			(!!this.docxPath && !this.filling);
		this.startButton.disabled = active;
		this.stopButton.disabled = !active || this.extracting;
		this.copyButton.disabled = !ready;
		this.regenerateButton.disabled = !ready || active;
		this.applyButton.disabled = !ready || active;
		if (this.extracting) {
			this.status.textContent = '正在提取文档全文...';
		} else if (this.filling) {
			this.status.textContent = '正在填充 docx 模板...';
		} else if (this.inserting) {
			this.status.textContent = '正在写入排版文档...';
		} else if (state.error) {
			this.status.textContent = state.error;
		} else if (active) {
			this.status.textContent =
				state.state === 'streaming' ? 'AI 正在排版...' : 'AI 正在生成...';
		} else if (ready) {
			this.status.textContent = this.docxPath
				? '排版完成，可覆盖当前文档'
				: '排版完成，可预览后插入文档（HTML 回退）';
			this.applyButton.textContent = this.docxPath ? '覆盖文档' : '插入文档';
		} else {
			this.status.textContent = '';
		}
	}

	private createButton(label: string, primary = false): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className =
			'mobile-ai-task-dialog__btn ' +
			(primary
				? 'mobile-ai-task-dialog__btn--primary'
				: 'mobile-ai-task-dialog__btn--secondary');
		button.textContent = label;
		return button;
	}
}
