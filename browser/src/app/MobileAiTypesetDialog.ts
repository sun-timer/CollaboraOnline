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
	private sourceExtension = '';
	private applyError = '';
	private previewHtml = '';
	private readonly unsubscribe: () => void;
	private disposed = false;
	private extractionEpoch = 0;

	constructor() {
		this.controller = WriterAiController.getInstance();
		this.sheet = new MobileAiSheet({
			title: 'AI 排版',
			presentation: 'writer',
			taskType: 'typeset',
			onClose: () => this.dispose(),
		});
		this.sheet.root.classList.add('mobile-ai-sheet-root--typeset-design');
		this.sheet.root.querySelector('.mobile-ai-sheet-close')!.innerHTML = MOBILE_AI_TYPESET_CLOSE;
		this.content = document.createElement('div');
		this.content.className = 'mobile-ai-task-dialog mobile-ai-task-dialog--typeset';

		this.selectPanel = document.createElement('div');
		this.selectPanel.className = 'mobile-ai-typeset-select';
		const grid = document.createElement('div');
		grid.className = 'mobile-ai-typeset-grid';
		WriterAiCatalog.TYPESET_TYPES.forEach((item) => {
			const card = document.createElement('button');
			card.type = 'button';
			card.className = 'mobile-ai-typeset-card mobile-ai-typeset-card--' + item.key;
			const image = document.createElement('img');
			image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(MOBILE_AI_TYPESET_PREVIEWS[item.key]);
			image.alt = '';
			const label = document.createElement('span'); label.textContent = item.label;
			card.append(image, label);
			card.setAttribute('aria-pressed', item.key === this.selectedType ? 'true' : 'false');
			card.onclick = () => this.selectType(item.key);
			this.typeCards.push(card);
			grid.appendChild(card);
		});
		this.selectPanel.appendChild(grid);
		this.startButton = this.createButton('开始排版', true);
		this.startButton.classList.add('mobile-ai-design-primary');
		this.startButton.innerHTML = '<span class="mobile-ai-design-sparkle">' + MOBILE_AI_TYPESET_SPARKLE + '</span><span>开始排版</span>';
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
		this.controller.resetPreview();
		this.sheet.open();
		this.resetResultState();
		this.showSelectPanel();
		this.render();
	}

	close(): void {
		this.sheet.close();
		this.dispose();
	}

	private dispose(): void {
		if (this.disposed) return;
		this.disposed = true;
		this.extractionEpoch++;
		if (this.controller.getState().state !== 'accepted') this.controller.cancel();
		this.unsubscribe();
	}

	private resetResultState(): void {
		this.sections = null;
		this.typesetImages = null;
		this.docxPath = '';
		this.sourceExtension = '';
		this.applyError = '';
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
		const liveSnapshot = (window as any).ThisIsTheiOSApp && !this.isPresentation();
		const epoch = ++this.extractionEpoch;
		this.status.textContent = '正在提取文档全文...';
		this.showPreviewPanel();
		this.startButton.disabled = true;
		const extract = (): void => {
			if (this.disposed || epoch !== this.extractionEpoch) return;
			this.extractAndRequest(epoch)
				.catch((error: Error) => {
					if (this.disposed || epoch !== this.extractionEpoch) return;
					this.extracting = false;
					const message = error && error.message ? error.message : '文档全文提取失败';
					this.startButton.disabled = false;
					if (liveSnapshot) {
						this.applyError = message;
						this.showPreviewPanel();
					} else {
						this.status.textContent = message;
						this.showSelectPanel();
					}
				})
				.finally(() => {
					if (this.disposed || epoch !== this.extractionEpoch) return;
					this.extracting = false;
					this.render();
				});
		};
		if (liveSnapshot) {
			// Native extraction exports the live model and acknowledges only
			// after that snapshot is available; no save/timer race is needed.
			extract();
			return;
		}
		const map = (window as any).app?.map;
		if (map && typeof map.sendUnoCommand === 'function') {
			map.sendUnoCommand('.uno:Save');
		}
		window.setTimeout(extract, 500);
	}

	private isPresentation(): boolean {
		return (window as any).app?.map?.getDocType?.() === 'presentation';
	}

	private extractAndRequest(epoch: number): Promise<void> {
		const bridge = TypesetBridge.getInstance();
		if (bridge.isAvailable() && !this.isPresentation()) {
			return bridge.extractStructured().then((result) => {
				if (this.disposed || epoch !== this.extractionEpoch) return;
				this.paragraphs = result.paragraphs || [];
				this.sourceExtension = (result.sourceExtension || '').toLowerCase();
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
			if (this.disposed || epoch !== this.extractionEpoch) return;
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
		if ((window as any).ThisIsTheiOSApp && !this.isPresentation()) {
			this.startTypeset();
			return;
		}
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
		if (sections && (this.isPresentation() || TypesetBridge.getInstance().isAvailable())) {
			if (this.isPresentation()) {
				this.sections = sections;
				this.previewHtml = TypesetPreviewHtml.build(this.selectedType, sections);
				this.preview.innerHTML = this.previewHtml;
				this.render();
				return;
			}
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
		if (this.isPresentation() || !TypesetBridge.getInstance().isAvailable()) {
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
				if (this.disposed) return;
				this.docxPath = result.docxPath || '';
				this.filling = false;
				this.render();
			})
			.catch((error: Error) => {
				if (this.disposed) return;
				this.filling = false;
				this.docxPath = '';
				this.status.textContent =
					(error && error.message ? error.message : '模板填充失败') +
					'，将使用 HTML 方式插入';
				this.render();
			});
	}

	private async applyResult(): Promise<void> {
		this.applyError = '';
		if (!this.isPresentation() && this.docxPath && this.sourceExtension === 'docx' &&
			TypesetBridge.getInstance().isAvailable()) {
			this.inserting = true;
			this.status.textContent = '正在写入排版文档...';
			this.render();
			try {
				const ok = await TypesetBridge.getInstance().insertDocument(this.docxPath);
				if (this.disposed) return;
				this.inserting = false;
				if (!ok) {
					this.applyError = '插入文档失败';
					this.render();
					return;
				}
				this.close();
			} catch (_error) {
				if (this.disposed) return;
				this.inserting = false;
				this.applyError = '插入文档失败';
				this.render();
			}
			return;
		}
		if (!this.isPresentation() && this.docxPath && this.sourceExtension !== 'docx' &&
			this.typesetImages && Object.keys(this.typesetImages).length > 0) {
			this.status.textContent = '原文包含图片，兼容写入无法安全保留图片；文档未修改。请另存为 DOCX 后再排版';
			this.applyError = this.status.textContent;
			this.render();
			return;
		}
		const state = this.controller.getState();
		const html = this.previewHtml
			? this.previewHtml
			: MobileAiResultRenderer.sanitizeTypesetHtml(state.preview);
		if ((window as any).ThisIsTheiOSApp) {
			if (await this.controller.acceptAsync(html, 'replaceDocument') && !this.disposed) this.close();
		} else {
			this.controller.accept(html);
		}
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
			state.state === 'streaming' || state.state === 'inserting';
		const ready =
			(state.state === 'ready' && !!state.preview && !this.filling) ||
			(!!this.docxPath && !this.filling);
		this.startButton.disabled = active;
		this.stopButton.disabled = !active || this.extracting;
		this.copyButton.disabled = !ready;
		this.regenerateButton.disabled = !ready || active;
		this.applyButton.disabled = !ready || active;
		if (this.applyError) this.applyButton.disabled = true;
		if (this.extracting) {
			this.status.textContent = '正在提取文档全文...';
		} else if (this.filling) {
			this.status.textContent = '正在填充 docx 模板...';
		} else if (this.inserting) {
			this.status.textContent = '正在写入排版文档...';
		} else if (this.applyError) {
			this.status.textContent = this.applyError;
		} else if (state.error) {
			this.status.textContent = state.error;
		} else if (state.warning) {
			this.status.textContent = state.warning;
		} else if (active) {
			this.status.textContent =
				state.state === 'streaming' ? 'AI 正在排版...' : 'AI 正在生成...';
		} else if (ready) {
			this.status.textContent = this.sourceExtension === 'docx' && this.docxPath
				? '排版完成，可替换当前 DOCX 文档'
				: '排版完成，将保留原格式并替换正文';
			this.applyButton.textContent = '覆盖文档';
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

const MOBILE_AI_TYPESET_PREVIEWS: Record<string, string> = {"paper": "<svg width=\"242\" height=\"238\" viewBox=\"0 0 242 238\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<rect width=\"242\" height=\"238\" rx=\"16\" fill=\"white\"/>\n<path d=\"M83.76 15.732C85.5893 19.1667 88.3987 22.116 92.188 24.58L90.816 26.54C87.3627 24.3373 84.6747 21.5747 82.752 18.252C80.9413 21.332 78.272 24.1133 74.744 26.596L73.288 24.664C75.0053 23.4507 76.62 22.0693 78.132 20.52C79.644 18.9707 80.8387 17.3747 81.716 15.732H83.76ZM71.944 22.452C71.5333 21.8173 70.8987 20.9493 70.04 19.848C69.2 18.7467 68.4907 17.8693 67.912 17.216L69.76 15.816C70.32 16.4507 71.02 17.2907 71.86 18.336C72.7 19.3627 73.344 20.2027 73.792 20.856L71.944 22.452ZM71.944 36.004L75.052 33.484L75.836 35.612C73.9507 37.0307 72.5227 38.1507 71.552 38.972C71.44 39.0653 71.0573 39.4107 70.404 40.008L68.976 38.3C69.2933 38.0013 69.4987 37.7587 69.592 37.572C69.6853 37.3667 69.732 37.096 69.732 36.76V26.652H66.176V24.328H71.944V36.004ZM87.708 36.06C87.7267 35.6493 87.7453 35.1267 87.764 34.492C87.8013 33.8387 87.82 33.2787 87.82 32.812L90.144 33.428C90.032 34.884 89.976 35.612 89.976 35.612C89.9573 35.8733 89.92 36.4147 89.864 37.236C89.7893 38.3933 89.4533 39.2147 88.856 39.7C88.2587 40.1853 87.288 40.484 85.944 40.596C85.216 40.6707 84.404 40.708 83.508 40.708C82.612 40.708 81.8 40.6707 81.072 40.596C80.064 40.5027 79.28 40.3253 78.72 40.064C78.16 39.8213 77.74 39.4013 77.46 38.804C77.1987 38.2067 77.068 37.3573 77.068 36.256V25.448H79.448V30.096C80.848 29.4427 82.276 28.7147 83.732 27.912C85.2067 27.0907 86.392 26.3627 87.288 25.728L88.408 27.912C87.344 28.5467 85.944 29.3213 84.208 30.236C82.4907 31.132 80.904 31.9067 79.448 32.56V36.144C79.448 36.7227 79.4947 37.1613 79.588 37.46C79.7 37.7587 79.8867 37.9827 80.148 38.132C80.428 38.2627 80.848 38.356 81.408 38.412C82.2667 38.4867 82.9107 38.524 83.34 38.524C83.7693 38.524 84.4133 38.4867 85.272 38.412C85.944 38.3373 86.4387 38.2347 86.756 38.104C87.0733 37.9733 87.2973 37.7587 87.428 37.46C87.5773 37.1427 87.6707 36.676 87.708 36.06ZM118.872 40.96C116.296 40.0453 114.037 39.0933 112.096 38.104C110.155 37.096 108.456 35.9947 107 34.8C105.563 35.9947 103.883 37.1053 101.96 38.132C100.037 39.1587 97.7973 40.148 95.24 41.1L94.148 38.832C96.6307 37.9733 98.7773 37.096 100.588 36.2C102.417 35.304 103.995 34.324 105.32 33.26C103.883 31.8227 102.669 30.1893 101.68 28.36C100.709 26.512 99.888 24.384 99.216 21.976H94.764V19.764H106.188C105.815 18.4947 105.395 17.2533 104.928 16.04L107.224 15.424C107.728 16.7493 108.213 18.196 108.68 19.764H119.152V21.976H114.616C113.981 24.4027 113.188 26.5307 112.236 28.36C111.284 30.1893 110.089 31.832 108.652 33.288C111.321 35.3973 115.045 37.18 119.824 38.636L118.872 40.96ZM101.624 21.976C102.184 24.048 102.893 25.8867 103.752 27.492C104.611 29.0973 105.684 30.5347 106.972 31.804C108.223 30.5347 109.259 29.1067 110.08 27.52C110.92 25.9147 111.601 24.0667 112.124 21.976H101.624ZM130.968 31.384C130.203 30.2453 129.353 29.088 128.42 27.912V41.1H126.18V28.024C125.041 30.88 123.921 33.1293 122.82 34.772L121.7 32.476C122.596 31.188 123.436 29.732 124.22 28.108C125.004 26.4653 125.657 24.7947 126.18 23.096V22.9H122.68V20.856H126.18V15.788H128.42V20.856H131.64V22.9H128.42V25.28C128.924 25.784 129.596 26.512 130.436 27.464C131.295 28.3973 131.948 29.144 132.396 29.704L130.968 31.384ZM133.376 17.328H146.536V19.4H133.376V17.328ZM147.796 26.26H140.88V38.048C140.88 39.112 140.628 39.8307 140.124 40.204C139.62 40.596 138.771 40.792 137.576 40.792C136.829 40.792 136.073 40.736 135.308 40.624L134.832 38.496C135.803 38.664 136.633 38.748 137.324 38.748C137.847 38.748 138.192 38.6267 138.36 38.384C138.528 38.1413 138.612 37.7027 138.612 37.068V26.26H132.088V24.188H147.796V26.26ZM136.568 29.396C136.101 30.8333 135.467 32.448 134.664 34.24C133.861 36.032 133.087 37.5627 132.34 38.832L130.436 37.74C131.22 36.4707 131.995 34.9867 132.76 33.288C133.525 31.5707 134.141 29.9933 134.608 28.556L136.568 29.396ZM144.184 28.612C144.744 29.676 145.444 31.1693 146.284 33.092C147.143 35.0147 147.796 36.5827 148.244 37.796L146.2 38.664C145.771 37.4507 145.145 35.8827 144.324 33.96C143.521 32.0187 142.859 30.5253 142.336 29.48L144.184 28.612ZM151.94 16.712H161.348V26.12H151.94V16.712ZM167.536 21.192L168.096 18.924H162.944V17.02H175.208V18.924H170.168C170.037 19.5027 169.841 20.2587 169.58 21.192H174.284V31.496H172.268V22.928H165.856V31.524H163.952V21.192H167.536ZM159.276 20.576V18.42H153.956V20.576H159.276ZM153.956 22.256V24.412H159.276V22.256H153.956ZM170.028 24.216V28.724C170.028 29.7507 169.935 30.6373 169.748 31.384C172.175 33.288 173.967 34.7253 175.124 35.696L173.78 37.376C172.641 36.3493 171.045 34.9773 168.992 33.26C168.525 34.0813 167.853 34.828 166.976 35.5C166.099 36.1533 164.941 36.816 163.504 37.488L162.496 35.752C164.083 35.0053 165.277 34.296 166.08 33.624C166.883 32.9333 167.424 32.1867 167.704 31.384C167.984 30.5813 168.124 29.5547 168.124 28.304V24.216H170.028ZM160.312 38.272C163.019 38.5707 165.809 38.72 168.684 38.72C171.055 38.72 173.416 38.636 175.768 38.468L175.32 40.512C173.341 40.6427 171.185 40.708 168.852 40.708C165.753 40.708 162.897 40.568 160.284 40.288C158.735 40.12 157.437 39.6907 156.392 39C155.365 38.2907 154.413 37.2453 153.536 35.864C153.331 36.76 153.06 37.6093 152.724 38.412C152.388 39.2147 151.959 40.0547 151.436 40.932L149.672 39.924C150.531 38.58 151.137 37.2733 151.492 36.004C151.865 34.7347 152.127 33.0827 152.276 31.048L154.18 31.16C154.143 31.7573 154.068 32.4573 153.956 33.26C154.46 34.0813 154.899 34.7627 155.272 35.304C155.664 35.8453 156.084 36.3027 156.532 36.676V30.068H150.596V28.136H162.636V30.068H158.492V32.784H162.132V34.632H158.492V37.852C159.015 38.0387 159.621 38.1787 160.312 38.272Z\" fill=\"#101010\"/>\n<path d=\"M123.5 193H200.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M50.252 193.072C51.1693 195.429 53.2813 197.04 56.588 197.904L55.948 199.216C54.38 198.757 53.052 198.117 51.964 197.296C50.876 196.464 50.044 195.467 49.468 194.304C48.5187 196.299 46.3533 197.952 42.972 199.264L42.268 198C44.252 197.264 45.7293 196.491 46.7 195.68C47.6707 194.869 48.284 194 48.54 193.072H42.508V191.76H48.7V188.96H43.244V187.696H50.188C51.0733 186.608 51.916 185.488 52.716 184.336L53.932 185.056C53.1747 186.112 52.5027 186.992 51.916 187.696H55.532V188.96H50.076V191.68V191.76H56.3V193.072H50.252ZM46.892 187.664C46.3693 186.864 45.756 186.016 45.052 185.12L46.156 184.4C46.7853 185.189 47.404 186.032 48.012 186.928L46.892 187.664ZM62.684 196.864C62.2147 197.173 61.564 197.643 60.732 198.272L60.444 198.496L60.044 198.816L59.356 197.776C59.5373 197.648 59.6653 197.525 59.74 197.408C59.8147 197.291 59.852 197.147 59.852 196.976V193.616H58.3V192.48H59.852V190.48H58.876V189.792L58.06 189.264C58.7853 187.973 59.404 186.363 59.916 184.432L61.068 184.64C60.94 185.12 60.7693 185.712 60.556 186.416H62.556V187.584H60.14C59.8947 188.245 59.6333 188.848 59.356 189.392H62.236V190.48H60.956V192.48H62.396V193.616H60.956V196.8L62.396 195.68L62.684 196.864ZM69.58 197.12H68.476V195.712H66.06V194.704H68.476V193.536H66.444V192.528H68.476V191.376H66.524V190.384H68.476V189.136H66.092V188.16H68.476V186.912H66.54V185.92H68.476V184.544H69.58V185.92H71.996V188.16H72.668V189.136H71.996V191.376H69.58V192.528H71.964V193.536H69.58V194.704H72.332V195.712H69.58V197.12ZM68.428 197.648C69.2493 197.701 69.9747 197.728 70.604 197.728C71.2547 197.728 71.98 197.701 72.78 197.648L72.572 198.72C71.9853 198.773 71.276 198.8 70.444 198.8C69.612 198.8 68.8973 198.773 68.3 198.72C67.5107 198.667 66.812 198.48 66.204 198.16C65.6067 197.84 65.0733 197.365 64.604 196.736C64.2413 197.504 63.804 198.235 63.292 198.928L62.396 198.176C63.0467 197.419 63.564 196.571 63.948 195.632C63.532 194.779 63.1693 193.68 62.86 192.336L63.74 191.984C63.932 192.827 64.1453 193.568 64.38 194.208C64.572 193.355 64.7107 192.352 64.796 191.2H62.956V190.144L64.716 186.784H62.988V185.712H65.9V186.8L64.172 190.144H65.852V191.248C65.7453 192.219 65.6333 193.045 65.516 193.728C65.3987 194.4 65.244 195.024 65.052 195.6C65.4253 196.208 65.884 196.688 66.428 197.04C66.9827 197.381 67.6493 197.584 68.428 197.648ZM70.94 186.912H69.58V188.16H70.94V186.912ZM70.94 189.136H69.58V190.384H70.94V189.136ZM76.172 184.688C76.4707 185.051 76.844 185.541 77.292 186.16C77.7507 186.779 78.1027 187.275 78.348 187.648L77.26 188.4C77.0573 188.027 76.7267 187.531 76.268 186.912C75.8093 186.293 75.42 185.803 75.1 185.44L76.172 184.688ZM88.108 196.992C88.108 197.717 87.9907 198.235 87.756 198.544C87.5213 198.853 87.1213 199.008 86.556 199.008C85.7773 199.008 85.1053 198.981 84.54 198.928L84.3 197.696C85.0253 197.771 85.644 197.808 86.156 197.808C86.4227 197.808 86.6093 197.717 86.716 197.536C86.8227 197.355 86.876 197.045 86.876 196.608V186.8H79.5V185.568H88.108V196.992ZM79.068 188.688H86.204V189.872H79.068V188.688ZM75.724 197.824C75.8947 197.653 76.012 197.477 76.076 197.296C76.1507 197.115 76.188 196.875 76.188 196.576V191.072H74.364V189.744H77.42V196.368L78.716 195.248L79.148 196.448C78.764 196.757 78.1827 197.253 77.404 197.936C76.9987 198.299 76.716 198.56 76.556 198.72L75.724 197.824ZM79.66 191.664H85.004V196.624H79.66V191.664ZM80.828 192.784V195.456H83.836V192.784H80.828ZM92.78 191.36C92.5347 191.36 92.3213 191.269 92.14 191.088C91.9693 190.907 91.884 190.688 91.884 190.432C91.884 190.176 91.9693 189.957 92.14 189.776C92.3213 189.595 92.5347 189.504 92.78 189.504C93.036 189.504 93.2493 189.595 93.42 189.776C93.6013 189.957 93.692 190.176 93.692 190.432C93.692 190.688 93.6013 190.907 93.42 191.088C93.2493 191.269 93.036 191.36 92.78 191.36ZM92.78 198.176C92.5347 198.176 92.3213 198.085 92.14 197.904C91.9693 197.723 91.884 197.504 91.884 197.248C91.884 196.992 91.9693 196.773 92.14 196.592C92.3213 196.411 92.5347 196.32 92.78 196.32C93.036 196.32 93.2493 196.411 93.42 196.592C93.6013 196.773 93.692 196.992 93.692 197.248C93.692 197.504 93.6013 197.723 93.42 197.904C93.2493 198.085 93.036 198.176 92.78 198.176Z\" fill=\"#999999\"/>\n<path d=\"M22.3125 119L218.312 119\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M30.3125 137L210.312 137\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M40.3125 155H200.312\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 211H219.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M119.96 70.0262H117.28C117.027 70.8929 116.8 71.6329 116.6 72.2462H119.32V82.2462C119.32 83.1395 119.153 83.7662 118.82 84.1262C118.5 84.4862 117.953 84.6662 117.18 84.6662C116.567 84.6662 115.947 84.6262 115.32 84.5462L115.08 83.1262C115.68 83.2329 116.247 83.2862 116.78 83.2862C117.22 83.2862 117.52 83.1862 117.68 82.9862C117.84 82.7862 117.92 82.4395 117.92 81.9462V73.6462H114.42V75.1862H117.26V76.4462H114.42V77.7862H116.5V82.3062H111.16V77.7862H113.2V76.4462H110.38V75.1862H113.2V73.6462H109.74V84.6262H108.34V72.2462H110.88C110.667 71.2595 110.5 70.5195 110.38 70.0262H107.88V68.5862H113.28C112.827 67.7195 112.527 67.1662 112.38 66.9262L113.82 66.3862L114.96 68.5862H119.96V70.0262ZM105.74 66.6662V70.3262H107.7V71.8062H105.74V75.1262L106.26 74.9262C107.113 74.6062 107.56 74.4395 107.6 74.4262L107.68 75.9862L106.54 76.3862L105.74 76.6662V82.4462C105.74 83.2462 105.6 83.7995 105.32 84.1062C105.04 84.4262 104.513 84.5862 103.74 84.5862C103.447 84.5862 103.007 84.5595 102.42 84.5062L102.1 83.0462C102.687 83.1262 103.113 83.1662 103.38 83.1662C103.74 83.1662 103.98 83.0729 104.1 82.8862C104.22 82.6995 104.28 82.3995 104.28 81.9862V77.1862L103.48 77.4862C103.32 77.5395 102.813 77.7262 101.96 78.0462L101.8 76.4462C102.053 76.3795 102.66 76.1862 103.62 75.8662L104.28 75.6462V71.8062H102.22V70.3262H104.28V66.6662H105.74ZM115.1 72.2462C115.353 71.5262 115.587 70.7862 115.8 70.0262H111.82C112.06 71.0129 112.227 71.7529 112.32 72.2462H115.1ZM112.46 81.0262H115.18V79.0862H112.46V81.0262ZM123.26 70.8262H127.9V68.9462H122.38V67.5462H139.36V68.9462H133.86V70.8262H138.5V75.4462H123.26V70.8262ZM129.36 70.8262H132.4V68.9462H129.36V70.8262ZM127.9 72.1462H124.8V74.1262H127.9V72.1462ZM132.4 72.1462H129.36V74.1262H132.4V72.1462ZM137.04 74.1262V72.1462H133.86V74.1262H137.04ZM135.92 78.7862C135.227 80.0262 134.38 81.0262 133.38 81.7862C135.287 82.3462 137.2 82.9262 139.12 83.5262L138.52 84.9862C136.587 84.3062 134.327 83.5595 131.74 82.7462C130.767 83.2129 129.573 83.6062 128.16 83.9262C126.747 84.2595 125 84.5595 122.92 84.8262L122.42 83.3462C125.607 83.0129 128.02 82.5929 129.66 82.0862L125.04 80.6862C125.413 80.1929 125.86 79.5595 126.38 78.7862H121.92V77.4262H127.24C127.68 76.6929 128.02 76.0729 128.26 75.5662L129.8 75.9862C129.693 76.1995 129.52 76.5195 129.28 76.9462L129.02 77.4262H139.92V78.7862H135.92ZM127.34 80.0462C128.553 80.3795 129.653 80.6929 130.64 80.9862C130.8 81.0262 131.133 81.1195 131.64 81.2662C132.213 80.9462 132.693 80.5929 133.08 80.2062C133.48 79.8062 133.847 79.3329 134.18 78.7862H128.18C127.9 79.2529 127.62 79.6729 127.34 80.0462Z\" fill=\"#6A6A6A\"/>\n</svg>", "gov": "<svg width=\"242\" height=\"238\" viewBox=\"0 0 242 238\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<rect width=\"242\" height=\"238\" rx=\"16\" fill=\"white\"/>\n<path d=\"M82.892 18.9122L75.64 29.0202L83.088 39.4082H80.036L74.1 31.0362L68.22 39.4082H65.336L72.784 29.0482L65.532 18.9122H68.612L74.324 27.0602L80.008 18.9122H82.892ZM101.322 18.9122L94.0697 29.0202L101.518 39.4082H98.4657L92.5297 31.0362L86.6497 39.4082H83.7657L91.2137 29.0482L83.9617 18.9122H87.0417L92.7537 27.0602L98.4377 18.9122H101.322ZM119.751 18.9122L112.499 29.0202L119.947 39.4082H116.895L110.959 31.0362L105.079 39.4082H102.195L109.643 29.0482L102.391 18.9122H105.471L111.183 27.0602L116.867 18.9122H119.751ZM146.665 34.5642V36.6922H135.409V41.5082H133.169V36.6922H121.913V34.5642H133.169V31.6802H124.517V19.8642H129.977C128.932 18.4829 128.12 17.4842 127.541 16.8682L129.305 15.7482C129.846 16.3082 130.733 17.4189 131.965 19.0802L130.761 19.8642H136.249C136.921 19.0242 137.882 17.6522 139.133 15.7482L141.177 16.6442L140.645 17.4282C140.458 17.7082 140.216 18.0535 139.917 18.4642C139.637 18.8749 139.301 19.3415 138.909 19.8642H144.061V31.6802H135.409V34.5642H146.665ZM133.169 24.7922V21.8802H126.729V24.7922H133.169ZM135.409 24.7922H141.849V21.8802H135.409V24.7922ZM126.729 26.7802V29.7202H133.169V26.7802H126.729ZM135.409 26.7802V29.7202H141.849V26.7802H135.409ZM157.417 20.3402H165.033C164.51 18.8282 163.95 17.4469 163.353 16.1962L165.649 15.5802C166.358 17.1295 166.993 18.7162 167.553 20.3402H174.385V22.5242H157.417V20.3402ZM157.445 16.3922C156.736 18.6509 155.989 20.6389 155.205 22.3562V41.2282H153.021V26.5282C152.218 27.8535 151.248 29.2442 150.109 30.7002L148.793 28.6842C150.062 27.0415 151.257 25.0909 152.377 22.8322C153.497 20.5549 154.421 18.2215 155.149 15.8322L157.445 16.3922ZM174.917 37.9522V40.1362H156.605V37.9522H165.929C166.489 36.3655 167.189 34.1349 168.029 31.2602C168.888 28.3855 169.569 25.9589 170.073 23.9802L172.285 24.6522C171.818 26.4069 171.174 28.5909 170.353 31.2042C169.55 33.7989 168.832 36.0482 168.197 37.9522H174.917ZM162.345 35.9642C162.028 34.6015 161.524 32.7255 160.833 30.3362C160.142 27.9282 159.573 26.0615 159.125 24.7362L161.253 24.1482C161.72 25.5482 162.28 27.3775 162.933 29.6362C163.586 31.8949 164.109 33.8082 164.501 35.3762L162.345 35.9642Z\" fill=\"#BE3030\"/>\n<path d=\"M81.168 67.216L78.06 71.548L81.252 76H79.944L77.4 72.412L74.88 76H73.644L76.836 71.56L73.728 67.216H75.048L77.496 70.708L79.932 67.216H81.168ZM89.0664 67.216L85.9584 71.548L89.1504 76H87.8424L85.2984 72.412L82.7784 76H81.5424L84.7344 71.56L81.6264 67.216H82.9464L85.3944 70.708L87.8304 67.216H89.0664ZM96.9649 67.216L93.8569 71.548L97.0489 76H95.7409L93.1969 72.412L90.6769 76H89.4409L92.6329 71.56L89.5249 67.216H90.8449L93.2929 70.708L95.7289 67.216H96.9649ZM104.72 66.22H107.552V67.156H105.728V76.396H107.552V77.332H104.72V66.22ZM110.021 75.052H113.993V76H108.389V75.316L111.233 72.148C111.769 71.556 112.137 71.092 112.337 70.756C112.537 70.412 112.637 70.04 112.637 69.64C112.637 69.128 112.497 68.732 112.217 68.452C111.937 68.172 111.585 68.032 111.161 68.032C110.729 68.032 110.349 68.18 110.021 68.476C109.693 68.764 109.457 69.148 109.313 69.628L108.449 69.136C108.625 68.48 108.973 67.972 109.493 67.612C110.013 67.244 110.593 67.06 111.233 67.06C111.697 67.06 112.121 67.16 112.505 67.36C112.889 67.552 113.193 67.84 113.417 68.224C113.649 68.6 113.765 69.056 113.765 69.592C113.765 70.112 113.637 70.604 113.381 71.068C113.125 71.532 112.677 72.116 112.037 72.82L110.021 75.052ZM120.909 71.608C120.909 73.096 120.661 74.224 120.165 74.992C119.677 75.752 118.973 76.132 118.053 76.132C117.141 76.132 116.437 75.748 115.941 74.98C115.453 74.212 115.209 73.088 115.209 71.608C115.209 70.12 115.453 68.992 115.941 68.224C116.437 67.448 117.141 67.06 118.053 67.06C118.973 67.06 119.677 67.444 120.165 68.212C120.661 68.98 120.909 70.112 120.909 71.608ZM116.349 71.608C116.349 72.8 116.485 73.688 116.757 74.272C117.037 74.856 117.469 75.148 118.053 75.148C118.637 75.148 119.069 74.86 119.349 74.284C119.629 73.7 119.769 72.808 119.769 71.608C119.769 70.4 119.629 69.504 119.349 68.92C119.069 68.328 118.637 68.032 118.053 68.032C117.469 68.032 117.037 68.328 116.757 68.92C116.485 69.504 116.349 70.4 116.349 71.608ZM123.709 75.052H127.681V76H122.077V75.316L124.921 72.148C125.457 71.556 125.825 71.092 126.025 70.756C126.225 70.412 126.325 70.04 126.325 69.64C126.325 69.128 126.185 68.732 125.905 68.452C125.625 68.172 125.273 68.032 124.849 68.032C124.417 68.032 124.037 68.18 123.709 68.476C123.381 68.764 123.145 69.148 123.001 69.628L122.137 69.136C122.313 68.48 122.661 67.972 123.181 67.612C123.701 67.244 124.281 67.06 124.921 67.06C125.385 67.06 125.809 67.16 126.193 67.36C126.577 67.552 126.881 67.84 127.105 68.224C127.337 68.6 127.453 69.056 127.453 69.592C127.453 70.112 127.325 70.604 127.069 71.068C126.813 71.532 126.365 72.116 125.725 72.82L123.709 75.052ZM132.952 71.308C133.48 71.476 133.872 71.748 134.128 72.124C134.392 72.5 134.524 72.964 134.524 73.516C134.524 74.06 134.396 74.532 134.14 74.932C133.884 75.324 133.544 75.624 133.12 75.832C132.696 76.032 132.232 76.132 131.728 76.132C131.088 76.132 130.496 75.968 129.952 75.64C129.416 75.312 129.056 74.832 128.872 74.2L129.736 73.708C129.888 74.188 130.148 74.552 130.516 74.8C130.892 75.04 131.296 75.16 131.728 75.16C132.2 75.16 132.592 75.016 132.904 74.728C133.224 74.432 133.384 74.024 133.384 73.504C133.384 72.432 132.652 71.896 131.188 71.896H130.816L130.828 70.936H131.248C131.84 70.936 132.288 70.792 132.592 70.504C132.904 70.216 133.06 69.848 133.06 69.4C133.06 68.976 132.928 68.644 132.664 68.404C132.4 68.164 132.06 68.044 131.644 68.044C131.212 68.044 130.844 68.172 130.54 68.428C130.244 68.684 130.016 69.052 129.856 69.532L128.992 69.04C129.184 68.392 129.52 67.904 130 67.576C130.488 67.24 131.056 67.072 131.704 67.072C132.456 67.072 133.06 67.284 133.516 67.708C133.972 68.132 134.2 68.688 134.2 69.376C134.2 69.816 134.096 70.204 133.888 70.54C133.688 70.876 133.376 71.132 132.952 71.308ZM135.404 67.156V66.22H138.236V77.332H135.404V76.396H137.228V67.156H135.404ZM143.711 69.796V68.692L146.267 67.204H147.083V76H146.003V68.488L143.711 69.796ZM155.643 71.608C155.643 73.096 155.395 74.224 154.899 74.992C154.411 75.752 153.707 76.132 152.787 76.132C151.875 76.132 151.171 75.748 150.675 74.98C150.187 74.212 149.943 73.088 149.943 71.608C149.943 70.12 150.187 68.992 150.675 68.224C151.171 67.448 151.875 67.06 152.787 67.06C153.707 67.06 154.411 67.444 154.899 68.212C155.395 68.98 155.643 70.112 155.643 71.608ZM151.083 71.608C151.083 72.8 151.219 73.688 151.491 74.272C151.771 74.856 152.203 75.148 152.787 75.148C153.371 75.148 153.803 74.86 154.083 74.284C154.363 73.7 154.503 72.808 154.503 71.608C154.503 70.4 154.363 69.504 154.083 68.92C153.803 68.328 153.371 68.032 152.787 68.032C152.203 68.032 151.771 68.328 151.491 68.92C151.219 69.504 151.083 70.4 151.083 71.608ZM158.407 66.472H166.003V69.7H158.407V66.472ZM159.367 68.86H165.055V67.312H159.367V68.86ZM167.659 71.608H160.015L159.775 72.784H165.931C165.923 73.088 165.899 73.468 165.859 73.924C165.827 74.372 165.787 74.74 165.739 75.028C165.667 75.508 165.567 75.864 165.439 76.096C165.311 76.328 165.111 76.488 164.839 76.576C164.567 76.672 164.159 76.724 163.615 76.732H163.195C162.499 76.732 161.807 76.708 161.119 76.66L160.939 75.724C161.731 75.796 162.527 75.832 163.327 75.832C163.671 75.832 163.927 75.808 164.095 75.76C164.263 75.704 164.387 75.608 164.467 75.472C164.547 75.336 164.615 75.12 164.671 74.824C164.735 74.448 164.775 74.048 164.791 73.624H158.527L158.959 71.608H156.775V70.756H167.659V71.608Z\" fill=\"#494949\"/>\n<path d=\"M108.12 124.56C107.573 123.747 106.967 122.92 106.3 122.08V131.5H104.7V122.16C103.887 124.2 103.087 125.807 102.3 126.98L101.5 125.34C102.14 124.42 102.74 123.38 103.3 122.22C103.86 121.047 104.327 119.853 104.7 118.64V118.5H102.2V117.04H104.7V113.42H106.3V117.04H108.6V118.5H106.3V120.2C106.66 120.56 107.14 121.08 107.74 121.76C108.353 122.427 108.82 122.96 109.14 123.36L108.12 124.56ZM109.84 114.52H119.24V116H109.84V114.52ZM120.14 120.9H115.2V129.32C115.2 130.08 115.02 130.593 114.66 130.86C114.3 131.14 113.693 131.28 112.84 131.28C112.307 131.28 111.767 131.24 111.22 131.16L110.88 129.64C111.573 129.76 112.167 129.82 112.66 129.82C113.033 129.82 113.28 129.733 113.4 129.56C113.52 129.387 113.58 129.073 113.58 128.62V120.9H108.92V119.42H120.14V120.9ZM112.12 123.14C111.787 124.167 111.333 125.32 110.76 126.6C110.187 127.88 109.633 128.973 109.1 129.88L107.74 129.1C108.3 128.193 108.853 127.133 109.4 125.92C109.947 124.693 110.387 123.567 110.72 122.54L112.12 123.14ZM117.56 122.58C117.96 123.34 118.46 124.407 119.06 125.78C119.673 127.153 120.14 128.273 120.46 129.14L119 129.76C118.693 128.893 118.247 127.773 117.66 126.4C117.087 125.013 116.613 123.947 116.24 123.2L117.56 122.58ZM123.1 114.08H129.82V120.8H123.1V114.08ZM134.24 117.28L134.64 115.66H130.96V114.3H139.72V115.66H136.12C136.027 116.073 135.887 116.613 135.7 117.28H139.06V124.64H137.62V118.52H133.04V124.66H131.68V117.28H134.24ZM128.34 116.84V115.3H124.54V116.84H128.34ZM124.54 118.04V119.58H128.34V118.04H124.54ZM136.02 119.44V122.66C136.02 123.393 135.953 124.027 135.82 124.56C137.553 125.92 138.833 126.947 139.66 127.64L138.7 128.84C137.887 128.107 136.747 127.127 135.28 125.9C134.947 126.487 134.467 127.02 133.84 127.5C133.213 127.967 132.387 128.44 131.36 128.92L130.64 127.68C131.773 127.147 132.627 126.64 133.2 126.16C133.773 125.667 134.16 125.133 134.36 124.56C134.56 123.987 134.66 123.253 134.66 122.36V119.44H136.02ZM129.08 129.48C131.013 129.693 133.007 129.8 135.06 129.8C136.753 129.8 138.44 129.74 140.12 129.62L139.8 131.08C138.387 131.173 136.847 131.22 135.18 131.22C132.967 131.22 130.927 131.12 129.06 130.92C127.953 130.8 127.027 130.493 126.28 130C125.547 129.493 124.867 128.747 124.24 127.76C124.093 128.4 123.9 129.007 123.66 129.58C123.42 130.153 123.113 130.753 122.74 131.38L121.48 130.66C122.093 129.7 122.527 128.767 122.78 127.86C123.047 126.953 123.233 125.773 123.34 124.32L124.7 124.4C124.673 124.827 124.62 125.327 124.54 125.9C124.9 126.487 125.213 126.973 125.48 127.36C125.76 127.747 126.06 128.073 126.38 128.34V123.62H122.14V122.24H130.74V123.62H127.78V125.56H130.38V126.88H127.78V129.18C128.153 129.313 128.587 129.413 129.08 129.48Z\" fill=\"#6A6A6A\"/>\n<path d=\"M23.5 172H61.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 190L219.5 190\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 208L219.5 208\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M21.8477 90.5068H220.153\" stroke-width=\"2.52083\" stroke=\"#BE3030\"/>\n</svg>", "contract": "<svg width=\"242\" height=\"238\" viewBox=\"0 0 242 238\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<rect width=\"242\" height=\"238\" rx=\"16\" fill=\"white\"/>\n<path d=\"M75.5 162L219.5 162\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 180L219.5 180\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 198L219.5 198\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M83.392 18.9122L76.14 29.0202L83.588 39.4082H80.536L74.6 31.0362L68.72 39.4082H65.836L73.284 29.0482L66.032 18.9122H69.112L74.824 27.0602L80.508 18.9122H83.392ZM101.822 18.9122L94.5697 29.0202L102.018 39.4082H98.9657L93.0297 31.0362L87.1497 39.4082H84.2657L91.7137 29.0482L84.4617 18.9122H87.5417L93.2537 27.0602L98.9377 18.9122H101.822ZM120.251 18.9122L112.999 29.0202L120.447 39.4082H117.395L111.459 31.0362L105.579 39.4082H102.695L110.143 29.0482L102.891 18.9122H105.971L111.683 27.0602L117.367 18.9122H120.251ZM135.825 15.9442C137.094 17.9042 138.756 19.6962 140.809 21.3202C142.862 22.9442 145.214 24.3535 147.865 25.5482L146.717 27.7602C144.925 26.8829 143.273 25.9309 141.761 24.9042V26.6682H127.677V24.9882C126.184 26.0149 124.541 26.9669 122.749 27.8442L121.713 25.5762C124.27 24.4002 126.622 22.9535 128.769 21.2362C130.916 19.5189 132.568 17.7549 133.725 15.9442H135.825ZM141.229 24.5402C138.597 22.6735 136.45 20.6109 134.789 18.3522C133.202 20.5175 131.046 22.5802 128.321 24.5402H141.229ZM127.985 41.2282H125.661V30.0002H143.777V41.2562H141.509V39.8002H127.985V41.2282ZM141.509 37.7002V32.1002H127.985V37.7002H141.509ZM173.625 37.6162C173.625 38.5309 173.532 39.2402 173.345 39.7442C173.177 40.2482 172.878 40.6029 172.449 40.8082C172.038 41.0322 171.45 41.1535 170.685 41.1722C169.546 41.1909 168.408 41.1349 167.269 41.0042L166.849 38.9042C167.764 39.0535 168.8 39.1282 169.957 39.1282C170.517 39.1282 170.89 38.9789 171.077 38.6802C171.282 38.3629 171.385 37.8122 171.385 37.0282V19.5562H153.829V41.2282H151.617V17.4562H173.625V37.6162ZM155.593 22.6082H169.649V24.6242H155.593V22.6082ZM157.245 27.5922H167.969V36.5242H157.245V27.5922ZM159.289 29.5242V34.5922H165.925V29.5242H159.289Z\" fill=\"#101010\"/>\n<path d=\"M24.3077 75.24H39.3677V86.96H32.6477V92.42H30.9877V86.96H24.3077V75.24ZM30.9877 80.24V76.86H25.9277V80.24H30.9877ZM32.6477 80.24H37.7077V76.86H32.6477V80.24ZM25.9277 81.82V85.34H30.9877V81.82H25.9277ZM32.6477 81.82V85.34H37.7077V81.82H32.6477ZM49.9277 78.56L49.7877 81.18L49.7477 81.64H58.3477C58.3343 82.7333 58.2677 84.02 58.1477 85.5C58.041 86.9667 57.921 88.0667 57.7877 88.8C57.6277 89.6667 57.421 90.32 57.1677 90.76C56.9277 91.1867 56.5677 91.4867 56.0877 91.66C55.6077 91.8467 54.9077 91.94 53.9877 91.94C53.1877 91.94 52.241 91.8933 51.1477 91.8L50.8277 90.24C51.9343 90.3733 52.9277 90.44 53.8077 90.44C54.3543 90.44 54.7743 90.38 55.0677 90.26C55.361 90.14 55.5877 89.9267 55.7477 89.62C55.9077 89.3133 56.0477 88.8467 56.1677 88.22C56.4343 86.86 56.581 85.2067 56.6077 83.26H49.5877C49.401 84.5933 49.1077 85.78 48.7077 86.82C48.321 87.8467 47.781 88.8267 47.0877 89.76C46.3943 90.68 45.481 91.64 44.3477 92.64L43.1077 91.26C44.3343 90.22 45.2877 89.24 45.9677 88.32C46.6477 87.3867 47.1477 86.3533 47.4677 85.22C47.801 84.0867 48.0277 82.6667 48.1477 80.96C48.1877 80.4 48.221 79.6 48.2477 78.56H42.9877V76.98H51.5077C50.961 75.6467 50.601 74.8133 50.4277 74.48L52.0277 73.94L53.1477 76.46L53.3677 76.98H60.7677V78.56H49.9277ZM65.9477 82.7C65.641 82.7 65.3743 82.5867 65.1477 82.36C64.9343 82.1333 64.8277 81.86 64.8277 81.54C64.8277 81.22 64.9343 80.9467 65.1477 80.72C65.3743 80.4933 65.641 80.38 65.9477 80.38C66.2677 80.38 66.5343 80.4933 66.7477 80.72C66.9743 80.9467 67.0877 81.22 67.0877 81.54C67.0877 81.86 66.9743 82.1333 66.7477 82.36C66.5343 82.5867 66.2677 82.7 65.9477 82.7ZM65.9477 91.22C65.641 91.22 65.3743 91.1067 65.1477 90.88C64.9343 90.6533 64.8277 90.38 64.8277 90.06C64.8277 89.74 64.9343 89.4667 65.1477 89.24C65.3743 89.0133 65.641 88.9 65.9477 88.9C66.2677 88.9 66.5343 89.0133 66.7477 89.24C66.9743 89.4667 67.0877 89.74 67.0877 90.06C67.0877 90.38 66.9743 90.6533 66.7477 90.88C66.5343 91.1067 66.2677 91.22 65.9477 91.22Z\" fill=\"#6A6A6A\"/>\n<path d=\"M75.5 85H139.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5077 130C23.5077 129.387 23.741 128.74 24.2077 128.06C24.6743 127.38 25.7277 126.307 27.3677 124.84C29.021 123.36 31.6277 121.18 35.1877 118.3H24.5477V116.68H37.9677V118.28C34.4743 121.013 31.801 123.18 29.9477 124.78C28.0943 126.38 26.861 127.54 26.2477 128.26C25.6477 128.967 25.3477 129.473 25.3477 129.78C25.3477 130.06 25.3943 130.273 25.4877 130.42C25.581 130.567 25.7543 130.687 26.0077 130.78C26.2743 130.86 26.6677 130.92 27.1877 130.96C28.721 131.08 30.2677 131.14 31.8277 131.14C33.3743 131.14 34.9143 131.08 36.4477 130.96C36.9543 130.92 37.341 130.847 37.6077 130.74C37.8743 130.62 38.0677 130.433 38.1877 130.18C38.321 129.927 38.4077 129.56 38.4477 129.08C38.501 128.253 38.5477 127.193 38.5877 125.9L40.2877 126.36L40.2077 127.54C40.1543 128.487 40.1077 129.207 40.0677 129.7C40.041 130.38 39.9077 130.913 39.6677 131.3C39.4277 131.687 39.0677 131.973 38.5877 132.16C38.1077 132.347 37.4543 132.473 36.6277 132.54C34.9877 132.66 33.3343 132.72 31.6677 132.72C30.0277 132.72 28.3743 132.66 26.7077 132.54C24.5743 132.393 23.5077 131.547 23.5077 130ZM49.9277 119.56L49.7877 122.18L49.7477 122.64H58.3477C58.3343 123.733 58.2677 125.02 58.1477 126.5C58.041 127.967 57.921 129.067 57.7877 129.8C57.6277 130.667 57.421 131.32 57.1677 131.76C56.9277 132.187 56.5677 132.487 56.0877 132.66C55.6077 132.847 54.9077 132.94 53.9877 132.94C53.1877 132.94 52.241 132.893 51.1477 132.8L50.8277 131.24C51.9343 131.373 52.9277 131.44 53.8077 131.44C54.3543 131.44 54.7743 131.38 55.0677 131.26C55.361 131.14 55.5877 130.927 55.7477 130.62C55.9077 130.313 56.0477 129.847 56.1677 129.22C56.4343 127.86 56.581 126.207 56.6077 124.26H49.5877C49.401 125.593 49.1077 126.78 48.7077 127.82C48.321 128.847 47.781 129.827 47.0877 130.76C46.3943 131.68 45.481 132.64 44.3477 133.64L43.1077 132.26C44.3343 131.22 45.2877 130.24 45.9677 129.32C46.6477 128.387 47.1477 127.353 47.4677 126.22C47.801 125.087 48.0277 123.667 48.1477 121.96C48.1877 121.4 48.221 120.6 48.2477 119.56H42.9877V117.98H51.5077C50.961 116.647 50.601 115.813 50.4277 115.48L52.0277 114.94L53.1477 117.46L53.3677 117.98H60.7677V119.56H49.9277ZM65.9477 123.7C65.641 123.7 65.3743 123.587 65.1477 123.36C64.9343 123.133 64.8277 122.86 64.8277 122.54C64.8277 122.22 64.9343 121.947 65.1477 121.72C65.3743 121.493 65.641 121.38 65.9477 121.38C66.2677 121.38 66.5343 121.493 66.7477 121.72C66.9743 121.947 67.0877 122.22 67.0877 122.54C67.0877 122.86 66.9743 123.133 66.7477 123.36C66.5343 123.587 66.2677 123.7 65.9477 123.7ZM65.9477 132.22C65.641 132.22 65.3743 132.107 65.1477 131.88C64.9343 131.653 64.8277 131.38 64.8277 131.06C64.8277 130.74 64.9343 130.467 65.1477 130.24C65.3743 130.013 65.641 129.9 65.9477 129.9C66.2677 129.9 66.5343 130.013 66.7477 130.24C66.9743 130.467 67.0877 130.74 67.0877 131.06C67.0877 131.38 66.9743 131.653 66.7477 131.88C66.5343 132.107 66.2677 132.22 65.9477 132.22Z\" fill=\"#6A6A6A\"/>\n<path d=\"M75.5 126H139.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n</svg>", "general": "<svg width=\"242\" height=\"238\" viewBox=\"0 0 242 238\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<rect width=\"242\" height=\"238\" rx=\"16\" fill=\"white\"/>\n<path d=\"M91.372 41.3682C88.796 40.4535 86.5373 39.5015 84.596 38.5122C82.6547 37.5042 80.956 36.4029 79.5 35.2082C78.0627 36.4029 76.3827 37.5135 74.46 38.5402C72.5373 39.5669 70.2973 40.5562 67.74 41.5082L66.648 39.2402C69.1307 38.3815 71.2773 37.5042 73.088 36.6082C74.9173 35.7122 76.4947 34.7322 77.82 33.6682C76.3827 32.2309 75.1693 30.5975 74.18 28.7682C73.2093 26.9202 72.388 24.7922 71.716 22.3842H67.264V20.1722H78.688C78.3147 18.9029 77.8947 17.6615 77.428 16.4482L79.724 15.8322C80.228 17.1575 80.7133 18.6042 81.18 20.1722H91.652V22.3842H87.116C86.4813 24.8109 85.688 26.9389 84.736 28.7682C83.784 30.5975 82.5893 32.2402 81.152 33.6962C83.8213 35.8055 87.5453 37.5882 92.324 39.0442L91.372 41.3682ZM74.124 22.3842C74.684 24.4562 75.3933 26.2949 76.252 27.9002C77.1107 29.5055 78.184 30.9429 79.472 32.2122C80.7227 30.9429 81.7587 29.5149 82.58 27.9282C83.42 26.3229 84.1013 24.4749 84.624 22.3842H74.124ZM110.832 15.9162H112.932V24.9882H118.98V41.4522H116.852V39.8002H103.972V37.6442H116.852V33.3322H105.064V31.1762H116.852V27.2002H104.476V24.9882H110.832V15.9162ZM102.572 31.1482C101.993 30.1589 101.34 29.1135 100.612 28.0122V41.4242H98.484V28.4042C97.6813 30.6815 96.7013 32.7722 95.544 34.6762L94.452 32.4922C95.236 31.1669 95.992 29.6829 96.72 28.0402C97.448 26.3975 98.0267 24.8389 98.456 23.3642H95.18V21.2082H98.484V16.0002H100.612V21.2082H103.972V23.3642H100.612V25.4082C101.788 26.6962 102.964 28.0869 104.14 29.5802L102.572 31.1482ZM107.808 23.8962C107.397 23.1682 106.809 22.2255 106.044 21.0682C105.297 19.9109 104.663 18.9869 104.14 18.2962L105.932 17.0642C106.343 17.5682 106.959 18.4455 107.78 19.6962C108.601 20.9282 109.236 21.9269 109.684 22.6922L107.808 23.8962ZM119.624 18.2122C119.176 19.0522 118.579 20.0695 117.832 21.2642C117.085 22.4589 116.451 23.3922 115.928 24.0642L114.052 22.8602C114.649 22.1135 115.312 21.1709 116.04 20.0322C116.768 18.8749 117.328 17.9135 117.72 17.1482L119.624 18.2122ZM131.468 31.7922C130.703 30.6535 129.853 29.4962 128.92 28.3202V41.5082H126.68V28.4322C125.541 31.2882 124.421 33.5375 123.32 35.1802L122.2 32.8842C123.096 31.5962 123.936 30.1402 124.72 28.5162C125.504 26.8735 126.157 25.2029 126.68 23.5042V23.3082H123.18V21.2642H126.68V16.1962H128.92V21.2642H132.14V23.3082H128.92V25.6882C129.424 26.1922 130.096 26.9202 130.936 27.8722C131.795 28.8055 132.448 29.5522 132.896 30.1122L131.468 31.7922ZM133.876 17.7362H147.036V19.8082H133.876V17.7362ZM148.296 26.6682H141.38V38.4562C141.38 39.5202 141.128 40.2389 140.624 40.6122C140.12 41.0042 139.271 41.2002 138.076 41.2002C137.329 41.2002 136.573 41.1442 135.808 41.0322L135.332 38.9042C136.303 39.0722 137.133 39.1562 137.824 39.1562C138.347 39.1562 138.692 39.0349 138.86 38.7922C139.028 38.5495 139.112 38.1109 139.112 37.4762V26.6682H132.588V24.5962H148.296V26.6682ZM137.068 29.8042C136.601 31.2415 135.967 32.8562 135.164 34.6482C134.361 36.4402 133.587 37.9709 132.84 39.2402L130.936 38.1482C131.72 36.8789 132.495 35.3949 133.26 33.6962C134.025 31.9789 134.641 30.4015 135.108 28.9642L137.068 29.8042ZM144.684 29.0202C145.244 30.0842 145.944 31.5775 146.784 33.5002C147.643 35.4229 148.296 36.9909 148.744 38.2042L146.7 39.0722C146.271 37.8589 145.645 36.2909 144.824 34.3682C144.021 32.4269 143.359 30.9335 142.836 29.8882L144.684 29.0202ZM152.44 17.1202H161.848V26.5282H152.44V17.1202ZM168.036 21.6002L168.596 19.3322H163.444V17.4282H175.708V19.3322H170.668C170.537 19.9109 170.341 20.6669 170.08 21.6002H174.784V31.9042H172.768V23.3362H166.356V31.9322H164.452V21.6002H168.036ZM159.776 20.9842V18.8282H154.456V20.9842H159.776ZM154.456 22.6642V24.8202H159.776V22.6642H154.456ZM170.528 24.6242V29.1322C170.528 30.1589 170.435 31.0455 170.248 31.7922C172.675 33.6962 174.467 35.1335 175.624 36.1042L174.28 37.7842C173.141 36.7575 171.545 35.3855 169.492 33.6682C169.025 34.4895 168.353 35.2362 167.476 35.9082C166.599 36.5615 165.441 37.2242 164.004 37.8962L162.996 36.1602C164.583 35.4135 165.777 34.7042 166.58 34.0322C167.383 33.3415 167.924 32.5949 168.204 31.7922C168.484 30.9895 168.624 29.9629 168.624 28.7122V24.6242H170.528ZM160.812 38.6802C163.519 38.9789 166.309 39.1282 169.184 39.1282C171.555 39.1282 173.916 39.0442 176.268 38.8762L175.82 40.9202C173.841 41.0509 171.685 41.1162 169.352 41.1162C166.253 41.1162 163.397 40.9762 160.784 40.6962C159.235 40.5282 157.937 40.0989 156.892 39.4082C155.865 38.6989 154.913 37.6535 154.036 36.2722C153.831 37.1682 153.56 38.0175 153.224 38.8202C152.888 39.6229 152.459 40.4629 151.936 41.3402L150.172 40.3322C151.031 38.9882 151.637 37.6815 151.992 36.4122C152.365 35.1429 152.627 33.4909 152.776 31.4562L154.68 31.5682C154.643 32.1655 154.568 32.8655 154.456 33.6682C154.96 34.4895 155.399 35.1709 155.772 35.7122C156.164 36.2535 156.584 36.7109 157.032 37.0842V30.4762H151.096V28.5442H163.136V30.4762H158.992V33.1922H162.632V35.0402H158.992V38.2602C159.515 38.4469 160.121 38.5869 160.812 38.6802Z\" fill=\"#101010\"/>\n<path d=\"M107.66 68.58H109.2V83.78C109.2 84.4467 109.113 84.9467 108.94 85.28C108.78 85.6267 108.493 85.86 108.08 85.98C107.68 86.1133 107.087 86.18 106.3 86.18C105.94 86.18 105.387 86.1533 104.64 86.1L104.32 84.5C105.067 84.5933 105.707 84.64 106.24 84.64C106.64 84.64 106.94 84.6067 107.14 84.54C107.34 84.46 107.473 84.32 107.54 84.12C107.62 83.92 107.66 83.62 107.66 83.22V68.58ZM102.88 70.78H92.18V69.34H102.88V70.78ZM104 70.38H105.52V81.72H104V70.38ZM93.42 76.36V72.18H101.66V76.36H93.42ZM94.86 75.1H100.2V73.46H94.86V75.1ZM102.38 86.08H92.66V77.68H102.38V86.08ZM96.86 81.06V78.94H94.12V81.06H96.86ZM98.28 81.06H100.92V78.94H98.28V81.06ZM96.86 82.36H94.12V84.76H96.86V82.36ZM98.28 82.36V84.76H100.92V82.36H98.28ZM118.12 79.56C117.573 78.7467 116.967 77.92 116.3 77.08V86.5H114.7V77.16C113.887 79.2 113.087 80.8067 112.3 81.98L111.5 80.34C112.14 79.42 112.74 78.38 113.3 77.22C113.86 76.0467 114.327 74.8533 114.7 73.64V73.5H112.2V72.04H114.7V68.42H116.3V72.04H118.6V73.5H116.3V75.2C116.66 75.56 117.14 76.08 117.74 76.76C118.353 77.4267 118.82 77.96 119.14 78.36L118.12 79.56ZM119.84 69.52H129.24V71H119.84V69.52ZM130.14 75.9H125.2V84.32C125.2 85.08 125.02 85.5933 124.66 85.86C124.3 86.14 123.693 86.28 122.84 86.28C122.307 86.28 121.767 86.24 121.22 86.16L120.88 84.64C121.573 84.76 122.167 84.82 122.66 84.82C123.033 84.82 123.28 84.7333 123.4 84.56C123.52 84.3867 123.58 84.0733 123.58 83.62V75.9H118.92V74.42H130.14V75.9ZM122.12 78.14C121.787 79.1667 121.333 80.32 120.76 81.6C120.187 82.88 119.633 83.9733 119.1 84.88L117.74 84.1C118.3 83.1933 118.853 82.1333 119.4 80.92C119.947 79.6933 120.387 78.5667 120.72 77.54L122.12 78.14ZM127.56 77.58C127.96 78.34 128.46 79.4067 129.06 80.78C129.673 82.1533 130.14 83.2733 130.46 84.14L129 84.76C128.693 83.8933 128.247 82.7733 127.66 81.4C127.087 80.0133 126.613 78.9467 126.24 78.2L127.56 77.58ZM133.1 69.08H139.82V75.8H133.1V69.08ZM144.24 72.28L144.64 70.66H140.96V69.3H149.72V70.66H146.12C146.027 71.0733 145.887 71.6133 145.7 72.28H149.06V79.64H147.62V73.52H143.04V79.66H141.68V72.28H144.24ZM138.34 71.84V70.3H134.54V71.84H138.34ZM134.54 73.04V74.58H138.34V73.04H134.54ZM146.02 74.44V77.66C146.02 78.3933 145.953 79.0267 145.82 79.56C147.553 80.92 148.833 81.9467 149.66 82.64L148.7 83.84C147.887 83.1067 146.747 82.1267 145.28 80.9C144.947 81.4867 144.467 82.02 143.84 82.5C143.213 82.9667 142.387 83.44 141.36 83.92L140.64 82.68C141.773 82.1467 142.627 81.64 143.2 81.16C143.773 80.6667 144.16 80.1333 144.36 79.56C144.56 78.9867 144.66 78.2533 144.66 77.36V74.44H146.02ZM139.08 84.48C141.013 84.6933 143.007 84.8 145.06 84.8C146.753 84.8 148.44 84.74 150.12 84.62L149.8 86.08C148.387 86.1733 146.847 86.22 145.18 86.22C142.967 86.22 140.927 86.12 139.06 85.92C137.953 85.8 137.027 85.4933 136.28 85C135.547 84.4933 134.867 83.7467 134.24 82.76C134.093 83.4 133.9 84.0067 133.66 84.58C133.42 85.1533 133.113 85.7533 132.74 86.38L131.48 85.66C132.093 84.7 132.527 83.7667 132.78 82.86C133.047 81.9533 133.233 80.7733 133.34 79.32L134.7 79.4C134.673 79.8267 134.62 80.3267 134.54 80.9C134.9 81.4867 135.213 81.9733 135.48 82.36C135.76 82.7467 136.06 83.0733 136.38 83.34V78.62H132.14V77.24H140.74V78.62H137.78V80.56H140.38V81.88H137.78V84.18C138.153 84.3133 138.587 84.4133 139.08 84.48Z\" fill=\"#6A6A6A\"/>\n<path d=\"M31.5 189H211.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M41.5 207H201.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 123H53.5\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23 141L219 141\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n<path d=\"M23.5 171L219.5 171\" stroke-width=\"5.04167\" stroke=\"#DFDFDF\"/>\n</svg>"};
const MOBILE_AI_TYPESET_CLOSE = "<svg width=\"80\" height=\"80\" viewBox=\"0 0 80 80\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<g clip-path=\"url(#clip0_330_34786)\">\n<path d=\"M55.5595 26.56C55.8395 26.24 55.9795 25.88 55.9795 25.48C55.9795 25.08 55.8395 24.7333 55.5595 24.44C55.2662 24.12 54.9129 23.96 54.4995 23.96C54.0862 23.96 53.7329 24.12 53.4395 24.44L39.9995 37.88L26.5595 24.44C26.2662 24.12 25.9129 23.96 25.4995 23.96C25.0862 23.96 24.7195 24.12 24.3995 24.44C24.1462 24.7333 24.0195 25.08 24.0195 25.48C24.0195 25.88 24.1462 26.24 24.3995 26.56L37.8795 40L24.3995 53.4C24.1462 53.72 24.0195 54.0866 24.0195 54.5C24.0195 54.9133 24.1462 55.2666 24.3995 55.56C24.7195 55.8133 25.0862 55.9466 25.4995 55.96C25.9129 55.9733 26.2662 55.84 26.5595 55.56L39.9995 42.12L53.4395 55.56C53.7329 55.84 54.0862 55.98 54.4995 55.98C54.9129 55.98 55.2662 55.84 55.5595 55.56C55.8395 55.2666 55.9795 54.9133 55.9795 54.5C55.9795 54.0866 55.8395 53.72 55.5595 53.4L42.1195 40L55.5595 26.56Z\" fill=\"black\" fill-opacity=\"0.901961\"/>\n</g>\n<defs>\n<clipPath id=\"clip0_330_34786\">\n<rect width=\"48\" height=\"48\" fill=\"white\" transform=\"translate(16 16)\"/>\n</clipPath>\n</defs>\n</svg>";
const MOBILE_AI_TYPESET_SPARKLE = "<svg width=\"40\" height=\"40\" viewBox=\"0 0 40 40\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M21.6123 7.86523C21.7032 11.2907 23.1059 14.5532 25.5312 16.9785C28.0425 19.49 31.4484 20.9011 35 20.9014V24.1074H34.9912V24.1182C31.4368 24.1182 28.0316 25.5321 25.5225 28.041C23.0234 30.5401 21.6095 33.9456 21.6094 37.5H18.3916C18.3916 33.9454 16.9779 30.5394 14.4688 28.0303C11.9667 25.5281 8.57646 24.118 5.03906 24.1074C5.0293 24.1074 5.01953 24.1084 5.00977 24.1084C5.00651 24.1084 5.00326 24.1074 5 24.1074V20.8916C5.00326 20.8916 5.00651 20.8916 5.00977 20.8916C8.56436 20.8916 11.9694 19.4779 14.4785 16.9688C16.9052 14.5423 18.3035 11.2807 18.3955 7.85938C18.3931 7.74313 18.3916 7.62659 18.3916 7.50977H18.4014C18.4014 7.50651 18.4014 7.50326 18.4014 7.5H21.6182C21.618 7.62207 21.615 7.7438 21.6123 7.86523Z\" fill=\"white\"/>\n<path d=\"M33.3057 0.0825639C33.3261 1.83153 34.0297 3.50329 35.2659 4.73944C36.5214 5.99492 38.2244 6.70076 40 6.70099V8.303H39.9947V8.30833C38.2176 8.30838 36.5151 9.0156 35.2606 10.2699C34.011 11.5195 33.3043 13.2227 33.3043 15H31.6957L31.6877 14.6671C31.6054 13.011 30.9102 11.4407 29.7341 10.2646C28.4848 9.01531 26.7927 8.31017 25.0266 8.303C25.0195 8.30302 25.0124 8.30433 25.0053 8.30433C25.0036 8.30433 25.0018 8.303 25 8.303V6.69567C25.0018 6.69567 25.0036 6.69566 25.0053 6.69567L25.3369 6.68768C26.9931 6.60542 28.5633 5.91029 29.7394 4.73411C30.9689 3.50458 31.6682 1.84622 31.697 0.110529C31.6966 0.0751531 31.6957 0.0394767 31.6957 0.00399503H31.701V0H33.3083C33.3083 0.0275626 33.306 0.0550652 33.3057 0.0825639Z\" fill=\"white\"/>\n</svg>";
