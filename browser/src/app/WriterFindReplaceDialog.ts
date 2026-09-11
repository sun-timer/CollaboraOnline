/*
 * Writer find/replace dialog (iOS).
 *
 * Thin DOM UI over WriterEditorController.runFind / runFindReplace. Uses
 * WriterEditorSheet chrome. Mode selector switches between find and replace;
 * settings toggles feed the SearchItem flags.
 */

class WriterFindReplaceDialog {
	private sheet: WriterEditorSheet | null = null;
	private readonly controller: WriterEditorController;
	private mode: 'find' | 'replace' = 'find';
	private readonly searchInput: HTMLInputElement;
	private readonly replaceInput: HTMLInputElement;
	private ignoreCheckbox!: HTMLInputElement;
	private caseCheckbox!: HTMLInputElement;
	private wholeCheckbox!: HTMLInputElement;
	private readonly replaceRow: HTMLDivElement;
	private readonly findModeBtn: HTMLButtonElement;
	private readonly replaceModeBtn: HTMLButtonElement;
	private readonly settingsRow: HTMLDivElement;
	private readonly replaceButton: HTMLButtonElement;
	private readonly replaceAllButton: HTMLButtonElement;

	static open(): void {
		if (!(window as any).ThisIsTheiOSApp) {
			return;
		}
		const controller = WriterEditorController.getInstance();
		const dialog = new WriterFindReplaceDialog(controller);
		dialog.show();
	}

	static closeActive(): void {
		const bridge = (window as any).__coolWriterFindReplace;
		if (bridge && bridge.instance instanceof WriterFindReplaceDialog) {
			bridge.instance.close();
		}
	}

	static mountBridge(): void {
		if (!(window as any).ThisIsTheiOSApp) {
			return;
		}
		const bridge = {
			instance: null as WriterFindReplaceDialog | null,
			open: (): void => WriterFindReplaceDialog.open(),
			close: (): void => WriterFindReplaceDialog.closeActive(),
		};
		(window as any).__coolWriterFindReplace = bridge;
	}

	constructor(controller: WriterEditorController) {
		this.controller = controller;

		this.searchInput = document.createElement('input');
		this.replaceInput = document.createElement('input');
		this.settingsRow = document.createElement('div');
		this.replaceRow = document.createElement('div');
		this.findModeBtn = this.makeModeButton('查找', true);
		this.replaceModeBtn = this.makeModeButton('替换', false);
		this.replaceButton = this.makeButton('替换', () => this.doReplace(false));
		this.replaceAllButton = this.makeButton('全部替换', () => this.doReplace(true));
	}

	private show(): void {
		const bridge = (window as any).__coolWriterFindReplace;
		if (bridge) {
			bridge.instance = this;
		}
		this.sheet = new WriterEditorSheet('查找替换', () => this.close());
		this.sheet.setBody(this.buildBody());
		this.syncReplaceEnabled();
		this.sheet.open();
		this.searchInput.focus();
	}

	private buildBody(): HTMLElement {
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

		const modeRow = document.createElement('div');
		modeRow.style.cssText =
			'display:flex;gap:4px;border-bottom:1px solid #d8dde3;background:#f2f3f5;border-radius:8px;padding:2px;';
		modeRow.appendChild(this.findModeBtn);
		modeRow.appendChild(this.replaceModeBtn);
		content.appendChild(modeRow);

		this.searchInput.type = 'text';
		this.searchInput.placeholder = '查找内容';
		this.searchInput.style.cssText =
			'flex:1;padding:10px;border:1px solid #d8dde3;border-radius:8px;font:inherit;';
		this.searchInput.addEventListener('input', () => this.syncReplaceEnabled());

		const searchRow = document.createElement('div');
		searchRow.style.cssText = 'display:flex;gap:8px;align-items:center;';
		searchRow.appendChild(this.searchInput);
		searchRow.appendChild(this.makeButton('上一处', () => this.doFind(true)));
		searchRow.appendChild(this.makeButton('下一处', () => this.doFind(false)));
		content.appendChild(searchRow);

		this.replaceRow.style.cssText = 'display:none;flex-direction:column;gap:8px;';
		this.replaceInput.type = 'text';
		this.replaceInput.placeholder = '替换为';
		this.replaceInput.style.cssText =
			'width:100%;padding:10px;border:1px solid #d8dde3;border-radius:8px;font:inherit;box-sizing:border-box;';
		this.replaceRow.appendChild(this.replaceInput);
		const replaceActions = document.createElement('div');
		replaceActions.style.cssText = 'display:flex;gap:8px;';
		replaceActions.appendChild(this.replaceButton);
		replaceActions.appendChild(this.replaceAllButton);
		this.replaceRow.appendChild(replaceActions);
		content.appendChild(this.replaceRow);

		this.settingsRow.style.cssText = 'display:flex;gap:16px;align-items:center;flex-wrap:wrap;';
		this.ignoreCheckbox = this.makeCheckbox('忽略大小写', true);
		this.caseCheckbox = this.makeCheckbox('区分大小写', false);
		this.wholeCheckbox = this.makeCheckbox('全字匹配', false);
		this.caseCheckbox.addEventListener('change', () => {
			if (this.caseCheckbox.checked) {
				this.ignoreCheckbox.checked = false;
			}
		});
		this.ignoreCheckbox.addEventListener('change', () => {
			if (this.ignoreCheckbox.checked) {
				this.caseCheckbox.checked = false;
			}
		});
		content.appendChild(this.settingsRow);

		return content;
	}

	open(): void {
		this.show();
	}

	close(): void {
		if (this.sheet) {
			this.sheet.close();
			this.sheet = null;
		}
		const bridge = (window as any).__coolWriterFindReplace;
		if (bridge && bridge.instance === this) {
			bridge.instance = null;
		}
	}

	private makeModeButton(label: string, isFind: boolean): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.setAttribute('aria-label', label);
		button.style.cssText =
			'flex:1;padding:8px;border:none;border-radius:6px;font:inherit;font-size:15px;cursor:pointer;' +
			(isFind ? 'background:#fff;color:#15171a;' : 'background:transparent;color:#666;');
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
		const findActive = mode === 'find';
		this.findModeBtn.style.background = findActive ? '#fff' : 'transparent';
		this.findModeBtn.style.color = findActive ? '#15171a' : '#666';
		this.replaceModeBtn.style.background = findActive ? 'transparent' : '#fff';
		this.replaceModeBtn.style.color = findActive ? '#666' : '#15171a';
		this.syncReplaceEnabled();
	}

	private options(): WriterFindReplaceOptions {
		return {
			caseSensitive: this.caseCheckbox.checked,
			wholeWord: this.wholeCheckbox.checked,
		};
	}

	private syncReplaceEnabled(): void {
		const hasQuery = this.searchInput.value.trim().length > 0;
		this.replaceButton.disabled = !hasQuery;
		this.replaceAllButton.disabled = !hasQuery;
		this.replaceButton.style.opacity = hasQuery ? '1' : '0.4';
		this.replaceAllButton.style.opacity = hasQuery ? '1' : '0.4';
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

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	WriterFindReplaceDialog.mountBridge();
}
