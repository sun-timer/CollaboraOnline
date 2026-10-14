/*
 * Calc cell selection floating menu bridge.
 * Android: native popup (copy/paste/cut/clear/translate).
 * iOS preview: the same actions via the DOM selection menu.
 * A multi-cell range uses cellselectionarea; the menu waits until the
 * pointer is up so the drag is not swallowed by the popup overlay.
 */

class AndroidCalcCellMenu {
	private static lastShowAt = 0;
	private static lastAnchorKey = '';
	private static readonly dedupeMs = 180;
	private static pointerDown = false;
	private static deferUntilPointerUp = false;
	private static calcMenuShownOnIOS = false;
	private static listenersInstalled = false;

	private static isIOS(): boolean {
		return !!(window as any).ThisIsTheiOSApp;
	}

	private static isAndroid(): boolean {
		return !!window.ThisIsTheAndroidApp;
	}

	/** iOS copy menu is preview-only; edit mode keeps the keyboard / text flow. */
	private static allowOnThisPlatform(): boolean {
		if (AndroidCalcCellMenu.isAndroid()) {
			return typeof window.postMobileMessage === 'function';
		}
		if (!AndroidCalcCellMenu.isIOS()) {
			return false;
		}
		if (typeof (window as any).MobileSelectionEvents === 'undefined') {
			return false;
		}
		const readOnly =
			!!app.map &&
			typeof app.map.isReadOnlyMode === 'function' &&
			app.map.isReadOnlyMode();
		return readOnly;
	}

	private static installPointerListeners(): void {
		if (AndroidCalcCellMenu.listenersInstalled || typeof window === 'undefined') {
			return;
		}
		AndroidCalcCellMenu.listenersInstalled = true;
		const onDown = (event: Event) => {
			const target = event.target as HTMLElement | null;
			if (
				target &&
				typeof target.closest === 'function' &&
				target.closest('.fabric-selection-menu, .fabric-selection-menu-overlay')
			) {
				return;
			}
			AndroidCalcCellMenu.pointerDown = true;
			AndroidCalcCellMenu.deferUntilPointerUp = false;
		};
		const onUp = () => {
			if (!AndroidCalcCellMenu.pointerDown) {
				return;
			}
			AndroidCalcCellMenu.pointerDown = false;
			if (!AndroidCalcCellMenu.deferUntilPointerUp) {
				return;
			}
			AndroidCalcCellMenu.deferUntilPointerUp = false;
			window.setTimeout(() => AndroidCalcCellMenu.tryShow(1), 40);
		};
		if (typeof (window as any).PointerEvent === 'function') {
			window.addEventListener('pointerdown', onDown, true);
			window.addEventListener('pointerup', onUp, true);
			window.addEventListener('pointercancel', onUp, true);
		} else {
			window.addEventListener('touchstart', onDown, true);
			window.addEventListener('touchend', onUp, true);
			window.addEventListener('touchcancel', onUp, true);
		}
	}

	/** Active cell, or the dragged range when it extends past that cell. */
	private static anchorRectangle(): {
		pX1: number;
		pX2: number;
		pY1: number;
		pY2: number;
	} | null {
		const area =
			app.map && app.map._docLayer && app.map._docLayer._cellSelectionArea;
		const cursor =
			app.calc.cellCursorVisible && app.calc.cellCursorRectangle
				? app.calc.cellCursorRectangle
				: null;
		if (area && area.pWidth > 1 && area.pHeight > 1) {
			if (
				!cursor ||
				area.pWidth > cursor.pWidth + 2 ||
				area.pHeight > cursor.pHeight + 2
			) {
				return area;
			}
		}
		return cursor;
	}

	static hide(): void {
		AndroidCalcCellMenu.deferUntilPointerUp = false;
		if (AndroidCalcCellMenu.isAndroid() && typeof window.postMobileMessage === 'function') {
			window.postMobileMessage('CALC_CELL_POPUP hide');
		}
		if (AndroidCalcCellMenu.isIOS() && AndroidCalcCellMenu.calcMenuShownOnIOS) {
			const events = (window as any).MobileSelectionEvents;
			if (events && typeof events.broadcastHide === 'function') {
				events.broadcastHide();
			}
			AndroidCalcCellMenu.calcMenuShownOnIOS = false;
		}
	}

	static tryShow(retry = 0): void {
		AndroidCalcCellMenu.installPointerListeners();
		if (!AndroidCalcCellMenu.allowOnThisPlatform()) {
			return;
		}
		if (AndroidCalcCellMenu.pointerDown) {
			AndroidCalcCellMenu.deferUntilPointerUp = true;
			return;
		}
		if (!app.map || app.map.getDocType() !== 'spreadsheet') {
			AndroidCalcCellMenu.reportSkip(
				'not_calc doc=' + (app.map ? app.map.getDocType() : 'no_map'),
				retry,
			);
			return;
		}
		if (app.map.wholeRowSelected || app.map.wholeColumnSelected) {
			AndroidCalcCellMenu.hide();
			AndroidCalcCellMenu.reportSkip('whole_row_col', retry);
			return;
		}
		const rect = AndroidCalcCellMenu.anchorRectangle();
		if (!rect) {
			if (retry < 8) {
				window.setTimeout(() => AndroidCalcCellMenu.tryShow(retry + 1), 60);
			} else {
				AndroidCalcCellMenu.reportSkip('no_cell_cursor', retry);
			}
			return;
		}
		if (
			app.definitions.graphicSelection &&
			app.definitions.graphicSelection.handlesSection
		) {
			AndroidCalcCellMenu.reportSkip('graphic_selection', retry);
			return;
		}
		const docLayer = app.map._docLayer;
		if (!docLayer || !app.sectionContainer) {
			AndroidCalcCellMenu.reportSkip('no_doc_layer', retry);
			return;
		}

		const layout = app.activeDocument && app.activeDocument.activeLayout;
		const viewed = layout && layout.viewedRectangle;
		if (!viewed) {
			AndroidCalcCellMenu.reportSkip('no_viewed_rect', retry);
			return;
		}
		// pX/pY are core pixels. Passing them through _twipsToPixels treats
		// pixels as twips and collapses every cell to the top-left.
		// Match AndroidSelectionMenu: view pixels, then CSS, then physical.
		const docAnchor = app.sectionContainer.getDocumentAnchor
			? app.sectionContainer.getDocumentAnchor()
			: [0, 0];
		const originX = viewed.pX1 - (docAnchor[0] || 0);
		const originY = viewed.pY1 - (docAnchor[1] || 0);
		const viewX = (rect.pX1 + rect.pX2) / 2 - originX;
		const viewTop = rect.pY1 - originY;
		const viewBottom = rect.pY2 - originY;
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const scale = app.dpiScale || 1;
		const cssX = viewX / scale + canvasRect.x;
		const cssY = viewTop / scale + canvasRect.y;
		const cssBottomY = viewBottom / scale + canvasRect.y;
		const anchorKey =
			Math.round(cssX) + ',' + Math.round(cssY) + ',' + Math.round(cssBottomY);
		const now = Date.now();
		if (
			now - AndroidCalcCellMenu.lastShowAt < AndroidCalcCellMenu.dedupeMs &&
			retry === 0 &&
			anchorKey === AndroidCalcCellMenu.lastAnchorKey
		) {
			AndroidCalcCellMenu.reportSkip('dedupe', retry);
			return;
		}

		if (AndroidCalcCellMenu.isIOS()) {
			const events = (window as any).MobileSelectionEvents;
			const menu = (window as any).MobileSelectionMenu;
			if (menu && typeof menu.iosKeyboardOpen === 'function' && menu.iosKeyboardOpen()) {
				events.broadcastHide();
				AndroidCalcCellMenu.calcMenuShownOnIOS = false;
				return;
			}
			events.broadcastShow(
				{
					anchorX: cssX,
					anchorY: cssY,
					anchorBottomY: cssBottomY,
				},
				'',
				'calc',
			);
			AndroidCalcCellMenu.calcMenuShownOnIOS = true;
		} else {
			const anchorX = Math.round(cssX * scale);
			const anchorY = Math.round(cssY * scale);
			const anchorBottomY = Math.round(cssBottomY * scale);
			window.postMobileMessage(
				'CALC_CELL_POPUP show ' +
					anchorX +
					' ' +
					anchorY +
					' ' +
					anchorBottomY,
			);
			window.postMobileMessage(
				'CALC_CELL_DISPATCH show=' + anchorX + ',' + anchorY + ',' + anchorBottomY,
			);
		}
		AndroidCalcCellMenu.lastShowAt = now;
		AndroidCalcCellMenu.lastAnchorKey = anchorKey;
	}

	private static reportSkip(reason: string, retry: number): void {
		// Android logcat only. On iOS, postMobileMessage falls through to the
		// document socket, and an unknown CALC_CELL_DISPATCH becomes an error dialog.
		if (
			retry > 0 ||
			!window.ThisIsTheAndroidApp ||
			typeof window.postMobileMessage !== 'function'
		) {
			return;
		}
		window.postMobileMessage('CALC_CELL_DISPATCH skip=' + reason);
	}
}

(window as any).AndroidCalcCellMenu = AndroidCalcCellMenu;
