/*
 * Android native selection menu bridge (preview / read-only UI mode).
 * Self-contained hooks — do not patch CanvasSectionContainer / CanvasTileLayer.
 */

class AndroidSelectionMenu {
	private static hooksInstalled = false;
	private static pendingLongPressSelection = false;
	private static nativeLongPressUntil = 0;
	private static readonly nativeLongPressGuardMs = 700;
	private static nativeSelectionDragActive = false;
	private static nativeSelectionTouchActive = false;
	private static lastDragSelectionUpdateAt = 0;
	private static readonly dragSelectionThrottleMs = 60;

	static hide(): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		window.postMobileMessage('SELECTIONMENU hide');
	}

	static markNativeLongPress(): void {
		AndroidSelectionMenu.nativeLongPressUntil =
			Date.now() + AndroidSelectionMenu.nativeLongPressGuardMs;
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
		const hitTarget = document.elementFromPoint(viewX, viewY);
		if (hitTarget && !canvas.contains(hitTarget)) {
			return null;
		}

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

	/**
	 * Start a real core text selection near the long-press point.
	 *
	 * Preview mode ignores edit-style mouse double-click selection, so sending
	 * selecttext start/end is the stable path that makes core emit textselection.
	 */
	static createTextSelectionAt(viewX: number, viewY: number): void {
		const pos = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
		if (!pos) {
			AndroidSelectionMenu.pendingLongPressSelection = false;
			AndroidSelectionMenu.nativeSelectionDragActive = false;
			AndroidSelectionMenu.nativeSelectionTouchActive = false;
			return;
		}

		const layer = app.map._docLayer;
		const spanTwips = Math.max(
			Math.round(12 * app.pixelsToTwips),
			Math.round(12 * app.dpiScale * app.pixelsToTwips),
		);
		layer._postSelectTextEvent('start', pos.x, pos.y);
		layer._postSelectTextEvent('end', pos.x + spanTwips, pos.y);
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
			AndroidSelectionMenu.nativeSelectionDragActive = false;
			AndroidSelectionMenu.nativeSelectionTouchActive = false;
			return;
		}
		const now = Date.now();
		if (!force && now - AndroidSelectionMenu.lastDragSelectionUpdateAt <
			AndroidSelectionMenu.dragSelectionThrottleMs) {
			return;
		}
		const pos = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
		if (!pos) {
			return;
		}
		AndroidSelectionMenu.lastDragSelectionUpdateAt = now;
		app.map._docLayer._postSelectTextEvent('end', pos.x, pos.y);
	}

	static finishTextSelectionDrag(viewX?: number, viewY?: number): void {
		if (!AndroidSelectionMenu.nativeSelectionDragActive) {
			return;
		}
		if (typeof viewX === 'number' && typeof viewY === 'number') {
			AndroidSelectionMenu.updateTextSelectionEndAt(viewX, viewY, true);
		}
		AndroidSelectionMenu.nativeSelectionDragActive = false;
		AndroidSelectionMenu.nativeSelectionTouchActive = false;
		window.setTimeout(() => AndroidSelectionMenu.tryShow(), 80);
	}

	/**
	 * Long-press in preview mode: select word at touch point, then show native menu.
	 * Invoked from LOActivity only when native preview mode is active.
	 * viewX/viewY are WebView-local coordinates.
	 */
	static onLongPressAt(viewX: number, viewY: number): void {
		if (!window.ThisIsTheAndroidApp) {
			return;
		}
		if (!AndroidSelectionMenu.isWriterDoc()) {
			return;
		}

		AndroidSelectionMenu.markNativeLongPress();
		AndroidSelectionMenu.pendingLongPressSelection = true;
		AndroidSelectionMenu.nativeSelectionDragActive = true;
		AndroidSelectionMenu.nativeSelectionTouchActive = true;
		AndroidSelectionMenu.lastDragSelectionUpdateAt = 0;
		AndroidSelectionMenu.createTextSelectionAt(viewX, viewY);
	}

	/** Show menu anchored above the current text selection (preview / read-only UI only). */
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
		if (!TextSelections || !TextSelections.isActive()) {
			return;
		}

		const startRect = TextSelections.getStartRectangle();
		const endRect = TextSelections.getEndRectangle();
		if (!startRect || !endRect || !app.sectionContainer) {
			return;
		}

		const topTwips = Math.min(startRect.pY1, endRect.pY1);
		const leftTwips = Math.min(startRect.pX1, endRect.pX1);
		const rightTwips = Math.max(startRect.pX2, endRect.pX2);
		const centerTwipsX = (leftTwips + rightTwips) / 2;
		const point = new cool.SimplePoint(centerTwipsX, topTwips);
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const anchorX = Math.round(point.vX / app.dpiScale) + canvasRect.x;
		const anchorY = Math.round(point.vY / app.dpiScale) + canvasRect.y;

		window.postMobileMessage('SELECTIONMENU show ' + anchorX + ' ' + anchorY);
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

		document.addEventListener(
			'touchmove',
			(e: TouchEvent) => {
				if (
					!AndroidSelectionMenu.nativeSelectionDragActive ||
					!AndroidSelectionMenu.isPreviewWriterMode() ||
					e.touches.length !== 1
				) {
					return;
				}

				e.preventDefault();
				e.stopPropagation();
				const touch = e.touches[0];
				AndroidSelectionMenu.updateTextSelectionEndAt(
					touch.clientX,
					touch.clientY,
				);
			},
			{ capture: true, passive: false },
		);

		const finishTouchSelection = (e: TouchEvent) => {
			if (!AndroidSelectionMenu.nativeSelectionDragActive) {
				return;
			}
			e.preventDefault();
			e.stopPropagation();
			const touch = e.changedTouches && e.changedTouches.length > 0
				? e.changedTouches[0]
				: null;
			if (touch) {
				AndroidSelectionMenu.finishTextSelectionDrag(
					touch.clientX,
					touch.clientY,
				);
			} else {
				AndroidSelectionMenu.finishTextSelectionDrag();
			}
		};

		document.addEventListener('touchend', finishTouchSelection, {
			capture: true,
			passive: false,
		});
		document.addEventListener('touchcancel', finishTouchSelection, {
			capture: true,
			passive: false,
		});

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
					if (AndroidSelectionMenu.pendingLongPressSelection) {
						AndroidSelectionMenu.pendingLongPressSelection = false;
						if (!AndroidSelectionMenu.nativeSelectionTouchActive) {
							window.setTimeout(() => AndroidSelectionMenu.tryShow(), 50);
						}
					}
				} else {
					AndroidSelectionMenu.pendingLongPressSelection = false;
					AndroidSelectionMenu.nativeSelectionDragActive = false;
					AndroidSelectionMenu.nativeSelectionTouchActive = false;
					AndroidSelectionMenu.hide();
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
