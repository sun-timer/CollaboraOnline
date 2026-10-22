/*
 * Android-style AI Assistant sheet: document Q&A and chat tabs.
 */

/** Android lolib_ic_ai_send_plane — tmp/发送.svg */
const MOBILE_AI_SEND_PLANE_SVG =
	'<svg viewBox="22 20 28 28" width="20" height="20" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
	'<path fill="#ffffff" d="M50 47.7988L37.036 44.8868L36.028 28.7868L34.936 44.8868L22 47.7988L36 19.7988L50 47.7988Z"/></svg>';

const MOBILE_AI_STOP_SVG =
	'<svg viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
	'<rect x="7" y="7" width="10" height="10" rx="1.5" fill="#ffffff"/></svg>';

class MobileAiAssistantPanel {
	private readonly controller: MobileAiConversationController;
	private readonly sheet: MobileAiSheet;
	private readonly content: HTMLDivElement;
	private readonly contextArea: HTMLDivElement;
	private readonly tabDocQa: HTMLButtonElement;
	private readonly tabChat: HTMLButtonElement;
	private readonly tabDocQaCell: HTMLDivElement;
	private readonly tabChatCell: HTMLDivElement;
	private readonly messages: HTMLDivElement;
	private readonly input: HTMLTextAreaElement;
	private readonly inputRow: HTMLDivElement;
	private readonly sendButton: HTMLButtonElement;
	private readonly status: HTMLDivElement;
	private readonly clearLink: HTMLButtonElement;
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = MobileAiConversationController.shared();
		this.sheet = new MobileAiSheet({
			title: 'AI助手',
			keyboardAvoidance: () =>
				!!(window as any).ThisIsTheiOSApp &&
				(window as any).app?.map?.getDocType?.() === 'spreadsheet',
			onClose: () => {
				this.setIosCalcKeyboardLayout(false);
				this.controller.cancel();
			},
		});
		this.content = document.createElement('div');
		this.content.style.cssText = 'display:flex;flex-direction:column;gap:12px;min-height:300px;';
		this.contextArea = document.createElement('div');


		const tabs = document.createElement('div');
		tabs.style.cssText =
			'display:flex;gap:8px;padding:4px 8px;border-radius:8px;background:#f2f3f5;';
		this.tabDocQaCell = document.createElement('div');
		this.tabDocQaCell.style.cssText = 'flex:1;border-radius:8px;background:#fff;';
		this.tabDocQa = this.createTab('文档Q&A', 'doc_qa');
		this.tabDocQaCell.appendChild(this.tabDocQa);
		this.tabChatCell = document.createElement('div');
		this.tabChatCell.style.cssText =
			'flex:1;border-radius:8px;background:transparent;';
		this.tabChat = this.createTab('聊天', 'chat');
		this.tabChatCell.appendChild(this.tabChat);
		tabs.appendChild(this.tabDocQaCell);
		tabs.appendChild(this.tabChatCell);
		this.content.appendChild(tabs);

		this.messages = document.createElement('div');
		this.messages.setAttribute('aria-live', 'polite');
		this.messages.style.cssText =
			'display:flex;flex-direction:column;gap:8px;min-height:160px;';
		this.content.appendChild(this.messages);

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		this.status.style.cssText = 'min-height:20px;color:#5f6368;font-size:13px;';
		this.content.appendChild(this.status);

		this.inputRow = document.createElement('div');
		this.inputRow.style.cssText =
			'display:flex;align-items:center;gap:8px;min-height:48px;padding:6px 6px 6px 16px;' +
			'border-radius:24px;background:#f0f4f9;';
		this.input = document.createElement('textarea');
		this.input.rows = 1;
		this.input.maxLength = 4000;
		this.input.placeholder = '发消息...';
		this.input.setAttribute('aria-label', 'AI 消息');
		this.input.setAttribute('enterkeyhint', 'send');
		this.input.setAttribute('inputmode', 'text');
		this.input.setAttribute('autocomplete', 'off');
		this.input.setAttribute('autocorrect', 'on');
		this.input.style.cssText =
			'flex:1;box-sizing:border-box;resize:none;max-height:72px;padding:8px 0;border:none;' +
			'background:transparent;font:inherit;font-size:16px;color:#202124;outline:none;' +
			'-webkit-user-select:text;user-select:text;';
		this.input.addEventListener('blur', () => this.restoreFocusIfStolen());
		this.inputRow.appendChild(this.input);
		this.sendButton = document.createElement('button');
		this.sendButton.type = 'button';
		this.sendButton.setAttribute('aria-label', '发送');
		this.sendButton.style.cssText =
			'display:flex;align-items:center;justify-content:center;flex-shrink:0;' +
			'width:36px;height:36px;padding:0;border:none;border-radius:50%;cursor:pointer;' +
			'background:linear-gradient(135deg,#1DA8FF 0%,#DD00CE 100%);';
		this.sendButton.innerHTML = MOBILE_AI_SEND_PLANE_SVG;
		this.sendButton.onclick = () => this.onSendOrStop();
		this.inputRow.appendChild(this.sendButton);
		this.content.appendChild(this.inputRow);

		this.clearLink = document.createElement('button');
		this.clearLink.type = 'button';
		this.clearLink.textContent = '清空对话';
		this.clearLink.className = 'mobile-ai-assistant-clear';
		this.clearLink.style.cssText =
			'align-self:flex-end;padding:0;border:none;background:none;font:inherit;' +
			'font-size:13px;color:#5f6368;cursor:pointer;';
		this.clearLink.onclick = () => this.controller.clear();
		this.content.appendChild(this.clearLink);

		this.sheet.setBody(this.content);
		this.unsubscribe = this.controller.subscribe(() => this.render());
		this.render();
	}

	open(): void {
		this.controller.onDocumentOpened();
		this.refreshDocQaLabel();
		this.setIosCalcKeyboardLayout(this.isIosCalc());
		this.sheet.open(this.input);
		this.render();
	}

	close(): void {
		this.input.blur();
		this.sheet.close();
		this.setIosCalcKeyboardLayout(false);
	}

	private isIosCalc(): boolean {
		return (
			!!(window as any).ThisIsTheiOSApp &&
			(window as any).app?.map?.getDocType?.() === 'spreadsheet'
		);
	}

	private setIosCalcKeyboardLayout(enabled: boolean): void {
		if (enabled) {
			const tabs = this.tabDocQaCell.parentElement;
			this.content.insertBefore(this.contextArea, this.content.firstChild);
			if (tabs) this.contextArea.appendChild(tabs);
			this.contextArea.append(this.messages, this.status);
			this.contextArea.style.cssText =
				'display:flex;flex-direction:column;gap:12px;flex:1 1 auto;min-height:0;overflow:auto;';
		} else if (this.contextArea.parentElement) {
			while (this.contextArea.firstChild) {
				this.content.insertBefore(this.contextArea.firstChild, this.contextArea);
			}
			this.contextArea.remove();
		}
		this.content.style.flex = enabled ? '1 1 auto' : '';
		this.content.style.minHeight = enabled ? '0' : '300px';
		this.messages.style.flex = enabled ? '1 1 auto' : '';
		this.messages.style.minHeight = enabled ? '0' : '160px';
		this.messages.style.overflow = enabled ? 'auto' : '';
		this.messages.style.setProperty(
			'-webkit-overflow-scrolling',
			enabled ? 'touch' : '',
		);
		this.inputRow.style.flex = enabled ? '0 0 auto' : '';
		this.clearLink.style.flex = enabled ? '0 0 auto' : '';
	}

	private restoreFocusIfStolen(): void {
		window.setTimeout(() => {
			if (!this.sheet.root.parentElement || this.input.disabled) {
				return;
			}
			const active = document.activeElement as HTMLElement | null;
			if (active && this.sheet.root.contains(active)) {
				return;
			}
			const mapContainer =
				typeof window !== 'undefined' ? (window as any).app?.map?.getContainer?.() : null;
			const shouldRefocus =
				active === mapContainer ||
				(active?.classList && active.classList.contains('clipboard'));
			if (shouldRefocus) {
				this.input.focus();
			}
		}, 0);
	}

	refreshDocQaLabel(): void {
		const docType =
			typeof window !== 'undefined' && (window as any).app?.map?.getDocType
				? (window as any).app.map.getDocType()
				: '';
		this.tabDocQa.textContent = docType === 'spreadsheet' ? '表格Q&A' : '文档Q&A';
	}

	private createTab(
		label: string,
		mode: 'doc_qa' | 'chat',
	): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.style.cssText =
			'width:100%;min-height:36px;border:none;background:transparent;font:inherit;' +
			'font-size:16px;cursor:pointer;';
		button.onclick = () => {
			this.controller.setMode(mode);
			this.render();
		};
		return button;
	}

	private onSendOrStop(): void {
		const state = this.controller.getState();
		if (state.status === 'loading' || state.status === 'streaming') {
			this.controller.cancel();
			return;
		}
		this.send();
	}

	private send(): void {
		const mode = this.controller.getState().mode;
		const entry = MobileAiUiCatalog.getEntry(mode);
		if (!entry || !entry.iosSupport) {
			this.status.textContent = 'iOS 尚未支持此 AI 助手请求';
			return;
		}
		if (this.controller.send(this.input.value, mode)) {
			this.input.value = '';
		}
	}

	private render(): void {
		const state = this.controller.getState();
		const modeEntry = MobileAiUiCatalog.getEntry(state.mode);
		const isDocQa = state.mode === 'doc_qa';
		this.tabDocQaCell.style.background = isDocQa ? '#fff' : 'transparent';
		this.tabChatCell.style.background = isDocQa ? 'transparent' : '#fff';
		this.tabDocQa.style.color = isDocQa ? '#101010' : '#6a6a6a';
		this.tabChat.style.color = isDocQa ? '#6a6a6a' : '#101010';
		this.tabDocQa.disabled = false;
		this.tabChat.disabled = false;

		this.messages.replaceChildren();
		state.messages.forEach((message) => {
			const bubble = document.createElement('div');
			bubble.style.cssText =
				'padding:10px 12px;border-radius:10px;line-height:1.5;' +
				(message.role === 'user'
					? 'align-self:flex-end;background:#e4f0ff;'
					: 'align-self:flex-start;background:#f1f3f4;');
			if (message.role === 'assistant' && message.content) {
				MobileAiResultRenderer.renderInto(bubble, message.content);
			} else {
				bubble.textContent = message.content || '正在生成…';
			}
			this.messages.appendChild(bubble);
		});

		const busy = state.status === 'loading' || state.status === 'streaming';
		this.sendButton.disabled = !busy && !modeEntry?.iosSupport;
		this.sendButton.innerHTML = busy ? MOBILE_AI_STOP_SVG : MOBILE_AI_SEND_PLANE_SVG;
		this.sendButton.setAttribute('aria-label', busy ? '停止' : '发送');
		this.input.disabled = busy;
		this.status.textContent =
			state.error ||
			(!modeEntry?.iosSupport
				? 'iOS 尚未支持此 AI 助手请求'
				: state.status === 'loading'
					? 'AI 正在生成...'
					: state.status === 'streaming'
						? 'AI 正在输出...'
						: '');
	}
}
