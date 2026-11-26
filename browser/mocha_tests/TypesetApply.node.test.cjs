'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { stripTypeScriptTypes } = require('node:module');
const { JSDOM } = require('jsdom');

function harness(options = {}) {
	const dom = new JSDOM('<body/>');
	const { window } = dom;
	const inserts = [];
	const fillRequests = [];
	const localInserts = [];
	let resolveInsert;
	const typesetBridge = {
		isAvailable: () => options.available !== false,
		insertDocument: (path) => { inserts.push(path); return new Promise((resolve) => { resolveInsert = resolve; }); },
		fillTemplate: (type, sections, sourceName, images) => {
			fillRequests.push({ type, sections, sourceName, images });
			return Promise.resolve({ docxPath: '/tmp/filled-template.docx', sourceRequestId: 'typeset-fill-1' });
		},
	};
	const accepted = [];
	const requested = [];
	const state = { state: 'ready', preview: 'AI preview', requestId: 'req-1' };
	const controller = {
		getState: () => state,
		subscribe: () => () => {},
		resetPreview: () => {},
		cancel: () => {},
		acceptAsync: async (html, mode) => { accepted.push({ html, mode }); return true; },
		accept: (html) => accepted.push({ html, mode: 'sync' }),
		copy: () => {},
		request: (...args) => { requested.push(args); state.state = 'loading'; state.preview = ''; },
	};
	class Sheet {
		constructor(config) {
			this.config = config;
			this.root = window.document.createElement('div');
			this.root.innerHTML = '<button class="mobile-ai-sheet-close"></button>';
			window.document.body.appendChild(this.root);
		}
		setBody(body) { this.root.appendChild(body); }
		open() {}
		close() { this.closed = true; this.config.onClose(); }
	}
	const context = vm.createContext({
		window,
		document: window.document,
		console,
		MobileAiSheet: Sheet,
		WriterAiController: { getInstance: () => controller },
		WriterAiCatalog: { TYPESET_TYPES: [{ key: 'paper', label: '论文' }] },
		TypesetBridge: { getInstance: () => typesetBridge },
		insertIosNativeHtml: async (html, sourceRequestId, mode) => {
			localInserts.push({ html, sourceRequestId, mode });
			return true;
		},
		MobileAiResultRenderer: { sanitizeTypesetHtml: () => '<p>safe</p>', renderTypesetInto() {} },
	});
	window.app = { map: { getDocType: () => 'text' } };
	window.ThisIsTheiOSApp = true;
	const source = fs.readFileSync(`${__dirname}/../src/app/MobileAiTypesetDialog.ts`, 'utf8');
	const previewSource = fs.readFileSync(`${__dirname}/../src/app/TypesetPreviewHtml.ts`, 'utf8');
	vm.runInContext(stripTypeScriptTypes(previewSource), context);
	vm.runInContext(stripTypeScriptTypes(source) + ';window.Dialog=MobileAiTypesetDialog;', context);
	const dialog = new window.Dialog();
	dialog.previewHtml = '<html><body><p>排版</p></body></html>';
	dialog.docxPath = '/tmp/typeset.docx';
	dialog.sourceExtension = options.sourceExtension || 'docx';
	dialog.typesetImages = options.images || null;
	return { dialog, accepted, requested, state, window, inserts, fillRequests, localInserts, typesetBridge, getResolve: () => resolveInsert, close: () => dom.window.close() };
}

test('iOS typesetting waits for the current snapshot and discards opening-image state without save/timer polling', async () => {
	const h = harness({ images: { oldImage: { base64: 'old' } } });
	try {
		let resolveSnapshot;
		h.typesetBridge.extractStructured = () => new Promise(resolve => { resolveSnapshot = resolve; });
		h.window.app.map.sendUnoCommand = () => { throw new Error('must not race an asynchronous Save'); };
		h.window.setTimeout = () => { throw new Error('must not rely on a fixed save delay'); };
		h.dialog.startTypeset();
		assert.equal(h.requested.length, 0);
		assert.equal(h.dialog.typesetImages, null);
		resolveSnapshot({ fullText: '删除图片后的当前正文', paragraphs: ['删除图片后的当前正文'], images: {}, sourceExtension: 'odt' });
		await new Promise(resolve => setImmediate(resolve));
		assert.equal(h.requested.length, 1);
		assert.equal(h.requested[0][2], '删除图片后的当前正文');
		assert.equal(Object.keys(h.dialog.typesetImages).length, 0);
		assert.equal(h.dialog.sourceExtension, 'odt');
	} finally { h.close(); }
});

test('regeneration takes a new current snapshot and still retains protection for real source images', async () => {
	const h = harness();
	try {
		let snapshots = 0;
		h.typesetBridge.extractStructured = async () => ({ fullText: ++snapshots === 1 ? '[图1]正文' : '删图后的正文', paragraphs: ['正文'], images: snapshots === 1 ? { 图1: { base64: 'current' } } : {}, sourceExtension: 'odt' });
		h.dialog.startTypeset();
		await new Promise(resolve => setImmediate(resolve));
		assert.equal(Object.keys(h.dialog.typesetImages).length, 1);
		h.dialog.regenerate();
		await new Promise(resolve => setImmediate(resolve));
		assert.equal(snapshots, 2);
		assert.equal(h.requested[1][2], '删图后的正文');
		assert.equal(Object.keys(h.dialog.typesetImages).length, 0);
	} finally { h.close(); }
});

test('snapshot failure is shown and does not request AI using stale source content', async () => {
	const h = harness();
	try {
		h.state.error = '旧模型错误';
		h.typesetBridge.extractStructured = async () => { throw new Error('无法读取当前文档内容，请稍后重试'); };
		h.dialog.startTypeset();
		await new Promise(resolve => setImmediate(resolve));
		assert.equal(h.requested.length, 0);
		assert.equal(h.dialog.status.textContent, '无法读取当前文档内容，请稍后重试');
		assert.equal(h.dialog.previewPanel.style.display, 'flex');
		assert.equal(h.dialog.applyButton.disabled, true);
	} finally { h.close(); }
});

test('DOCX source uses native replacement; failure leaves dialog open with persistent error', async () => {
	const h = harness({ sourceExtension: 'docx' });
	try {
		const applying = h.dialog.applyResult();
		assert.deepEqual(h.inserts, ['/tmp/typeset.docx']);
		h.getResolve()(false);
		await applying;
		await new Promise((resolve) => setTimeout(resolve, 0));
		assert.equal(h.accepted.length, 0);
		assert.equal(h.dialog.sheet.closed, undefined);
		assert.equal(h.dialog.status.textContent, '插入文档失败');
	} finally { h.close(); }
});

test('ODT compatibility path replaces the whole document rather than catalog append', async () => {
	const h = harness({ sourceExtension: 'odt' });
	try {
		await h.dialog.applyResult();
		assert.equal(h.typesetBridge.isAvailable(), true);
		assert.equal(h.inserts.length, 0);
		assert.equal(h.accepted.length, 1);
		assert.equal(h.accepted[0].mode, 'replaceDocument');
		assert.equal(h.accepted[0].html, '<html><body><p>排版</p></body></html>');
	} finally { h.close(); }
});

test('a blank Writer document generates the selected template without an AI request', async () => {
	const h = harness({ sourceExtension: 'odt' });
	try {
		h.state.state = 'idle';
		h.state.preview = '';
		delete h.state.requestId;
		h.typesetBridge.extractStructured = async () => ({ fullText: '', paragraphs: [], sourceExtension: 'odt' });
		h.dialog.startTypeset();
		await new Promise(resolve => setImmediate(resolve));
		assert.equal(h.requested.length, 0);
		assert.equal(h.fillRequests.length, 1);
		assert.equal(h.fillRequests[0].type, 'paper');
		assert.deepEqual(Object.keys(h.fillRequests[0].sections), []);
		assert.equal(h.dialog.blankTemplate, true);
		assert.match(h.dialog.previewHtml, /论文标题|摘要正文|正文/);
		assert.equal(h.dialog.copyButton.disabled, true);
		assert.equal(h.dialog.applyButton.disabled, false);
		await h.dialog.applyResult();
		assert.deepEqual(h.localInserts.map(insert => [insert.sourceRequestId, insert.mode]), [['typeset-fill-1', 'replaceDocument']]);
		assert.equal(h.accepted.length, 0);
		assert.equal(h.dialog.sheet.closed, true);
	} finally { h.close(); }
});

test('ODT with source images refuses replacement and keeps the warning visible', async () => {
	const h = harness({ sourceExtension: 'odt', images: { image1: { base64: 'abc' } } });
	try {
		await h.dialog.applyResult();
		assert.equal(h.accepted.length, 0);
		assert.equal(h.dialog.status.textContent, '原文包含图片，兼容写入无法安全保留图片；文档未修改。请另存为 DOCX 后再排版');
		assert.equal(h.dialog.applyButton.disabled, true);
	} finally { h.close(); }
});

test('WriterAiController forwards replaceDocument override and accepts only after native acknowledgement', async () => {
	const dom = new JSDOM('<body/>');
	const { window } = dom;
	window.ThisIsTheiOSApp = true;
	window.app = { map: { getDocType: () => 'text' } };
	let listener;
	const accepted = [];
	const bridge = {
		request: () => 'req-1', cancel: () => true, accept: (...args) => (accepted.push(args), true),
		getSelectedText: () => '原文', isAvailable: () => true,
		subscribe: (callback) => (listener = callback, () => {}),
	};
	const inserted = [];
	const source = fs.readFileSync(`${__dirname}/../src/app/WriterAiController.ts`, 'utf8');
	const context = vm.createContext({
		window,
		console,
		MobileAiBridge: { getInstance: () => bridge },
		WriterAiCatalog: {
			validateRequest: () => ({ valid: true }),
			getTask: () => ({ resultMode: 'insertAtEnd' }),
		},
	});
	vm.runInContext(stripTypeScriptTypes(source) + ';window.Controller=WriterAiController;', context);
	try {
		const controller = new window.Controller(bridge, {
			pastePlainText: () => true,
			insertWriterHtml: async (html, requestId, mode) => (inserted.push({ html, requestId, mode }), true),
		});
		controller.request('continue');
		listener({ type: 'ai.done', requestId: 'req-1', payload: { fullText: '排版全文' } });
		assert.equal(await controller.acceptAsync('<p>排版全文</p>', 'replaceDocument'), true);
		assert.deepEqual(inserted, [{ html: '<p>排版全文</p>', requestId: 'req-1', mode: 'replaceDocument' }]);
		assert.equal(accepted.length, 1);
	} finally { dom.window.close(); }
});
