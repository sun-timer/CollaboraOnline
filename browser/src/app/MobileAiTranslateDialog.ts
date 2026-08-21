/*
 * Android-style translation dialog with editable source and language swap.
 */

class MobileAiTranslateDialog {
	private readonly controller: WriterAiController;
	private readonly sheet: MobileAiSheet;
	private readonly sourceLanguage: HTMLSelectElement;
	private readonly targetLanguage: HTMLSelectElement;
	private readonly sourceText: HTMLTextAreaElement;
	private readonly result: HTMLDivElement;
	private readonly status: HTMLDivElement;
	private readonly generateButton: HTMLButtonElement;
	private readonly stopButton: HTMLButtonElement;
	private readonly copyButton: HTMLButtonElement;
	private readonly regenerateButton: HTMLButtonElement;
	private readonly applyButton: HTMLButtonElement;
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = WriterAiController.getInstance();
		this.sheet = new MobileAiSheet({ title: 'AI 翻译' });
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:10px;';

		const languageRow = document.createElement('div');
		languageRow.style.cssText = 'display:flex;gap:8px;align-items:center;';
		this.sourceLanguage = this.createLanguageSelect('源语言');
		this.targetLanguage = this.createLanguageSelect('目标语言');
		const swapButton = this.createButton('交换');
		swapButton.onclick = () => this.swapLanguages();
		languageRow.appendChild(this.sourceLanguage);
		languageRow.appendChild(swapButton);
		languageRow.appendChild(this.targetLanguage);
		content.appendChild(languageRow);

		this.sourceText = document.createElement('textarea');
		this.sourceText.rows = 6;
		this.sourceText.placeholder = '请输入或编辑原文';
		this.sourceText.setAttribute('aria-label', '原文');
		this.sourceText.style.cssText = 'width:100%;box-sizing:border-box;resize:vertical;';
		content.appendChild(this.sourceText);

		this.result = document.createElement('div');
		this.result.style.cssText =
			'min-height:140px;max-height:32dvh;overflow:auto;padding:16px;' +
			'border:1px solid #d8dde3;border-radius:8px;line-height:1.6;';
		this.result.setAttribute('aria-live', 'polite');
		content.appendChild(this.result);

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		content.appendChild(this.status);

		const requestRow = document.createElement('div');
		requestRow.style.cssText = 'display:flex;gap:8px;';
		this.generateButton = this.createButton('开始翻译');
		this.generateButton.onclick = () => this.request();
		requestRow.appendChild(this.generateButton);
		this.stopButton = this.createButton('停止生成');
		this.stopButton.onclick = () => this.controller.cancel();
		requestRow.appendChild(this.stopButton);
		content.appendChild(requestRow);

		const resultRow = document.createElement('div');
		resultRow.style.cssText = 'display:flex;gap:8px;';
		this.copyButton = this.createButton('复制');
		this.copyButton.onclick = () => this.controller.copy();
		resultRow.appendChild(this.copyButton);
		this.regenerateButton = this.createButton('重新生成');
		this.regenerateButton.onclick = () => this.request();
		resultRow.appendChild(this.regenerateButton);
		this.applyButton = this.createButton('插入文档');
		this.applyButton.onclick = () =>
			this.controller.accept(MobileAiResultRenderer.toHtml(this.controller.getState().preview));
		resultRow.appendChild(this.applyButton);
		content.appendChild(resultRow);

		this.sheet.setBody(content);
		this.unsubscribe = this.controller.subscribe(() => this.render());
	}

	open(): void {
		this.sourceText.value = MobileAiBridge.getInstance().getSelectedText();
		this.targetLanguage.value = WriterAiCatalog.DEFAULT_TARGET_LANGUAGE;
		this.sourceLanguage.value = WriterAiCatalog.DEFAULT_SOURCE_LANGUAGE;
		this.sheet.open();
		this.render();
	}

	close(): void {
		this.controller.cancel();
		this.unsubscribe();
		this.sheet.close();
	}

	private request(): void {
		this.controller.request(
			'translate',
			{
				sourceLang: this.sourceLanguage.value,
				targetLang: this.targetLanguage.value,
			},
			this.sourceText.value,
		);
	}

	private swapLanguages(): void {
		if (this.sourceLanguage.value === WriterAiCatalog.DEFAULT_SOURCE_LANGUAGE) {
			return;
		}
		const source = this.sourceLanguage.value;
		this.sourceLanguage.value = this.targetLanguage.value;
		this.targetLanguage.value = source;
	}

	private createLanguageSelect(label: string): HTMLSelectElement {
		const select = document.createElement('select');
		select.setAttribute('aria-label', label);
		const labels: { [key: string]: string } = {
			auto: '自动识别',
			zh: '中文',
			en: 'English',
			ja: '日本語',
			ko: '한국어',
			fr: 'Français',
			de: 'Deutsch',
			es: 'Español',
			ru: 'Русский',
		};
		WriterAiCatalog.TRANSLATE_LANGUAGES.forEach((language) => {
			const option = document.createElement('option');
			option.value = language;
			option.textContent = labels[language] || language;
			if (label === '目标语言' && language === 'auto') {
				option.disabled = true;
			}
			select.appendChild(option);
		});
		return select;
	}

	private render(): void {
		const state = this.controller.getState();
		MobileAiResultRenderer.renderInto(this.result, state.preview);
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		this.generateButton.disabled = active;
		this.stopButton.disabled = !active;
		this.copyButton.disabled = !ready;
		this.regenerateButton.disabled = !ready;
		this.applyButton.disabled = !ready;
		this.status.textContent =
			state.error ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出...'
					: 'AI 正在翻译...'
				: '');
	}

	private createButton(label: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		return button;
	}
}
