/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('CalcAiController', function () {
	interface FakeCalcAiBridge extends CalcAiBridgeLike {
		calls: {
			request: Array<{ [key: string]: any }>;
			cancel: string[];
			accept: Array<{ requestId: string; text: string }>;
		};
		emit(message: NativeBridgeEnvelope): void;
	}

	function createFakeBridge(): FakeCalcAiBridge {
		const listeners: Array<(message: NativeBridgeEnvelope) => void> = [];
		let nextRequestId = 1;
		const calls: FakeCalcAiBridge['calls'] = {
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

	beforeEach(function () {
		(window as any).app = {
			map: {
				getDocType: function () {
					return 'spreadsheet';
				},
			},
			calc: {
				cellAddress: { x: 0, y: 0 },
			},
		};
	});

	it('streams a formula preview and inserts the normalized formula once', function () {
		const bridge = createFakeBridge();
		const inserted: string[] = [];
		const controller = new CalcAiController(bridge, {
			pastePlainText(text) {
				inserted.push(text);
				return true;
			},
		});

		assert.equal(
			controller.request('calc_formula', '计算平均值', { cellAddress: 'A1' }),
			'request-1',
		);
		assert.equal(bridge.calls.request[0].selection, '计算平均值');
		assert.equal(bridge.calls.request[0].context.cellAddress, 'A1');
		bridge.emit(aiMessage('ai.stream', { delta: '```\n=AVERAGE(A1:A10)\n```' }));
		bridge.emit(
			aiMessage('ai.done', { fullText: '```\n=AVERAGE(A1:A10)\n```' }),
		);

		assert.equal(controller.getState().state, 'ready');
		assert.equal(inserted.length, 0);
		assert.equal(controller.accept(), true);
		assert.deepEqual(inserted, ['=AVERAGE(A1:A10)']);
		assert.deepEqual(bridge.calls.accept, [
			{ requestId: 'request-1', text: '=AVERAGE(A1:A10)' },
		]);
		assert.equal(controller.getState().state, 'accepted');
	});

	it('keeps data analysis read-only without inserting into cells', function () {
		const bridge = createFakeBridge();
		const inserted: string[] = [];
		const controller = new CalcAiController(bridge, {
			pastePlainText(text) {
				inserted.push(text);
				return true;
			},
		});

		assert.equal(
			controller.request('calc_data_analysis', '总结趋势', {
				cellRange: 'A1:B3',
				cellData: '1\t2',
			}),
			'request-1',
		);
		bridge.emit(aiMessage('ai.done', { fullText: '数据整体平稳' }));
		assert.equal(controller.getState().state, 'ready');
		assert.equal(controller.accept(), false);
		assert.deepEqual(inserted, []);
	});

	it('rejects empty prompts and empty analysis ranges', function () {
		const bridge = createFakeBridge();
		const controller = new CalcAiController(bridge, {
			pastePlainText() {
				return true;
			},
		});
		assert.equal(controller.request('calc_formula', ' '), null);
		assert.equal(controller.getState().state, 'error');
		assert.equal(
			controller.request('calc_data_analysis', '总结', { cellData: '1' }),
			null,
		);
		assert.equal(controller.getState().error, '请先选择要分析的单元格区域');
	});

	it('rejects Writer documents', function () {
		(window as any).app.map.getDocType = function () {
			return 'text';
		};
		const bridge = createFakeBridge();
		const controller = new CalcAiController(bridge, {
			pastePlainText() {
				return true;
			},
		});
		assert.equal(controller.request('calc_formula', '求和'), null);
		assert.equal(controller.getState().error, 'Calc AI 仅支持电子表格文档');
	});
});
