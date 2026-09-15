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
	| 'save'
	| 'export'
	| 'print'
	| 'findReplace';

type ImpressEditorDialogType =
	| 'image'
	| 'table'
	| 'shape'
	| 'saveAs'
	| 'comment'
	| 'hyperlink';

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
		{ id: 'layout-title', label: '标题幻灯片', whatLayout: 0 },
		{ id: 'layout-title-content', label: '标题和内容', whatLayout: 1 },
		{ id: 'layout-section', label: '节标题', whatLayout: 2 },
		{ id: 'layout-two-content', label: '两栏内容', whatLayout: 3 },
		{ id: 'layout-compare', label: '比较', whatLayout: 15 },
		{ id: 'layout-title-only', label: '仅标题', whatLayout: 19 },
		{ id: 'layout-blank', label: '空白', whatLayout: 20 },
		{ id: 'layout-picture-title', label: '图片与标题', whatLayout: 12 },
		{ id: 'layout-vertical', label: '竖排标题与文本', whatLayout: 28 },
		{ id: 'layout-content', label: '内容', whatLayout: 32 },
		{ id: 'layout-end', label: '末尾幻灯片', whatLayout: 19 },
	];

	/** Android ImpressTransitionCatalog labels + setId + iconViewIndex (1–36). */
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
				id: 'common-insert-image',
				label: '本地图像',
				tab: 'default',
				icon: 'image',
				kind: 'dialog',
				dialog: 'image',
				group: 'common',
			},
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
				label: '导出为',
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
			{
				id: 'insert-image',
				label: '本地图像',
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
				id: 'insert-hyperlink',
				label: '超链接',
				tab: 'insert',
				icon: 'page-number',
				kind: 'dialog',
				dialog: 'hyperlink',
				unocmd: '.uno:SetHyperlink',
				group: 'insert',
			},
			{
				id: 'insert-comment',
				label: '批注',
				tab: 'insert',
				icon: 'comment',
				kind: 'dialog',
				dialog: 'comment',
				unocmd: '.uno:InsertAnnotation',
				group: 'insert',
			},
			{
				id: 'insert-textbox',
				label: '文本框',
				tab: 'insert',
				icon: 'pagebreak',
				kind: 'command',
				unocmd: '.uno:DrawText',
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
				icon: 'spell-check',
				kind: 'command',
				unocmd: '.uno:SpellDialog',
				group: 'review',
			},
			{
				id: 'review-comment',
				label: '批注',
				tab: 'review',
				icon: 'comment',
				kind: 'dialog',
				dialog: 'comment',
				unocmd: '.uno:InsertAnnotation',
				group: 'review',
			},
		];

		ImpressEditorCatalog.LAYOUTS.forEach((layout, index) => {
			if (index < 3) {
				features.push({
					id: 'common-' + layout.id,
					label: layout.label,
					tab: 'default',
					icon: 'pagebreak',
					kind: 'queryCommand',
					unocmd: '.uno:AssignLayout',
					queryParams: '?WhatLayout:long=' + layout.whatLayout,
					group: 'layout',
				});
			}
			features.push({
				id: layout.id,
				label: layout.label,
				tab: 'layout',
				icon: 'pagebreak',
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
				icon: 'pagebreak',
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
		if (
			(feature.kind === 'command' || feature.kind === 'queryCommand') &&
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
