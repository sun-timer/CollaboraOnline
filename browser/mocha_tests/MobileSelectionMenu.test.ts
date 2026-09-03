/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile Selection Menu', function () {
	it('lists selection-based AI tasks enabled on iOS', function () {
		const types = MobileSelectionMenu.menuTaskTypes();
		assert.ok(types.indexOf('polish') >= 0);
		assert.ok(types.indexOf('translate') >= 0);
		assert.ok(types.indexOf('expand') >= 0);
		assert.ok(types.indexOf('condense') >= 0);
		assert.ok(types.indexOf('rewrite') >= 0);
		assert.ok(types.indexOf('continue') >= 0);
		// 非选区输入的任务不进选区菜单。
		assert.ok(types.indexOf('outline') < 0);
		assert.ok(types.indexOf('text_extract') < 0);
		assert.ok(types.indexOf('format_batch') < 0);
	});
});