/*
 * Android tablet phone preview — see MobilePhonePreviewController inline JS for layout.
 */

class MobilePhonePreview {
	private static readonly STYLE_ID = 'mobile-phone-preview-style';
	private static active = false;

	static show(): void {
		MobilePhonePreview.active = true;
		document.body.classList.add('mobile-phone-preview-active');
		MobilePhonePreview.scheduleTileRecover('show');
	}

	static hide(): void {
		if (!MobilePhonePreview.active) {
			return;
		}
		MobilePhonePreview.active = false;
		document.body.classList.remove('mobile-phone-preview-active');
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
