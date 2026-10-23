/*
 * Calc edit-mode cell tap bridge.
 * A short tap often never becomes a click: Android WebView drops it while the hidden
 * contenteditable holds IME focus, and iOS marks any finger wobble as a drag so the
 * canvas click is discarded. Either way the cell range stays selected.
 * Android: LOActivity / COWebView call dispatchTapAt() with physical pixels.
 * iOS: CanvasSectionContainer calls the iOS methods after section hit testing and
 * normal touch dispatch. Graphic sections keep their own move/resize handlers.
 */

class AndroidCalcTap {
	private static lastTapAt = 0;
	private static androidDispatchedAt = 0;
	private static readonly dedupeMs = 35;
	private static readonly doubleTapWindowMs = 350;
	private static lastCellTap: {
		at: number;
		x: number;
		y: number;
		docLayer: any;
		part: number;
		cellIdentity?: string;
	} | null = null;
	private static cellMenuTimer = 0;
	private static iosInstalled = false;
	private static iosDown: {
		x: number; y: number; at: number; identifier: number;
		docLayer: any; part: number; section: CanvasSectionObject;
	} | null = null;
	private static iosLastCellTap: {
		at: number; x: number; y: number;
		docLayer: any; part: number; cell: string | null;
	} | null = null;
	private static iosMap: any = null;
	private static readonly iosReset = () => AndroidCalcTap.resetIOSState();
	private static iosDispatchedAt = 0;
	private static iosIgnoreClickUntil = 0;
	private static iosMenuTimer = 0;
	private static readonly iosTapMaxMs = 450;
	private static readonly iosTapIdentityLifetimeMs = 5000;
	static readonly iosTapSlopCss = 5;

	private static report(message: string): void {
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function') {
			window.postMobileMessage('CALC_CELL_TAP ' + message);
		}
		if ((window as any).ThisIsTheiOSApp) {
			console.log('calc_cell_tap: ' + message);
		}
	}

	/** True for the synthetic touch click after the native/iOS Calc tap bridge. */
	static shouldIgnoreWebClick(event?: MouseEvent): boolean {
		if (AndroidCalcTap.isIOSCalc()) {
			// Only suppress a compatibility click for a touch already owned by
			// the iOS grid path. Mouse/trackpad clicks still use MouseControl.
			if ((event as any)?.pointerType === 'mouse') return false;
			return Date.now() < AndroidCalcTap.iosIgnoreClickUntil;
		}
		if (event) {
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
		}
		if (AndroidSelectionMenu.isTouchGeneratedClick(event)) {
			return true;
		}
		const now = Date.now();
		return (
			(now - AndroidCalcTap.iosDispatchedAt >= 0 &&
				now - AndroidCalcTap.iosDispatchedAt < 400) ||
			(now - AndroidCalcTap.androidDispatchedAt >= 0 &&
				now - AndroidCalcTap.androidDispatchedAt < 400)
		);
	}

	static dispatchTapAt(viewX: number, viewY: number): boolean {
		if (AndroidCalcTap.isIOSCalc()) {
			return AndroidCalcTap.dispatchIOSTapAt(viewX, viewY);
		}
		try {
			const mobile =
				!!window.ThisIsTheAndroidApp || !!(window as any).ThisIsTheiOSApp;
			if (!mobile || !app.map || !app.map.isEditMode()) {
				AndroidCalcTap.report('skipped=not_edit_mode');
				return false;
			}
			if (!app.map._docLayer || app.map._docLayer._docType !== 'spreadsheet') {
				AndroidCalcTap.report('skipped=not_calc');
				return false;
			}
			if (!app.sectionContainer) {
				AndroidCalcTap.report('failed=no_container');
				return false;
			}

			const now = Date.now();
			if (now - AndroidCalcTap.lastTapAt < AndroidCalcTap.dedupeMs) {
				return false;
			}
			AndroidCalcTap.lastTapAt = now;

			try {
				if (app.map._textInput && typeof app.map._textInput.blur === 'function') {
					app.map._textInput.blur();
				}
			} catch (_e) {
				// Best-effort: commit in-cell edit before switching cells.
			}

			// Prefer row/column header selection over single-cell tap (AndroidCalcTap regressed this).
			if (
				(window as any).AndroidCalcHeaderMenu &&
				typeof (window as any).AndroidCalcHeaderMenu.dispatchAtClient === 'function' &&
				(window as any).AndroidCalcHeaderMenu.dispatchAtClient(viewX, viewY, 'tap')
			) {
				AndroidCalcTap.androidDispatchedAt = now;
				AndroidCalcTap.lastCellTap = null;
				AndroidCalcTap.clearCellMenuTimer();
				AndroidCalcTap.report('header_handled');
				return true;
			}

			const position = app.sectionContainer.getCalcTapPositionAtClient(viewX, viewY);
			if (!position) {
				AndroidCalcTap.report('failed=no_pos');
				return false;
			}
			const docLayer = app.map._docLayer;
			const part = docLayer._selectedPart;
			const previousTap = AndroidCalcTap.lastCellTap;
			const currentCell = app.calc && app.calc.cellCursorVisible
				? app.calc.cellCursorRectangle
				: null;
			const currentCellIdentity = currentCell && app.calc?.cellAddress
				? [part, ...currentCell.toArray(), ...app.calc.cellAddress.toArray()].join(':')
				: '';
			const firstPointInsideCurrentCell = !!(
				currentCell &&
				typeof currentCell.containsPoint === 'function' &&
				previousTap &&
				currentCell.containsPoint([previousTap.x, previousTap.y])
			);
			const secondPointInsideCurrentCell = !!(
				currentCell &&
				typeof currentCell.containsPoint === 'function' &&
				currentCell.containsPoint([position.x, position.y])
			);
			const isSameCellDoubleTap = !!(
				previousTap &&
				now - previousTap.at <= AndroidCalcTap.doubleTapWindowMs &&
				previousTap.docLayer === docLayer &&
				previousTap.part === part &&
				!!currentCellIdentity &&
				firstPointInsideCurrentCell &&
				secondPointInsideCurrentCell &&
				(!previousTap.cellIdentity || previousTap.cellIdentity === currentCellIdentity)
			);

			if (isSameCellDoubleTap) {
				previousTap.cellIdentity = currentCellIdentity;
				AndroidCalcTap.clearCellMenuTimer();
				AndroidCalcTap.lastCellTap = null;
				AndroidSelectionMenu.beginTextInputRequest(position);
				const doubleTapPosition = app.sectionContainer.dispatchCalcTapAtClient(
					viewX,
					viewY,
					2,
				);
				if (!doubleTapPosition) {
					AndroidSelectionMenu.cancelTextInputRequest('calc_double_tap_failed');
					AndroidCalcTap.report('failed=no_double_tap_pos');
					return false;
				}
				AndroidCalcTap.androidDispatchedAt = now;
				AndroidSelectionMenu.resolveTextInputTap();
				AndroidCalcTap.report('double_tap_cell=' + part);
			} else {
				AndroidCalcTap.clearCellMenuTimer();
				const tapPosition = app.sectionContainer.dispatchCalcTapAtClient(viewX, viewY, 1);
				if (!tapPosition) {
					AndroidCalcTap.report('failed=no_pos');
					return false;
				}
				AndroidCalcTap.androidDispatchedAt = now;
				AndroidCalcTap.lastCellTap = {
					at: now,
					x: tapPosition.x,
					y: tapPosition.y,
					docLayer,
					part,
				};
				AndroidCalcTap.cellMenuTimer = window.setTimeout(() => {
					AndroidCalcTap.cellMenuTimer = 0;
					if (AndroidCalcTap.lastCellTap?.at === now) {
						AndroidCalcTap.lastCellTap = null;
						(window as any).AndroidCalcCellMenu?.tryShow();
					}
				}, AndroidCalcTap.doubleTapWindowMs);
			}

			AndroidCalcTap.report(
				'client=' +
					Math.round(viewX) +
					',' +
					Math.round(viewY) +
				' core=' +
					position.x +
					',' +
					position.y,
			);
			return true;
		} catch (e: any) {
			const msg = e && e.message ? e.message : String(e);
			AndroidCalcTap.report('failed=' + msg);
			return false;
		}
	}

	private static clearCellMenuTimer(): void {
		if (AndroidCalcTap.cellMenuTimer) {
			window.clearTimeout(AndroidCalcTap.cellMenuTimer);
			AndroidCalcTap.cellMenuTimer = 0;
		}
	}

	static isIOSCalc(): boolean {
		return !!(window as any).ThisIsTheiOSApp &&
			app.map?._docLayer?._docType === 'spreadsheet';
	}

	static resetIOSState(): void {
		AndroidCalcTap.clearIOSMenuTimer();
		if (!AndroidCalcTap.isIOSCalc() && !AndroidCalcTap.iosDown && !AndroidCalcTap.iosLastCellTap) return;
		AndroidCalcTap.iosDown = null;
		AndroidCalcTap.iosLastCellTap = null;
		if (AndroidCalcTap.isIOSCalc()) (window as any).AndroidCalcCellMenu?.dismiss();
	}

	private static ensureIOSMapListeners(): void {
		if (AndroidCalcTap.iosMap === app.map) return;
		if (AndroidCalcTap.iosMap) {
			AndroidCalcTap.iosMap.off('scrolltopart docloaded', AndroidCalcTap.iosReset);
		}
		AndroidCalcTap.resetIOSState();
		AndroidCalcTap.iosMap = app.map;
		app.map.on('scrolltopart docloaded', AndroidCalcTap.iosReset);
	}

	static beginIOSTouch(event: TouchEvent): void {
		if (!AndroidCalcTap.isIOSCalc()) return;
		AndroidCalcTap.ensureIOSMapListeners();
		if (event.touches.length !== 1) {
			AndroidCalcTap.resetIOSState();
			return;
		}
		const touch = event.touches[0];
		const section = app.sectionContainer.getIOSCalcSectionAtClient(
			touch.clientX, touch.clientY, event.target,
		);
		// A selected graphic is only excluded where its actual section or
		// handle is hit. Tapping the grid outside it must still deselect it.
		if (!section || section.name !== app.CSections.MouseControl.name) {
			// The next compatibility click belongs to this object/header, not
			// the grid touch that happened shortly before it.
			AndroidCalcTap.iosIgnoreClickUntil = 0;
			AndroidCalcTap.resetIOSState();
			return;
		}
		(window as any).AndroidCalcCellMenu?.dismiss();
		AndroidCalcTap.iosIgnoreClickUntil = Date.now() + 1000;
		AndroidCalcTap.iosDown = {
			x: touch.clientX, y: touch.clientY, at: Date.now(), identifier: touch.identifier,
			docLayer: app.map._docLayer, part: app.map._docLayer._selectedPart, section,
		};
	}

	static moveIOSTouch(event: TouchEvent): void {
		if (!AndroidCalcTap.isIOSCalc()) return;
		const down = AndroidCalcTap.iosDown;
		if (!down) return;
		const touch = event.touches[0];
		if (event.touches.length !== 1 || touch.identifier !== down.identifier ||
			(touch.clientX - down.x) ** 2 + (touch.clientY - down.y) ** 2 > AndroidCalcTap.iosTapSlopCss ** 2) {
			AndroidCalcTap.resetIOSState();
		}
	}

	static endIOSTouch(event: TouchEvent): void {
		if (!AndroidCalcTap.isIOSCalc()) return;
		const down = AndroidCalcTap.iosDown;
		AndroidCalcTap.iosDown = null;
		if (!down) return;
		AndroidCalcTap.iosIgnoreClickUntil = Date.now() + 700;
		const touch = event.changedTouches[0];
		if (event.touches.length || event.changedTouches.length !== 1 ||
			touch.identifier !== down.identifier || Date.now() - down.at > AndroidCalcTap.iosTapMaxMs ||
			down.docLayer !== app.map._docLayer || down.part !== app.map._docLayer._selectedPart ||
			app.sectionContainer.isDraggingSomething() ||
			(touch.clientX - down.x) ** 2 + (touch.clientY - down.y) ** 2 > AndroidCalcTap.iosTapSlopCss ** 2 ||
			app.sectionContainer.getIOSCalcSectionAtClient(touch.clientX, touch.clientY, event.target) !== down.section) {
			AndroidCalcTap.resetIOSState();
			return;
		}
		const scale = app.dpiScale || 1;
		AndroidCalcTap.dispatchIOSTapAt(touch.clientX * scale, touch.clientY * scale, down.at);
	}

	private static clearIOSMenuTimer(): void {
		if (!AndroidCalcTap.iosMenuTimer) return;
		window.clearTimeout(AndroidCalcTap.iosMenuTimer);
		AndroidCalcTap.iosMenuTimer = 0;
	}

	// A slow repeated tap may be the first half of a double tap on an already
	// selected cell, so the menu waits out the double-tap window first.
	private static scheduleIOSCellMenu(tap: NonNullable<typeof AndroidCalcTap.iosLastCellTap>): void {
		AndroidCalcTap.clearIOSMenuTimer();
		AndroidCalcTap.iosMenuTimer = window.setTimeout(() => {
			AndroidCalcTap.iosMenuTimer = 0;
			// A finger that went down inside the window decides on touch-up.
			if (AndroidCalcTap.iosDown || AndroidCalcTap.iosLastCellTap !== tap || !AndroidCalcTap.isIOSCalc() ||
				tap.docLayer !== app.map._docLayer || tap.part !== app.map._docLayer._selectedPart) return;
			AndroidCalcTap.iosLastCellTap = null;
			(window as any).AndroidCalcCellMenu?.showForIOSTap(tap.docLayer, tap.part, tap.cell);
		}, AndroidCalcTap.doubleTapWindowMs);
	}

	private static iosCellAt(position: { x: number; y: number }): string | null {
		const cursor = app.calc.cellCursorVisible && app.calc.cellCursorRectangle;
		const address = app.calc.cellAddress;
		if (cursor && address && cursor.containsPoint([position.x, position.y])) {
			return address.x + ':' + address.y;
		}
		const layer = app.map._docLayer;
		const geometry = layer.sheetGeometry;
		if (!geometry || geometry.getPart() !== layer._selectedPart) return null;
		// MouseControl.refreshPosition adds the viewed (or frozen-pane)
		// core-pixel origin; SimplePoint.x/y expose those pixels as tile twips.
		// Do not convert them again to print twips before querying geometry.
		const cell = geometry.getCellFromPos(new cool.Point(position.x, position.y), 'tiletwips');
		if (!Number.isFinite(cell.x) || !Number.isFinite(cell.y)) return null;
		return cell.x + ':' + cell.y;
	}

	static onIOSCellCursor(): void {
		if (!AndroidCalcTap.isIOSCalc()) return;
		AndroidSelectionMenu.onIOSCalcCellCursorUpdate();
		const previous = AndroidCalcTap.iosLastCellTap;
		const address = app.calc.cellAddress;
		if (!previous) return;
		if (previous.docLayer !== app.map._docLayer || previous.part !== app.map._docLayer._selectedPart ||
			Date.now() - previous.at > AndroidCalcTap.iosTapIdentityLifetimeMs) {
			AndroidCalcTap.resetIOSState();
			return;
		}
		const cursor = app.calc.cellCursorVisible && app.calc.cellCursorRectangle;
		// Calc may clear the old cursor before it publishes the newly selected
		// one. Keep the tap identity briefly through that transient EMPTY update;
		// a real cursor at a different address still invalidates it below.
		if (!cursor) return;
		if (!address || !cursor.containsPoint([previous.x, previous.y])) {
			AndroidCalcTap.resetIOSState();
			return;
		}
		// Core's address also canonicalizes merged cells after the first tap.
		previous.cell = address.x + ':' + address.y;
	}

	/** startedAt is the touch-down time; double taps are measured from the previous tap-up. */
	private static dispatchIOSTapAt(viewX: number, viewY: number, startedAt?: number): boolean {
		if (!AndroidCalcTap.isIOSCalc() || !app.sectionContainer) return false;
		const scale = app.dpiScale || 1;
		const section = app.sectionContainer.getIOSCalcSectionAtClient(viewX / scale, viewY / scale);
		if (!section || section.name !== app.CSections.MouseControl.name) return false;
		const position = app.sectionContainer.getCalcTapPositionAtClient(viewX, viewY);
		if (!position) return false;
		const now = Date.now();
		const docLayer = app.map._docLayer;
		const part = docLayer._selectedPart;
		const cell = AndroidCalcTap.iosCellAt(position);
		const previous = AndroidCalcTap.iosLastCellTap;
		const sameCell = previous && previous.docLayer === docLayer && previous.part === part &&
			cell !== null && previous.cell === cell;
		AndroidCalcTap.iosDispatchedAt = now;
		AndroidCalcTap.iosIgnoreClickUntil = now + 700;
		const menuWasPending = !!AndroidCalcTap.iosMenuTimer;
		AndroidCalcTap.clearIOSMenuTimer();
		if (sameCell && (startedAt ?? now) - previous.at > AndroidCalcTap.doubleTapWindowMs) {
			previous.at = now;
			AndroidCalcTap.scheduleIOSCellMenu(previous);
			return true;
		}
		if (sameCell && !app.map.isEditMode()) {
			// Read-only repeated taps keep the selection. Never send the
			// second core click or request text input until edit mode is active.
			previous.at = now;
			if (menuWasPending) AndroidCalcTap.scheduleIOSCellMenu(previous);
			return true;
		}
		if (sameCell && app.map.isEditMode()) {
			AndroidCalcTap.iosLastCellTap = null;
			(window as any).AndroidCalcCellMenu?.dismiss();
			AndroidSelectionMenu.beginTextInputRequest(position);
			app.sectionContainer.dispatchCalcTapAtClient(viewX, viewY, 2);
			AndroidSelectionMenu.resolveTextInputTap();
			return true;
		}
		(window as any).AndroidCalcCellMenu?.dismiss();
		if (app.map.isEditMode()) app.map._textInput?.blur();
		app.sectionContainer.dispatchCalcTapAtClient(viewX, viewY, 1);
		AndroidCalcTap.iosLastCellTap = { at: now, x: position.x, y: position.y, docLayer, part, cell };
		return true;
	}

	static installIOS(): void {
		if (AndroidCalcTap.iosInstalled || !(window as any).ThisIsTheiOSApp) {
			return;
		}
		AndroidCalcTap.iosInstalled = true;
		// CanvasSectionContainer owns grid dispatch. These listeners only clear
		// stale cell identities when the user leaves the canvas or cancels.
		document.addEventListener(
			'touchstart',
			(event: TouchEvent) => {
				if (!AndroidCalcTap.isIOSCalc()) {
					if (AndroidCalcTap.iosLastCellTap || AndroidCalcTap.iosDown) AndroidCalcTap.resetIOSState();
					return;
				}
				AndroidCalcTap.ensureIOSMapListeners();
				const touch = event.touches[0];
				if (event.touches.length !== 1 || !app.sectionContainer?.getIOSCalcSectionAtClient(
					touch.clientX, touch.clientY, event.target,
				)) AndroidCalcTap.resetIOSState();
			},
			true,
		);
		document.addEventListener(
			'touchcancel',
			AndroidCalcTap.iosReset,
			true,
		);
		app.events.on('updatepermission', AndroidCalcTap.iosReset);
	}
}

function bootIOSCalcTap(): void {
	AndroidCalcTap.installIOS();
}
bootIOSCalcTap();
if (typeof document !== 'undefined' && document.readyState === 'loading') {
	document.addEventListener('DOMContentLoaded', bootIOSCalcTap, { once: true });
}

(window as any).AndroidCalcTap = AndroidCalcTap;
