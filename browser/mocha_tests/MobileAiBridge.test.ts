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
});
