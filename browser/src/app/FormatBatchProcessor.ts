/*
 * Local format-batch text rules, shared by Android and iOS.
 *
 * 1:1 port of FormatBatchProcessor.java (android/lib .../ai/) so both
 * platforms apply identical rules. No AI network, no credentials.
 */

class FormatBatchProcessor {
	static readonly RULE_EN_TO_ZH_PUNCT = 0; // 英文标点转中文
	static readonly RULE_ZH_TO_EN_PUNCT = 1; // 中文标点转英文
	static readonly RULE_GHOST_TO_SPACE = 2; // 幽灵字符转空格
	static readonly RULE_REMOVE_EXTRA_BLANK_LINES = 3; // 删除多余空行
	static readonly RULE_REMOVE_WAVY_UNDERLINE = 4; // 消除下滑波浪线
	static readonly RULE_REMOVE_HYPERLINK = 5; // 消除超链接识别

	static readonly RULE_COUNT = 6;

	/** Order matches RULE_* constants; used by the DOM checkbox form. */
	static readonly RULE_LABELS = [
		'英文标点转中文',
		'中文标点转英文',
		'幽灵字符转空格',
		'删除多余空行',
		'消除下滑波浪线',
		'消除超链接',
	];

	private constructor() {}

	/** Apply enabled rules in fixed order; options length must be RULE_COUNT. */
	static process(input: string, options: boolean[]): string {
		let text = input;
		if (options.length > FormatBatchProcessor.RULE_EN_TO_ZH_PUNCT &&
			options[FormatBatchProcessor.RULE_EN_TO_ZH_PUNCT]) {
			text = FormatBatchProcessor.enPunctToZh(text);
		}
		if (options.length > FormatBatchProcessor.RULE_ZH_TO_EN_PUNCT &&
			options[FormatBatchProcessor.RULE_ZH_TO_EN_PUNCT]) {
			text = FormatBatchProcessor.zhPunctToEn(text);
		}
		if (options.length > FormatBatchProcessor.RULE_GHOST_TO_SPACE &&
			options[FormatBatchProcessor.RULE_GHOST_TO_SPACE]) {
			text = FormatBatchProcessor.ghostCharsToSpace(text);
		}
		if (options.length > FormatBatchProcessor.RULE_REMOVE_EXTRA_BLANK_LINES &&
			options[FormatBatchProcessor.RULE_REMOVE_EXTRA_BLANK_LINES]) {
			text = FormatBatchProcessor.removeExtraBlankLines(text);
		}
		if (options.length > FormatBatchProcessor.RULE_REMOVE_WAVY_UNDERLINE &&
			options[FormatBatchProcessor.RULE_REMOVE_WAVY_UNDERLINE]) {
			text = FormatBatchProcessor.removeWavyUnderlineArtifacts(text);
		}
		if (options.length > FormatBatchProcessor.RULE_REMOVE_HYPERLINK &&
			options[FormatBatchProcessor.RULE_REMOVE_HYPERLINK]) {
			text = FormatBatchProcessor.removeHyperlinkMarkers(text);
		}
		return text;
	}

	private static enPunctToZh(text: string): string {
		return text
			.split(',').join('，')
			.split('.').join('。')
			.split('!').join('！')
			.split('?').join('？')
			.split(';').join('；')
			.split(':').join('：')
			.split('(').join('（')
			.split(')').join('）');
	}

	private static zhPunctToEn(text: string): string {
		return text
			.split('，').join(',')
			.split('。').join('.')
			.split('！').join('!')
			.split('？').join('?')
			.split('；').join(';')
			.split('：').join(':')
			.split('（').join('(')
			.split('）').join(')');
	}

	private static ghostCharsToSpace(text: string): string {
		// Zero-width chars / BOM / soft hyphen → single space, then collapse.
		const replaced = text
			.split('\u200B').join(' ')
			.split('\u200C').join(' ')
			.split('\u200D').join(' ')
			.split('\uFEFF').join(' ')
			.split('\u00AD').join(' ');
		return replaced.replace(/[ \t]{2,}/g, ' ');
	}

	private static removeExtraBlankLines(text: string): string {
		const collapsed = text
			.replace(/\r\n|\r/g, '\n')
			.replace(/\n{3,}/g, '\n\n');
		// Java String.trim() removes only chars <= U+0020; String.prototype.trim
		// removes all Unicode whitespace. Keep Android parity explicitly.
		return collapsed.replace(/^[\u0000-\u0020]+/, '').replace(/[\u0000-\u0020]+$/, '');
	}

	private static removeWavyUnderlineArtifacts(text: string): string {
		return text
			.split('\u00AD').join('')
			.split('\u2307').join('')
			.split('\uFE26').join('')
			.split('\uFE4F').join('')
			.split('\u0330').join('')
			.split('\u0334').join('')
			.split('\u223C').join('')
			.split('\uFF5E').join('')
			.split('\u2305').join('');
	}

	private static removeHyperlinkMarkers(text: string): string {
		let result = text;
		// [text](url) → text
		result = result.replace(/\[([^\]]+?)\]\((https?:\/\/[^\s)]+)\)/g, '$1');
		// 文本(url) → 文本
		result = result.replace(/([^\s(]*)\((https?:\/\/[^\s)]+)\)/g, '$1');
		// Bare URL inline marker → removed (keep surrounding text).
		result = result.replace(/https?:\/\/[^\s））]+/g, '');
		return result;
	}
}
if (typeof window !== 'undefined') {
	(window as any).FormatBatchProcessor = FormatBatchProcessor;
}