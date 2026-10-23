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
	/** Set when a long-press asks to show before the cell cursor message arrives. */
	private static pendingLongPress = false;
	private static iosTapRequest: { docLayer: any; part: number; cell: string; at: number } | null = null;
	private static iosRequestGeneration = 0;
	private static iosDismissGeneration = 0;
	private static readonly iosTapRequestLifetimeMs = 5000;
	private static readonly iosRangeSettleMs = 150;

	private static isIOS(): boolean {
		return !!(window as any).ThisIsTheiOSApp;
	}

	private static isAndroid(): boolean {
		return !!window.ThisIsTheAndroidApp;
	}

	private static isIOSCalc(): boolean {
		return AndroidCalcCellMenu.isIOS() && app.map?._docLayer?._docType === 'spreadsheet';
	}

	/** A cursor update can satisfy an explicit request, but cannot create one. */
	static onIOSSelectionChanged(): void {
		if (AndroidCalcCellMenu.isIOSCalc()) AndroidCalcCellMenu.tryShow();
	}

	static showForIOSTap(docLayer: any, part: number, cell: string): void {
		if (!AndroidCalcCellMenu.isIOSCalc()) return;
		AndroidCalcCellMenu.iosRequestGeneration++;
		AndroidCalcCellMenu.pendingLongPress = false;
		AndroidCalcCellMenu.iosTapRequest = { docLayer, part, cell, at: Date.now() };
		AndroidCalcCellMenu.tryShow();
	}

	/**
	 * After a selection handle is released. The final handle position is only
	 * posted on release, so wait for core's reply to anchor on the final range.
	 * A new touch dismisses and cancels it; a transient EMPTY cursor does not.
	 */
	static showAfterIOSRangeSelection(): void {
		if (!AndroidCalcCellMenu.isIOSCalc()) return;
		AndroidCalcCellMenu.dismiss();
		const generation = AndroidCalcCellMenu.iosDismissGeneration;
		window.setTimeout(() => {
			if (!AndroidCalcCellMenu.isIOSCalc() || generation !== AndroidCalcCellMenu.iosDismissGeneration) return;
			AndroidCalcCellMenu.pendingLongPress = true;
			AndroidCalcCellMenu.tryShow(1);
		}, AndroidCalcCellMenu.iosRangeSettleMs);
	}

	/** Hide a stale visible menu while keeping a fresh tap request through EMPTY. */
	static onIOSCellCursorCleared(): void {
		if (!AndroidCalcCellMenu.isIOSCalc()) {
			AndroidCalcCellMenu.hide();
			return;
		}
		const request = AndroidCalcCellMenu.iosTapRequest;
		const keepRequest = !!request && request.docLayer === app.map._docLayer &&
			request.part === app.map._docLayer._selectedPart &&
			Date.now() - request.at <= AndroidCalcCellMenu.iosTapRequestLifetimeMs;
		AndroidCalcCellMenu.hide();
		if (keepRequest) {
			AndroidCalcCellMenu.iosTapRequest = request;
			AndroidCalcCellMenu.iosRequestGeneration++;
		}
	}

	private static retryShow(retry: number, delay: number): void {
		const iosCalc = AndroidCalcCellMenu.isIOSCalc();
		const generation = AndroidCalcCellMenu.iosRequestGeneration;
		window.setTimeout(() => {
			if (iosCalc && (!AndroidCalcCellMenu.isIOSCalc() || generation !== AndroidCalcCellMenu.iosRequestGeneration)) return;
			AndroidCalcCellMenu.tryShow(retry);
		}, delay);
	}

	/**
	 * Android follows the cell cursor. iOS Calc requires an explicit slow
	 * second tap or long-press; cursor updates only resolve that request.
	 */
	private static allowOnThisPlatform(): boolean {
		if (AndroidCalcCellMenu.isAndroid()) {
			return typeof window.postMobileMessage === 'function';
		}
		if (AndroidCalcCellMenu.isIOS()) {
			return typeof (window as any).MobileSelectionEvents !== 'undefined';
		}
		return false;
	}

	/** After a calc long-press placed the cell cursor (no text range to anchor). */
	static showAfterSelection(): void {
		if (AndroidCalcCellMenu.isIOSCalc()) (window as any).AndroidCalcTap?.resetIOSState();
		// Finger-up already happened. A stale pointerdown flag would defer the
		// menu until a pointerup that will not come again.
		AndroidCalcCellMenu.pointerDown = false;
		AndroidCalcCellMenu.deferUntilPointerUp = false;
		AndroidCalcCellMenu.pendingLongPress = true;
		AndroidCalcCellMenu.tryShow(0);
	}

	/** Long-press in edit mode: compact bar only when the press hits the selection. */
	static showFromLongPress(point: any): void {
		if (AndroidCalcCellMenu.isIOSCalc()) (window as any).AndroidCalcTap?.resetIOSState();
		if (!AndroidCalcCellMenu.pointInsideSelection(point)) {
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.hide();
			return;
		}
		AndroidCalcCellMenu.pendingLongPress = true;
		AndroidCalcCellMenu.tryShow(0);
	}

	static dismiss(): void {
		AndroidCalcCellMenu.iosDismissGeneration++;
		AndroidCalcCellMenu.pendingLongPress = false;
		AndroidCalcCellMenu.hide();
	}

	private static pointInsideSelection(point: any): boolean {
		if (!point) {
			return false;
		}
		const cursor =
			app.calc && app.calc.cellCursorVisible && app.calc.cellCursorRectangle;
		if (cursor && AndroidCalcCellMenu.rectContains(cursor, point)) {
			return true;
		}
		const area = app.map && app.map._docLayer && app.map._docLayer._cellSelectionArea;
		return !!(area && AndroidCalcCellMenu.rectContains(area, point));
	}

	private static rectContains(rect: any, point: any): boolean {
		const px = point.pX;
		const py = point.pY;
		if (typeof rect.pContainsPoint === 'function' && point.pToArray) {
			return rect.pContainsPoint(point.pToArray());
		}
		if (typeof rect.containsPoint === 'function' && point.toArray) {
			return rect.containsPoint(point.toArray());
		}
		if (rect.pX1 === undefined || px === undefined) {
			return false;
		}
		return px >= rect.pX1 && px <= rect.pX2 && py >= rect.pY1 && py <= rect.pY2;
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
			AndroidCalcCellMenu.retryShow(1, 40);
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
		if (AndroidCalcCellMenu.isIOSCalc()) {
			AndroidCalcCellMenu.iosTapRequest = null;
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.iosRequestGeneration++;
		}
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
		if (AndroidCalcCellMenu.isIOSCalc()) {
			const request = AndroidCalcCellMenu.iosTapRequest;
			if (!request && !AndroidCalcCellMenu.pendingLongPress) return;
			if (request) {
				if (request.docLayer !== app.map._docLayer || request.part !== app.map._docLayer._selectedPart) {
					AndroidCalcCellMenu.dismiss();
					return;
				}
				if (Date.now() - request.at > AndroidCalcCellMenu.iosTapRequestLifetimeMs) {
					AndroidCalcCellMenu.dismiss();
					return;
				}
				const address = app.calc.cellAddress;
				if (!address || !app.calc.cellCursorVisible || request.cell !== address.x + ':' + address.y) return;
			}
		}
		AndroidCalcCellMenu.installPointerListeners();
		if (!AndroidCalcCellMenu.allowOnThisPlatform()) {
			return;
		}
		if (AndroidCalcCellMenu.pointerDown) {
			AndroidCalcCellMenu.deferUntilPointerUp = true;
			return;
		}
		if (!app.map || app.map.getDocType() !== 'spreadsheet') {
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.reportSkip(
				'not_calc doc=' + (app.map ? app.map.getDocType() : 'no_map'),
				retry,
			);
			return;
		}
		if (app.map.wholeRowSelected || app.map.wholeColumnSelected) {
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.hide();
			AndroidCalcCellMenu.reportSkip('whole_row_col', retry);
			return;
		}
		const rect = AndroidCalcCellMenu.anchorRectangle();
		if (!rect) {
			if (retry < 8) {
				AndroidCalcCellMenu.retryShow(retry + 1, 60);
			} else {
				AndroidCalcCellMenu.pendingLongPress = false;
				AndroidCalcCellMenu.reportSkip('no_cell_cursor', retry);
			}
			return;
		}
		if (
			app.definitions.graphicSelection &&
			app.definitions.graphicSelection.handlesSection
		) {
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.reportSkip('graphic_selection', retry);
			return;
		}
		const docLayer = app.map._docLayer;
		if (!docLayer || !app.sectionContainer) {
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.reportSkip('no_doc_layer', retry);
			return;
		}

		const layout = app.activeDocument && app.activeDocument.activeLayout;
		const viewed = layout && layout.viewedRectangle;
		if (!viewed) {
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.reportSkip('no_viewed_rect', retry);
			return;
		}
		// pX/pY are core pixels. Passing them through _twipsToPixels treats
		// pixels as twips and collapses every cell to the top-left.
		// Match AndroidSelectionMenu: view pixels, then CSS, then physical.
		const docAnchor = app.sectionContainer.getDocumentAnchor
			? app.sectionContainer.getDocumentAnchor()
			: [0, 0];
		let viewedOriginX = viewed.pX1;
		let viewedOriginY = viewed.pY1;
		if (AndroidCalcCellMenu.isIOSCalc()) {
			// Cell cursor rectangles stay in the frozen pane's zero-origin space.
			// Match Cursor.updatePositionAndSize and MouseControl.refreshPosition
			// when mapping that document rectangle back to the canvas.
			if (app.isXOrdinateInFrozenPane && app.isXOrdinateInFrozenPane(rect.pX1))
				viewedOriginX = 0;
			if (app.isYOrdinateInFrozenPane && app.isYOrdinateInFrozenPane(rect.pY1))
				viewedOriginY = 0;
		}
		const originX = viewedOriginX - (docAnchor[0] || 0);
		const originY = viewedOriginY - (docAnchor[1] || 0);
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
			AndroidCalcCellMenu.pendingLongPress = false;
			AndroidCalcCellMenu.reportSkip('dedupe', retry);
			return;
		}

		if (AndroidCalcCellMenu.isIOS()) {
			const events = (window as any).MobileSelectionEvents;
			// Same as preview: the cell cursor itself is enough. An open
			// formula keyboard must not swallow the menu in edit mode.
			AndroidCalcCellMenu.pendingLongPress = false;
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
			AndroidCalcCellMenu.pendingLongPress = false;
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
