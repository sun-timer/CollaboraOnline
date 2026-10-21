/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('MobileWriterComments', function () {
	it('summarizeWriterComments keeps root unresolved comments', function () {
		const out = MobileWriterComments.summarizeWriterComments(
			[
				{ id: '1', author: 'A', text: 'Hello', parent: '0', resolved: 'false' },
				{ id: '3', author: 'LocalUser#12', text: 'Legacy author', parent: '0' },
				{ id: '2', author: 'B', text: 'Reply', parent: '1' },
				{ id: 'new', author: 'C', text: 'Draft', parent: '0' },
			],
			false,
		);
		assert.equal(out.length, 2);
		assert.equal(out[0].id, '1');
		assert.equal(out[0].author, 'A');
		assert.equal(out[1].author, 'LocalUser#12');
	});

	it('remaps only the exact current-view author to the Settings nickname', function () {
		const out = MobileWriterComments.summarizeWriterComments(
			[
				{ id: '1', author: 'LocalUser#12', text: 'Mine', parent: '0' },
				{ id: '2', author: 'LocalUser#13', text: 'Unknown legacy author', parent: '0' },
				{ id: '3', author: 'Alice', text: 'Other author', parent: '0' },
			],
			false,
			{ 'LocalUser#12': 'Settings Nickname' },
		);
		assert.deepEqual(out.map((comment: any) => comment.author), [
			'Settings Nickname',
			'LocalUser#13',
			'Alice',
		]);
	});

	it('requests the existing native comment list without inserting an annotation', function () {
		const originalIOS = (window as any).ThisIsTheiOSApp;
		(window as any).ThisIsTheiOSApp = true;
		const messages: string[] = [];
		try {
			assert.equal(
				MobileWriterComments.requestNativeCommentList((message) => messages.push(message)),
				true,
			);
		} finally {
			(window as any).ThisIsTheiOSApp = originalIOS;
		}
		assert.deepEqual(messages, ['SHOWCOMMENTLIST']);
	});

	it('summarizeWriterComments hides resolved unless requested', function () {
		const hidden = MobileWriterComments.summarizeWriterComments(
			[{ id: '9', author: 'X', text: 'Done', parent: '0', resolved: 'true' }],
			false,
		);
		assert.equal(hidden.length, 0);
		const shown = MobileWriterComments.summarizeWriterComments(
			[{ id: '9', author: 'X', text: 'Done', parent: '0', resolved: 'true' }],
			true,
		);
		assert.equal(shown.length, 1);
		assert.equal(shown[0].resolved, true);
	});
});
