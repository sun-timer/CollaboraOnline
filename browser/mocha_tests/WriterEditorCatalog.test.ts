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
	it('every feature icon has a rendered icon asset', function () {
		WriterEditorCatalog.FEATURES.forEach((feature) => {
			assert.ok(
				WriterEditorIcons.has(feature.icon),
				'missing icon asset for ' + feature.id,
			);
		});
	});
	it('carries the full CO paper-format preset list (29 entries)', function () {
		assert.equal(WriterEditorCatalog.PAPER_FORMATS.length, 29);
		// Spot-check the LO PaperFormat enum values Android transcribes from CO.
		const byLabel = {};
		WriterEditorCatalog.PAPER_FORMATS.forEach((preset) => {
			byLabel[preset.label] = preset.value;
		});
		assert.equal(byLabel['A4'], '4');
		assert.equal(byLabel['A3'], '3');
		assert.equal(byLabel['A5'], '5');
		assert.equal(byLabel['信纸'], '8');
		assert.equal(byLabel['法律专用纸'], '9');
		assert.equal(byLabel['16开'], '31');
		assert.equal(byLabel['日本明信片'], '46');
		assert.equal(byLabel['10 号信封'], '28');
		// No duplicates or empty entries.
		const ids = WriterEditorCatalog.PAPER_FORMATS.map((preset) => preset.value);
		assert.equal(new Set(ids).size, ids.length);
		WriterEditorCatalog.PAPER_FORMATS.forEach((preset) => {
			assert.ok(preset.label && preset.value);
		});
	});

	it('carries the full CO page-margin preset list (8 entries)', function () {
		assert.equal(WriterEditorCatalog.MARGIN_PRESETS.length, 8);
		const labels = WriterEditorCatalog.MARGIN_PRESETS.map((m) => m.label);
		assert.deepEqual(labels, ['无', '窄', '适中', '正常（1.90 cm）', '正常（2.54 cm）', '正常（3.18 cm）', '宽', '镜像']);
		const wide = WriterEditorCatalog.MARGIN_PRESETS.find((m) => m.id === 'wide');
		assert.ok(wide);
		assert.deepEqual([wide.left, wide.right, wide.top, wide.bottom], [5080, 5080, 2540, 2540]);
		const mirrored = WriterEditorCatalog.MARGIN_PRESETS.find((m) => m.id === 'mirrored');
		assert.ok(mirrored);
		assert.deepEqual([mirrored.left, mirrored.right, mirrored.top, mirrored.bottom], [5080, 2540, 2540, 2540]);
		const none = WriterEditorCatalog.MARGIN_PRESETS.find((m) => m.id === 'none');
		assert.ok(none);
		assert.deepEqual([none.left, none.right, none.top, none.bottom], [0, 0, 0, 0]);
	});

	it('carries the Figma style ordering (8 entries with CO style ids)', function () {
		assert.equal(WriterEditorCatalog.STYLE_ORDER.length, 8);
		const ids = WriterEditorCatalog.STYLE_ORDER.map((s) => s.styleId);
		assert.deepEqual(ids, [
			'Default Paragraph Style', 'List', 'Caption', 'Index',
			'Heading 1', 'Heading 2', 'Heading 3', 'caption',
		]);
		assert.equal(WriterEditorCatalog.STYLE_ORDER[0].label, '正文');
		assert.equal(WriterEditorCatalog.STYLE_ORDER[4].label, '标题1');
	});

	it('reorders styles: preferred first in Figma order, rest kept in CO order', function () {
		const reordered = WriterEditorCatalog.reorderStyleOptions([
			'Heading 1', 'Text Body', 'Default Paragraph Style', 'List 1',
		]);
		assert.deepEqual(reordered, [
			{ label: '正文', value: 'Default Paragraph Style' },
			{ label: '列表', value: 'List 1' },
			{ label: '标题1', value: 'Heading 1' },
			{ label: 'Text Body', value: 'Text Body' },
		]);
	});

	it('reorders styles: List/Index prefix matching and case-insensitive ids', function () {
		const reordered = WriterEditorCatalog.reorderStyleOptions([
			'INDEX Line', 'list', 'DEFAULT PARAGRAPH STYLE',
		]);
		assert.deepEqual(reordered, [
			{ label: '正文', value: 'DEFAULT PARAGRAPH STYLE' },
			{ label: '列表', value: 'list' },
			{ label: '索引', value: 'INDEX Line' },
		]);
	});

	it('reorders styles: a preferred id is only placed once', function () {
		const reordered = WriterEditorCatalog.reorderStyleOptions(['List 1', 'List 2', 'List']);
		assert.equal(reordered.length, 3);
		assert.equal(reordered[0].label, '列表');
		const preferred = reordered.filter((option) => option.label === '列表');
		assert.equal(preferred.length, 1);
	});

	it('maps Windows font names to the CO-bundled substitutes', function () {
		assert.equal(WriterEditorCatalog.aliasFont('Arial'), 'Liberation Sans');
		assert.equal(WriterEditorCatalog.aliasFont('times new roman'), 'Liberation Serif');
		assert.equal(WriterEditorCatalog.aliasFont('Courier New'), 'Liberation Mono');
		assert.equal(WriterEditorCatalog.aliasFont('Noto Sans CJK SC'), 'Noto Sans CJK SC');
	});

	it('carries the fallback font list (5 entries)', function () {
		assert.deepEqual(WriterEditorCatalog.FONT_FALLBACK_OPTIONS, [
			'Liberation Serif', 'Liberation Sans', 'Liberation Mono', 'Arial', 'Times New Roman',
		]);
	});

	it('exposes track-changes as a dialog feature (open/close choices)', function () {
		const feature = WriterEditorCatalog.getFeature('track-changes');
		assert.ok(feature);
		assert.equal(feature.kind, 'dialog');
		assert.equal(feature.dialog, 'trackChanges');
	});
});
