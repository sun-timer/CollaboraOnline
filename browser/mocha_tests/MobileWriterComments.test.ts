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
				{ id: '2', author: 'B', text: 'Reply', parent: '1' },
				{ id: 'new', author: 'C', text: 'Draft', parent: '0' },
			],
			false,
		);
		assert.equal(out.length, 1);
		assert.equal(out[0].id, '1');
		assert.equal(out[0].author, 'A');
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
