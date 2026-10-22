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

	it('maps the viewport point into the document canvas buffer and magnifies the local crop', function () {
		const crop = MobileSelectionMagnifier.calculateSourceCrop({
			clientX: 150,
			clientY: 90,
			left: 50,
			top: 40,
			cssWidth: 400,
			cssHeight: 200,
			bufferWidth: 800,
			bufferHeight: 600,
			lensWidth: 112,
			lensHeight: 88,
			magnification: 2,
		});
		assert.deepEqual(crop, {
			sx: 144, sy: 84, sw: 112, sh: 132,
			dx: 0, dy: 0, dw: 112, dh: 88,
		});
	});

	it('clips edge crops without stretching them and rejects an unavailable canvas size', function () {
		const crop = MobileSelectionMagnifier.calculateSourceCrop({
			clientX: 0,
			clientY: 0,
			left: 0,
			top: 0,
			cssWidth: 100,
			cssHeight: 100,
			bufferWidth: 100,
			bufferHeight: 100,
			lensWidth: 80,
			lensHeight: 80,
			magnification: 1,
		});
		assert.deepEqual(crop, { sx: 0, sy: 0, sw: 40, sh: 40, dx: 40, dy: 40, dw: 40, dh: 40 });
		assert.equal(
			MobileSelectionMagnifier.calculateSourceCrop({
				clientX: 0,
				clientY: 0,
				left: 0,
				top: 0,
				cssWidth: 0,
				cssHeight: 100,
				bufferWidth: 100,
				bufferHeight: 100,
			}),
			null,
		);
	});
});
