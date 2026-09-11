/*
 * Best-effort full-document text extraction for document-level AI tasks.
 *
 * Mirrors the Android typeset/outline path: Save → SelectAll → read selection
 * → Deselect.
 */

class MobileAiDocumentExtractor {
	static extractFullText(): Promise<string> {
		return new Promise((resolve, reject) => {
			try {
				const map = (window as any).app?.map;
				if (!map || typeof map.sendUnoCommand !== 'function') {
					reject(new Error('文档不可用'));
					return;
				}
				map.sendUnoCommand('.uno:Save');
				window.setTimeout(() => {
					try {
						map.sendUnoCommand('.uno:SelectAll');
						window.setTimeout(() => {
							try {
								const text =
									MobileAiBridge.getInstance().getSelectedText().trim();
								map.sendUnoCommand('.uno:Deselect');
								if (!text) {
									reject(new Error('文档全文提取失败'));
									return;
								}
								resolve(text);
							} catch (_error) {
								reject(new Error('文档全文提取失败'));
							}
						}, 400);
					} catch (_error) {
						reject(new Error('文档全文提取失败'));
					}
				}, 500);
			} catch (_error) {
				reject(new Error('文档全文提取失败'));
			}
		});
	}
}
