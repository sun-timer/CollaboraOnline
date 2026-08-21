/*
 * Android-style AI Assistant sheet: document Q&A and chat tabs.
 */

class MobileAiAssistantPanel {
	private readonly controller: MobileAiConversationController;
	private readonly sheet: MobileAiSheet;
	private readonly tabDocQa: HTMLButtonElement;
	private readonly tabChat: HTMLButtonElement;
	private readonly messages: HTMLDivElement;
	private readonly input: HTMLTextAreaElement;
	private readonly sendButton: HTMLButtonElement;
	private readonly stopButton: HTMLButtonElement;
	private readonly status: HTMLDivElement;
	private readonly unsubscribe: () => void;

	constructor() {
		this.controller = new MobileAiConversationController();
		this.sheet = new MobileAiSheet({
			title: 'AI 助手',
			onClose: () => this.controller.cancel(),
		});
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;min-height:300px;';

		const tabs = document.createElement('div');
		tabs.style.cssText =
			'display:flex;gap:4px;padding:4px;border-radius:8px;background:#eef1f5;';
		this.tabDocQa = this.createTab('文档 Q&A', 'doc_qa');
		this.tabChat = this.createTab('聊天', 'chat');
		tabs.appendChild(this.tabDocQa);
		tabs.appendChild(this.tabChat);
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
		inputRow.style.cssText = 'display:flex;gap:8px;align-items:flex-end;';
		this.input = document.createElement('textarea');
		this.input.rows = 2;
		this.input.maxLength = 4000;
		this.input.placeholder = '发消息...';
		this.input.setAttribute('aria-label', 'AI 消息');
		this.input.style.cssText =
			'flex:1;box-sizing:border-box;resize:vertical;padding:10px;border:1px solid #c8cdd3;' +
			'border-radius:10px;font:inherit;';
		inputRow.appendChild(this.input);
		this.sendButton = this.createButton('发送');
		this.sendButton.onclick = () => this.send();
		inputRow.appendChild(this.sendButton);
		this.stopButton = this.createButton('停止');
		this.stopButton.onclick = () => this.controller.cancel();
		inputRow.appendChild(this.stopButton);
		content.appendChild(inputRow);

		const clearButton = this.createButton('清空');
		clearButton.onclick = () => this.controller.clear();
		content.appendChild(clearButton);

		this.sheet.setBody(content);
		this.unsubscribe = this.controller.subscribe(() => this.render());
		this.render();
	}

	open(): void {
		this.sheet.open();
		this.render();
	}

	private createTab(
		label: string,
		mode: 'doc_qa' | 'chat',
	): HTMLButtonElement {
		const button = this.createButton(label);
		button.style.flex = '1';
		button.onclick = () => {
			this.controller.setMode(mode);
			this.render();
		};
		return button;
	}

	private createButton(label: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		return button;
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
		this.tabDocQa.disabled = state.mode === 'doc_qa';
		this.tabChat.disabled = state.mode === 'chat';
		this.messages.replaceChildren();
		state.messages.forEach((message) => {
			const bubble = document.createElement('div');
			bubble.textContent = message.content || '正在生成…';
			bubble.style.cssText =
				'padding:10px 12px;border-radius:10px;white-space:pre-wrap;' +
				(message.role === 'user'
					? 'align-self:flex-end;background:#e4f0ff;'
					: 'align-self:flex-start;background:#f1f3f4;');
			this.messages.appendChild(bubble);
		});
		this.sendButton.disabled =
			!modeEntry?.iosSupport ||
			state.status === 'loading' ||
			state.status === 'streaming';
		this.stopButton.disabled =
			state.status !== 'loading' && state.status !== 'streaming';
		this.input.disabled = state.status === 'loading' || state.status === 'streaming';
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
