/*
 * Per-document-type accent tokens for iOS mobile chrome (Writer / Calc / Impress).
 * Mirrors Android tab active colors and primary buttons.
 */

type MobileDocThemeKind = 'writer' | 'calc' | 'impress';

class MobileDocTheme {
	static readonly ACCENT: Record<MobileDocThemeKind, string> = {
		writer: '#1278d9',
		calc: '#3b8040',
		impress: '#ec5d1f',
	};

	static kindForDocType(docType: string | undefined | null): MobileDocThemeKind {
		if (docType === 'spreadsheet') {
			return 'calc';
		}
		if (docType === 'presentation' || docType === 'drawing') {
			return 'impress';
		}
		return 'writer';
	}

	/**
	 * @param notifyNative When true, syncs iOS nativeDocumentType (toolbar colors,
	 * function-panel routing). Only on doc load — never on panel mount/open.
	 */
	static apply(
		docType: string | undefined | null,
		options?: { notifyNative?: boolean },
	): void {
		if (typeof document === 'undefined') {
			return;
		}
		const kind = MobileDocTheme.kindForDocType(docType);
		document.documentElement.setAttribute('data-mobile-doc-theme', kind);
		if (options?.notifyNative !== true) {
			return;
		}
		if (
			(window as any).ThisIsTheiOSApp &&
			typeof (window as any).postMobileMessage === 'function'
		) {
			const nativeType =
				docType === 'spreadsheet'
					? 'spreadsheet'
					: docType === 'presentation' || docType === 'drawing'
						? 'presentation'
						: 'text';
			(window as any).postMobileMessage('NATIVEDOCTYPE ' + nativeType);
		}
	}

	static applyFromMap(options?: { notifyNative?: boolean }): void {
		const map = (window as any).app && (window as any).app.map;
		const hasMap = map && typeof map.getDocType === 'function';
		const docType = hasMap ? map.getDocType() : null;
		if (!docType) {
			/* Map not ready: only refresh web CSS; keep native type from file extension. */
			MobileDocTheme.apply('text', { notifyNative: false });
			return;
		}
		MobileDocTheme.apply(docType, {
			notifyNative: options?.notifyNative === true,
		});
	}

	private static syncFromMapDocLoaded(): void {
		try {
			MobileDocTheme.applyFromMap({ notifyNative: true });
		} catch (_e) {
			/* ignore until map/doc layer is ready */
		}
	}

	/** Map is created after this module in bundle.js — attach docloaded when ready. */
	static attachWhenMapReady(): void {
		const map = (window as any).app && (window as any).app.map;
		if (!map || typeof map.on !== 'function') {
			window.setTimeout(() => MobileDocTheme.attachWhenMapReady(), 0);
			return;
		}
		if ((map as any).__coolMobileDocThemeAttached) {
			return;
		}
		(map as any).__coolMobileDocThemeAttached = true;
		map.on('docloaded', () => MobileDocTheme.syncFromMapDocLoaded());
		MobileDocTheme.syncFromMapDocLoaded();
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const start = () => MobileDocTheme.attachWhenMapReady();
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', start, { once: true });
	} else {
		start();
	}
}
