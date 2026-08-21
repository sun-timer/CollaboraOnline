/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile AI operation rendering', function () {
	it('escapes model HTML and keeps the supported Markdown subset', function () {
		const html = MobileAiResultRenderer.toHtml(
			'**重点**\n- 一项\n<script>alert(1)</script>',
		);
		assert.ok(html.indexOf('<strong>重点</strong>') >= 0);
		assert.ok(html.indexOf('<li>一项</li>') >= 0);
		assert.ok(html.indexOf('&lt;script&gt;') >= 0);
		assert.equal(html.indexOf('<script>'), -1);
	});
});
