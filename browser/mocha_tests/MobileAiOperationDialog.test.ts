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

	it('renders a Markdown table and keeps surrounding text', function () {
		const html = MobileAiResultRenderer.toHtml(
			'指标如下：\n\n| Metric | Value |\n| --- | ---: |\n| Rows | 15 |\n\n结束',
		);
		assert.ok(html.indexOf('<table') >= 0);
		assert.ok(html.indexOf('<th') >= 0 && html.indexOf('Metric') >= 0);
		assert.ok(html.indexOf('<td') >= 0 && html.indexOf('Rows') >= 0);
		assert.ok(html.indexOf('text-align:right') >= 0);
		assert.ok(html.indexOf('指标如下：') >= 0);
		assert.ok(html.indexOf('结束') >= 0);
		assert.equal(html.indexOf('| Metric |'), -1);
	});

	it('keeps an unfinished table as text until the separator row arrives', function () {
		const html = MobileAiResultRenderer.toHtml('| Metric | Value |');
		assert.equal(html.indexOf('<table'), -1);
		assert.ok(html.indexOf('| Metric | Value |') >= 0);
	});

	it('renders ordered Markdown items without paragraph spacing inflation', function () {
		const html = MobileAiResultRenderer.toHtml('1. 第一项\n\n2. 第二项');
		assert.ok(html.indexOf('<ol><li>第一项</li></ol>') >= 0);
		assert.ok(html.indexOf('<ol><li>第二项</li></ol>') >= 0);
	});
});
