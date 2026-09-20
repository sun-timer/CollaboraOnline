/*
 * Small, dependency-free Markdown subset for AI previews and document paste.
 *
 * It escapes the model output first and only adds a fixed set of tags.
 */

class MobileAiResultRenderer {
	static toHtml(markdown: string): string {
		const source = typeof markdown === 'string' ? markdown : '';
		const escaped = MobileAiResultRenderer.escapeHtml(source.trim());
		const lines = escaped.split(/\r?\n/);
		const output: string[] = [];
		let paragraphLines: string[] = [];
		let listTag: 'ul' | 'ol' | null = null;
		const flushParagraph = (): void => {
			if (paragraphLines.length === 0) {
				return;
			}
			output.push(
				`<p style="margin:0;">${MobileAiResultRenderer.inline(
					paragraphLines.join('<br>'),
				)}</p>`,
			);
			paragraphLines = [];
		};
		const closeList = (): void => {
			if (listTag) {
				output.push(`</${listTag}>`);
				listTag = null;
			}
		};

		lines.forEach((line) => {
			if (line.trim().length === 0) {
				flushParagraph();
				closeList();
				if (output.length > 0 && output[output.length - 1] !== '<br>') {
					output.push('<br>');
				}
				return;
			}

			const unorderedItem = line.match(/^\s*[-*+]\s+(.*)$/);
			const orderedItem = line.match(/^\s*\d+[.)]\s+(.*)$/);
			const item = unorderedItem || orderedItem;
			if (item) {
				flushParagraph();
				const nextListTag = unorderedItem ? 'ul' : 'ol';
				if (listTag !== nextListTag) {
					closeList();
					listTag = nextListTag;
					output.push(`<${listTag}>`);
				}
				output.push(`<li>${MobileAiResultRenderer.inline(item[1])}</li>`);
				return;
			}

			closeList();
			paragraphLines.push(line);
		});
		flushParagraph();
		closeList();
		while (output[output.length - 1] === '<br>') {
			output.pop();
		}
		return output.join('');
	}

	static renderInto(target: HTMLElement, markdown: string): void {
		target.innerHTML = MobileAiResultRenderer.toHtml(markdown);
	}

	static sanitizeTypesetHtml(raw: string): string {
		let html = typeof raw === 'string' ? raw.trim() : '';
		html = html.replace(/^```(?:html)?\s*\n?/i, '');
		html = html.replace(/\n?```\s*$/, '');
		return html.trim();
	}

	static isLikelyHtml(value: string): boolean {
		const trimmed = MobileAiResultRenderer.sanitizeTypesetHtml(value);
		return /^<[a-z][\s\S]*>/i.test(trimmed);
	}

	static renderTypesetInto(target: HTMLElement, raw: string): void {
		const html = MobileAiResultRenderer.sanitizeTypesetHtml(raw);
		if (MobileAiResultRenderer.isLikelyHtml(html)) {
			target.innerHTML = html;
			return;
		}
		MobileAiResultRenderer.renderInto(target, raw);
	}

	private static inline(value: string): string {
		return value
			.replace(/`([^`]+)`/g, '<code>$1</code>')
			.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
			.replace(/\*([^*]+)\*/g, '<em>$1</em>');
	}

	private static escapeHtml(value: string): string {
		return value
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#39;');
	}
}
