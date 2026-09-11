/*
 * Writer spell-check sheet (iOS).
 *
 * Intercepts CO SpellingDialog JSDialog (via MobileNativeDialogRouter) and
 * renders a native-styled bottom sheet. Control ids mirror Android
 * SpellingDialogHandler + core SpellingDialog.
 */

interface WriterSpellingControl {
	id: string;
	type?: string;
	text?: string;
	enabled?: boolean;
	visible?: boolean;
	hidden?: boolean;
	entries?: any;
	selectedEntries?: any;
	image?: string;
}

interface WriterSpellingPayload {
	action: 'show' | 'update' | 'dismiss';
	windowId: number;
	title?: string;
	controls?: WriterSpellingControl[];
}

class WriterSpellingSheet {
	private sheet: WriterEditorSheet | null = null;
	private windowId = -1;
	private sessionOpen = false;
	private sentenceEl: HTMLElement | null = null;
	private suggestionsList: HTMLElement | null = null;
	private noSuggestionsEl: HTMLElement | null = null;
	private readonly actionButtons: { [id: string]: HTMLButtonElement } = {};
	private suggestionTexts: string[] = [];
	private suggestionRows: number[] = [];
	private selectedRow = -1;

	static isSessionOpen(): boolean {
		const panel = (window as any).__coolWriterSpellingSheet;
		return panel instanceof WriterSpellingSheet && panel.sessionOpen;
	}

	static shouldInterceptMessagebox(msgData: any): boolean {
		if (!WriterSpellingSheet.isSessionOpen()) {
			return false;
		}
		if (WriterSpellingSheet.isSpellFinishMessagebox(msgData)) {
			return true;
		}
		return WriterSpellingSheet.isIncidentalSpellInfoMessagebox(msgData);
	}

	static handleMessagebox(msgData: any): boolean {
		if (!WriterSpellingSheet.shouldInterceptMessagebox(msgData)) {
			return false;
		}
		if (WriterSpellingSheet.isSpellFinishMessagebox(msgData)) {
			WriterSpellingSheet.showCompletionToast();
		}
		const windowId = msgData.id;
		if (windowId !== undefined) {
			WriterSpellingSheet.sendDialogResponse(windowId, 'ok', 1);
		}
		return true;
	}

	static handlePayload(payload: WriterSpellingPayload): void {
		const panel = WriterSpellingSheet.mount();
		if (!panel) {
			return;
		}
		if (payload.action === 'dismiss') {
			panel.close(false);
			return;
		}
		if (payload.action === 'update') {
			panel.applyControls(payload.controls || []);
			return;
		}
		panel.show(payload.windowId, payload.title || '拼写检查', payload.controls || []);
	}

	static closeActive(): void {
		const panel = (window as any).__coolWriterSpellingSheet;
		if (panel instanceof WriterSpellingSheet) {
			panel.close(false);
		}
	}

	static mount(): WriterSpellingSheet | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolWriterSpellingSheet;
		if (existing instanceof WriterSpellingSheet) {
			return existing;
		}
		const sheet = new WriterSpellingSheet();
		(window as any).__coolWriterSpellingSheet = sheet;
		return sheet;
	}

	private static isSpellFinishMessagebox(msgData: any): boolean {
		const message = WriterSpellingSheet.resolveMessageboxMessage(msgData);
		if (message.includes('已经完成') && message.includes('拼写检查')) {
			return true;
		}
		const lower = message.toLowerCase();
		return lower.includes('spellcheck') && lower.includes('completed');
	}

	private static isIncidentalSpellInfoMessagebox(msgData: any): boolean {
		if (!WriterSpellingSheet.isOkOnlyMessagebox(msgData)) {
			return false;
		}
		const title = msgData.title || '';
		const text = msgData.text || '';
		const message = WriterSpellingSheet.resolveMessageboxMessage(msgData);
		return (
			message.length === 0 ||
			message === title ||
			WriterSpellingSheet.isGenericMessageboxTitle(title)
		);
	}

	private static isOkOnlyMessagebox(msgData: any): boolean {
		const controls: any[] = msgData.children || [];
		let okCount = 0;
		let otherCount = 0;
		WriterSpellingSheet.walkControls(controls, (control) => {
			const type = control.type || '';
			if (type === 'okbutton') {
				okCount++;
			} else if (
				type === 'pushbutton' ||
				type === 'cancelbutton' ||
				type === 'helpbutton'
			) {
				otherCount++;
			}
		});
		return okCount > 0 && otherCount === 0;
	}

	private static walkControls(children: any[], visitor: (control: any) => void): void {
		if (!children) {
			return;
		}
		children.forEach((child) => {
			if (!child) {
				return;
			}
			if (child.type) {
				visitor(child);
			}
			if (child.children) {
				WriterSpellingSheet.walkControls(child.children, visitor);
			}
		});
	}

	private static resolveMessageboxMessage(msgData: any): string {
		const parts: string[] = [];
		const title = (msgData.title || '').trim();
		const text = (msgData.text || '').trim();
		if (title) {
			parts.push(title);
		}
		if (text && text !== title) {
			parts.push(text);
		}
		WriterSpellingSheet.walkControls(msgData.children || [], (control) => {
			const type = control.type || '';
			if (type === 'fixedtext' || type === 'label' || type === 'multilineedit') {
				const controlText = (control.text || control.label || '').trim();
				if (controlText && !parts.includes(controlText)) {
					parts.push(controlText);
				}
			}
		});
		return parts.join('\n');
	}

	private static isGenericMessageboxTitle(title: string): boolean {
		const trimmed = (title || '').trim();
		return trimmed === '信息' || trimmed === 'Information' || trimmed === 'Info';
	}

	private static showCompletionToast(): void {
		const toast = document.createElement('div');
		toast.textContent = '拼写检查已完成';
		toast.style.cssText =
			'position:fixed;left:50%;bottom:calc(24px + env(safe-area-inset-bottom));' +
			'transform:translateX(-50%);z-index:10002;padding:10px 16px;border-radius:8px;' +
			'background:rgba(30,30,30,.88);color:#fff;font-size:14px;pointer-events:none;';
		document.body.appendChild(toast);
		window.setTimeout(() => toast.remove(), 2200);
	}

	private static sendDialogResponse(windowId: number, controlId: string, responseCode: number): void {
		const map = (window as any).app && (window as any).app.map;
		const socket = map && map.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return;
		}
		const payload = JSON.stringify({
			id: controlId,
			cmd: 'click',
			data: String(responseCode),
			type: 'responsebutton',
		});
		socket.sendMessage('dialogevent ' + windowId + ' ' + payload);
	}

	private show(windowId: number, title: string, controls: WriterSpellingControl[]): void {
		this.windowId = windowId;
		this.sessionOpen = true;
		this.sheet = new WriterEditorSheet(title, () => this.close(true));
		this.sheet.setBody(this.buildBody());
		this.applyControls(controls);
		this.sheet.open();
	}

	private buildBody(): HTMLElement {
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:8px;';

		const notInDict = document.createElement('div');
		notInDict.textContent = '不在词典中';
		notInDict.style.cssText = 'font-size:13px;color:#666;';
		content.appendChild(notInDict);

		this.sentenceEl = document.createElement('div');
		this.sentenceEl.style.cssText =
			'min-height:56px;padding:12px;border-radius:12px;background:#f5f7fa;' +
			'font-size:15px;color:#101010;line-height:1.5;';
		content.appendChild(this.sentenceEl);

		const suggestLabel = document.createElement('div');
		suggestLabel.textContent = '建议';
		suggestLabel.style.cssText = 'margin-top:8px;font-size:13px;color:#666;';
		content.appendChild(suggestLabel);

		this.suggestionsList = document.createElement('div');
		this.suggestionsList.style.cssText =
			'max-height:120px;overflow:auto;border-radius:12px;background:#f5f7fa;';
		content.appendChild(this.suggestionsList);

		this.noSuggestionsEl = document.createElement('div');
		this.noSuggestionsEl.textContent = '（无建议）';
		this.noSuggestionsEl.style.cssText = 'display:none;font-size:14px;color:#999;';
		content.appendChild(this.noSuggestionsEl);

		content.appendChild(this.buildActionRow(['change', '更正'], ['changeall', '全部更正']));
		content.appendChild(this.buildActionRow(['ignore', '忽略'], ['ignoreall', '全部忽略']));

		const addBtn = this.makeActionButton('add', '添加到词典', true);
		addBtn.style.display = 'none';
		content.appendChild(addBtn);

		const closeBtn = this.makeActionButton('close', '关闭', false);
		closeBtn.style.cssText += 'width:100%;min-height:52px;margin-top:4px;';
		content.appendChild(closeBtn);

		return content;
	}

	private buildActionRow(
		left: [string, string],
		right: [string, string],
	): HTMLElement {
		const row = document.createElement('div');
		row.style.cssText = 'display:flex;gap:8px;margin-top:8px;';
		const leftBtn = this.makeActionButton(left[0], left[1], left[0] === 'change');
		leftBtn.style.flex = '1';
		const rightBtn = this.makeActionButton(right[0], right[1], false);
		rightBtn.style.flex = '1';
		row.appendChild(leftBtn);
		row.appendChild(rightBtn);
		return row;
	}

	private makeActionButton(id: string, label: string, primary: boolean): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.style.cssText =
			'min-height:44px;border-radius:10px;border:1px solid #d8dde3;font:inherit;' +
			'font-size:15px;cursor:pointer;' +
			(primary
				? 'background:linear-gradient(135deg,#6b8cff,#4a6cf7);color:#fff;border:none;'
				: 'background:#fff;color:#333;');
		button.onclick = () => this.onAction(id);
		this.actionButtons[id] = button;
		return button;
	}

	private applyControls(controls: WriterSpellingControl[]): void {
		controls.forEach((control) => this.applyControl(control));
	}

	private applyControl(control: WriterSpellingControl): void {
		const id = control.id || '';
		if (!id) {
			return;
		}
		switch (id) {
			case 'errorsentence':
			case 'explain':
				this.applySentenceText(control);
				break;
			case 'suggestionslb':
				this.applySuggestions(control);
				this.syncSelectionToCore();
				break;
			case 'change':
			case 'changeall':
			case 'ignore':
			case 'ignoreall':
			case 'add':
			case 'close':
				this.applyButtonState(this.actionButtons[id], control);
				break;
			default:
				if (
					(control.type === 'fixedtext' || control.type === 'label') &&
					control.text &&
					(id.includes('sentence') || id.includes('explain'))
				) {
					this.applySentenceText(control);
				}
				break;
		}
	}

	private applySentenceText(control: WriterSpellingControl): void {
		if (!this.sentenceEl) {
			return;
		}
		if (control.text) {
			this.sentenceEl.textContent = control.text;
			return;
		}
		if (control.image) {
			this.sentenceEl.textContent = '（错词已在文档中高亮显示）';
		}
	}

	private applySuggestions(control: WriterSpellingControl, keepSelection = false): void {
		const previousSelection = keepSelection ? this.selectedRow : -1;
		this.suggestionTexts = [];
		this.suggestionRows = [];
		this.selectedRow = -1;
		if (!this.suggestionsList) {
			return;
		}
		this.suggestionsList.replaceChildren();

		const entries = control.entries;
		if (Array.isArray(entries)) {
			entries.forEach((entry, index) => this.parseSuggestionEntry(entry, index));
		}

		const selectedEntries = control.selectedEntries;
		if (
			previousSelection >= 0 &&
			this.suggestionRows.indexOf(previousSelection) >= 0
		) {
			this.selectedRow = previousSelection;
		} else if (Array.isArray(selectedEntries) && selectedEntries.length > 0) {
			this.selectedRow = Number.parseInt(String(selectedEntries[0]), 10);
		} else if (this.suggestionRows.length > 0) {
			this.selectedRow = this.suggestionRows[0];
		}

		this.suggestionTexts.forEach((text, index) => {
			const rowEl = document.createElement('button');
			rowEl.type = 'button';
			rowEl.textContent = text;
			const rowIndex = this.suggestionRows[index];
			const selected = rowIndex === this.selectedRow;
			rowEl.style.cssText =
				'display:block;width:100%;text-align:left;padding:10px 12px;border:none;' +
				'border-bottom:1px solid #e8e8e8;background:' +
				(selected ? '#e8f0fe' : 'transparent') +
				';font:inherit;font-size:14px;color:#222;cursor:pointer;';
			rowEl.onclick = () => {
				this.selectedRow = rowIndex;
				this.applySuggestions(control, true);
				this.syncSelectionToCore();
			};
			this.suggestionsList?.appendChild(rowEl);
		});

		const hasSuggestions = this.suggestionTexts.length > 0;
		if (this.suggestionsList) {
			this.suggestionsList.style.display = hasSuggestions ? 'block' : 'none';
		}
		if (this.noSuggestionsEl) {
			this.noSuggestionsEl.style.display = hasSuggestions ? 'none' : 'block';
		}
	}

	private parseSuggestionEntry(raw: any, fallbackIndex: number): void {
		if (typeof raw === 'string') {
			this.suggestionTexts.push(raw);
			this.suggestionRows.push(fallbackIndex);
			return;
		}
		if (!raw || typeof raw !== 'object') {
			return;
		}
		const row = raw.row !== undefined ? Number.parseInt(String(raw.row), 10) : fallbackIndex;
		let text = raw.text || '';
		if (!text && Array.isArray(raw.columns) && raw.columns.length > 0) {
			const col0 = raw.columns[0];
			text = typeof col0 === 'object' && col0 ? col0.text || '' : String(col0 || '');
		}
		if (text) {
			this.suggestionTexts.push(text);
			this.suggestionRows.push(row);
			if (raw.selected) {
				this.selectedRow = row;
			}
		}
	}

	private applyButtonState(button: HTMLButtonElement | undefined, control: WriterSpellingControl): void {
		if (!button) {
			return;
		}
		if (control.enabled !== undefined) {
			button.disabled = !control.enabled;
			button.style.opacity = control.enabled ? '1' : '0.4';
		}
		if (control.visible === false || control.hidden === true) {
			button.style.display = 'none';
		} else if (control.visible === true) {
			button.style.display = '';
		}
	}

	private onAction(buttonId: string): void {
		if (buttonId === 'close') {
			this.close(true);
			return;
		}
		this.sendDialogEvent(buttonId, 'click', '', 'pushbutton');
	}

	private syncSelectionToCore(): void {
		if (this.selectedRow >= 0) {
			this.sendDialogEvent(
				'suggestionslb',
				'select',
				String(this.selectedRow),
				'treeview',
			);
		}
	}

	private sendDialogEvent(controlId: string, cmd: string, data: string, type: string): void {
		const map = (window as any).app && (window as any).app.map;
		const socket = map && map.socket;
		if (!socket || typeof socket.sendMessage !== 'function' || this.windowId < 0) {
			return;
		}
		const payload = JSON.stringify({ id: controlId, cmd: cmd, data: data, type: type });
		socket.sendMessage('dialogevent ' + this.windowId + ' ' + payload);
	}

	private close(notifyCore: boolean): void {
		if (notifyCore && this.windowId >= 0) {
			this.sendDialogEvent('close', 'click', '', 'pushbutton');
		}
		this.sessionOpen = false;
		if (this.sheet) {
			this.sheet.close();
			this.sheet = null;
		}
		this.windowId = -1;
		this.sentenceEl = null;
		this.suggestionsList = null;
		this.noSuggestionsEl = null;
		Object.keys(this.actionButtons).forEach((key) => delete this.actionButtons[key]);
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	WriterSpellingSheet.mount();
	(window as any).WriterSpellingSheet = WriterSpellingSheet;
}
