/*
 * Writer find/replace dialog (iOS).
 *
 * Thin DOM UI over WriterEditorController.runFind / runFindReplace. Wraps
 * the shared MobileAiSheet surface. Mode selector switches between find and
 * replace; settings toggles feed the SearchItem flags.
 */

class WriterFindReplaceDialog {
	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;
	private mode: 'find' | 'replace' = 'find';
	private readonly searchInput: HTMLInputElement;
	private readonly replaceInput: HTMLInputElement;
	private readonly caseCheckbox: HTMLInputElement;
	private readonly wholeCheckbox: HTMLInputElement;
	private readonly replaceRow: HTMLDivElement;
	private readonly findModeBtn: HTMLButtonElement;
	private readonly replaceModeBtn: HTMLButtonElement;
	private readonly settingsRow: HTMLDivElement;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '查找替换' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

		// Mode selector
		const modeRow = document.createElement('div');
		modeRow.style.cssText = 'display:flex;gap:4px;border-bottom:1px solid #d8dde3;';
		this.findModeBtn = this.makeModeButton('查找', true);
		this.replaceModeBtn = this.makeModeButton('替换', false);
		modeRow.appendChild(this.findModeBtn);
		modeRow.appendChild(this.replaceModeBtn);
		content.appendChild(modeRow);

		// Search row
		const searchRow = document.createElement('div');
		searchRow.style.cssText = 'display:flex;gap:8px;align-items:center;';
		this.searchInput = document.createElement('input');
		this.searchInput.type = 'text';
		this.searchInput.placeholder = '查找内容';
		this.searchInput.style.cssText =
			'flex:1;padding:10px;border:1px solid #d8dde3;border-radius:8px;font:inherit;';
		searchRow.appendChild(this.searchInput);
		const prevButton = this.makeButton('上一处', () => this.doFind(true));
		const nextButton = this.makeButton('下一处', () => this.doFind(false));
		searchRow.appendChild(prevButton);
		searchRow.appendChild(nextButton);
		content.appendChild(searchRow);

		// Replace row (visible in replace mode)
		this.replaceRow = document.createElement('div');
		this.replaceRow.style.cssText = 'display:flex;gap:8px;align-items:center;';
		this.replaceInput = document.createElement('input');
		this.replaceInput.type = 'text';
		this.replaceInput.placeholder = '替换为';
		this.replaceInput.style.cssText =
			'flex:1;padding:10px;border:1px solid #d8dde3;border-radius:8px;font:inherit;';
		this.replaceRow.appendChild(this.replaceInput);
		const replaceButton = this.makeButton('替换', () => this.doReplace(false));
		const replaceAllButton = this.makeButton('全部替换', () => this.doReplace(true));
		this.replaceRow.appendChild(replaceButton);
		this.replaceRow.appendChild(replaceAllButton);
		content.appendChild(this.replaceRow);
		this.replaceRow.style.display = 'none';

		// Settings
		this.settingsRow = document.createElement('div');
		this.settingsRow.style.cssText = 'display:flex;gap:16px;align-items:center;';
		this.caseCheckbox = this.makeCheckbox('区分大小写', false);
		this.wholeCheckbox = this.makeCheckbox('全字匹配', false);
		content.appendChild(this.settingsRow);

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
		this.searchInput.focus();
	}

	private makeModeButton(label: string, isFind: boolean): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.setAttribute('aria-label', label);
		button.style.cssText =
			'flex:1;padding:8px;background:none;border:none;border-bottom:2px solid ' +
			(isFind ? '#1a73e8' : 'transparent') +
			';color:' + (isFind ? '#1a73e8' : '#5f6368') + ';font:inherit;font-size:15px;cursor:pointer;';
		button.onclick = () => this.setMode(isFind ? 'find' : 'replace');
		return button;
	}

	private makeButton(label: string, handler: () => void): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.setAttribute('aria-label', label);
		button.style.cssText =
			'padding:8px 12px;border:1px solid #d8dde3;border-radius:8px;background:#fff;font:inherit;cursor:pointer;';
		button.onclick = handler;
		return button;
	}

	private makeCheckbox(label: string, checked: boolean): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'checkbox';
		input.checked = checked;
		input.id = 'writer-find-' + label;
		const labelElement = document.createElement('label');
		labelElement.textContent = label;
		labelElement.htmlFor = input.id;
		labelElement.style.cssText = 'font-size:14px;cursor:pointer;';
		const wrap = document.createElement('span');
		wrap.style.cssText = 'display:flex;align-items:center;gap:4px;';
		wrap.appendChild(input);
		wrap.appendChild(labelElement);
		this.settingsRow.appendChild(wrap);
		return input;
	}

	private setMode(mode: 'find' | 'replace'): void {
		this.mode = mode;
		this.replaceRow.style.display = mode === 'replace' ? 'flex' : 'none';
		this.findModeBtn.style.borderBottomColor =
			mode === 'find' ? '#1a73e8' : 'transparent';
		this.findModeBtn.style.color = mode === 'find' ? '#1a73e8' : '#5f6368';
		this.replaceModeBtn.style.borderBottomColor =
			mode === 'replace' ? '#1a73e8' : 'transparent';
		this.replaceModeBtn.style.color = mode === 'replace' ? '#1a73e8' : '#5f6368';
	}

	private options(): WriterFindReplaceOptions {
		return {
			caseSensitive: this.caseCheckbox.checked,
			wholeWord: this.wholeCheckbox.checked,
		};
	}

	private doFind(backward: boolean): void {
		const result = this.controller.runFind(this.searchInput.value.trim(), backward, this.options());
		if (!result.executed) {
			this.flash(result.reason || '查找失败');
		}
	}

	private doReplace(replaceAll: boolean): void {
		const result = this.controller.runFindReplace(
			this.searchInput.value.trim(),
			this.replaceInput.value,
			replaceAll,
			this.options(),
		);
		if (!result.executed) {
			this.flash(result.reason || '替换失败');
		}
	}

	private flash(message: string): void {
		this.searchInput.setAttribute('title', message);
	}
}
