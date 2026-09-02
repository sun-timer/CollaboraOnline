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
});
