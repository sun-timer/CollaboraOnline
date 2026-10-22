/*
 * Full-document text extraction for mobile AI document Q&A (first turn).
 *
 * Android 全文问答在原生面板里提取，不走这里。演示文稿在
 * LOActivity.extractDocumentTextForDocQaFirstTurn 导出后读取，不要改回全选。
 * iOS：表格跳过压缩包，用全选；演示文稿先导出再读文本框；文字文档先读文件再全选。
 */

class MobileAiDocumentExtractor {
	private static readonly EMPTY_EXTRACT_RESULT = { success: false, text: '' };

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

	static extractFullText(options: { allowEmpty?: boolean } = {}): Promise<string> {
		MobileAiDocumentExtractor.debug('extractFullText start');
		return MobileAiDocumentExtractor.extractViaNative(options.allowEmpty === true)
			.then((nativeResult) => {
				MobileAiDocumentExtractor.debug(
					'native chars=' + nativeResult.text.length +
					' success=' + nativeResult.success,
				);
				if (nativeResult.success && nativeResult.text.length > 0) {
					return { text: nativeResult.text, emptyDocument: false };
				}
				if (nativeResult.success && options.allowEmpty === true) {
					return { text: '', emptyDocument: true };
				}
				if (options.allowEmpty === true) {
					throw new Error('文档全文提取失败');
				}
				return MobileAiDocumentExtractor.extractViaSelection().then((text) => ({
					text,
					emptyDocument: false,
				}));
			})
			.then((result) => {
				if (result.text) {
					MobileAiDocumentExtractor.debug('extract ok chars=' + result.text.length);
					return result.text;
				}
				if (result.emptyDocument) {
					MobileAiDocumentExtractor.debug('extract empty document');
					return '';
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

	private static extractViaNative(
		allowEmpty: boolean,
	): Promise<{ success: boolean; text: string }> {
		return new Promise((resolve) => {
			let releaseSelectionMenuSuppression = (): void => undefined;
			try {
				if (typeof window === 'undefined' || !(window as any).ThisIsTheiOSApp) {
					resolve(MobileAiDocumentExtractor.EMPTY_EXTRACT_RESULT);
					return;
				}
				const nativeBridge = NativeBridge.getInstance();
				if (!nativeBridge.isAvailable()) {
					resolve(MobileAiDocumentExtractor.EMPTY_EXTRACT_RESULT);
					return;
				}
				const requestId = NativeBridge.createId('doc-extract');
				const documentSessionId = nativeBridge.getDocumentSessionId();
				releaseSelectionMenuSuppression =
					MobileAiDocumentExtractor.suppressProgrammaticSelectionMenu();
				let settled = false;
				const finish = (success: boolean, text: string): void => {
					if (settled) {
						return;
					}
					settled = true;
					releaseSelectionMenuSuppression();
					window.clearTimeout(timer);
					unsubscribe();
					resolve({
						success,
						text: typeof text === 'string' ? text.trim() : '',
					});
				};
				const unsubscribe = nativeBridge.subscribe((message) => {
					if (message.requestId !== requestId) {
						return;
					}
					if (message.type === 'ai.doc_extract.done') {
						const payload = message.payload || {};
						finish(true, typeof payload.text === 'string' ? payload.text : '');
						return;
					}
					if (
						message.type === 'ai.doc_extract.error' ||
						message.type === 'native.error' ||
						message.type === 'ai.error'
					) {
						finish(false, '');
					}
				});
				const timer = window.setTimeout(() => finish(false, ''), 8000);
				const posted = nativeBridge.postMessage({
					protocolVersion: NativeBridge.PROTOCOL_VERSION,
					channel: 'native',
					type: 'ai.doc_extract',
					requestId,
					documentSessionId,
					targetPlatform: 'ios',
					payload: allowEmpty ? { allowEmpty: true } : {},
				});
				if (!posted) {
					finish(false, '');
				}
			} catch (_error) {
				releaseSelectionMenuSuppression();
				resolve(MobileAiDocumentExtractor.EMPTY_EXTRACT_RESULT);
			}
		});
	}

	private static extractViaSelection(): Promise<string> {
		return new Promise((resolve) => {
			let releaseSelectionMenuSuppression = (): void => undefined;
			let cleanedUp = false;
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
				const cleanup = (): void => {
					if (cleanedUp) {
						return;
					}
					cleanedUp = true;
					deselect();
					releaseSelectionMenuSuppression();
				};

				const requestSelectionContent = (): void => {
					if (socket && typeof socket.sendMessage === 'function') {
						// Raw Copy matches Android copyViaWebsocketFallback.
						socket.sendMessage('uno .uno:Copy');
						socket.sendMessage(gettextMime);
					}
				};

				releaseSelectionMenuSuppression =
					MobileAiDocumentExtractor.suppressProgrammaticSelectionMenu();
				map.sendUnoCommand('.uno:SelectAll');

				let attempt = 0;
				const poll = (): void => {
					attempt += 1;
					const text = MobileAiDocumentExtractor.currentSelectionText();
					if (text) {
						cleanup();
						resolve(text);
						return;
					}
					if (attempt === 1 || attempt === 4) {
						requestSelectionContent();
					}
					if (attempt >= 24) {
						cleanup();
						resolve('');
						return;
					}
					window.setTimeout(poll, attempt < 6 ? 120 : 220);
				};
				window.setTimeout(poll, 120);
			} catch (_error) {
				releaseSelectionMenuSuppression();
				resolve('');
			}
		});
	}

	private static suppressProgrammaticSelectionMenu(): () => void {
		try {
			const selectionMenu = (window as any).AndroidSelectionMenu;
			if (
				selectionMenu &&
				typeof selectionMenu.beginProgrammaticSelection === 'function' &&
				typeof selectionMenu.endProgrammaticSelection === 'function'
			) {
				selectionMenu.beginProgrammaticSelection();
				let released = false;
				return () => {
					if (released) {
						return;
					}
					released = true;
					selectionMenu.endProgrammaticSelection();
				};
			}
		} catch (_error) {
			// Menu suppression is best effort and must not block extraction.
		}
		return () => undefined;
	}
}
