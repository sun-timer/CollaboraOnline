/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterWordCountSheet', function () {
	it('getRowDefs mirrors Android WordCountSheetController rows', function () {
		const rows = WriterWordCountSheet.getRowDefs();
		assert.deepEqual(
			rows.map((row) => row.id),
			['docwords', 'docchars', 'doccharsnospaces', 'doccjkchars', 'docComments'],
		);
	});

	it('normalizeCount formats numeric strings with grouping', function () {
		assert.equal(WriterWordCountSheet.normalizeCount('1234'), '1,234');
		assert.equal(WriterWordCountSheet.normalizeCount(''), '0');
		assert.equal(WriterWordCountSheet.normalizeCount('n/a'), 'n/a');
	});
});
