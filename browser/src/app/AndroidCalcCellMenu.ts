/*
 * Android Calc cell selection floating menu bridge.
 * Shows native popup (copy/paste/cut/clear/translate) when a cell is selected.
 */

class AndroidCalcCellMenu {
	private static lastShowAt = 0;
	private static readonly dedupeMs = 180;

	static hide(): void {
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function') {
			window.postMobileMessage('CALC_CELL_POPUP hide');
		}
	}

	static tryShow(retry = 0): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		const now = Date.now();
		if (now - AndroidCalcCellMenu.lastShowAt < AndroidCalcCellMenu.dedupeMs && retry === 0) {
			AndroidCalcCellMenu.reportSkip('dedupe', retry);
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
		if (!app.calc.cellCursorVisible || !app.calc.cellCursorRectangle) {
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

		const rect = app.calc.cellCursorRectangle;
		const cx = (rect.pX1 + rect.pX2) / 2;
		const pixelTop = docLayer._twipsToPixels(new cool.SimplePoint(cx, rect.pY1));
		const pixelBottom = docLayer._twipsToPixels(new cool.SimplePoint(cx, rect.pY2));
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const scale = app.dpiScale || 1;
		// 与 GraphicSelectionMiddleware / AndroidSelectionMenu 一致：WebView 物理像素
		const cssX = pixelTop.x / scale + canvasRect.x;
		const cssY = pixelTop.y / scale + canvasRect.y;
		const cssBottomY = pixelBottom.y / scale + canvasRect.y;
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
		AndroidCalcCellMenu.lastShowAt = now;
	}

	private static reportSkip(reason: string, retry: number): void {
		if (retry > 0) {
			return;
		}
		window.postMobileMessage('CALC_CELL_DISPATCH skip=' + reason);
	}
}

(window as any).AndroidCalcCellMenu = AndroidCalcCellMenu;
