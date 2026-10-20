/*
 * Android native selection menu bridge (preview / read-only UI mode).
 * Self-contained hooks — do not patch CanvasSectionContainer / CanvasTileLayer.
 */

type PendingMobileTextInputRequest = {
	docLayer: any;
	generation: number;
	targetPoint: { x: number; y: number } | null;
	startCaret: { x: number; y: number } | null;
	calcCell: { part: number; address: string; bounds: number[] } | null;
	allowCurrentCaretFallback: boolean;
	tapResolved: boolean;
	caretConfirmed: boolean;
	timer: number;
};

class AndroidSelectionMenu {
	private static hooksInstalled = false;
	private static inputGestureHooksInstalled = false;
	private static inputLifecycleHooksInstalled = false;
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
	/**
	 * Mobile long-press selects a few characters at the press point instead of
	 * extending `selecttext start` from the previous caret.
	 */
	private static localCharacterSelection = false;
	private static readonly localLongPressCharCount = 2;
	private static readonly minSelectionSpanTwips = 80;
	private static tryShowRetryTimer = 0;
	private static readonly tryShowRetryDelayMs = 100;
	private static readonly tryShowMaxRetries = 30;
	/** Nested guard for background operations that use SelectAll to read text. */
	private static programmaticSelectionSuppressionDepth = 0;
	/** Last iOS touch in CSS pixels. Used when core view coords collapse to the canvas origin. */
	private static lastIosGestureCss: { x: number; y: number } | null = null;
	/**
	 * Finger-up after a long-press still synthesizes a click. On a slide that
	 * click selects the shape and clears the text selection.
	 */
	private static suppressClickUntil = 0;
	private static androidKeyboardVisible = false;
	private static textInputGeneration = 0;
	private static validTextInputGeneration = 0;
	private static validTextInputDocLayer: any = null;
	private static validTextInputExpiresAt = 0;
	private static validTextInputTimer = 0;
	private static pendingTextInputRequest: PendingMobileTextInputRequest | null = null;
	private static touchGesture: {
		x: number;
		y: number;
		moved: boolean;
	} | null = null;
	private static lastTouchAt = 0;
	private static lastTouchPoint: { x: number; y: number } | null = null;
	private static lastTouchWasTap = true;
	private static preserveKeyboardDuringTouch = false;
	private static preserveKeyboardTimer = 0;

	/** True while a native long-press selection gesture is in flight. */
	static isLongPressGesturePending(): boolean {
		return AndroidSelectionMenu.pendingLongPressSelection;
	}

	/** True when core has a text range, so a shape message must not replace it. */
	static hasVisibleTextSelection(): boolean {
		return AndroidSelectionMenu.hasNonDegenerateSelection();
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
		if (AndroidSelectionMenu.isIOS()) {
			MobileSelectionEvents.broadcastHide();
			return;
		}
		if (!AndroidSelectionMenu.isAndroid() || typeof window.postMobileMessage !== 'function') {
			return;
		}
		window.postMobileMessage('SELECTIONMENU hide');
	}

	/**
	 * Suppress the user-facing selection menu while AI/document services use
	 * SelectAll as an internal text-extraction mechanism. The document
	 * selection itself remains intact until the caller explicitly deselects it.
	 */
	static beginProgrammaticSelection(): void {
		AndroidSelectionMenu.programmaticSelectionSuppressionDepth++;
		AndroidSelectionMenu.clearTryShowRetry();
		AndroidSelectionMenu.hide();
	}

	static endProgrammaticSelection(): void {
		if (AndroidSelectionMenu.programmaticSelectionSuppressionDepth > 0) {
			AndroidSelectionMenu.programmaticSelectionSuppressionDepth--;
		}
		if (AndroidSelectionMenu.programmaticSelectionSuppressionDepth === 0) {
			AndroidSelectionMenu.clearTryShowRetry();
			AndroidSelectionMenu.hide();
		}
	}

	private static isProgrammaticSelectionSuppressed(): boolean {
		return AndroidSelectionMenu.programmaticSelectionSuppressionDepth > 0;
	}

	static markNativeLongPress(): void {
		// Reserved for native long-press guard extensions.
	}

	private static isKeyboardOpen(): boolean {
		return AndroidSelectionMenu.isIOS()
			? typeof MobileSelectionMenu !== 'undefined' &&
				MobileSelectionMenu.iosKeyboardOpen()
			: AndroidSelectionMenu.androidKeyboardVisible;
	}

	static onAndroidKeyboardVisibilityChanged(visible: boolean): void {
		AndroidSelectionMenu.androidKeyboardVisible = visible;
		if (!visible) {
			AndroidSelectionMenu.clearKeyboardPreservation();
		}
	}

	static onIOSKeyboardVisibilityChanged(visible: boolean): void {
		if (!visible) {
			AndroidSelectionMenu.clearKeyboardPreservation();
		}
	}

	/** Distinguish the canvas's synthetic touch click from an actual mouse click. */
	static isTouchGeneratedClick(event?: MouseEvent): boolean {
		if (!event || event.detail === 0) {
			return false;
		}
		const sourceCapabilities = (event as any).sourceCapabilities;
		if (sourceCapabilities && typeof sourceCapabilities.firesTouchEvents === 'boolean') {
			return sourceCapabilities.firesTouchEvents;
		}
		const pointerType = (event as any).pointerType;
		if (pointerType === 'touch' || pointerType === 'pen') {
			return true;
		}
		if (pointerType === 'mouse') {
			return false;
		}
		const age = Date.now() - AndroidSelectionMenu.lastTouchAt;
		const point = AndroidSelectionMenu.lastTouchPoint;
		return !!(
			AndroidSelectionMenu.lastTouchWasTap &&
			point &&
			age >= 0 &&
			age <= 700 &&
			Math.abs(event.clientX - point.x) <= 24 &&
			Math.abs(event.clientY - point.y) <= 24
		);
	}

	static isTextInputGenerationCurrent(generation: number): boolean {
		return (
			generation === AndroidSelectionMenu.validTextInputGeneration &&
			Date.now() < AndroidSelectionMenu.validTextInputExpiresAt &&
			!!app.map &&
			app.map._docLayer === AndroidSelectionMenu.validTextInputDocLayer &&
			AndroidSelectionMenu.isEditMode() &&
			!AndroidSelectionMenu.pendingLongPressSelection
		);
	}

	/** Keep focus(false) from dismissing an already-open keyboard during a touch selection. */
	static shouldPreserveKeyboardAfterTouch(): boolean {
		if (
			!AndroidSelectionMenu.preserveKeyboardDuringTouch ||
			!AndroidSelectionMenu.isKeyboardOpen() ||
			!AndroidSelectionMenu.isEditMode() ||
			!app.map
		) {
			return false;
		}
		if (
			(app.map.uiManager && app.map.uiManager.isAnyDialogOpen &&
				app.map.uiManager.isAnyDialogOpen()) ||
			(typeof JSDialog !== 'undefined' && JSDialog.IsAnyInputFocused())
		) {
			return false;
		}
		return true;
	}

	static beginTextInputRequest(
		targetPoint: { x: number; y: number } | null = null,
		allowCurrentCaretFallback: boolean = true,
	): void {
		if (
			!AndroidSelectionMenu.isMobileApp() ||
			!AndroidSelectionMenu.isEditMode() ||
			!AndroidSelectionMenu.isSelectionDoc() ||
			!app.map?._docLayer
		) {
			return;
		}
		AndroidSelectionMenu.cancelTextInputRequest('replace');
		const request = {
			docLayer: app.map._docLayer,
			generation: ++AndroidSelectionMenu.textInputGeneration,
			targetPoint,
			startCaret: app.file && app.file.textCursor && app.file.textCursor.visible
				? { x: app.file.textCursor.rectangle!.x1, y: app.file.textCursor.rectangle!.y1 }
				: null,
			allowCurrentCaretFallback,
			calcCell: app.map.getDocType() === 'spreadsheet' && app.calc?.cellCursorVisible &&
				app.calc.cellCursorRectangle && app.calc.cellAddress
				? {
					part: app.map._docLayer._selectedPart,
					address: app.calc.cellAddress.toArray().join(':'),
					bounds: app.calc.cellCursorRectangle.toArray(),
				}
				: null,
			tapResolved: false,
			caretConfirmed: false,
			timer: 0,
		};
		AndroidSelectionMenu.pendingTextInputRequest = request;
		request.timer = window.setTimeout(() => {
			if (AndroidSelectionMenu.pendingTextInputRequest === request) {
				AndroidSelectionMenu.pendingTextInputRequest = null;
				AndroidSelectionMenu.releaseKeyboardPreservation(0);
			}
		}, 1000);
	}

	static resolveTextInputTap(): void {
		const request = AndroidSelectionMenu.pendingTextInputRequest;
		if (!request) {
			return;
		}
		request.tapResolved = true;
		// Core may not emit visibility/invalidate when a tap lands on the caret
		// that is already visible. Reuse it only for a very tight caret hit.
		if (
			request.allowCurrentCaretFallback &&
			AndroidSelectionMenu.isCurrentCaretAtTap(request)
		) {
			request.caretConfirmed = true;
		}
		AndroidSelectionMenu.maybeShowKeyboardForTextInput(request);
	}

	private static getRequestCalcCell(
		request: PendingMobileTextInputRequest,
	): cool.SimpleRectangle | null {
		// Core may hide the cell cursor while the same cell is being edited.
		const snapshot = request.calcCell;
		if (
			!snapshot ||
			!app.map ||
			app.map._docLayer !== request.docLayer ||
			app.map.getDocType() !== 'spreadsheet' ||
			app.map._docLayer?._selectedPart !== snapshot.part ||
			!app.calc?.cellAddress ||
			app.calc.cellAddress.toArray().join(':') !== snapshot.address
		) {
			return null;
		}
		if (
			app.calc.cellCursorVisible &&
			(!app.calc.cellCursorRectangle ||
				!app.calc.cellCursorRectangle.equals(snapshot.bounds))
		) {
			return null;
		}
		return new cool.SimpleRectangle(
			snapshot.bounds[0],
			snapshot.bounds[1],
			snapshot.bounds[2],
			snapshot.bounds[3],
		);
	}

	private static isCurrentCaretAtTap(
		request: PendingMobileTextInputRequest,
	): boolean {
		if (
			!request.targetPoint ||
			!app.map ||
			!app.file?.textCursor?.visible
		) {
			return false;
		}
		if (app.map.getDocType() === 'spreadsheet') {
			const cell = AndroidSelectionMenu.getRequestCalcCell(request);
			if (!cell || !cell.containsPoint([request.targetPoint.x, request.targetPoint.y])) {
				return false;
			}
		}
		return AndroidSelectionMenu.isCaretAtTarget(
			app.file.textCursor.rectangle,
			request.targetPoint,
		);
	}

	private static isCaretAtTarget(
		caret: any,
		targetPoint: { x: number; y: number },
	): boolean {
		const tolerance = Math.round(8 * app.pixelsToTwips);
		const verticalTolerance = Math.round(3 * app.pixelsToTwips);
		return (
			Math.abs(targetPoint.x - caret.x1) <= tolerance &&
			targetPoint.y >= Math.min(caret.y1, caret.y2) - verticalTolerance &&
			targetPoint.y <= Math.max(caret.y1, caret.y2) + verticalTolerance
		);
	}

	static cancelTextInputRequest(_reason: string = 'cancel'): void {
		const request = AndroidSelectionMenu.pendingTextInputRequest;
		if (request) {
			window.clearTimeout(request.timer);
			AndroidSelectionMenu.pendingTextInputRequest = null;
		}
		AndroidSelectionMenu.textInputGeneration++;
		AndroidSelectionMenu.validTextInputGeneration = 0;
		AndroidSelectionMenu.validTextInputDocLayer = null;
		AndroidSelectionMenu.validTextInputExpiresAt = 0;
		if (AndroidSelectionMenu.validTextInputTimer) {
			window.clearTimeout(AndroidSelectionMenu.validTextInputTimer);
			AndroidSelectionMenu.validTextInputTimer = 0;
		}
	}

	private static maybeShowKeyboardForTextInput(
		request: PendingMobileTextInputRequest,
	): void {
		if (
			AndroidSelectionMenu.pendingTextInputRequest !== request ||
			!request.tapResolved ||
			!request.caretConfirmed ||
			!app.map ||
			app.map._docLayer !== request.docLayer ||
			!AndroidSelectionMenu.isEditMode() ||
			AndroidSelectionMenu.pendingLongPressSelection
		) {
			return;
		}
		window.clearTimeout(request.timer);
		AndroidSelectionMenu.pendingTextInputRequest = null;
		AndroidSelectionMenu.releaseKeyboardPreservation(800);
		if (AndroidSelectionMenu.isKeyboardOpen()) {
			app.map.focus(true);
			return;
		}
		if (AndroidSelectionMenu.isIOS()) {
			app.map.focus(true);
		} else if (typeof window.postMobileMessage === 'function') {
			AndroidSelectionMenu.validTextInputGeneration = request.generation;
			AndroidSelectionMenu.validTextInputDocLayer = request.docLayer;
			AndroidSelectionMenu.validTextInputExpiresAt = Date.now() + 1500;
			if (AndroidSelectionMenu.validTextInputTimer) {
				window.clearTimeout(AndroidSelectionMenu.validTextInputTimer);
			}
			AndroidSelectionMenu.validTextInputTimer = window.setTimeout(() => {
				if (AndroidSelectionMenu.validTextInputGeneration === request.generation) {
					AndroidSelectionMenu.validTextInputGeneration = 0;
					AndroidSelectionMenu.validTextInputDocLayer = null;
					AndroidSelectionMenu.validTextInputExpiresAt = 0;
				}
				AndroidSelectionMenu.validTextInputTimer = 0;
			}, 1500);
			window.postMobileMessage('SHOWTEXTIME ' + request.generation);
		}
	}

	private static onTextCursorVisibility(event: any): void {
		AndroidSelectionMenu.onTextCaretUpdate(event?.detail?.visible === true);
	}

	private static onTextCaretUpdate(cursorVisible: boolean): void {
		const request = AndroidSelectionMenu.pendingTextInputRequest;
		if (!request) {
			return;
		}
		if (
			!app.map ||
			app.map._docLayer !== request.docLayer ||
			!AndroidSelectionMenu.isEditMode()
		) {
			AndroidSelectionMenu.cancelTextInputRequest('document_or_permission_changed');
			return;
		}
		if (cursorVisible && app.file && app.file.textCursor && app.file.textCursor.visible) {
			const textCursor = app.file.textCursor;
			const caret = textCursor.rectangle!;
			const caretChanged = !request.startCaret ||
				Math.abs(caret.x1 - request.startCaret.x) > 1 ||
				Math.abs(caret.y1 - request.startCaret.y) > 1;
			if (app.map.getDocType() === 'spreadsheet') {
				const cell = AndroidSelectionMenu.getRequestCalcCell(request);
				if (
					!cell ||
					!cell.containsPoint([caret.x1, caret.y1]) ||
					(request.targetPoint &&
						!cell.containsPoint([request.targetPoint.x, request.targetPoint.y]))
				) {
					return;
				}
			} else if (
				request.targetPoint &&
				(!caretChanged && !AndroidSelectionMenu.isCaretAtTarget(caret, request.targetPoint))
			) {
				return;
			} else if (request.targetPoint && caretChanged) {
				const tolerance = Math.round(72 * app.pixelsToTwips);
				if (
					Math.abs(caret.x1 - request.targetPoint.x) > tolerance ||
					Math.abs(caret.y1 - request.targetPoint.y) > tolerance
				) {
					return;
				}
			}
			request.caretConfirmed = true;
			AndroidSelectionMenu.maybeShowKeyboardForTextInput(request);
		}
	}

	private static clearKeyboardPreservation(): void {
		AndroidSelectionMenu.preserveKeyboardDuringTouch = false;
		if (AndroidSelectionMenu.preserveKeyboardTimer) {
			window.clearTimeout(AndroidSelectionMenu.preserveKeyboardTimer);
			AndroidSelectionMenu.preserveKeyboardTimer = 0;
		}
	}

	private static releaseKeyboardPreservation(delayMs: number): void {
		if (AndroidSelectionMenu.preserveKeyboardTimer) {
			window.clearTimeout(AndroidSelectionMenu.preserveKeyboardTimer);
		}
		AndroidSelectionMenu.preserveKeyboardTimer = window.setTimeout(() => {
			if (
				AndroidSelectionMenu.preserveKeyboardDuringTouch &&
				AndroidSelectionMenu.isKeyboardOpen() &&
				AndroidSelectionMenu.isEditMode() &&
				AndroidSelectionMenu.hasActiveSelectionForKeyboardPreservation()
			) {
				AndroidSelectionMenu.releaseKeyboardPreservation(500);
			} else {
				AndroidSelectionMenu.clearKeyboardPreservation();
			}
		}, delayMs);
	}

	private static hasActiveSelectionForKeyboardPreservation(): boolean {
		return (
			AndroidSelectionMenu.nativeSelectionDragActive ||
			AndroidSelectionMenu.pendingLongPressSelection ||
			AndroidSelectionMenu.hasVisibleTextSelection() ||
			(!!app.map && app.map.getDocType() === 'spreadsheet' &&
				!!app.calc?.cellCursorVisible) ||
			(typeof GraphicSelection !== 'undefined' &&
				GraphicSelection.hasActiveSelection())
		);
	}

	private static isTouchTapEligible(): boolean {
		return !AndroidSelectionMenu.lastTouchAt ||
			Date.now() - AndroidSelectionMenu.lastTouchAt > 800 ||
			AndroidSelectionMenu.lastTouchWasTap;
	}

	private static installInputGestureHooks(): void {
		if (AndroidSelectionMenu.inputGestureHooksInstalled) {
			return;
		}
		AndroidSelectionMenu.inputGestureHooksInstalled = true;

		const startGesture = (event: Event) => {
			AndroidSelectionMenu.cancelTextInputRequest('new_touch');
			AndroidSelectionMenu.clearKeyboardPreservation();
			if (
				event.type === 'pointerdown' &&
				(event as PointerEvent).pointerType === 'mouse'
			) {
				AndroidSelectionMenu.lastTouchAt = 0;
				AndroidSelectionMenu.lastTouchPoint = null;
				AndroidSelectionMenu.lastTouchWasTap = false;
				AndroidSelectionMenu.touchGesture = null;
				return;
			}
			AndroidSelectionMenu.lastTouchAt = Date.now();
			AndroidSelectionMenu.lastTouchWasTap = false;

			const target = event.target as HTMLElement | null;
			if (
				!target ||
				target.tagName !== 'CANVAS' ||
				!AndroidSelectionMenu.isSelectionDoc()
			) {
				AndroidSelectionMenu.touchGesture = null;
				return;
			}

			let x: number | null = null;
			let y: number | null = null;
			const touchEvent = event as TouchEvent;
			if (touchEvent.touches && touchEvent.touches.length > 0) {
				x = touchEvent.touches[0].clientX;
				y = touchEvent.touches[0].clientY;
			} else {
				const pointerEvent = event as PointerEvent;
				if (typeof pointerEvent.clientX === 'number') {
					x = pointerEvent.clientX;
					y = pointerEvent.clientY;
				}
			}
			if (x === null || y === null) {
				AndroidSelectionMenu.touchGesture = null;
				return;
			}
			AndroidSelectionMenu.lastTouchPoint = { x, y };
			AndroidSelectionMenu.touchGesture = { x, y, moved: false };
			AndroidSelectionMenu.preserveKeyboardDuringTouch =
				AndroidSelectionMenu.isEditMode() && AndroidSelectionMenu.isKeyboardOpen();
			if (AndroidSelectionMenu.preserveKeyboardDuringTouch) {
				AndroidSelectionMenu.releaseKeyboardPreservation(1600);
			}
		};

		const moveGesture = (event: TouchEvent | PointerEvent) => {
			const gesture = AndroidSelectionMenu.touchGesture;
			if (!gesture) {
				return;
			}
			const touch = (event as TouchEvent).touches?.[0];
			const x = touch ? touch.clientX : (event as PointerEvent).clientX;
			const y = touch ? touch.clientY : (event as PointerEvent).clientY;
			const multiTouch = !!(event as TouchEvent).touches &&
				(event as TouchEvent).touches.length > 1;
			if (
				multiTouch ||
				Math.abs(x - gesture.x) + Math.abs(y - gesture.y) > 12
			) {
				gesture.moved = true;
				AndroidSelectionMenu.lastTouchWasTap = false;
				AndroidSelectionMenu.cancelTextInputRequest('scroll_or_zoom_gesture');
				if (
					!AndroidSelectionMenu.nativeSelectionDragActive &&
					!AndroidSelectionMenu.hasActiveSelectionForKeyboardPreservation()
				) {
					AndroidSelectionMenu.clearKeyboardPreservation();
				}
			}
		};

		const endGesture = (cancelled: boolean = false) => {
			const gesture = AndroidSelectionMenu.touchGesture;
			if (!gesture) {
				return;
			}
			AndroidSelectionMenu.lastTouchAt = Date.now();
			AndroidSelectionMenu.lastTouchWasTap =
				!cancelled && !gesture.moved && !AndroidSelectionMenu.nativeSelectionDragActive;
			AndroidSelectionMenu.touchGesture = null;
			if (
				cancelled ||
				(gesture.moved &&
					!AndroidSelectionMenu.nativeSelectionDragActive &&
					!AndroidSelectionMenu.hasActiveSelectionForKeyboardPreservation())
			) {
				AndroidSelectionMenu.cancelTextInputRequest('touch_cancelled_or_moved');
				AndroidSelectionMenu.clearKeyboardPreservation();
			} else if (AndroidSelectionMenu.preserveKeyboardDuringTouch) {
				AndroidSelectionMenu.releaseKeyboardPreservation(1600);
			}
		};

		document.addEventListener('pointerdown', startGesture, true);
		document.addEventListener('touchstart', startGesture, { capture: true, passive: true });
		document.addEventListener('pointermove', moveGesture, true);
		document.addEventListener('touchmove', moveGesture, { capture: true, passive: true });
		document.addEventListener('pointerup', () => endGesture(), true);
		document.addEventListener('touchend', () => endGesture(), { capture: true, passive: true });
		document.addEventListener('pointercancel', () => endGesture(true), true);
		document.addEventListener('touchcancel', () => endGesture(true), { capture: true, passive: true });
		document.addEventListener('visibilitychange', () => {
			if (document.hidden) {
				AndroidSelectionMenu.cancelTextInputRequest('page_hidden');
				AndroidSelectionMenu.clearKeyboardPreservation();
			}
		});

		if (app.events && typeof app.events.on === 'function') {
			app.events.on(
				'TextCursorVisibility',
				AndroidSelectionMenu.onTextCursorVisibility,
			);
			app.events.on('updatepermission', () => {
				if (!AndroidSelectionMenu.isEditMode()) {
					AndroidSelectionMenu.cancelTextInputRequest('permission_changed');
					AndroidSelectionMenu.clearKeyboardPreservation();
				}
			});
		}
	}

	private static installTextInputLifecycleHooks(): void {
		if (AndroidSelectionMenu.inputLifecycleHooksInstalled) {
			return;
		}
		const map = app.map;
		if (!map || typeof map.on !== 'function') {
			window.setTimeout(AndroidSelectionMenu.installTextInputLifecycleHooks, 200);
			return;
		}
		AndroidSelectionMenu.inputLifecycleHooksInstalled = true;
		const cancelForDocumentChange = () => {
			AndroidSelectionMenu.cancelTextInputRequest('document_changed');
			AndroidSelectionMenu.clearKeyboardPreservation();
			AndroidSelectionMenu.touchGesture = null;
		};
		map.on('docloaded', () => {
			cancelForDocumentChange();
			window.setTimeout(AndroidSelectionMenu.installCaretUpdateHook, 0);
		});
		map.on('closeapp', cancelForDocumentChange);
		map.on('zoomstart', cancelForDocumentChange);
		map.on('setpart', () =>
			AndroidSelectionMenu.cancelTextInputRequest('part_changed'),
		);
		AndroidSelectionMenu.installCaretUpdateHook();
	}

	private static installCaretUpdateHook(): void {
		const layer = app.map && app.map._docLayer;
		if (
			!layer ||
			typeof layer._onInvalidateCursorMsg !== 'function' ||
			layer.__androidSelectionImeCursorHookInstalled
		) {
			return;
		}
		const original = layer._onInvalidateCursorMsg;
		layer._onInvalidateCursorMsg = function (textMsg: string) {
			const result = original.call(this, textMsg);
			AndroidSelectionMenu.onTextCaretUpdate(!!app.file?.textCursor?.visible);
			return result;
		};
		layer.__androidSelectionImeCursorHookInstalled = true;
	}

	private static isAndroid(): boolean {
		return !!((window as any).ThisIsTheAndroidApp);
	}

	private static isIOS(): boolean {
		return !!((window as any).ThisIsTheiOSApp);
	}

	private static isMobileApp(): boolean {
		return AndroidSelectionMenu.isAndroid() || AndroidSelectionMenu.isIOS();
	}

	/** Read-only Word or PPT. Both show the text menu; AI stays edit-only. */
	private static isPreviewWriterMode(): boolean {
		return (
			!!app.map &&
			typeof app.map.isReadOnlyMode === 'function' &&
			app.map.isReadOnlyMode() &&
			(app.map.getDocType() === 'text' ||
				app.map.getDocType() === 'presentation')
		);
	}

	private static isPresentation(): boolean {
		return !!app.map && app.map.getDocType() === 'presentation';
	}

	private static isWriterDoc(): boolean {
		return !!app.map && app.map.getDocType() === 'text';
	}

	/**
	 * Android / iOS 的 Word、PPT、Excel：长按选中文字，松手后出我们的菜单。
	 * 不要改回内核右键菜单。Draw 不走这条。
	 */
	private static isSelectionDoc(): boolean {
		if (!app.map) {
			return false;
		}
		const docType = app.map.getDocType();
		return (
			docType === 'text' ||
			docType === 'presentation' ||
			docType === 'spreadsheet'
		);
	}

	private static isEditMode(): boolean {
		return (
			!!app.map &&
			typeof app.map.isReadOnlyMode === 'function' &&
			!app.map.isReadOnlyMode()
		);
	}

	/**
	 * 两端都挡住长按右键。画布约 550ms 会先发右键，选字要到约 600ms，
	 * 不挡住的话内核菜单会先弹出来。不要只在手势进行中才挡。
	 */
	private static shouldSuppressContextMenu(): boolean {
		if (AndroidSelectionMenu.isProgrammaticSelectionSuppressed()) {
			return true;
		}
		return AndroidSelectionMenu.isSelectionDoc();
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
		AndroidSelectionMenu.localCharacterSelection = false;
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
	 * Double-tap only. Do NOT send selecttext reset here —
	 * LOK reset collapses the caret to document start.
	 */
	private static selectWordAtTwips(pos: { x: number; y: number }): void {
		AndroidSelectionMenu.lastSelectionStartAt = Date.now();
		AndroidSelectionMenu.selectionStartTwips = { x: pos.x, y: pos.y };
		AndroidSelectionMenu.selectionStartAcknowledged = false;
		AndroidSelectionMenu.localCharacterSelection = false;
		AndroidSelectionMenu.debugLog(
			'word_select twips=' + pos.x + ',' + pos.y,
		);
		app.map._docLayer._postSelectTextEvent('start', pos.x, pos.y);
	}

	/**
	 * Place the caret on the pressed character, step one character left, then
	 * select `localLongPressCharCount` characters. `selecttext start` would
	 * instead select from the previous caret to this point (tablet log:
	 * stationary long-press, dragged=false, chars=10..87).
	 * Goes through the socket directly: sendUnoCommand drops these in preview.
	 */
	private static selectLocalCharactersAtTwips(pos: { x: number; y: number }): void {
		const layer = app.map && app.map._docLayer;
		if (!layer || typeof layer._postMouseEvent !== 'function') {
			AndroidSelectionMenu.cancelGesture();
			return;
		}
		AndroidSelectionMenu.lastSelectionStartAt = Date.now();
		AndroidSelectionMenu.selectionStartTwips = { x: pos.x, y: pos.y };
		AndroidSelectionMenu.selectionStartAcknowledged = false;
		AndroidSelectionMenu.localCharacterSelection = true;
		AndroidSelectionMenu.debugLog(
			'local_select twips=' + pos.x + ',' + pos.y +
				' chars=' + AndroidSelectionMenu.localLongPressCharCount,
		);
		layer._postMouseEvent('buttondown', pos.x, pos.y, 1, 1, 0);
		layer._postMouseEvent('buttonup', pos.x, pos.y, 1, 1, 0);
		AndroidSelectionMenu.sendSelectionUno('.uno:GoLeft');
		for (let i = 0; i < AndroidSelectionMenu.localLongPressCharCount; i++) {
			AndroidSelectionMenu.sendSelectionUno('.uno:CharRightSel');
		}
	}

	private static sendSelectionUno(command: string): void {
		if (!app.socket || typeof app.socket.sendMessage !== 'function') {
			return;
		}
		AndroidSelectionMenu.debugLog('uno ' + command);
		app.socket.sendMessage('uno ' + command);
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

	/** Long-press: a few characters at the press point; drag still extends the end. */
	static startTextSelectionAt(viewX: number, viewY: number): void {
		const pos = AndroidSelectionMenu.viewPointToDocumentTwips(viewX, viewY);
		if (!pos) {
			AndroidSelectionMenu.cancelGesture();
			return;
		}

		// Slide text lives in a shape. A click selects that shape, and
		// CharRightSel then has no text cursor in preview or edit.
		// selecttext hits the characters under the point.
		if (AndroidSelectionMenu.isPresentation()) {
			AndroidSelectionMenu.selectWordAtTwips(pos);
			return;
		}
		// Word / Excel: click the character, then select two characters there.
		// selecttext start spans from the previous caret.
		if (AndroidSelectionMenu.isAndroid() || AndroidSelectionMenu.isIOS()) {
			AndroidSelectionMenu.selectLocalCharactersAtTwips(pos);
			return;
		}
		AndroidSelectionMenu.selectWordAtTwips(pos);
	}

	/**
	 * Double-tap word select (tiles-section point from MouseControl).
	 * Uses the same selecttext reset+start path as long-press.
	 */
	static onDoubleTapAtSectionPoint(point: cool.SimplePoint): void {
		if (!AndroidSelectionMenu.isMobileApp() || !AndroidSelectionMenu.isSelectionDoc()) {
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
			// Local long-press already queued a caret click plus character
			// steps. selecttext start here selects from the old caret.
			if (AndroidSelectionMenu.localCharacterSelection) {
				return;
			}
			const anchor = AndroidSelectionMenu.selectionStartTwips;
			if (!anchor) {
				return;
			}
			app.map._docLayer._postSelectTextEvent('start', anchor.x, anchor.y);
		}
		if (!AndroidSelectionMenu.isSelectionDoc()) {
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
				// Stationary long-press already selected the characters at the
				// anchor. Do not re-send `end` at the same point — it can make
				// core collapse the selection to a caret.
				AndroidSelectionMenu.debugLog(
					'finish start=' + (start ? start.x + ',' + start.y : 'null') +
						' end=' + endTwips.x + ',' + endTwips.y +
						' dragged=' + dragged);
			}
		}

		AndroidSelectionMenu.nativeSelectionDragActive = false;
		AndroidSelectionMenu.selectionGestureComplete = true;
		if (AndroidSelectionMenu.preserveKeyboardDuringTouch) {
			AndroidSelectionMenu.releaseKeyboardPreservation(1200);
		}

		// Calc has no text range for a selected cell. Show the compact cell
		// menu on finger-up, the same moment Writer/Impress show theirs.
		// Leaving this to the text-selection retry lets an EMPTY textselection
		// cancel the gesture before the menu appears.
		// A selected graphic keeps the graphic menu; the cell menu would cover it.
		if (app.map && app.map.getDocType() === 'spreadsheet') {
			AndroidSelectionMenu.pendingLongPressSelection = false;
			const graphicSelected =
				typeof GraphicSelection !== 'undefined' &&
				GraphicSelection.hasActiveSelection();
			if (!graphicSelected) {
				AndroidSelectionMenu.showCalcMenuAtPress();
			}
			return;
		}

		// Long-press word select often has identical start/end twips; wait for
		// textselection: instead of cancelling here. onEmptyTextSelection handles
		// genuine zero-width failures.
		AndroidSelectionMenu.scheduleTryShowAfterGesture();
	}

	/**
	 * 长按入口。Android 由原生手势调用，iOS 由 installIosLongPress 调用。
	 * 先关掉内核菜单，再选中按下位置的文字；菜单等松手后再出。
	 */
	static onLongPressAt(viewX: number, viewY: number): void {
		if (!AndroidSelectionMenu.isMobileApp() || !AndroidSelectionMenu.isSelectionDoc()) {
			return;
		}
		AndroidSelectionMenu.cancelTextInputRequest('long_press');
		if (AndroidSelectionMenu.isKeyboardOpen() && AndroidSelectionMenu.isEditMode()) {
			AndroidSelectionMenu.preserveKeyboardDuringTouch = true;
			AndroidSelectionMenu.releaseKeyboardPreservation(1800);
		}

		// An already-selected image or chart keeps the graphic menu.
		// A slide's text is also a shape: long-press must still select that
		// text. An empty textselection falls back to the graphic menu.
		if (
			!AndroidSelectionMenu.isPresentation() &&
			typeof GraphicSelection !== 'undefined' &&
			GraphicSelection.hasActiveSelection()
		) {
			GraphicSelection.updateGraphicSelection();
			return;
		}

		if (app.map && typeof app.map.fire === 'function') {
			app.map.fire('closemobilewizard');
		}
		AndroidSelectionMenu.prepareWordSelectionGesture();
		AndroidSelectionMenu.markNativeLongPress();
		AndroidSelectionMenu.selectionGestureComplete = false;
		AndroidSelectionMenu.nativeSelectionDragActive = true;
		AndroidSelectionMenu.lastDragSelectionUpdateAt = 0;
		AndroidSelectionMenu.suppressClickUntil = Date.now() + 700;
		AndroidSelectionMenu.startTextSelectionAt(viewX, viewY);
	}

	/**
	 * Show menu anchored above the current text selection.
	 * Only when TextSelections is active with a non-degenerate range.
	 */
	static tryShow(): void {
		if (AndroidSelectionMenu.isProgrammaticSelectionSuppressed()) {
			return;
		}
		if (AndroidSelectionMenu.isIOS()) {
			AndroidSelectionMenu.tryShowIOS();
			return;
		}
		if (!AndroidSelectionMenu.isAndroid() || typeof window.postMobileMessage !== 'function') {
			return;
		}
		if (!app.map || typeof app.map.isReadOnlyMode !== 'function') {
			return;
		}
		// Writer and Impress use the text selection menu. Calc uses the compact bar.
		if (!AndroidSelectionMenu.isSelectionDoc()) {
			return;
		}
		if (!AndroidSelectionMenu.hasNonDegenerateSelection()) {
			return;
		}
		// Excel 选中文字后出紧凑条（复制/剪切/粘贴/清除/翻译），不要出文字菜单。
		if (app.map.getDocType() === 'spreadsheet') {
			const calcMenu = (window as any).AndroidCalcCellMenu;
			if (calcMenu && typeof calcMenu.showAfterSelection === 'function') {
				calcMenu.showAfterSelection();
			}
			AndroidSelectionMenu.pendingLongPressSelection = false;
			AndroidSelectionMenu.selectionStartTwips = null;
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

	/** iOS: broadcast the selection to the DOM menu instead of a native popup. */
	private static tryShowIOS(): void {
		if (AndroidSelectionMenu.isProgrammaticSelectionSuppressed()) {
			return;
		}
		if (
			!app.map ||
			!AndroidSelectionMenu.isSelectionDoc() ||
			!AndroidSelectionMenu.hasNonDegenerateSelection()
		) {
			return;
		}
		const startRect = TextSelections.getStartRectangle();
		const endRect = TextSelections.getEndRectangle();
		if (!startRect || !endRect || !app.sectionContainer) {
			return;
		}
		const topViewY = Math.min(startRect.v1Y, startRect.v2Y, endRect.v1Y, endRect.v2Y);
		const leftViewX = Math.min(startRect.v1X, startRect.v3X, endRect.v1X, endRect.v3X);
		const rightViewX = Math.max(startRect.v2X, startRect.v4X, endRect.v2X, endRect.v4X);
		const bottomViewY = Math.max(startRect.v3Y, startRect.v4Y, endRect.v3Y, endRect.v4Y);
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const fromHandles = MobileSelectionEvents.computeAnchorCss({
			topViewY,
			leftViewX,
			rightViewX,
			bottomViewY,
			canvasX: canvasRect.x,
			canvasY: canvasRect.y,
			scale: app.dpiScale || 1,
		});
		const anchor = AndroidSelectionMenu.resolveIosTextAnchor(fromHandles);
		if (!anchor) {
			return;
		}
		const clamped = AndroidSelectionMenu.clampIosAnchor(
			anchor.anchorX,
			anchor.anchorY,
			anchor.anchorBottomY,
		);
		const docLayer = app.map?._docLayer;
		const text =
			docLayer && typeof docLayer._selectedTextContent === 'string'
				? docLayer._selectedTextContent
				: '';
		const mode =
			app.map.getDocType() === 'spreadsheet' ? 'calc' : 'text';
		MobileSelectionEvents.broadcastShow(
			{ anchorX: clamped.x, anchorY: clamped.y, anchorBottomY: clamped.bottomY },
			text,
			mode,
		);
		AndroidSelectionMenu.pendingLongPressSelection = false;
		AndroidSelectionMenu.selectionStartTwips = null;
	}

	/**
	 * Prefer the painted text-selection section. Start/end handle rects in edit
	 * mode can collapse to the canvas origin while the highlight is elsewhere.
	 */
	private static resolveIosTextAnchor(fromHandles: {
		anchorX: number;
		anchorY: number;
		anchorBottomY: number;
	} | null): { anchorX: number; anchorY: number; anchorBottomY: number } | null {
		const fromSection = AndroidSelectionMenu.iosTextSelectionSectionAnchor();
		let anchor =
			fromSection && !AndroidSelectionMenu.iosAnchorCollapsed(fromSection.anchorX, fromSection.anchorY)
				? fromSection
				: fromHandles;
		if (
			anchor &&
			AndroidSelectionMenu.iosAnchorCollapsed(anchor.anchorX, anchor.anchorY)
		) {
			const gesture = AndroidSelectionMenu.lastIosGestureCss;
			if (
				gesture &&
				!AndroidSelectionMenu.iosAnchorCollapsed(gesture.x, gesture.y)
			) {
				anchor = {
					anchorX: gesture.x,
					anchorY: gesture.y,
					anchorBottomY: gesture.y + 18,
				};
			}
		}
		return anchor;
	}

	private static iosTextSelectionSectionAnchor(): {
		anchorX: number;
		anchorY: number;
		anchorBottomY: number;
	} | null {
		const view = app.activeDocument && app.activeDocument.activeView;
		const section = view && view.selectionSection;
		if (!section || !section.isSectionShown() || !section.myTopLeft || !section.size) {
			return null;
		}
		if (section.size[0] < 1 || section.size[1] < 1 || !app.sectionContainer) {
			return null;
		}
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const scale = app.dpiScale || 1;
		const left = section.myTopLeft[0] / scale + canvasRect.x;
		const top = section.myTopLeft[1] / scale + canvasRect.y;
		const right = (section.myTopLeft[0] + section.size[0]) / scale + canvasRect.x;
		const bottom = (section.myTopLeft[1] + section.size[1]) / scale + canvasRect.y;
		if (![left, top, right, bottom].every((value) => Number.isFinite(value))) {
			return null;
		}
		return {
			anchorX: (left + right) / 2,
			anchorY: top,
			anchorBottomY: bottom,
		};
	}

	private static iosAnchorCollapsed(x: number, y: number): boolean {
		if (!Number.isFinite(x) || !Number.isFinite(y)) {
			return true;
		}
		const canvas = document.getElementById('canvas-container');
		const rect = canvas
			? canvas.getBoundingClientRect()
			: app.sectionContainer
				? app.sectionContainer.getCanvasBoundingClientRect()
				: null;
		if (!rect || rect.width < 8 || rect.height < 8) {
			return x < 24 && y < 24;
		}
		return Math.abs(x - rect.left) < 24 && Math.abs(y - rect.top) < 24;
	}

	/** Skip clamping when the canvas box is empty — that pins every anchor to 8,8. */
	private static clampIosAnchor(
		x: number,
		y: number,
		bottomY: number,
	): { x: number; y: number; bottomY: number } {
		const canvas = document.getElementById('canvas-container');
		const rect = canvas ? canvas.getBoundingClientRect() : null;
		if (!rect || rect.width < 8 || rect.height < 8) {
			return {
				x: Math.round(x),
				y: Math.round(y),
				bottomY: Math.round(bottomY),
			};
		}
		return AndroidSelectionMenu.clampAnchorInCss(x, y, bottomY);
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
			AndroidSelectionMenu.isProgrammaticSelectionSuppressed() ||
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
				return;
			}
			if (app.map && app.map.getDocType() === 'spreadsheet') {
				AndroidSelectionMenu.showCalcMenuAtPress();
			}
		};
		AndroidSelectionMenu.tryShowRetryTimer = window.setTimeout(tick, 50);
	}

	/** Excel 单元格里没有可选文字：沿用当前单元格光标，松手后出同一条紧凑菜单。 */
	private static showCalcMenuAtPress(): void {
		const cursorVisible = !!(
			app.calc &&
			app.calc.cellCursorVisible &&
			app.calc.cellCursorRectangle
		);
		// A second click on the cell that is already selected enters in-cell
		// edit and the keyboard then dismisses the menu. Only move the cursor
		// when there is no cell to anchor on.
		if (!cursorVisible) {
			const pos = AndroidSelectionMenu.selectionStartTwips;
			const layer = app.map && app.map._docLayer;
			if (pos && layer && typeof layer._postMouseEvent === 'function') {
				layer._postMouseEvent('buttondown', pos.x, pos.y, 1, 1, 0);
				layer._postMouseEvent('buttonup', pos.x, pos.y, 1, 1, 0);
			}
		}
		const menu = (window as any).AndroidCalcCellMenu;
		if (menu && typeof menu.showAfterSelection === 'function') {
			window.setTimeout(() => menu.showAfterSelection(), cursorVisible ? 0 : 80);
		}
	}

	/** Show menu when core reports a selection without our long-press gesture flags. */
	private static scheduleTryShowFromCoreSelection(): void {
		if (AndroidSelectionMenu.isProgrammaticSelectionSuppressed()) {
			return;
		}
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
		if (AndroidSelectionMenu.isProgrammaticSelectionSuppressed()) {
			AndroidSelectionMenu.hide();
			return;
		}
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
			// Long-press on a picture still has a shape and no text range.
			if (
				AndroidSelectionMenu.isPresentation() &&
				AndroidSelectionMenu.selectionGestureComplete &&
				typeof GraphicSelection !== 'undefined' &&
				GraphicSelection.hasActiveSelection()
			) {
				AndroidSelectionMenu.pendingLongPressSelection = false;
				AndroidSelectionMenu.nativeSelectionDragActive = false;
				GraphicSelection.updateGraphicSelection();
				return;
			}
			AndroidSelectionMenu.cancelGesture();
			AndroidSelectionMenu.clearLocalTextSelection();
		}
	}

	/** Install Android-only hooks without modifying upstream canvas/tile sources. */
	static install(): void {
		if (!AndroidSelectionMenu.isMobileApp() || AndroidSelectionMenu.hooksInstalled) {
			return;
		}
		AndroidSelectionMenu.hooksInstalled = true;
		AndroidSelectionMenu.installInputGestureHooks();

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
				AndroidSelectionMenu.isMobileApp() &&
				AndroidSelectionMenu.shouldSuppressContextMenu()
			) {
				e.preventDefault();
				e.stopPropagation();
				if (app.map && typeof app.map.fire === 'function') {
					app.map.fire('closemobilewizard');
				}
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
					!AndroidSelectionMenu.isMobileApp() ||
					!app.map ||
					!AndroidSelectionMenu.isSelectionDoc()
				) {
					return originalOnClick.call(this, point, e);
				}

				const isCalcEdit =
					app.map.isEditMode() &&
					app.map._docLayer &&
					app.map._docLayer._docType === 'spreadsheet';
				if (
					isCalcEdit &&
					typeof (window as any).AndroidCalcTap?.shouldIgnoreWebClick === 'function' &&
					(window as any).AndroidCalcTap.shouldIgnoreWebClick(e)
				) {
					// Mobile Calc touch input is dispatched once by the native/iOS touch
					// bridge. Suppress its synthetic browser click before it reaches Core.
					return;
				}

				if (Date.now() < AndroidSelectionMenu.suppressClickUntil) {
					return;
				}

				app.map.fire('closepopups');
				app.map.fire('editorgotfocus');

				(<any>this).refreshPosition(point);
				this.clickCount++;
				let inputRequestForTap = false;

				if (!(<any>window).mode.isDesktop()) {
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
				const mouseControl = this as any;
				const isPresentation = app.map.getDocType() === 'presentation';
				const isTouchTap = AndroidSelectionMenu.isTouchTapEligible();
				const textTarget = AndroidSelectionMenu.sectionPointToDocumentTwips(
					clickInfo.sendingPosition,
				);
				let useCorePresentationDoubleTap = false;
				let presentationDoubleTapAttempt: {
					docLayer: any;
					part: number;
					requestGeneration: number;
					targetPoint: { x: number; y: number };
					hadTextCaretOrSelection: boolean;
				} | null = null;

				if (this.clickTimer) {
					app.timerRegistry.clearTimeout(this.clickTimer);
					// A first tap can select the shape. If it began outside text edit,
					// let Core receive the second click to enter the shape's text.
					const firstTap = mouseControl.__mobilePresentationTapSequence;
					const samePresentationTap =
						isPresentation &&
						AndroidSelectionMenu.isEditMode() &&
						isTouchTap &&
						firstTap &&
						firstTap.docLayer === app.map._docLayer &&
						firstTap.part === app.map._docLayer._selectedPart &&
						firstTap.targetPoint &&
						textTarget &&
						Math.abs(firstTap.targetPoint.x - textTarget.x) <=
							Math.round(72 * app.pixelsToTwips) &&
						Math.abs(firstTap.targetPoint.y - textTarget.y) <=
							Math.round(72 * app.pixelsToTwips);
					useCorePresentationDoubleTap = !!(
						samePresentationTap && !firstTap.hadTextCaretOrSelection
					);
					if (useCorePresentationDoubleTap && textTarget) {
						inputRequestForTap = true;
						presentationDoubleTapAttempt = {
							docLayer: app.map._docLayer,
							part: app.map._docLayer._selectedPart,
							requestGeneration: AndroidSelectionMenu.textInputGeneration,
							targetPoint: textTarget,
							hadTextCaretOrSelection: firstTap.hadTextCaretOrSelection,
						};
						mouseControl.__mobilePresentationTapSequence =
							presentationDoubleTapAttempt;
					} else {
						mouseControl.__mobilePresentationTapSequence = null;
						AndroidSelectionMenu.cancelTextInputRequest('double_tap');
					}
				} else {
					inputRequestForTap =
						AndroidSelectionMenu.isEditMode() &&
						app.map.getDocType() !== 'spreadsheet' &&
						isTouchTap &&
						!!textTarget;
					if (isPresentation && inputRequestForTap) {
						mouseControl.__mobilePresentationTapSequence = {
							docLayer: app.map._docLayer,
							part: app.map._docLayer._selectedPart,
							targetPoint: textTarget,
							hadTextCaretOrSelection:
								!!app.file?.textCursor?.visible ||
								AndroidSelectionMenu.hasVisibleTextSelection(),
						};
					} else {
						mouseControl.__mobilePresentationTapSequence = null;
					}
					if (inputRequestForTap) {
						AndroidSelectionMenu.beginTextInputRequest(textTarget);
					}
					(<any>this).sendClick(clickInfo, 1);
					if (!AndroidSelectionMenu.isEditMode()) {
						app.map.focus(false);
					}
				}

				this.clickTimer = app.timerRegistry.setTimeout(
					'clicktimer',
					() => {
						if (this.clickCount === 2) {
							if (useCorePresentationDoubleTap && inputRequestForTap) {
								const attempt = presentationDoubleTapAttempt;
								const canDispatchCoreDoubleTap = !!(
									attempt &&
									mouseControl.__mobilePresentationTapSequence === attempt &&
									AndroidSelectionMenu.textInputGeneration ===
										attempt.requestGeneration &&
									app.map &&
									app.map._docLayer === attempt.docLayer &&
									app.map._docLayer._selectedPart === attempt.part &&
									AndroidSelectionMenu.isEditMode() &&
									AndroidSelectionMenu.isTouchTapEligible()
								);
								mouseControl.__mobilePresentationTapSequence = null;
								if (canDispatchCoreDoubleTap && attempt) {
									const caretConfirmedByFirstTap =
										!attempt.hadTextCaretOrSelection &&
										!!app.file?.textCursor?.visible &&
										AndroidSelectionMenu.isCaretAtTarget(
											app.file.textCursor.rectangle,
											attempt.targetPoint,
										);
									AndroidSelectionMenu.beginTextInputRequest(
										attempt.targetPoint,
										caretConfirmedByFirstTap,
									);
									const request = AndroidSelectionMenu.pendingTextInputRequest;
									if (
										request &&
										AndroidSelectionMenu.pendingTextInputRequest === request &&
										request.docLayer === attempt.docLayer &&
										request.generation ===
											AndroidSelectionMenu.textInputGeneration
									) {
										(<any>this).sendClick(clickInfo, 2);
										AndroidSelectionMenu.resolveTextInputTap();
									}
								}
							} else {
								AndroidSelectionMenu.cancelTextInputRequest('double_tap');
								AndroidSelectionMenu.onDoubleTapAtSectionPoint(
									clickInfo.sendingPosition,
								);
							}
						} else if (this.clickCount > 1) {
							mouseControl.__mobilePresentationTapSequence = null;
							AndroidSelectionMenu.cancelTextInputRequest('multi_tap');
							(<any>this).sendClick(
								clickInfo,
								this.clickCount,
							);
						} else if (inputRequestForTap) {
							AndroidSelectionMenu.resolveTextInputTap();
						}

						mouseControl.__mobilePresentationTapSequence = null;
						this.clickTimer = null;
						this.clickCount = 0;
					},
					250,
				);
			};
		};
		installMouseControlHook();
		AndroidSelectionMenu.installTextInputLifecycleHooks();

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
				if (AndroidSelectionMenu.isProgrammaticSelectionSuppressed()) {
					AndroidSelectionMenu.clearTryShowRetry();
					AndroidSelectionMenu.hide();
					return;
				}
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

				// Edit mode (Writer / Impress): same gesture-driven rules as preview —
				// do not pop the menu on every core textselection (e.g. leftover SelectAll).
				// Calc's menu is the cell bar shown on finger-up. An EMPTY
				// textselection is normal for a selected cell and must not cancel it.
				if (app.map && app.map.getDocType() === 'spreadsheet') {
					return;
				}
				if (
					AndroidSelectionMenu.isSelectionDoc() &&
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
		if (AndroidSelectionMenu.isIOS()) {
			AndroidSelectionMenu.installIosLongPress();
		}
	}

	/**
	 * iOS 没有原生长按桥，在浏览器里等 600ms 再走 onLongPressAt。
	 * Word、PPT、Excel 都走这里。坐标是 CSS 像素，再乘 dpiScale 给共用的 twips 计算。
	 */
	private static installIosLongPress(): void {
		let longPressTimer = 0;
		let startX = 0;
		let startY = 0;
		const scale = () => app.dpiScale || 1;

		const onTouchStart = (e: TouchEvent) => {
			const touch = e.touches[0];
			if (!touch || !AndroidSelectionMenu.isSelectionDoc()) {
				return;
			}
			const target = e.target as HTMLElement | null;
			if (!target || target.tagName !== 'CANVAS') {
				return;
			}
			startX = touch.clientX;
			startY = touch.clientY;
			AndroidSelectionMenu.lastIosGestureCss = { x: startX, y: startY };
			AndroidSelectionMenu.clearTryShowRetry();
			longPressTimer = window.setTimeout(() => {
				longPressTimer = 0;
				AndroidSelectionMenu.onLongPressAt(
					startX * scale(),
					startY * scale(),
				);
			}, 600);
		};

		const onTouchMove = (e: TouchEvent) => {
			const touch = e.touches[0];
			if (!touch) {
				return;
			}
			if (AndroidSelectionMenu.nativeSelectionDragActive) {
				AndroidSelectionMenu.lastIosGestureCss = {
					x: touch.clientX,
					y: touch.clientY,
				};
				AndroidSelectionMenu.updateTextSelectionEndAt(
					touch.clientX * scale(),
					touch.clientY * scale(),
				);
				return;
			}
			// Movement beyond the slop means scrolling intent: cancel the pending long-press.
			if (
				longPressTimer &&
				Math.abs(touch.clientX - startX) + Math.abs(touch.clientY - startY) > 12
			) {
				window.clearTimeout(longPressTimer);
				longPressTimer = 0;
			}
		};

		const onTouchEnd = (e: TouchEvent) => {
			if (longPressTimer) {
				window.clearTimeout(longPressTimer);
				longPressTimer = 0;
			}
			if (AndroidSelectionMenu.nativeSelectionDragActive) {
				const touch = e.changedTouches[0];
				if (touch) {
					AndroidSelectionMenu.lastIosGestureCss = {
						x: touch.clientX,
						y: touch.clientY,
					};
					AndroidSelectionMenu.finishTextSelectionDrag(
						touch.clientX * scale(),
						touch.clientY * scale(),
					);
				} else {
					AndroidSelectionMenu.finishTextSelectionDrag();
				}
			}
		};

		const onTouchCancel = (e: TouchEvent) => {
			// iOS 系统中断(控制中心/来电/应用切换)会发 touchcancel:
			// 清定时器,拖拽中则收尾,避免残留手势状态。
			if (longPressTimer) {
				window.clearTimeout(longPressTimer);
				longPressTimer = 0;
			}
			if (AndroidSelectionMenu.nativeSelectionDragActive) {
				const touch = e.changedTouches[0];
				if (touch) {
					AndroidSelectionMenu.finishTextSelectionDrag(
						touch.clientX * scale(),
						touch.clientY * scale(),
					);
				} else {
					AndroidSelectionMenu.finishTextSelectionDrag();
				}
			}
		};

		document.addEventListener('touchstart', onTouchStart, { passive: true });
		document.addEventListener('touchmove', onTouchMove, { passive: true });
		document.addEventListener('touchend', onTouchEnd, { passive: true });
		document.addEventListener('touchcancel', onTouchCancel, { passive: true });
	}
}

(window as any).AndroidSelectionMenu = AndroidSelectionMenu;
if ((window as any).ThisIsTheAndroidApp || (window as any).ThisIsTheiOSApp) {
	AndroidSelectionMenu.install();
}
