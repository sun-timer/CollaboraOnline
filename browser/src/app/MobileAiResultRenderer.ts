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

		let index = 0;
		while (index < lines.length) {
			const line = lines[index];
			if (line.trim().length === 0) {
				flushParagraph();
				closeList();
				if (output.length > 0 && output[output.length - 1] !== '<br>') {
					output.push('<br>');
				}
				index++;
				continue;
			}

			if (MobileAiResultRenderer.isTableLine(line)) {
				const block: string[] = [];
				while (
					index < lines.length &&
					MobileAiResultRenderer.isTableLine(lines[index])
				) {
					block.push(lines[index]);
					index++;
				}
				const tableHtml = MobileAiResultRenderer.renderTable(block);
				if (tableHtml) {
					flushParagraph();
					closeList();
					output.push(tableHtml);
				} else {
					closeList();
					paragraphLines.push(...block);
				}
				continue;
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
				index++;
				continue;
			}

			closeList();
			paragraphLines.push(line);
			index++;
		}
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

	private static isTableLine(line: string): boolean {
		return line.trim().startsWith('|');
	}

	private static splitTableCells(line: string): string[] {
		let text = line.trim();
		if (text.startsWith('|')) {
			text = text.slice(1);
		}
		if (text.endsWith('|')) {
			text = text.slice(0, -1);
		}
		return text.split('|').map((cell) => cell.trim());
	}

	private static isTableSeparator(line: string): boolean {
		const cells = MobileAiResultRenderer.splitTableCells(line);
		return (
			cells.length >= 2 &&
			cells.every((cell) => /^:?-{3,}:?$/.test(cell))
		);
	}

	/** Header plus a separator row. Later body rows appear as they stream in. */
	private static renderTable(block: string[]): string | null {
		const separatorIndex = block.findIndex((line) =>
			MobileAiResultRenderer.isTableSeparator(line),
		);
		if (separatorIndex !== 1) {
			return null;
		}
		const header = MobileAiResultRenderer.splitTableCells(block[0]);
		if (header.length < 2) {
			return null;
		}
		const aligns = MobileAiResultRenderer.splitTableCells(block[1]).map(
			(cell) => {
				const left = cell.startsWith(':');
				const right = cell.endsWith(':');
				if (left && right) {
					return 'center';
				}
				if (right) {
					return 'right';
				}
				return 'left';
			},
		);
		const rows = block
			.slice(2)
			.map((line) => MobileAiResultRenderer.splitTableCells(line))
			.filter((cells) => cells.some((cell) => cell.length > 0));
		const columnCount = Math.max(
			header.length,
			aligns.length,
			...rows.map((row) => row.length),
			1,
		);
		const renderRow = (cells: string[], headerCell: boolean): string => {
			const padded = cells.slice(0, columnCount);
			while (padded.length < columnCount) {
				padded.push('');
			}
			const tag = headerCell ? 'th' : 'td';
			return (
				'<tr>' +
				padded
					.map((cell, cellIndex) => {
						const align = aligns[cellIndex] || 'left';
						const style =
							'border:1px solid #d5dbe3;padding:4px 8px;vertical-align:top;text-align:' +
							align +
							';' +
							(headerCell ? 'background:#e8edf2;font-weight:600;' : '');
						return `<${tag} style="${style}">${MobileAiResultRenderer.inline(cell)}</${tag}>`;
					})
					.join('') +
				'</tr>'
			);
		};
		return (
			'<div style="overflow-x:auto;max-width:100%;-webkit-overflow-scrolling:touch;">' +
			'<table style="border-collapse:collapse;width:100%;margin:4px 0;font-size:inherit;line-height:1.4;">' +
			'<thead>' +
			renderRow(header, true) +
			'</thead><tbody>' +
			rows.map((row) => renderRow(row, false)).join('') +
			'</tbody></table></div>'
		);
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
