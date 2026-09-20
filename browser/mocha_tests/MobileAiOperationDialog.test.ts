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

	it('does not add browser paragraph margins around Markdown blocks', function () {
		const html = MobileAiResultRenderer.toHtml('第一段\n\n第二段');
		assert.ok(html.indexOf('<p style="margin:0;">第一段</p><br><p style="margin:0;">第二段</p>') >= 0);
	});

	it('renders ordered Markdown items without paragraph spacing inflation', function () {
		const html = MobileAiResultRenderer.toHtml('1. 第一项\n\n2. 第二项');
		assert.ok(html.indexOf('<ol><li>第一项</li></ol>') >= 0);
		assert.ok(html.indexOf('<ol><li>第二项</li></ol>') >= 0);
	});
});
