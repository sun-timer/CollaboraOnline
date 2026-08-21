/*
 * Shared AI facade for mobile Browser code.
 *
 * The facade owns the v1 envelope and request/session filtering. Native
 * implementations own networking, storage and UI; this file must never carry
 * an API key or an Authorization header.
 */

interface MobileAiRequestPayload {
	requestId?: string;
	documentSessionId?: string;
	taskType?: string;
	modelMode?: string;
	selection?: string;
	history?: any[];
	context?: { [key: string]: any };
	docQaFirstTurn?: boolean;
	[key: string]: any;
}

interface MobileAiEventListener {
	(message: NativeBridgeEnvelope): void;
}

class MobileAiBridge {
	static readonly WRITER_P0_TASK_TYPES = WriterAiCatalog.P0_TASK_TYPES;

	private static instance: MobileAiBridge | null = null;
	private readonly nativeBridge: NativeBridge;
	private readonly listeners: MobileAiEventListener[] = [];
	private readonly activeRequests: { [requestId: string]: string } = {};
	private readonly unsubscribeNative: () => void;

	private constructor(nativeBridge: NativeBridge) {
		this.nativeBridge = nativeBridge;
		this.unsubscribeNative = nativeBridge.subscribe((message) => {
			this.handleNativeMessage(message);
		});
	}

	static getInstance(): MobileAiBridge {
		if (!MobileAiBridge.instance) {
			MobileAiBridge.instance = new MobileAiBridge(NativeBridge.getInstance());
			if (typeof window !== 'undefined') {
				(window as any).MobileAiBridge = MobileAiBridge.instance;
				// Keep the Java callback name stable while Android migrates.
				if (!(window as any).__coolAiBridge) {
					(window as any).__coolAiBridge = MobileAiBridge.instance;
				}
			}
		}
		return MobileAiBridge.instance;
	}

	request(payload: MobileAiRequestPayload = {}): string {
		const requestId = payload.requestId || NativeBridge.createId('request');
		const documentSessionId =
			payload.documentSessionId || this.nativeBridge.getDocumentSessionId();
		const requestPayload: MobileAiRequestPayload = {
			...payload,
			requestId,
			documentSessionId,
		};
		this.activeRequests[requestId] = documentSessionId;

		const envelope: NativeBridgeEnvelope = {
			protocolVersion: NativeBridge.PROTOCOL_VERSION,
			channel: 'native',
			type: 'ai.request',
			requestId,
			documentSessionId,
			targetPlatform: 'any',
			payload: requestPayload as { [key: string]: any },
		};
		const legacyRequestPayload = this.legacyRequestPayload(requestPayload);
		const legacyPosted = this.isAndroid()
			? this.postLegacy('ai.request', legacyRequestPayload, requestId)
			: false;
		const nativePosted = this.nativeBridge.postMessage(envelope);
		if (!legacyPosted && !nativePosted) {
			delete this.activeRequests[requestId];
		}
		return requestId;
	}

	cancel(requestId: string): boolean {
		if (!requestId) {
			return false;
		}
		const documentSessionId =
			this.activeRequests[requestId] ||
			this.nativeBridge.getDocumentSessionId();
		const envelope: NativeBridgeEnvelope = {
			protocolVersion: NativeBridge.PROTOCOL_VERSION,
			channel: 'native',
			type: 'ai.cancel',
			requestId,
			documentSessionId,
			targetPlatform: 'any',
			payload: {},
		};
		const legacyPosted = this.isAndroid()
			? this.postLegacy('ai.cancel', { requestId }, requestId)
			: false;
		if (this.nativeBridge.postMessage(envelope)) {
			return true;
		}
		return legacyPosted;
	}

	accept(requestId: string, text: string): boolean {
		if (!requestId || !text) {
			return false;
		}
		const documentSessionId =
			this.activeRequests[requestId] ||
			this.nativeBridge.getDocumentSessionId();
		const payload = { requestId, text };
		const envelope: NativeBridgeEnvelope = {
			protocolVersion: NativeBridge.PROTOCOL_VERSION,
			channel: 'native',
			type: 'ai.accept',
			requestId,
			documentSessionId,
			targetPlatform: 'any',
			payload,
		};
		const legacyPosted = this.isAndroid()
			? this.postLegacy('ai.accept', payload, requestId)
			: false;
		if (this.nativeBridge.postMessage(envelope)) {
			return true;
		}
		return legacyPosted;
	}

	isAvailable(): boolean {
		return this.nativeBridge.isAvailable() || this.isAndroid();
	}

	subscribe(listener: MobileAiEventListener): () => void {
		this.listeners.push(listener);
		return () => {
			const index = this.listeners.indexOf(listener);
			if (index >= 0) {
				this.listeners.splice(index, 1);
			}
		};
	}

	onNativeEvent(event: NativeBridgeEnvelope | { [key: string]: any }): void {
		if (!event) {
			return;
		}
		if (
			(event as NativeBridgeEnvelope).protocolVersion ===
			NativeBridge.PROTOCOL_VERSION
		) {
			this.nativeBridge.onMessage(event as NativeBridgeEnvelope);
			return;
		}
		const legacyEvent = event as { [key: string]: any };
		if (typeof legacyEvent.type !== 'string') {
			return;
		}
		this.nativeBridge.onMessage({
			protocolVersion: NativeBridge.PROTOCOL_VERSION,
			channel: 'native',
			type: legacyEvent.type,
			requestId: legacyEvent.requestId,
			documentSessionId:
				legacyEvent.documentSessionId ||
				this.activeRequests[legacyEvent.requestId] ||
				undefined,
			payload: legacyEvent,
		});
	}

	getSelectedText(): string {
		try {
			const docLayer = (window as any).app?.map?._docLayer;
			if (
				docLayer &&
				typeof docLayer._selectedTextContent === 'string' &&
				docLayer._selectedTextContent
			) {
				return docLayer._selectedTextContent;
			}
			const clip = (window as any).app?.map?._clip;
			if (
				clip &&
				typeof clip._selectionPlainTextContent === 'string' &&
				clip._selectionPlainTextContent
			) {
				return clip._selectionPlainTextContent;
			}
			const selection = window.getSelection();
			if (selection && selection.toString && selection.toString().trim()) {
				return selection.toString().trim();
			}
		} catch (_error) {
			// Selection is best effort and must not break document editing.
		}
		return '';
	}

	private handleNativeMessage(message: NativeBridgeEnvelope): void {
		if (
			message.targetPlatform &&
			message.targetPlatform !== 'any' &&
			((message.targetPlatform === 'ios' && !(window as any).ThisIsTheiOSApp) ||
				(message.targetPlatform === 'android' &&
					!(window as any).ThisIsTheAndroidApp))
		) {
			return;
		}
		if (message.requestId && message.type.indexOf('ai.') === 0) {
			const expectedSession = this.activeRequests[message.requestId];
			if (
				expectedSession &&
				message.documentSessionId &&
				expectedSession !== message.documentSessionId
			) {
				return;
			}
		}
		this.listeners.slice().forEach((listener) => listener(message));
		if (
			message.requestId &&
			(message.type === 'ai.done' ||
				message.type === 'ai.error' ||
				(message.type === 'ai.state' && message.payload?.state === 'cancelled'))
		) {
			delete this.activeRequests[message.requestId];
		}
	}

	private postLegacy(
		type: string,
		payload: { [key: string]: any },
		requestId: string,
	): boolean {
		if (
			typeof window === 'undefined' ||
			!(window as any).ThisIsTheAndroidApp ||
			typeof (window as any).postMobileMessage !== 'function'
		) {
			delete this.activeRequests[requestId];
			return false;
		}
		try {
			(window as any).postMobileMessage(type + ' ' + JSON.stringify(payload));
			return true;
		} catch (_error) {
			delete this.activeRequests[requestId];
			return false;
		}
	}

	private isAndroid(): boolean {
		return (
			typeof window !== 'undefined' && !!(window as any).ThisIsTheAndroidApp
		);
	}

	private legacyRequestPayload(
		payload: MobileAiRequestPayload,
	): MobileAiRequestPayload {
		if (!this.isAndroid() || typeof payload.taskType !== 'string') {
			return payload;
		}
		const task = WriterAiCatalog.getTask(payload.taskType);
		if (!task || task.androidTaskType === task.taskType) {
			return payload;
		}
		return {
			...payload,
			taskType: task.androidTaskType,
		};
	}
}

if (typeof window !== 'undefined') {
	MobileAiBridge.getInstance();
}
