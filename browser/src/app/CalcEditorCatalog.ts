/*
 * Calc edit-mode function catalog (iOS). Mirrors Android CalcFunctionPanelController.buildTabs().
 */

type CalcEditorTab = 'default' | 'file' | 'insert' | 'layout' | 'data' | 'review';

type CalcEditorFeatureKind =
	| 'section'
	| 'splitPicker'
	| 'charTools'
	| 'iconGrid'
	| 'stepperPair'
	| 'togglePair'
	| 'colorPickerPair'
	| 'command'
	| 'save'
	| 'export'
	| 'print'
	| 'dialog'
	| 'toggle'
	| 'iconValueRow'
	| 'submenu';

type CalcEditorGridMode = 'format' | 'iconCompact';

type CalcEditorDialogType =
	| 'fontName'
	| 'fontSize'
	| 'fontColor'
	| 'backgroundColor'
	| 'borderColor'
	| 'chart'
	| 'comment'
	| 'hyperlink'
	| 'dataValidation'
	| 'groupOutline'
	| 'paperOrientation'
	| 'printArea'
	| 'shape'
	| 'saveAs'
	| 'image'
	| 'dataValidation'
	| 'calcShape';

interface CalcEditorGridCell {
	label: string;
	unocmd: string;
	icon?: string;
}

interface CalcEditorFeature {
	id: string;
	label: string;
	tab: CalcEditorTab;
	icon: string;
	kind: CalcEditorFeatureKind;
	unocmd?: string;
	dialog?: CalcEditorDialogType;
	grid?: CalcEditorGridCell[];
	gridCols?: number;
	/** When set, each entry is one row width (e.g. align 6+5). Overrides gridCols row splitting. */
	gridRowCols?: number[];
	gridMode?: CalcEditorGridMode;
	wrapInCard?: boolean;
	stepperPairId?: 'decimal_steppers' | 'indent_steppers';
	togglePairId?: 'decimal_toggles' | 'stack_wrap_toggles';
	valueLabel?: string;
	defaultOn?: boolean;
	submenu?: CalcEditorFeature[];
}

interface CalcEditorTabDefinition {
	id: CalcEditorTab;
	label: string;
}

interface CalcEditorValidationResult {
	valid: boolean;
	errorCode?: string;
}

class CalcEditorCatalog {
	static readonly TABS: CalcEditorTabDefinition[] = [
		{ id: 'default', label: '常用' },
		{ id: 'file', label: '文件' },
		{ id: 'insert', label: '插入' },
		{ id: 'layout', label: '布局' },
		{ id: 'data', label: '数据' },
		{ id: 'review', label: '审阅' },
	];

	static readonly NUMFMT_GRID: CalcEditorGridCell[] = [
		{
			label: '常规',
			unocmd: '.uno:NumberFormatStandard',
			icon: 'calc-num-general',
		},
		{
			label: '数字',
			unocmd: '.uno:NumberFormatDecimal',
			icon: 'calc-num-number',
		},
		{
			label: '百分比',
			unocmd: '.uno:NumberFormatPercent',
			icon: 'calc-num-percent',
		},
		{
			label: '货币',
			unocmd: '.uno:NumberFormatCurrency',
			icon: 'calc-num-currency',
		},
		{
			label: '日期',
			unocmd: '.uno:NumberFormatDate',
			icon: 'calc-num-date',
		},
		{
			label: '时间',
			unocmd: '.uno:NumberFormatTime',
			icon: 'calc-num-time',
		},
		{
			label: '科学',
			unocmd: '.uno:NumberFormatScientific',
			icon: 'calc-num-scientific',
		},
		{
			label: '分数',
			unocmd: '.uno:FormatCellDialog',
			icon: 'calc-num-fraction',
		},
		{
			label: '布尔值',
			unocmd: '.uno:FormatCellDialog',
			icon: 'calc-num-boolean',
		},
		{
			label: '文本',
			unocmd: '.uno:NumberFormatText',
			icon: 'calc-num-text',
		},
	];

	static readonly ALIGN_GRID: CalcEditorGridCell[] = [
		{ label: '左', unocmd: '.uno:AlignLeft', icon: 'calc-align-left' },
		{
			label: '中',
			unocmd: '.uno:AlignHorizontalCenter',
			icon: 'calc-align-center-h',
		},
		{ label: '右', unocmd: '.uno:AlignRight', icon: 'calc-align-right' },
		{ label: '两端', unocmd: '.uno:AlignBlock', icon: 'calc-align-justify' },
		{
			label: '减缩进',
			unocmd: '.uno:DecrementIndent',
			icon: 'calc-indent-decrease',
		},
		{
			label: '增缩进',
			unocmd: '.uno:IncrementIndent',
			icon: 'calc-indent-increase',
		},
		{ label: '顶', unocmd: '.uno:AlignTop', icon: 'calc-align-top' },
		{
			label: '中',
			unocmd: '.uno:AlignVCenter',
			icon: 'calc-align-center-v',
		},
		{ label: '底', unocmd: '.uno:AlignBottom', icon: 'calc-align-bottom' },
		{
			label: '增行缩',
			unocmd: '.uno:IncrementIndent',
			icon: 'calc-indent-increase-row',
		},
		{
			label: '减行缩',
			unocmd: '.uno:DecrementIndent',
			icon: 'calc-indent-decrease-row',
		},
	];

	static readonly BORDER_GRID: CalcEditorGridCell[] = [
		{ label: '全', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-all-dashed' },
		{ label: '外', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-all-solid' },
		{
			label: '框',
			unocmd: '.uno:SetBorderStyle',
			icon: 'calc-border-outer-solid-inner-dashed',
		},
		{ label: '粗', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-outer-thick' },
		{ label: '上', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-top' },
		{ label: '下', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-bottom' },
		{ label: '左', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-left' },
		{ label: '右', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-right' },
		{
			label: '竖',
			unocmd: '.uno:SetBorderStyle',
			icon: 'calc-border-inner-vertical',
		},
		{
			label: '内横',
			unocmd: '.uno:SetBorderStyle',
			icon: 'calc-border-inner-horizontal',
		},
		{ label: '↘', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-diag-tl-br' },
		{ label: '↙', unocmd: '.uno:SetBorderStyle', icon: 'calc-border-diag-tr-bl' },
	];

	static readonly SHEET_GRID: CalcEditorGridCell[] = [
		{
			label: '插入行',
			unocmd: '.uno:InsertRowsBefore',
			icon: 'calc-sheet-insert-row',
		},
		{
			label: '插入列',
			unocmd: '.uno:InsertColumnsAfter',
			icon: 'calc-sheet-insert-col',
		},
		{
			label: '删除行',
			unocmd: '.uno:DeleteRows',
			icon: 'calc-sheet-delete-row',
		},
		{
			label: '删除列',
			unocmd: '.uno:DeleteColumns',
			icon: 'calc-sheet-delete-col',
		},
		{
			label: '冻结行列',
			unocmd: '.uno:FreezePanes',
			icon: 'calc-sheet-freeze-panes',
		},
		{
			label: '冻结列',
			unocmd: '.uno:FreezePanesColumn',
			icon: 'calc-sheet-freeze-col',
		},
		{
			label: '冻结行',
			unocmd: '.uno:FreezePanesRow',
			icon: 'calc-sheet-freeze-row',
		},
	];

	static readonly CHAR_TOOLS_ROW1: {
		id: string;
		label: string;
		unocmd: string;
		icon: string;
	}[] = [
		{ id: 'bold', label: '粗体', unocmd: '.uno:Bold', icon: 'calc-bold' },
		{ id: 'italic', label: '斜体', unocmd: '.uno:Italic', icon: 'calc-italic' },
		{
			id: 'underline',
			label: '下划线',
			unocmd: '.uno:Underline',
			icon: 'calc-underline',
		},
		{
			id: 'strike',
			label: '删除线',
			unocmd: '.uno:Strikeout',
			icon: 'calc-strikethrough',
		},
		{ id: 'shadow', label: '阴影', unocmd: '.uno:Shadowed', icon: 'calc-shadow' },
		{
			id: 'highlight',
			label: '高亮',
			unocmd: '.uno:CharBackColor',
			icon: 'calc-highlight',
		},
	];

	static readonly CHAR_TOOLS_ROW2: {
		id: string;
		label: string;
		unocmd: string;
		icon: string;
	}[] = [
		{
			id: 'super',
			label: '上标',
			unocmd: '.uno:SuperScript',
			icon: 'calc-superscript',
		},
		{ id: 'sub', label: '下标', unocmd: '.uno:SubScript', icon: 'calc-subscript' },
	];

	static readonly GROUP_OUTLINE_ITEMS: CalcEditorFeature[] = [
		{
			id: 'group',
			label: '组合',
			tab: 'data',
			icon: 'calc-group',
			kind: 'command',
			unocmd: '.uno:Group',
		},
		{
			id: 'ungroup',
			label: '取消组合',
			tab: 'data',
			icon: 'calc-ungroup',
			kind: 'command',
			unocmd: '.uno:Ungroup',
		},
		{
			id: 'clear-outline',
			label: '移除大纲',
			tab: 'data',
			icon: 'calc-clear-outline',
			kind: 'command',
			unocmd: '.uno:ClearOutline',
		},
		{
			id: 'hide-detail',
			label: '隐藏明细数据',
			tab: 'data',
			icon: 'calc-hide-detail',
			kind: 'command',
			unocmd: '.uno:HideDetail',
		},
		{
			id: 'show-detail',
			label: '显示明细数据',
			tab: 'data',
			icon: 'calc-show-detail',
			kind: 'command',
			unocmd: '.uno:ShowDetail',
		},
	];

	private static readonly FEATURES: CalcEditorFeature[] = CalcEditorCatalog.buildFeatures();

	private static buildFeatures(): CalcEditorFeature[] {
		const features: CalcEditorFeature[] = [
			{ id: 'sec-char', label: '字符', tab: 'default', icon: '', kind: 'section' },
			{
				id: 'font-name',
				label: '字体',
				tab: 'default',
				icon: 'font',
				kind: 'dialog',
				dialog: 'fontName',
			},
			{
				id: 'font-size-color',
				label: '字号颜色',
				tab: 'default',
				icon: 'font-size',
				kind: 'splitPicker',
			},
			{
				id: 'char-tools',
				label: '字符样式',
				tab: 'default',
				icon: '',
				kind: 'charTools',
			},
			{ id: 'sec-numfmt', label: '数值格式', tab: 'default', icon: '', kind: 'section' },
			{
				id: 'numfmt-grid',
				label: '数值格式',
				tab: 'default',
				icon: '',
				kind: 'iconGrid',
				grid: CalcEditorCatalog.NUMFMT_GRID,
				gridCols: 5,
				gridMode: 'format',
			},
			{
				id: 'decimal-steppers',
				label: '小数',
				tab: 'default',
				icon: '',
				kind: 'stepperPair',
				stepperPairId: 'decimal_steppers',
			},
			{
				id: 'decimal-toggles',
				label: '数值开关',
				tab: 'default',
				icon: '',
				kind: 'togglePair',
				togglePairId: 'decimal_toggles',
			},
			{ id: 'sec-align', label: '对齐', tab: 'default', icon: '', kind: 'section' },
			{
				id: 'align-grid',
				label: '对齐',
				tab: 'default',
				icon: '',
				kind: 'iconGrid',
				grid: CalcEditorCatalog.ALIGN_GRID,
				gridCols: 6,
				gridRowCols: [6, 5],
				gridMode: 'iconCompact',
				wrapInCard: true,
			},
			{
				id: 'indent-steppers',
				label: '缩进',
				tab: 'default',
				icon: '',
				kind: 'stepperPair',
				stepperPairId: 'indent_steppers',
			},
			{
				id: 'stack-wrap-toggles',
				label: '排列换行',
				tab: 'default',
				icon: '',
				kind: 'togglePair',
				togglePairId: 'stack_wrap_toggles',
			},
			{
				id: 'merge-cells',
				label: '合并单元格',
				tab: 'default',
				icon: 'calc-merge-cells',
				kind: 'command',
				unocmd: '.uno:ToggleMergeCells',
			},
			{ id: 'sec-border', label: '边框', tab: 'default', icon: '', kind: 'section' },
			{
				id: 'border-styles',
				label: '边框样式',
				tab: 'default',
				icon: '',
				kind: 'iconGrid',
				grid: CalcEditorCatalog.BORDER_GRID,
				gridCols: 6,
				gridRowCols: [6, 6],
				gridMode: 'iconCompact',
				wrapInCard: true,
			},
			{
				id: 'color-pickers',
				label: '颜色',
				tab: 'default',
				icon: '',
				kind: 'colorPickerPair',
			},
			{ id: 'sec-sheet', label: '工作表', tab: 'default', icon: '', kind: 'section' },
			{
				id: 'sheet-ops',
				label: '工作表',
				tab: 'default',
				icon: '',
				kind: 'iconGrid',
				grid: CalcEditorCatalog.SHEET_GRID,
				gridCols: 7,
				gridMode: 'iconCompact',
				wrapInCard: true,
			},
			{
				id: 'save',
				label: '保存',
				tab: 'file',
				icon: 'calc-file-save',
				kind: 'save',
			},
			{
				id: 'save-as',
				label: '另存为',
				tab: 'file',
				icon: 'calc-file-save-as',
				kind: 'dialog',
				dialog: 'saveAs',
			},
			{
				id: 'export-pdf',
				label: '导出为',
				tab: 'file',
				icon: 'calc-file-export',
				kind: 'export',
			},
			{
				id: 'print',
				label: '打印',
				tab: 'file',
				icon: 'calc-file-print',
				kind: 'print',
			},
			{
				id: 'insert-local-image',
				label: '本地图像',
				tab: 'insert',
				icon: 'calc-insert-local-image',
				kind: 'dialog',
				dialog: 'image',
			},
			{
				id: 'insert-chart',
				label: '图表',
				tab: 'insert',
				icon: 'calc-insert-chart',
				kind: 'dialog',
				dialog: 'chart',
			},
			{
				id: 'insert-comment',
				label: '批注',
				tab: 'insert',
				icon: 'calc-insert-comment',
				kind: 'dialog',
				dialog: 'comment',
			},
			{
				id: 'insert-hyperlink',
				label: '超链接',
				tab: 'insert',
				icon: 'calc-insert-hyperlink',
				kind: 'dialog',
				dialog: 'hyperlink',
			},
			{
				id: 'insert-shape',
				label: '形状',
				tab: 'insert',
				icon: 'calc-insert-shape',
				kind: 'dialog',
				dialog: 'calcShape',
			},
			{
				id: 'insert-date',
				label: '日期',
				tab: 'insert',
				icon: 'calc-insert-date',
				kind: 'command',
				unocmd: '.uno:InsertCurrentDate',
			},
			{
				id: 'insert-time',
				label: '时间',
				tab: 'insert',
				icon: 'calc-insert-time',
				kind: 'command',
				unocmd: '.uno:InsertCurrentTime',
			},
			{
				id: 'paper-orientation',
				label: '纸张方向',
				tab: 'layout',
				icon: 'calc-paper-orientation',
				kind: 'iconValueRow',
				dialog: 'paperOrientation',
				valueLabel: '纵向',
			},
			{
				id: 'print-area',
				label: '打印区域',
				tab: 'layout',
				icon: 'calc-print-area',
				kind: 'iconValueRow',
				dialog: 'printArea',
				valueLabel: 'A4',
			},
			{
				id: 'grid-lines',
				label: '显示网格线',
				tab: 'layout',
				icon: 'calc-grid-lines',
				kind: 'toggle',
				unocmd: '.uno:ToggleSheetGrid',
				defaultOn: true,
			},
			{
				id: 'data-validation',
				label: '数据有效性',
				tab: 'data',
				icon: 'calc-data-validation',
				kind: 'dialog',
				dialog: 'dataValidation',
			},
			{
				id: 'sort-asc',
				label: '升序',
				tab: 'data',
				icon: 'calc-sort-asc',
				kind: 'command',
				unocmd: '.uno:SortAscending',
			},
			{
				id: 'sort-desc',
				label: '降序',
				tab: 'data',
				icon: 'calc-sort-desc',
				kind: 'command',
				unocmd: '.uno:SortDescending',
			},
			{
				id: 'group-outline',
				label: '分组及分级显示',
				tab: 'data',
				icon: 'calc-group-outline',
				kind: 'submenu',
				submenu: CalcEditorCatalog.GROUP_OUTLINE_ITEMS,
			},
			{
				id: 'spell-check',
				label: '拼写检查',
				tab: 'review',
				icon: 'calc-spell-check',
				kind: 'command',
				unocmd: '.uno:SpellDialog',
			},
			{
				id: 'review-comment',
				label: '批注',
				tab: 'review',
				icon: 'calc-review-comment',
				kind: 'dialog',
				dialog: 'comment',
			},
		];
		return features;
	}

	static getFeatures(tab: CalcEditorTab): CalcEditorFeature[] {
		return CalcEditorCatalog.FEATURES.filter((f) => f.tab === tab);
	}

	static getFeature(id: string): CalcEditorFeature | null {
		return CalcEditorCatalog.FEATURES.find((f) => f.id === id) || null;
	}

	static validateRegistry(): CalcEditorValidationResult {
		const ids = new Set<string>();
		for (let i = 0; i < CalcEditorCatalog.FEATURES.length; i++) {
			const feature = CalcEditorCatalog.FEATURES[i];
			if (ids.has(feature.id)) {
				return { valid: false, errorCode: 'duplicate_id:' + feature.id };
			}
			ids.add(feature.id);
		}
		return { valid: true };
	}
}
