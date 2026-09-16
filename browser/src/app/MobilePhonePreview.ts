/*
 * Android tablet phone preview — see MobilePhonePreviewController inline JS for layout.
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
		'body.mobile-phone-preview-active #map,' +
		'body.mobile-phone-preview-active #map .leaflet-container,' +
		'body.mobile-phone-preview-active #map .leaflet-map-pane,' +
		'body.mobile-phone-preview-active #map .leaflet-tile-pane,' +
		'body.mobile-phone-preview-active #map .leaflet-canvas-container{' +
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
		'border-radius:0!important;box-shadow:none!important;}';

	private static active = false;
	private static resizeDebounceTimer: number | null = null;
	private static boundOnViewportChange: (() => void) | null = null;

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

	private static applyFrameLayout(tag: string): void {
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
			return;
		}
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

		if (app?.map && typeof app.map.invalidateSize === 'function') {
			app.map.invalidateSize(false);
		}
		const docLayer = app?.map?._docLayer;
		if (docLayer && typeof docLayer._fitWidthZoom === 'function') {
			docLayer._fitWidthZoom(null, null, true);
		}
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
				MobilePhonePreview.applyFrameLayout('resize');
				MobilePhonePreview.scheduleTileRecover('resize');
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
		window.visualViewport?.addEventListener(
			'scroll',
			MobilePhonePreview.boundOnViewportChange,
		);
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
		window.visualViewport?.removeEventListener(
			'scroll',
			MobilePhonePreview.boundOnViewportChange,
		);
		MobilePhonePreview.boundOnViewportChange = null;
	}

	static show(): void {
		try {
			MobilePhonePreview.active = true;
			document.body.classList.add('mobile-phone-preview-active');
			MobilePhonePreview.ensurePreviewStyles();
			MobilePhonePreview.attachViewportListeners();
			MobilePhonePreview.applyFrameLayout('show');
			MobilePhonePreview.scheduleTileRecover('show');
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
			MobilePhonePreview.applyFrameLayout('relayout');
			MobilePhonePreview.scheduleTileRecover('relayout');
		} catch (e) {
			console.error('mobile-phone-preview relayout', e);
		}
	}

	static hide(): void {
		if (!MobilePhonePreview.active) {
			return;
		}
		MobilePhonePreview.active = false;
		MobilePhonePreview.detachViewportListeners();
		document.body.classList.remove('mobile-phone-preview-active');
		document.body.classList.remove('mobile-phone-preview-narrow');
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
}

(window as any).MobilePhonePreview = MobilePhonePreview;
