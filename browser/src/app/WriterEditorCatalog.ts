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
	| 'pageNumber'; // insert page number field

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
	 * The numeric values match the browser Paper enum (WriterLayoutCatalog
	 * PAPER_SIZES offsets), as used by Android.
	 */
	static readonly PAPER_FORMATS: { label: string; value: string }[] = [
		{ label: 'A4', value: '4' },
		{ label: 'A3', value: '3' },
		{ label: 'A5', value: '5' },
		{ label: 'Letter', value: '8' },
		{ label: 'Legal', value: '9' },
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
			kind: 'command',
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
