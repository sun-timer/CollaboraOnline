/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile Selection Events', function () {
	it('computes CSS anchors from text-selection rectangles', function () {
		const anchor = MobileSelectionEvents.computeAnchorCss({
			topViewY: 100,
			leftViewX: 200,
			rightViewX: 400,
			bottomViewY: 140,
			canvasX: 20,
			canvasY: 40,
			scale: 2,
		});
		assert.deepEqual(anchor, { anchorX: 170, anchorY: 90, anchorBottomY: 110 });
	});

	it('rejects a non-positive scale', function () {
		assert.equal(
			MobileSelectionEvents.computeAnchorCss({
				topViewY: 100,
				leftViewX: 200,
				rightViewX: 400,
				bottomViewY: 140,
				canvasX: 20,
				canvasY: 40,
				scale: 0,
			}),
			null,
		);
	});

	it('caps the broadcast selection text to bound the payload', function () {
		const text = 'a'.repeat(5000);
		assert.equal(MobileSelectionEvents.sanitizeText(text).length, 2000);
		assert.equal(MobileSelectionEvents.sanitizeText('短文'), '短文');
	});
});