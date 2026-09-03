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

interface FabricSelectionShowPayload extends FabricSelectionAnchor {
	platform: 'ios';
	text: string;
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

	static broadcastShow(anchor: FabricSelectionAnchor, text: string): void {
		if (typeof window === 'undefined') {
			return;
		}
		const payload: FabricSelectionShowPayload = {
			platform: 'ios',
			anchorX: anchor.anchorX,
			anchorY: anchor.anchorY,
			anchorBottomY: anchor.anchorBottomY,
			text: MobileSelectionEvents.sanitizeText(text),
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

if (typeof window !== 'undefined') {
	(window as any).MobileSelectionEvents = MobileSelectionEvents;
}