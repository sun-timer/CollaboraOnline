/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;
var { JSDOM } = require('jsdom');

describe('CalcEditorHyperlinkDialog', function () {
	let dom: import('jsdom').JSDOM;
	let previousWindow: any;
	let previousDocument: any;
	let previousMutationObserver: any;
	let map: any;
	let context: CalcEditorDialogContext;
	let snapshot: CalcHyperlinkSelectionSnapshot;
	let calls: Array<{ text: string; url: string }>;
	let closeCount: number;
	let onDismissed: (() => void) | null;
	let mapFocusCount: number;
	let sentMessages: string[];
	let unoCommands: string[];

	function createDialog(
		result: any = { dispatched: 'unocmd', command: '.uno:SetHyperlink' },
		throws = false,
		controllerOverride?: WriterEditorController,
	): CalcEditorHyperlinkDialog {
		calls = [];
		closeCount = 0;
		onDismissed = null;
		mapFocusCount = 0;
		sentMessages = [];
		unoCommands = [];
		const controller: any = controllerOverride || {
			getSelectedText: () => 'ignored-after-focus',
			insertHyperlink: (text: string, url: string) => {
				calls.push({ text, url });
				if (throws) throw new Error('send failed');
				return result;
			},
		};
		const subpages: HTMLElement[] = [];
		const host: WriterEditorInlineSubpageHost = {
			pushSubpage: (_title: string, body: HTMLElement) => {
				subpages.push(body);
				document.body.appendChild(body);
			},
			popSubpage: (callback?: () => void) => {
				closeCount++;
				subpages.pop()?.remove();
				onDismissed = callback || null;
			},
			clearSubpages: () => document.body.replaceChildren(),
		};
		let dialog: CalcEditorHyperlinkDialog;
		dialog = new CalcEditorHyperlinkDialog(controller, host, () => { closeCount += 100; }, {
			closePanel: (done) => {
				// CalcEditorPanel detaches this pending dialog from its child list,
				// then calls this callback after the panel itself has detached.
				document.body.replaceChildren();
				onDismissed = done;
			},
			restorePanel: () => dialog.restorePreservingDraft(),
		});
		return dialog;
	}

	function clickAdd(): void {
		const add = Array.from(document.querySelectorAll('button.writer-function-calc-hyperlink-form__submit'))
			.find((button) => button.textContent === '添加');
		assert.ok(add);
		(add as HTMLButtonElement).click();
	}

	beforeEach(function () {
		previousWindow = (global as any).window;
		previousDocument = (global as any).document;
		previousMutationObserver = (global as any).MutationObserver;
		dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://localhost/' });
		(global as any).window = dom.window;
		(global as any).document = dom.window.document;
		(global as any).MutationObserver = dom.window.MutationObserver;
		map = {
			getDocType: () => 'spreadsheet',
			isEditMode: () => true,
			sendUnoCommand: (command: string) => unoCommands.push(command),
			focus: (acceptInput: boolean) => { assert.equal(acceptInput, true); mapFocusCount++; },
			_docLayer: { _viewId: 7, _selectedPart: 2, _partNames: ["Quarter '24"] },
			uiManager: { isUIBlocked: () => false },
			dialog: { hasOpenedDialog: () => false },
			jsdialog: { hasDialogOpened: () => false },
		};
		(dom.window as any).ThisIsTheiOSApp = true;
		(dom.window as any).app = {
			map,
		socket: { sendMessage: (message: string) => sentMessages.push(message) },
			calc: {
				cellAddress: { toArray: () => [1, 4] },
				cellCursorVisible: true,
				cellCursorRectangle: { toArray: () => [10, 20, 30, 40] },
			},
		};
		context = calcEditorDialogContext() as CalcEditorDialogContext;
		snapshot = CalcEditorHyperlinkDialog.captureSelection(context) as CalcHyperlinkSelectionSnapshot;
	});

	afterEach(function () {
		dom.window.close();
		if (previousWindow === undefined) delete (global as any).window;
		else (global as any).window = previousWindow;
		if (previousDocument === undefined) delete (global as any).document;
		else (global as any).document = previousDocument;
		if (previousMutationObserver === undefined) delete (global as any).MutationObserver;
		else (global as any).MutationObserver = previousMutationObserver;
	});

	it('encodes URL spaces and Unicode while preserving a supported URL scheme', function () {
		const builder = (CalcEditorHyperlinkDialog as any).buildWebUrl;
		assert.equal(builder('https://example.com/中文 路径?q=你好 世界'), 'https://example.com/%E4%B8%AD%E6%96%87%20%E8%B7%AF%E5%BE%84?q=%E4%BD%A0%E5%A5%BD%20%E4%B8%96%E7%95%8C');
		assert.equal(builder('example.com/a b'), 'http://example.com/a%20b');
	});

	it('RFC-encodes mail subject and body and normalizes body line endings', function () {
		const builder = (CalcEditorHyperlinkDialog as any).buildMailUrl;
		assert.equal(
			builder('name+tag@example.com', '你好 & team', 'first line\nsecond line'),
			'mailto:name%2Btag@example.com?subject=%E4%BD%A0%E5%A5%BD%20%26%20team&body=first%20line%0D%0Asecond%20line',
		);
	});

	it('quotes sheet names and doubles embedded apostrophes for internal links', function () {
		const builder = (CalcEditorHyperlinkDialog as any).buildDocumentSheetUrl;
		assert.equal(builder("Quarter '24"), "#'Quarter ''24'.A1");
		assert.equal(builder('工作表 一'), "#'工作表 一'.A1");
	});

	it('does not capture a selection without a validated document context', function () {
		assert.equal(CalcEditorHyperlinkDialog.captureSelection(null), null);
	});

	it('selects and dispatches an existing sheet-scoped name with quoted sheet characters', function () {
		const dialog = createDialog();
		dialog.open(context, 'selected', snapshot);
		(document.querySelectorAll('button.writer-function-calc-hyperlink-form__segment')[2] as HTMLButtonElement).click();
		(document.querySelector('[aria-label="打开文档中的目标"]') as HTMLButtonElement).click();
		assert.ok(sentMessages[0].includes('.uno:InsertName'));
		assert.equal(CalcEditorHyperlinkDialog.handleDocumentTargetMessage({
			id: 41,
			dialogid: 'InsertNameDialog',
			children: [{ id: 'ctrl', entries: [{ columns: [{ text: 'Revenue Plan' }, { text: 'A1:B4' }, { text: "Quarter '24" }] }] }],
		}), true);
		assert.ok(sentMessages.some((message) => message.includes('.uno:DefineDBName')));
		assert.equal(CalcEditorHyperlinkDialog.handleDocumentTargetMessage({
			id: 42,
			dialogid: 'DefineDatabaseRangeDialog',
			children: [{ id: 'entry-list', entries: [{ columns: [{ text: 'SalesData' }] }] }],
		}), true);

		const nameGroup = Array.from(document.querySelectorAll('button.writer-function-calc-hyperlink-form__tree-row'))
			.find((button) => button.textContent?.includes('范围名称')) as HTMLButtonElement;
		assert.ok(nameGroup);
		nameGroup.click();
		const namedTarget = Array.from(document.querySelectorAll('button.writer-function-calc-hyperlink-form__tree-row--child'))
			.find((button) => button.textContent === "'Quarter ''24'.Revenue Plan") as HTMLButtonElement;
		assert.ok(namedTarget);
		namedTarget.click();
		(Array.from(document.querySelectorAll('button.writer-function-calc-hyperlink-form__submit'))
			.find((button) => button.textContent === '应用') as HTMLButtonElement).click();
		clickAdd();
		onDismissed?.();
		assert.deepEqual(calls, [{ text: 'selected', url: "#'Quarter ''24'.Revenue Plan" }]);
	});

	it('dispatches the captured text and keeps the dialog open when dispatch is unavailable', function () {
		const dialog = createDialog({ dispatched: 'none', reason: 'not sent' });
		dialog.open(context, 'captured text', snapshot);
		const inputs = document.querySelectorAll('input');
		inputs[1].value = 'example.com/a b';
		clickAdd();
		assert.equal(calls.length, 0, 'command waits for subpage dismissal');
		assert.equal(mapFocusCount, 0, 'document focus waits for subpage dismissal');
		onDismissed?.();
		assert.deepEqual(calls, [{ text: 'captured text', url: 'http://example.com/a%20b' }]);
		assert.equal(closeCount, 1, 'the failed dispatch reopens the draft after dismissal');
		assert.match(document.querySelector('[role="status"]')?.textContent || '', /无法发送/);
	});

	it('sends the typed SetHyperlink payload through WriterEditorController after focus restoration', function () {
		const dialog = createDialog(undefined, false, new WriterEditorController());
		dialog.open(context, '中文 链接', snapshot);
		(document.querySelectorAll('input')[1] as HTMLInputElement).value = 'https://example.com/工作 表';
		clickAdd();
		assert.equal(unoCommands.length, 0);
		onDismissed?.();
		assert.equal(mapFocusCount, 1);
		assert.equal(unoCommands.length, 1);
		assert.match(unoCommands[0], /^\.uno:SetHyperlink /);
		assert.match(unoCommands[0], /"Hyperlink\.Text":\{"type":"string","value":"中文 链接"\}/);
		assert.match(unoCommands[0], /"Hyperlink\.URL":\{"type":"string","value":"https:\/\/example\.com\/%E5%B7%A5%E4%BD%9C%20%E8%A1%A8"\}/);
		assert.match(unoCommands[0], /"Hyperlink\.ReplacementText":\{"type":"string","value":"中文 链接"\}/);
	});

	it('shows a useful error for missing required fields and dispatches encoded email details', function () {
		const dialog = createDialog();
		dialog.open(context, '', snapshot);
		clickAdd();
		assert.equal(calls.length, 0);
		assert.match(document.querySelector('[role="status"]')?.textContent || '', /请输入链接/);

		(document.querySelectorAll('button.writer-function-calc-hyperlink-form__segment')[1] as HTMLButtonElement).click();
		clickAdd();
		assert.match(document.querySelector('[role="status"]')?.textContent || '', /请输入收件人/);
		const fields = document.querySelectorAll('input');
		fields[2].value = 'name+tag@example.com';
		fields[3].value = '你好 & team';
		(document.querySelector('textarea') as HTMLTextAreaElement).value = 'first line\nsecond line';
		clickAdd();
		assert.equal(calls.length, 0);
		onDismissed?.();
		assert.deepEqual(calls, [{
			text: 'name+tag@example.com',
			url: 'mailto:name%2Btag@example.com?subject=%E4%BD%A0%E5%A5%BD%20%26%20team&body=first%20line%0D%0Asecond%20line',
		}]);
		assert.equal(closeCount, 101);
	});

	it('restores the retained form after a failed send and does not send after the target moves while closing', function () {
		const dialog = createDialog({ dispatched: 'none', reason: 'not sent' });
		dialog.open(context, 'selected', snapshot);
		(document.querySelectorAll('input')[1] as HTMLInputElement).value = 'https://example.com/a b';
		clickAdd();
		onDismissed?.();
		assert.equal(calls.length, 1);
		assert.ok(document.body.textContent?.includes('超链接'));
		assert.equal((document.querySelectorAll('input')[0] as HTMLInputElement).value, 'selected');
		assert.equal((document.querySelectorAll('input')[1] as HTMLInputElement).value, 'https://example.com/a b');
		assert.equal(mapFocusCount, 1);

		clickAdd();
		(dom.window as any).app.calc.cellAddress = { toArray: () => [9, 9] };
		onDismissed?.();
		assert.equal(calls.length, 1, 'a stale cell is never sent');
		assert.ok(document.querySelector('[role="status"]')?.textContent?.includes('文档或单元格已更改'));
	});

	it('cancels a pending command when the dialog is explicitly closed or reopened', function () {
		const dialog = createDialog();
		dialog.open(context, 'selected', snapshot);
		(document.querySelectorAll('input')[1] as HTMLInputElement).value = 'https://example.com';
		clickAdd();
		const lateDismiss = onDismissed;
		dialog.close();
		lateDismiss?.();
		assert.equal(calls.length, 0);
		assert.equal(mapFocusCount, 0);

		dialog.open(context, 'new selection', snapshot);
		lateDismiss?.();
		assert.equal(calls.length, 0, 'a previous close callback cannot target a reopened dialog');
	});

	it('rejects a moved cell and blocked UI before sending the UNO command', function () {
		const dialog = createDialog();
		dialog.open(context, '', snapshot);
		(document.querySelectorAll('input')[1] as HTMLInputElement).value = 'example.com';
		(dom.window as any).app.calc.cellAddress = { toArray: () => [1, 5] };
		clickAdd();
		assert.equal(calls.length, 0);
		assert.equal(closeCount, 0);
		assert.match(document.querySelector('[role="status"]')?.textContent || '', /文档或单元格已更改/);

		(dom.window as any).app.calc.cellAddress = { toArray: () => [1, 4] };
		map.uiManager.isUIBlocked = () => true;
		clickAdd();
		assert.equal(calls.length, 0);
		assert.match(document.querySelector('[role="status"]')?.textContent || '', /关闭其他对话框/);
	});

	it('rejects a changed view on the same document layer after panel dismissal', function () {
		const dialog = createDialog();
		dialog.open(context, '', snapshot);
		(document.querySelectorAll('input')[1] as HTMLInputElement).value = 'example.com';
		clickAdd();
		map._docLayer._viewId++;
		onDismissed?.();
		assert.equal(calls.length, 0);
		assert.equal(mapFocusCount, 0);
		assert.match(document.querySelector('[role="status"]')?.textContent || '', /文档或单元格已更改/);
	});

	it('handles Enter as submit, but leaves multiline mail body on plain Enter', function () {
		const dialog = createDialog();
		dialog.open(context, '', snapshot);
		(document.querySelectorAll('input')[1] as HTMLInputElement).value = 'example.com';
		(document.querySelectorAll('input')[1] as HTMLInputElement).dispatchEvent(
			new dom.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }),
		);
		assert.equal(calls.length, 0, 'Enter also waits for the full panel dismissal');
		onDismissed?.();
		assert.equal(calls.length, 1);
		assert.equal(closeCount, 101);
	});
});
