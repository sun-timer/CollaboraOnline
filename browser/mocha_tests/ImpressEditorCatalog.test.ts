/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('ImpressEditorCatalog', function () {
	it('registers six editor tabs', function () {
		assert.equal(ImpressEditorCatalog.TABS.length, 6);
		assert.deepEqual(
			ImpressEditorCatalog.TABS.map(function (tab) {
				return tab.id;
			}),
			['default', 'file', 'insert', 'transition', 'layout', 'review'],
		);
	});

	it('passes registry validation', function () {
		assert.deepEqual(ImpressEditorCatalog.validateRegistry(), { valid: true });
	});

	it('wires file rows matching Android', function () {
		assert.equal(ImpressEditorCatalog.getFeature('save')?.kind, 'save');
		assert.equal(ImpressEditorCatalog.getFeature('save')?.unocmd, '.uno:Save');
		assert.equal(ImpressEditorCatalog.getFeature('save-as')?.dialog, 'saveAs');
		assert.equal(ImpressEditorCatalog.getFeature('export-pdf')?.kind, 'export');
		assert.equal(ImpressEditorCatalog.getFeature('print')?.kind, 'print');
	});

	it('maps AssignLayout WhatLayout values from Android', function () {
		const title = ImpressEditorCatalog.getFeature('layout-title');
		assert.ok(title);
		assert.equal(title?.unocmd, '.uno:AssignLayout');
		assert.equal(title?.queryParams, '?WhatLayout:long=0');
		assert.equal(ImpressEditorCatalog.LAYOUTS.length, 11);
	});

	it('includes insert image and 36 Android transitions', function () {
		assert.equal(ImpressEditorCatalog.getFeature('insert-image')?.dialog, 'image');
		assert.equal(ImpressEditorCatalog.TRANSITIONS.length, 36);
		assert.equal(ImpressEditorCatalog.getFeatures('transition').length, 36);
		assert.equal(ImpressEditorCatalog.TRANSITIONS[0].iconViewIndex, 0);
		assert.equal(ImpressEditorCatalog.TRANSITIONS[35].setId, 'newsflash');
		assert.equal(ImpressEditorCatalog.getFeature('tr-wipe')?.iconViewIndex, 1);
	});

	it('wires insert table, shape, hyperlink, and comment from Android catalogs', function () {
		assert.equal(ImpressEditorCatalog.getFeature('insert-table')?.dialog, 'table');
		assert.equal(ImpressEditorCatalog.getFeature('insert-table')?.unocmd, '.uno:InsertTable');
		assert.equal(ImpressEditorCatalog.getFeature('insert-shape')?.dialog, 'shape');
		assert.equal(ImpressEditorCatalog.getFeature('insert-hyperlink')?.dialog, 'hyperlink');
		assert.equal(ImpressEditorCatalog.getFeature('insert-hyperlink')?.unocmd, '.uno:SetHyperlink');
		assert.equal(ImpressEditorCatalog.getFeature('insert-comment')?.dialog, 'comment');
		assert.equal(ImpressEditorCatalog.getFeature('insert-comment')?.unocmd, '.uno:InsertAnnotation');
		assert.equal(ImpressEditorCatalog.getFeature('review-comment')?.dialog, 'comment');
	});
});
