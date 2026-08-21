/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('MobileAiConversationController', function () {
	function createBridge() {
		const listeners: Array<(message: NativeBridgeEnvelope) => void> = [];
		const calls: { request: any[]; cancel: string[] } = {
			request: [],
			cancel: [],
		};
		let nextRequestId = 1;
		return {
			calls,
			request(payload: { [key: string]: any }): string {
				calls.request.push(payload);
				return 'conversation-' + nextRequestId++;
			},
			cancel(requestId: string): boolean {
				calls.cancel.push(requestId);
				return true;
			},
			isAvailable(): boolean {
				return true;
			},
			getSelectedText(): string {
				return '文档选区';
			},
			subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void {
				listeners.push(listener);
				return function () {
					const index = listeners.indexOf(listener);
					if (index >= 0) {
						listeners.splice(index, 1);
					}
				};
			},
			emit(type: string, payload: { [key: string]: any }, requestId = 'conversation-1') {
				const message: NativeBridgeEnvelope = {
					protocolVersion: 1,
					channel: 'native',
					type,
					requestId,
					payload,
				};
				listeners.slice().forEach(function (listener) {
					listener(message);
				});
			},
		};
	}

	it('keeps conversation messages separate from Writer insertion', function () {
		const bridge = createBridge();
		const controller = new MobileAiConversationController(bridge);

		assert.equal(controller.send('这份文档讲了什么？', 'doc_qa'), 'conversation-1');
		assert.equal(bridge.calls.request[0].taskType, 'doc_qa');
		assert.equal(bridge.calls.request[0].selection, '文档选区');
		bridge.emit('ai.stream', { delta: '这是' });
		bridge.emit('ai.done', { fullText: '这是文档摘要' });

		const state = controller.getState();
		assert.equal(state.status, 'ready');
		assert.deepEqual(state.messages, [
			{ role: 'user', content: '这份文档讲了什么？' },
			{ role: 'assistant', content: '这是文档摘要' },
		]);
	});

	it('rejects empty prompts and cancels an active request', function () {
		const bridge = createBridge();
		const controller = new MobileAiConversationController(bridge);

		assert.equal(controller.send('   '), null);
		assert.equal(controller.getState().status, 'error');
		controller.send('继续');
		assert.equal(controller.cancel(), true);
		assert.deepEqual(bridge.calls.cancel, ['conversation-1']);
		assert.equal(controller.getState().status, 'cancelled');
	});
});
