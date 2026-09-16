/*
 * Android-style translation dialog with editable source and language swap.
 */

class MobileAiTranslateDialog {
	private readonly controller: WriterAiController;
	private readonly sheet: MobileAiSheet;
	private readonly layout: MobileAiTaskDialogControls;
	private readonly sourceLanguage: HTMLSelectElement;
	private readonly targetLanguage: HTMLSelectElement;
	private readonly sourceText: HTMLTextAreaElement;
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = WriterAiController.getInstance();
		this.layout = MobileAiTaskDialogLayout.create({
			generateLabel: '开始翻译',
			keepInputOnResult: true,
		});
		this.sheet = new MobileAiSheet({
			title: 'AI 翻译',
			presentation: 'writer',
			taskType: 'translate',
		});

		const languageRow = document.createElement('div');
		languageRow.className = 'mobile-ai-task-dialog__lang-row';
		this.sourceLanguage = this.createLanguageSelect('源语言');
		this.targetLanguage = this.createLanguageSelect('目标语言');
		const swapButton = document.createElement('button');
		swapButton.type = 'button';
		swapButton.className = 'mobile-ai-task-dialog__lang-swap';
		swapButton.textContent = '交换';
		swapButton.onclick = () => this.swapLanguages();
		languageRow.appendChild(this.sourceLanguage);
		languageRow.appendChild(swapButton);
		languageRow.appendChild(this.targetLanguage);
		this.layout.inputSlot.appendChild(languageRow);

		this.sourceText = MobileAiTaskDialogLayout.multilineInput(
			'请输入或编辑原文',
			'原文',
			6,
		);
		this.layout.inputSlot.appendChild(
			MobileAiTaskDialogLayout.cardField('', this.sourceText),
		);

		this.layout.generateButton.onclick = () => this.request();
		this.layout.stopButton.onclick = () => this.controller.cancel();
		this.layout.copyRow.onclick = () => this.controller.copy();
		this.layout.regenerateButton.onclick = () => this.request();
		this.layout.applyButton.onclick = () =>
			this.controller.accept(
				MobileAiResultRenderer.toHtml(this.controller.getState().preview),
			);

		this.sheet.setBody(this.layout.root);
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
		MobileAiResultRenderer.renderInto(
			this.layout.resultPreview,
			state.preview,
		);
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		if (ready) {
			this.layout.setStage('result');
		} else if (active) {
			this.layout.setStage('generating');
		} else {
			this.layout.setStage('input');
		}
		this.layout.generateButton.disabled = active;
		this.layout.stopButton.disabled = !active;
		this.layout.copyRow.disabled = !ready;
		this.layout.regenerateButton.disabled = !ready;
		this.layout.applyButton.disabled = !ready;
		this.layout.status.textContent =
			state.error ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出…'
					: 'AI 正在翻译…'
				: ready
					? '翻译完成'
					: '');
	}
}
