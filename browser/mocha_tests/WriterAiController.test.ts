/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterAiController', function () {
	interface FakeWriterAiBridge extends WriterAiBridgeLike {
		calls: {
			request: Array<{ [key: string]: any }>;
			cancel: string[];
			accept: Array<{ requestId: string; text: string }>;
		};
		emit(message: NativeBridgeEnvelope): void;
	}

	function createFakeBridge(selection: string): FakeWriterAiBridge {
		const listeners: Array<(message: NativeBridgeEnvelope) => void> = [];
		let nextRequestId = 1;
		const calls: FakeWriterAiBridge['calls'] = {
			request: [],
			cancel: [],
			accept: [],
		};
		return {
			calls,
			request(payload: { [key: string]: any }): string {
				calls.request.push(payload);
				const requestId = 'request-' + nextRequestId;
				nextRequestId += 1;
				return requestId;
			},
			cancel(requestId: string): boolean {
				calls.cancel.push(requestId);
				return true;
			},
			accept(requestId: string, text: string): boolean {
				calls.accept.push({ requestId, text });
				return true;
			},
			getSelectedText(): string {
				return selection;
			},
			isAvailable(): boolean {
				return true;
			},
			subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void {
				listeners.push(listener);
				return function () {
					const index = listeners.indexOf(listener);
					if (index >= 0) listeners.splice(index, 1);
				};
			},
			emit(message: NativeBridgeEnvelope): void {
				listeners.slice().forEach(function (listener) {
					listener(message);
				});
			},
		};
	}

	function aiMessage(type: string, payload: { [key: string]: any }): NativeBridgeEnvelope {
		return {
			protocolVersion: 1,
			channel: 'native',
			type,
			requestId: 'request-1',
			payload,
		};
	}

	it('streams a preview without modifying the document and accepts it once', function () {
		const bridge = createFakeBridge('原始文本');
		const inserted: string[] = [];
		const controller = new WriterAiController(bridge, {
			pastePlainText(text) {
				inserted.push(text);
				return true;
			},
		});

		assert.equal(controller.request('polish', { polishStyle: 'formal' }), 'request-1');
		assert.equal(bridge.calls.request[0].selection, '原始文本');
		bridge.emit(aiMessage('ai.stream', { delta: '润色' }));
		bridge.emit(aiMessage('ai.done', { fullText: '润色后的文本' }));

		assert.equal(controller.getState().state, 'ready');
		assert.equal(controller.getState().preview, '润色后的文本');
		assert.equal(inserted.length, 0);
		assert.equal(controller.accept(), true);
		assert.deepEqual(inserted, ['润色后的文本']);
		assert.deepEqual(bridge.calls.accept, [
			{ requestId: 'request-1', text: '润色后的文本' },
		]);
		assert.equal(controller.getState().state, 'accepted');
	});

	it('notifies the iOS toolbar after accepting an AI insertion', function () {
		const previousWindow = (global as any).window;
		const nativeMessages: string[] = [];
		(global as any).window = { ThisIsTheiOSApp: true };
		try {
			const bridge = createFakeBridge('原始文本');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
				postMobileMessage(message: string) {
					nativeMessages.push(message);
				},
			});
			controller.request('polish');
			bridge.emit(aiMessage('ai.done', { fullText: '润色后的文本' }));

			assert.equal(controller.accept(), true);
			assert.deepEqual(nativeMessages, ['NATIVE_UNDO_RECORD reason=ai_insert']);
		} finally {
			(global as any).window = previousWindow;
		}
	});

	it('appends continuation after the original selection', function () {
		const bridge = createFakeBridge('原始段落');
		const inserted: string[] = [];
		const controller = new WriterAiController(bridge, {
			pastePlainText(text) {
				inserted.push(text);
				return true;
			},
		});
		controller.request('continue');
		bridge.emit(aiMessage('ai.done', { fullText: '续写内容' }));

		assert.equal(controller.accept(), true);
		assert.deepEqual(inserted, ['续写内容']);
	});

	it('uses presentation replacement semantics for all five processing tasks', function () {
		const previousWindow = (global as any).window;
		const pastedHtml: Array<{ html: string; plainText: string }> = [];
		const pastedPlainText: string[] = [];
		(global as any).window = {
			app: {
				map: {
					getDocType: () => 'presentation',
					_clip: {
						pasteAiTextAsHtml(html: string, plainText: string) {
							pastedHtml.push({ html, plainText });
							return true;
						},
						pastePlainText(text: string) {
							pastedPlainText.push(text);
							return true;
						},
					},
				},
			},
		};
		try {
			['polish', 'expand', 'condense', 'rewrite', 'translate'].forEach((taskType) => {
				const bridge = createFakeBridge('幻灯片选区');
				const controller = new WriterAiController(bridge);
				controller.request(taskType);
				assert.equal(bridge.calls.request[0].selection, '幻灯片选区');
				bridge.emit(aiMessage('ai.done', { fullText: taskType + '结果' }));
				assert.equal(controller.accept('<p>' + taskType + '结果</p>'), true);
				assert.deepEqual(pastedHtml, [
					{ html: '<p>' + taskType + '结果</p>', plainText: taskType + '结果' },
				]);
				assert.deepEqual(pastedPlainText, []);
				pastedHtml.length = 0;
			});
		} finally {
			(global as any).window = previousWindow;
		}
	});

	it('appends PPT continuation while preserving the selected text', function () {
		const previousWindow = (global as any).window;
		const pasted: string[] = [];
		(global as any).window = {
			app: {
				map: {
					getDocType: () => 'presentation',
					_clip: {
						pastePlainText(text: string) {
							pasted.push(text);
							return true;
						},
					},
				},
			},
		};
		try {
			const bridge = createFakeBridge('原始幻灯片文本');
			const controller = new WriterAiController(bridge);
			controller.request('continue');
			bridge.emit(aiMessage('ai.done', { fullText: '续写内容' }));
			assert.equal(controller.accept(), true);
			assert.deepEqual(pasted, ['原始幻灯片文本续写内容']);
		} finally {
			(global as any).window = previousWindow;
		}
	});

	it('keeps Writer continuation fallback unchanged', function () {
		const previousWindow = (global as any).window;
		(global as any).window = {
			app: { map: { getDocType: () => 'text' } },
		};
		try {
			const bridge = createFakeBridge('原始段落');
			const inserted: string[] = [];
			const controller = new WriterAiController(bridge, {
				pastePlainText(text: string) {
					inserted.push(text);
					return true;
				},
			});
			controller.request('continue');
			bridge.emit(aiMessage('ai.done', { fullText: '续写内容' }));
			assert.equal(controller.accept(), true);
			assert.deepEqual(inserted, ['续写内容']);
		} finally {
			(global as any).window = previousWindow;
		}
	});

	it('regenerates with the same task and context and ignores the old request', function () {
		const bridge = createFakeBridge('原始文本');
		const controller = new WriterAiController(bridge, {
			pastePlainText() {
				return true;
			},
		});

		controller.request('polish', { polishStyle: 'formal' });
		bridge.emit(aiMessage('ai.done', { fullText: '第一版' }));
		assert.equal(controller.regenerate(), 'request-2');
		assert.deepEqual(bridge.calls.request, [
			{
				taskType: 'polish',
				selection: '原始文本',
				context: { polishStyle: 'formal' },
			},
			{
				taskType: 'polish',
				selection: '原始文本',
				context: { polishStyle: 'formal' },
			},
		]);
	});

	it('rejects an empty selection and ignores another request session', function () {
		const emptyBridge = createFakeBridge(' ');
		const emptyController = new WriterAiController(emptyBridge, {
			pastePlainText() {
				return true;
			},
		});
		assert.equal(emptyController.request('polish'), null);
		assert.equal(emptyController.getState().state, 'error');

		const bridge = createFakeBridge('文本');
		const controller = new WriterAiController(bridge, {
			pastePlainText() {
				return true;
			},
		});
		controller.request('polish');
		bridge.emit({
			...aiMessage('ai.done', { fullText: '不应显示' }),
			requestId: 'other-request',
		});
		assert.equal(controller.getState().state, 'loading');
	});

	it('inserts insertAtEnd results at the document end', function () {
		const bridge = createFakeBridge('原始文本');
		const insertedEnd: string[] = [];
		const controller = new WriterAiController(bridge, {
			insertAtEnd(text: string): boolean {
				insertedEnd.push(text);
				return true;
			},
			pastePlainText(): boolean {
				return false;
			},
			copyText(): boolean {
				return false;
			},
		});

		assert.equal(controller.request('outline', {}), 'request-1');
		bridge.emit(aiMessage('ai.stream', { delta: '1. 引言' }));
		bridge.emit(aiMessage('ai.done', { fullText: '1. 引言\n2. 方法' }));

		assert.equal(controller.getState().state, 'ready');
		assert.equal(controller.accept(), true);
		assert.deepEqual(insertedEnd, ['1. 引言\n2. 方法']);
		assert.equal(controller.getState().state, 'accepted');
	});

	it('passes outline type and requirement through to the request payload', function () {
		const bridge = createFakeBridge('');
		const controller = new WriterAiController(bridge, {
			insertAtEnd(): boolean {
				return true;
			},
			pastePlainText(): boolean {
				return false;
			},
			copyText(): boolean {
				return false;
			},
		});

		controller.request('outline', { outlineType: 'paper', requirement: '含方法部分' });
		assert.deepEqual(bridge.calls.request[0], {
			taskType: 'outline',
			selection: '',
			context: { outlineType: 'paper', requirement: '含方法部分' },
		});
	});

	it('passes article template and variable values to the request payload', function () {
		const bridge = createFakeBridge('');
		const controller = new WriterAiController(bridge, {
			insertAtEnd(): boolean {
				return true;
			},
			pastePlainText(): boolean {
				return false;
			},
			copyText(): boolean {
				return false;
			},
		});

		controller.request('article_generate', {
			template: 'leave_apply',
			variables: ['张三', '家中有事', '3天', '2026-09-03'],
		});
		assert.deepEqual(bridge.calls.request[0], {
			taskType: 'article_generate',
			selection: '',
			context: {
				template: 'leave_apply',
				variables: ['张三', '家中有事', '3天', '2026-09-03'],
			},
		});
	});

	it('attaches images to the request payload and keeps them on regenerate', function () {
		const bridge = createFakeBridge('');
		const controller = new WriterAiController(bridge, {
			insertAtEnd(): boolean {
				return true;
			},
			pastePlainText(): boolean {
				return false;
			},
			copyText(): boolean {
				return false;
			},
		});

		controller.request('text_extract', {}, undefined, ['aW1nMQ==']);

		bridge.emit(aiMessage('ai.done', { fullText: '识别出的文字' }));
		assert.equal(controller.getState().state, 'ready');
		controller.regenerate();
		assert.deepEqual(bridge.calls.request[1].images, ['aW1nMQ==']);
	});

	it('omits the images field when no image was attached', function () {
		const bridge = createFakeBridge('选区');
		const controller = new WriterAiController(bridge, {
			replaceSelection(): boolean {
				return true;
			},
			pastePlainText(): boolean {
				return false;
			},
			copyText(): boolean {
				return false;
			},
		});
		controller.request('polish', {});
		assert.equal(bridge.calls.request[0].images, undefined);
	});
	it('awaits clipboard completion and keeps a failed copy visible without rejecting the preview', async function () {
		const bridge = createFakeBridge('选区');
		const controller = new WriterAiController(bridge, {
			pastePlainText(): boolean { return false; },
			copyText(): Promise<boolean> { return Promise.resolve(true); },
		});
		controller.request('polish');
		bridge.emit(aiMessage('ai.done', { fullText: '结果' }));
		assert.equal(await controller.copy(), true);
		assert.equal(controller.getState().error, undefined);

		const failedBridge = createFakeBridge('选区');
		const failedController = new WriterAiController(failedBridge, {
			pastePlainText(): boolean { return false; },
			copyText(): Promise<boolean> { return Promise.reject(new Error('剪贴板拒绝访问')); },
		});
		failedController.request('polish');
		failedBridge.emit(aiMessage('ai.done', { fullText: '另一个结果' }));
		assert.equal(await failedController.copy(), false);
		assert.equal(failedController.getState().state, 'ready');
		assert.equal(failedController.getState().error, '剪贴板拒绝访问');
	});
	it('ignores a late clipboard result after a new task replaces the preview', async function () {
		const bridge = createFakeBridge('选区');
		let resolveCopy: ((copied: boolean) => void) | undefined;
		const controller = new WriterAiController(bridge, {
			pastePlainText(): boolean { return false; },
			copyText(): Promise<boolean> {
				return new Promise((resolve) => { resolveCopy = resolve; });
			},
		});
		controller.request('polish');
		bridge.emit(aiMessage('ai.done', { fullText: '旧结果' }));
		const copy = controller.copy();
		controller.resetPreview();
		controller.request('polish');
		resolveCopy!(false);
		assert.equal(await copy, false);
		assert.equal(controller.getState().state, 'loading');
		assert.equal(controller.getState().error, undefined);
	});
	it('reports the native clipboard API rejection as a failed copy', async function () {
		const previousNavigator = Object.getOwnPropertyDescriptor(global, 'navigator');
		Object.defineProperty(global, 'navigator', {
			configurable: true,
			value: { clipboard: { writeText: () => Promise.reject(new Error('denied')) } },
		});
		try {
			const adapter = (WriterAiController as any).defaultDocumentAdapter() as WriterAiDocumentAdapter;
			assert.equal(await adapter.copyText!('text'), false);
		} finally {
			if (previousNavigator) Object.defineProperty(global, 'navigator', previousNavigator);
			else delete (global as any).navigator;
		}
	});
	it('keeps native truncation warnings visible while the result remains ready', function () {
		const bridge = createFakeBridge('选区');
		const controller = new WriterAiController(bridge, { pastePlainText: () => false });
		controller.request('polish');
		bridge.emit(aiMessage('ai.done', { fullText: '结果', warning: '回答已截断' }));
		assert.equal(controller.getState().state, 'ready');
		assert.equal(controller.getState().warning, '回答已截断');
	});
	it('waits for native Writer insertion acknowledgement and sends the catalog result mode', async function () {
		const previousWindow = (global as any).window;
		const originalGetInstance = NativeBridge.getInstance;
		const posted: NativeBridgeEnvelope[] = [];
		const nativeBridge = new (NativeBridge as any)({
			postMessage: (message: string) => posted.push(JSON.parse(message)),
		}) as NativeBridge;
		NativeBridge.getInstance = () => nativeBridge;
		(global as any).window = {
			ThisIsTheiOSApp: true, setTimeout, clearTimeout,
			app: { map: { getDocType: () => 'text', isEditMode: () => true,
				focus: () => undefined, _docLayer: { _viewId: 42 } } },
		};
		try {
			const bridge = createFakeBridge('原文');
			const controller = new WriterAiController(bridge);
			controller.request('continue');
			bridge.emit(aiMessage('ai.done', { fullText: '续写' }));
			const insertion = controller.acceptAsync('<p>续写</p>');
			assert.equal(controller.getState().state, 'inserting');
			assert.equal(posted.length, 1);
			assert.equal(posted[0].type, 'ai.text.insert');
			assert.equal(posted[0].payload?.resultMode, 'appendAfterSelection');
			assert.equal(bridge.calls.accept.length, 0);
			nativeBridge.onMessage({
				...posted[0], type: 'ai.text.insert.done', payload: { inserted: true },
			});
			assert.equal(await insertion, true);
			assert.equal(bridge.calls.accept.length, 1);
			assert.equal(controller.getState().state, 'accepted');
		} finally {
			NativeBridge.getInstance = originalGetInstance;
			(global as any).window = previousWindow;
		}
	});
	it('uses native Impress paste and ignores insertion replies from another document', async function () {
		const previousWindow = (global as any).window;
		const originalGetInstance = NativeBridge.getInstance;
		const posted: NativeBridgeEnvelope[] = [];
		const focus: boolean[] = [];
		const nativeBridge = new (NativeBridge as any)({
			postMessage: (message: string) => posted.push(JSON.parse(message)),
		}) as NativeBridge;
		NativeBridge.getInstance = () => nativeBridge;
		(global as any).window = {
			ThisIsTheiOSApp: true, setTimeout, clearTimeout,
			app: { map: { getDocType: () => 'presentation', isEditMode: () => true,
				focus: (keyboard: boolean) => focus.push(keyboard), _docLayer: { _viewId: 42 } } },
		};
		try {
			const adapter = (WriterAiController as any).defaultDocumentAdapter() as WriterAiDocumentAdapter;
			let complete = false;
			const insertion = adapter.insertPresentationHtml!('<p>重写结果</p>', 'generation-1').then((ok) => { complete = true; return ok; });
			assert.equal(posted.length, 1);
			assert.equal(posted[0].type, 'ai.text.insert');
			assert.deepEqual(focus, [false]);
			assert.deepEqual(posted[0].payload, { html: '<p>重写结果</p>', sourceRequestId: 'generation-1', viewId: 42 });
			const response: NativeBridgeEnvelope = {
				...posted[0], type: 'ai.text.insert.done', payload: { inserted: true },
			};
			nativeBridge.onMessage({ ...response, documentSessionId: 'other-document' });
			await Promise.resolve();
			assert.equal(complete, false);
			nativeBridge.onMessage(response);
			assert.equal(await insertion, true);
		} finally {
			NativeBridge.getInstance = originalGetInstance;
			(global as any).window = previousWindow;
		}
	});

	it('retries a lost native paste ACK with the same operation id without duplicating insertion', async function () {
		const previousWindow = (global as any).window;
		const originalGetInstance = NativeBridge.getInstance;
		const timers: Array<() => void> = [];
		const posted: NativeBridgeEnvelope[] = [];
		const pastedSources = new Set<string>();
		let pasteCount = 0;
		let nativeBridge: NativeBridge;
		nativeBridge = new (NativeBridge as any)({
			postMessage: (serialized: string) => {
				const message: NativeBridgeEnvelope = JSON.parse(serialized);
				posted.push(message);
				const source = message.payload!.sourceRequestId;
				if (!pastedSources.has(source)) {
					pastedSources.add(source);
					pasteCount++;
					return; // Paste succeeded; deliberately lose the first acknowledgement.
				}
				nativeBridge.onMessage({ ...message, type: 'ai.text.insert.done', payload: { inserted: true } });
			},
		}) as NativeBridge;
		NativeBridge.getInstance = () => nativeBridge;
		(global as any).window = {
			ThisIsTheiOSApp: true,
			setTimeout: (callback: () => void) => { timers.push(callback); return timers.length; },
			clearTimeout: () => undefined,
			app: { map: { getDocType: () => 'presentation', isEditMode: () => true,
				_docLayer: { _viewId: 42 }, focus: () => undefined } },
		};
		try {
			const bridge = createFakeBridge('选中文本');
			const controller = new WriterAiController(bridge);
			controller.request('rewrite');
			bridge.emit(aiMessage('ai.done', { fullText: '重写结果' }));
			const firstTry = controller.acceptAsync('<p>重写结果</p>');
			assert.equal(pasteCount, 1);
			assert.equal(bridge.calls.accept.length, 0);
			timers[0]();
			assert.equal(await firstTry, false);
			assert.equal(controller.getState().state, 'ready');
			assert.equal(await controller.acceptAsync('<p>重写结果</p>'), true);
			assert.equal(posted.length, 2);
			assert.equal(posted[0].requestId, posted[1].requestId);
			assert.equal(posted[0].payload!.sourceRequestId, 'request-1');
			assert.equal(posted[1].payload!.viewId, 42);
			assert.equal(pasteCount, 1);
			assert.equal(bridge.calls.accept.length, 1);
			assert.equal(controller.getState().state, 'accepted');
		} finally {
			NativeBridge.getInstance = originalGetInstance;
			(global as any).window = previousWindow;
		}
	});

	it('clears a stale loading state from a closed dialog when a new task opens', function () {
		const bridge = createFakeBridge('选中文本');
		const controller = new WriterAiController(bridge, {
			pastePlainText: () => false,
		});
		const requestId = controller.request('polish', { polishStyle: 'formal' });
		assert.equal(controller.getState().state, 'loading');
		assert.equal(controller.resetPreview(), true);
		assert.deepEqual(bridge.calls.cancel, [requestId]);
		assert.equal(controller.getState().state, 'idle');
		assert.equal(controller.getState().preview, '');
		assert.equal(controller.getState().requestId, undefined);
	});

	it('does not reset while a native insertion is still being confirmed', function () {
		const bridge = createFakeBridge('选中文本');
		const controller = new WriterAiController(bridge, { pastePlainText: () => false });
		controller.request('rewrite');
		bridge.emit(aiMessage('ai.done', { fullText: '重写结果' }));
		(controller as any).state = { ...(controller as any).state, state: 'inserting' };
		assert.equal(controller.resetPreview(), false);
		assert.equal(controller.getState().state, 'inserting');
		assert.deepEqual(bridge.calls.cancel, []);
	});

});
