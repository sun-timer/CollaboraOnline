/*
 * Full-document text extraction for mobile AI document Q&A (first turn).
 *
 * Android Doc QA uses LOK SelectAll → Copy → clipboard poll. iOS native
 * extract follows that path; JS SelectAll + Copy + gettextselection remains
 * the fallback when NativeBridge is unavailable.
 */

class MobileAiDocumentExtractor {
	static toPlainText(htmlOrText: string): string {
		const raw = typeof htmlOrText === 'string' ? htmlOrText : '';
		if (!raw) {
			return '';
		}
		if (raw.indexOf('<') < 0) {
			return raw.trim();
		}
		try {
			if (typeof document !== 'undefined') {
				const div = document.createElement('div');
				div.innerHTML = raw;
				return (div.textContent || div.innerText || '').trim();
			}
		} catch (_error) {
			// Fall through to regex stripping.
		}
		return raw.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
	}

	static currentSelectionText(): string {
		return MobileAiDocumentExtractor.toPlainText(
			MobileAiBridge.getInstance().getSelectedText(),
		);
	}

	static extractFullText(): Promise<string> {
		MobileAiDocumentExtractor.debug('extractFullText start');
		return MobileAiDocumentExtractor.extractViaNative()
			.then((nativeText) => {
				MobileAiDocumentExtractor.debug(
					'native chars=' + (nativeText ? nativeText.length : 0),
				);
				if (nativeText) {
					return nativeText;
				}
				return MobileAiDocumentExtractor.extractViaSelection();
			})
			.then((text) => {
				if (text) {
					MobileAiDocumentExtractor.debug('extract ok chars=' + text.length);
					return text;
				}
				MobileAiDocumentExtractor.debug('extract empty');
				throw new Error('文档全文提取失败');
			});
	}

	private static debug(message: string): void {
		try {
			const post = (window as any).postMobileDebug;
			if (typeof post === 'function') {
				post('[AIExtract] ' + message);
			}
		} catch (_error) {
			// Logging must not break extraction.
		}
	}

	private static extractViaNative(): Promise<string> {
		return new Promise((resolve) => {
			try {
				if (typeof window === 'undefined' || !(window as any).ThisIsTheiOSApp) {
					resolve('');
					return;
				}
				const nativeBridge = NativeBridge.getInstance();
				if (!nativeBridge.isAvailable()) {
					resolve('');
					return;
				}
				const requestId = NativeBridge.createId('doc-extract');
				const documentSessionId = nativeBridge.getDocumentSessionId();
				let settled = false;
				const finish = (text: string): void => {
					if (settled) {
						return;
					}
					settled = true;
					window.clearTimeout(timer);
					unsubscribe();
					resolve(typeof text === 'string' ? text.trim() : '');
				};
				const unsubscribe = nativeBridge.subscribe((message) => {
					if (message.requestId !== requestId) {
						return;
					}
					if (message.type === 'ai.doc_extract.done') {
						const payload = message.payload || {};
						finish(
							typeof payload.text === 'string' ? payload.text : '',
						);
						return;
					}
					if (
						message.type === 'ai.doc_extract.error' ||
						message.type === 'native.error' ||
						message.type === 'ai.error'
					) {
						finish('');
					}
				});
				const timer = window.setTimeout(() => finish(''), 4000);
				const posted = nativeBridge.postMessage({
					protocolVersion: NativeBridge.PROTOCOL_VERSION,
					channel: 'native',
					type: 'ai.doc_extract',
					requestId,
					documentSessionId,
					targetPlatform: 'ios',
					payload: {},
				});
				if (!posted) {
					finish('');
				}
			} catch (_error) {
				resolve('');
			}
		});
	}

	private static extractViaSelection(): Promise<string> {
		return new Promise((resolve) => {
			try {
				const appRef = (window as any).app;
				const map = appRef?.map;
				if (!map || typeof map.sendUnoCommand !== 'function') {
					resolve('');
					return;
				}
				const socket = appRef?.socket;
				const gettextMime =
					'gettextselection mimetype=text/html,text/plain;charset=utf-8';

				const deselect = (): void => {
					try {
						map.sendUnoCommand('.uno:Deselect');
					} catch (_error) {
						// Best effort.
					}
				};

				const requestSelectionContent = (): void => {
					if (socket && typeof socket.sendMessage === 'function') {
						// Raw Copy matches Android copyViaWebsocketFallback.
						socket.sendMessage('uno .uno:Copy');
						socket.sendMessage(gettextMime);
					}
				};

				map.sendUnoCommand('.uno:SelectAll');

				let attempt = 0;
				const poll = (): void => {
					attempt += 1;
					const text = MobileAiDocumentExtractor.currentSelectionText();
					if (text) {
						deselect();
						resolve(text);
						return;
					}
					if (attempt === 1 || attempt === 4) {
						requestSelectionContent();
					}
					if (attempt >= 24) {
						deselect();
						resolve('');
						return;
					}
					window.setTimeout(poll, attempt < 6 ? 120 : 220);
				};
				window.setTimeout(poll, 120);
			} catch (_error) {
				resolve('');
			}
		});
	}
}
