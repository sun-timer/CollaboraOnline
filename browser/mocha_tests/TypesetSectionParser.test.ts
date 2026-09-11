/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Typeset section parser', function () {
	it('parses wrapped sections JSON from AI output', function () {
		const sections = TypesetSectionParser.parse(
			'```json\n{"sections":{"title":"标题","body":"正文"}}\n```',
		);
		assert.ok(sections);
		assert.equal(sections.title, '标题');
		assert.equal(sections.body, '正文');
	});

	it('builds sections from paragraph classifications', function () {
		const sections = TypesetSectionParser.parseParagraphClassifications(
			'[{"paraIndex":0,"section":"title"},{"paraIndex":1,"section":"body"}]',
			['标题', '正文段落'],
		);
		assert.ok(sections);
		assert.equal(sections.title, '标题');
		assert.equal(sections.body, '正文段落');
	});

	it('builds preview HTML for gov sections', function () {
		const html = TypesetPreviewHtml.build('gov', {
			title: '通知',
			body: '正文内容',
		});
		assert.ok(html.indexOf('通知') >= 0);
		assert.ok(html.indexOf('正文内容') >= 0);
	});
});
