/*
 * Conversation state for the Android-style AI Assistant sheet.
 *
 * Conversation requests never use Writer selection replacement semantics.
 * Document context and credentials are owned by the native implementation.
 */

interface MobileAiConversationBridgeLike {
	request(payload: { [key: string]: any }): string;
	cancel(requestId: string): boolean;
	subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void;
	isAvailable(): boolean;
	getSelectedText(): string;
}

interface MobileAiConversationMessage {
	role: 'user' | 'assistant';
	content: string;
}

interface MobileAiConversationState {
	mode: 'doc_qa' | 'chat';
	status: 'idle' | 'loading' | 'streaming' | 'ready' | 'error' | 'cancelled';
	requestId?: string;
	messages: MobileAiConversationMessage[];
	error?: string;
}

type MobileAiConversationStateListener = (
	state: MobileAiConversationState,
) => void;

class MobileAiConversationController {
	private readonly bridge: MobileAiConversationBridgeLike;
	private readonly listeners: MobileAiConversationStateListener[] = [];
	private readonly unsubscribeBridge: () => void;
	private state: MobileAiConversationState = {
		mode: 'doc_qa',
		status: 'idle',
		messages: [],
	};
	private activeAssistantMessageIndex = -1;

	constructor(
		bridge: MobileAiConversationBridgeLike = MobileAiBridge.getInstance(),
	) {
		this.bridge = bridge;
		this.unsubscribeBridge = bridge.subscribe((message) => {
			this.handleNativeMessage(message);
		});
	}

	getState(): MobileAiConversationState {
		return {
			mode: this.state.mode,
			status: this.state.status,
			requestId: this.state.requestId,
			messages: this.state.messages.map((message) => ({ ...message })),
			error: this.state.error,
		};
	}

	subscribe(listener: MobileAiConversationStateListener): () => void {
		this.listeners.push(listener);
		return () => {
			const index = this.listeners.indexOf(listener);
			if (index >= 0) {
				this.listeners.splice(index, 1);
			}
		};
	}

	send(prompt: string, mode: 'doc_qa' | 'chat' = this.state.mode): string | null {
		const text = typeof prompt === 'string' ? prompt.trim() : '';
		if (!text) {
			this.setError('请输入问题或消息');
			return null;
		}
		if (this.state.status === 'loading' || this.state.status === 'streaming') {
			this.setError('已有 AI 请求正在进行');
			return null;
		}
		if (!this.bridge.isAvailable()) {
			this.setError('NativeBridge 不可用');
			return null;
		}

		const history = this.state.messages.map((message) => ({ ...message }));
		const requestId = this.bridge.request({
			taskType: mode,
			selection: mode === 'doc_qa' ? this.bridge.getSelectedText() : '',
			history,
			context: { prompt: text },
			docQaFirstTurn: mode === 'doc_qa' && history.length === 0,
		});
		this.activeAssistantMessageIndex = this.state.messages.length + 1;
		this.state = {
			mode,
			status: 'loading',
			requestId,
			messages: [
				...history,
				{ role: 'user', content: text },
				{ role: 'assistant', content: '' },
			],
		};
		this.notify();
		return requestId;
	}

	setMode(mode: 'doc_qa' | 'chat'): void {
		if (this.state.status === 'loading' || this.state.status === 'streaming') {
			return;
		}
		this.state = { ...this.state, mode, error: undefined };
		this.notify();
	}

	cancel(): boolean {
		if (!this.state.requestId) {
			return false;
		}
		const cancelled = this.bridge.cancel(this.state.requestId);
		if (cancelled) {
			this.state = { ...this.state, status: 'cancelled' };
			this.notify();
		}
		return cancelled;
	}

	clear(): void {
		if (this.state.status === 'loading' || this.state.status === 'streaming') {
			return;
		}
		this.state = {
			mode: this.state.mode,
			status: 'idle',
			messages: [],
		};
		this.activeAssistantMessageIndex = -1;
		this.notify();
	}

	dispose(): void {
		if (
			this.state.requestId &&
			(this.state.status === 'loading' || this.state.status === 'streaming')
		) {
			this.bridge.cancel(this.state.requestId);
		}
		this.unsubscribeBridge();
		this.listeners.splice(0);
	}

	private handleNativeMessage(message: NativeBridgeEnvelope): void {
		if (!this.state.requestId || message.requestId !== this.state.requestId) {
			return;
		}
		const payload = message.payload || {};
		switch (message.type) {
			case 'ai.state':
				if (payload.state === 'loading' || payload.state === 'streaming') {
					this.state = { ...this.state, status: payload.state };
					this.notify();
				} else if (payload.state === 'cancelled') {
					this.state = { ...this.state, status: 'cancelled' };
					this.notify();
				}
				break;
			case 'ai.stream':
				if (typeof payload.delta !== 'string') {
					return;
				}
				this.appendAssistantText(payload.delta);
				this.state = { ...this.state, status: 'streaming' };
				this.notify();
				break;
			case 'ai.done':
				if (typeof payload.fullText === 'string') {
					this.replaceAssistantText(payload.fullText);
				}
				this.state = { ...this.state, status: 'ready' };
				this.notify();
				break;
			case 'ai.error':
			case 'native.error':
				this.setError(
					typeof payload.message === 'string' ? payload.message : 'AI 请求失败',
				);
				break;
			default:
				break;
		}
	}

	private appendAssistantText(delta: string): void {
		if (this.activeAssistantMessageIndex < 0) {
			return;
		}
		const message = this.state.messages[this.activeAssistantMessageIndex];
		if (!message || message.role !== 'assistant') {
			return;
		}
		message.content += delta;
	}

	private replaceAssistantText(text: string): void {
		if (this.activeAssistantMessageIndex < 0) {
			return;
		}
		const message = this.state.messages[this.activeAssistantMessageIndex];
		if (message && message.role === 'assistant') {
			message.content = text;
		}
	}

	private setError(message: string): void {
		this.state = { ...this.state, status: 'error', error: message };
		this.notify();
	}

	private notify(): void {
		const snapshot = this.getState();
		this.listeners.slice().forEach((listener) => listener(snapshot));
	}
}
