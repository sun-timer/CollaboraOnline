/* -*- js-indent-level: 8 -*- */
/* eslint-disable @typescript-eslint/no-var-requires -- mocha main.ts concatenates global test scripts. */
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
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-image')?.dialog,
			'image',
		);
		assert.equal(ImpressEditorCatalog.TRANSITIONS.length, 36);
		assert.equal(ImpressEditorCatalog.getFeatures('transition').length, 36);
		assert.equal(ImpressEditorCatalog.TRANSITIONS[0].iconViewIndex, 0);
		assert.equal(ImpressEditorCatalog.TRANSITIONS[35].setId, 'newsflash');
		assert.equal(ImpressEditorCatalog.getFeature('tr-wipe')?.iconViewIndex, 1);
	});

	it('wires insert table, shape, hyperlink, and comment from Android catalogs', function () {
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-table')?.dialog,
			'table',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-table')?.unocmd,
			'.uno:InsertTable',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-shape')?.dialog,
			'shape',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-hyperlink')?.dialog,
			'hyperlink',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-hyperlink')?.unocmd,
			'.uno:SetHyperlink',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-comment')?.dialog,
			'comment',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-comment')?.unocmd,
			'.uno:InsertAnnotation',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('review-comment')?.dialog,
			'commentList',
		);
		assert.equal(ImpressEditorCatalog.getFeature('review-comment')?.label, '批注');
		assert.equal(
			ImpressEditorCatalog.getFeature('review-comment')?.unocmd,
			undefined,
		);
	});

	it('removes local image from common tab but keeps insert tab image', function () {
		assert.equal(ImpressEditorCatalog.getFeature('common-insert-image'), null);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-image')?.tab,
			'insert',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('insert-image')?.dialog,
			'image',
		);
		const defaultIds = ImpressEditorCatalog.getFeatures('default').map(
			function (f) {
				return f.id;
			},
		);
		assert.equal(defaultIds.indexOf('common-insert-image'), -1);
	});

	it('lists the 20 Android paper formats with A4 first and PaperFormat:short commands', function () {
		assert.equal(
			ImpressEditorCatalog.getFeature('slide-format')?.dialog,
			'slideFormat',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('slide-orientation')?.dialog,
			'slideOrientation',
		);
		assert.deepEqual(
			ImpressEditorCatalog.SLIDE_FORMATS.map(function (entry) {
				return entry.label;
			}),
			[
				'A4',
				'A3',
				'A5',
				'A6',
				'A2',
				'A1',
				'A0',
				'B6(ISO)',
				'B5(ISO)',
				'B4(ISO)',
				'B6(JIS)',
				'B5(JIS)',
				'B4(JIS)',
				'Letter',
				'Legal',
				'Tabloid',
				'16开',
				'32开',
				'大32开',
				'自定义',
			],
		);
		assert.equal(ImpressEditorCatalog.SLIDE_FORMATS.length, 20);
		assert.equal(ImpressEditorCatalog.SLIDE_FORMATS[0].paperFormat, '4');
		assert.equal(
			ImpressEditorCatalog.paperFormatCommand(
				ImpressEditorCatalog.SLIDE_FORMATS[0].paperFormat,
			),
			'.uno:AttributePageSize?PaperFormat:short=4',
		);
		assert.equal(
			ImpressEditorCatalog.paperFormatCommand('56'),
			'.uno:AttributePageSize?PaperFormat:short=56',
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_FORMATS[19].paperFormat,
			ImpressEditorCatalog.CUSTOM_PAPER_FORMAT,
		);
		const letter = ImpressEditorCatalog.SLIDE_FORMATS.find(
			(entry) => entry.label === 'Letter',
		);
		assert.equal(letter?.paperFormat, '8');
		assert.equal(letter?.width, 21590);
		assert.equal(letter?.height, 27940);
		assert.equal(
			ImpressEditorCatalog.SLIDE_ORIENTATIONS[0].unocmd,
			'.uno:AttributePage?AttributePage.Landscape:bool=true',
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_ORIENTATIONS[1].unocmd,
			'.uno:AttributePage?AttributePage.Landscape:bool=false',
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
		const commonLayouts = ImpressEditorCatalog.getFeatures('default').filter(
			function (f) {
				return f.row === 'layoutPreview';
			},
		);
		assert.equal(commonLayouts.length, 3);
		assert.equal(commonLayouts[0].id, 'common-layout-title');
		assert.equal(commonLayouts[2].id, 'common-layout-section');
		const layoutTab = ImpressEditorCatalog.getFeatures('layout');
		assert.equal(layoutTab.length, 11);
		assert.equal(layoutTab[0].id, 'layout-title');
		assert.equal(layoutTab[10].id, 'layout-end');
		const defaultIds = ImpressEditorCatalog.getFeatures('default').map(
			function (f) {
				return f.id;
			},
		);
		assert.equal(
			defaultIds.indexOf('sec-layout') + 1,
			defaultIds.indexOf('common-layout-title'),
		);
		assert.ok(
			defaultIds.indexOf('common-layout-section') <
				defaultIds.indexOf('sec-char'),
		);
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
		const defaultIds = ImpressEditorCatalog.getFeatures('default').map(
			function (f) {
				return f.id;
			},
		);
		const charSection = defaultIds.indexOf('sec-char');
		const paraSection = defaultIds.indexOf('sec-para');
		assert.ok(charSection >= 0 && paraSection > charSection);
		['font-name', 'font-size', 'font-color'].forEach(function (id) {
			const index = defaultIds.indexOf(id);
			assert.ok(index > charSection && index < paraSection, id);
		});
		assert.equal(
			ImpressEditorCatalog.getFeature('font-name')?.dialog,
			'fontName',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('font-size')?.dialog,
			'fontSize',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('font-color')?.dialog,
			'fontColor',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('font-name')?.pickerDefault,
			'宋体',
		);
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
		assert.equal(
			ImpressEditorCatalog.getFeature('char-highlight')?.dialog,
			'highlightColor',
		);
	});

	it('registers slide background and master pickers on common tab', function () {
		assert.equal(
			ImpressEditorCatalog.getFeature('slide-background')?.dialog,
			'slideBackground',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('slide-master')?.dialog,
			'slideMaster',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('slide-background')?.pickerDefault,
			'无',
		);
		assert.equal(
			ImpressEditorCatalog.getFeature('slide-master')?.pickerDefault,
			'默认',
		);
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
		assert.deepEqual(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS.map(function (entry) {
				return entry.kind;
			}),
			['none', 'color', 'gradient', 'hatch', 'bitmap', 'pattern', 'master'],
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[1].afterSelect,
			'colorPicker',
		);
		assert.equal(
			ImpressEditorCatalog.SLIDE_BACKGROUND_OPTIONS[4].afterSelect,
			'imagePicker',
		);
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
		assert.equal(
			ImpressEditorCatalog.SLIDE_MASTER_OPTIONS[1].afterSelect,
			'colorPicker',
		);
	});
});

describe('Impress editor interaction regressions', function () {
	let originalWindow: any;
	let originalDocument: any;
	let dom: any;
	let commands: string[];
	let unoCalls: Array<{ command: string; args?: any }>;
	let listeners: Record<string, Array<(event: any) => void>>;

	beforeEach(function () {
		const JSDOM = require('jsdom').JSDOM;
		dom = new JSDOM('<!doctype html><html><body></body></html>', {
			pretendToBeVisual: true,
		});
		originalWindow = (global as any).window;
		originalDocument = (global as any).document;
		(global as any).window = dom.window;
		(global as any).document = dom.window.document;
		commands = [];
		unoCalls = [];
		listeners = {};
		(dom.window as any).app = {
			socket: { sendMessage: (message: string) => commands.push(message) },
			map: {
				getDocType: () => 'presentation',
				isEditMode: () => true,
				sendUnoCommand: (command: string, args?: any) => {
					commands.push(command);
					unoCalls.push({ command, args });
				},
				_docLayer: { _partWidthTwips: 15874, _partHeightTwips: 8929, _viewId: 2, _selectedPart: 4 },
				sidebar: { setupTargetDeck: (command: string) => commands.push('deck:' + command) },
				stateChangeHandler: {
					getItemValue: (command: string) =>
						(
							({
								'.uno:FontHeight': '24',
								'.uno:Color': 0xff0000,
							}) as Record<string, any>
						)[command],
				},
				on: (name: string, fn: (event: any) => void) =>
					(listeners[name] ||= []).push(fn),
				off: (name: string, fn: (event: any) => void) => {
					listeners[name] = (listeners[name] || []).filter(
						(item) => item !== fn,
					);
				},
			},
		};
	});

	afterEach(function () {
		dom.window.close();
		(global as any).window = originalWindow;
		(global as any).document = originalDocument;
	});

	it('shows actual font state and hides tabs on nested color pages', function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		assert.equal(panel.pickerLabels['font-size'], '小一');
		assert.equal(panel.fontColorRgb, 0xff0000);
		panel.openFontColorDialog();
		assert.ok(
			dom.window.document.querySelector(
				'.writer-function-tab-bar.writer-function-subpage-host__hidden',
			),
		);
		panel.close();
		assert.equal(listeners.commandstatechanged.length, 0);
	});

	it('opens hyperlink inside the function panel and provides a return action', function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		panel.onFeature(ImpressEditorCatalog.getFeature('insert-hyperlink'));
		assert.equal(
			dom.window.document.querySelectorAll('.mobile-ai-sheet').length,
			0,
		);
		assert.equal(
			dom.window.document.querySelector(
				'.writer-function-inline-subpage__frame h2',
			).textContent,
			'插入超链接',
		);
		dom.window.document.querySelector('button[aria-label="返回"]').click();
		assert.equal(
			dom.window.document.querySelectorAll('.writer-function-inline-subpage')
				.length,
			0,
		);
		panel.close();
	});

	it('dismisses the Impress panel before opening the native comment list', function () {
		const panel = new (ImpressEditorPanel as any)();
		const openedWithPanelVisible: boolean[] = [];
		(dom.window as any).__coolOpenCommentList = () => {
			openedWithPanelVisible.push(
				!!dom.window.document.querySelector('.writer-function-sheet'),
			);
		};
		panel.open();
		panel.onFeature(ImpressEditorCatalog.getFeature('review-comment'));
		assert.deepEqual(openedWithPanelVisible, [false]);
	});

	it('sends a numeric font size and refreshes the selected size label', async function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		panel.openFontSizeDialog();
		dom.window.document.querySelector('button[aria-label="四号"]').click();
		await new Promise((resolve) => dom.window.setTimeout(resolve, 5));
		assert.equal(
			commands[0],
			'.uno:FontHeight {"FontHeight.Height":{"type":"float","value":"14"}}',
		);
		assert.equal(panel.pickerLabels['font-size'], '四号');
		panel.close();
	});

	it('swaps actual slide dimensions when choosing portrait', async function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		panel.openSlideOrientationDialog();
		dom.window.document.querySelector('button[aria-label="纵向"]').click();
		await new Promise((resolve) => dom.window.setTimeout(resolve, 5));
		const size = JSON.parse(
			commands[0].slice('.uno:AttributePageSize '.length),
		);
		assert.ok(
			size['AttributePageSize.Width'].value <
				size['AttributePageSize.Height'].value,
		);
		assert.equal(
			commands[1],
			'.uno:AttributePage?AttributePage.Landscape:bool=false',
		);
		panel.close();
	});

	it('keeps Impress row and column counts distinct when inserting a table', function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		panel.onFeature(ImpressEditorCatalog.getFeature('insert-table'));
		dom.window.document.querySelector('button[aria-label="增加行"]').click();
		const buttons = Array.from(
			dom.window.document.querySelectorAll('button'),
		) as HTMLButtonElement[];
		buttons.find((button) => button.textContent === '插入表格')?.click();
		assert.equal(
			commands[0],
			'.uno:InsertTable {"Columns":{"type":"long","value":2},"Rows":{"type":"long","value":3}}',
		);
		panel.close();
	});

	it('leaves a newly opened subpage in place after choosing an option', async function () {
		const anchor = dom.window.document.createElement('div');
		dom.window.document.body.appendChild(anchor);
		const host = new WriterEditorInlineSubpage(anchor, []);
		const picker = new WriterEditorChooseDialog(
			'一级',
			[{ label: '下一步', value: 'next' }],
			() => host.pushSubpage('二级', dom.window.document.createElement('div')),
			undefined,
			host,
		);
		picker.open();
		anchor.querySelector('.writer-editor-option-row').click();
		await new Promise((resolve) => dom.window.setTimeout(resolve, 5));
		assert.equal(anchor.querySelector('h2').textContent, '二级');
	});

	it('applies a gradient with the page gradient command', function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		panel.openSlideBackgroundDialog();
		dom.window.document.querySelector('button[aria-label="渐变"]').click();
		assert.equal(unoCalls.length, 1);
		assert.ok(String(unoCalls[0].command).startsWith('.uno:FillPageGradient '));
		assert.equal(panel.pickerLabels['slide-background'], '渐变');
		panel.close();
	});

	it('asks the photo picker for 位图 and paints 图案 as a background image', function () {
		const messages: string[] = [];
		(dom.window as any).postMobileMessage = (message: string) => messages.push(message);
		assert.equal(ImpressBackgroundBridge.apply('bitmap'), true);
		assert.ok(messages[0].startsWith('IMPRESS_BACKGROUND_PICK_IMAGE '));
		assert.equal(ImpressBackgroundBridge.apply('pattern'), true);
		assert.ok(messages[1].startsWith('insertfile name=slide_background.png type=selectbackground data='));
	});

	it('clears the page fill natively for 使用幻灯片背景', function () {
		const messages: string[] = [];
		(dom.window as any).postMobileMessage = (message: string) => messages.push(message);
		(dom.window as any).NativeBridge = { getInstance: () => ({ getDocumentSessionId: () => 'session' }) };
		let sent: boolean | undefined;
		ImpressBackgroundBridge.applyMasterBackground((value) => (sent = value));
		assert.equal(sent, true);
		assert.equal(unoCalls.length, 0);
		assert.ok(messages[0].startsWith('IMPRESS_MASTER_BACKGROUND '));
		assert.equal(JSON.parse(messages[0].substring('IMPRESS_MASTER_BACKGROUND '.length)).visible, true);
	});

	it('applies a slide background color with FillColor.Color', function () {
		let sent: boolean | undefined;
		ImpressBackgroundBridge.applyColor(0x123456, (value) => (sent = value));
		assert.equal(sent, true);
		assert.deepEqual(unoCalls.map((call) => call.command), [
			'.uno:FillPageColor {"FillColor.Color":{"type":"long","value":1193046}}',
		]);
	});

	it('marks the picked background swatch once the color command is sent', async function () {
		const panel = new (ImpressEditorPanel as any)();
		panel.open();
		panel.openSlideBackgroundDialog();
		dom.window.document.querySelector('button[aria-label="颜色"]').click();
		const previous = dom.window.document.querySelector('button[aria-label="颜色 #FFFFFF"] .writer-function-color-picker__check');
		const candidate = dom.window.document.querySelector('button[aria-label="颜色 #000000"] .writer-function-color-picker__check');
		assert.equal(previous.hidden, false);
		assert.equal(candidate.hidden, true);
		(dom.window.document.querySelector('button[aria-label="颜色 #000000"]') as HTMLButtonElement).click();
		assert.equal(candidate.hidden, false);
		await Promise.resolve();
		await Promise.resolve();
		assert.equal(candidate.hidden, false);
		panel.close();
	});

	it('dismisses the panel before sending the text box command', async function () {
		const panel = new (ImpressEditorPanel as any)();
		const focusCalls: boolean[] = [];
		(dom.window as any).app.map.focus = (acceptInput?: boolean) => {
			focusCalls.push(!!acceptInput);
		};
		panel.open();
		panel.sendUno = (command: string) =>
			commands.push(
				(panel.sheet.root.isConnected ? 'panel-open:' : 'panel-closed:') +
					command,
			);
		panel.onFeature(ImpressEditorCatalog.getFeature('insert-textbox'));
		await new Promise((resolve) => dom.window.setTimeout(resolve, 5));
		assert.deepEqual(commands, ['panel-closed:.uno:Text?CreateDirectly:bool=true']);
		assert.deepEqual(focusCalls, []);
	});

	it('does not activate transition presets before the populated Core icon view arrives', async function () {
		ImpressTransitionBridge.apply(2, false);
		assert.deepEqual(commands, [
			'uno .uno:SidebarShow',
			'uno .uno:SlideChangeWindow',
		]);
		const emit = (entries: any[]) =>
			listeners.mobilewizard.slice().forEach((fn) =>
				fn({
					data: {
						id: -1,
						children: [{ id: 'transitions_icons', type: 'iconview', entries }],
					},
				}),
			);
		emit([]);
		assert.equal(commands.length, 2);
		emit([{ row: 0 }, { row: 1 }]);
		assert.equal(commands.length, 2);
		emit([{ row: 0 }, { row: 1 }, { row: 2 }]);
		await new Promise((resolve) => dom.window.setTimeout(resolve, 5));
		assert.equal(commands.length, 4);
		assert.equal(
			commands[2],
			'dialogevent -1 {"id":"transitions_icons","cmd":"select","data":"2","type":"iconview"}',
		);
		assert.equal(
			commands[3],
			'dialogevent -1 {"id":"transitions_icons","cmd":"activate","data":"2","type":"iconview"}',
		);
		assert.equal(listeners.mobilewizard.length, 0);
	});

	it('keeps unmatched messages on the original route and permits retry after missing presets', function () {
		const timers: Array<{ run: () => void; delay: number }> = [];
		dom.window.setTimeout = (run: () => void, delay: number) => {
			timers.push({ run, delay });
			return timers.length;
		};
		ImpressTransitionBridge.apply(2, false);
		assert.equal(
			ImpressTransitionBridge.handlePayload({ id: 9, children: [] }),
			false,
		);
		assert.equal(
			ImpressTransitionBridge.handlePayload({
				id: -1,
				control: {
					id: 'transitions_icons',
					type: 'iconview',
					entries: [],
				},
			}),
			true,
		);
		timers.find((timer) => timer.delay === 6000)?.run();
		assert.equal(commands.length, 2);
		assert.equal(
			ImpressTransitionBridge.handlePayload({
				id: -1,
				control: {
					id: 'transitions_icons',
					type: 'iconview',
					entries: [{ row: 2 }],
				},
			}),
			false,
		);
		ImpressTransitionBridge.apply(2, false);
		assert.equal(
			ImpressTransitionBridge.handlePayload({
				id: -1,
				control: {
					id: 'transitions_icons',
					type: 'iconview',
					entries: [{ row: 2 }],
				},
			}),
			true,
		);
		timers.find((timer) => timer.delay === 0)?.run();
		assert.equal(commands.length, 6);
		assert.ok(commands[4].indexOf('"cmd":"select"') > 0);
		assert.ok(commands[5].indexOf('"cmd":"activate"') > 0);
	});
});
