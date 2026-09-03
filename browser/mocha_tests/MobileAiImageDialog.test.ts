/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile AI Image Dialog', function () {
	it('builds the image_generate request with trimmed description', function () {
		assert.deepEqual(MobileAiImageDialog.buildPayload('  夕阳下的海面插画  '), {
			taskType: 'image_generate',
			selection: '夕阳下的海面插画',
			context: {},
		});
	});

	it('builds an empty selection payload for a blank description', function () {
		const payload = MobileAiImageDialog.buildPayload('  ');
		assert.equal(payload.selection, '');
	});
});