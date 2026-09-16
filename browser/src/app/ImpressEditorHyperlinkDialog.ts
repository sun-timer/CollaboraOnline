/*
 * Impress insert-hyperlink dialog (iOS).
 *
 * Internet + mail fields aligned with Android ImpressHyperlinkPickerController,
 * without copying Android XML. Dispatches .uno:SetHyperlink via WriterEditorController.
 */

class ImpressEditorHyperlinkDialog {
	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;
	private readonly textInput: HTMLInputElement;
	private readonly urlInput: HTMLInputElement;
	private readonly internetTab: HTMLButtonElement;
	private readonly mailTab: HTMLButtonElement;
	private mode: 'internet' | 'mail' = 'internet';

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '插入超链接' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

		const tabs = document.createElement('div');
		tabs.style.cssText = 'display:flex;gap:8px;';
		this.internetTab = this.modeButton('互联网', 'internet');
		this.mailTab = this.modeButton('邮件', 'mail');
		tabs.appendChild(this.internetTab);
		tabs.appendChild(this.mailTab);
		content.appendChild(tabs);

		this.textInput = this.field('显示文本', '链接文字');
		this.urlInput = this.field('链接地址', 'https://');
		content.appendChild(this.wrapField('显示文本', this.textInput));
		content.appendChild(this.wrapField('链接地址', this.urlInput));

		const insert = document.createElement('button');
		insert.type = 'button';
		insert.textContent = '插入';
		insert.setAttribute('aria-label', '插入超链接');
		insert.style.cssText =
			'padding:12px 16px;border:none;border-radius:8px;background:#ec5d1f;' +
			'color:#fff;font:inherit;font-size:16px;font-weight:600;cursor:pointer;';
		insert.onclick = () => this.insert();
		content.appendChild(insert);

		this.sheet.setBody(content);
	}

	open(): void {
		this.textInput.value = this.controller.getSelectedText() || '';
		this.urlInput.value = '';
		this.setMode('internet');
		this.sheet.open();
		this.urlInput.focus();
	}

	close(): void {
		this.sheet.close();
	}

	private modeButton(
		label: string,
		mode: 'internet' | 'mail',
	): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.setAttribute('aria-label', label);
		button.dataset.mode = mode;
		button.style.cssText =
			'flex:1;padding:8px 0;border:1px solid #e3e3e3;border-radius:8px;' +
			'background:#f2f3f5;font:inherit;cursor:pointer;';
		button.onclick = () => this.setMode(mode);
		return button;
	}

	private setMode(mode: 'internet' | 'mail'): void {
		this.mode = mode;
		this.urlInput.placeholder =
			mode === 'mail' ? 'name@example.com' : 'https://';
		this.styleModeButton(this.internetTab, mode === 'internet');
		this.styleModeButton(this.mailTab, mode === 'mail');
	}

	private styleModeButton(button: HTMLButtonElement, active: boolean): void {
		button.style.background = active ? '#ec5d1f' : '#f2f3f5';
		button.style.color = active ? '#fff' : '#333';
	}

	private field(label: string, placeholder: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.placeholder = placeholder;
		input.setAttribute('aria-label', label);
		input.style.cssText =
			'width:100%;box-sizing:border-box;padding:10px 12px;border:1px solid #e3e3e3;' +
			'border-radius:8px;font:inherit;font-size:15px;';
		return input;
	}

	private wrapField(
		labelText: string,
		input: HTMLInputElement,
	): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.style.cssText = 'display:flex;flex-direction:column;gap:6px;';
		const label = document.createElement('label');
		label.textContent = labelText;
		label.style.cssText = 'font-size:13px;color:#5f6368;';
		wrap.appendChild(label);
		wrap.appendChild(input);
		return wrap;
	}

	private insert(): void {
		let url = this.urlInput.value.trim();
		if (!url) {
			return;
		}
		if (this.mode === 'mail') {
			if (url.toLowerCase().indexOf('mailto:') !== 0) {
				url = 'mailto:' + url;
			}
		} else if (!/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url)) {
			url = 'https://' + url;
		}
		this.controller.insertHyperlink(this.textInput.value, url);
		this.sheet.close();
	}
}
