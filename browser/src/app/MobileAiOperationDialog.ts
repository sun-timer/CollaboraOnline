/*
 * Android-style Writer text operation dialog.
 */

class MobileAiOperationDialog {
	private readonly taskType: string;
	private readonly controller: WriterAiController;
	private readonly sheet: MobileAiSheet;
	private readonly requirement?: HTMLInputElement;
	private readonly style?: HTMLSelectElement;
	private readonly outlineType?: HTMLSelectElement;
	private readonly outlineDescription?: HTMLTextAreaElement;
	private readonly articleTemplate?: HTMLSelectElement;
	private readonly articleValues: HTMLInputElement[] = [];
	private readonly articleForm?: HTMLDivElement;
	private readonly imageThumb?: HTMLImageElement;
	private pendingImage = '';
	private lastGeneratedImage = '';
	private readonly preview: HTMLDivElement;
	private readonly status: HTMLDivElement;
	private readonly generateButton: HTMLButtonElement;
	private readonly stopButton: HTMLButtonElement;
	private readonly copyButton: HTMLButtonElement;
	private readonly regenerateButton: HTMLButtonElement;
	private readonly applyButton: HTMLButtonElement;
	private readonly unsubscribe: () => void;

	constructor(taskType: string) {
		this.taskType = taskType;
		this.controller = WriterAiController.getInstance();
		const entry = MobileAiUiCatalog.getEntry(taskType);
		this.sheet = new MobileAiSheet({ title: entry?.label || 'AI 文案处理' });
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;min-height:300px;';

		if (taskType === 'polish') {
			this.style = document.createElement('select');
			this.style.setAttribute('aria-label', '润色风格');
			const labels: { [key: string]: string } = {
				quick: '快速润色',
				formal: '更正式',
				lively: '更活泼',
				party_govt: '党政风',
				colloquial: '口语化',
				academic: '更学术',
				internet: '网络话术',
			};
			WriterAiCatalog.POLISH_STYLES.forEach((style) => {
				const option = document.createElement('option');
				option.value = style;
				option.textContent = labels[style] || style;
				this.style?.appendChild(option);
			});
			this.style.value = WriterAiCatalog.DEFAULT_POLISH_STYLE;
			content.appendChild(this.style);
		}

		if (
			taskType === 'expand' ||
			taskType === 'condense' ||
			taskType === 'rewrite'
		) {
			this.requirement = document.createElement('input');
			this.requirement.type = 'text';
			this.requirement.placeholder = '额外要求（可选）';
			this.requirement.setAttribute('aria-label', '额外要求');
			this.requirement.style.width = '100%';
			content.appendChild(this.requirement);
		}

		if (taskType === 'outline') {
			this.outlineType = document.createElement('select');
			this.outlineType.setAttribute('aria-label', '大纲类型');
			WriterAiCatalog.OUTLINE_TYPES.forEach((item) => {
				const option = document.createElement('option');
				option.value = item.key;
				option.textContent = item.label;
				this.outlineType?.appendChild(option);
			});

			this.outlineType.value = 'general';
			content.appendChild(this.outlineType);
			this.outlineDescription = document.createElement('textarea');
			this.outlineDescription.rows = 4;
			this.outlineDescription.placeholder = '补充说明（可选）';
			this.outlineDescription.setAttribute('aria-label', '大纲补充说明');
			this.outlineDescription.style.cssText =
				'width:100%;box-sizing:border-box;resize:vertical;';
			content.appendChild(this.outlineDescription);
		}

		if (taskType === 'article_generate') {
			this.articleTemplate = document.createElement('select');
			this.articleTemplate.setAttribute('aria-label', '文案类型');
			Object.keys(WriterAiCatalog.ARTICLE_TEMPLATES).forEach((key) => {
				const option = document.createElement('option');
				option.value = key;
				const item = WriterAiCatalog.ARTICLE_TEMPLATES[key];
				option.textContent = `${item.category} / ${key}`;
				this.articleTemplate?.appendChild(option);
			});
			this.articleTemplate.onchange = () => this.renderArticleForm();
			content.appendChild(this.articleTemplate);
			this.articleForm = document.createElement('div');
			this.articleForm.style.cssText =
				'display:flex;flex-direction:column;gap:8px;';
			content.appendChild(this.articleForm);
			this.renderArticleForm();
		}

		if (taskType === 'text_extract') {
			const pick = document.createElement('button');
			pick.type = 'button';
			pick.textContent = '选择图片';
			pick.onclick = () => this.pickImage();
			content.appendChild(pick);
			const thumb = document.createElement('img');
			thumb.style.cssText =
				'max-width:100%;max-height:160px;object-fit:contain;' +
				'display:none;border-radius:8px;';
			content.appendChild(thumb);
			this.imageThumb = thumb;
		}

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		content.appendChild(this.status);

		this.preview = document.createElement('div');
		this.preview.setAttribute('aria-live', 'polite');
		this.preview.style.cssText =
			'min-height:160px;max-height:42dvh;overflow:auto;padding:16px;' +
			'border:1px solid #d8dde3;border-radius:8px;line-height:1.6;';
		content.appendChild(this.preview);

		const inputActions = document.createElement('div');
		inputActions.style.cssText = 'display:flex;gap:8px;';
		this.generateButton = this.createButton(
			taskType === 'text_extract' ? '提取文字' : '开始生成',
		);
		this.generateButton.onclick = () => this.request();
		inputActions.appendChild(this.generateButton);
		this.stopButton = this.createButton('停止生成');
		this.stopButton.onclick = () => this.controller.cancel();
		inputActions.appendChild(this.stopButton);
		content.appendChild(inputActions);

		const resultActions = document.createElement('div');
		resultActions.style.cssText = 'display:flex;gap:8px;';
		this.copyButton = this.createButton('复制');
		this.copyButton.onclick = () => this.controller.copy();
		resultActions.appendChild(this.copyButton);
		this.regenerateButton = this.createButton('重新生成');
		this.regenerateButton.onclick = () => this.controller.regenerate();
		resultActions.appendChild(this.regenerateButton);
		this.applyButton = this.createButton('插入文档');
		this.applyButton.onclick = () =>
			this.controller.accept(MobileAiResultRenderer.toHtml(this.controller.getState().preview));
		this.regenerateButton = this.createButton('重新生成');
		this.regenerateButton.onclick = () => this.regenerate();
		this.sheet.setBody(content);
		this.unsubscribe = this.controller.subscribe(() => this.render());
	}

	open(): void {
		this.sheet.open();
		const state = this.controller.getState();
		if (this.taskType === 'continue' && state.state !== 'ready') {
			this.request();
		}
		this.render();
	}

	private renderArticleForm(): void {
		const item = WriterAiCatalog.getArticleTemplate(
			this.articleTemplate?.value || '',
		);
		if (!this.articleForm || !item) {
			return;
		}
		this.articleForm.replaceChildren();
		this.articleValues.length = 0;
		item.variables.forEach((label) => {
			const input = document.createElement('input');
			input.type = 'text';
			input.placeholder = label;
			input.setAttribute('aria-label', label);
			this.articleForm?.appendChild(input);
			this.articleValues.push(input);
		});
	}

	private pickImage(): void {
		const input = document.createElement('input');
		input.type = 'file';
		input.accept = 'image/*';
		input.onchange = () => {
			const file = input.files && input.files[0];
			if (!file) {
				return;
			}
			const reader = new FileReader();
			reader.onload = () => {
				const bytes = new Uint8Array(reader.result as ArrayBuffer);
				let binary = '';
				for (let i = 0; i < bytes.length; i++) {
					binary += String.fromCharCode(bytes[i]);
				}
				this.pendingImage = window.btoa(binary);
				if (this.imageThumb) {
					this.imageThumb.src = 'data:image/png;base64,' + this.pendingImage;
					this.imageThumb.style.display = 'block';
				}
				this.render();
			};
			reader.readAsArrayBuffer(file);
		};
		input.click();
	}

	close(): void {
		this.controller.cancel();
		this.unsubscribe();
		this.sheet.close();
	}

	private request(): void {
		const context: { [key: string]: any } = {};
		if (this.taskType === 'polish') {
			context.polishStyle =
				this.style?.value || WriterAiCatalog.DEFAULT_POLISH_STYLE;
		} else if (
			this.taskType === 'expand' ||
			this.taskType === 'condense' ||
			this.taskType === 'rewrite'
		) {
			context.requirement = this.requirement?.value || '';
		} else if (this.taskType === 'outline') {
			context.outlineType = this.outlineType?.value || 'general';
			context.requirement = this.outlineDescription?.value.trim() || '';
		} else if (this.taskType === 'article_generate') {
			context.template = this.articleTemplate?.value || '';
			context.variables = this.articleValues.map((input) => input.value.trim());
		} else if (this.taskType === 'text_extract') {
			this.lastGeneratedImage = this.pendingImage;
			this.controller.request(
				'text_extract',
				{},
				undefined,
				this.pendingImage ? [this.pendingImage] : undefined,
			);
			return;
		}
		this.controller.request(this.taskType, context);
	}

	private regenerate(): void {
		if (
			this.taskType === 'text_extract' &&
			this.pendingImage !== this.lastGeneratedImage
		) {
			// 重选图后 controller 仍持有旧图,replay 会回放过期结果;
			// 直接按当前图发起新请求。
			this.request();
			return;
		}
		this.controller.regenerate();
	}

	private render(): void {
		const state = this.controller.getState();
		MobileAiResultRenderer.renderInto(this.preview, state.preview);
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		this.generateButton.disabled =
			active ||
			(this.taskType === 'text_extract' && !this.pendingImage);
		this.stopButton.disabled = !active;
		this.copyButton.disabled = !ready;
		this.regenerateButton.disabled = !ready;
		this.applyButton.disabled = !ready;
		this.status.textContent =
			state.error ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出...'
					: 'AI 正在生成...'
				: ready
					? '生成完成'
					: '');
	}

	private createButton(label: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		return button;
	}
}
