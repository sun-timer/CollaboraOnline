/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;
var { JSDOM } = require('jsdom');

describe('Writer function sheet shell', function () {
	let dom: import('jsdom').JSDOM;
	let document: Document;
	let window: Window & typeof globalThis;

	beforeEach(function () {
		dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', {
			url: 'https://localhost/',
		});
		document = dom.window.document;
		window = dom.window as Window & typeof globalThis;
		(global as any).document = document;
		(global as any).window = window;
		(window as any).ThisIsTheiOSApp = true;
		delete (window as any).__coolWriterEditorPanel;
		delete (window as any).__coolWriterAiPanel;
	});

	afterEach(function () {
		delete (global as any).document;
		delete (global as any).window;
	});

	it('WriterEditorSheet uses PreviewFunctionSheet-aligned chrome classes', function () {
		const sheet = new WriterEditorSheet('功能');
		sheet.open();
		assert.ok(document.querySelector('.writer-function-sheet'));
		const panel = document.querySelector('.writer-function-sheet__panel');
		assert.ok(panel);
		assert.ok(document.querySelector('.writer-function-sheet__grabber'));
		assert.ok(document.querySelector('.writer-function-sheet__title'));
		const close = document.querySelector('.writer-function-sheet__close');
		assert.ok(close);
		assert.equal(close?.getAttribute('aria-label'), '关闭功能面板');
		assert.ok(close?.querySelector('svg'));
		assert.equal(
			document.querySelector('.writer-function-sheet__title')?.textContent,
			'功能',
		);
		sheet.close();
		assert.equal(document.querySelector('.writer-function-sheet'), null);
	});

	it('WriterEditorPanel tab bar uses pill track and header action trio', function () {
		const aiCalls: string[] = [];
		const focusCalls: boolean[] = [];
		(window as any).__coolWriterAiPanel = {
			openOperationSheet(): void {
				aiCalls.push('openOperationSheet');
			},
		};
		(window as any).app = {
			map: {
				focus(showKeyboard: boolean): void {
					focusCalls.push(showKeyboard);
				},
			},
		};

		const panel = WriterEditorPanel.mount();
		assert.ok(panel);
		panel.open();

		assert.ok(document.querySelector('.writer-function-tab-track'));
		assert.ok(document.querySelector('.writer-function-tab--active'));
		assert.equal(
			document.querySelectorAll('.writer-function-tab').length,
			WriterEditorCatalog.TABS.length,
		);
		assert.equal(
			document.querySelectorAll('.writer-function-action-btn').length,
			3,
		);

		const aiBtn = document.querySelector(
			'.writer-function-action-btn[aria-label="AI功能"]',
		) as HTMLButtonElement;
		const keyboardBtn = document.querySelector(
			'.writer-function-action-btn[aria-label="呼出键盘"]',
		) as HTMLButtonElement;
		assert.ok(aiBtn);
		assert.ok(keyboardBtn);
		aiBtn.click();
		assert.deepEqual(aiCalls, ['openOperationSheet']);
		assert.equal(document.querySelector('.writer-function-sheet'), null);

		panel.open();
		keyboardBtn.click();
		assert.deepEqual(focusCalls, [true]);
		assert.equal(document.querySelector('.writer-function-sheet'), null);
	});

	it('WriterEditorIcons exposes function-panel header icons', function () {
		['ai-sparkle', 'keyboard', 'collapse', 'close'].forEach((key) => {
			assert.ok(WriterEditorIcons.has(key), key);
		});
	});

	it('WriterEditorPanel file tab renders PreviewFunctionSheet-aligned list rows', function () {
		(window as any).app = { map: { on() {}, off() {} } };
		const panel = WriterEditorPanel.mount();
		assert.ok(panel);
		panel.open();

		const fileTab = Array.from(
			document.querySelectorAll('.writer-function-tab'),
		).find((tab) => tab.textContent === '文件') as HTMLButtonElement;
		assert.ok(fileTab);
		fileTab.click();

		assert.ok(document.querySelector('.writer-function-list'));
		assert.equal(
			document.querySelectorAll('.writer-function-list-row').length,
			4,
		);
		assert.equal(
			document.querySelectorAll('.writer-function-list-divider').length,
			3,
		);
		assert.ok(document.querySelector('.writer-function-hint--hidden'));
	});

	it('WriterEditorPanel insert tab uses dedicated 2×3 grid class', function () {
		(window as any).app = { map: { on() {}, off() {} } };
		const panel = WriterEditorPanel.mount();
		assert.ok(panel);
		panel.open();

		const insertTab = Array.from(
			document.querySelectorAll('.writer-function-tab'),
		).find((tab) => tab.textContent === '插入') as HTMLButtonElement;
		assert.ok(insertTab);
		insertTab.click();

		const grid = document.querySelector('.writer-function-grid--insert');
		assert.ok(grid);
		assert.equal(
			document.querySelectorAll('.writer-function-tile').length,
			WriterEditorCatalog.getFeatures('insert').length,
		);
	});
});
