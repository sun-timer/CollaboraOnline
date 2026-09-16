/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterFindReplaceDialog', function () {
	it('uses find-only title in preview mode', function () {
		assert.equal(WriterFindReplaceDialog.titleForReplaceEnabled(false), '查找');
		assert.equal(WriterFindReplaceDialog.titleForReplaceEnabled(true), '查找替换');
	});

	it('parses replaceEnabled open option', function () {
		assert.equal(WriterFindReplaceDialog.isReplaceEnabledOption(undefined), false);
		assert.equal(WriterFindReplaceDialog.isReplaceEnabledOption({}), false);
		assert.equal(
			WriterFindReplaceDialog.isReplaceEnabledOption({ replaceEnabled: false }),
			false,
		);
		assert.equal(
			WriterFindReplaceDialog.isReplaceEnabledOption({ replaceEnabled: true }),
			true,
		);
	});
});
