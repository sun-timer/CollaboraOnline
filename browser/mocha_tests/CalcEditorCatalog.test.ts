/* -*- js-indent-level: 8 -*- */

var assert = require('assert').strict;

describe('CalcEditorCatalog', function () {
	it('registers six editor tabs aligned with Android', function () {
		assert.equal(CalcEditorCatalog.TABS.length, 6);
		assert.deepEqual(
			CalcEditorCatalog.TABS.map(function (tab) {
				return tab.id;
			}),
			['default', 'file', 'insert', 'layout', 'data', 'review'],
		);
	});

	it('passes registry validation', function () {
		assert.deepEqual(CalcEditorCatalog.validateRegistry(), { valid: true });
	});

	it('includes calc-specific insert and data actions', function () {
		assert.equal(CalcEditorCatalog.getFeature('insert-chart')?.dialog, 'chart');
		assert.equal(CalcEditorCatalog.getFeature('sort-asc')?.unocmd, '.uno:SortAscending');
		assert.equal(CalcEditorCatalog.getFeature('merge-cells')?.unocmd, '.uno:ToggleMergeCells');
	});
});
