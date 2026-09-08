/*
 * Parse AI typeset V2 JSON responses into section maps.
 */

class TypesetSectionParser {
	static parse(raw: string): { [key: string]: string } | null {
		if (!raw || typeof raw !== 'string') {
			return null;
		}
		let json = TypesetSectionParser.stripMarkdownFences(raw.trim());
		try {
			const root = JSON.parse(json);
			if (!root || typeof root !== 'object' || Array.isArray(root)) {
				return null;
			}
			if (root.sections && typeof root.sections === 'object') {
				const sections: { [key: string]: string } = {};
				Object.keys(root.sections).forEach((key) => {
					const value = root.sections[key];
					if (typeof value === 'string' && value.trim()) {
						sections[key] = value.trim();
					}
				});
				return Object.keys(sections).length > 0 ? sections : null;
			}
			const flat: { [key: string]: string } = {};
			Object.keys(root).forEach((key) => {
				const value = root[key];
				if (typeof value === 'string' && value.trim()) {
					flat[key] = value.trim();
				}
			});
			return Object.keys(flat).length > 0 ? flat : null;
		} catch (_error) {
			return null;
		}
	}

	static parseParagraphClassifications(
		raw: string,
		paragraphs: string[],
	): { [key: string]: string } | null {
		if (!paragraphs || paragraphs.length === 0) {
			return null;
		}
		let json = TypesetSectionParser.stripMarkdownFences(raw.trim());
		try {
			const parsed = JSON.parse(json);
			const items = Array.isArray(parsed)
				? parsed
				: parsed && Array.isArray(parsed.paragraphs)
					? parsed.paragraphs
					: null;
			if (!items) {
				return null;
			}
			const sections: { [key: string]: string } = {};
			items.forEach((item: any) => {
				if (!item || typeof item !== 'object') {
					return;
				}
				const index =
					typeof item.paraIndex === 'number'
						? item.paraIndex
						: typeof item.index === 'number'
							? item.index
							: -1;
				const section =
					typeof item.section === 'string' ? item.section.trim() : '';
				if (index < 0 || index >= paragraphs.length || !section) {
					return;
				}
				const text = paragraphs[index];
				if (!text) {
					return;
				}
				if (sections[section]) {
					sections[section] += '\n\n' + text;
				} else {
					sections[section] = text;
				}
			});
			return Object.keys(sections).length > 0 ? sections : null;
		} catch (_error) {
			return null;
		}
	}

	private static stripMarkdownFences(text: string): string {
		let value = text.trim();
		if (value.startsWith('```')) {
			const firstNewline = value.indexOf('\n');
			value = firstNewline > 0 ? value.substring(firstNewline + 1) : value.substring(3);
		}
		if (value.endsWith('```')) {
			value = value.substring(0, value.length - 3).trim();
		}
		return value.trim();
	}
}
