/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile AI UI Catalog', function () {
	it('keeps Android Writer groups and task mappings stable', function () {
		const writerEntries = MobileAiUiCatalog.getEntries('text');
		assert.deepEqual(
			writerEntries.slice(0, 8).map(function (entry) {
				return entry.taskType;
			}),
			[
				'continue',
				'outline',
				'article_generate',
				'polish',
				'expand',
				'condense',
				'rewrite',
				'translate',
			],
		);
		assert.equal(
			MobileAiUiCatalog.getEntry('continue')?.androidTaskType,
			'continue_write',
		);
		assert.equal(
			MobileAiUiCatalog.getEntry('continue')?.resultMode,
			'appendAfterSelection',
		);
	});

	it('shows unsupported Android entries without allowing requests', function () {
		assert.ok(
			MobileAiUiCatalog.getEntries('text').some(function (entry) {
				return entry.taskType === 'outline' && entry.iosSupport;
			}),
		);
		assert.ok(
			MobileAiUiCatalog.getEntries('text').some(function (entry) {
				return entry.taskType === 'article_generate' && entry.iosSupport;
			}),
		);
		assert.ok(
			MobileAiUiCatalog.getEntries('spreadsheet').some(function (entry) {
				return entry.taskType === 'calc_formula' && entry.iosSupport;
			}),
		);
		assert.ok(
			MobileAiUiCatalog.getEntries('spreadsheet').some(function (entry) {
				return entry.taskType === 'calc_data_analysis' && entry.iosSupport;
			}),
		);
		assert.ok(
			MobileAiUiCatalog.getEntries('spreadsheet').some(function (entry) {
				return entry.taskType === 'calc_chart' && entry.iosSupport;
			}),
		);
		assert.equal(MobileAiUiCatalog.canRun('outline', 'text'), true);
		assert.equal(MobileAiUiCatalog.canRun('article_generate', 'text'), true);
		assert.equal(MobileAiUiCatalog.canRun('polish', 'text'), true);
		assert.equal(MobileAiUiCatalog.canRun('polish', 'spreadsheet'), false);
		assert.equal(MobileAiUiCatalog.canRun('calc_formula', 'spreadsheet'), true);
		assert.equal(
			MobileAiUiCatalog.canRun('calc_data_analysis', 'spreadsheet'),
			true,
		);
		assert.equal(MobileAiUiCatalog.canRun('calc_chart', 'spreadsheet'), true);
	});

	it('keeps summarize available without making it an Android operation card', function () {
		const summarize = MobileAiUiCatalog.getEntry('summarize');
		assert.ok(summarize);
		assert.equal(summarize?.iosSupport, true);
		assert.equal(summarize?.includeInOperationSheet, false);
	});

	it('enables text extraction on iOS with the operation dialog', function () {
		const extract = MobileAiUiCatalog.getEntry('text_extract');
		assert.ok(extract);
		assert.equal(extract?.iosSupport, true);
		assert.equal(extract?.dialog, 'operation');
		assert.equal(MobileAiUiCatalog.canRun('text_extract', 'text'), true);
	});

	it('enables image generation on iOS with the image dialog', function () {
		const image = MobileAiUiCatalog.getEntry('image_generate');
		assert.ok(image);
		assert.equal(image?.iosSupport, true);
		assert.equal(image?.dialog, 'image');
		assert.equal(MobileAiUiCatalog.canRun('image_generate', 'text'), true);
	});
});
