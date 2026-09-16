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
	extractFullText?(): Promise<string>;
	loadConversationHistory?(
		mode: 'doc_qa' | 'chat',
	): Promise<MobileAiConversationMessage[]>;
	saveConversationHistory?(
		mode: 'doc_qa' | 'chat',
		messages: MobileAiConversationMessage[],
	): Promise<void>;
	clearConversationHistory?(mode: 'doc_qa' | 'chat'): Promise<void>;
	conversationPersistenceAvailable?(): boolean;
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
	private static sharedInstance: MobileAiConversationController | null = null;

	private readonly bridge: MobileAiConversationBridgeLike;
	private readonly listeners: MobileAiConversationStateListener[] = [];
	private readonly unsubscribeBridge: () => void;
	private readonly docQaMessages: MobileAiConversationMessage[] = [];
	private readonly chatMessages: MobileAiConversationMessage[] = [];
	private state: Omit<MobileAiConversationState, 'messages'> & {
		messages?: MobileAiConversationMessage[];
	} = {
		mode: 'doc_qa',
		status: 'idle',
	};
	private activeAssistantMessageIndex = -1;
	private documentSessionBound = false;
	private historyLoadGeneration = 0;
	private docQaFullText = '';
	private extractGeneration = 0;

	static shared(): MobileAiConversationController {
		if (!MobileAiConversationController.sharedInstance) {
			MobileAiConversationController.sharedInstance =
				new MobileAiConversationController();
		}
		return MobileAiConversationController.sharedInstance;
	}

	static resetSharedForTests(): void {
		if (MobileAiConversationController.sharedInstance) {
			MobileAiConversationController.sharedInstance.dispose();
			MobileAiConversationController.sharedInstance = null;
		}
	}

	clearDocumentSession(): void {
		if (
			this.state.requestId &&
			(this.state.status === 'loading' || this.state.status === 'streaming')
		) {
			this.bridge.cancel(this.state.requestId);
		}
		this.docQaMessages.splice(0);
		this.chatMessages.splice(0);
		this.documentSessionBound = false;
		this.historyLoadGeneration += 1;
		this.extractGeneration += 1;
		this.docQaFullText = '';
		this.activeAssistantMessageIndex = -1;
		this.state = { mode: 'doc_qa', status: 'idle' };
		this.notify();
	}

	constructor(
		bridge: MobileAiConversationBridgeLike = MobileAiBridge.getInstance(),
	) {
		this.bridge = bridge;
		this.unsubscribeBridge = bridge.subscribe((message) => {
			this.handleNativeMessage(message);
		});
	}

	onDocumentOpened(): void {
		if (this.documentSessionBound) {
			return;
		}
		this.documentSessionBound = true;
		this.loadPersistedHistories();
	}

	getState(): MobileAiConversationState {
		return {
			mode: this.state.mode,
			status: this.state.status,
			requestId: this.state.requestId,
			messages: this.activeMessages().map((message) => ({ ...message })),
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

		const messages = this.activeMessages();
		const history = messages.map((message) => ({ ...message }));

		messages.push({ role: 'user', content: text });
		messages.push({ role: 'assistant', content: '' });
		this.activeAssistantMessageIndex = messages.length - 1;
		this.state = {
			...this.state,
			mode,
			status: 'loading',
			requestId: undefined,
			error: undefined,
		};
		this.notify();

		if (
			mode === 'doc_qa' &&
			history.length === 0 &&
			typeof this.bridge.extractFullText === 'function'
		) {
			const generation = ++this.extractGeneration;
			this.bridge.extractFullText().then(
				(fullText) => {
					if (generation !== this.extractGeneration) {
						return;
					}
					const docText =
						typeof fullText === 'string' ? fullText.trim() : '';
					if (!docText) {
						this.setError('文档全文提取失败，请稍后重试');
						return;
					}
					this.docQaFullText = docText;
					this.fireRequest(text, mode, history);
				},
				() => {
					if (generation !== this.extractGeneration) {
						return;
					}
					this.setError('文档全文提取失败，请稍后重试');
				},
			);
			return 'doc-qa-extract';
		}

		return this.fireRequest(text, mode, history);
	}

	setMode(mode: 'doc_qa' | 'chat'): void {
		if (this.state.status === 'loading' || this.state.status === 'streaming') {
			return;
		}
		if (this.state.mode === mode) {
			return;
		}
		this.state = { ...this.state, mode, error: undefined };
		this.activeAssistantMessageIndex = -1;
		this.notify();
	}

	cancel(): boolean {
		if (this.state.status === 'loading' && !this.state.requestId) {
			this.extractGeneration += 1;
			this.state = { ...this.state, status: 'cancelled' };
			this.notify();
			return true;
		}
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
		const mode = this.state.mode;
		const messages = this.activeMessages();
		messages.splice(0, messages.length);
		this.activeAssistantMessageIndex = -1;
		this.extractGeneration += 1;
		this.docQaFullText = '';
		this.state = {
			...this.state,
			status: 'idle',
			requestId: undefined,
			error: undefined,
		};
		this.clearPersistedMode(mode);
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
		this.docQaMessages.splice(0);
		this.chatMessages.splice(0);
		this.documentSessionBound = false;
		this.historyLoadGeneration += 1;
		this.extractGeneration += 1;
		this.docQaFullText = '';
		this.state = { mode: 'doc_qa', status: 'idle' };
		this.activeAssistantMessageIndex = -1;
	}

	private fireRequest(
		prompt: string,
		mode: 'doc_qa' | 'chat',
		history: MobileAiConversationMessage[],
	): string {
		const selection =
			mode === 'doc_qa'
				? this.docQaFullText || this.bridge.getSelectedText()
				: '';
		const requestId = this.bridge.request({
			taskType: mode,
			selection,
			history,
			context: { prompt },
			docQaFirstTurn: mode === 'doc_qa' && history.length === 0,
		});
		this.state = {
			...this.state,
			mode,
			status: 'loading',
			requestId,
			error: undefined,
		};
		this.persistMode(mode);
		this.notify();
		return requestId;
	}

	private activeMessages(): MobileAiConversationMessage[] {
		return this.state.mode === 'doc_qa'
			? this.docQaMessages
			: this.chatMessages;
	}

	private messagesForMode(mode: 'doc_qa' | 'chat'): MobileAiConversationMessage[] {
		return mode === 'doc_qa' ? this.docQaMessages : this.chatMessages;
	}

	private loadPersistedHistories(): void {
		if (!this.bridge.conversationPersistenceAvailable?.()) {
			return;
		}
		const generation = ++this.historyLoadGeneration;
		const loadMode = (mode: 'doc_qa' | 'chat') => {
			if (!this.bridge.loadConversationHistory) {
				return Promise.resolve([]);
			}
			return this.bridge.loadConversationHistory(mode);
		};
		Promise.all([loadMode('doc_qa'), loadMode('chat')]).then(
			([docQa, chat]) => {
				if (generation !== this.historyLoadGeneration) {
					return;
				}
				if (this.docQaMessages.length === 0) {
					this.docQaMessages.splice(
						0,
						this.docQaMessages.length,
						...docQa.map((message) => ({ ...message })),
					);
				}
				if (this.chatMessages.length === 0) {
					this.chatMessages.splice(
						0,
						this.chatMessages.length,
						...chat.map((message) => ({ ...message })),
					);
				}
				this.notify();
			},
		);
	}

	private persistMode(mode: 'doc_qa' | 'chat'): void {
		if (
			!this.bridge.conversationPersistenceAvailable?.() ||
			!this.bridge.saveConversationHistory
		) {
			return;
		}
		const messages = this.messagesForMode(mode).map((message) => ({
			...message,
		}));
		this.bridge.saveConversationHistory(mode, messages);
	}

	private clearPersistedMode(mode: 'doc_qa' | 'chat'): void {
		if (
			!this.documentSessionBound ||
			!this.bridge.conversationPersistenceAvailable?.() ||
			!this.bridge.clearConversationHistory
		) {
			return;
		}
		this.bridge.clearConversationHistory(mode);
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
				this.persistMode(this.state.mode);
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
		const messages = this.activeMessages();
		const message = messages[this.activeAssistantMessageIndex];
		if (!message || message.role !== 'assistant') {
			return;
		}
		message.content += delta;
	}

	private replaceAssistantText(text: string): void {
		if (this.activeAssistantMessageIndex < 0) {
			return;
		}
		const messages = this.activeMessages();
		const message = messages[this.activeAssistantMessageIndex];
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

if (typeof window !== 'undefined') {
	(window as any).MobileAiConversationController = MobileAiConversationController;
}
