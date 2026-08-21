/*
 * Writer P0 AI interaction state machine.
 *
 * The controller owns selection/session filtering and preview state. Network
 * access and credentials remain in the native implementation.
 */

interface WriterAiBridgeLike {
	request(payload: { [key: string]: any }): string;
	cancel(requestId: string): boolean;
	accept(requestId: string, text: string): boolean;
	getSelectedText(): string;
	isAvailable(): boolean;
	subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void;
}

interface WriterAiDocumentAdapter {
	pastePlainText(text: string): boolean;
	pasteHtml?(html: string, plainText: string): boolean;
	replaceSelection?(text: string): boolean;
	appendAfterSelection?(text: string): boolean;
	insertAtEnd?(text: string): boolean;
	copyText?(text: string): boolean;
}

interface WriterAiControllerState {
	state: 'idle' | 'loading' | 'streaming' | 'ready' | 'accepted' | 'cancelled' | 'error';
	requestId?: string;
	taskType?: string;
	preview: string;
	error?: string;
}

type WriterAiStateListener = (state: WriterAiControllerState) => void;

class WriterAiController {
	private readonly bridge: WriterAiBridgeLike;
	private readonly documentAdapter: WriterAiDocumentAdapter;
	private readonly listeners: WriterAiStateListener[] = [];
	private readonly unsubscribeBridge: () => void;
	private state: WriterAiControllerState = { state: 'idle', preview: '' };
	private originalSelection = '';
	private resultMode: 'replaceSelection' | 'appendAfterSelection' = 'replaceSelection';
	private lastRequest: {
		taskType: string;
		context: { [key: string]: any };
		selection?: string;
	} | null = null;

	constructor(
		bridge: WriterAiBridgeLike = MobileAiBridge.getInstance(),
		documentAdapter: WriterAiDocumentAdapter = WriterAiController.defaultDocumentAdapter(),
	) {
		this.bridge = bridge;
		this.documentAdapter = documentAdapter;
		this.unsubscribeBridge = bridge.subscribe((message) => {
			this.handleNativeMessage(message);
		});
	}

	static getInstance(): WriterAiController {
		const existing =
			typeof window !== 'undefined'
				? (window as any).__coolWriterAiController
				: null;
		if (existing instanceof WriterAiController) {
			return existing;
		}
		const controller = new WriterAiController();
		if (typeof window !== 'undefined') {
			(window as any).__coolWriterAiController = controller;
			(window as any).WriterAiController = controller;
		}
		return controller;
	}

	getState(): WriterAiControllerState {
		return {
			state: this.state.state,
			requestId: this.state.requestId,
			taskType: this.state.taskType,
			preview: this.state.preview,
			error: this.state.error,
		};
	}

	subscribe(listener: WriterAiStateListener): () => void {
		this.listeners.push(listener);
		return () => {
			const index = this.listeners.indexOf(listener);
			if (index >= 0) {
				this.listeners.splice(index, 1);
			}
		};
	}

	request(
		taskType: string,
		context: { [key: string]: any } = {},
		selectionOverride?: string,
	): string | null {
		if (this.state.state === 'loading' || this.state.state === 'streaming') {
			this.setError('已有 AI 请求正在进行');
			return null;
		}
		if (!this.bridge.isAvailable()) {
			this.setError('NativeBridge 不可用');
			return null;
		}
		const map = typeof window !== 'undefined' ? (window as any).app?.map : null;
		if (map && map.getDocType && map.getDocType() !== 'text') {
			this.setError('Writer AI 仅支持 Writer 文档');
			return null;
		}

		const selection =
			typeof selectionOverride === 'string'
				? selectionOverride
				: this.bridge.getSelectedText();
		const payload = {
			taskType,
			selection,
			context: context || {},
		};
		const validation = WriterAiCatalog.validateRequest(payload);
		if (!validation.valid) {
			this.setError(this.messageForError(validation.errorCode || 'invalid_payload'));
			return null;
		}
		const task = WriterAiCatalog.getTask(taskType);
		if (!task) {
			this.setError('不支持的 Writer AI 任务');
			return null;
		}

		this.originalSelection = selection;
		this.resultMode = task.resultMode;
		this.lastRequest = {
			taskType,
			context: context || {},
			selection,
		};
		this.state = {
			state: 'loading',
			taskType,
			preview: '',
		};
		const requestId = this.bridge.request(payload);
		this.state.requestId = requestId;
		this.notify();
		return requestId;
	}

	cancel(): boolean {
		if (!this.state.requestId) {
			return false;
		}
		const cancelled = this.bridge.cancel(this.state.requestId);
		if (cancelled) {
			this.state = {
				...this.state,
				state: 'cancelled',
			};
			this.notify();
		}
		return cancelled;
	}

	regenerate(): string | null {
		if (!this.lastRequest) {
			return null;
		}
		if (this.state.state === 'loading' || this.state.state === 'streaming') {
			return null;
		}
		return this.request(
			this.lastRequest.taskType,
			this.lastRequest.context,
			this.lastRequest.selection,
		);
	}

	copy(): boolean {
		if (!this.state.preview) {
			return false;
		}
		if (this.documentAdapter.copyText) {
			return this.documentAdapter.copyText(this.state.preview);
		}
		try {
			const clipboard = (navigator as any).clipboard;
			if (!clipboard || typeof clipboard.writeText !== 'function') {
				return false;
			}
			clipboard.writeText(this.state.preview);
			return true;
		} catch (_error) {
			return false;
		}
	}

	accept(html?: string): boolean {
		if (
			this.state.state !== 'ready' ||
			!this.state.requestId ||
			!this.state.preview
		) {
			return false;
		}
		const text = this.state.preview;
		let pasted = false;
		if (
			html &&
			this.resultMode === 'replaceSelection' &&
			this.documentAdapter.pasteHtml
		) {
			pasted = this.documentAdapter.pasteHtml(html, text);
		} else if (
			this.resultMode === 'appendAfterSelection' &&
			this.documentAdapter.appendAfterSelection
		) {
			pasted = this.documentAdapter.appendAfterSelection(text);
		} else if (this.resultMode === 'replaceSelection' && this.documentAdapter.replaceSelection) {
			pasted = this.documentAdapter.replaceSelection(text);
		} else if (this.documentAdapter.insertAtEnd) {
			pasted = this.documentAdapter.insertAtEnd(text);
		} else {
			pasted = this.documentAdapter.pastePlainText(text);
		}
		if (!pasted) {
			this.setError('无法将 AI 结果插入文档');
			return false;
		}
		const accepted = this.bridge.accept(this.state.requestId, this.state.preview);
		if (!accepted) {
			this.setError('NativeBridge 未确认 AI 结果');
			return false;
		}
		this.state = {
			...this.state,
			state: 'accepted',
		};
		this.notify();
		return true;
	}

	dispose(): void {
		if (
			this.state.requestId &&
			(this.state.state === 'loading' || this.state.state === 'streaming')
		) {
			this.bridge.cancel(this.state.requestId);
		}
		this.unsubscribeBridge();
		this.listeners.splice(0);
	}

	private handleNativeMessage(message: NativeBridgeEnvelope): void {
		const requestId = this.state.requestId;
		if (!requestId || message.requestId !== requestId) {
			return;
		}
		if (
			message.documentSessionId &&
			message.documentSessionId !==
				(typeof window !== 'undefined'
					? NativeBridge.getInstance().getDocumentSessionId()
					: message.documentSessionId)
		) {
			return;
		}
		const payload = message.payload || {};
		switch (message.type) {
			case 'ai.state':
				if (payload.state === 'loading' || payload.state === 'streaming') {
					this.state = { ...this.state, state: payload.state };
				} else if (payload.state === 'cancelled') {
					this.state = { ...this.state, state: 'cancelled' };
				} else if (payload.accepted) {
					this.state = { ...this.state, state: 'accepted' };
				}
				this.notify();
				break;
			case 'ai.stream':
				if (typeof payload.delta !== 'string') {
					return;
				}
				this.state = {
					...this.state,
					state: 'streaming',
					preview: this.state.preview + payload.delta,
				};
				this.notify();
				break;
			case 'ai.done':
				this.state = {
					...this.state,
					state: 'ready',
					preview:
						typeof payload.fullText === 'string'
							? payload.fullText
							: this.state.preview,
				};
				this.notify();
				break;
			case 'ai.error':
			case 'native.error':
				this.setError(
					typeof payload.message === 'string'
						? payload.message
						: 'AI 请求失败',
				);
				break;
			default:
				break;
		}
	}

	private setError(message: string): void {
		this.state = {
			...this.state,
			state: 'error',
			error: message,
		};
		this.notify();
	}

	private messageForError(errorCode: string): string {
		const messages: { [code: string]: string } = {
			empty_selection: '请先选择要处理的文字',
			sensitive_field: '请求不能包含 API 凭据',
			invalid_polish_style: '润色风格无效',
			invalid_source_language: '源语言无效',
			invalid_target_language: '目标语言无效',
			invalid_context_field: '当前任务不支持该参数',
			unsupported_task_type: '不支持的 Writer AI 任务',
		};
		return messages[errorCode] || 'Writer AI 请求参数无效';
	}

	private notify(): void {
		const snapshot = this.getState();
		this.listeners.slice().forEach((listener) => listener(snapshot));
	}

	private static defaultDocumentAdapter(): WriterAiDocumentAdapter {
		return {
			pastePlainText(text: string): boolean {
				try {
					const clip = (window as any).app?.map?._clip;
					return !!clip && typeof clip.pastePlainText === 'function'
						? clip.pastePlainText(text)
						: false;
				} catch (_error) {
					return false;
				}
			},
			pasteHtml(html: string, plainText: string): boolean {
				try {
					const clip = (window as any).app?.map?._clip;
					return !!clip && typeof clip.pasteAiTextAsHtml === 'function'
						? clip.pasteAiTextAsHtml(html, plainText)
						: false;
				} catch (_error) {
					return false;
				}
			},
			copyText(text: string): boolean {
				try {
					const clipboard = (navigator as any).clipboard;
					if (!clipboard || typeof clipboard.writeText !== 'function') {
						return false;
					}
					clipboard.writeText(text);
					return true;
				} catch (_error) {
					return false;
				}
			},
		};
	}
}

if (typeof window !== 'undefined') {
	WriterAiController.getInstance();
}
