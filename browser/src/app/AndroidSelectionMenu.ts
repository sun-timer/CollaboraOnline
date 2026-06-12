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
	private static readonly minSelectionSpanTwips = 80;
	private static tryShowRetryTimer = 0;
	private static readonly tryShowRetryDelayMs = 100;
	private static readonly tryShowMaxRetries = 30;

	/** True while a native long-press selection gesture is in flight. */
	static isLongPressGesturePending(): boolean {
		return AndroidSelectionMenu.pendingLongPressSelection;
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
		AndroidSelectionMenu.lastDragSelectionUpdateAt = 0;
		AndroidSelectionMenu.clearTryShowRetry();
		AndroidSelectionMenu.hide();
	}

	private static resetForNewGesture(): void {
		AndroidSelectionMenu.cancelGesture();
		AndroidSelectionMenu.clearLocalTextSelection();
	}

	/** WebView-local touch coords (viewX/viewY) → document twips. */
	private static viewPointToDocumentTwips(
		viewX: number,
		viewY: number,
	): { x: number; y: number } | null {
		const canvas = document.getElementById('canvas-container');
		if (!canvas || !app.sectionContainer || !app.activeDocument || !app.map._docLayer) {
			return null;
		}

		const canvasRect = canvas.getBoundingClientRect();
		const point = new cool.SimplePoint(viewX - canvasRect.left, viewY - canvasRect.top);
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

	/** Long-press: send selecttext start only; end is sent on finger up. */
	static startTextSelectionAt(viewX: number, viewY: number): void {
		const pos = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
		if (!pos) {
			AndroidSelectionMenu.cancelGesture();
			return;
		}

		AndroidSelectionMenu.lastSelectionStartAt = Date.now();
		AndroidSelectionMenu.selectionStartTwips = { x: pos.x, y: pos.y };
		app.map._docLayer._postSelectTextEvent('start', pos.x, pos.y);
	}

	static updateTextSelectionEndAt(
		viewX: number,
		viewY: number,
		force: boolean = false,
	): void {
		if (!AndroidSelectionMenu.nativeSelectionDragActive) {
			return;
		}
		if (!AndroidSelectionMenu.isPreviewWriterMode()) {
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
				AndroidSelectionMenu.updateTextSelectionEndAt(viewX, viewY, true);
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

		AndroidSelectionMenu.resetForNewGesture();
		const findBridge = (window as any).AndroidFindReplaceBridge;
		if (
			findBridge &&
			typeof findBridge.clearSuppressSelectionMenu === 'function'
		) {
			findBridge.clearSuppressSelectionMenu();
		}
		AndroidSelectionMenu.markNativeLongPress();
		AndroidSelectionMenu.pendingLongPressSelection = true;
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
		if (!app.map || typeof app.map.isReadOnlyMode !== 'function' || !app.map.isReadOnlyMode()) {
			return;
		}
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
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const anchor = AndroidSelectionMenu.clampAnchorToCanvas(
			Math.round(centerViewX) + Math.round(canvasRect.x * app.dpiScale),
			Math.round(topViewY) + Math.round(canvasRect.y * app.dpiScale),
		);

		window.postMobileMessage('SELECTIONMENU show ' + anchor.x + ' ' + anchor.y);
		AndroidSelectionMenu.pendingLongPressSelection = false;
		AndroidSelectionMenu.selectionStartTwips = null;
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
				if (AndroidSelectionMenu.isPreviewWriterMode()) {
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
					AndroidSelectionMenu.isPreviewWriterMode()
				) {
					e.preventDefault();
					e.stopPropagation();
					return;
				}
				return originalOnContextMenu.call(this, point, e);
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
				if (!AndroidSelectionMenu.isPreviewWriterMode()) {
					return;
				}
				const payload = textMsg.replace('textselection:', '').trim();
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
			};
		};
		installTextSelectionHook();
	}
}

(window as any).AndroidSelectionMenu = AndroidSelectionMenu;
if (window.ThisIsTheAndroidApp) {
	AndroidSelectionMenu.install();
}
