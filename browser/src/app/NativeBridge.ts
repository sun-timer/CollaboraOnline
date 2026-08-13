/*
 * Versioned Browser-to-native transport.
 *
 * CoreBridge continues to use postMobileMessage()/lok. NativeBridge is
 * intentionally independent so unknown native messages cannot fall through
 * to the Core protocol.
 */

type NativeBridgeTargetPlatform = 'any' | 'android' | 'ios';

interface NativeBridgeEnvelope {
	protocolVersion: number;
	channel: 'native';
	type: string;
	requestId?: string;
	documentSessionId?: string;
	targetPlatform?: NativeBridgeTargetPlatform;
	payload?: { [key: string]: any };
}

interface NativeBridgeValidationResult {
	valid: boolean;
	errorCode?: string;
}

type NativeBridgeListener = (message: NativeBridgeEnvelope) => void;

interface NativeBridgeTransport {
	postMessage(message: string): void;
}

class NativeBridge {
	static readonly PROTOCOL_VERSION = 1;
	private static readonly SUPPORTED_TYPES: { [type: string]: boolean } = {
		'native.ready': true,
		'native.error': true,
		'ai.request': true,
		'ai.cancel': true,
		'ai.accept': true,
		'ai.state': true,
		'ai.stream': true,
		'ai.done': true,
		'ai.error': true,
	};

	private static instance: NativeBridge | null = null;
	private readonly listeners: NativeBridgeListener[] = [];
	private transport: NativeBridgeTransport | null;
	private readonly documentSessionId: string;

	private constructor(transport: NativeBridgeTransport | null) {
		this.transport = transport;
		this.documentSessionId = NativeBridge.createId('document');
	}

	static getInstance(): NativeBridge {
		if (!NativeBridge.instance) {
			const existing =
				typeof window !== 'undefined'
					? (window as any).__coolNativeBridge
					: null;
			NativeBridge.instance =
				existing instanceof NativeBridge
					? existing
					: new NativeBridge(NativeBridge.resolveTransport());
			if (typeof window !== 'undefined') {
				(window as any).__coolNativeBridge = NativeBridge.instance;
				(window as any).NativeBridge = NativeBridge.instance;
			}
		}
		return NativeBridge.instance;
	}

	static validateEnvelope(value: any): NativeBridgeValidationResult {
		if (!value || typeof value !== 'object') {
			return { valid: false, errorCode: 'invalid_payload' };
		}
		if (value.protocolVersion !== NativeBridge.PROTOCOL_VERSION) {
			return { valid: false, errorCode: 'unsupported_version' };
		}
		if (value.channel !== 'native') {
			return { valid: false, errorCode: 'invalid_channel' };
		}
		if (
			typeof value.type !== 'string' ||
			!NativeBridge.SUPPORTED_TYPES[value.type]
		) {
			return { valid: false, errorCode: 'unsupported_type' };
		}
		if (
			value.targetPlatform !== undefined &&
			value.targetPlatform !== 'any' &&
			value.targetPlatform !== 'android' &&
			value.targetPlatform !== 'ios'
		) {
			return { valid: false, errorCode: 'invalid_target_platform' };
		}
		if (
			(value.type === 'ai.request' ||
				value.type === 'ai.cancel' ||
				value.type === 'ai.accept' ||
				value.type === 'ai.state' ||
				value.type === 'ai.stream' ||
				value.type === 'ai.done' ||
				value.type === 'ai.error') &&
			(typeof value.requestId !== 'string' || value.requestId.trim() === '')
		) {
			return { valid: false, errorCode: 'missing_request_id' };
		}
		if (
			value.documentSessionId !== undefined &&
			(typeof value.documentSessionId !== 'string' ||
				value.documentSessionId.trim() === '')
		) {
			return { valid: false, errorCode: 'invalid_document_session' };
		}
		return { valid: true };
	}

	static createId(prefix: string): string {
		return (
			prefix +
			'-' +
			Date.now().toString(36) +
			'-' +
			Math.random().toString(16).slice(2)
		);
	}

	isAvailable(): boolean {
		return this.getTransport() !== null;
	}

	getDocumentSessionId(): string {
		return this.documentSessionId;
	}

	postMessage(message: NativeBridgeEnvelope): boolean {
		const validation = NativeBridge.validateEnvelope(message);
		const transport = this.getTransport();
		if (!validation.valid || !transport) {
			return false;
		}
		const target = message.targetPlatform || 'any';
		if (
			(target === 'ios' && !NativeBridge.isIOS()) ||
			(target === 'android' && !NativeBridge.isAndroid())
		) {
			return false;
		}
		try {
			transport.postMessage(JSON.stringify(message));
			return true;
		} catch (_error) {
			return false;
		}
	}

	subscribe(listener: NativeBridgeListener): () => void {
		this.listeners.push(listener);
		return () => {
			const index = this.listeners.indexOf(listener);
			if (index >= 0) {
				this.listeners.splice(index, 1);
			}
		};
	}

	onMessage(message: NativeBridgeEnvelope | string): void {
		let envelope: NativeBridgeEnvelope;
		try {
			envelope = typeof message === 'string' ? JSON.parse(message) : message;
		} catch (_error) {
			return;
		}
		if (!NativeBridge.validateEnvelope(envelope).valid) {
			return;
		}
		this.listeners.slice().forEach((listener) => listener(envelope));
	}

	private getTransport(): NativeBridgeTransport | null {
		if (!this.transport) {
			this.transport = NativeBridge.resolveTransport();
		}
		return this.transport;
	}

	private static resolveTransport(): NativeBridgeTransport | null {
		if (typeof window === 'undefined') {
			return null;
		}
		const transport = (window as any).NativeBridgeTransport;
		if (!transport || typeof transport.postMessage !== 'function') {
			return null;
		}
		return transport as NativeBridgeTransport;
	}

	private static isIOS(): boolean {
		return typeof window !== 'undefined' && !!(window as any).ThisIsTheiOSApp;
	}

	private static isAndroid(): boolean {
		return (
			typeof window !== 'undefined' && !!(window as any).ThisIsTheAndroidApp
		);
	}
}

if (typeof window !== 'undefined') {
	NativeBridge.getInstance();
}
