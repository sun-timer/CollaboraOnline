/*
 * Writer word count sheet (iOS).
 *
 * Intercepts CO WordCountDialog JSDialog (via MobileNativeDialogRouter) and
 * renders a native-styled bottom sheet. Row ids mirror Android
 * WordCountSheetController ROWS + core control ids.
 */

interface WriterWordCountRowDef {
	id: string;
	label: string;
}

interface WriterWordCountControl {
	id: string;
	text?: string;
	visible?: boolean;
	hidden?: boolean;
}

interface WriterWordCountPayload {
	action: 'show' | 'update' | 'dismiss';
	windowId: number;
	controls?: WriterWordCountControl[];
}

class WriterWordCountSheet {
	private sheet: WriterEditorSheet | null = null;
	private readonly valueElements: { [id: string]: HTMLElement } = {};
	private windowId = -1;

	static getRowDefs(): WriterWordCountRowDef[] {
		return [
			{ id: 'docwords', label: '字词' },
			{ id: 'docchars', label: '字符数（计空格）' },
			{ id: 'doccharsnospaces', label: '字符数（不计空格）' },
			{ id: 'doccjkchars', label: '东亚文字+韩文' },
			{ id: 'docComments', label: '批注数' },
		];
	}

	static normalizeCount(raw: string): string {
		const trimmed = (raw || '').trim();
		if (!trimmed) {
			return '0';
		}
		const digits = trimmed.replace(/,/g, '').replace(/\s/g, '');
		const value = Number.parseInt(digits, 10);
		if (Number.isFinite(value)) {
			return value.toLocaleString();
		}
		return trimmed;
	}

	static handlePayload(payload: WriterWordCountPayload): void {
		const panel = WriterWordCountSheet.mount();
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
		panel.show(payload.windowId, payload.controls || []);
	}

	static closeActive(): void {
		const panel = (window as any).__coolWriterWordCountSheet;
		if (panel instanceof WriterWordCountSheet) {
			panel.close(false);
		}
	}

	static mount(): WriterWordCountSheet | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolWriterWordCountSheet;
		if (existing instanceof WriterWordCountSheet) {
			return existing;
		}
		const sheet = new WriterWordCountSheet();
		(window as any).__coolWriterWordCountSheet = sheet;
		return sheet;
	}

	private show(windowId: number, controls: WriterWordCountControl[]): void {
		this.windowId = windowId;
		this.sheet = new WriterEditorSheet('字数统计', () => this.close(true));
		this.sheet.setBody(this.buildBody());
		this.applyControls(controls);
		this.sheet.open();
	}

	private buildBody(): HTMLElement {
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;';
		WriterWordCountSheet.getRowDefs().forEach((row, index) => {
			const rowEl = document.createElement('div');
			rowEl.style.cssText =
				'display:flex;align-items:center;justify-content:space-between;' +
				'min-height:41px;padding:12px 12px;color:#333;font-size:14px;';
			const label = document.createElement('span');
			label.textContent = row.label;
			const value = document.createElement('span');
			value.textContent = '0';
			value.style.cssText = 'font-size:12px;color:#6a6a6a;margin-left:8px;';
			rowEl.appendChild(label);
			rowEl.appendChild(value);
			this.valueElements[row.id] = value;
			content.appendChild(rowEl);
			if (index + 1 < WriterWordCountSheet.getRowDefs().length) {
				const divider = document.createElement('div');
				divider.style.cssText = 'height:1px;background:rgba(0,0,0,0.08);';
				content.appendChild(divider);
			}
		});
		return content;
	}

	private applyControls(controls: WriterWordCountControl[]): void {
		controls.forEach((control) => {
			const valueEl = this.valueElements[control.id];
			if (!valueEl) {
				return;
			}
			const row = valueEl.parentElement;
			if (control.visible === false || control.hidden === true) {
				if (row) {
					row.style.display = 'none';
				}
				return;
			}
			if (row) {
				row.style.display = 'flex';
			}
			if (control.text) {
				valueEl.textContent = WriterWordCountSheet.normalizeCount(control.text);
			}
		});
	}

	private close(notifyCore: boolean): void {
		const closingWindow = this.windowId;
		if (this.sheet) {
			this.sheet.close();
			this.sheet = null;
		}
		this.windowId = -1;
		if (notifyCore && closingWindow >= 0) {
			this.sendDialogResponse(closingWindow);
		}
	}

	private sendDialogResponse(windowId: number): void {
		const map = (window as any).app && (window as any).app.map;
		const socket = map && map.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return;
		}
		const payload = JSON.stringify({
			id: 'close',
			cmd: 'click',
			data: '7',
			type: 'responsebutton',
		});
		socket.sendMessage('dialogevent ' + windowId + ' ' + payload);
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	WriterWordCountSheet.mount();
	(window as any).WriterWordCountSheet = WriterWordCountSheet;
}
