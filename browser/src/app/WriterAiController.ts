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
	getSelectedTextAsync?(): Promise<string>;
	isAvailable(): boolean;
	subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void;
}

interface WriterAiDocumentAdapter {
	pastePlainText(text: string): boolean;
	postMobileMessage?(message: string): void;
	pasteHtml?(html: string, plainText: string): boolean;
	insertPresentationHtml?(html: string, sourceRequestId: string): Promise<boolean>;
	insertWriterHtml?(
		html: string,
		sourceRequestId: string,
		resultMode: 'replaceSelection' | 'appendAfterSelection' | 'insertAtEnd',
	): Promise<boolean>;
	replaceSelection?(text: string): boolean;
	appendAfterSelection?(text: string, originalSelection?: string): boolean;
	insertAtEnd?(text: string): boolean;
	insertHtmlAtEnd?(html: string, plainText: string): boolean;
	copyText?(text: string): boolean | Promise<boolean>;
}

interface WriterAiControllerState {
	state: 'idle' | 'loading' | 'streaming' | 'ready' | 'inserting' | 'accepted' | 'cancelled' | 'error';
	requestId?: string;
	taskType?: string;
	preview: string;
	error?: string;
	warning?: string;
}

type WriterAiStateListener = (state: WriterAiControllerState) => void;

function insertIosNativeHtml(
	html: string,
	sourceRequestId: string,
	resultMode?: 'replaceSelection' | 'appendAfterSelection' | 'insertAtEnd',
): Promise<boolean> {
	const nativeBridge = NativeBridge.getInstance();
	const requestId = (resultMode ? 'ai-writer-insert-' : 'ai-insert-') + sourceRequestId;
	const documentSessionId = nativeBridge.getDocumentSessionId();
	const map = (window as any).app?.map;
	const viewId = map?._docLayer?._viewId;
	if (!sourceRequestId || !Number.isInteger(viewId) || viewId < 0) {
		return Promise.reject(new Error('文档视图未准备好，未插入 AI 结果'));
	}
	if (map?.isEditMode && !map.isEditMode() && map._switchToEditMode) {
		map._switchToEditMode();
	}
	map?.focus?.(false);
	return new Promise((resolve, reject) => {
		let settled = false;
		const cleanup = { timer: undefined as number | undefined };
		let unsubscribe = (): void => undefined;
		const finish = (inserted: boolean, message?: string): void => {
			if (settled) return;
			settled = true;
			if (cleanup.timer !== undefined) window.clearTimeout(cleanup.timer);
			unsubscribe();
			if (message) reject(new Error(message));
			else resolve(inserted);
		};
		unsubscribe = nativeBridge.subscribe((message) => {
			if (message.requestId !== requestId ||
				message.documentSessionId !== documentSessionId) return;
			if (message.type === 'ai.text.insert.done') {
				finish(message.payload?.inserted === true);
			} else if (message.type === 'ai.text.insert.error' || message.type === 'native.error') {
				finish(false, message.payload?.message || 'AI 结果插入失败');
			}
		});
		cleanup.timer = window.setTimeout(() => finish(false, '插入结果确认超时，请检查文档内容后再操作'), 15000);
		const payload: { [key: string]: any } = { html, sourceRequestId, viewId };
		if (resultMode) payload.resultMode = resultMode;
		if (!nativeBridge.postMessage({
			protocolVersion: NativeBridge.PROTOCOL_VERSION,
			channel: 'native', type: 'ai.text.insert', requestId,
			documentSessionId, targetPlatform: 'ios', payload,
		})) finish(false, '文档连接不可用，未插入 AI 结果');
	});
}

class WriterAiController {
	private readonly bridge: WriterAiBridgeLike;
	private readonly documentAdapter: WriterAiDocumentAdapter;
	private readonly listeners: WriterAiStateListener[] = [];
	private readonly unsubscribeBridge: () => void;
	private state: WriterAiControllerState = { state: 'idle', preview: '' };
	private originalSelection = '';
	private resultMode:
		| 'replaceSelection'
		| 'appendAfterSelection'
		| 'insertAtEnd' = 'replaceSelection';
	private lastRequest: {
		taskType: string;
		context: { [key: string]: any };
		selection?: string;
		images?: string[];
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
			warning: this.state.warning,
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

	/**
	 * A newly opened task must not show the previous dialog's preview or loading
	 * state. An unfinished generation belongs to the closed dialog, so cancel it.
	 * A native paste still in progress is left alone; its result is not discardable.
	 */
	resetPreview(): boolean {
		if (this.state.state === 'inserting') {
			return false;
		}
		if (
			this.state.requestId &&
			(this.state.state === 'loading' || this.state.state === 'streaming' || this.state.state === 'ready')
		) {
			this.bridge.cancel(this.state.requestId);
		}
		this.state = { state: 'idle', preview: '' };
		this.lastRequest = null;
		this.originalSelection = '';
		this.notify();
		return true;
	}

	request(
		taskType: string,
		context: { [key: string]: any } = {},
		selectionOverride?: string,
		images?: string[],
	): string | null {
		if (this.state.state === 'inserting') return null;
		if (this.state.state === 'loading' || this.state.state === 'streaming') {
			this.setError('已有 AI 请求正在进行');
			return null;
		}
		if (!this.bridge.isAvailable()) {
			this.setError('NativeBridge 不可用');
			return null;
		}
		const map = typeof window !== 'undefined' ? (window as any).app?.map : null;
		const docType = map && map.getDocType ? map.getDocType() : null;
		if (docType && docType !== 'text' && docType !== 'presentation') {
			this.setError('Writer AI 仅支持 Writer/Impress 文档');
			return null;
		}

		const selection =
			typeof selectionOverride === 'string'
				? selectionOverride
				: this.bridge.getSelectedText();
		const payload: { [key: string]: any } = {
			taskType,
			selection,
			context: context || {},
		};
		if (images && images.length > 0) {
			payload.images = images;
		}
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
			images: images && images.length > 0 ? images : undefined,
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

	/** Read the current selection without exposing bridge details to UI callers. */
	requestWithCurrentSelection(
		taskType: string,
		context: { [key: string]: any } = {},
		images?: string[],
	): string | null | Promise<string | null> {
		if (typeof this.bridge.getSelectedTextAsync !== 'function') {
			return this.request(taskType, context, this.bridge.getSelectedText(), images);
		}
		return this.bridge.getSelectedTextAsync().then((selection) =>
			this.request(taskType, context, selection, images),
		);
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
		if (this.state.state === 'loading' || this.state.state === 'streaming' || this.state.state === 'inserting') {
			return null;
		}
		return this.request(
			this.lastRequest.taskType,
			this.lastRequest.context,
			this.lastRequest.selection,
			this.lastRequest.images,
		);
	}

	async copy(): Promise<boolean> {
		if (!this.state.preview) {
			return false;
		}
		const requestId = this.state.requestId;
		try {
			if (this.documentAdapter.copyText) {
				const copied = await this.documentAdapter.copyText(this.state.preview);
				if (!copied) throw new Error('复制失败，请检查剪贴板权限后重试');
				this.clearCopyError(requestId);
				return true;
			}
			const clipboard = (navigator as any).clipboard;
			if (!clipboard || typeof clipboard.writeText !== 'function') {
				throw new Error('当前环境不支持复制');
			}
			await clipboard.writeText(this.state.preview);
			this.clearCopyError(requestId);
			return true;
		} catch (error) {
			if (this.state.requestId === requestId && this.state.state === 'ready') {
				this.state = {
					...this.state,
					error: error instanceof Error ? error.message : '复制失败，请检查剪贴板权限后重试',
				};
				this.notify();
			}
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
			this.resultMode === 'insertAtEnd' &&
			this.documentAdapter.insertHtmlAtEnd
		) {
			pasted = this.documentAdapter.insertHtmlAtEnd(html, text);
		} else if (
			this.resultMode === 'insertAtEnd' &&
			this.documentAdapter.insertAtEnd
		) {
			pasted = this.documentAdapter.insertAtEnd(text);
		} else if (
			html &&
			this.resultMode === 'replaceSelection' &&
			this.documentAdapter.pasteHtml
		) {
			pasted = this.documentAdapter.pasteHtml(html, text);
		} else if (
			this.resultMode === 'appendAfterSelection' &&
			this.isPresentationDocument() &&
			this.documentAdapter.appendAfterSelection
		) {
			pasted = this.documentAdapter.appendAfterSelection(
				text,
				this.originalSelection,
			);
		} else if (
			this.resultMode === 'replaceSelection' &&
			this.documentAdapter.replaceSelection
		) {
			pasted = this.documentAdapter.replaceSelection(text);
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
		// iOS's native toolbar otherwise waits for a command-state event that can
		// be missed while the async clipboard paste is still being processed.
		// Android receives the same state from its WebView toolbar path, so keep
		// this native-shell nudge iOS-specific.
		this.notifyNativeUndoRecord('ai_insert');
		this.state = {
			...this.state,
			state: 'accepted',
		};
		this.notify();
		return true;
	}

	/** iOS Writer and Impress wait for LOK's native paste acknowledgement. */
	async acceptAsync(html: string): Promise<boolean> {
		const isIOS = typeof window !== 'undefined' && !!(window as any).ThisIsTheiOSApp;
		const isPresentation = this.isPresentationDocument();
		const isWriter = this.isWriterDocument();
		const nativeInsert = isPresentation
			? this.documentAdapter.insertPresentationHtml
			: this.documentAdapter.insertWriterHtml;
		if (!isIOS || (!isPresentation && !isWriter) || !nativeInsert) {
			return this.accept(html);
		}
		if (this.state.state !== 'ready' || !this.state.requestId || !this.state.preview) {
			return false;
		}
		const requestId = this.state.requestId;
		this.state = { ...this.state, state: 'inserting', error: undefined };
		this.notify();
		try {
			const insertionHtml = isPresentation && this.resultMode === 'appendAfterSelection'
				? MobileAiResultRenderer.toHtml(this.originalSelection + this.state.preview)
				: html;
			const inserted = isPresentation
				? await this.documentAdapter.insertPresentationHtml!(insertionHtml, requestId)
				: await this.documentAdapter.insertWriterHtml!(insertionHtml, requestId, this.resultMode);
			if (this.state.requestId !== requestId || this.state.state !== 'inserting') {
				return false;
			}
			if (!inserted) {
				throw new Error('无法将 AI 结果插入文档，请保留文本框选区后重试');
			}
			// Native insertion records its own undo state after LOK confirms paste.
			this.bridge.accept(requestId, this.state.preview);
			this.state = { ...this.state, state: 'accepted' };
			this.notify();
			return true;
		} catch (error) {
			if (this.state.requestId === requestId) {
				// Preserve the generated result and allow retry after an insertion
				// failure; a failed native paste must never dismiss the dialog.
				this.state = {
					...this.state, state: 'ready',
					error: error instanceof Error ? error.message : 'AI 结果插入失败',
				};
				this.notify();
			}
			return false;
		}
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
			this.state.state === 'accepted' ||
			this.state.state === 'cancelled' ||
			this.state.state === 'error' ||
			this.state.state === 'inserting'
		) {
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
					warning: typeof payload.warning === 'string' ? payload.warning : undefined,
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
			empty_document: '文档全文提取失败',
			invalid_typeset_type: '排版类型无效',
		};
		return messages[errorCode] || 'Writer AI 请求参数无效';
	}

	private isPresentationDocument(): boolean {
		const map = typeof window !== 'undefined' ? (window as any).app?.map : null;
		return !!map && typeof map.getDocType === 'function' && map.getDocType() === 'presentation';
	}

	private isWriterDocument(): boolean {
		const map = typeof window !== 'undefined' ? (window as any).app?.map : null;
		return !!map && typeof map.getDocType === 'function' && map.getDocType() === 'text';
	}

	private clearCopyError(requestId?: string): void {
		if (this.state.requestId === requestId && this.state.state === 'ready' && this.state.error) {
			this.state = { ...this.state, error: undefined };
			this.notify();
		}
	}

	private notify(): void {
		const snapshot = this.getState();
		this.listeners.slice().forEach((listener) => listener(snapshot));
	}

	private notifyNativeUndoRecord(reason: string): void {
		if (
			typeof window === 'undefined' ||
			!(window as any).ThisIsTheiOSApp ||
			typeof this.documentAdapter.postMobileMessage !== 'function'
		) {
			return;
		}
		this.documentAdapter.postMobileMessage('NATIVE_UNDO_RECORD reason=' + reason);
	}

	private static defaultDocumentAdapter(): WriterAiDocumentAdapter {
		return {
			postMobileMessage(message: string): void {
				try {
					const poster = (window as any).postMobileMessage;
					if (typeof poster === 'function') {
						poster(message);
					}
				} catch (_error) {
					// Native undo state is best-effort and must not block AI insertion.
				}
			},
			insertPresentationHtml(html: string, sourceRequestId: string): Promise<boolean> {
				return insertIosNativeHtml(html, sourceRequestId);
			},
			insertWriterHtml(
				html: string,
				sourceRequestId: string,
				resultMode: 'replaceSelection' | 'appendAfterSelection' | 'insertAtEnd',
			): Promise<boolean> {
				return insertIosNativeHtml(html, sourceRequestId, resultMode);
			},

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
			insertAtEnd(text: string): boolean {
				try {
					const map = (window as any).app?.map;
					if (
						!map ||
						typeof map.sendUnoCommand !== 'function' ||
						typeof map._clip?.pastePlainText !== 'function'
					) {
						return false;
					}
					// Move the caret to the document end, then paste once the
					// command lands. Mirrors the Android typeset/outline path
					// (.uno:GoToEndOfDoc + delayed paste).
					map.sendUnoCommand('.uno:GoToEndOfDoc');
					window.setTimeout(
						() => {
							try {
								map._clip.pastePlainText(text);
							} catch (_error) {
								// Best-effort insertion; caller already accepted.
							}
						},
						300,
					);
					return true;
				} catch (_error) {
					return false;
				}
			},
			insertHtmlAtEnd(html: string, plainText: string): boolean {
				try {
					const map = (window as any).app?.map;
					const clip = map?._clip;
					if (
						!map ||
						typeof map.sendUnoCommand !== 'function' ||
						!clip ||
						typeof clip.pasteAiTextAsHtml !== 'function'
					) {
						return false;
					}
					map.sendUnoCommand('.uno:GoToEndOfDoc');
					window.setTimeout(
						() => {
							try {
								clip.pasteAiTextAsHtml(html, plainText);
							} catch (_error) {
								// Best-effort insertion; caller already accepted.
							}
						},
						300,
					);
					return true;
				} catch (_error) {
					return false;
				}
			},
			async copyText(text: string): Promise<boolean> {
				try {
					const clipboard = (navigator as any).clipboard;
					if (!clipboard || typeof clipboard.writeText !== 'function') {
						return false;
					}
				await clipboard.writeText(text);
					return true;
				} catch (_error) {
					return false;
				}
			},
			appendAfterSelection(text: string, originalSelection = ''): boolean {
				try {
					const clip = (window as any).app?.map?._clip;
					if (!clip || typeof clip.pastePlainText !== 'function') {
						return false;
					}
					// LOK pastes over the active range.  Replacing that range with the
					// original text followed by the continuation preserves the selected
					// text and produces the required append-at-selection-end result in
					// Impress without changing Writer's existing fallback behavior.
					return clip.pastePlainText(originalSelection + text);
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
