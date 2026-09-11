/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterParagraphPanel', function () {
	it('getQuickActionItems exposes six Android-mirrored UNO commands', function () {
		const items = WriterParagraphPanel.getQuickActionItems();
		assert.equal(items.length, 6);
		assert.deepEqual(
			items.map((item) => item.command),
			[
				'.uno:LeftPara',
				'.uno:CenterPara',
				'.uno:RightPara',
				'.uno:JustifyPara',
				'.uno:DefaultBullet',
				'.uno:DefaultNumbering',
			],
		);
	});

	it('getQuickActionItems uses writer editor icons for each action', function () {
		const items = WriterParagraphPanel.getQuickActionItems();
		items.forEach((item) => {
			assert.ok(WriterEditorIcons.has(item.iconKey), item.iconKey);
		});
	});
});
