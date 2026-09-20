/*
 * Android-style AI Assistant sheet: document Q&A and chat tabs.
 */

/** Android lolib_ic_ai_send_plane */
const MOBILE_AI_SEND_PLANE_SVG =
	'<svg viewBox="0 0 24 24" width="20" height="20" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
	'<path fill="#ffffff" d="M12,3 L16,18 L12,15 L8,18 Z"/></svg>';

const MOBILE_AI_STOP_SVG =
	'<svg viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
	'<rect x="7" y="7" width="10" height="10" rx="1.5" fill="#ffffff"/></svg>';

class MobileAiAssistantPanel {
	private readonly controller: MobileAiConversationController;
	private readonly sheet: MobileAiSheet;
	private readonly tabDocQa: HTMLButtonElement;
	private readonly tabChat: HTMLButtonElement;
	private readonly tabDocQaCell: HTMLDivElement;
	private readonly tabChatCell: HTMLDivElement;
	private readonly messages: HTMLDivElement;
	private readonly input: HTMLTextAreaElement;
	private readonly sendButton: HTMLButtonElement;
	private readonly status: HTMLDivElement;
	private readonly clearLink: HTMLButtonElement;
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = MobileAiConversationController.shared();
		this.sheet = new MobileAiSheet({
			title: 'AI助手',
			onClose: () => this.controller.cancel(),
		});
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;min-height:300px;';

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
		content.appendChild(tabs);

		this.messages = document.createElement('div');
		this.messages.setAttribute('aria-live', 'polite');
		this.messages.style.cssText =
			'display:flex;flex-direction:column;gap:8px;min-height:160px;';
		content.appendChild(this.messages);

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		this.status.style.cssText = 'min-height:20px;color:#5f6368;font-size:13px;';
		content.appendChild(this.status);

		const inputRow = document.createElement('div');
		inputRow.style.cssText =
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
		inputRow.appendChild(this.input);
		this.sendButton = document.createElement('button');
		this.sendButton.type = 'button';
		this.sendButton.setAttribute('aria-label', '发送');
		this.sendButton.style.cssText =
			'display:flex;align-items:center;justify-content:center;flex-shrink:0;' +
			'width:36px;height:36px;padding:0;border:none;border-radius:50%;cursor:pointer;' +
			'background:linear-gradient(315deg,#3a7bff 0%,#b925f3 100%);';
		this.sendButton.innerHTML = MOBILE_AI_SEND_PLANE_SVG;
		this.sendButton.onclick = () => this.onSendOrStop();
		inputRow.appendChild(this.sendButton);
		content.appendChild(inputRow);

		this.clearLink = document.createElement('button');
		this.clearLink.type = 'button';
		this.clearLink.textContent = '清空对话';
		this.clearLink.style.cssText =
			'align-self:flex-end;padding:0;border:none;background:none;font:inherit;' +
			'font-size:13px;color:#5f6368;cursor:pointer;';
		this.clearLink.onclick = () => this.controller.clear();
		content.appendChild(this.clearLink);

		this.sheet.setBody(content);
		this.unsubscribe = this.controller.subscribe(() => this.render());
		this.render();
	}

	open(): void {
		this.controller.onDocumentOpened();
		this.refreshDocQaLabel();
		this.sheet.open(this.input);
		this.render();
	}

	close(): void {
		this.input.blur();
		this.sheet.close();
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
