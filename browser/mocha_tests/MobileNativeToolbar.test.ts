/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile native toolbar platform contract', function () {
	it('uses the native shell for iPhone and iPad mobile UI', function () {
		assert.equal(
			shouldUseNativeMobileToolbar({ android: false, ios: true, mobile: true }),
			true,
		);
		assert.equal(
			shouldUseNativeMobileToolbar({ android: false, ios: true, mobile: false }),
			false,
		);
	});

	it('preserves Android native toolbar behavior and desktop isolation', function () {
		assert.equal(
			shouldUseNativeMobileToolbar({ android: true, ios: false, mobile: true }),
			true,
		);
		assert.equal(
			shouldUseNativeMobileToolbar({ android: false, ios: false, mobile: true }),
			false,
		);
	});
});
