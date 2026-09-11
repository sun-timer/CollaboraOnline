/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile AI operation sheet document routing', function () {
	it('uses the native iOS document type before the WebView map is ready', function () {
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType('spreadsheet', undefined),
			'spreadsheet',
		);
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType('presentation', 'text'),
			'presentation',
		);
	});

	it('falls back to the map type and then Writer only when native type is absent', function () {
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType(undefined, 'spreadsheet'),
			'spreadsheet',
		);
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType(undefined, undefined),
			'text',
		);
	});
});
