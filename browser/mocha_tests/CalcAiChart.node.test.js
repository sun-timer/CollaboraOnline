/*
 * Copyright the Collabora Online contributors.
 * SPDX-License-Identifier: MPL-2.0
 *
 * Run with Node 24+: node --test mocha_tests/CalcAiChart.node.test.js
 * Runs the source with native type stripping, without building browser assets.
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { stripTypeScriptTypes } = require('node:module');
const test = require('node:test');
const vm = require('node:vm');

function createHarness({ connected = true } = {}) {
	const messages = [];
	const window = { app: connected ? { socket: { sendMessage: message => messages.push(message) } } : {} };
	const context = vm.createContext({ window, console });
	for (const file of ['WriterEditorCatalog.ts', 'CalcAiCatalog.ts', 'CalcAiMutateApply.ts']) {
		const source = fs.readFileSync(path.resolve(__dirname, '../src/app', file), 'utf8');
		vm.runInContext(stripTypeScriptTypes(source), context, { filename: file });
	}
	return {
		messages,
		apply: plan => window.CalcAiMutateApply.apply('calc_chart', JSON.stringify(plan)),
	};
}

const range = "$'销售 数据'.$A$1:$B$6";
for (const [type, template] of [
	['pie', 'Pie'], ['bar', 'Bar'], ['line', 'LineSymbol'], ['column', null],
]) {
	test(`AI ${type} plan sends its type and source range to the document`, () => {
		const harness = createHarness();
		assert.equal(harness.apply({ chart: { chartType: type, dataRange: range } }).ok, true);
		assert.equal(harness.messages.length, 1);
		const prefix = 'uno .uno:InsertObjectChart ';
		assert.ok(harness.messages[0].startsWith(prefix));
		const args = JSON.parse(harness.messages[0].slice(prefix.length));
		assert.deepEqual(args.RangeList, { type: 'string', value: range });
		assert.deepEqual(args.InNewTable, { type: 'boolean', value: false });
		assert.deepEqual(args.ChartTemplate, template
			? { type: 'string', value: 'com.sun.star.chart2.template.' + template } : undefined);
	});
}

test('accepts a top-level plan and normalizes chart type whitespace and case', () => {
	const harness = createHarness();
	assert.equal(harness.apply({ chartType: ' PIE ', dataRange: ' A1:B6 ' }).ok, true);
	const args = JSON.parse(harness.messages[0].slice('uno .uno:InsertObjectChart '.length));
	assert.equal(args.ChartTemplate.value, 'com.sun.star.chart2.template.Pie');
	assert.equal(args.RangeList.value, 'A1:B6');
});

test('invalid or missing chart type does not silently insert a column chart', () => {
	for (const chartType of [undefined, '', 'radar', 'pie|bar|column|line']) {
		const harness = createHarness();
		const result = harness.apply({ chart: { chartType, dataRange: range } });
		assert.equal(result.ok, false);
		assert.match(result.error, /图表类型/);
		assert.equal(harness.messages.length, 0);
	}
});

test('missing source range fails without inserting a chart', () => {
	const harness = createHarness();
	assert.equal(harness.apply({ chart: { chartType: 'pie' } }).ok, false);
	assert.equal(harness.messages.length, 0);
});

test('a disconnected document returns an insertion failure', () => {
	const harness = createHarness({ connected: false });
	const result = harness.apply({ chart: { chartType: 'pie', dataRange: range } });
	assert.equal(result.ok, false);
	assert.match(result.error, /无法发送插入图表命令/);
});
