/*
 * Shared Writer document-editing feature catalog.
 *
 * This is the Browser-side public contract for the iOS Writer function panel.
 * It mirrors the WriterAiCatalog pattern: pure data + validation, no DOM, no
 * network. Native/UNO wiring lives in WriterEditorController's adapter.
 */

type WriterEditorTab = 'default' | 'file' | 'insert' | 'layout' | 'review';

type WriterEditorFeatureKind =
	| 'command' // .uno:LeftPara (no args)
	| 'queryCommand' // .uno:StyleApply?Style=..&FamilyName=..
	| 'commandWithArgs' // .uno:InsertTable?Columns=..&Rows=..
	| 'dialog' // opens a secondary dialog (watermark / margins / paper size)
	| 'findReplace' // opens the find/replace layer
	| 'save' // .uno:Save special
	| 'export' // app.map.downloadAs('pdf')
	| 'print'; // app.map.downloadAs('print')

type WriterEditorDialogType =
	| 'fontName' // CO font list
	| 'fontSize' // Chinese size table (初号..小五)
	| 'style' // paragraph style picker
	| 'table' // InsertTable Columns x Rows
	| 'image' // insert image (file picker)
	| 'shape' // basic shapes picker
	| 'watermark' // text / angle / transparency
	| 'margins' // page LR / UL margins
	| 'paperSize' // AttributePageSize
	| 'saveAs' // save-as dialog
	| 'pageBreak' // page break options
	| 'pageNumber' // insert page number field
	| 'trackChanges' // enable / disable change tracking
	| 'chart'; // insert chart (InsertObjectChart type picker)

interface WriterEditorFeature {
	id: string;
	label: string;
	tab: WriterEditorTab;
	icon: string;
	kind: WriterEditorFeatureKind;
	unocmd?: string;
	queryParams?: string;
	args?: { [key: string]: any };
	dialog?: WriterEditorDialogType;
	needsSelection?: boolean;
	group?: string;
}

interface WriterEditorTabDefinition {
	id: WriterEditorTab;
	label: string;
}

interface WriterEditorValidationResult {
	valid: boolean;
	errorCode?: string;
}

/**
 * Chinese font size table for Writer (初号..小五), mapped to the point value
 * used by the `.uno:CharHeight` command.
 */
const WriterEditorCharHeightCN: { [label: string]: string } = {
	'初号': '42pt',
	'小初': '36pt',
	'一号': '26pt',
	'小一': '24pt',
	'二号': '22pt',
	'小二': '18pt',
	'三号': '16pt',
	'小三': '15pt',
	'四号': '14pt',
	'小四': '12pt',
	'五号': '10.5pt',
	'小五': '9pt',
};

class WriterEditorCatalog {
	static readonly TABS: WriterEditorTabDefinition[] = [
		{ id: 'default', label: '常用' },
		{ id: 'file', label: '文件' },
		{ id: 'insert', label: '插入' },
		{ id: 'layout', label: '布局' },
		{ id: 'review', label: '审阅' },
	];

	static readonly CHAR_HEIGHT_CN: { [label: string]: string } =
		WriterEditorCharHeightCN;
	/**
	 * Paper-format presets for `.uno:AttributePageSize?PaperFormat:short=N`.
	 * All 29 entries transcribe CO's PaperFormat enum (A4=4, A3=3, ...) via
	 * Android WriterLayoutCatalog.PAPER_SIZES (L63-93); labels match Android.
	 */
	static readonly PAPER_FORMATS: { label: string; value: string }[] = [
		{ label: 'A6', value: '56' },
		{ label: 'A5', value: '5' },
		{ label: 'A4', value: '4' },
		{ label: 'A3', value: '3' },
		{ label: 'B6（ISO）', value: '12' },
		{ label: 'B5（ISO）', value: '7' },
		{ label: 'B4（ISO）', value: '6' },
		{ label: '信纸', value: '8' },
		{ label: '法律专用纸', value: '9' },
		{ label: '长债券纸', value: '24' },
		{ label: '小报', value: '10' },
		{ label: 'B6（JIS）', value: '36' },
		{ label: 'B5（JIS）', value: '35' },
		{ label: 'B4（JIS）', value: '34' },
		{ label: '16开', value: '31' },
		{ label: '32开', value: '32' },
		{ label: '大32开', value: '33' },
		{ label: 'DL 信封', value: '17' },
		{ label: 'C6 信封', value: '15' },
		{ label: 'C6/5 信封', value: '16' },
		{ label: 'C5 信封', value: '14' },
		{ label: 'C4 信封', value: '13' },
		{ label: '6¾ 号信封', value: '26' },
		{ label: '7¾ 号信封', value: '25' },
		{ label: '9 号信封', value: '27' },
		{ label: '10 号信封', value: '28' },
		{ label: '11 号信封', value: '29' },
		{ label: '12 号信封', value: '30' },
		{ label: '日本明信片', value: '46' },
	];

	/**
	 * Page-margin presets (hundredths of mm) dispatched via
	 * `.uno:PageLRMargin` / `.uno:PageULMargin`. Transcribes Android
	 * WriterLayoutCatalog.MARGINS (L44-60, CO pageMarginOptions + Figma
	 * 5252:57102); field order follows applyMargins(left, right, top, bottom).
	 */
	static readonly MARGIN_PRESETS: {
		id: string;
		label: string;
		left: number;
		right: number;
		top: number;
		bottom: number;
	}[] = [
		{ id: 'none', label: '无', left: 0, right: 0, top: 0, bottom: 0 },
		{ id: 'narrow', label: '窄', left: 1270, right: 1270, top: 1270, bottom: 1270 },
		{ id: 'moderate', label: '适中', left: 1905, right: 1905, top: 2540, bottom: 2540 },
		{ id: 'normal190', label: '正常（1.90 cm）', left: 1905, right: 1905, top: 1905, bottom: 1905 },
		{ id: 'normal254', label: '正常（2.54 cm）', left: 2540, right: 2540, top: 2540, bottom: 2540 },
		{ id: 'normal318', label: '正常（3.18 cm）', left: 3175, right: 3175, top: 3175, bottom: 3175 },
		{ id: 'wide', label: '宽', left: 5080, right: 5080, top: 2540, bottom: 2540 },
		{ id: 'mirrored', label: '镜像', left: 5080, right: 2540, top: 2540, bottom: 2540 },
	];

	/**
	 * Paragraph-style picker ordering (Figma 5252:56110): the preferred styles
	 * surface first with Chinese labels, everything else follows in CO order.
	 * Transcribes Android FunctionPanelController.STYLE_ORDER (L1772-1781).
	 */
	static readonly STYLE_ORDER: { label: string; styleId: string }[] = [
		{ label: '正文', styleId: 'Default Paragraph Style' },
		{ label: '列表', styleId: 'List' },
		{ label: '题注', styleId: 'Caption' },
		{ label: '索引', styleId: 'Index' },
		{ label: '标题1', styleId: 'Heading 1' },
		{ label: '标题2', styleId: 'Heading 2' },
		{ label: '标题3', styleId: 'Heading 3' },
		{ label: 'caption', styleId: 'caption' },
	];

	/**
	 * Common Windows font names → CO-bundled substitutes, applied before the
	 * CharFontName dispatch so what the user picks is what core renders.
	 * Transcribes Android FunctionPanelController.FONT_ALIAS_MAP (L1076-1080).
	 */
	static readonly FONT_ALIAS_MAP: { from: string; to: string }[] = [
		{ from: 'Times New Roman', to: 'Liberation Serif' },
		{ from: 'Arial', to: 'Liberation Sans' },
		{ from: 'Courier New', to: 'Liberation Mono' },
	];

	/**
	 * Fallback font options when `.uno:CharFontName` returns no list.
	 * Transcribes Android FunctionPanelController.FALLBACK_FONT_OPTIONS
	 * (L2124-2126).
	 */
	static readonly FONT_FALLBACK_OPTIONS: string[] = [
		'Liberation Serif', 'Liberation Sans', 'Liberation Mono', 'Arial', 'Times New Roman',
	];

	/**
	 * Chart-type picker sections for `.uno:InsertObjectChart`.
	 * Transcribes Android ChartTypePickerUi.TYPE_ROWS (L52-67, Figma
	 * 258:10319); labels match Android.
	 */
	static readonly CHART_CATEGORIES: {
		title: string;
		types: { label: string; unoType: string }[];
	}[] = [
		{
			title: '饼图',
			types: [
				{ label: '基础饼图', unoType: 'pie' },
				{ label: '基础饼图(圆角)', unoType: 'pie-rounded' },
				{ label: '变形饼图', unoType: 'pie-exploded' },
			],
		},
		{
			title: '线图',
			types: [
				{ label: '折线图', unoType: 'line' },
				{ label: '曲线折线图', unoType: 'line-curve' },
			],
		},
		{
			title: '柱图',
			types: [
				{ label: '基础柱状图', unoType: 'column' },
				{ label: '基础条形图', unoType: 'bar' },
				{ label: '堆叠柱状图', unoType: 'column-stacked' },
			],
		},
	];

	/**
	 * Shared 36-color palette (24 + 12 blocks, 0xRRGGBB) for the font-color /
	 * highlight pickers. Transcribes Android CalcFontColorCatalog.BLOCKS
	 * (script-generated from Figma).
	 */
	static readonly CHAR_COLOR_BLOCKS: number[][] = [
		[
			0x8ACFFF, 0xD596FF, 0xBEFFC6, 0xFFC891, 0xFFE4E5, 0xFFFFFF,
			0x009CFF, 0xA628FF, 0x00FF47, 0xFFC700, 0xE65D61, 0xC0C0C0,
			0x0000FF, 0x7000D5, 0x89CD00, 0xFF9300, 0xA62900, 0x808080,
			0x010086, 0x390069, 0x008200, 0xFF5700, 0x8C0000, 0x000000,
		],
		[
			0xD20000, 0xFFBD00, 0x7ED330, 0x00B3F7, 0x792BA6, 0xFFFFFF,
			0xFF0000, 0xFFFF00, 0x00B242, 0x0073C7, 0x002164, 0x000000,
		],
	];

	static readonly FEATURES: WriterEditorFeature[] = [
		// ---- 常用 (default) ----
		{
			id: 'undo',
			label: '撤销',
			tab: 'default',
			icon: 'undo',
			kind: 'command',
			unocmd: '.uno:Undo',
			group: 'history',
		},
		{
			id: 'redo',
			label: '重做',
			tab: 'default',
			icon: 'redo',
			kind: 'command',
			unocmd: '.uno:Redo',
			group: 'history',
		},
		{
			id: 'font-name',
			label: '字体',
			tab: 'default',
			icon: 'font',
			kind: 'dialog',
			dialog: 'fontName',
			unocmd: '.uno:CharFontName',
			group: 'format',
		},
		{
			id: 'font-size',
			label: '字号',
			tab: 'default',
			icon: 'font-size',
			kind: 'dialog',
			dialog: 'fontSize',
			unocmd: '.uno:CharHeight',
			group: 'format',
		},
		{
			id: 'style',
			label: '样式',
			tab: 'default',
			icon: 'style',
			kind: 'dialog',
			dialog: 'style',
			unocmd: '.uno:StyleApply',
			group: 'format',
		},
		{
			id: 'align-left',
			label: '左对齐',
			tab: 'default',
			icon: 'align-left',
			kind: 'command',
			unocmd: '.uno:LeftPara',
			group: 'paragraph',
		},
		{
			id: 'align-center',
			label: '居中',
			tab: 'default',
			icon: 'align-center',
			kind: 'command',
			unocmd: '.uno:CenterPara',
			group: 'paragraph',
		},
		{
			id: 'align-right',
			label: '右对齐',
			tab: 'default',
			icon: 'align-right',
			kind: 'command',
			unocmd: '.uno:RightPara',
			group: 'paragraph',
		},
		{
			id: 'align-justify',
			label: '两端对齐',
			tab: 'default',
			icon: 'align-justify',
			kind: 'command',
			unocmd: '.uno:JustifyPara',
			group: 'paragraph',
		},
		{
			id: 'bullet-list',
			label: '项目符号',
			tab: 'default',
			icon: 'bullet-list',
			kind: 'command',
			unocmd: '.uno:DefaultBullet',
			group: 'paragraph',
		},
		{
			id: 'numbered-list',
			label: '编号',
			tab: 'default',
			icon: 'numbered-list',
			kind: 'command',
			unocmd: '.uno:DefaultNumbering',
			group: 'paragraph',
		},

		// ---- 文件 (file) ----
		{
			id: 'save',
			label: '保存',
			tab: 'file',
			icon: 'save',
			kind: 'save',
			unocmd: '.uno:Save',
			group: 'file',
		},
		{
			id: 'save-as',
			label: '另存为',
			tab: 'file',
			icon: 'save-as',
			kind: 'dialog',
			dialog: 'saveAs',
			group: 'file',
		},
		{
			id: 'export-pdf',
			label: '导出为 PDF',
			tab: 'file',
			icon: 'export-pdf',
			kind: 'export',
			group: 'file',
		},
		{
			id: 'print',
			label: '打印',
			tab: 'file',
			icon: 'print',
			kind: 'print',
			group: 'file',
		},

		// ---- 插入 (insert) ----
		{
			id: 'insert-image',
			label: '图片',
			tab: 'insert',
			icon: 'image',
			kind: 'dialog',
			dialog: 'image',
			group: 'insert',
		},
		{
			id: 'insert-chart',
			label: '图表',
			tab: 'insert',
			icon: 'chart',
			kind: 'dialog',
			dialog: 'chart',
			unocmd: '.uno:InsertObjectChart',
			group: 'insert',
		},
		{
			id: 'insert-table',
			label: '表格',
			tab: 'insert',
			icon: 'table',
			kind: 'dialog',
			dialog: 'table',
			unocmd: '.uno:InsertTable',
			group: 'insert',
		},
		{
			id: 'insert-shape',
			label: '形状',
			tab: 'insert',
			icon: 'shape',
			kind: 'dialog',
			dialog: 'shape',
			group: 'insert',
		},
		{
			id: 'insert-comment',
			label: '批注',
			tab: 'insert',
			icon: 'comment',
			kind: 'command',
			unocmd: '.uno:InsertAnnotation',
			needsSelection: true,
			group: 'insert',
		},
		{
			id: 'insert-page-number',
			label: '页码',
			tab: 'insert',
			icon: 'page-number',
			kind: 'command',
			unocmd: '.uno:InsertPageNumberField',
			group: 'insert',
		},
		{
			id: 'insert-pagebreak',
			label: '分页符',
			tab: 'insert',
			icon: 'pagebreak',
			kind: 'command',
			unocmd: '.uno:InsertPagebreak',
			group: 'insert',
		},

		// ---- 布局 (layout) ----
		{
			id: 'watermark',
			label: '水印',
			tab: 'layout',
			icon: 'watermark',
			kind: 'dialog',
			dialog: 'watermark',
			unocmd: '.uno:Watermark',
			group: 'page',
		},
		{
			id: 'margins',
			label: '页边距',
			tab: 'layout',
			icon: 'margins',
			kind: 'dialog',
			dialog: 'margins',
			group: 'page',
		},
		{
			id: 'paper-size',
			label: '纸张大小',
			tab: 'layout',
			icon: 'paper-size',
			kind: 'dialog',
			dialog: 'paperSize',
			group: 'page',
		},
		{
			id: 'orientation',
			label: '页面方向',
			tab: 'layout',
			icon: 'orientation',
			kind: 'command',
			unocmd: '.uno:Orientation',
			group: 'page',
		},

		// ---- 审阅 (review) ----
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
			icon: 'spell-check',
			kind: 'command',
			unocmd: '.uno:SpellDialog',
			group: 'review',
		},
		{
			id: 'track-changes',
			label: '追踪修订',
			tab: 'review',
			icon: 'track-changes',
			kind: 'dialog',
			dialog: 'trackChanges',
			unocmd: '.uno:TrackChangesInAllViews',
			group: 'review',
		},
		{
			id: 'show-tracked-changes',
			label: '显示修订',
			tab: 'review',
			icon: 'show-tracked-changes',
			kind: 'command',
			unocmd: '.uno:ShowTrackedChanges',
			group: 'review',
		},
		{
			id: 'accept-tracked-change',
			label: '接受',
			tab: 'review',
			icon: 'accept-tracked-change',
			kind: 'command',
			unocmd: '.uno:AcceptTrackedChange',
			group: 'review',
		},
		{
			id: 'reject-tracked-change',
			label: '拒绝',
			tab: 'review',
			icon: 'reject-tracked-change',
			kind: 'command',
			unocmd: '.uno:RejectTrackedChange',
			group: 'review',
		},
	];

	static getFeatures(tab: WriterEditorTab): WriterEditorFeature[] {
		return WriterEditorCatalog.FEATURES.filter(
			(feature) => feature.tab === tab,
		);
	}

	static getFeature(id: string): WriterEditorFeature | null {
		const found = WriterEditorCatalog.FEATURES.find(
			(feature) => feature.id === id,
		);
		return found || null;
	}

	static getTab(id: string): WriterEditorTabDefinition | null {
		const found = WriterEditorCatalog.TABS.find((tab) => tab.id === id);
		return found || null;
	}

	/**
	 * Reorders CO paragraph styles: preferred styles (Figma 5252:56110) first
	 * with their Chinese labels, remaining CO styles appended in CO order.
	 * Mirrors Android FunctionPanelController.reorderStyles (L1809-1831) +
	 * styleMatches (L1833-1848).
	 */
	static reorderStyleOptions(names: string[]): { label: string; value: string }[] {
		const used: boolean[] = [];
		names.forEach(() => used.push(false));
		const result: { label: string; value: string }[] = [];
		WriterEditorCatalog.STYLE_ORDER.forEach((preferred) => {
			for (let i = 0; i < names.length; i++) {
				if (!used[i] && WriterEditorCatalog.styleMatches(names[i], preferred.styleId)) {
					result.push({ label: preferred.label, value: names[i] });
					used[i] = true;
					break;
				}
			}
		});
		for (let i = 0; i < names.length; i++) {
			if (!used[i]) {
				result.push({ label: names[i], value: names[i] });
			}
		}
		return result;
	}

	/** Maps a Windows font name to its CO-bundled substitute (case-insensitive). */
	static aliasFont(name: string): string {
		const normalized = name.trim();
		const found = WriterEditorCatalog.FONT_ALIAS_MAP.find(
			(pair) => pair.from.toLowerCase() === normalized.toLowerCase(),
		);
		return found ? found.to : normalized;
	}

	private static styleMatches(styleId: string, target: string): boolean {
		const lower = styleId.trim().toLowerCase();
		if (lower === target.toLowerCase()) {
			return true;
		}
		if (target.toLowerCase() === 'list' && lower.indexOf('list') === 0) {
			return true;
		}
		if (target.toLowerCase() === 'index' && lower.indexOf('index') === 0) {
			return true;
		}
		return false;
	}

	/**
	 * Maps an app chart-type id to its LO chart2 template service name.
	 * Transcribes Android CalcChartTypeMapper.toTemplateService (L14-38).
	 */
	static chartTemplateService(unoChartType: string): string {
		const services: { [key: string]: string } = {
			'pie': 'com.sun.star.chart2.template.Pie',
			'pie-rounded': 'com.sun.star.chart2.template.Donut',
			'pie-exploded': 'com.sun.star.chart2.template.PieAllExploded',
			'line': 'com.sun.star.chart2.template.LineSymbol',
			'line-curve': 'com.sun.star.chart2.template.LineSymbol',
			'column': 'com.sun.star.chart2.template.Column',
			'bar': 'com.sun.star.chart2.template.Bar',
			'column-stacked': 'com.sun.star.chart2.template.StackedColumn',
		};
		return services[unoChartType] || '';
	}

	/** Curve style for InsertObjectChart; 1 = cubic splines, -1 = unset. */
	static chartCurveStyle(unoChartType: string): number {
		return unoChartType === 'line-curve' ? 1 : -1;
	}

	/**
	 * Whether InsertObjectChart needs an explicit ChartTemplate argument.
	 * Column is core's default so Android omits it; everything else passes
	 * the template (Android CalcChartTypeMapper.needsCustomTemplate L47-54).
	 */
	static needsChartTemplate(unoChartType: string): boolean {
		const template = WriterEditorCatalog.chartTemplateService(unoChartType);
		if (!template) {
			return false;
		}
		return template !== 'com.sun.star.chart2.template.Column' ||
			WriterEditorCatalog.chartCurveStyle(unoChartType) >= 0;
	}

	/**
	 * Validates a feature definition for the registry. Rejects empty ids,
	 * duplicate ids, missing unocmd on command-like kinds, and command-like
	 * kinds without an icon.
	 */
	static validateFeature(feature: WriterEditorFeature): WriterEditorValidationResult {
		if (!feature.id || !feature.id.trim()) {
			return { valid: false, errorCode: 'empty_id' };
		}
		if (!feature.label || !feature.label.trim()) {
			return { valid: false, errorCode: 'empty_label' };
		}
		if (!feature.icon || !feature.icon.trim()) {
			return { valid: false, errorCode: 'empty_icon' };
		}
		const commandLike =
			feature.kind === 'command' ||
			feature.kind === 'queryCommand' ||
			feature.kind === 'commandWithArgs';
		if (commandLike && !feature.unocmd) {
			return { valid: false, errorCode: 'missing_unocmd' };
		}
		const duplicates = WriterEditorCatalog.FEATURES.filter(
			(candidate) => candidate.id === feature.id,
		);
		if (duplicates.length > 0) {
			return { valid: false, errorCode: 'duplicate_id' };
		}
		return { valid: true };
	}

	/** Validates the whole registry, returning the first offending feature. */
	static validateRegistry(): WriterEditorValidationResult {
		const seen: { [id: string]: boolean } = {};
		for (const feature of WriterEditorCatalog.FEATURES) {
			if (!feature.id || !feature.id.trim()) {
				return { valid: false, errorCode: 'empty_id' };
			}
			if (seen[feature.id]) {
				return { valid: false, errorCode: 'duplicate_id' };
			}
			seen[feature.id] = true;
			if (!feature.label || !feature.label.trim()) {
				return { valid: false, errorCode: 'empty_label' };
			}
			if (!feature.icon || !feature.icon.trim()) {
				return { valid: false, errorCode: 'empty_icon' };
			}
			const commandLike =
				feature.kind === 'command' ||
				feature.kind === 'queryCommand' ||
				feature.kind === 'commandWithArgs';
			if (commandLike && !feature.unocmd) {
				return { valid: false, errorCode: 'missing_unocmd' };
			}
		}
		return { valid: true };
	}
}
