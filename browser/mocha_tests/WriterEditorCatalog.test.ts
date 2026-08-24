/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterEditorCatalog', function () {
	it('registers five editor tabs', function () {
		assert.equal(WriterEditorCatalog.TABS.length, 5);
		const ids = WriterEditorCatalog.TABS.map((tab) => tab.id);
		assert.deepEqual(ids, ['default', 'file', 'insert', 'layout', 'review']);
	});

	it('passes registry validation', function () {
		assert.deepEqual(WriterEditorCatalog.validateRegistry(), { valid: true });
	});

	it('has at least one feature per tab', function () {
		WriterEditorCatalog.TABS.forEach((tab) => {
			assert.ok(
				WriterEditorCatalog.getFeatures(tab.id).length >= 1,
				'tab ' + tab.id + ' must have features',
			);
		});
	});

	it('exposes the common-tab history and paragraph commands', function () {
		const undo = WriterEditorCatalog.getFeature('undo');
		assert.ok(undo);
		assert.equal(undo && undo.unocmd, '.uno:Undo');
		const alignLeft = WriterEditorCatalog.getFeature('align-left');
		assert.ok(alignLeft);
		assert.equal(alignLeft && alignLeft.unocmd, '.uno:LeftPara');
	});

	it('maps save to the .uno:Save command', function () {
		const save = WriterEditorCatalog.getFeature('save');
		assert.ok(save);
		assert.equal(save && save.kind, 'save');
		assert.equal(save && save.unocmd, '.uno:Save');
	});

	it('carries the Chinese font-size table', function () {
		assert.equal(WriterEditorCatalog.CHAR_HEIGHT_CN['初号'], '42pt');
		assert.equal(WriterEditorCatalog.CHAR_HEIGHT_CN['小五'], '9pt');
	});

	it('rejects a feature with an empty id', function () {
		const feature: WriterEditorFeature = {
			id: '',
			label: 'x',
			tab: 'default',
			icon: 'x',
			kind: 'command',
			unocmd: '.uno:Undo',
		};
		assert.equal(WriterEditorCatalog.validateFeature(feature).errorCode, 'empty_id');
	});

	it('rejects a command-like feature without a unocmd', function () {
		const feature: WriterEditorFeature = {
			id: 'broken',
			label: 'x',
			tab: 'default',
			icon: 'x',
			kind: 'command',
		};
		assert.equal(
			WriterEditorCatalog.validateFeature(feature).errorCode,
			'missing_unocmd',
		);
	});

	it('rejects an empty icon', function () {
		const feature: WriterEditorFeature = {
			id: 'broken-icon',
			label: 'x',
			tab: 'default',
			icon: '',
			kind: 'command',
			unocmd: '.uno:Undo',
		};
		assert.equal(WriterEditorCatalog.validateFeature(feature).errorCode, 'empty_icon');
	});

	it('detects a duplicate feature id against the registry', function () {
		const duplicate: WriterEditorFeature = {
			id: 'undo',
			label: 'x',
			tab: 'default',
			icon: 'x',
			kind: 'command',
			unocmd: '.uno:Undo',
		};
		assert.equal(
			WriterEditorCatalog.validateFeature(duplicate).errorCode,
			'duplicate_id',
		);
	});
});
