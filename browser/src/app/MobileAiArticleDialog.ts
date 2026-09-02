/*
 * Android-style article generation form.
 *
 * The form is rendered even while the iOS native task contract is pending, so
 * the UI shape is stable before the backend capability is enabled.
 */

class MobileAiArticleDialog {
	private readonly sheet: MobileAiSheet;
	private readonly template: HTMLSelectElement;
	private readonly form: HTMLDivElement;
	private readonly generate: HTMLButtonElement;

	constructor() {
		this.sheet = new MobileAiSheet({ title: '文案生成' });
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';
		this.template = document.createElement('select');
		this.template.setAttribute('aria-label', '文案类型');
		Object.keys(WriterAiCatalog.ARTICLE_TEMPLATES).forEach((key) => {
			const option = document.createElement('option');
			option.value = key;
			const item = WriterAiCatalog.ARTICLE_TEMPLATES[key];
			option.textContent = `${item.category} / ${key}`;
			this.template.appendChild(option);
		});
		this.template.onchange = () => this.renderForm();
		content.appendChild(this.template);
		this.form = document.createElement('div');
		this.form.style.cssText = 'display:flex;flex-direction:column;gap:8px;';
		content.appendChild(this.form);
		this.generate = document.createElement('button');
		this.generate.type = 'button';
		this.generate.disabled = true;
		this.generate.title = '生成能力待接通';
		content.appendChild(this.generate);
		const status = document.createElement('div');
		status.textContent = '生成能力待接通';
		status.setAttribute('role', 'status');
		content.appendChild(status);
		this.sheet.setBody(content);
		this.renderForm();
	}

	open(): void {
		this.sheet.open();
	}

	private renderForm(): void {
		const item = WriterAiCatalog.getArticleTemplate(this.template.value);
		this.form.replaceChildren();
		if (!item) {
			return;
		}
		item.variables.forEach((label) => {
			const input = document.createElement('input');
			input.type = 'text';
			input.placeholder = label;
			input.setAttribute('aria-label', label);
			this.form.appendChild(input);
		});
	}
}
