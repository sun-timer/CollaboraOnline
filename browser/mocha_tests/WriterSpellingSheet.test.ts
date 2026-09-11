/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterSpellingSheet', function () {
	it('detects spell-check completion messageboxes', function () {
		const msgData = {
			id: 42,
			type: 'messagebox',
			title: '拼写检查',
			text: '已经完成拼写检查',
			children: [{ type: 'okbutton', id: 'ok' }],
		};
		assert.equal(WriterSpellingSheet.isSessionOpen(), false);
		assert.equal(
			WriterSpellingSheet.shouldInterceptMessagebox(msgData),
			false,
		);
	});

	it('parseSuggestionEntry handles string and object entries', function () {
		(window as any).ThisIsTheiOSApp = true;
		const sheet = WriterSpellingSheet.mount();
		assert.ok(sheet);
		const parse = (sheet as any).parseSuggestionEntry.bind(sheet);
		parse('hello', 0);
		parse({ row: 2, text: 'world', selected: true }, 1);
		assert.deepEqual((sheet as any).suggestionTexts, ['hello', 'world']);
		assert.deepEqual((sheet as any).suggestionRows, [0, 2]);
		assert.equal((sheet as any).selectedRow, 2);
		delete (window as any).ThisIsTheiOSApp;
		delete (window as any).__coolWriterSpellingSheet;
	});
});
