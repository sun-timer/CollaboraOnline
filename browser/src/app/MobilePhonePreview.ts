/*
 * Phone preview frame for iOS and Android.
 * Spreadsheet fits the used range into the frame; Writer and Impress keep _fitWidthZoom.
 */

class MobilePhonePreview {
	private static readonly STYLE_ID = 'mobile-phone-preview-style';
	private static readonly PHONE_ASPECT = 390 / 844;
	/** Viewports at or below this width fill the webview (phone / iPhone). */
	private static readonly NARROW_VIEWPORT_MAX = 600;
	private static readonly PREVIEW_STYLE_CSS =
		'body.mobile-phone-preview-active #main-document-content{' +
		'position:fixed!important;border-radius:16px!important;' +
		'overflow:hidden!important;z-index:10000!important;' +
		'pointer-events:auto!important;background:#fff!important;' +
		'box-shadow:0 0 0 9999px rgba(0,0,0,.55),0 0 0 3px #D0D4DC,0 8px 28px rgba(0,0,0,.25)!important;' +
		'flex:none!important;touch-action:manipulation!important;}' +
		'body.mobile-phone-preview-active #document-container,' +
		'body.mobile-phone-preview-active [data-docType=\'spreadsheet\'] #document-container{' +
		'position:absolute!important;top:0!important;left:0!important;' +
		'right:0!important;bottom:0!important;width:auto!important;height:auto!important;}' +
		'body.mobile-phone-preview-active:not(.mobile-phone-preview-spreadsheet) #map,' +
		'body.mobile-phone-preview-active:not(.mobile-phone-preview-spreadsheet) #map .leaflet-container,' +
		'body.mobile-phone-preview-active:not(.mobile-phone-preview-spreadsheet) #map .leaflet-map-pane,' +
		'body.mobile-phone-preview-active:not(.mobile-phone-preview-spreadsheet) #map .leaflet-tile-pane,' +
		'body.mobile-phone-preview-active:not(.mobile-phone-preview-spreadsheet) #map .leaflet-canvas-container{' +
		'position:absolute!important;top:0!important;left:0!important;' +
		'width:100%!important;height:100%!important;}' +
		'body.mobile-phone-preview-active #navigation-sidebar,' +
		'body.mobile-phone-preview-active #sidebar-dock-wrapper,' +
		'body.mobile-phone-preview-active nav.main-nav,' +
		'body.mobile-phone-preview-active #toolbar-wrapper,' +
		'body.mobile-phone-preview-active #toolbar-down,' +
		'body.mobile-phone-preview-active #spreadsheet-toolbar,' +
		'body.mobile-phone-preview-active #presentation-controls-wrapper{' +
		'display:none!important;pointer-events:none!important;}' +
		'body.mobile-phone-preview-active #mobile-edit-button{' +
		'z-index:10001!important;pointer-events:auto!important;}' +
		'body.mobile-phone-preview-active.mobile-phone-preview-narrow #main-document-content{' +
		'border-radius:0!important;box-shadow:none!important;}' +
		'body.mobile-phone-preview-pending #document-container{visibility:hidden!important;}';

	private static readonly CALC_FIT_MIN_COLUMNS = 5;
	private static readonly CALC_FIT_MIN_ROWS = 15;
	private static readonly CALC_FIT_RETRY_MS = 100;
	private static readonly CALC_FIT_MAX_DEFERS = 24;

	private static active = false;
	private static resizeDebounceTimer: number | null = null;
	private static boundOnViewportChange: (() => void) | null = null;
	private static calcFitTimer: number | null = null;
	private static calcViewCaptured = false;
	private static calcFitApplied = false;
	private static lastFrameW = 0;
	private static lastFrameH = 0;
	private static savedCalcView: { zoom: number; x: number; y: number } | null =
		null;

	private static viewportInnerSize(): { iw: number; ih: number } {
		const vv = window.visualViewport;
		const iw = Math.round(
			vv?.width ||
				window.innerWidth ||
				document.documentElement.clientWidth ||
				800,
		);
		const ih = Math.round(
			vv?.height ||
				window.innerHeight ||
				document.documentElement.clientHeight ||
				600,
		);
		return { iw, ih };
	}

	private static isNarrowViewport(iw: number): boolean {
		return iw < MobilePhonePreview.NARROW_VIEWPORT_MAX;
	}

	private static computeFrame(
		iw: number,
		ih: number,
		pad: number,
	): { left: number; top: number; frameW: number; frameH: number } {
		const maxW = Math.max(120, iw - pad * 2);
		const maxH = Math.max(160, ih - pad * 2);
		if (MobilePhonePreview.isNarrowViewport(iw)) {
			return {
				left: 0,
				top: 0,
				frameW: Math.max(120, iw),
				frameH: Math.max(160, ih),
			};
		}
		const aspect = MobilePhonePreview.PHONE_ASPECT;
		let frameH = maxH;
		let frameW = frameH * aspect;
		if (frameW > maxW) {
			frameW = maxW;
			frameH = frameW / aspect;
		}
		const left = Math.max(0, (iw - frameW) / 2);
		const top = Math.max(0, (ih - frameH) / 2);
		return { left, top, frameW, frameH };
	}

	/** @returns true when the preview frame size changed. */
	private static applyFrameLayout(tag: string): boolean {
		const pad = 8;
		const { iw, ih } = MobilePhonePreview.viewportInnerSize();
		const { left, top, frameW, frameH } = MobilePhonePreview.computeFrame(
			iw,
			ih,
			pad,
		);
		document.body.classList.toggle(
			'mobile-phone-preview-narrow',
			MobilePhonePreview.isNarrowViewport(iw),
		);

		const mc = document.getElementById('main-document-content');
		if (!mc) {
			console.error('mobile-phone-preview: no #main-document-content');
			return false;
		}
		const frameUnchanged =
			MobilePhonePreview.lastFrameW === frameW &&
			MobilePhonePreview.lastFrameH === frameH;
		MobilePhonePreview.lastFrameW = frameW;
		MobilePhonePreview.lastFrameH = frameH;
		mc.style.setProperty('left', left + 'px', 'important');
		mc.style.setProperty('top', top + 'px', 'important');
		mc.style.setProperty('width', frameW + 'px', 'important');
		mc.style.setProperty('height', frameH + 'px', 'important');
		mc.style.setProperty('position', 'fixed', 'important');
		mc.style.setProperty('z-index', '10000', 'important');

		console.log(
			'mobile-phone-preview ' +
				tag +
				' left=' +
				left +
				' top=' +
				top +
				' frame=' +
				frameW +
				'x' +
				frameH +
				' inner=' +
				iw +
				'x' +
				ih +
				' narrow=' +
				MobilePhonePreview.isNarrowViewport(iw),
		);

		if (
			!frameUnchanged &&
			app?.map &&
			typeof app.map.invalidateSize === 'function'
		) {
			app.map.invalidateSize(false);
		}
		const docLayer = app?.map?._docLayer;
		const docType =
			app?.map && typeof app.map.getDocType === 'function'
				? app.map.getDocType()
				: '';
		document.body.classList.toggle(
			'mobile-phone-preview-spreadsheet',
			docType === 'spreadsheet',
		);
		if (
			docLayer &&
			docType !== 'spreadsheet' &&
			typeof docLayer._fitWidthZoom === 'function'
		) {
			docLayer._fitWidthZoom(null, null, true);
		}
		return !frameUnchanged;
	}

	private static revealCalcSheet(): void {
		document.body.classList.remove('mobile-phone-preview-pending');
	}

	private static ensurePreviewStyles(): void {
		let styleEl = document.getElementById(MobilePhonePreview.STYLE_ID);
		if (!styleEl) {
			styleEl = document.createElement('style');
			styleEl.id = MobilePhonePreview.STYLE_ID;
			document.head.appendChild(styleEl);
		}
		styleEl.textContent = MobilePhonePreview.PREVIEW_STYLE_CSS;
	}

	private static attachViewportListeners(): void {
		if (MobilePhonePreview.boundOnViewportChange) {
			return;
		}
		MobilePhonePreview.boundOnViewportChange = () => {
			if (!MobilePhonePreview.active) {
				return;
			}
			if (MobilePhonePreview.resizeDebounceTimer !== null) {
				window.clearTimeout(MobilePhonePreview.resizeDebounceTimer);
			}
			MobilePhonePreview.resizeDebounceTimer = window.setTimeout(() => {
				MobilePhonePreview.resizeDebounceTimer = null;
				if (!MobilePhonePreview.active) {
					return;
				}
				const frameChanged = MobilePhonePreview.applyFrameLayout('resize');
				if (MobilePhonePreview.isSpreadsheet()) {
					if (frameChanged || !MobilePhonePreview.calcFitApplied) {
						MobilePhonePreview.applyCalcFit('resize', 0);
					}
				} else {
					MobilePhonePreview.scheduleTileRecover('resize');
				}
			}, 120);
		};
		window.addEventListener(
			'resize',
			MobilePhonePreview.boundOnViewportChange,
		);
		window.visualViewport?.addEventListener(
			'resize',
			MobilePhonePreview.boundOnViewportChange,
		);
		// visualViewport scroll is not a frame resize. On iOS a sheet pan can
		// move it, and re-fitting then would reload tiles mid-gesture.
	}

	private static detachViewportListeners(): void {
		if (MobilePhonePreview.resizeDebounceTimer !== null) {
			window.clearTimeout(MobilePhonePreview.resizeDebounceTimer);
			MobilePhonePreview.resizeDebounceTimer = null;
		}
		if (!MobilePhonePreview.boundOnViewportChange) {
			return;
		}
		window.removeEventListener(
			'resize',
			MobilePhonePreview.boundOnViewportChange,
		);
		window.visualViewport?.removeEventListener(
			'resize',
			MobilePhonePreview.boundOnViewportChange,
		);
		MobilePhonePreview.boundOnViewportChange = null;
	}

	static show(): void {
		try {
			const opening = !MobilePhonePreview.active;
			MobilePhonePreview.active = true;
			if (opening) {
				MobilePhonePreview.captureCalcView();
				if (MobilePhonePreview.isSpreadsheet()) {
					// Hide the editing zoom until fit runs in this same turn.
					document.body.classList.add('mobile-phone-preview-pending');
				}
			}
			document.body.classList.add('mobile-phone-preview-active');
			MobilePhonePreview.ensurePreviewStyles();
			MobilePhonePreview.attachViewportListeners();
			MobilePhonePreview.applyFrameLayout('show');
			if (MobilePhonePreview.isSpreadsheet()) {
				MobilePhonePreview.applyCalcFit('show', 0);
			} else {
				MobilePhonePreview.scheduleTileRecover('show');
			}
		} catch (e) {
			console.error('mobile-phone-preview show', e);
		}
	}

	/** Re-apply frame after keyboard / native chrome settles (Android relayout). */
	static relayout(): void {
		if (!MobilePhonePreview.active) {
			return;
		}
		try {
			const frameChanged = MobilePhonePreview.applyFrameLayout('relayout');
			if (MobilePhonePreview.isSpreadsheet()) {
				if (frameChanged || !MobilePhonePreview.calcFitApplied) {
					MobilePhonePreview.applyCalcFit('relayout', 0);
				}
			} else {
				MobilePhonePreview.scheduleTileRecover('relayout');
			}
		} catch (e) {
			console.error('mobile-phone-preview relayout', e);
		}
	}

	static hide(): void {
		if (!MobilePhonePreview.active) {
			return;
		}
		MobilePhonePreview.active = false;
		MobilePhonePreview.calcFitApplied = false;
		MobilePhonePreview.lastFrameW = 0;
		MobilePhonePreview.lastFrameH = 0;
		MobilePhonePreview.revealCalcSheet();
		MobilePhonePreview.clearCalcFitTimer();
		MobilePhonePreview.detachViewportListeners();
		document.body.classList.remove('mobile-phone-preview-active');
		document.body.classList.remove('mobile-phone-preview-narrow');
		document.body.classList.remove('mobile-phone-preview-spreadsheet');
		const mc = document.getElementById('main-document-content');
		if (mc) {
			mc.style.removeProperty('left');
			mc.style.removeProperty('top');
			mc.style.removeProperty('width');
			mc.style.removeProperty('height');
			mc.style.removeProperty('position');
			mc.style.removeProperty('z-index');
		}
		const styleEl = document.getElementById(MobilePhonePreview.STYLE_ID);
		if (styleEl) {
			styleEl.remove();
		}
		const fab = document.getElementById('mobile-edit-button');
		if (fab) {
			fab.style.removeProperty('z-index');
			fab.style.removeProperty('pointer-events');
		}
		if (app?.map && typeof app.map.invalidateSize === 'function') {
			app.map.invalidateSize(false);
		}
		MobilePhonePreview.restoreCalcView();
		MobilePhonePreview.scheduleTileRecover('hide');
	}

	private static hasValidMapSize(): boolean {
		const size =
			app?.map && typeof app.map.getSize === 'function'
				? app.map.getSize()
				: null;
		return !!(size && size.x > 0 && size.y > 0);
	}

	private static hasValidPixelBounds(): boolean {
		if (!app?.map || typeof app.map.getPixelBounds !== 'function') {
			return false;
		}
		const bounds = app.map.getPixelBounds();
		if (!bounds?.max || !bounds?.min) {
			return false;
		}
		const width = bounds.max.x - bounds.min.x;
		const height = bounds.max.y - bounds.min.y;
		return width > 0 && height > 0;
	}

	private static applyTileRecover(tag: string, deferCount = 0): void {
		try {
			const docLayer = app?.map?._docLayer;
			if (!docLayer) {
				return;
			}
			if (
				!MobilePhonePreview.hasValidMapSize() ||
				!MobilePhonePreview.hasValidPixelBounds()
			) {
				if (deferCount < 24) {
					window.setTimeout(
						() => MobilePhonePreview.applyTileRecover(tag, deferCount + 1),
						100,
					);
				}
				return;
			}
			if (typeof docLayer._resetClientVisArea === 'function') {
				docLayer._resetClientVisArea();
			}
			if (typeof docLayer._sendClientZoom === 'function') {
				docLayer._sendClientZoom(true);
			}
			if (typeof docLayer._requestNewTiles === 'function') {
				docLayer._requestNewTiles();
			}
			if (typeof TileManager?.update === 'function') {
				TileManager.update();
			}
			if (
				MobilePhonePreview.active &&
				MobilePhonePreview.isSpreadsheet() &&
				app.activeDocument?.activeLayout &&
				typeof app.activeDocument.activeLayout.scrollTo === 'function'
			) {
				app.activeDocument.activeLayout.scrollTo(0, 0);
			}
		} catch (_e) {
			// Best-effort refresh after viewport change.
		}
	}

	private static scheduleTileRecover(tag: string): void {
		if (!app?.map || typeof app.map.invalidateSize !== 'function') {
			return;
		}
		app.map.invalidateSize(false);
		window.setTimeout(() => MobilePhonePreview.applyTileRecover(tag, 0), 180);
		window.setTimeout(() => MobilePhonePreview.applyTileRecover(tag, 0), 520);
		window.setTimeout(() => MobilePhonePreview.applyTileRecover(tag, 0), 980);
	}

	private static isSpreadsheet(): boolean {
		return (
			!!app?.map &&
			typeof app.map.getDocType === 'function' &&
			app.map.getDocType() === 'spreadsheet'
		);
	}

	private static clearCalcFitTimer(): void {
		if (MobilePhonePreview.calcFitTimer !== null) {
			window.clearTimeout(MobilePhonePreview.calcFitTimer);
			MobilePhonePreview.calcFitTimer = null;
		}
	}

	/** First show of a preview session only. relayout must not overwrite this. */
	private static captureCalcView(): void {
		if (
			MobilePhonePreview.calcViewCaptured ||
			!MobilePhonePreview.isSpreadsheet()
		) {
			return;
		}
		const map = app.map;
		const rect = app.activeDocument?.activeLayout?.viewedRectangle;
		if (!map || typeof map.getZoom !== 'function' || !rect) {
			return;
		}
		const zoom = map.getZoom();
		if (
			typeof zoom !== 'number' ||
			!Number.isFinite(zoom) ||
			typeof rect.x1 !== 'number' ||
			typeof rect.y1 !== 'number'
		) {
			return;
		}
		MobilePhonePreview.savedCalcView = { zoom: zoom, x: rect.x1, y: rect.y1 };
		MobilePhonePreview.calcViewCaptured = true;
	}

	private static restoreCalcView(): void {
		const saved = MobilePhonePreview.savedCalcView;
		MobilePhonePreview.savedCalcView = null;
		MobilePhonePreview.calcViewCaptured = false;
		if (!saved || !MobilePhonePreview.isSpreadsheet()) {
			return;
		}
		const map = app.map;
		const layout = app.activeDocument?.activeLayout;
		if (!map || typeof map.setZoom !== 'function') {
			return;
		}
		map.setZoom(saved.zoom, { animate: false });
		if (layout && typeof layout.scrollTo === 'function') {
			const origin = new cool.SimplePoint(saved.x, saved.y);
			layout.scrollTo(origin.pX, origin.pY);
		}
	}

	private static headerCssExtent(): { width: number; height: number } {
		const dpi = app.dpiScale > 0 ? app.dpiScale : 1;
		const container = app.sectionContainer;
		let width = 0;
		let height = 0;
		if (
			container &&
			app.CSections?.RowHeader &&
			typeof container.doesSectionExist === 'function' &&
			container.doesSectionExist(app.CSections.RowHeader.name)
		) {
			const section = container.getSectionWithName(app.CSections.RowHeader.name);
			if (section?.size?.[0] > 0) {
				width = section.size[0] / dpi;
			}
		}
		if (
			container &&
			app.CSections?.ColumnHeader &&
			typeof container.doesSectionExist === 'function' &&
			container.doesSectionExist(app.CSections.ColumnHeader.name)
		) {
			const section = container.getSectionWithName(
				app.CSections.ColumnHeader.name,
			);
			if (section?.size?.[1] > 0) {
				height = section.size[1] / dpi;
			}
		}
		return { width: width, height: height };
	}

	/** Used-range end in current-zoom CSS pixels. Null until sheet geometry is ready. */
	private static calcUsedRangeCss(): { width: number; height: number } | null {
		const docLayer = app?.map?._docLayer;
		const geometry = docLayer?.sheetGeometry;
		if (!docLayer || !geometry) {
			return null;
		}
		const lastColumn = Math.floor(Number(docLayer._lastColumn));
		const lastRow = Math.floor(Number(docLayer._lastRow));
		if (!Number.isFinite(lastColumn) || !Number.isFinite(lastRow)) {
			return null;
		}
		const colIndex = Math.max(
			MobilePhonePreview.CALC_FIT_MIN_COLUMNS - 1,
			lastColumn,
		);
		const rowIndex = Math.max(
			MobilePhonePreview.CALC_FIT_MIN_ROWS - 1,
			lastRow,
		);
		const columns =
			typeof geometry.getColumnsGeometry === 'function'
				? geometry.getColumnsGeometry()
				: null;
		const rows =
			typeof geometry.getRowsGeometry === 'function'
				? geometry.getRowsGeometry()
				: null;
		if (
			!columns ||
			!rows ||
			typeof columns.getElementDataAny !== 'function' ||
			typeof rows.getElementDataAny !== 'function'
		) {
			return null;
		}
		const col = columns.getElementDataAny(colIndex, 'tiletwips');
		const row = rows.getElementDataAny(rowIndex, 'tiletwips');
		if (!col || !row || !(col.size > 0) || !(row.size > 0)) {
			return null;
		}
		const end = new cool.SimplePoint(
			col.startpos + col.size,
			row.startpos + row.size,
		);
		if (typeof docLayer._twipsToPixels === 'function') {
			const px = docLayer._twipsToPixels(end);
			if (px && px.x > 0 && px.y > 0) {
				return { width: px.x, height: px.y };
			}
		}
		if (end.cX > 0 && end.cY > 0) {
			return { width: end.cX, height: end.cY };
		}
		return null;
	}

	private static applyCalcFit(tag: string, deferCount: number): void {
		if (!MobilePhonePreview.active || !MobilePhonePreview.isSpreadsheet()) {
			return;
		}
		const retry = (): void => {
			if (deferCount >= MobilePhonePreview.CALC_FIT_MAX_DEFERS) {
				console.warn(
					'mobile-phone-preview calc-fit skipped tag=' + tag,
				);
				MobilePhonePreview.revealCalcSheet();
				return;
			}
			MobilePhonePreview.calcFitTimer = window.setTimeout(() => {
				MobilePhonePreview.calcFitTimer = null;
				MobilePhonePreview.applyCalcFit(tag, deferCount + 1);
			}, MobilePhonePreview.CALC_FIT_RETRY_MS);
		};
		if (!MobilePhonePreview.calcViewCaptured) {
			MobilePhonePreview.captureCalcView();
		}
		if (
			!MobilePhonePreview.calcViewCaptured ||
			!MobilePhonePreview.hasValidMapSize()
		) {
			retry();
			return;
		}
		const frame = document.getElementById('main-document-content');
		if (!frame || frame.clientWidth <= 0 || frame.clientHeight <= 0) {
			retry();
			return;
		}
		const extent = MobilePhonePreview.calcUsedRangeCss();
		const headers = MobilePhonePreview.headerCssExtent();
		const availW = frame.clientWidth - headers.width;
		const availH = frame.clientHeight - headers.height;
		if (
			!extent ||
			availW <= 1 ||
			availH <= 1 ||
			extent.width <= 1 ||
			extent.height <= 1
		) {
			retry();
			return;
		}
		const mapSize = app.map.getSize();
		const slop = 20;
		// Headers sit inside the map, so the map should match the frame,
		// not the cell area left after subtracting them.
		const mapMatchesFrame =
			Math.abs(mapSize.x - frame.clientWidth) <= slop &&
			Math.abs(mapSize.y - frame.clientHeight) <= slop;
		if (
			!mapMatchesFrame &&
			deferCount < MobilePhonePreview.CALC_FIT_MAX_DEFERS
		) {
			retry();
			return;
		}
		const ratio = Math.min(availW / extent.width, availH / extent.height);
		if (!Number.isFinite(ratio) || ratio <= 0) {
			retry();
			return;
		}
		const map = app.map;
		let zoom = map.getScaleZoom(ratio);
		if (typeof zoom !== 'number' || !Number.isFinite(zoom)) {
			retry();
			return;
		}
		// Tiles are laid out at Math.round(zoom), while pan uses the exact zoom.
		// A fractional fit stays aligned at the origin and drifts as soon as the
		// sheet is panned, so the unfrozen pane (right of a column freeze) draws
		// later columns on top of each other. Floor stays on a tile zoom and
		// still keeps the used range inside the frame.
		zoom = Math.floor(zoom);
		if (
			MobilePhonePreview.calcFitApplied &&
			Math.floor(map.getZoom()) === zoom
		) {
			MobilePhonePreview.revealCalcSheet();
			return;
		}
		map.setZoom(zoom, { animate: false });
		MobilePhonePreview.calcFitApplied = true;
		const layout = app.activeDocument?.activeLayout;
		if (layout && typeof layout.scrollTo === 'function') {
			layout.scrollTo(0, 0);
		}
		console.log(
			'mobile-phone-preview calc-fit tag=' +
				tag +
				' zoom=' +
				zoom +
				' avail=' +
				Math.round(availW) +
				'x' +
				Math.round(availH) +
				' data=' +
				Math.round(extent.width) +
				'x' +
				Math.round(extent.height),
		);
		MobilePhonePreview.revealCalcSheet();
		MobilePhonePreview.applyTileRecover(tag, 0);
	}
}

(window as any).MobilePhonePreview = MobilePhonePreview;
