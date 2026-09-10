/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile Selection Menu', function () {
	it('lists all Writer edit-mode AI task types aligned with Android', function () {
		const types = MobileSelectionMenu.aiTaskTypes();
		assert.deepEqual(types, [
			'translate',
			'outline',
			'continue',
			'article_generate',
			'expand',
			'polish',
			'condense',
			'rewrite',
		]);
	});

	it('edit menu exposes clipboard + AI ids in 5+5+2 order', function () {
		const ids = MobileSelectionMenu.editMenuItems().map((item) => item.id);
		assert.deepEqual(ids, [
			'copy',
			'cut',
			'paste',
			'select_all',
			'translate',
			'outline',
			'continue_write',
			'article_generate',
			'expand',
			'polish',
			'condense',
			'rewrite',
		]);
	});

	it('ships Figma/Android icons for every menu item', function () {
		MobileSelectionMenu.editMenuItems().forEach((item) => {
			assert.ok(MobileSelectionMenuIcons.has(item.iconKey), item.iconKey);
		});
	});
});
