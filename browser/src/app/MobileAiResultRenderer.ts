/*
 * Small, dependency-free Markdown subset for AI previews and document paste.
 *
 * It escapes the model output first and only adds a fixed set of tags.
 */

class MobileAiResultRenderer {
	static toHtml(markdown: string): string {
		const source = typeof markdown === 'string' ? markdown : '';
		const escaped = MobileAiResultRenderer.escapeHtml(source);
		const lines = escaped.split(/\r?\n/);
		const output: string[] = [];
		let inList = false;
		lines.forEach((line) => {
			const listItem = line.match(/^\s*-\s+(.*)$/);
			if (listItem) {
				if (!inList) {
					output.push('<ul>');
					inList = true;
				}
				output.push(`<li>${MobileAiResultRenderer.inline(listItem[1])}</li>`);
				return;
			}
			if (inList) {
				output.push('</ul>');
				inList = false;
			}
			output.push(
				line.length > 0
					? `<p>${MobileAiResultRenderer.inline(line)}</p>`
					: '<br>',
			);
		});
		if (inList) {
			output.push('</ul>');
		}
		return output.join('');
	}

	static renderInto(target: HTMLElement, markdown: string): void {
		target.innerHTML = MobileAiResultRenderer.toHtml(markdown);
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
