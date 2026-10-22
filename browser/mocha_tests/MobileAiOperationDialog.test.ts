/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

function createMobileAiDialogHarness(options: any = {}) {
	const previousGetController = WriterAiController.getInstance;
	const previousGetBridge = MobileAiBridge.getInstance;
	const previousExtract = MobileAiDocumentExtractor.extractFullText;
	const previousIOSFlag = (window as any).ThisIsTheiOSApp;
	const previousNativeType = (window as any).MobileNativeDocumentType;
	const previousApp = (window as any).app;
	const listeners: Array<() => void> = [];
	const requests: any[] = [];
	let state: any = { state: 'idle', preview: '' };
	const notify = () => listeners.slice().forEach((listener) => listener());
	const controller = {
		resetPreview() {
			state = { state: 'idle', preview: '' };
			notify();
			return true;
		},
		getState: () => state,
		subscribe(listener: () => void) {
			listeners.push(listener);
			return () => listeners.splice(listeners.indexOf(listener), 1);
		},
		request(...args: any[]) {
			requests.push(args);
			state = { state: 'loading', taskType: args[0], preview: '' };
			notify();
			return 'request-1';
		},
		requestWithCurrentSelection(...args: any[]) {
			requests.push(args);
			return 'request-1';
		},
		cancel() {
			state = { ...state, state: 'cancelled' };
			return true;
		},
		copy: async () => options.copyResult === true,
		acceptAsync: async () => options.acceptResult === true,
		accept: () => true,
		regenerate: () => 'request-2',
	};
	(window as any).ThisIsTheiOSApp = true;
	(window as any).MobileNativeDocumentType = 'text';
	(window as any).app = { map: { getDocType: () => 'text' } };
	WriterAiController.getInstance = () => controller as any;
	MobileAiBridge.getInstance = () => ({
		getSelectedText: () => options.selection || '',
		getSelectedTextAsync: () => options.selectionPromise || Promise.resolve(options.selection || ''),
	} as any);
	MobileAiDocumentExtractor.extractFullText = () =>
		options.extractPromise || Promise.resolve('完整文档');
	return {
		controller,
		requests,
		setState(next: any) {
			state = next;
			notify();
		},
		cleanup() {
			WriterAiController.getInstance = previousGetController;
			MobileAiBridge.getInstance = previousGetBridge;
			MobileAiDocumentExtractor.extractFullText = previousExtract;
			if (previousIOSFlag === undefined) delete (window as any).ThisIsTheiOSApp;
			else (window as any).ThisIsTheiOSApp = previousIOSFlag;
			if (previousNativeType === undefined) delete (window as any).MobileNativeDocumentType;
			else (window as any).MobileNativeDocumentType = previousNativeType;
			(window as any).app = previousApp;
			document.querySelectorAll('[data-cool-mobile-ai-ui="1"]').forEach((el) => el.remove());
			document.querySelectorAll('.mobile-ai-wai-dropdown-root').forEach((el) => el.remove());
		},
	};
}

function flushMobileAiDialogAsync(): Promise<void> {
	return new Promise((resolve) => window.setTimeout(resolve, 0));
}

describe('Mobile AI operation rendering', function () {
	it('auto-starts expand from the open-time snapshot and omits the requirement', async function () {
		let resolveSelection!: (selection: string) => void;
		const harness = createMobileAiDialogHarness({
			selectionPromise: new Promise<string>((resolve) => (resolveSelection = resolve)),
		});
		try {
			const dialog = new MobileAiOperationDialog('expand');
			dialog.open();
			resolveSelection('初始选区');
			await flushMobileAiDialogAsync();
			assert.equal(harness.requests.length, 1);
			assert.equal(harness.requests[0][0], 'expand');
			assert.equal(harness.requests[0][2], '初始选区');
			assert.equal(Object.prototype.hasOwnProperty.call(harness.requests[0][1], 'requirement'), false);
			assert.equal(document.querySelector('textarea[aria-label="文案要求"]'), null);
			dialog.close();
		} finally {
			harness.cleanup();
		}
	});

	it('keeps polish manual while using the captured selection', async function () {
		const harness = createMobileAiDialogHarness({ selection: '打开时的选区' });
		try {
			const dialog = new MobileAiOperationDialog('polish');
			dialog.open();
			await flushMobileAiDialogAsync();
			assert.equal(harness.requests.length, 0);
			(document.querySelector('.mobile-ai-task-dialog__generate') as HTMLButtonElement).click();
			assert.equal(harness.requests.length, 1);
			assert.equal(harness.requests[0][2], '打开时的选区');
			dialog.close();
		} finally {
			harness.cleanup();
		}
	});

	it('uses the outline menu and sends the extracted full document', async function () {
		const harness = createMobileAiDialogHarness({
			extractPromise: Promise.resolve('未保存的完整正文'),
		});
		try {
			const dialog = new MobileAiOperationDialog('outline');
			dialog.open();
			const picker = document.querySelector('.mobile-ai-task-dialog__picker-card') as HTMLButtonElement;
			picker.click();
			assert.equal(picker.getAttribute('aria-expanded'), 'true');
			assert.equal(picker.querySelector('.mobile-ai-task-dialog__picker-card-chevron')?.textContent, '▴');
			picker.click();
			assert.equal(picker.getAttribute('aria-expanded'), 'false');
			picker.click();
			const meeting = Array.from(document.querySelectorAll('.mobile-ai-wai-dropdown-row')).find(
				(row) => row.textContent === '会议纪要',
			) as HTMLButtonElement;
			assert.ok(meeting);
			meeting.click();
			assert.equal(picker.getAttribute('aria-expanded'), 'false');
			assert.equal(picker.querySelector('.mobile-ai-task-dialog__picker-card-chevron')?.textContent, '▾');
			(document.querySelector('.mobile-ai-task-dialog__generate') as HTMLButtonElement).click();
			await flushMobileAiDialogAsync();
			assert.equal(harness.requests.length, 1);
			assert.equal(harness.requests[0][1].outlineType, 'meeting');
			assert.equal(harness.requests[0][2], '未保存的完整正文');
			dialog.close();
		} finally {
			harness.cleanup();
		}
	});

	it('passes an empty extracted document through and surfaces extraction failures', async function () {
		const emptyHarness = createMobileAiDialogHarness({ extractPromise: Promise.resolve('') });
		try {
			const dialog = new MobileAiOperationDialog('outline');
			dialog.open();
			(document.querySelector('.mobile-ai-task-dialog__generate') as HTMLButtonElement).click();
			await flushMobileAiDialogAsync();
			assert.equal(emptyHarness.requests.length, 1);
			assert.equal(emptyHarness.requests[0][2], '');
			dialog.close();
		} finally {
			emptyHarness.cleanup();
		}

		const failureHarness = createMobileAiDialogHarness({
			extractPromise: Promise.reject(new Error('文档全文提取失败')),
		});
		try {
			const dialog = new MobileAiOperationDialog('outline');
			dialog.open();
			(document.querySelector('.mobile-ai-task-dialog__generate') as HTMLButtonElement).click();
			await flushMobileAiDialogAsync();
			assert.equal(failureHarness.requests.length, 0);
			assert.equal(document.querySelector('.mobile-ai-task-dialog__status')?.textContent, '文档全文提取失败');
			dialog.close();
		} finally {
			failureHarness.cleanup();
		}
	});

	it('closes after successful polish copy and only after a successful apply', async function () {
		const failedHarness = createMobileAiDialogHarness({ selection: '选区', copyResult: false, acceptResult: false });
		try {
			const dialog = new MobileAiOperationDialog('polish');
			dialog.open();
			await flushMobileAiDialogAsync();
			failedHarness.setState({ state: 'ready', taskType: 'polish', preview: '结果' });
			(document.querySelector('.mobile-ai-task-dialog__copy-row') as HTMLButtonElement).click();
			await flushMobileAiDialogAsync();
			assert.ok(document.querySelector('[data-cool-mobile-ai-ui="1"]'));
			const apply = Array.from(document.querySelectorAll('.mobile-ai-task-dialog__result-bar button'))[1] as HTMLButtonElement;
			apply.click();
			await flushMobileAiDialogAsync();
			assert.ok(document.querySelector('[data-cool-mobile-ai-ui="1"]'));
			dialog.close();
		} finally {
			failedHarness.cleanup();
		}

		const successHarness = createMobileAiDialogHarness({ selection: '选区', copyResult: true });
		try {
			const dialog = new MobileAiOperationDialog('polish');
			dialog.open();
			await flushMobileAiDialogAsync();
			successHarness.setState({ state: 'ready', taskType: 'polish', preview: '结果' });
			(document.querySelector('.mobile-ai-task-dialog__copy-row') as HTMLButtonElement).click();
			await flushMobileAiDialogAsync();
			assert.equal(document.querySelector('[data-cool-mobile-ai-ui="1"]'), null);
		} finally {
			successHarness.cleanup();
		}

		const applyHarness = createMobileAiDialogHarness({ selection: '选区', acceptResult: true });
		try {
			const dialog = new MobileAiOperationDialog('expand');
			dialog.open();
			await flushMobileAiDialogAsync();
			applyHarness.setState({ state: 'ready', taskType: 'expand', preview: '结果' });
			const apply = Array.from(document.querySelectorAll('.mobile-ai-task-dialog__result-bar button'))[1] as HTMLButtonElement;
			apply.click();
			await flushMobileAiDialogAsync();
			assert.equal(document.querySelector('[data-cool-mobile-ai-ui="1"]'), null);
		} finally {
			applyHarness.cleanup();
		}
	});

	it('does not issue a request when selection resolves after the dialog closes', async function () {
		let resolveSelection!: (selection: string) => void;
		const harness = createMobileAiDialogHarness({
			selectionPromise: new Promise<string>((resolve) => (resolveSelection = resolve)),
		});
		try {
			const dialog = new MobileAiOperationDialog('rewrite');
			dialog.open();
			dialog.close();
			resolveSelection('選區');
			await flushMobileAiDialogAsync();
			assert.equal(harness.requests.length, 0);
		} finally {
			harness.cleanup();
		}
	});

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

describe('Mobile AI article dialog lifecycle', function () {
	it('clears a prior error on open without issuing an article request', function () {
		const harness = createMobileAiDialogHarness();
		try {
			harness.setState({ state: 'error', error: 'AI request failed', preview: '' });
			const dialog = new MobileAiArticleGenerateDialog();
			dialog.open();
			assert.equal(harness.requests.length, 0);
			assert.equal(document.querySelector('.mobile-ai-task-dialog__status')?.textContent, '');
			dialog.close();
		} finally {
			harness.cleanup();
		}
	});

	it('dismisses only after async article insertion succeeds', async function () {
		const exerciseApply = async (acceptResult: boolean): Promise<boolean> => {
			const harness = createMobileAiDialogHarness({ acceptResult });
			try {
				const dialog = new MobileAiArticleGenerateDialog();
				dialog.open();
				const cards = document.querySelectorAll('.mobile-ai-article-dialog__setup .mobile-ai-task-dialog__select-card');
				(cards[0] as HTMLButtonElement).click();
				(document.querySelector('.mobile-ai-wai-dropdown-row') as HTMLButtonElement).click();
				(document.querySelectorAll('.mobile-ai-article-dialog__setup .mobile-ai-task-dialog__select-card')[1] as HTMLButtonElement).click();
				(document.querySelector('.mobile-ai-wai-dropdown-row') as HTMLButtonElement).click();
				(document.querySelector('.mobile-ai-article-dialog__cta-bar button') as HTMLButtonElement).click();
				assert.equal(harness.requests.length, 1);
				harness.setState({ state: 'ready', taskType: 'article_generate', preview: '文案结果' });
				const insert = Array.from(document.querySelectorAll('.mobile-ai-task-dialog__result-bar button'))[1] as HTMLButtonElement;
				insert.click();
				await flushMobileAiDialogAsync();
				return document.querySelector('[data-cool-mobile-ai-ui="1"]') !== null;
			} finally {
				harness.cleanup();
			}
		};

		assert.equal(await exerciseApply(false), true);
		assert.equal(await exerciseApply(true), false);
	});

	it('unsubscribes on the sheet close button so later updates leave its DOM untouched', function () {
		const harness = createMobileAiDialogHarness();
		try {
			const dialog = new MobileAiArticleGenerateDialog();
			dialog.open();
			const root = document.querySelector('[data-cool-mobile-ai-ui="1"]') as HTMLElement;
			(document.querySelector('.mobile-ai-sheet-close') as HTMLButtonElement).click();
			assert.equal(root.parentElement, null);
			const closedMarkup = root.innerHTML;
			harness.setState({ state: 'error', error: 'late update', preview: '' });
			assert.equal(root.innerHTML, closedMarkup);
		} finally {
			harness.cleanup();
		}
	});
});
