/*
 * Android native selection menu bridge (preview / read-only UI mode).
 * Self-contained hooks — do not patch CanvasSectionContainer / CanvasTileLayer.
 */

class AndroidSelectionMenu {
	private static hooksInstalled = false;
	private static pendingLongPressSelection = false;
	private static selectionGestureComplete = false;
	private static nativeSelectionDragActive = false;
	private static lastDragSelectionUpdateAt = 0;
	private static readonly dragSelectionThrottleMs = 60;
	private static lastSelectionStartAt = 0;
	private static readonly ignoreEmptyAfterStartMs = 500;
	private static selectionStartTwips: { x: number; y: number } | null = null;
	/** True once core acknowledges `selecttext start` during the current gesture. */
	private static selectionStartAcknowledged = false;
	private static readonly minSelectionSpanTwips = 80;
	private static tryShowRetryTimer = 0;
	private static readonly tryShowRetryDelayMs = 100;
	private static readonly tryShowMaxRetries = 30;

	/** True while a native long-press selection gesture is in flight. */
	static isLongPressGesturePending(): boolean {
		return AndroidSelectionMenu.pendingLongPressSelection;
	}

	/** Debug trace; reaches logcat via WebChromeClient.onConsoleMessage. */
	private static debugLog(msg: string): void {
		try {
			if (typeof console !== 'undefined' && typeof console.log === 'function') {
				console.log('[selection_menu] ' + msg);
			}
		} catch (_e) {
			// Best-effort only.
		}
	}

	static hide(): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		window.postMobileMessage('SELECTIONMENU hide');
	}

	static markNativeLongPress(): void {
		// Reserved for native long-press guard extensions.
	}

	private static isPreviewWriterMode(): boolean {
		return (
			!!app.map &&
			typeof app.map.isReadOnlyMode === 'function' &&
			app.map.isReadOnlyMode() &&
			app.map.getDocType() === 'text'
		);
	}

	private static isWriterDoc(): boolean {
		return !!app.map && app.map.getDocType() === 'text';
	}

	private static isEditMode(): boolean {
		return (
			!!app.map &&
			typeof app.map.isReadOnlyMode === 'function' &&
			!app.map.isReadOnlyMode()
		);
	}

	/**
	 * In preview Writer, long-press always means "select text for the AI menu",
	 * so the browser's 550ms long-press must never fall through to a right-click.
	 * In edit-mode Writer we only suppress while a native selection gesture is in
	 * flight, so a long-press that did NOT become a selection still keeps the
	 * normal edit context menu.
	 */
	private static shouldSuppressContextMenu(): boolean {
		if (!AndroidSelectionMenu.isWriterDoc()) {
			return false;
		}
		return (
			AndroidSelectionMenu.isPreviewWriterMode() ||
			AndroidSelectionMenu.nativeSelectionDragActive ||
			AndroidSelectionMenu.pendingLongPressSelection
		);
	}

	private static clearLocalTextSelection(): void {
		try {
			if (TextSelections && typeof TextSelections.deactivate === 'function') {
				TextSelections.deactivate();
			}
			if (
				app.activeDocument &&
				app.activeDocument.activeView &&
				typeof app.activeDocument.activeView.clearTextSelection === 'function'
			) {
				app.activeDocument.activeView.clearTextSelection();
			}
		} catch (_e) {
			// Best-effort cleanup only.
		}
	}

	/** Reset bridge flags and hide menu (no selecttext end). */
	static cancelGesture(): void {
		AndroidSelectionMenu.pendingLongPressSelection = false;
		AndroidSelectionMenu.selectionGestureComplete = false;
		AndroidSelectionMenu.nativeSelectionDragActive = false;
		AndroidSelectionMenu.selectionStartTwips = null;
		AndroidSelectionMenu.selectionStartAcknowledged = false;
		AndroidSelectionMenu.lastDragSelectionUpdateAt = 0;
		AndroidSelectionMenu.clearTryShowRetry();
		AndroidSelectionMenu.hide();
	}

	private static resetForNewGesture(): void {
		AndroidSelectionMenu.cancelGesture();
		AndroidSelectionMenu.clearLocalTextSelection();
	}

	/** WebView-local touch coords (physical px) → document twips. */
	private static viewPointToDocumentTwips(
		viewX: number,
		viewY: number,
	): { x: number; y: number } | null {
		if (!app.sectionContainer) {
			return null;
		}
		return app.sectionContainer.clientViewPointToDocumentTwips(viewX, viewY);
	}

	/** Tiles-section locale point → document twips (same math as MouseControl). */
	private static sectionPointToDocumentTwips(
		point: cool.SimplePoint,
	): { x: number; y: number } | null {
		if (!app.sectionContainer || !app.activeDocument || !app.map._docLayer) {
			return null;
		}

		let documentPoint = point.clone();
		documentPoint.pX +=
			-app.activeDocument.activeLayout.viewedRectangle.pX1 +
			app.sectionContainer.getDocumentAnchor()[0];
		documentPoint.pY +=
			-app.activeDocument.activeLayout.viewedRectangle.pY1 +
			app.sectionContainer.getDocumentAnchor()[1];
		documentPoint =
			app.activeDocument.activeLayout.canvasToDocumentPoint(documentPoint);

		if (Number.isNaN(documentPoint.x) || Number.isNaN(documentPoint.y)) {
			return null;
		}

		return {
			x: Math.round(documentPoint.x),
			y: Math.round(documentPoint.y),
		};
	}

	/**
	 * Select word at document twips via selecttext start.
	 * Shared by long-press and double-tap. Do NOT send selecttext reset here —
	 * LOK reset collapses the caret to document start.
	 */
	private static selectWordAtTwips(pos: { x: number; y: number }): void {
		AndroidSelectionMenu.lastSelectionStartAt = Date.now();
		AndroidSelectionMenu.selectionStartTwips = { x: pos.x, y: pos.y };
		AndroidSelectionMenu.selectionStartAcknowledged = false;
		AndroidSelectionMenu.debugLog(
			'word_select twips=' + pos.x + ',' + pos.y,
		);
		app.map._docLayer._postSelectTextEvent('start', pos.x, pos.y);
	}

	private static prepareWordSelectionGesture(): void {
		AndroidSelectionMenu.resetForNewGesture();
		const findBridge = (window as any).AndroidFindReplaceBridge;
		if (
			findBridge &&
			typeof findBridge.clearSuppressSelectionMenu === 'function'
		) {
			findBridge.clearSuppressSelectionMenu();
		}
		AndroidSelectionMenu.pendingLongPressSelection = true;
	}

	/** Long-press: selecttext start at press point (word select); end on drag/up. */
	static startTextSelectionAt(viewX: number, viewY: number): void {
		const pos = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
		if (!pos) {
			AndroidSelectionMenu.cancelGesture();
			return;
		}

		AndroidSelectionMenu.selectWordAtTwips(pos);
	}

	/**
	 * Double-tap word select (tiles-section point from MouseControl).
	 * Uses the same selecttext reset+start path as long-press.
	 */
	static onDoubleTapAtSectionPoint(point: cool.SimplePoint): void {
		if (!window.ThisIsTheAndroidApp || !AndroidSelectionMenu.isWriterDoc()) {
			return;
		}

		AndroidSelectionMenu.prepareWordSelectionGesture();
		AndroidSelectionMenu.selectionGestureComplete = true;
		AndroidSelectionMenu.nativeSelectionDragActive = false;

		const pos = AndroidSelectionMenu.sectionPointToDocumentTwips(point);
		if (!pos) {
			AndroidSelectionMenu.cancelGesture();
			return;
		}

		AndroidSelectionMenu.debugLog(
			'double_tap word_select at ' + pos.x + ',' + pos.y,
		);
		AndroidSelectionMenu.selectWordAtTwips(pos);
		AndroidSelectionMenu.scheduleTryShowAfterGesture();
	}

	private static isZeroWidthTwips(
		start: { x: number; y: number },
		end: { x: number; y: number },
	): boolean {
		return (
			Math.abs(end.x - start.x) < AndroidSelectionMenu.minSelectionSpanTwips &&
			Math.abs(end.y - start.y) < AndroidSelectionMenu.minSelectionSpanTwips
		);
	}

	private static hasNonDegenerateSelection(): boolean {
		if (!TextSelections || !TextSelections.isActive()) {
			return false;
		}
		const startRect = TextSelections.getStartRectangle();
		const endRect = TextSelections.getEndRectangle();
		if (!startRect || !endRect) {
			return false;
		}
		const left = Math.min(startRect.pX1, endRect.pX1, startRect.pX2, endRect.pX2);
		const right = Math.max(startRect.pX1, endRect.pX1, startRect.pX2, endRect.pX2);
		const top = Math.min(startRect.pY1, endRect.pY1, startRect.pY2, endRect.pY2);
		const bottom = Math.max(startRect.pY1, endRect.pY1, startRect.pY2, endRect.pY2);
		return (
			right - left >= AndroidSelectionMenu.minSelectionSpanTwips ||
			bottom - top >= AndroidSelectionMenu.minSelectionSpanTwips
		);
	}

	static updateTextSelectionEndAt(
		viewX: number,
		viewY: number,
		force: boolean = false,
	): void {
		if (!AndroidSelectionMenu.nativeSelectionDragActive) {
			return;
		}
		// Do not extend before core has processed `selecttext start`; otherwise
		// `end` may anchor from the old caret (often document start).
		if (!AndroidSelectionMenu.selectionStartAcknowledged) {
			if (!force) {
				return;
			}
			const anchor = AndroidSelectionMenu.selectionStartTwips;
			if (!anchor) {
				return;
			}
			app.map._docLayer._postSelectTextEvent('start', anchor.x, anchor.y);
		}
		if (!AndroidSelectionMenu.isWriterDoc()) {
			AndroidSelectionMenu.cancelGesture();
			return;
		}
		const now = Date.now();
		if (
			!force &&
			now - AndroidSelectionMenu.lastDragSelectionUpdateAt <
				AndroidSelectionMenu.dragSelectionThrottleMs
		) {
			return;
		}
		const pos = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
		if (!pos) {
			return;
		}
		// Never send `selecttext end` at (or near) the start anchor: core collapses
		// the word selection created by `selecttext start` to EMPTY (log evidence:
		// start->rects then same-point end->EMPTY), which is the "selection flashes
		// then disappears" bug. Only extend once the finger really moved.
		const start = AndroidSelectionMenu.selectionStartTwips;
		if (
			start !== null &&
			Math.abs(pos.x - start.x) < AndroidSelectionMenu.minSelectionSpanTwips &&
			Math.abs(pos.y - start.y) < AndroidSelectionMenu.minSelectionSpanTwips
		) {
			return;
		}
		AndroidSelectionMenu.lastDragSelectionUpdateAt = now;
		app.map._docLayer._postSelectTextEvent('end', pos.x, pos.y);
	}

	/** Finger up: finalize selecttext end; menu only after gesture complete + textselection:. */
	static finishTextSelectionDrag(viewX?: number, viewY?: number): void {
		if (!AndroidSelectionMenu.nativeSelectionDragActive) {
			return;
		}

		let endTwips: { x: number; y: number } | null = null;
		if (typeof viewX === 'number' && typeof viewY === 'number') {
			endTwips = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
			if (endTwips) {
				const start = AndroidSelectionMenu.selectionStartTwips;
				const dragged =
					start === null ||
					Math.abs(endTwips.x - start.x) >=
						AndroidSelectionMenu.minSelectionSpanTwips ||
					Math.abs(endTwips.y - start.y) >=
						AndroidSelectionMenu.minSelectionSpanTwips;
				if (dragged) {
					AndroidSelectionMenu.updateTextSelectionEndAt(viewX, viewY, true);
				}
				// Stationary long-press: `selecttext start` already word-selected at the
				// anchor; do not re-send `end` at the same point — it can make core
				// collapse the selection to a caret (selection flash-away).
				AndroidSelectionMenu.debugLog(
					'finish start=' + (start ? start.x + ',' + start.y : 'null') +
						' end=' + endTwips.x + ',' + endTwips.y +
						' dragged=' + dragged);
			}
		}

		AndroidSelectionMenu.nativeSelectionDragActive = false;
		AndroidSelectionMenu.selectionGestureComplete = true;

		// Long-press word select often has identical start/end twips; wait for
		// textselection: instead of cancelling here. onEmptyTextSelection handles
		// genuine zero-width failures.
		AndroidSelectionMenu.scheduleTryShowAfterGesture();
	}

	/**
	 * Long-press in preview mode (WebView-local viewX/viewY).
	 * Invoked from LOActivity / COWebView only when native preview mode is active.
	 */
	static onLongPressAt(viewX: number, viewY: number): void {
		if (!window.ThisIsTheAndroidApp || !AndroidSelectionMenu.isWriterDoc()) {
			return;
		}

		AndroidSelectionMenu.prepareWordSelectionGesture();
		AndroidSelectionMenu.markNativeLongPress();
		AndroidSelectionMenu.selectionGestureComplete = false;
		AndroidSelectionMenu.nativeSelectionDragActive = true;
		AndroidSelectionMenu.lastDragSelectionUpdateAt = 0;
		AndroidSelectionMenu.startTextSelectionAt(viewX, viewY);
	}

	/**
	 * Show menu anchored above the current text selection.
	 * Only when TextSelections is active with a non-degenerate range.
	 */
	static tryShow(): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		if (!app.map || typeof app.map.isReadOnlyMode !== 'function') {
			return;
		}
		// Both preview (read-only) and edit mode are allowed for Writer docs.
		if (app.map.getDocType() !== 'text') {
			return;
		}
		if (!AndroidSelectionMenu.hasNonDegenerateSelection()) {
			return;
		}

		const startRect = TextSelections.getStartRectangle();
		const endRect = TextSelections.getEndRectangle();
		if (!startRect || !endRect || !app.sectionContainer) {
			return;
		}

		const topViewY = Math.min(
			startRect.v1Y,
			startRect.v2Y,
			endRect.v1Y,
			endRect.v2Y,
		);
		const leftViewX = Math.min(
			startRect.v1X,
			startRect.v3X,
			endRect.v1X,
			endRect.v3X,
		);
		const rightViewX = Math.max(
			startRect.v2X,
			startRect.v4X,
			endRect.v2X,
			endRect.v4X,
		);
		const centerViewX = (leftViewX + rightViewX) / 2;
		const bottomViewY = Math.max(
			startRect.v3Y,
			startRect.v4Y,
			endRect.v3Y,
			endRect.v4Y,
		);
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const scale = app.dpiScale || 1;
		const cssX = Math.round(centerViewX / scale) + canvasRect.x;
		const cssY = Math.round(topViewY / scale) + canvasRect.y;
		const cssBottomY = Math.round(bottomViewY / scale) + canvasRect.y;
		const anchor = AndroidSelectionMenu.clampAnchorInCss(cssX, cssY, cssBottomY);

		window.postMobileMessage(
			'SELECTIONMENU show ' +
				Math.round(anchor.x * scale) +
				' ' +
				Math.round(anchor.y * scale) +
				' ' +
				Math.round(anchor.bottomY * scale),
		);
		AndroidSelectionMenu.pendingLongPressSelection = false;
		AndroidSelectionMenu.selectionStartTwips = null;
	}

	private static clampAnchorInCss(
		x: number,
		y: number,
		bottomY: number,
	): { x: number; y: number; bottomY: number } {
		const canvas = document.getElementById('canvas-container');
		if (!canvas) {
			return { x, y, bottomY };
		}
		const rect = canvas.getBoundingClientRect();
		const margin = 8;
		const clampY = (value: number) =>
			Math.round(
				Math.max(rect.top + margin, Math.min(value, rect.bottom - margin)),
			);
		return {
			x: Math.round(
				Math.max(rect.left + margin, Math.min(x, rect.right - margin)),
			),
			y: clampY(y),
			bottomY: clampY(bottomY),
		};
	}

	private static clampAnchorToCanvas(x: number, y: number): { x: number; y: number } {
		const canvas = document.getElementById('canvas-container');
		if (!canvas) {
			return { x, y };
		}
		const rect = canvas.getBoundingClientRect();
		const margin = 8;
		const scale = app.dpiScale || 1;
		return {
			x: Math.round(
				Math.max(
					rect.left * scale + margin,
					Math.min(x, rect.right * scale - margin),
				),
			),
			y: Math.round(
				Math.max(
					rect.top * scale + margin,
					Math.min(y, rect.bottom * scale - margin),
				),
			),
		};
	}

	private static clearTryShowRetry(): void {
		if (AndroidSelectionMenu.tryShowRetryTimer) {
			window.clearTimeout(AndroidSelectionMenu.tryShowRetryTimer);
			AndroidSelectionMenu.tryShowRetryTimer = 0;
		}
	}

	private static scheduleTryShowAfterGesture(): void {
		if (
			!AndroidSelectionMenu.pendingLongPressSelection ||
			!AndroidSelectionMenu.selectionGestureComplete ||
			AndroidSelectionMenu.nativeSelectionDragActive
		) {
			return;
		}
		AndroidSelectionMenu.clearTryShowRetry();
		let attempts = 0;
		const tick = (): void => {
			AndroidSelectionMenu.tryShowRetryTimer = 0;
			if (
				!AndroidSelectionMenu.pendingLongPressSelection ||
				!AndroidSelectionMenu.selectionGestureComplete ||
				AndroidSelectionMenu.nativeSelectionDragActive
			) {
				return;
			}
			if (AndroidSelectionMenu.hasNonDegenerateSelection()) {
				AndroidSelectionMenu.tryShow();
				return;
			}
			attempts++;
			if (attempts < AndroidSelectionMenu.tryShowMaxRetries) {
				AndroidSelectionMenu.tryShowRetryTimer = window.setTimeout(
					tick,
					AndroidSelectionMenu.tryShowRetryDelayMs,
				);
			}
		};
		AndroidSelectionMenu.tryShowRetryTimer = window.setTimeout(tick, 50);
	}

	/** Show menu when core reports a selection without our long-press gesture flags. */
	private static scheduleTryShowFromCoreSelection(): void {
		AndroidSelectionMenu.clearTryShowRetry();
		let attempts = 0;
		const tick = (): void => {
			AndroidSelectionMenu.tryShowRetryTimer = 0;
			if (AndroidSelectionMenu.hasNonDegenerateSelection()) {
				AndroidSelectionMenu.tryShow();
				return;
			}
			attempts++;
			if (attempts < AndroidSelectionMenu.tryShowMaxRetries) {
				AndroidSelectionMenu.tryShowRetryTimer = window.setTimeout(
					tick,
					AndroidSelectionMenu.tryShowRetryDelayMs,
				);
			}
		};
		AndroidSelectionMenu.tryShowRetryTimer = window.setTimeout(tick, 50);
	}

	private static shouldUseGestureTryShow(): boolean {
		return (
			AndroidSelectionMenu.pendingLongPressSelection &&
			AndroidSelectionMenu.selectionGestureComplete &&
			!AndroidSelectionMenu.nativeSelectionDragActive
		);
	}

	private static onEmptyTextSelection(): void {
		if (AndroidSelectionMenu.nativeSelectionDragActive) {
			return;
		}
		const now = Date.now();
		if (
			AndroidSelectionMenu.pendingLongPressSelection &&
			!AndroidSelectionMenu.selectionGestureComplete &&
			now - AndroidSelectionMenu.lastSelectionStartAt <
				AndroidSelectionMenu.ignoreEmptyAfterStartMs
		) {
			return;
		}
		if (AndroidSelectionMenu.pendingLongPressSelection) {
			AndroidSelectionMenu.cancelGesture();
			AndroidSelectionMenu.clearLocalTextSelection();
		}
	}

	/** Install Android-only hooks without modifying upstream canvas/tile sources. */
	static install(): void {
		if (!window.ThisIsTheAndroidApp || AndroidSelectionMenu.hooksInstalled) {
			return;
		}
		AndroidSelectionMenu.hooksInstalled = true;

		document.addEventListener(
			'contextmenu',
			(e: Event) => {
				if (AndroidSelectionMenu.shouldSuppressContextMenu()) {
					e.preventDefault();
					e.stopPropagation();
				}
			},
			true,
		);

		const installMouseControlHook = () => {
			if (typeof MouseControl === 'undefined') {
				window.setTimeout(installMouseControlHook, 200);
				return;
			}

			const originalOnContextMenu = MouseControl.prototype.onContextMenu;
			MouseControl.prototype.onContextMenu = function (
				point: cool.SimplePoint,
				e: MouseEvent,
			): void {
				if (
					window.ThisIsTheAndroidApp &&
					AndroidSelectionMenu.shouldSuppressContextMenu()
				) {
					e.preventDefault();
					e.stopPropagation();
					return;
				}
				return originalOnContextMenu.call(this, point, e);
			};

			const originalOnClick = MouseControl.prototype.onClick;
			MouseControl.prototype.onClick = function (
				point: cool.SimplePoint,
				e: MouseEvent,
			): void {
				if (
					!window.ThisIsTheAndroidApp ||
					!app.map ||
					app.map.getDocType() !== 'text'
				) {
					return originalOnClick.call(this, point, e);
				}

				app.map.fire('closepopups');
				app.map.fire('editorgotfocus');

				(<any>this).refreshPosition(point);
				this.clickCount++;

				if (!(<any>window).mode.isDesktop()) {
					const isCalcEdit =
						app.map.isEditMode() &&
						app.map._docLayer &&
						app.map._docLayer._docType === 'spreadsheet';
					if (!isCalcEdit) {
						app.map.fire('closemobilewizard');
					}
				}

				let buttons = app.LOButtons.left;
				let modifier = MouseControl.readModifier(e);
				const sendingPosition = this.currentPosition.clone();

				if (window.L.Browser.mac) {
					if (
						modifier == app.UNOModifier.CTRL &&
						buttons == app.LOButtons.left
					) {
						modifier = 0;
						buttons = app.LOButtons.right;
					}
				}

				const clickInfo = {
					sendingPosition: sendingPosition,
					buttons: buttons,
					modifier: modifier,
				};

				if (this.clickTimer) {
					app.timerRegistry.clearTimeout(this.clickTimer);
				} else {
					(<any>this).sendClick(clickInfo, 1);
					app.map.focus(
						(<any>window).mode.isDesktop()
							? undefined
							: this.getMobileKeyboardVisibility(),
					);
				}

				this.clickTimer = app.timerRegistry.setTimeout(
					'clicktimer',
					() => {
						if (this.clickCount === 2) {
							AndroidSelectionMenu.onDoubleTapAtSectionPoint(
								clickInfo.sendingPosition,
							);
						} else if (this.clickCount > 1) {
							(<any>this).sendClick(
								clickInfo,
								this.clickCount,
							);
						}

						this.clickTimer = null;
						this.clickCount = 0;
					},
					250,
				);
			};
		};
		installMouseControlHook();

		const installTextSelectionHook = () => {
			if (
				!app.map ||
				!app.map._docLayer ||
				typeof app.map._docLayer._onTextSelectionMsg !== 'function'
			) {
				window.setTimeout(installTextSelectionHook, 200);
				return;
			}

			const layer = app.map._docLayer;
			const original = layer._onTextSelectionMsg.bind(layer);
			layer._onTextSelectionMsg = function (textMsg: string) {
				original(textMsg);
				const payload = textMsg.replace('textselection:', '').trim();

				if (
					AndroidSelectionMenu.pendingLongPressSelection &&
					payload &&
					payload !== 'EMPTY'
				) {
					AndroidSelectionMenu.selectionStartAcknowledged = true;
				}

				AndroidSelectionMenu.debugLog(
					'textselection payload=' +
						(payload === 'EMPTY' || payload === ''
							? 'EMPTY'
							: 'rects len=' + payload.length) +
						' preview=' + AndroidSelectionMenu.isPreviewWriterMode() +
						' edit=' + AndroidSelectionMenu.isEditMode() +
						' pending=' + AndroidSelectionMenu.pendingLongPressSelection +
						' gestureComplete=' + AndroidSelectionMenu.selectionGestureComplete +
						' dragActive=' + AndroidSelectionMenu.nativeSelectionDragActive +
						' startAck=' + AndroidSelectionMenu.selectionStartAcknowledged);

				// Preview (read-only) Writer mode: original gesture-driven logic.
				if (AndroidSelectionMenu.isPreviewWriterMode()) {
					if (payload && payload !== 'EMPTY') {
						const findBridge = (window as any).AndroidFindReplaceBridge;
						if (
							findBridge &&
							typeof findBridge.consumeSuppressSelectionMenu === 'function' &&
							findBridge.consumeSuppressSelectionMenu()
						) {
							AndroidSelectionMenu.hide();
							return;
						}
						if (AndroidSelectionMenu.nativeSelectionDragActive) {
							return;
						}
						if (AndroidSelectionMenu.shouldUseGestureTryShow()) {
							AndroidSelectionMenu.scheduleTryShowAfterGesture();
						} else if (!AndroidSelectionMenu.pendingLongPressSelection) {
							AndroidSelectionMenu.scheduleTryShowFromCoreSelection();
						}
					} else {
						AndroidSelectionMenu.onEmptyTextSelection();
					}
					return;
				}

				// Edit mode (Writer): same gesture-driven rules as preview — do not
				// pop the menu on every core textselection (e.g. leftover SelectAll).
				if (
					AndroidSelectionMenu.isWriterDoc() &&
					AndroidSelectionMenu.isEditMode()
				) {
					if (payload && payload !== 'EMPTY') {
						const findBridge = (window as any).AndroidFindReplaceBridge;
						if (
							findBridge &&
							typeof findBridge.consumeSuppressSelectionMenu === 'function' &&
							findBridge.consumeSuppressSelectionMenu()
						) {
							AndroidSelectionMenu.hide();
							return;
						}
						// Native long-press gesture still in flight: do NOT show the
						// menu overlay now — it would intercept the finger-up and break
						// the gesture (selection flash-away). finishTextSelectionDrag →
						// scheduleTryShowAfterGesture shows it after finger-up.
						if (AndroidSelectionMenu.nativeSelectionDragActive) {
							return;
						}
						if (AndroidSelectionMenu.shouldUseGestureTryShow()) {
							AndroidSelectionMenu.scheduleTryShowAfterGesture();
						} else if (!AndroidSelectionMenu.pendingLongPressSelection) {
							AndroidSelectionMenu.scheduleTryShowFromCoreSelection();
						}
					} else {
						AndroidSelectionMenu.onEmptyTextSelection();
					}
				}
			};
		};
		installTextSelectionHook();
	}
}

(window as any).AndroidSelectionMenu = AndroidSelectionMenu;
if (window.ThisIsTheAndroidApp) {
	AndroidSelectionMenu.install();
}
