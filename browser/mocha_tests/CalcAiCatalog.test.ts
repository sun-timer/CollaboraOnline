/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Calc AI Catalog v1', function () {
	it('exposes the Phase 5 P1 task types in protocol order', function () {
		assert.deepEqual(CalcAiCatalog.P1_TASK_TYPES, [
			'calc_formula',
			'calc_data_analysis',
		]);
	});

	it('defines unique prompt ids and result modes for all P1+P2 tasks', function () {
		const promptIds: { [promptId: string]: boolean } = {};
		CalcAiCatalog.P1_TASK_TYPES.concat(CalcAiCatalog.P2_TASK_TYPES).forEach(
			function (taskType) {
				const task = CalcAiCatalog.getTask(taskType);
				assert.ok(task, 'task defined: ' + taskType);
				assert.equal(promptIds[task.promptId], undefined, 'unique promptId');
				promptIds[task.promptId] = true;
			},
		);
		assert.equal(CalcAiCatalog.getTask('calc_formula')?.resultMode, 'insertFormula');
		assert.equal(
			CalcAiCatalog.getTask('calc_data_analysis')?.resultMode,
			'conversation',
		);
		assert.equal(CalcAiCatalog.getTask('calc_chart')?.resultMode, 'mutateConfirm');
		assert.equal(CalcAiCatalog.getTask('polish'), null);
	});

	it('validates prompts, ranges and secure-field boundaries', function () {
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_formula',
				selection: '计算平均值',
				context: { cellAddress: 'A1' },
			}).valid,
			true,
		);
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_formula',
				selection: '计算平均值',
				context: {},
			}).valid,
			true,
		);
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_data_analysis',
				selection: '总结趋势',
				context: { cellRange: 'A1:B10', cellData: '1\t2' },
			}).valid,
			true,
		);
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_data_analysis',
				selection: '总结趋势',
				context: { cellData: '1\t2' },
			}).errorCode,
			'empty_range',
		);
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_formula',
				selection: ' ',
				context: {},
			}).errorCode,
			'empty_prompt',
		);
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_formula',
				selection: '计算平均值',
				apiKey: 'must-not-cross-bridge',
			}).errorCode,
			'sensitive_field',
		);
		assert.equal(
			CalcAiCatalog.validateRequest({
				taskType: 'calc_formula',
				selection: '计算平均值',
				context: { cellRange: 'A1:B2' },
			}).errorCode,
			'invalid_context_field',
		);
	});

	it('normalizes formula output from free-form model text', function () {
		assert.equal(CalcAiCatalog.normalizeFormula('=SUM(A1:A10)'), '=SUM(A1:A10)');
		assert.equal(
			CalcAiCatalog.normalizeFormula('```\n=AVERAGE(B1:B5)\n```'),
			'=AVERAGE(B1:B5)',
		);
		assert.equal(CalcAiCatalog.normalizeFormula('公式：SUM(C1:C3)'), '=SUM(C1:C3)');
		assert.equal(CalcAiCatalog.normalizeFormula('SUM(D1:D2)'), '=SUM(D1:D2)');
	});
});

describe('Calc AI Context', function () {
	it('samples large TSV selections like Android', function () {
		const lines = [];
		for (let i = 0; i < 205; i++) {
			lines.push('row-' + i);
		}
		const sample = CalcAiContext.sampleCellData(lines.join('\n'));
		assert.ok(sample.indexOf('共 205 行') >= 0);
		assert.ok(sample.indexOf('row-0') >= 0);
		assert.ok(sample.indexOf('row-19') >= 0);
		assert.equal(sample.indexOf('row-20') < 0, true);
	});

	it('encodes active cell addresses in A1 notation', function () {
		assert.equal(CalcAiContext.columnToLetters(0), 'A');
		assert.equal(CalcAiContext.columnToLetters(25), 'Z');
		assert.equal(CalcAiContext.columnToLetters(26), 'AA');
	});
});
