/*
 * Android Calc edit-mode cell tap bridge.
 * WebView often skips synthetic click when the hidden contenteditable holds IME focus;
 * LOActivity / COWebView detect a short tap natively and call dispatchTapAt().
 */

class AndroidCalcTap {
	private static lastTapAt = 0;
	private static readonly dedupeMs = 120;

	private static report(message: string): void {
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function') {
			window.postMobileMessage('CALC_CELL_TAP ' + message);
		}
	}

	static dispatchTapAt(viewX: number, viewY: number): void {
		try {
			if (!window.ThisIsTheAndroidApp || !app.map || !app.map.isEditMode()) {
				AndroidCalcTap.report('skipped=not_edit_mode');
				return;
			}
			if (!app.map._docLayer || app.map._docLayer._docType !== 'spreadsheet') {
				AndroidCalcTap.report('skipped=not_calc');
				return;
			}
			if (!app.sectionContainer) {
				AndroidCalcTap.report('failed=no_container');
				return;
			}

			const now = Date.now();
			if (now - AndroidCalcTap.lastTapAt < AndroidCalcTap.dedupeMs) {
				return;
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
				return;
			}

			const twips = app.sectionContainer.dispatchCalcTapAtClient(viewX, viewY);
			if (!twips) {
				AndroidCalcTap.report('failed=no_pos');
				return;
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
		} catch (e: any) {
			const msg = e && e.message ? e.message : String(e);
			AndroidCalcTap.report('failed=' + msg);
		}
	}
}

(window as any).AndroidCalcTap = AndroidCalcTap;
