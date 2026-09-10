/*
 * Browser facade for native typeset V2 operations (extract / fill / insert).
 */

interface TypesetImagePayload {
	base64: string;
	mimeType: string;
	extension: string;
	cx?: number;
	cy?: number;
}

interface TypesetStructuredExtract {
	fullText: string;
	paragraphs: string[];
	images?: { [marker: string]: TypesetImagePayload };
}

interface TypesetFillResult {
	docxPath: string;
	previewHtml?: string;
}

class TypesetBridge {
	private static instance: TypesetBridge | null = null;
	private readonly nativeBridge: NativeBridge;
	private readonly listeners: Array<(message: NativeBridgeEnvelope) => void> = [];
	private readonly pending: {
		[requestId: string]: {
			resolve: (payload: any) => void;
			reject: (error: Error) => void;
		};
	} = {};
	private readonly unsubscribe: () => void;

	private constructor(nativeBridge: NativeBridge) {
		this.nativeBridge = nativeBridge;
		this.unsubscribe = nativeBridge.subscribe((message) => {
			if (!message.type || message.type.indexOf('typeset.') !== 0) {
				return;
			}
			if (!message.requestId) {
				return;
			}
			const pending = this.pending[message.requestId];
			if (!pending) {
				return;
			}
			if (message.type.endsWith('.error')) {
				const payload = message.payload || {};
				delete this.pending[message.requestId];
				pending.reject(
					new Error(
						typeof payload.message === 'string'
							? payload.message
							: 'Typeset 操作失败',
					),
				);
				return;
			}
			if (message.type.endsWith('.done')) {
				delete this.pending[message.requestId];
				pending.resolve(message.payload || {});
			}
		});
	}

	static getInstance(): TypesetBridge {
		if (!TypesetBridge.instance) {
			TypesetBridge.instance = new TypesetBridge(NativeBridge.getInstance());
			if (typeof window !== 'undefined') {
				(window as any).TypesetBridge = TypesetBridge.instance;
			}
		}
		return TypesetBridge.instance;
	}

	isAvailable(): boolean {
		return this.nativeBridge.isAvailable();
	}

	extractStructured(): Promise<TypesetStructuredExtract> {
		return this.request('typeset.extract', {});
	}

	fillTemplate(
		typesetType: string,
		sections: { [key: string]: string },
		sourceName?: string,
		images?: { [marker: string]: TypesetImagePayload },
	): Promise<TypesetFillResult> {
		const payload: { [key: string]: any } = {
			typesetType,
			sections,
			sourceName: sourceName || '',
		};
		if (images && Object.keys(images).length > 0) {
			payload.images = images;
		}
		return this.request('typeset.fill', payload);
	}

	insertDocument(docxPath: string): Promise<boolean> {
		return this.request('typeset.insert', { docxPath }).then(
			() => true,
			() => false,
		);
	}

	private request(type: string, payload: { [key: string]: any }): Promise<any> {
		return new Promise((resolve, reject) => {
			if (!this.nativeBridge.isAvailable()) {
				reject(new Error('NativeBridge 不可用'));
				return;
			}
			const requestId = NativeBridge.createId('typeset');
			this.pending[requestId] = { resolve, reject };
			const posted = this.nativeBridge.postMessage({
				protocolVersion: NativeBridge.PROTOCOL_VERSION,
				channel: 'native',
				type,
				requestId,
				documentSessionId: this.nativeBridge.getDocumentSessionId(),
				targetPlatform: 'ios',
				payload,
			});
			if (!posted) {
				delete this.pending[requestId];
				reject(new Error('无法发送 typeset 请求'));
			}
		});
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	TypesetBridge.getInstance();
}
