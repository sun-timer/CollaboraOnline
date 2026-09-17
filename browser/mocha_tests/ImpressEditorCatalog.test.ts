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
		assert.equal(ImpressEditorCatalog.getFeature('save')?.tab, 'file');
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

	it('removes local image from common tab but keeps insert tab image', function () {
		assert.equal(ImpressEditorCatalog.getFeature('common-insert-image'), null);
		assert.equal(ImpressEditorCatalog.getFeature('insert-image')?.tab, 'insert');
		assert.equal(ImpressEditorCatalog.getFeature('insert-image')?.dialog, 'image');
		const defaultIds = ImpressEditorCatalog.getFeatures('default').map(function (f) {
			return f.id;
		});
		assert.equal(defaultIds.indexOf('common-insert-image'), -1);
	});

	it('registers slide format and orientation matching Android UNO', function () {
		assert.equal(ImpressEditorCatalog.getFeature('slide-format')?.dialog, 'slideFormat');
		assert.equal(ImpressEditorCatalog.getFeature('slide-orientation')?.dialog, 'slideOrientation');
		assert.equal(ImpressEditorCatalog.SLIDE_FORMATS[0].label, 'A4');
		assert.equal(ImpressEditorCatalog.SLIDE_FORMATS[0].paperFormat, '4');
		assert.equal(
			ImpressEditorCatalog.SLIDE_ORIENTATIONS[0].unocmd,
			'.uno:Orientation?isLandscape:bool=true',
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_ORIENTATIONS[1].unocmd,
			'.uno:Orientation?isLandscape:bool=false',
		);
	});

	it('registers six paragraph commands matching Android PARA_COMMANDS', function () {
		const expected = [
			['para-left', '.uno:LeftPara'],
			['para-center', '.uno:CenterPara'],
			['para-right', '.uno:RightPara'],
			['para-justify', '.uno:JustifyPara'],
			['para-bullet', '.uno:DefaultBullet'],
			['para-number', '.uno:DefaultNumbering'],
		];
		expected.forEach(function (pair) {
			const feature = ImpressEditorCatalog.getFeature(pair[0]);
			assert.ok(feature);
			assert.equal(feature?.unocmd, pair[1]);
			assert.equal(feature?.row, 'chip');
		});
	});

	it('keeps first three layouts on default and all eleven on layout tab', function () {
		const commonLayouts = ImpressEditorCatalog.getFeatures('default').filter(function (f) {
			return f.row === 'layoutPreview';
		});
		assert.equal(commonLayouts.length, 3);
		assert.equal(commonLayouts[0].id, 'common-layout-title');
		assert.equal(commonLayouts[2].id, 'common-layout-section');
		const layoutTab = ImpressEditorCatalog.getFeatures('layout');
		assert.equal(layoutTab.length, 11);
		assert.equal(layoutTab[0].id, 'layout-title');
		assert.equal(layoutTab[10].id, 'layout-end');
		const defaultIds = ImpressEditorCatalog.getFeatures('default').map(function (f) {
			return f.id;
		});
		assert.equal(defaultIds.indexOf('sec-layout') + 1, defaultIds.indexOf('common-layout-title'));
		assert.ok(defaultIds.indexOf('common-layout-section') < defaultIds.indexOf('sec-char'));
	});

	it('registers common tab sections 幻灯片 / 布局 / 字符 / 段落', function () {
		const sections = ImpressEditorCatalog.getFeatures('default')
			.filter(function (f) {
				return f.kind === 'section';
			})
			.map(function (f) {
				return f.label;
			});
		assert.deepEqual(sections, ['幻灯片', '布局', '字符', '段落']);
	});

	it('places character pickers and tools between 字符 and 段落 sections', function () {
		const defaultIds = ImpressEditorCatalog.getFeatures('default').map(function (f) {
			return f.id;
		});
		const charSection = defaultIds.indexOf('sec-char');
		const paraSection = defaultIds.indexOf('sec-para');
		assert.ok(charSection >= 0 && paraSection > charSection);
		['font-name', 'font-size', 'font-color'].forEach(function (id) {
			const index = defaultIds.indexOf(id);
			assert.ok(index > charSection && index < paraSection, id);
		});
		assert.equal(ImpressEditorCatalog.getFeature('font-name')?.dialog, 'fontName');
		assert.equal(ImpressEditorCatalog.getFeature('font-size')?.dialog, 'fontSize');
		assert.equal(ImpressEditorCatalog.getFeature('font-color')?.dialog, 'fontColor');
		assert.equal(ImpressEditorCatalog.getFeature('font-name')?.pickerDefault, '宋体');
	});

	it('maps char tool UNO commands like Android Impress CHAR_TOOL_COMMANDS', function () {
		const expected = [
			['char-bold', '.uno:Bold'],
			['char-italic', '.uno:Italic'],
			['char-underline', '.uno:Underline'],
			['char-strikeout', '.uno:Strikeout'],
			['char-shadow', '.uno:Shadowed'],
			['char-highlight', '.uno:CharBackColor'],
			['char-superscript', '.uno:SuperScript'],
			['char-subscript', '.uno:SubScript'],
		];
		expected.forEach(function (pair) {
			const feature = ImpressEditorCatalog.getFeature(pair[0]);
			assert.ok(feature, pair[0]);
			assert.equal(feature?.kind, 'charTool');
			assert.equal(feature?.unocmd, pair[1]);
			assert.equal(feature?.row, 'charTool');
		});
		assert.equal(ImpressEditorCatalog.getFeature('char-highlight')?.dialog, 'highlightColor');
	});

	it('registers slide background and master pickers on common tab', function () {
		assert.equal(ImpressEditorCatalog.getFeature('slide-background')?.dialog, 'slideBackground');
		assert.equal(ImpressEditorCatalog.getFeature('slide-master')?.dialog, 'slideMaster');
		assert.equal(ImpressEditorCatalog.getFeature('slide-background')?.pickerDefault, '无');
		assert.equal(ImpressEditorCatalog.getFeature('slide-master')?.pickerDefault, '默认');
	});

	it('maps slide background options matching Android BACKGROUND_LABELS/COMMANDS', function () {
		const expectedLabels = [
			'无',
			'颜色',
			'渐变',
			'阴影线',
			'位图',
			'图案',
			'使用幻灯片背景',
		];
		assert.deepEqual(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS.map(function (entry) {
				return entry.label;
			}),
			expectedLabels,
		);
		assert.equal(ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS.length, 7);
		const fillStyles = ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS.filter(function (entry) {
			return entry.fillPageStyle !== undefined;
		}).map(function (entry) {
			return entry.fillPageStyle;
		});
		assert.deepEqual(fillStyles, [0, 1, 2, 3, 4]);
		assert.equal(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[0].unocmd,
			ImpressEditorCatalog.buildFillPageStyleCommand(0),
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[4].unocmd,
			'.uno:SelectBackground',
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[6].unocmd,
			'.uno:DisplayMasterBackground?DisplayMasterBackground:bool=true',
		);
		assert.equal(ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[1].afterSelect, 'colorPicker');
		assert.equal(ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[4].afterSelect, 'imagePicker');
	});

	it('maps master slide options matching Android MASTER_SLIDE_LABELS', function () {
		assert.deepEqual(
			ImpressEditorCatalog.SLIDE_MASTER_OPTIONS.map(function (entry) {
				return entry.label;
			}),
			['默认', '纯色'],
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_MASTER_OPTIONS[0].unocmd,
			ImpressEditorCatalog.buildFillPageStyleCommand(0),
		);
		assert.equal(ImpressEditorCatalog.SLIDE_MASTER_OPTIONS[1].unocmd, null);
		assert.equal(ImpressEditorCatalog.SLIDE_MASTER_OPTIONS[1].afterSelect, 'colorPicker');
	});
});
