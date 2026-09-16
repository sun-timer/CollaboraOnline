/*
 * Full-document text extraction for mobile AI document Q&A (first turn).
 *
 * SelectAll + gettextselection + poll selection buffers; no Save (matches Android).
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
		return new Promise((resolve, reject) => {
			try {
				const appRef = (window as any).app;
				const map = appRef?.map;
				if (!map || typeof map.sendUnoCommand !== 'function') {
					reject(new Error('文档全文提取失败'));
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
						socket.sendMessage(gettextMime);
					}
				};

				map.sendUnoCommand('.uno:SelectAll');
				requestSelectionContent();

				let attempt = 0;
				const poll = (): void => {
					attempt += 1;
					const text = MobileAiDocumentExtractor.currentSelectionText();
					if (text) {
						deselect();
						resolve(text);
						return;
					}
					if (attempt === 4) {
						requestSelectionContent();
					}
					if (attempt >= 16) {
						deselect();
						reject(new Error('文档全文提取失败'));
						return;
					}
					window.setTimeout(poll, attempt === 1 ? 120 : 200);
				};
				window.setTimeout(poll, 120);
			} catch (_error) {
				reject(new Error('文档全文提取失败'));
			}
		});
	}
}
