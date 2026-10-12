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
		const layout = app.activeDocument && app.activeDocument.activeLayout;
		const viewed = layout && layout.viewedRectangle;
		if (!viewed) {
			AndroidCalcCellMenu.reportSkip('no_viewed_rect', retry);
			return;
		}
		// pX/pY are core pixels. Passing them through _twipsToPixels treats
		// pixels as twips and collapses every cell to the top-left.
		// Match AndroidSelectionMenu: view pixels, then CSS, then physical.
		const docAnchor = app.sectionContainer.getDocumentAnchor
			? app.sectionContainer.getDocumentAnchor()
			: [0, 0];
		const originX = viewed.pX1 - (docAnchor[0] || 0);
		const originY = viewed.pY1 - (docAnchor[1] || 0);
		const viewX = (rect.pX1 + rect.pX2) / 2 - originX;
		const viewTop = rect.pY1 - originY;
		const viewBottom = rect.pY2 - originY;
		const canvasRect = app.sectionContainer.getCanvasBoundingClientRect();
		const scale = app.dpiScale || 1;
		const cssX = viewX / scale + canvasRect.x;
		const cssY = viewTop / scale + canvasRect.y;
		const cssBottomY = viewBottom / scale + canvasRect.y;
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
