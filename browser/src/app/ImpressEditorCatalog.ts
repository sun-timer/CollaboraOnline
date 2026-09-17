/*
 * Shared Impress document-editing feature catalog (iOS function panel).
 *
 * Mirrors Android ImpressFunctionPanelController tabs:
 * 常用 / 文件 / 插入 / 切换 / 布局 / 审阅.
 */

type ImpressEditorTab =
	| 'default'
	| 'file'
	| 'insert'
	| 'transition'
	| 'layout'
	| 'review';

type ImpressEditorFeatureKind =
	| 'section'
	| 'command'
	| 'queryCommand'
	| 'dialog'
	| 'charTool'
	| 'save'
	| 'export'
	| 'print'
	| 'findReplace'
	| 'stub';

type ImpressEditorDialogType =
	| 'image'
	| 'table'
	| 'shape'
	| 'chart'
	| 'saveAs'
	| 'comment'
	| 'hyperlink'
	| 'slideFormat'
	| 'slideOrientation'
	| 'slideBackground'
	| 'slideMaster'
	| 'fontName'
	| 'fontSize'
	| 'fontColor'
	| 'highlightColor';

type ImpressEditorSplitPart = 'size' | 'color';

interface ImpressEditorFeature {
	id: string;
	label: string;
	tab: ImpressEditorTab;
	icon: string;
	kind: ImpressEditorFeatureKind;
	unocmd?: string;
	queryParams?: string;
	dialog?: ImpressEditorDialogType;
	group?: string;
	setId?: string;
	iconViewIndex?: number;
	row?: 'picker' | 'chip' | 'layoutPreview' | 'splitPicker' | 'charTool';
	splitPart?: ImpressEditorSplitPart;
	pickerDefault?: string;
}

interface ImpressEditorTabDefinition {
	id: ImpressEditorTab;
	label: string;
}

interface ImpressEditorValidationResult {
	valid: boolean;
	errorCode?: string;
}

interface ImpressLayoutEntry {
	id: string;
	label: string;
	whatLayout: number;
	icon: string;
}

/** Android ImpressFunctionPanelController fill styles (FillPageStyle). */
type ImpressFillPageStyle = 0 | 1 | 2 | 3 | 4;

interface ImpressSlideBackgroundOption {
	label: string;
	unocmd: string | null;
	fillPageStyle?: ImpressFillPageStyle;
	afterSelect?: 'colorPicker' | 'imagePicker';
}

interface ImpressSlideMasterOption {
	label: string;
	unocmd: string | null;
	afterSelect?: 'colorPicker';
}

class ImpressEditorCatalog {
	static readonly TABS: ImpressEditorTabDefinition[] = [
		{ id: 'default', label: '常用' },
		{ id: 'file', label: '文件' },
		{ id: 'insert', label: '插入' },
		{ id: 'transition', label: '切换' },
		{ id: 'layout', label: '布局' },
		{ id: 'review', label: '审阅' },
	];

	/** Android ImpressSlideLayoutCatalog ENTRIES. */
	static readonly LAYOUTS: ImpressLayoutEntry[] = [
		{
			id: 'layout-title',
			label: '标题幻灯片',
			whatLayout: 0,
			icon: 'impress-layout-01-title',
		},
		{
			id: 'layout-title-content',
			label: '标题和内容',
			whatLayout: 1,
			icon: 'impress-layout-02-title-content',
		},
		{
			id: 'layout-section',
			label: '节标题',
			whatLayout: 2,
			icon: 'impress-layout-03-section',
		},
		{
			id: 'layout-two-content',
			label: '两栏内容',
			whatLayout: 3,
			icon: 'impress-layout-04-two-content',
		},
		{
			id: 'layout-compare',
			label: '比较',
			whatLayout: 15,
			icon: 'impress-layout-05-compare',
		},
		{
			id: 'layout-title-only',
			label: '仅标题',
			whatLayout: 19,
			icon: 'impress-layout-06-title-only',
		},
		{
			id: 'layout-blank',
			label: '空白',
			whatLayout: 20,
			icon: 'impress-layout-07-blank',
		},
		{
			id: 'layout-picture-title',
			label: '图片与标题',
			whatLayout: 12,
			icon: 'impress-layout-08-picture-title',
		},
		{
			id: 'layout-vertical',
			label: '竖排标题与文本',
			whatLayout: 28,
			icon: 'impress-layout-09-vertical',
		},
		{
			id: 'layout-content',
			label: '内容',
			whatLayout: 32,
			icon: 'impress-layout-10-content',
		},
		{
			id: 'layout-end',
			label: '末尾幻灯片',
			whatLayout: 19,
			icon: 'impress-layout-11-end',
		},
	];

	/** Android ImpressFunctionPanelController FORMAT_LABELS / FORMAT_COMMANDS. */
	static readonly SLIDE_FORMATS: {
		label: string;
		paperFormat?: string;
		pageSizeUno?: string;
	}[] = [
		{ label: 'A4', paperFormat: '4' },
		{ label: 'A3', paperFormat: '3' },
		{ label: 'A5', paperFormat: '5' },
		{ label: 'A6', paperFormat: '56' },
		{ label: 'A2', paperFormat: '2' },
		{ label: 'A1', paperFormat: '1' },
		{ label: 'A0', paperFormat: '0' },
		{ label: 'B6(ISO)', paperFormat: '12' },
		{ label: 'B5(ISO)', paperFormat: '7' },
		{ label: 'B4(ISO)', paperFormat: '6' },
		{ label: 'B6(JIS)', paperFormat: '36' },
		{ label: 'B5(JIS)', paperFormat: '35' },
		{ label: 'B4(JIS)', paperFormat: '34' },
		{ label: 'Letter', paperFormat: '8' },
		{ label: 'Legal', paperFormat: '9' },
		{ label: 'Tabloid', paperFormat: '10' },
		{ label: '16开', paperFormat: '31' },
		{ label: '32开', paperFormat: '32' },
		{ label: '大32开', paperFormat: '33' },
		{ label: '自定义', paperFormat: '11' },
	];

	/** Android ORIENTATION_LABELS / ORIENTATION_COMMANDS. */
	static readonly SLIDE_ORIENTATIONS: { label: string; unocmd: string }[] = [
		{ label: '横向', unocmd: '.uno:Orientation?isLandscape:bool=true' },
		{ label: '纵向', unocmd: '.uno:Orientation?isLandscape:bool=false' },
	];

	/** Android BACKGROUND_LABELS / BACKGROUND_COMMANDS + createBackgroundActions. */
	static readonly SLIDE_BACKGROUND_OPTIONS: ImpressSlideBackgroundOption[] = [
		{
			label: '无',
			unocmd: ImpressEditorCatalog.buildFillPageStyleCommand(0),
			fillPageStyle: 0,
		},
		{
			label: '颜色',
			unocmd: ImpressEditorCatalog.buildFillPageStyleCommand(1),
			fillPageStyle: 1,
			afterSelect: 'colorPicker',
		},
		{
			label: '渐变',
			unocmd: ImpressEditorCatalog.buildFillPageStyleCommand(2),
			fillPageStyle: 2,
		},
		{
			label: '阴影线',
			unocmd: ImpressEditorCatalog.buildFillPageStyleCommand(3),
			fillPageStyle: 3,
		},
		{
			label: '位图',
			unocmd: '.uno:SelectBackground',
			afterSelect: 'imagePicker',
		},
		{
			label: '图案',
			unocmd: ImpressEditorCatalog.buildFillPageStyleCommand(4),
			fillPageStyle: 4,
		},
		{
			label: '使用幻灯片背景',
			unocmd: '.uno:DisplayMasterBackground?DisplayMasterBackground:bool=true',
		},
	];

	/** Android MASTER_SLIDE_LABELS; 默认 uses FillPageStyle 0 on iOS (Android clears local state only). */
	static readonly SLIDE_MASTER_OPTIONS: ImpressSlideMasterOption[] = [
		{
			label: '默认',
			unocmd: ImpressEditorCatalog.buildFillPageStyleCommand(0),
		},
		{
			label: '纯色',
			unocmd: null,
			afterSelect: 'colorPicker',
		},
	];

	/** Android ImpressTransitionCatalog icon assets → WriterEditorIcons keys. */
	static transitionIconKey(iconViewIndex: number): string {
		const n = iconViewIndex + 1;
		const padded = n < 10 ? '00' + n : n < 100 ? '0' + n : String(n);
		return 'impress-transition-' + padded;
	}

	static buildFillPageStyleCommand(fillStyle: ImpressFillPageStyle): string {
		return (
			'.uno:FillPageStyle {"FillPageStyle":{"type":"short","value":' + fillStyle + '}}'
		);
	}

	static buildBackgroundColorCommand(rgb: number): string {
		return (
			'.uno:BackgroundColor {"BackgroundColor.Color":{"type":"long","value":' +
			rgb +
			'}}'
		);
	}

	static readonly TRANSITIONS: { id: string; label: string; setId: string; iconViewIndex: number }[] = [
		{ id: 'tr-none', label: '无', setId: '', iconViewIndex: 0 },
		{ id: 'tr-wipe', label: '擦除', setId: 'wipe', iconViewIndex: 1 },
		{ id: 'tr-wheel', label: '滚轮', setId: 'wheel', iconViewIndex: 2 },
		{ id: 'tr-uncover', label: '揭开', setId: 'uncover', iconViewIndex: 3 },
		{ id: 'tr-random-bars', label: '条形', setId: 'random-bars', iconViewIndex: 4 },
		{ id: 'tr-checkerboard', label: '棋盘', setId: 'checkerboard', iconViewIndex: 5 },
		{ id: 'tr-shape', label: '形状', setId: 'shape', iconViewIndex: 6 },
		{ id: 'tr-box', label: '框', setId: 'box', iconViewIndex: 7 },
		{ id: 'tr-wedge', label: '楔形', setId: 'wedge', iconViewIndex: 8 },
		{ id: 'tr-venetian', label: '百叶窗', setId: 'venetian-blinds', iconViewIndex: 9 },
		{ id: 'tr-fade', label: '淡入', setId: 'fade', iconViewIndex: 10 },
		{ id: 'tr-cut', label: '切入', setId: 'cut', iconViewIndex: 11 },
		{ id: 'tr-cover', label: '覆盖', setId: 'cover', iconViewIndex: 12 },
		{ id: 'tr-dissolve', label: '溶解', setId: 'dissolve', iconViewIndex: 13 },
		{ id: 'tr-random', label: '随机', setId: 'random', iconViewIndex: 14 },
		{ id: 'tr-comb', label: '梳动', setId: 'comb', iconViewIndex: 15 },
		{ id: 'tr-push', label: '推出', setId: 'push', iconViewIndex: 16 },
		{ id: 'tr-split', label: '拆分', setId: 'split', iconViewIndex: 17 },
		{ id: 'tr-diagonal', label: '斜角方块', setId: 'diagonal-squares', iconViewIndex: 18 },
		{ id: 'tr-tile', label: '磁贴', setId: 'tile-flip', iconViewIndex: 19 },
		{ id: 'tr-cube', label: '立方体', setId: 'cube-turning', iconViewIndex: 20 },
		{ id: 'tr-circles', label: '多重圆', setId: 'revolving-circles', iconViewIndex: 21 },
		{ id: 'tr-helix', label: '螺旋', setId: 'turning-helix', iconViewIndex: 22 },
		{ id: 'tr-fall', label: '跌落', setId: 'fall', iconViewIndex: 23 },
		{ id: 'tr-turn', label: '翻转', setId: 'turn-around', iconViewIndex: 24 },
		{ id: 'tr-iris', label: '光圈', setId: 'iris', iconViewIndex: 25 },
		{ id: 'tr-turn-down', label: '向下转', setId: 'turn-down', iconViewIndex: 26 },
		{ id: 'tr-rochade', label: '左右互换', setId: 'rochade', iconViewIndex: 27 },
		{ id: 'tr-venetian-3d', label: '3D百叶窗', setId: 'venetian-blinds-3d', iconViewIndex: 28 },
		{ id: 'tr-static', label: '静电干扰', setId: 'static', iconViewIndex: 29 },
		{ id: 'tr-fine-dissolve', label: '精细溶解', setId: 'finedissolve', iconViewIndex: 30 },
		{ id: 'tr-vortex', label: '漩涡', setId: 'vortex', iconViewIndex: 31 },
		{ id: 'tr-ripple', label: '涟漪', setId: 'ripple', iconViewIndex: 32 },
		{ id: 'tr-glitter', label: '闪耀', setId: 'glitter', iconViewIndex: 33 },
		{ id: 'tr-honeycomb', label: '蜂巢', setId: 'honeycomb', iconViewIndex: 34 },
		{ id: 'tr-newsflash', label: '新闻快讯', setId: 'newsflash', iconViewIndex: 35 },
	];

	private static readonly FEATURES: ImpressEditorFeature[] = ImpressEditorCatalog.buildFeatures();

	private static buildFeatures(): ImpressEditorFeature[] {
		const features: ImpressEditorFeature[] = [
			{
				id: 'sec-slide',
				label: '幻灯片',
				tab: 'default',
				icon: '',
				kind: 'section',
			},
			{
				id: 'slide-format',
				label: '格式',
				tab: 'default',
				icon: 'impress-slide-format',
				kind: 'dialog',
				dialog: 'slideFormat',
				row: 'picker',
				pickerDefault: 'A4',
				group: 'slide',
			},
			{
				id: 'slide-orientation',
				label: '方向',
				tab: 'default',
				icon: 'impress-slide-orientation',
				kind: 'dialog',
				dialog: 'slideOrientation',
				row: 'picker',
				pickerDefault: '横向',
				group: 'slide',
			},
			{
				id: 'slide-background',
				label: '背景',
				tab: 'default',
				icon: 'impress-slide-background',
				kind: 'dialog',
				dialog: 'slideBackground',
				row: 'picker',
				pickerDefault: '无',
				group: 'slide',
			},
			{
				id: 'slide-master',
				label: '母版幻灯片',
				tab: 'default',
				icon: 'impress-slide-master',
				kind: 'dialog',
				dialog: 'slideMaster',
				row: 'picker',
				pickerDefault: '默认',
				group: 'slide',
			},
			{
				id: 'sec-layout',
				label: '布局',
				tab: 'default',
				icon: '',
				kind: 'section',
			},
		];

		ImpressEditorCatalog.LAYOUTS.forEach((layout, index) => {
			if (index < 3) {
				features.push({
					id: 'common-' + layout.id,
					label: layout.label,
					tab: 'default',
					icon: layout.icon,
					kind: 'queryCommand',
					unocmd: '.uno:AssignLayout',
					queryParams: '?WhatLayout:long=' + layout.whatLayout,
					group: 'layout',
					row: 'layoutPreview',
				});
			}
		});

		features.push(
			{
				id: 'sec-char',
				label: '字符',
				tab: 'default',
				icon: '',
				kind: 'section',
			},
			{
				id: 'font-name',
				label: '字体',
				tab: 'default',
				icon: 'font',
				kind: 'dialog',
				dialog: 'fontName',
				unocmd: '.uno:CharFontName',
				row: 'picker',
				pickerDefault: '宋体',
				group: 'char',
			},
			{
				id: 'font-size',
				label: '字号',
				tab: 'default',
				icon: 'font-size',
				kind: 'dialog',
				dialog: 'fontSize',
				unocmd: '.uno:FontHeight',
				row: 'splitPicker',
				splitPart: 'size',
				pickerDefault: '四号',
				group: 'char',
			},
			{
				id: 'font-color',
				label: '字体颜色',
				tab: 'default',
				icon: 'font',
				kind: 'dialog',
				dialog: 'fontColor',
				row: 'splitPicker',
				splitPart: 'color',
				group: 'char',
			},
			{
				id: 'char-bold',
				label: '粗体',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:Bold',
				row: 'charTool',
				group: 'char-tools-1',
			},
			{
				id: 'char-italic',
				label: '斜体',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:Italic',
				row: 'charTool',
				group: 'char-tools-1',
			},
			{
				id: 'char-underline',
				label: '下划线',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:Underline',
				row: 'charTool',
				group: 'char-tools-1',
			},
			{
				id: 'char-strikeout',
				label: '删除线',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:Strikeout',
				row: 'charTool',
				group: 'char-tools-1',
			},
			{
				id: 'char-shadow',
				label: '阴影',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:Shadowed',
				row: 'charTool',
				group: 'char-tools-1',
			},
			{
				id: 'char-highlight',
				label: '高亮',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:CharBackColor',
				dialog: 'highlightColor',
				row: 'charTool',
				group: 'char-tools-1',
			},
			{
				id: 'char-superscript',
				label: '上标',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:SuperScript',
				row: 'charTool',
				group: 'char-tools-2',
			},
			{
				id: 'char-subscript',
				label: '下标',
				tab: 'default',
				icon: 'font',
				kind: 'charTool',
				unocmd: '.uno:SubScript',
				row: 'charTool',
				group: 'char-tools-2',
			},
			{
				id: 'sec-para',
				label: '段落',
				tab: 'default',
				icon: '',
				kind: 'section',
			},
			{
				id: 'para-left',
				label: '左对齐',
				tab: 'default',
				icon: 'align-left',
				kind: 'command',
				unocmd: '.uno:LeftPara',
				row: 'chip',
				group: 'para',
			},
			{
				id: 'para-center',
				label: '居中对齐',
				tab: 'default',
				icon: 'align-center',
				kind: 'command',
				unocmd: '.uno:CenterPara',
				row: 'chip',
				group: 'para',
			},
			{
				id: 'para-right',
				label: '右对齐',
				tab: 'default',
				icon: 'align-right',
				kind: 'command',
				unocmd: '.uno:RightPara',
				row: 'chip',
				group: 'para',
			},
			{
				id: 'para-justify',
				label: '两端对齐',
				tab: 'default',
				icon: 'align-justify',
				kind: 'command',
				unocmd: '.uno:JustifyPara',
				row: 'chip',
				group: 'para',
			},
			{
				id: 'para-bullet',
				label: '无序列表',
				tab: 'default',
				icon: 'impress-bullet-list',
				kind: 'command',
				unocmd: '.uno:DefaultBullet',
				row: 'chip',
				group: 'para',
			},
			{
				id: 'para-number',
				label: '有序列表',
				tab: 'default',
				icon: 'impress-number-list',
				kind: 'command',
				unocmd: '.uno:DefaultNumbering',
				row: 'chip',
				group: 'para',
			},
			{
				id: 'save',
				label: '保存',
				tab: 'file',
				icon: 'calc-file-save',
				kind: 'save',
				group: 'file',
			},
			{
				id: 'save-as',
				label: '另存为',
				tab: 'file',
				icon: 'impress-file-save-as',
				kind: 'dialog',
				dialog: 'saveAs',
				group: 'file',
			},
			{
				id: 'export-pdf',
				label: '导出为',
				tab: 'file',
				icon: 'calc-file-export',
				kind: 'export',
				group: 'file',
			},
			{
				id: 'print',
				label: '打印',
				tab: 'file',
				icon: 'calc-file-print',
				kind: 'print',
				group: 'file',
			},
			{
				id: 'insert-image',
				label: '本地图像',
				tab: 'insert',
				icon: 'impress-insert-local-image',
				kind: 'dialog',
				dialog: 'image',
				group: 'insert',
			},
			{
				id: 'insert-chart',
				label: '图表',
				tab: 'insert',
				icon: 'impress-insert-chart',
				kind: 'dialog',
				dialog: 'chart',
				group: 'insert',
			},
			{
				id: 'insert-comment',
				label: '批注',
				tab: 'insert',
				icon: 'impress-insert-comment',
				kind: 'dialog',
				dialog: 'comment',
				unocmd: '.uno:InsertAnnotation',
				group: 'insert',
			},
			{
				id: 'insert-table',
				label: '表格',
				tab: 'insert',
				icon: 'impress-insert-table',
				kind: 'dialog',
				dialog: 'table',
				unocmd: '.uno:InsertTable',
				group: 'insert',
			},
			{
				id: 'insert-hyperlink',
				label: '超链接',
				tab: 'insert',
				icon: 'impress-insert-hyperlink',
				kind: 'dialog',
				dialog: 'hyperlink',
				unocmd: '.uno:SetHyperlink',
				group: 'insert',
			},
			{
				id: 'insert-shape',
				label: '形状',
				tab: 'insert',
				icon: 'impress-insert-shape',
				kind: 'dialog',
				dialog: 'shape',
				group: 'insert',
			},
			{
				id: 'insert-textbox',
				label: '文本框',
				tab: 'insert',
				icon: 'impress-insert-textbox',
				kind: 'command',
				unocmd: '.uno:DrawText',
				group: 'insert',
			},
			{
				id: 'insert-more-fields',
				label: '更多字段',
				tab: 'insert',
				icon: 'impress-insert-more-fields',
				kind: 'stub',
				group: 'insert',
			},
			{
				id: 'find-replace',
				label: '查找替换',
				tab: 'review',
				icon: 'find-replace',
				kind: 'findReplace',
				group: 'review',
			},
			{
				id: 'spell-check',
				label: '拼写检查',
				tab: 'review',
				icon: 'impress-spell-check',
				kind: 'command',
				unocmd: '.uno:SpellDialog',
				group: 'review',
			},
			{
				id: 'review-comment',
				label: '批注',
				tab: 'review',
				icon: 'impress-review-comment',
				kind: 'dialog',
				dialog: 'comment',
				unocmd: '.uno:InsertAnnotation',
				group: 'review',
			},
		);

		ImpressEditorCatalog.LAYOUTS.forEach((layout) => {
			features.push({
				id: layout.id,
				label: layout.label,
				tab: 'layout',
				icon: layout.icon,
				kind: 'queryCommand',
				unocmd: '.uno:AssignLayout',
				queryParams: '?WhatLayout:long=' + layout.whatLayout,
				group: 'layout',
			});
		});

		ImpressEditorCatalog.TRANSITIONS.forEach((item) => {
			features.push({
				id: item.id,
				label: item.label,
				tab: 'transition',
				icon: ImpressEditorCatalog.transitionIconKey(item.iconViewIndex),
				kind: 'command',
				unocmd: '.uno:SlideChangeWindow',
				setId: item.setId,
				iconViewIndex: item.iconViewIndex,
				group: 'transition',
			});
		});
		return features;
	}

	static getFeatures(tab: ImpressEditorTab): ImpressEditorFeature[] {
		return ImpressEditorCatalog.FEATURES.filter((feature) => feature.tab === tab);
	}

	static getFeature(id: string): ImpressEditorFeature | null {
		return ImpressEditorCatalog.FEATURES.find((feature) => feature.id === id) || null;
	}

	static validateFeature(feature: ImpressEditorFeature): ImpressEditorValidationResult {
		if (!feature.id) {
			return { valid: false, errorCode: 'empty_id' };
		}
		if (!feature.icon && feature.kind !== 'section') {
			return { valid: false, errorCode: 'empty_icon' };
		}
		if (feature.kind === 'stub') {
			return { valid: true };
		}
		if (
			(feature.kind === 'command' ||
				feature.kind === 'queryCommand' ||
				feature.kind === 'charTool') &&
			!feature.unocmd
		) {
			return { valid: false, errorCode: 'missing_unocmd' };
		}
		return { valid: true };
	}

	static validateRegistry(): ImpressEditorValidationResult {
		for (let i = 0; i < ImpressEditorCatalog.FEATURES.length; i++) {
			const result = ImpressEditorCatalog.validateFeature(
				ImpressEditorCatalog.FEATURES[i],
			);
			if (!result.valid) {
				return result;
			}
		}
		return { valid: true };
	}
}
