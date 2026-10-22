/*
 * iOS HTML selection menu for Writer and Impress text selections.
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
	private textEditMode = false;
	/** Ignore taps until this time. iOS emits extra clicks when the long-press finger lifts. */
	private static ignoreActionsUntil = 0;
	private static readonly OPEN_CLICK_GUARD_MS = 500;
	private readonly onShow: (event: Event) => void;
	private readonly onHide: (event: Event) => void;
	private readonly onVisualViewport: () => void;
	private readonly onOutsideClick: (event: MouseEvent) => void;
	/** Native keyboard frame is on screen (DocumentViewController). */
	private nativeKeyboardVisible = false;
	private lastAnchorX = 0;
	private lastAnchorY = 0;
	private positionAttempts = 0;

	constructor() {
		this.onShow = (event: Event) => this.show(event as CustomEvent);
		this.onHide = () => this.hide();
		this.onVisualViewport = () => this.handleVisualViewport();
		this.onOutsideClick = (event: MouseEvent) => {
			if (MobileSelectionMenu.consumeOpeningClick(event)) {
				return;
			}
			if (this.container && !this.container.contains(event.target as Node)) {
				this.hide();
			}
		};
		window.addEventListener(MobileSelectionEvents.SHOW_EVENT, this.onShow);
		window.addEventListener(MobileSelectionEvents.HIDE_EVENT, this.onHide);
		const viewport = window.visualViewport;
		if (viewport) {
			viewport.addEventListener('resize', this.onVisualViewport);
			viewport.addEventListener('scroll', this.onVisualViewport);
		}
	}

	/** Soft keyboard covers part of the web view; used for input-state coordination. */
	static iosKeyboardOpen(): boolean {
		const menu = (window as any).__coolMobileSelectionMenu as MobileSelectionMenu | undefined;
		if (menu && menu.keyboardOpen()) {
			return true;
		}
		return MobileSelectionMenu.visualKeyboardOpen();
	}

	static visualKeyboardOpen(): boolean {
		const viewport = window.visualViewport;
		if (!viewport) {
			return false;
		}
		return window.innerHeight - viewport.offsetTop - viewport.height > 150;
	}

	keyboardOpen(): boolean {
		return this.nativeKeyboardVisible || MobileSelectionMenu.visualKeyboardOpen();
	}

	/** Called from iOS DocumentViewController keyboardWillChangeFrame. */
	setKeyboardVisible(visible: boolean): void {
		this.nativeKeyboardVisible = visible;
		const selectionMenu = (window as any).AndroidSelectionMenu;
		if (
			!visible &&
			selectionMenu &&
			typeof selectionMenu.onIOSKeyboardVisibilityChanged === 'function'
		) {
			selectionMenu.onIOSKeyboardVisibilityChanged(false);
		}
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
		const viewport = window.visualViewport;
		if (viewport) {
			viewport.removeEventListener('resize', this.onVisualViewport);
			viewport.removeEventListener('scroll', this.onVisualViewport);
		}
		this.hide();
	}

	private handleVisualViewport(): void {
		if (this.container) {
			this.positionNearAnchor(this.lastAnchorX, this.lastAnchorY);
		}
	}

	hide(): void {
		document.removeEventListener('click', this.onOutsideClick, true);
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

		this.menuMode = detail.mode || 'text';
		if (this.menuMode === 'text') {
			if (docType === 'spreadsheet') {
				this.menuMode = 'calc';
			} else if (docType !== 'text' && docType !== 'presentation') {
				return;
			}
		}

		this.anchorBottomY =
			typeof detail.anchorBottomY === 'number' ? detail.anchorBottomY : detail.anchorY;

		MobileSelectionMenu.ignoreActionsUntil =
			Date.now() + MobileSelectionMenu.OPEN_CLICK_GUARD_MS;

		const overlay = document.createElement('div');
		overlay.className = MobileSelectionMenu.OVERLAY_CLASS;

		const panel = document.createElement('div');
		panel.className = MobileSelectionMenu.MENU_CLASS;
		const editMode = MobileSelectionMenu.isEditModeActive();
		const editable = MobileSelectionMenu.isDocEditable();
		if (this.menuMode === 'graphic') {
			panel.classList.add('fabric-selection-menu--graphic');
		} else if (this.menuMode === 'calc') {
			panel.classList.add('fabric-selection-menu--calc');
		} else if (editMode) {
			panel.classList.add('fabric-selection-menu--text-edit');
		} else {
			panel.classList.add('fabric-selection-menu--text-reading');
		}
		this.textEditMode = this.menuMode === 'text' && editMode;
		panel.onclick = (e) => e.stopPropagation();

		const rows = MobileSelectionMenu.buildRows(this.menuMode, editMode, editable);
		rows.forEach((row) => panel.appendChild(row));

		document.body.appendChild(overlay);
		document.body.appendChild(panel);
		document.addEventListener('click', this.onOutsideClick, true);
		this.overlay = overlay;
		this.container = panel;
		if (this.textEditMode) {
			const viewport = panel.querySelector<HTMLElement>(
				'.fabric-selection-menu__text-edit-viewport',
			);
			if (viewport) {
				viewport.scrollLeft = 0;
			}
		}
		this.schedulePosition(detail.anchorX, detail.anchorY);
	}

	/** Measure, then place. A zero-size first frame used to leave the menu at the static top-left. */
	private schedulePosition(anchorX: number, anchorY: number): void {
		this.lastAnchorX = anchorX;
		this.lastAnchorY = anchorY;
		this.positionAttempts = 0;
		const step = () => {
			if (!this.container) {
				return;
			}
			this.positionNearAnchor(this.lastAnchorX, this.lastAnchorY);
			this.positionAttempts++;
			const unmeasured =
				this.container.offsetWidth < 8 || this.container.offsetHeight < 8;
			if (unmeasured && this.positionAttempts < 8) {
				window.requestAnimationFrame(step);
			}
		};
		step();
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
		if (editMode) {
			return [
				MobileSelectionMenu.buildTextEditStrip(items, { editMode, editable, mode }),
			];
		}
		const row1Ids = ['copy', 'cut', 'paste', 'select_all', 'translate'];
		return [
			MobileSelectionMenu.buildButtonRow(
				items.filter((item) => row1Ids.indexOf(item.id) >= 0),
				{ editMode, editable, mode },
			),
		];
	}

	private static buildTextEditStrip(
		items: MobileSelectionMenuItemSpec[],
		ctx: { editMode: boolean; editable: boolean; mode: 'text' | 'calc' | 'graphic' },
	): HTMLElement {
		const strip = document.createElement('div');
		strip.className = 'fabric-selection-menu__text-edit-strip';
		const viewport = document.createElement('div');
		viewport.className = 'fabric-selection-menu__text-edit-viewport';
		const row = document.createElement('div');
		row.className = 'fabric-selection-menu__text-edit-row';
		items.forEach((item) => {
			if (!MobileSelectionMenu.isItemVisible(item, ctx)) {
				return;
			}
			const button = MobileSelectionMenu.createButton(item);
			button.classList.add('fabric-selection-menu__button--text-edit');
			// Keep the document selection and its keyboard focus when tapping an action.
			button.addEventListener('mousedown', (event) => event.preventDefault());
			row.appendChild(button);
		});
		viewport.appendChild(row);

		const next = document.createElement('button');
		next.type = 'button';
		next.className = 'fabric-selection-menu__next';
		next.setAttribute('aria-label', '显示更多操作');
		next.addEventListener('mousedown', (event) => event.preventDefault());
		next.innerHTML =
			'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 18l6-6-6-6" /></svg>';
		const suppressStripClicks = MobileSelectionMenu.installStripGestureGuard(strip, viewport);
		const updateNextState = () => {
			const hasMore = viewport.scrollLeft + viewport.clientWidth < row.scrollWidth - 1;
			next.disabled = !hasMore;
			next.setAttribute('aria-disabled', hasMore ? 'false' : 'true');
		};
		viewport.addEventListener('scroll', updateNextState, { passive: true });
		next.addEventListener('click', (event) => {
			event.preventDefault();
			event.stopPropagation();
			if (!next.disabled) {
				// Avoid a synthetic click landing on an action while the arrow scrolls.
				suppressStripClicks(250);
				viewport.scrollBy({ left: viewport.clientWidth, behavior: 'smooth' });
			}
		});

		strip.appendChild(viewport);
		strip.appendChild(next);
		return strip;
	}

	private static installStripGestureGuard(
		strip: HTMLElement,
		viewport: HTMLElement,
	): (durationMs: number) => void {
		let startX = 0;
		let startY = 0;
		let pointerActive = false;
		let dragged = false;
		let suppressClickUntil = 0;
		const gestureStart = (x: number, y: number) => {
			startX = x;
			startY = y;
			pointerActive = true;
			dragged = false;
		};
		const gestureMove = (x: number, y: number) => {
			const deltaX = Math.abs(x - startX);
			const deltaY = Math.abs(y - startY);
			if (pointerActive && deltaX > 8 && deltaX > deltaY) {
				dragged = true;
			}
		};
		const gestureEnd = () => {
			if (dragged) {
				// Retain suppression briefly while native momentum scrolling settles.
				suppressClickUntil = Date.now() + 400;
			}
			pointerActive = false;
			dragged = false;
		};
		const gestureCancel = () => {
			if (pointerActive) {
				suppressClickUntil = Date.now() + 400;
			}
			pointerActive = false;
			dragged = false;
		};
		viewport.addEventListener(
			'scroll',
			() => {
				if (pointerActive || dragged || Date.now() < suppressClickUntil) {
					if (pointerActive) {
						dragged = true;
					}
					suppressClickUntil = Date.now() + 400;
				}
			},
			{ passive: true },
		);
		if ('PointerEvent' in window) {
			strip.addEventListener(
				'pointerdown',
				(event: PointerEvent) => {
					if (event.pointerType !== 'mouse') {
						gestureStart(event.clientX, event.clientY);
					} else {
						pointerActive = false;
						dragged = false;
						suppressClickUntil = 0;
					}
				},
				{ passive: true },
			);
			strip.addEventListener(
				'pointermove',
				(event: PointerEvent) => gestureMove(event.clientX, event.clientY),
				{ passive: true },
			);
			strip.addEventListener('pointerup', gestureEnd, { passive: true });
			strip.addEventListener('pointercancel', gestureCancel, { passive: true });
		} else {
			strip.addEventListener(
				'touchstart',
				(event: TouchEvent) => {
					const touch = event.changedTouches[0];
					if (touch) {
						gestureStart(touch.clientX, touch.clientY);
					}
				},
				{ passive: true },
			);
			strip.addEventListener(
				'touchmove',
				(event: TouchEvent) => {
					const touch = event.changedTouches[0];
					if (touch) {
						gestureMove(touch.clientX, touch.clientY);
					}
				},
				{ passive: true },
			);
			strip.addEventListener('touchend', gestureEnd, { passive: true });
			strip.addEventListener('touchcancel', gestureCancel, { passive: true });
		}
		strip.addEventListener(
			'click',
			(event) => {
				if (dragged || Date.now() < suppressClickUntil) {
					event.preventDefault();
					event.stopImmediatePropagation();
				}
			},
			true,
		);
		return (durationMs: number) => {
			suppressClickUntil = Math.max(suppressClickUntil, Date.now() + durationMs);
		};
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
				return ctx.editable && ctx.editMode;
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
			if (item.id === 'paste' || item.id === 'cut') {
				return ctx.editable && ctx.editMode;
			}
			if (item.id === 'delete' || item.id === 'image_edit') {
				return ctx.editable;
			}
			return true;
		}
		if (item.id === 'paste' || item.id === 'cut') {
			return ctx.editable && ctx.editMode;
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

		button.onclick = (event) => {
			if (MobileSelectionMenu.consumeOpeningClick(event)) {
				return;
			}
			MobileSelectionMenu.onItemTap(item);
		};
		return button;
	}

	/** Swallow the clicks iOS synthesizes as the long-press finger lifts onto the new menu. */
	private static consumeOpeningClick(event: Event): boolean {
		if (Date.now() >= MobileSelectionMenu.ignoreActionsUntil) {
			return false;
		}
		event.preventDefault();
		event.stopPropagation();
		return true;
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
			// iOS only writes UIPasteboard when Copy is passed through the native
			// clipboard handler. A bare execCommand falls through to the keyboard-paste warning.
			if (
				clip._selectionType !== 'text' &&
				clip._selectionType !== 'slide' &&
				clip._selectionType !== 'complex'
			) {
				clip.setTextSelectionType('text');
			}
			clip._execCopyCutPaste('copy', '.uno:Copy');
		} else if (actionId === 'cut' || actionId === 'paste') {
			if (!MobileSelectionMenu.isDocEditable() || !MobileSelectionMenu.isEditModeActive()) {
				return;
			}
			clip._execCopyCutPaste(actionId, actionId === 'cut' ? '.uno:Cut' : undefined);
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
		const viewport = window.visualViewport;
		const viewWidth = viewport ? viewport.width : window.innerWidth;
		const viewHeight = viewport ? viewport.height : window.innerHeight;
		const maxWidth = this.textEditMode ? 320 : MobileSelectionMenu.POPUP_MAX_WIDTH_PX;
		const width = Math.min(
			this.menuMode === 'graphic'
				? viewWidth - MobileSelectionMenu.POPUP_MARGIN_PX * 2
				: maxWidth,
			viewWidth - MobileSelectionMenu.POPUP_MARGIN_PX * 2,
		);
		if (this.menuMode === 'calc') {
			this.container.style.width = 'max-content';
		} else if (this.menuMode === 'text' && !this.textEditMode) {
			this.container.style.width = 'max-content';
		} else {
			this.container.style.width = width + 'px';
		}
		if (this.textEditMode) {
			const viewport = this.container.querySelector<HTMLElement>(
				'.fabric-selection-menu__text-edit-viewport',
			);
			const row = this.container.querySelector<HTMLElement>(
				'.fabric-selection-menu__text-edit-row',
			);
			if (viewport && row) {
				row.style.setProperty('--text-edit-item-width', viewport.clientWidth / 5 + 'px');
				const next = this.container.querySelector<HTMLButtonElement>(
					'.fabric-selection-menu__next',
				);
				if (next) {
					const hasMore = viewport.scrollLeft + viewport.clientWidth < row.scrollWidth - 1;
					next.disabled = !hasMore;
					next.setAttribute('aria-disabled', hasMore ? 'false' : 'true');
				}
			}
		}

		const compactReadingMode = this.menuMode === 'text' && !this.textEditMode;
		const menuWidth = this.container.offsetWidth ||
			(this.menuMode === 'calc' ? 72 : compactReadingMode ? 128 : width);
		const menuHeight = this.container.offsetHeight || 200;
		const margin = MobileSelectionMenu.POPUP_MARGIN_PX;
		const bottomReserved =
			MobileSelectionMenu.BOTTOM_TOOLBAR_PX +
			MobileSelectionMenu.readSafeAreaBottom();
		const maxContentBottom = viewHeight - bottomReserved - margin;

		let x = anchorX - menuWidth / 2;
		x = Math.max(margin, Math.min(x, viewWidth - menuWidth - margin));

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

		// position:fixed is layout-viewport relative. iOS pans the visual
		// viewport when the caret is focused, which otherwise pins this menu
		// to the visible top-left.
		const offsetLeft = viewport ? viewport.offsetLeft : 0;
		const offsetTop = viewport ? viewport.offsetTop : 0;
		this.container.style.left = Math.round(x + offsetLeft) + 'px';
		this.container.style.top = Math.round(y + offsetTop) + 'px';
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
