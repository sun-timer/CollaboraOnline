/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('NativeBridge v1 contract', function () {
	it('accepts a valid AI request envelope', function () {
		const result = NativeBridge.validateEnvelope(
			NativeBridgeTestData.validRequest,
		);

		assert.equal(result.valid, true);
		assert.equal(result.errorCode, undefined);
	});

	it('requires an id, document session and HTML for native AI insertion', function () {
		const request = {
			protocolVersion: 1, channel: 'native', type: 'ai.text.insert',
			requestId: 'insert-1', documentSessionId: 'document-1',
			targetPlatform: 'ios', payload: { html: '<p>结果</p>', sourceRequestId: 'generation-1', viewId: 42 },
		};
		assert.equal(NativeBridge.validateEnvelope(request).valid, true);
		assert.equal(NativeBridge.validateEnvelope({ ...request, requestId: '' }).valid, false);
		assert.equal(NativeBridge.validateEnvelope({ ...request, documentSessionId: undefined }).valid, false);
		assert.equal(NativeBridge.validateEnvelope({ ...request, payload: { ...request.payload, html: '' } }).valid, false);
		assert.equal(NativeBridge.validateEnvelope({ ...request, payload: { ...request.payload, sourceRequestId: '' } }).valid, false);
		assert.equal(NativeBridge.validateEnvelope({ ...request, payload: { ...request.payload, viewId: -1 } }).valid, false);
		assert.equal(NativeBridge.validateEnvelope({ ...request, payload: { ...request.payload, viewId: undefined } }).valid, false);
	});

	it('rejects unknown native message types', function () {
		const result = NativeBridge.validateEnvelope(
			NativeBridgeTestData.unknownType,
		);

		assert.equal(result.valid, false);
		assert.equal(result.errorCode, 'unsupported_type');
	});

	it('rejects AI requests without a request id', function () {
		const result = NativeBridge.validateEnvelope(
			NativeBridgeTestData.missingRequestId,
		);

		assert.equal(result.valid, false);
		assert.equal(result.errorCode, 'missing_request_id');
	});
});

describe('MobileAiBridge Writer P0 contract', function () {
	it('exposes only the phase 2 Writer task type table', function () {
		assert.deepEqual(MobileAiBridge.WRITER_P0_TASK_TYPES, [
			'polish',
			'translate',
			'expand',
			'condense',
			'rewrite',
			'continue',
			'summarize',
		]);
	});

	it('reports a failed request post as ai.error so the task does not stay loading', async function () {
		// A bridge without a transport makes every post fail.
		const nativeBridge = new (NativeBridge as any)(null);
		const bridge = new (MobileAiBridge as any)(nativeBridge);
		const events: any[] = [];
		bridge.subscribe((message: any) => events.push(message));
		const requestId = bridge.request({ taskType: 'polish', selection: '文本' });
		assert.equal(events.length, 0);
		await Promise.resolve();
		assert.equal(events.length, 1);
		assert.equal(events[0].type, 'ai.error');
		assert.equal(events[0].requestId, requestId);
	});
});
