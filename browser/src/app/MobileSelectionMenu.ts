/*
 * iOS HTML selection menu (ticket 05 — 5+5+2 parity).
 *
 * Renders the Writer text-selection popup for iOS. Android keeps its native
 * SelectionMenuController; iOS consumes fabric selection events here.
 */

interface MobileSelectionMenuItemSpec {
	id: string;
	label: string;
	iconKey: string;
	kind: 'clipboard' | 'ai' | 'action';
	androidTaskType?: string;
	catalogTaskType?: string;
	unoCommand?: string;
}

interface MobileSelectionShowDetail {
	anchorX: number;
	anchorY: number;
	anchorBottomY?: number;
	text: string;
	mode?: 'text' | 'calc' | 'graphic';
}

class MobileSelectionMenu {
	static readonly MENU_CLASS = 'fabric-selection-menu';
	static readonly OVERLAY_CLASS = 'fabric-selection-menu-overlay';
	private static readonly POPUP_MARGIN_PX = 16;
	private static readonly POPUP_ANCHOR_GAP_PX = 12;
	private static readonly POPUP_SELECTION_GAP_PX = 24;
	private static readonly POPUP_MAX_WIDTH_PX = 301;
	private static readonly BOTTOM_TOOLBAR_PX = 82;

	private overlay: HTMLDivElement | null = null;
	private container: HTMLDivElement | null = null;
	private anchorBottomY = 0;
	private menuMode: 'text' | 'calc' | 'graphic' = 'text';
	private readonly onShow: (event: Event) => void;
	private readonly onHide: (event: Event) => void;

	constructor() {
		this.onShow = (event: Event) => this.show(event as CustomEvent);
		this.onHide = () => this.hide();
		window.addEventListener(MobileSelectionEvents.SHOW_EVENT, this.onShow);
		window.addEventListener(MobileSelectionEvents.HIDE_EVENT, this.onHide);
	}

	/** Fixed Writer edit layout (Android lolib_selection_popup.xml). */
	static editMenuItems(): MobileSelectionMenuItemSpec[] {
		return [
			{ id: 'copy', label: '复制', iconKey: 'copy', kind: 'clipboard' },
			{ id: 'cut', label: '剪切', iconKey: 'cut', kind: 'clipboard' },
			{ id: 'paste', label: '粘贴', iconKey: 'paste', kind: 'clipboard' },
			{ id: 'select_all', label: '全选', iconKey: 'select_all', kind: 'clipboard' },
			{
				id: 'translate',
				label: '翻译',
				iconKey: 'translate',
				kind: 'ai',
				androidTaskType: 'translate',
				catalogTaskType: 'translate',
			},
			{
				id: 'outline',
				label: '总结大纲',
				iconKey: 'outline',
				kind: 'ai',
				androidTaskType: 'outline',
				catalogTaskType: 'outline',
			},
			{
				id: 'continue_write',
				label: '文案续写',
				iconKey: 'continue_write',
				kind: 'ai',
				androidTaskType: 'continue_write',
				catalogTaskType: 'continue',
			},
			{
				id: 'article_generate',
				label: '文案生成',
				iconKey: 'article_generate',
				kind: 'ai',
				androidTaskType: 'article_generate',
				catalogTaskType: 'article_generate',
			},
			{
				id: 'expand',
				label: '文案扩写',
				iconKey: 'expand',
				kind: 'ai',
				androidTaskType: 'expand',
				catalogTaskType: 'expand',
			},
			{
				id: 'polish',
				label: '文案润色',
				iconKey: 'polish',
				kind: 'ai',
				androidTaskType: 'polish',
				catalogTaskType: 'polish',
			},
			{
				id: 'condense',
				label: '文案缩写',
				iconKey: 'condense',
				kind: 'ai',
				androidTaskType: 'condense',
				catalogTaskType: 'condense',
			},
			{
				id: 'rewrite',
				label: '文案重写',
				iconKey: 'rewrite',
				kind: 'ai',
				androidTaskType: 'rewrite',
				catalogTaskType: 'rewrite',
			},
		];
	}

	/** Calc cell text selection (Android SelectionMenuController calcMode). */
	static calcMenuItems(): MobileSelectionMenuItemSpec[] {
		return [
			{ id: 'copy', label: '复制', iconKey: 'copy', kind: 'clipboard' },
			{ id: 'paste', label: '粘贴', iconKey: 'paste', kind: 'clipboard' },
			{ id: 'cut', label: '剪切', iconKey: 'cut', kind: 'clipboard' },
			{
				id: 'clear',
				label: '清除',
				iconKey: 'clear',
				kind: 'action',
				unoCommand: '.uno:ClearContents',
			},
			{
				id: 'translate',
				label: '翻译',
				iconKey: 'translate',
				kind: 'ai',
				androidTaskType: 'translate',
				catalogTaskType: 'translate',
			},
		];
	}

	/** Impress graphic selection compact row. */
	static graphicMenuItems(): MobileSelectionMenuItemSpec[] {
		return [
			{ id: 'copy', label: '复制', iconKey: 'copy', kind: 'clipboard' },
			{ id: 'cut', label: '剪切', iconKey: 'cut', kind: 'clipboard' },
			{ id: 'paste', label: '粘贴', iconKey: 'paste', kind: 'clipboard' },
			{
				id: 'delete',
				label: '删除',
				iconKey: 'delete',
				kind: 'action',
				unoCommand: '.uno:Delete',
			},
			{
				id: 'image_edit',
				label: '图片编辑',
				iconKey: 'image_edit',
				kind: 'action',
				unoCommand: '.uno:Crop',
			},
			{
				id: 'save',
				label: '保存',
				iconKey: 'save',
				kind: 'action',
			},
		];
	}

	static aiTaskTypes(): string[] {
		return MobileSelectionMenu.editMenuItems()
			.filter((item) => item.kind === 'ai' && item.catalogTaskType)
			.map((item) => item.catalogTaskType as string);
	}

	/** @deprecated Use aiTaskTypes(); kept for existing tests during migration. */
	static menuTaskTypes(): string[] {
		return MobileSelectionMenu.aiTaskTypes();
	}

	/** Selection menu entries allowed for a document and its current mode. */
	static menuTaskTypesForDocument(
		documentType: MobileAiDocumentType,
		isReadOnly: boolean,
	): string[] {
		return MobileAiUiCatalog.getSelectionEntries(documentType, !isReadOnly).map(
			(entry) => entry.taskType,
		);
	}

	static install(): MobileSelectionMenu | null {
		if (typeof window === 'undefined' || !(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolMobileSelectionMenu;
		if (existing instanceof MobileSelectionMenu) {
			return existing;
		}
		const menu = new MobileSelectionMenu();
		(window as any).__coolMobileSelectionMenu = menu;
		return menu;
	}

	dispose(): void {
		window.removeEventListener(MobileSelectionEvents.SHOW_EVENT, this.onShow);
		window.removeEventListener(MobileSelectionEvents.HIDE_EVENT, this.onHide);
		this.hide();
	}

	hide(): void {
		if (this.overlay && this.overlay.parentNode) {
			this.overlay.parentNode.removeChild(this.overlay);
		}
		if (this.container && this.container.parentNode) {
			this.container.parentNode.removeChild(this.container);
		}
		this.overlay = null;
		this.container = null;
	}

	private show(event: CustomEvent): void {
		const detail = event.detail as MobileSelectionShowDetail;
		if (!detail || typeof detail.anchorX !== 'number') {
			return;
		}
		this.hide();
		WriterQuickActionBar.closeAll();
		this.closeFunctionSheets();

		const map = (window as any).app?.map;
		const docType = map?.getDocType?.() || 'text';
		const isReadOnly =
			typeof map?.isReadOnlyMode === 'function' && !!map.isReadOnlyMode();

		this.menuMode = detail.mode || 'text';
		if (this.menuMode === 'text') {
			if (docType === 'spreadsheet') {
				this.menuMode = 'calc';
			} else if (docType === 'presentation' && isReadOnly) {
				if (
					MobileSelectionMenu.menuTaskTypesForDocument(docType, isReadOnly)
						.length === 0
				) {
					return;
				}
			} else if (docType !== 'text' && docType !== 'presentation') {
				return;
			}
		} else if (this.menuMode === 'graphic' && docType === 'spreadsheet') {
			return;
		}

		this.anchorBottomY =
			typeof detail.anchorBottomY === 'number' ? detail.anchorBottomY : detail.anchorY;

		const overlay = document.createElement('div');
		overlay.className = MobileSelectionMenu.OVERLAY_CLASS;
		overlay.onclick = () => this.hide();

		const panel = document.createElement('div');
		panel.className = MobileSelectionMenu.MENU_CLASS;
		if (this.menuMode === 'graphic') {
			panel.classList.add('fabric-selection-menu--graphic');
		}
		panel.onclick = (e) => e.stopPropagation();

		const editMode = MobileSelectionMenu.isEditModeActive();
		const editable = MobileSelectionMenu.isDocEditable();
		const rows = MobileSelectionMenu.buildRows(this.menuMode, editMode, editable);
		rows.forEach((row) => panel.appendChild(row));

		document.body.appendChild(overlay);
		document.body.appendChild(panel);
		this.overlay = overlay;
		this.container = panel;
		window.requestAnimationFrame(() => {
			this.positionNearAnchor(detail.anchorX, detail.anchorY);
		});
	}

	private static buildRows(
		mode: 'text' | 'calc' | 'graphic',
		editMode: boolean,
		editable: boolean,
	): HTMLElement[] {
		if (mode === 'calc') {
			return [
				MobileSelectionMenu.buildButtonRow(MobileSelectionMenu.calcMenuItems(), {
					editMode,
					editable,
					mode,
				}),
			];
		}
		if (mode === 'graphic') {
			return [
				MobileSelectionMenu.buildButtonRow(MobileSelectionMenu.graphicMenuItems(), {
					editMode,
					editable,
					mode,
				}),
			];
		}

		const items = MobileSelectionMenu.editMenuItems();
		const row1Ids = ['copy', 'cut', 'paste', 'select_all', 'translate'];
		const row2Ids = [
			'outline',
			'continue_write',
			'article_generate',
			'expand',
			'polish',
		];
		const row3Ids = ['condense', 'rewrite'];

		const rows: HTMLElement[] = [];
		const row1 = MobileSelectionMenu.buildButtonRow(
			items.filter((item) => row1Ids.indexOf(item.id) >= 0),
			{ editMode, editable, mode },
		);
		rows.push(row1);

		if (editMode) {
			rows.push(MobileSelectionMenu.buildDivider());
			rows.push(
				MobileSelectionMenu.buildButtonRow(
					items.filter((item) => row2Ids.indexOf(item.id) >= 0),
					{ editMode, editable, mode },
				),
			);
			rows.push(MobileSelectionMenu.buildDivider());
			const row3 = MobileSelectionMenu.buildButtonRow(
				items.filter((item) => row3Ids.indexOf(item.id) >= 0),
				{ editMode, editable, mode },
			);
			const spacer = document.createElement('div');
			spacer.className = 'fabric-selection-menu__spacer';
			row3.appendChild(spacer);
			rows.push(row3);
		}
		return rows;
	}

	private static buildDivider(): HTMLElement {
		const divider = document.createElement('div');
		divider.className = 'fabric-selection-menu__divider';
		return divider;
	}

	private static buildButtonRow(
		items: MobileSelectionMenuItemSpec[],
		ctx: { editMode: boolean; editable: boolean; mode: 'text' | 'calc' | 'graphic' },
	): HTMLElement {
		const row = document.createElement('div');
		row.className = 'fabric-selection-menu__row';
		if (ctx.mode === 'graphic') {
			row.classList.add('fabric-selection-menu__row--scroll');
		}
		items.forEach((item) => {
			if (!MobileSelectionMenu.isItemVisible(item, ctx)) {
				return;
			}
			row.appendChild(MobileSelectionMenu.createButton(item));
		});
		return row;
	}

	private static isItemVisible(
		item: MobileSelectionMenuItemSpec,
		ctx: { editMode: boolean; editable: boolean; mode: 'text' | 'calc' | 'graphic' },
	): boolean {
		if (ctx.mode === 'calc') {
			if (item.id === 'paste') {
				return ctx.editable;
			}
			if (item.id === 'cut' || item.id === 'clear') {
				return ctx.editable && ctx.editMode;
			}
			if (item.id === 'translate') {
				return ctx.editMode;
			}
			return true;
		}
		if (ctx.mode === 'graphic') {
			if (item.id === 'paste' || item.id === 'cut' || item.id === 'delete' || item.id === 'image_edit') {
				return ctx.editable;
			}
			return true;
		}
		if (item.id === 'paste' || item.id === 'cut') {
			return ctx.editable;
		}
		if (item.id === 'translate') {
			return ctx.editMode;
		}
		if (item.kind === 'ai') {
			return ctx.editMode;
		}
		return true;
	}

	private static createButton(item: MobileSelectionMenuItemSpec): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'fabric-selection-menu__button';
		if (item.id === 'save' || item.id === 'image_edit' || item.id === 'clear' || item.id === 'delete') {
			button.classList.add('fabric-selection-menu__button--compact');
		}
		button.setAttribute('aria-label', item.label);

		const icon = document.createElement('span');
		icon.className = 'fabric-selection-menu__icon';
		icon.innerHTML = MobileSelectionMenuIcons.get(item.iconKey);
		button.appendChild(icon);

		const label = document.createElement('span');
		label.className = 'fabric-selection-menu__label';
		label.textContent = item.label;
		button.appendChild(label);

		button.onclick = () => MobileSelectionMenu.onItemTap(item);
		return button;
	}

	private static onItemTap(item: MobileSelectionMenuItemSpec): void {
		const menu = (window as any).__coolMobileSelectionMenu as MobileSelectionMenu;
		if (item.kind === 'clipboard') {
			MobileSelectionMenu.runClipboardAction(item.id);
			menu?.hide();
			return;
		}
		if (item.kind === 'action') {
			menu?.hide();
			MobileSelectionMenu.runAction(item);
			return;
		}
		menu?.hide();
		if (item.catalogTaskType) {
			const panel = (window as any).__coolWriterAiPanel;
			if (panel && typeof panel.openTask === 'function') {
				panel.openTask(item.catalogTaskType);
			}
		}
	}

	private static runAction(item: MobileSelectionMenuItemSpec): void {
		const map = (window as any).app?.map;
		if (!map) {
			return;
		}
		if (item.id === 'save') {
			if (map.socket && typeof map.socket.sendMessage === 'function') {
				map.socket.sendMessage('save dontTerminateEdit=1 dontSaveIfUnmodified=0');
			}
			return;
		}
		if (!item.unoCommand) {
			return;
		}
		const run = () => map.sendUnoCommand(item.unoCommand as string);
		if (MobileSelectionMenu.isEditModeActive()) {
			run();
		} else if (typeof map.setEditMode === 'function') {
			map.setEditMode(true);
			window.setTimeout(run, 0);
		}
	}

	private static runClipboardAction(actionId: string): void {
		const map = (window as any).app?.map;
		if (!map) {
			return;
		}
		if (actionId === 'select_all') {
			map.sendUnoCommand('.uno:SelectAll');
			return;
		}
		const clip = map._clip;
		if (!clip || typeof clip._execCopyCutPaste !== 'function') {
			return;
		}
		if (actionId === 'copy') {
			clip._execCopyCutPaste('copy');
		} else if (actionId === 'cut') {
			if (!MobileSelectionMenu.isDocEditable()) {
				return;
			}
			const run = () => clip._execCopyCutPaste('cut');
			if (MobileSelectionMenu.isEditModeActive()) {
				run();
			} else if (typeof map.setEditMode === 'function') {
				map.setEditMode(true);
				window.setTimeout(run, 0);
			}
		} else if (actionId === 'paste') {
			if (!MobileSelectionMenu.isDocEditable()) {
				return;
			}
			const run = () => clip._execCopyCutPaste('paste');
			if (MobileSelectionMenu.isEditModeActive()) {
				run();
			} else if (typeof map.setEditMode === 'function') {
				map.setEditMode(true);
				window.setTimeout(run, 0);
			}
		}
	}

	private static isDocEditable(): boolean {
		const appRef = (window as any).app;
		if (!appRef) {
			return false;
		}
		if (typeof appRef.isReadOnly === 'function' && appRef.isReadOnly()) {
			return false;
		}
		return true;
	}

	private static isEditModeActive(): boolean {
		const map = (window as any).app?.map;
		return !!(map && typeof map.isEditMode === 'function' && map.isEditMode());
	}

	private closeFunctionSheets(): void {
		const editorPanel = (window as any).__coolWriterEditorPanel;
		if (editorPanel && typeof editorPanel.close === 'function') {
			editorPanel.close();
		}
	}

	private positionNearAnchor(anchorX: number, anchorY: number): void {
		if (!this.container) {
			return;
		}
		const width = Math.min(
			this.menuMode === 'graphic'
				? window.innerWidth - MobileSelectionMenu.POPUP_MARGIN_PX * 2
				: MobileSelectionMenu.POPUP_MAX_WIDTH_PX,
			window.innerWidth - MobileSelectionMenu.POPUP_MARGIN_PX * 2,
		);
		this.container.style.width = width + 'px';

		const menuWidth = this.container.offsetWidth || width;
		const menuHeight = this.container.offsetHeight || 200;
		const margin = MobileSelectionMenu.POPUP_MARGIN_PX;
		const bottomReserved =
			MobileSelectionMenu.BOTTOM_TOOLBAR_PX +
			MobileSelectionMenu.readSafeAreaBottom();
		const maxContentBottom = window.innerHeight - bottomReserved - margin;

		let x = anchorX - menuWidth / 2;
		x = Math.max(margin, Math.min(x, window.innerWidth - menuWidth - margin));

		const selectionCenterY = (anchorY + this.anchorBottomY) / 2;
		const spaceAbove = selectionCenterY - margin;
		const spaceBelow = maxContentBottom - selectionCenterY;
		const aboveTop = anchorY - menuHeight - MobileSelectionMenu.POPUP_ANCHOR_GAP_PX;
		const belowTop = this.anchorBottomY + MobileSelectionMenu.POPUP_SELECTION_GAP_PX;
		const canPlaceAbove = aboveTop >= margin;
		const canPlaceBelow = belowTop + menuHeight <= maxContentBottom;
		const preferAbove = spaceAbove >= spaceBelow;

		let y: number;
		if (preferAbove && canPlaceAbove) {
			y = aboveTop;
		} else if (canPlaceBelow) {
			y = belowTop;
		} else if (canPlaceAbove) {
			y = aboveTop;
		} else {
			y = Math.max(margin, Math.min(belowTop, maxContentBottom - menuHeight));
		}
		y = Math.max(margin, Math.min(y, maxContentBottom - menuHeight));

		this.container.style.left = Math.round(x) + 'px';
		this.container.style.top = Math.round(y) + 'px';
	}

	private static readSafeAreaBottom(): number {
		const probe = document.createElement('div');
		probe.style.cssText =
			'position:fixed;left:0;bottom:0;height:0;padding-bottom:env(safe-area-inset-bottom);visibility:hidden;';
		document.body.appendChild(probe);
		const value = parseFloat(window.getComputedStyle(probe).paddingBottom || '0') || 0;
		document.body.removeChild(probe);
		return value;
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	MobileSelectionMenu.install();
}
