/*
 * Calc insert-hyperlink subpage (Android CalcHyperlinkPickerController fields).
 */

class CalcEditorHyperlinkDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private readonly textInput: HTMLInputElement;
	private readonly urlInput: HTMLInputElement;
	private readonly onInserted?: () => void;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.controller = controller;
		this.onInserted = onInserted;

		const content = document.createElement('div');
		content.className = 'writer-function-calc-hyperlink-form';

		this.textInput = CalcEditorHyperlinkDialog.field('显示文本', '链接文字');
		this.urlInput = CalcEditorHyperlinkDialog.field('链接地址', 'https://');
		content.appendChild(CalcEditorHyperlinkDialog.wrapField('显示文本', this.textInput));
		content.appendChild(CalcEditorHyperlinkDialog.wrapField('链接地址', this.urlInput));

		const insert = document.createElement('button');
		insert.type = 'button';
		insert.className = 'writer-function-calc-hyperlink-form__submit';
		insert.textContent = '插入';
		insert.setAttribute('aria-label', '插入超链接');
		insert.onclick = () => this.insert();
		content.appendChild(insert);

		this.subpage = writerEditorMountSubpageDialog('插入超链接', content, host);
	}

	open(): void {
		this.textInput.value = this.controller.getSelectedText() || '';
		this.urlInput.value = '';
		this.subpage.open();
		this.urlInput.focus();
	}

	close(): void {
		this.subpage.close();
	}

	private insert(): void {
		let url = this.urlInput.value.trim();
		if (!url) {
			return;
		}
		if (!/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url)) {
			url = 'https://' + url;
		}
		this.controller.insertHyperlink(this.textInput.value, url);
		this.subpage.close();
		if (this.onInserted) {
			this.onInserted();
		}
	}

	private static field(label: string, placeholder: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.placeholder = placeholder;
		input.setAttribute('aria-label', label);
		input.className = 'writer-function-calc-hyperlink-form__input';
		return input;
	}

	private static wrapField(labelText: string, input: HTMLInputElement): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-hyperlink-form__field';
		const label = document.createElement('label');
		label.className = 'writer-function-calc-hyperlink-form__label';
		label.textContent = labelText;
		wrap.appendChild(label);
		wrap.appendChild(input);
		return wrap;
	}
}
