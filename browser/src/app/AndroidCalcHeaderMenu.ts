/*
 * Android Calc row/column header context menu bridge.
 * Replaces MobileWizard with native project-style popup.
 */

class AndroidCalcHeaderMenu {
	private static lastShowAt = 0;
	private static suppressHideUntil = 0;
	private static readonly dedupeMs = 180;
	private static readonly showDelayMs = 80;
	private static readonly hideGraceMs = 450;
	private static closePopupsBound = false;

	private static ensureClosePopupsListener(): void {
		if (AndroidCalcHeaderMenu.closePopupsBound || !app.map) {
			return;
		}
		AndroidCalcHeaderMenu.closePopupsBound = true;
		app.map.on('closepopups', () => {
			AndroidCalcHeaderMenu.hide();
		});
	}

	static hide(): void {
		if (Date.now() < AndroidCalcHeaderMenu.suppressHideUntil) {
			return;
		}
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function') {
			window.postMobileMessage('CALC_HEADER_POPUP hide');
		}
	}

	static tryShow(
		header: cool.Header,
		evt?: MouseEvent,
		indexOverride?: number,
	): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		if (!app.map || app.map.getDocType() !== 'spreadsheet') {
			return;
		}

		const headerSection = header as any;
		const index =
			indexOverride !== undefined
				? indexOverride
				: headerSection._lastMouseOverIndex !== undefined
					? headerSection._lastMouseOverIndex
					: headerSection._mouseOverEntry?.index;
		if (index === undefined || index < 0) {
			window.postMobileMessage('CALC_HEADER_DISPATCH skip=no_index');
			return;
		}

		const now = Date.now();
		if (now - AndroidCalcHeaderMenu.lastShowAt < AndroidCalcHeaderMenu.dedupeMs) {
			return;
		}

		AndroidCalcHeaderMenu.ensureClosePopupsListener();
		app.map.fire('closemobilewizard');
		if (window.AndroidCalcCellMenu) {
			window.AndroidCalcCellMenu.hide();
		}

		const isColumn = headerSection._isColumn;
		const type = isColumn ? 'column' : 'row';
		let cssX = 0;
		let cssY = 0;
		let cssBottom = 0;
		const rect = headerSection.getHeaderEntryBoundingClientRect(index);
		if (rect && rect.left !== undefined && rect.top !== undefined) {
			cssX = (rect.left + rect.right) / 2;
			cssY = rect.top;
			cssBottom = rect.bottom !== undefined ? rect.bottom : cssY;
		} else if (evt) {
			cssX = evt.clientX;
			cssY = evt.clientY;
			cssBottom = evt.clientY;
		} else {
			return;
		}

		// WebView 物理像素（与单元格浮窗 / SelectionMenuController 一致）
		const scale = app.dpiScale || 1;
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const anchorX = Math.round((cssX - canvasRect.x) * scale);
		const anchorY = Math.round((cssY - canvasRect.y) * scale);
		const anchorBottom = Math.round((cssBottom - canvasRect.y) * scale);

		const payload = JSON.stringify({
			type: type,
			index: index,
			anchorX: anchorX,
			anchorY: anchorY,
			anchorBottom: anchorBottom,
		});

		AndroidCalcHeaderMenu.suppressHideUntil =
			now + AndroidCalcHeaderMenu.showDelayMs + AndroidCalcHeaderMenu.hideGraceMs;

		window.setTimeout(() => {
			window.postMobileMessage('CALC_HEADER_POPUP show ' + payload);
			window.postMobileMessage(
				'CALC_HEADER_DISPATCH show=' + type + ' index=' + index,
			);
			AndroidCalcHeaderMenu.suppressHideUntil =
				Date.now() + AndroidCalcHeaderMenu.hideGraceMs;
		}, AndroidCalcHeaderMenu.showDelayMs);

		if (evt) {
			evt.preventDefault();
			evt.stopPropagation();
		}

		AndroidCalcHeaderMenu.lastShowAt = now;
	}

	/** 原生 WebView 坐标（MotionEvent.getX/Y，物理像素，相对 WebView 左上角） */
	static dispatchAtClient(
		clientX: number,
		clientY: number,
		mode: 'tap' | 'longpress',
	): boolean {
		if (!window.ThisIsTheAndroidApp || !app.sectionContainer) {
			AndroidCalcHeaderMenu.reportDispatch(mode, false, clientX, clientY, 'no_container');
			return false;
		}
		const hit = app.sectionContainer.dispatchCalcHeaderMenuAtClient(
			clientX,
			clientY,
			mode,
		);
		AndroidCalcHeaderMenu.reportDispatch(mode, hit, clientX, clientY);
		return hit;
	}

	private static reportDispatch(
		mode: 'tap' | 'longpress',
		hit: boolean,
		clientX: number,
		clientY: number,
		reason?: string,
	): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		let message =
			'CALC_HEADER_DISPATCH ' +
			mode +
			' hit=' +
			hit +
			' at=' +
			Math.round(clientX) +
			',' +
			Math.round(clientY);
		if (reason) {
			message += ' reason=' + reason;
		}
		window.postMobileMessage(message);
	}
}

(window as any).AndroidCalcHeaderMenu = AndroidCalcHeaderMenu;
