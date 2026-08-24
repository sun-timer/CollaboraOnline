/*
 * Calc P1 AI interaction state machine.
 *
 * Network and credentials stay in the native AIService. This controller owns
 * spreadsheet gating, preview state and formula insertion.
 */

interface CalcAiBridgeLike {
	request(payload: { [key: string]: any }): string;
	cancel(requestId: string): boolean;
	accept(requestId: string, text: string): boolean;
	isAvailable(): boolean;
	subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void;
}

interface CalcAiDocumentAdapter {
	pastePlainText(text: string): boolean;
	copyText?(text: string): boolean;
}

interface CalcAiControllerState {
	state: 'idle' | 'loading' | 'streaming' | 'ready' | 'accepted' | 'cancelled' | 'error';
	requestId?: string;
	taskType?: string;
	preview: string;
	error?: string;
}

type CalcAiStateListener = (state: CalcAiControllerState) => void;

class CalcAiController {
	private readonly bridge: CalcAiBridgeLike;
	private readonly documentAdapter: CalcAiDocumentAdapter;
	private readonly listeners: CalcAiStateListener[] = [];
	private readonly unsubscribeBridge: () => void;
	private state: CalcAiControllerState = { state: 'idle', preview: '' };
	private resultMode: 'insertFormula' | 'conversation' = 'insertFormula';
	private lastRequest: {
		taskType: string;
		context: { [key: string]: any };
		selection: string;
	} | null = null;

	constructor(
		bridge: CalcAiBridgeLike = MobileAiBridge.getInstance(),
		documentAdapter: CalcAiDocumentAdapter = CalcAiController.defaultDocumentAdapter(),
	) {
		this.bridge = bridge;
		this.documentAdapter = documentAdapter;
		this.unsubscribeBridge = bridge.subscribe((message) => {
			this.handleNativeMessage(message);
		});
	}

	static getInstance(): CalcAiController {
		const existing =
			typeof window !== 'undefined'
				? (window as any).__coolCalcAiController
				: null;
		if (existing instanceof CalcAiController) {
			return existing;
		}
		const controller = new CalcAiController();
		if (typeof window !== 'undefined') {
			(window as any).__coolCalcAiController = controller;
			(window as any).CalcAiController = controller;
		}
		return controller;
	}

	getState(): CalcAiControllerState {
		return {
			state: this.state.state,
			requestId: this.state.requestId,
			taskType: this.state.taskType,
			preview: this.state.preview,
			error: this.state.error,
		};
	}

	subscribe(listener: CalcAiStateListener): () => void {
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
		prompt: string,
		contextOverride?: { [key: string]: any },
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
		if (map && map.getDocType && map.getDocType() !== 'spreadsheet') {
			this.setError('Calc AI 仅支持电子表格文档');
			return null;
		}

		const task = CalcAiCatalog.getTask(taskType);
		if (!task) {
			this.setError('不支持的 Calc AI 任务');
			return null;
		}

		const context =
			contextOverride ||
			(taskType === 'calc_data_analysis'
				? CalcAiContext.buildAnalysisContext()
				: CalcAiContext.buildFormulaContext());
		const payload = {
			taskType,
			selection: typeof prompt === 'string' ? prompt : '',
			context,
		};
		const validation = CalcAiCatalog.validateRequest(payload);
		if (!validation.valid) {
			this.setError(this.messageForError(validation.errorCode || 'invalid_payload'));
			return null;
		}

		this.resultMode = task.resultMode;
		this.lastRequest = {
			taskType,
			context,
			selection: payload.selection,
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
			this.lastRequest.selection,
			this.lastRequest.context,
		);
	}

	copy(): boolean {
		if (!this.state.preview) {
			return false;
		}
		const text =
			this.resultMode === 'insertFormula'
				? CalcAiCatalog.normalizeFormula(this.state.preview)
				: this.state.preview;
		if (this.documentAdapter.copyText) {
			return this.documentAdapter.copyText(text);
		}
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
	}

	accept(): boolean {
		if (
			this.resultMode !== 'insertFormula' ||
			this.state.state !== 'ready' ||
			!this.state.requestId ||
			!this.state.preview
		) {
			return false;
		}
		const formula = CalcAiCatalog.normalizeFormula(this.state.preview);
		if (!formula) {
			this.setError('AI 未返回有效公式');
			return false;
		}
		const pasted = this.documentAdapter.pastePlainText(formula);
		if (!pasted) {
			const copied = this.copy();
			this.setError(
				copied
					? '无法写入单元格，公式已复制到剪贴板'
					: '无法将公式写入单元格',
			);
			return false;
		}
		const accepted = this.bridge.accept(this.state.requestId, formula);
		if (!accepted) {
			this.setError('NativeBridge 未确认 AI 结果');
			return false;
		}
		this.state = {
			...this.state,
			state: 'accepted',
			preview: formula,
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
			empty_prompt: '请输入公式或分析需求',
			empty_range: '请先选择要分析的单元格区域',
			sensitive_field: '请求不能包含 API 凭据',
			invalid_context_field: '当前任务不支持该参数',
			unsupported_task_type: '不支持的 Calc AI 任务',
		};
		return messages[errorCode] || 'Calc AI 请求参数无效';
	}

	private notify(): void {
		const snapshot = this.getState();
		this.listeners.slice().forEach((listener) => listener(snapshot));
	}

	private static defaultDocumentAdapter(): CalcAiDocumentAdapter {
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
	CalcAiController.getInstance();
}
