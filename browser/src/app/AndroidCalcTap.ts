/*
 * Calc edit-mode cell tap bridge.
 * A short tap often never becomes a click: Android WebView drops it while the hidden
 * contenteditable holds IME focus, and iOS marks any finger wobble as a drag so the
 * canvas click is discarded. Either way the cell range stays selected.
 * Android: LOActivity / COWebView call dispatchTapAt() with physical pixels.
 * iOS: a document touch listener below does the same for a short tap on the grid.
 */

class AndroidCalcTap {
	private static lastTapAt = 0;
	private static readonly dedupeMs = 120;
	private static iosInstalled = false;
	private static iosDown: { x: number; y: number; at: number } | null = null;
	private static iosDispatchedAt = 0;
	private static readonly iosTapMaxMs = 450;
	private static readonly iosTapSlopCss = 16;

	private static report(message: string): void {
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function') {
			window.postMobileMessage('CALC_CELL_TAP ' + message);
		}
		if ((window as any).ThisIsTheiOSApp) {
			console.log('calc_cell_tap: ' + message);
		}
	}

	/** True when the iOS short-tap path already moved the cell, so the late click must not. */
	static shouldIgnoreWebClick(): boolean {
		return Date.now() - AndroidCalcTap.iosDispatchedAt < 400;
	}

	static dispatchTapAt(viewX: number, viewY: number): boolean {
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
				AndroidCalcTap.report('header_handled');
				return true;
			}

			const twips = app.sectionContainer.dispatchCalcTapAtClient(viewX, viewY);
			if (!twips) {
				AndroidCalcTap.report('failed=no_pos');
				return false;
			}

			AndroidCalcTap.report(
				'client=' +
					Math.round(viewX) +
					',' +
					Math.round(viewY) +
					' twips=' +
					twips.x +
					',' +
					twips.y,
			);
			window.setTimeout(() => {
				(window as any).AndroidCalcCellMenu?.tryShow();
			}, 150);
			return true;
		} catch (e: any) {
			const msg = e && e.message ? e.message : String(e);
			AndroidCalcTap.report('failed=' + msg);
			return false;
		}
	}

	private static iosTargetIsGrid(target: EventTarget | null): boolean {
		const el = target as HTMLElement | null;
		if (!el || el.tagName !== 'CANVAS') {
			return false;
		}
		return true;
	}

	static installIOS(): void {
		if (AndroidCalcTap.iosInstalled || !(window as any).ThisIsTheiOSApp) {
			return;
		}
		AndroidCalcTap.iosInstalled = true;
		document.addEventListener(
			'touchstart',
			(event: TouchEvent) => {
				if (event.touches.length !== 1) {
					AndroidCalcTap.iosDown = null;
					return;
				}
				const touch = event.touches[0];
				AndroidCalcTap.iosDown = {
					x: touch.clientX,
					y: touch.clientY,
					at: Date.now(),
				};
			},
			true,
		);
		document.addEventListener(
			'touchend',
			(event: TouchEvent) => {
				const down = AndroidCalcTap.iosDown;
				AndroidCalcTap.iosDown = null;
				if (!down || event.changedTouches.length !== 1) {
					return;
				}
				if (!AndroidCalcTap.iosTargetIsGrid(event.target)) {
					return;
				}
				const touch = event.changedTouches[0];
				const dx = touch.clientX - down.x;
				const dy = touch.clientY - down.y;
				if (Date.now() - down.at > AndroidCalcTap.iosTapMaxMs) {
					return;
				}
				if (dx * dx + dy * dy > AndroidCalcTap.iosTapSlopCss * AndroidCalcTap.iosTapSlopCss) {
					return;
				}
				const scale = app.dpiScale || 1;
				const moved = AndroidCalcTap.dispatchTapAt(
					touch.clientX * scale,
					touch.clientY * scale,
				);
				if (moved) {
					AndroidCalcTap.iosDispatchedAt = Date.now();
					const calcMenu = (window as any).AndroidCalcCellMenu;
					if (calcMenu && typeof calcMenu.dismiss === 'function') {
						calcMenu.dismiss();
					}
				}
			},
			true,
		);
		document.addEventListener(
			'touchcancel',
			() => {
				AndroidCalcTap.iosDown = null;
			},
			true,
		);
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
