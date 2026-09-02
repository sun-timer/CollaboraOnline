/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * Mirrors FormatBatchProcessor.java rule semantics 1:1 (Android parity).
 */

var assert = require('assert').strict;

describe('FormatBatchProcessor', function () {
	const F = FormatBatchProcessor;

	function allOff(): boolean[] {
		return [false, false, false, false, false, false];
	}

	it('converts common English punctuation to Chinese (EN→ZH)', function () {
		const options = allOff();
		options[F.RULE_EN_TO_ZH_PUNCT] = true;
		assert.equal(
			F.process('Hello, world! Are you ok? (yes); go:', options),
			'Hello， world！ Are you ok？ （yes）； go：',
		);
	});

	it('converts Chinese punctuation back to English (ZH→EN)', function () {
		const options = allOff();
		options[F.RULE_ZH_TO_EN_PUNCT] = true;
		assert.equal(F.process('你好，世界！（测试）；好的：', options), '你好,世界!(测试);好的:');
	});

	it('turns ghost characters into spaces and collapses runs', function () {
		const options = allOff();
		options[F.RULE_GHOST_TO_SPACE] = true;
		assert.equal(F.process('a\u200Bb\uFEFF c\u00ADd', options), 'a b c d');
	});

	it('collapses 3+ newlines into one blank line and trims edges', function () {
		const options = allOff();
		options[F.RULE_REMOVE_EXTRA_BLANK_LINES] = true;
		assert.equal(
			F.process('\n\n开头\r\n中段\n\n\n\n结尾\n\n', options),
			'开头\n中段\n\n结尾',
		);
	});

	it('removes wavy-underline artifact characters', function () {
		const options = allOff();
		options[F.RULE_REMOVE_WAVY_UNDERLINE] = true;
		assert.equal(
			F.process('\u00AD\u223C\uFF5E\u2307wavy\uFE4F', options),
			'wavy',
		);
	});

	it('removes hyperlink markers (markdown, inline URL, bare URL)', function () {
		const options = allOff();
		options[F.RULE_REMOVE_HYPERLINK] = true;
		assert.equal(
			F.process(
				'[链接](https://example.com/page) 描述(https://x.io/a) 尾部 https://raw.dev/x',
				options,
			),
			'链接 描述 尾部 ',
		);
	});

	it('applies only the enabled rules and leaves text intact when all off', function () {
		const options = allOff();
		options[F.RULE_GHOST_TO_SPACE] = true;
		options[F.RULE_REMOVE_EXTRA_BLANK_LINES] = true;
		assert.equal(F.process('a\u200Bb\n\n\n\nc', options), 'a b\n\nc');
		assert.equal(F.process('原文,不变。', allOff()), '原文,不变。');
	});
});