/*
 * Copyright the Collabora Online contributors.
 * SPDX-License-Identifier: MPL-2.0
 * Run with Node 24+: node --test mocha_tests/ImpressSelectionMenu.node.test.js
 * Native type stripping runs source without building browser assets.
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { stripTypeScriptTypes } = require('node:module');
const { JSDOM } = require('jsdom');

function createHarness(docType = 'presentation', editable = true) {
	const dom = new JSDOM('<!doctype html><body></body>');
	const window = dom.window;
	const calls = [];
	window.app = { isReadOnly: () => !editable, map: { getDocType: () => docType, isEditMode: () => editable } };
	window.postMobileMessage = message => calls.push(message);
	const dialog = name => class {
		constructor(task) { this.task = task; }
		open() { calls.push([name, this.task]); }
		close() {}
	};
	const context = vm.createContext({ window, document: window.document, console,
		MobileAiOperationDialog: dialog('operation'),
		MobileAiArticleGenerateDialog: dialog('article'),
		MobileAiFormatBatchDialog: dialog('formatBatch'),
		MobileAiImageDialog: dialog('image'),
		MobileAiTranslateDialog: dialog('translate'),
		MobileAiCalcDialog: dialog('calc'),
		MobileAiTypesetDialog: dialog('typeset'),
	});
	for (const file of ['MobileAiUiCatalog.ts', 'MobileSelectionMenuIcons.ts',
		'MobileAiOperationIcons.ts', 'MobileSelectionMenu.ts', 'WriterAiPanel.ts']) {
		const source = fs.readFileSync(path.resolve(__dirname, '../src/app', file), 'utf8');
		vm.runInContext(stripTypeScriptTypes(source), context, { filename: file });
	}
	vm.runInContext('window.__coolWriterAiPanel = Object.create(WriterAiPanel.prototype); window.Menu = MobileSelectionMenu;', context);
	window.__coolMobileSelectionMenu = { hasTextSelection: true, hide() {} };
	const rows = window.Menu.buildRows('text', editable, editable);
	rows.forEach(row => window.document.body.appendChild(row));
	return { window, calls, menu: window.Menu, close: () => dom.window.close() };
}

test('PPT floating menu exposes the PPT AI list with no Word outline or translation', () => {
	const harness = createHarness();
	try {
		const items = harness.menu.editMenuItems('presentation');
		assert.deepEqual(Array.from(items.filter(i => i.kind === 'ai'), i => i.catalogTaskType), [
			'impress_generate', 'continue', 'article_generate', 'expand', 'polish',
			'condense', 'rewrite', 'text_extract', 'image_generate', 'format_batch',
		]);
		assert.equal(harness.window.document.querySelectorAll('.fabric-selection-menu__button').length, 14);
		for (const button of harness.window.document.querySelectorAll('.fabric-selection-menu__button')) {
			assert.ok(button.querySelector('svg'), button.getAttribute('aria-label'));
		}
	} finally { harness.close(); }
});

test('Generate PPT click opens native PPT creation, while Word outline keeps its own dialog', () => {
	for (const [docType, label, expected] of [
		['presentation', '生成PPT', 'IMPRESS_OPEN_OUTLINE'],
		['text', '总结大纲', ['operation', 'outline']],
	]) {
		const harness = createHarness(docType);
		try {
			harness.window.document.querySelector(`[aria-label="${label}"]`).click();
			assert.deepEqual(harness.calls, [expected]);
		} finally { harness.close(); }
	}
});

test('PPT extraction, image and format actions open the existing corresponding dialogs', () => {
	for (const [label, expected] of [
		['文案提取', ['operation', 'text_extract']],
		['AI图片', ['image', undefined]],
		['格式批量处理', ['formatBatch', undefined]],
	]) {
		const harness = createHarness();
		try {
			harness.window.document.querySelector(`[aria-label="${label}"]`).click();
			assert.deepEqual(harness.calls, [expected]);
		} finally { harness.close(); }
	}
});

test('PPT reading mode shows only Copy and Select All', () => {
	const harness = createHarness('presentation', false);
	try {
		const labels = Array.from(harness.window.document.querySelectorAll('.fabric-selection-menu__button'), b => b.getAttribute('aria-label'));
		assert.deepEqual(labels, ['复制', '全选']);
		assert.equal(harness.menu.menuTaskTypesForDocument('presentation', true).length, 0);
	} finally { harness.close(); }
});

test('Writer and Calc task lists retain their existing actions', () => {
	const harness = createHarness();
	try {
		assert.deepEqual(Array.from(harness.menu.aiTaskTypes()), [
			'translate', 'outline', 'continue', 'article_generate', 'expand', 'polish', 'condense', 'rewrite',
		]);
		assert.deepEqual(Array.from(harness.menu.calcMenuItems().filter(i => i.kind === 'ai'), i => i.catalogTaskType), [
			'calc_formula', 'calc_cond_format', 'calc_data_analysis', 'calc_data_process', 'calc_chart',
		]);
	} finally { harness.close(); }
});

test('a stale Word outline item cannot open the Word dialog inside PPT', () => {
	const harness = createHarness();
	try {
		const outline = harness.menu.editMenuItems().find(item => item.id === 'outline');
		harness.menu.onItemTap(outline);
		assert.equal(harness.calls.length, 0);
	} finally { harness.close(); }
});
