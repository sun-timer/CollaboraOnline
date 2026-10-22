/*
 * iOS fabric selection events.
 *
 * The AndroidSelectionMenu gesture state machine produces text selections
 * on both platforms. This module turns an iOS selection into a DOM event
 * that the native-feeling HTML selection menu (ticket 09) renders; Android
 * keeps its native popup channel and never consumes these events.
 */

interface FabricSelectionAnchor {
	anchorX: number;
	anchorY: number;
	anchorBottomY: number;
}

type MobileSelectionMenuMode = 'text' | 'calc' | 'graphic';

interface FabricSelectionShowPayload extends FabricSelectionAnchor {
	platform: 'ios';
	text: string;
	hasSelection?: boolean;
	mode?: MobileSelectionMenuMode;
}

class MobileSelectionEvents {
	static readonly SHOW_EVENT = 'fabric:selectionmenu:show';
	static readonly HIDE_EVENT = 'fabric:selectionmenu:hide';
	static readonly MAX_TEXT_LENGTH = 2000;

	/**
	 * Text-selection viewport rects (v-coordinates, physical px) + canvas
	 * offset + dpi scale → CSS anchor for the DOM menu. Same math as
	 * AndroidSelectionMenu.tryShow() so both platforms anchor identically.
	 */
	static computeAnchorCss(args: {
		topViewY: number;
		leftViewX: number;
		rightViewX: number;
		bottomViewY: number;
		canvasX: number;
		canvasY: number;
		scale: number;
	}): FabricSelectionAnchor | null {
		if (!args || args.scale <= 0) {
			return null;
		}
		const centerViewX = (args.leftViewX + args.rightViewX) / 2;
		return {
			anchorX: Math.round(centerViewX / args.scale) + args.canvasX,
			anchorY: Math.round(args.topViewY / args.scale) + args.canvasY,
			anchorBottomY: Math.round(args.bottomViewY / args.scale) + args.canvasY,
		};
	}

	static sanitizeText(text: string, max = MobileSelectionEvents.MAX_TEXT_LENGTH): string {
		return (text || '').slice(0, max);
	}

	static broadcastShow(
		anchor: FabricSelectionAnchor,
		text: string,
		mode: MobileSelectionMenuMode = 'text',
		hasSelection = true,
	): void {
		if (typeof window === 'undefined') {
			return;
		}
		const payload: FabricSelectionShowPayload = {
			platform: 'ios',
			anchorX: anchor.anchorX,
			anchorY: anchor.anchorY,
			anchorBottomY: anchor.anchorBottomY,
			text: MobileSelectionEvents.sanitizeText(text),
			hasSelection,
			mode,
		};
		window.dispatchEvent(
			new CustomEvent(MobileSelectionEvents.SHOW_EVENT, { detail: payload }),
		);
	}

	static broadcastHide(): void {
		if (typeof window !== 'undefined') {
			window.dispatchEvent(new CustomEvent(MobileSelectionEvents.HIDE_EVENT));
		}
	}
}

/** A small, gesture-scoped mirror of the document canvas for iOS Writer. */
class MobileSelectionMagnifier {
	static readonly WIDTH = 112;
	static readonly HEIGHT = 88;
	static readonly CONTENT_WIDTH = 108;
	static readonly CONTENT_HEIGHT = 84;
	static readonly MAGNIFICATION = 1.6;
	static readonly FRAME_INTERVAL_MS = 1000 / 30;
	private static root: HTMLDivElement | null = null;
	private static canvas: HTMLCanvasElement | null = null;
	private static context: CanvasRenderingContext2D | null = null;
	private static active = false;
	private static frameId = 0;
	private static lastDrawAt = 0;
	private static lastPoint: { x: number; y: number } | null = null;
	private static installed = false;
	private static lifecycleMap: any = null;
	private static sourceCanvas: HTMLCanvasElement | null = null;
	private static sourceDocLayer: any = null;

	static isEnabled(): boolean {
		return !!(
			typeof window !== 'undefined' &&
			typeof app !== 'undefined' &&
			app.map &&
			app.map.getDocType &&
			app.map.getDocType() === 'text' &&
			(window as any).ThisIsTheiOSApp &&
			app.map._docLayer
		);
	}

	/** Viewport CSS point -> source canvas-buffer crop, clipped at canvas edges. */
	static calculateSourceCrop(args: {
		clientX: number;
		clientY: number;
		left: number;
		top: number;
		cssWidth: number;
		cssHeight: number;
		bufferWidth: number;
		bufferHeight: number;
		lensWidth?: number;
		lensHeight?: number;
		magnification?: number;
		outputScale?: number;
	}): { sx: number; sy: number; sw: number; sh: number; dx: number; dy: number; dw: number; dh: number } | null {
		if (
			!args || args.cssWidth <= 0 || args.cssHeight <= 0 ||
			args.bufferWidth <= 0 || args.bufferHeight <= 0
		) {
			return null;
		}
		const lensWidth = args.lensWidth || MobileSelectionMagnifier.WIDTH;
		const lensHeight = args.lensHeight || MobileSelectionMagnifier.HEIGHT;
		const magnification = args.magnification || MobileSelectionMagnifier.MAGNIFICATION;
		const scaleX = args.bufferWidth / args.cssWidth;
		const scaleY = args.bufferHeight / args.cssHeight;
		const idealWidth = Math.min(args.bufferWidth, lensWidth / magnification * scaleX);
		const idealHeight = Math.min(args.bufferHeight, lensHeight / magnification * scaleY);
		const centerX = (args.clientX - args.left) * scaleX;
		const centerY = (args.clientY - args.top) * scaleY;
		const idealX = centerX - idealWidth / 2;
		const idealY = centerY - idealHeight / 2;
		const sx = Math.max(0, idealX);
		const sy = Math.max(0, idealY);
		const right = Math.min(args.bufferWidth, idealX + idealWidth);
		const bottom = Math.min(args.bufferHeight, idealY + idealHeight);
		const sw = Math.max(0, right - sx);
		const sh = Math.max(0, bottom - sy);
		if (!sw || !sh) {
			return null;
		}
		const outputScale = args.outputScale || 1;
		return {
			sx,
			sy,
			sw,
			sh,
			dx: (sx - idealX) / scaleX * magnification * outputScale,
			dy: (sy - idealY) / scaleY * magnification * outputScale,
			dw: sw / scaleX * magnification * outputScale,
			dh: sh / scaleY * magnification * outputScale,
		};
	}

	static install(): void {
		if (MobileSelectionMagnifier.installed || typeof document === 'undefined') {
			return;
		}
		MobileSelectionMagnifier.installed = true;
		document.addEventListener('visibilitychange', MobileSelectionMagnifier.onVisibilityChange);
		document.addEventListener('touchstart', MobileSelectionMagnifier.onTouchStart, true);
		document.addEventListener('touchend', MobileSelectionMagnifier.hide, true);
		document.addEventListener('touchcancel', MobileSelectionMagnifier.hide, true);
		window.visualViewport?.addEventListener('resize', MobileSelectionMagnifier.onVisualViewport);
		window.visualViewport?.addEventListener('scroll', MobileSelectionMagnifier.onVisualViewport);
		MobileSelectionMagnifier.bindDocumentLifecycle();
	}

	static startAt(clientX: number, clientY: number): void {
		if (!MobileSelectionMagnifier.isEnabled() || !Number.isFinite(clientX) || !Number.isFinite(clientY)) {
			MobileSelectionMagnifier.hide();
			return;
		}
		MobileSelectionMagnifier.install();
		MobileSelectionMagnifier.bindDocumentLifecycle();
		const source = document.getElementById('document-canvas');
		if (!(source instanceof HTMLCanvasElement) || !source.width || !source.height) {
			MobileSelectionMagnifier.hide();
			return;
		}
		MobileSelectionMagnifier.sourceCanvas = source;
		MobileSelectionMagnifier.sourceDocLayer = app.map._docLayer;
		MobileSelectionMagnifier.ensureOverlay();
		if (!MobileSelectionMagnifier.root || !MobileSelectionMagnifier.context) {
			return;
		}
		MobileSelectionMagnifier.active = true;
		MobileSelectionMagnifier.lastDrawAt = Number.NEGATIVE_INFINITY;
		MobileSelectionMagnifier.root.style.display = 'block';
		MobileSelectionMagnifier.updateAt(clientX, clientY);
	}

	static updateAt(clientX: number, clientY: number): void {
		if (!MobileSelectionMagnifier.active || !MobileSelectionMagnifier.root) {
			return;
		}
		MobileSelectionMagnifier.lastPoint = { x: clientX, y: clientY };
		const viewportWidth = window.visualViewport?.width || window.innerWidth;
		const viewportHeight = window.visualViewport?.height || window.innerHeight;
		const offsetLeft = window.visualViewport?.offsetLeft || 0;
		const offsetTop = window.visualViewport?.offsetTop || 0;
		const left = Math.max(offsetLeft + 4, Math.min(offsetLeft + viewportWidth - MobileSelectionMagnifier.WIDTH - 4, clientX - MobileSelectionMagnifier.WIDTH / 2));
		const top = Math.max(offsetTop + 4, Math.min(offsetTop + viewportHeight - MobileSelectionMagnifier.HEIGHT - 4, clientY - MobileSelectionMagnifier.HEIGHT - 14));
		MobileSelectionMagnifier.root.style.left = Math.round(left) + 'px';
		MobileSelectionMagnifier.root.style.top = Math.round(top) + 'px';
		MobileSelectionMagnifier.scheduleDraw();
	}

	static hide(): void {
		MobileSelectionMagnifier.active = false;
		MobileSelectionMagnifier.lastPoint = null;
		MobileSelectionMagnifier.sourceCanvas = null;
		MobileSelectionMagnifier.sourceDocLayer = null;
		if (MobileSelectionMagnifier.frameId) {
			window.cancelAnimationFrame(MobileSelectionMagnifier.frameId);
			MobileSelectionMagnifier.frameId = 0;
		}
		if (MobileSelectionMagnifier.root) {
			MobileSelectionMagnifier.root.style.display = 'none';
		}
	}

	private static onVisibilityChange = (): void => {
		if (document.visibilityState !== 'visible') {
			MobileSelectionMagnifier.hide();
		}
	};

	private static onTouchStart = (event: TouchEvent): void => {
		if (event.touches.length > 1) {
			MobileSelectionMagnifier.hide();
		}
	};

	private static onVisualViewport = (): void => {
		if (MobileSelectionMagnifier.active && MobileSelectionMagnifier.lastPoint) {
			MobileSelectionMagnifier.updateAt(
				MobileSelectionMagnifier.lastPoint.x,
				MobileSelectionMagnifier.lastPoint.y,
			);
		}
	};

	private static bindDocumentLifecycle(): void {
		const map = typeof app !== 'undefined' ? app.map : null;
		if (!map || map === MobileSelectionMagnifier.lifecycleMap || typeof map.on !== 'function') {
			return;
		}
		MobileSelectionMagnifier.lifecycleMap = map;
		map.on('docloaded', MobileSelectionMagnifier.hide);
		map.on('closeapp', MobileSelectionMagnifier.hide);
	}

	private static ensureOverlay(): void {
		if (MobileSelectionMagnifier.root) {
			return;
		}
		const dpr = Math.max(1, window.devicePixelRatio || 1);
		const root = document.createElement('div');
		root.setAttribute('aria-hidden', 'true');
		root.style.cssText = 'position:fixed;display:none;pointer-events:none;z-index:2147483000;width:112px;height:88px;border:2px solid #1683f2;border-radius:50%;overflow:hidden;box-sizing:border-box;background:#fff;box-shadow:0 2px 8px rgba(0,0,0,.22);';
		const canvas = document.createElement('canvas');
		canvas.width = Math.round(MobileSelectionMagnifier.CONTENT_WIDTH * dpr);
		canvas.height = Math.round(MobileSelectionMagnifier.CONTENT_HEIGHT * dpr);
		canvas.style.cssText = 'display:block;width:100%;height:100%;';
		root.appendChild(canvas);
		document.body.appendChild(root);
		MobileSelectionMagnifier.root = root;
		MobileSelectionMagnifier.canvas = canvas;
		MobileSelectionMagnifier.context = canvas.getContext('2d');
	}

	private static scheduleDraw(): void {
		if (MobileSelectionMagnifier.frameId || !MobileSelectionMagnifier.active) {
			return;
		}
		MobileSelectionMagnifier.frameId = window.requestAnimationFrame((now) => {
			MobileSelectionMagnifier.frameId = 0;
			if (!MobileSelectionMagnifier.active || !MobileSelectionMagnifier.lastPoint) {
				return;
			}
			if (now - MobileSelectionMagnifier.lastDrawAt >= MobileSelectionMagnifier.FRAME_INTERVAL_MS) {
				MobileSelectionMagnifier.draw();
				MobileSelectionMagnifier.lastDrawAt = now;
			}
			MobileSelectionMagnifier.scheduleDraw();
		});
	}

	private static draw(): void {
		const source = document.getElementById('document-canvas');
		const point = MobileSelectionMagnifier.lastPoint;
		const canvas = MobileSelectionMagnifier.canvas;
		const context = MobileSelectionMagnifier.context;
		if (
			!(source instanceof HTMLCanvasElement) ||
			source !== MobileSelectionMagnifier.sourceCanvas ||
			!source.isConnected ||
			!MobileSelectionMagnifier.isEnabled() ||
			app.map._docLayer !== MobileSelectionMagnifier.sourceDocLayer ||
			!point || !canvas || !context
		) {
			MobileSelectionMagnifier.hide();
			return;
		}
		const rect = source.getBoundingClientRect();
		const crop = MobileSelectionMagnifier.calculateSourceCrop({
			clientX: point.x,
			clientY: point.y,
			left: rect.left,
			top: rect.top,
			cssWidth: rect.width,
			cssHeight: rect.height,
			bufferWidth: source.width,
			bufferHeight: source.height,
			lensWidth: MobileSelectionMagnifier.CONTENT_WIDTH,
			lensHeight: MobileSelectionMagnifier.CONTENT_HEIGHT,
			outputScale: canvas.width / MobileSelectionMagnifier.CONTENT_WIDTH,
		});
		if (!crop) {
			MobileSelectionMagnifier.hide();
			return;
		}
		context.clearRect(0, 0, canvas.width, canvas.height);
		try {
			context.fillStyle = '#fff';
			context.fillRect(0, 0, canvas.width, canvas.height);
			context.drawImage(source, crop.sx, crop.sy, crop.sw, crop.sh, crop.dx, crop.dy, crop.dw, crop.dh);
		} catch (_) {
			MobileSelectionMagnifier.hide();
		}
	}
}

if (typeof window !== 'undefined') {
	(window as any).MobileSelectionEvents = MobileSelectionEvents;
	(window as any).MobileSelectionMagnifier = MobileSelectionMagnifier;
}
