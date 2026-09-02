/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Writer AI Catalog v1', function () {
	it('exposes the seven Writer P0 task types in protocol order', function () {
		assert.deepEqual(WriterAiCatalog.P0_TASK_TYPES, [
			'polish',
			'translate',
			'expand',
			'condense',
			'rewrite',
			'continue',
			'summarize',
		]);
	});

	it('defines a unique prompt and result mode for every P0 task', function () {
		const promptIds: { [promptId: string]: boolean } = {};
		WriterAiCatalog.P0_TASK_TYPES.forEach(function (taskType) {
			const task = WriterAiCatalog.getTask(taskType);
			assert.ok(task);
			assert.equal(promptIds[task.promptId], undefined);
			promptIds[task.promptId] = true;
			assert.ok(task.resultMode === 'replaceSelection' || task.resultMode === 'appendAfterSelection');
			assert.deepEqual(task.requiredInput, 'selection');
		});
		assert.equal(WriterAiCatalog.getTask('chat'), null);
		assert.equal(WriterAiCatalog.getTask('doc_qa'), null);
	});

	it('validates task-specific options and secure-field boundaries', function () {
		assert.equal(
			WriterAiCatalog.validateRequest({
				taskType: 'polish',
				selection: '原始文本',
				context: { polishStyle: 'formal' },
			}).valid,
			true,
		);
		assert.equal(
			WriterAiCatalog.validateRequest({
				taskType: 'translate',
				selection: '原始文本',
				context: { sourceLang: 'auto', targetLang: 'en' },
			}).valid,
			true,
		);
		assert.equal(
			WriterAiCatalog.validateRequest({
				taskType: 'translate',
				selection: '原始文本',
				context: { targetLang: 'xx' },
			}).errorCode,
			'invalid_target_language',
		);
		assert.equal(
			WriterAiCatalog.validateRequest({
				taskType: 'polish',
				selection: '原始文本',
				context: { polishStyle: 'unknown' },
			}).errorCode,
			'invalid_polish_style',
		);
		assert.equal(
			WriterAiCatalog.validateRequest({
				taskType: 'polish',
				selection: '原始文本',
				apiKey: 'must-not-cross-bridge',
			}).errorCode,
			'sensitive_field',
		);
	});

	it('uses the Android-compatible defaults and article template metadata', function () {
		assert.equal(WriterAiCatalog.DEFAULT_POLISH_STYLE, 'quick');
		assert.equal(WriterAiCatalog.DEFAULT_SOURCE_LANGUAGE, 'auto');
		assert.equal(WriterAiCatalog.DEFAULT_TARGET_LANGUAGE, 'zh');
		assert.ok(WriterAiCatalog.ARTICLE_TEMPLATES.general_notice);
		assert.deepEqual(WriterAiCatalog.ARTICLE_TEMPLATES.general_notice.variables, [
			'通知主要内容',
			'通知时间',
		]);
		assert.equal(WriterAiCatalog.ARTICLE_TEMPLATES.douyin_script.category, '营销类');
	});

	it('defines document-level insertAtEnd tasks for the iOS migration', function () {
		const outline = WriterAiCatalog.getTask('outline');
		assert.ok(outline);
		assert.equal(outline.resultMode, 'insertAtEnd');
		assert.equal(outline.requiredInput, 'document');

		const article = WriterAiCatalog.getTask('article_generate');
		assert.ok(article);
		assert.equal(article.resultMode, 'insertAtEnd');
		assert.equal(article.requiredInput, 'prompt');

		// Document/prompt-level tasks must not demand a text selection.
		assert.equal(
			WriterAiCatalog.validateRequest({ taskType: 'outline' }).valid,
			true,
		);
		assert.equal(outline.promptId, 'writer.outline');

		// Android 对齐：paper/report/speech/event/general（默认 general）。
		assert.deepEqual(
			WriterAiCatalog.OUTLINE_TYPES.map((item) => item.key),
			['paper', 'report', 'speech', 'event', 'general'],
		);
	});

	it('defines the image-based text_extract task', function () {
		const extract = WriterAiCatalog.getTask('text_extract');
		assert.ok(extract);
		assert.equal(extract.promptId, 'writer.text_extract');
		assert.equal(extract.resultMode, 'insertAtEnd');
		assert.equal(extract.requiredInput, 'document');
		assert.equal(
			WriterAiCatalog.validateRequest({ taskType: 'text_extract' }).valid,
			true,
		);
	});
});
