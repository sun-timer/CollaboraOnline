/*
 * Copyright the Collabora Online contributors.
 * SPDX-License-Identifier: MPL-2.0
 *
 * Run with Node 24+: node --test mocha_tests/CalcAiDialog.node.test.js
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { stripTypeScriptTypes } = require('node:module');
const test = require('node:test');
const vm = require('node:vm');
const { JSDOM } = require('jsdom');

const chart = 'AI图表生成';

function createHarness(t, bridgeAccepts = true) {
	const dom = new JSDOM('<!doctype html><body></body>');
	const { window } = dom;
	const { document } = window;
	t.after(() => window.close());
	window.ThisIsTheiOSApp = true;
	window.MobileNativeDocumentType = 'spreadsheet';
	let onMessage;
	let completeApply;
	let selectionSnapshot = 'A1:B3';
	let requestCount = 0;
	const acceptance = [];
	const bridge = {
		getSelectedText: () => '',
		getSelectedTextAsync: async () => '',
		isAvailable: () => true,
		subscribe: listener => { onMessage = listener; return () => {}; },
		request: () => 'request-' + ++requestCount,
		accept: (requestId, text) => {
			acceptance.push({ requestId, text });
			return bridgeAccepts;
		},
		cancel: () => true,
	};
	const context = vm.createContext({
		window, document, console,
		MobileAiAssistantPanel: class {},
		MobileAiBridge: { getInstance: () => bridge },
		CalcAiContext: {
			getActiveCellAddress: () => 'A1',
			getSelectedRange: () => 'A1:B3',
			buildFormulaContext: () => ({ cellAddress: 'A1' }),
			buildAnalysisContext: () => ({ cellRange: 'A1:B3' }),
			snapshot: () => selectionSnapshot,
		},
		CalcAiMutateApply: {
			apply: () => new Promise(resolve => { completeApply = resolve; }),
		},
	});
	for (const file of [
		'MobileAiUiCatalog.ts', 'CalcAiCatalog.ts', 'CalcAiController.ts',
		'MobileAiTaskDialogLayout.ts', 'MobileAiOperationIcons.ts',
		'MobileAiSheet.ts', 'MobileAiOperationSheet.ts', 'MobileAiCalcDialog.ts',
		'WriterAiPanel.ts',
	]) {
		const source = fs.readFileSync(path.resolve(__dirname, '../src/app', file), 'utf8');
		vm.runInContext(stripTypeScriptTypes(source), context, { filename: file });
	}
	const panel = vm.runInContext('WriterAiPanel.mount()', context);
	const operations = () => document.querySelector('.mobile-ai-op-root')?.closest('.mobile-ai-sheet-root');
	const dialog = () => document.querySelector('.mobile-ai-sheet-root--writer');
	const confirm = () => dialog().querySelector('.mobile-ai-task-dialog__result-bar .mobile-ai-task-dialog__btn--primary');
	const preview = '{"summary":"预览","chart":{"chartType":"column"}}';
	async function openPreview(label = chart, directTask) {
		panel.openOperationSheet();
		if (directTask) {
			panel.openTask(directTask);
		} else {
			operations().querySelector(`[aria-label="${label}"]`).click();
		}
		dialog().querySelector('textarea').value = '测试';
		dialog().querySelector('.mobile-ai-task-dialog__generate').click();
		await new Promise(resolve => setImmediate(resolve));
		onMessage({ type: 'ai.done', requestId: 'request-1', payload: { fullText: preview } });
		assert.equal(confirm().disabled, false);
		assert.equal(acceptance.length, 0);
		assert.ok(operations());
		assert.ok(dialog());
	}
	async function finish(result) {
		completeApply(result);
		await new Promise(resolve => setImmediate(resolve));
	}
	return {
		panel, operations, dialog, confirm, openPreview, finish, acceptance, preview,
		controller: window.CalcAiController,
		receive: onMessage,
		changeSelection: () => { selectionSnapshot = 'C1:C3'; },
	};
}

for (const label of [chart, 'AI条件格式', 'AI数据处理']) {
	test(`${label} closes both sheets only after successful confirmed apply`, async t => {
		const app = createHarness(t);
		await app.openPreview(label);
		app.confirm().click();
		assert.ok(app.dialog());
		assert.ok(app.operations());
		await app.finish({ ok: true });
		assert.deepEqual(app.acceptance, [{ requestId: 'request-1', text: app.preview }]);
		assert.equal(app.dialog(), null);
		assert.equal(app.operations(), undefined);
	});
}

test('a failed apply leaves both sheets open and shows the failure', async t => {
	const app = createHarness(t);
	await app.openPreview();
	app.confirm().click();
	await app.finish({ ok: false, error: '执行失败' });
	assert.match(app.dialog().textContent, /执行失败/);
	assert.ok(app.operations());
	assert.deepEqual(app.acceptance, []);
});

test('failure to send bridge acceptance leaves both sheets open', async t => {
	const app = createHarness(t, false);
	await app.openPreview();
	app.confirm().click();
	await app.finish({ ok: true });
	assert.match(app.dialog().textContent, /NativeBridge 未确认 AI 结果/);
	assert.ok(app.operations());
});

for (const dismissWith of ['button', 'backdrop']) {
	test(`late apply leaves the parent visible after ${dismissWith} dismissal`, async t => {
		const app = createHarness(t);
		await app.openPreview();
		app.confirm().click();
		const original = app.dialog();
		(dismissWith === 'button' ? original.querySelector('.mobile-ai-sheet-close') : original).click();
		await app.finish({ ok: true });
		assert.equal(app.dialog(), null);
		assert.ok(app.operations());
	});
}

test('late apply leaves a replacement task dialog visible', async t => {
	const app = createHarness(t);
	await app.openPreview();
	app.confirm().click();
	const original = app.dialog();
	app.operations().querySelector('[aria-label="AI条件格式"]').click();
	const replacement = app.dialog();
	assert.ok(replacement);
	assert.notEqual(replacement, original);
	await app.finish({ ok: true });
	assert.equal(app.dialog(), replacement);
	assert.ok(app.operations());
});

test('late apply closes its own dialog and leaves a reopened parent sheet visible', async t => {
	const app = createHarness(t);
	await app.openPreview();
	app.confirm().click();
	app.operations().querySelector('.mobile-ai-sheet-close').click();
	app.panel.openOperationSheet();
	await app.finish({ ok: true });
	assert.equal(app.dialog(), null);
	assert.ok(app.operations());
});

test('pending mutation keeps its request and preview until it finishes', async t => {
	const app = createHarness(t);
	const { controller } = app;
	await app.openPreview();
	app.confirm().click();
	assert.equal(controller.request('calc_data_process', 'another request'), null);
	app.changeSelection();
	assert.equal(controller.regenerate(), null);
	app.receive({
		type: 'ai.done', requestId: 'request-1', payload: { fullText: 'late preview' },
	});
	assert.equal(controller.getState().state, 'applying');
	assert.equal(await controller.accept(), false);
	await app.finish({ ok: true });
	assert.deepEqual(app.acceptance, [{ requestId: 'request-1', text: app.preview }]);
	assert.equal(controller.request('calc_data_process', 'another request'), 'request-2');
	assert.equal(controller.getState().state, 'loading');
});

test('new table remains open after successful confirmed apply', async t => {
	const app = createHarness(t);
	await app.openPreview(null, 'calc_new_table');
	app.confirm().click();
	await app.finish({ ok: true });
	assert.equal(app.acceptance.length, 1);
	assert.ok(app.dialog());
	assert.ok(app.operations());
});
