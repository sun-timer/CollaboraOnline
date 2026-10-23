/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

class TextSelections {
	// Below 4 are used for text selection handles, which are visible where mouse pointer doesn't exist (search "pointer: fine" in css).
	private static start: TextSelectionHandle;
	private static end: TextSelectionHandle;
	private static startRectangle: cool.SimpleRectangle | null = null; // This is the rectangle sent from the core side.
	private static endRectangle: cool.SimpleRectangle | null = null; // This is the rectangle sent from the core side.

	private static active: boolean = false; // Is there a text selection or not.
	private static nativeHandleEpoch: number = 0;
	private static nativeHandleSession: string = '';
	private static nativeHandleLifecycleBound: boolean = false;

	public static initiate() {
		try {
			this.nativeHandleSession = new URL(window.location.href).searchParams.get(
				'androidselection_session',
			) || '';
		} catch (_e) {
			this.nativeHandleSession = '';
		}
		this.start = new TextSelectionHandle(
			'selection_start_handle',
			30,
			44,
			new cool.SimplePoint(0, 0),
			'text-selection-handle-start',
			false,
		);
		this.end = new TextSelectionHandle(
			'selection_end_handle',
			30,
			44,
			new cool.SimplePoint(0, 0),
			'text-selection-handle-end',
			false,
		);

		app.sectionContainer.addSection(this.start);
		app.sectionContainer.addSection(this.end);
		if (!this.nativeHandleLifecycleBound && app.map && typeof app.map.on === 'function') {
			this.nativeHandleLifecycleBound = true;
			const resetNativeHandleRects = () => {
				this.nativeHandleEpoch++;
				this.publishAndroidHandleRects(false);
			};
			app.map.on('docloaded', resetNativeHandleRects);
			app.map.on('closeapp', resetNativeHandleRects);
			const refreshNativeHandleRects = () =>
				this.publishAndroidHandleRects(this.isActive());
			window.addEventListener('scroll', refreshNativeHandleRects, { capture: true, passive: true });
			window.addEventListener('resize', refreshNativeHandleRects, { passive: true });
			window.visualViewport?.addEventListener('scroll', refreshNativeHandleRects, { passive: true });
			window.visualViewport?.addEventListener('resize', refreshNativeHandleRects, { passive: true });
		}
		this.publishAndroidHandleRects(false);
	}

	public static deactivate() {
		if (!this.active) return;

		this.active = false;
		this.start.setShowSection(false);
		this.end.setShowSection(false);
		this.publishAndroidHandleRects(false);

		// Schedule a redraw if there's not any.
		app.sectionContainer.requestReDraw();
	}

	public static activate() {
		if (this.active) return;

		this.active = true;

		if (this.startRectangle && this.endRectangle) {
			this.start.setShowSection(true);
			this.end.setShowSection(true);
			this.updateMarkers();
		}
	}

	public static isActive(): boolean {
		return this.active;
	}

	public static showHandles() {
		if (this.start.isSectionShown()) this.start.setOpacity(1);

		if (this.end.isSectionShown()) this.end.setOpacity(1);
		this.publishAndroidHandleRects(this.isActive());
	}

	public static hideHandles() {
		if (this.start.isSectionShown()) this.start.setOpacity(0);

		if (this.end.isSectionShown()) this.end.setOpacity(0);
		this.publishAndroidHandleRects(false);
	}

	public static getStartRectangle(): cool.SimpleRectangle | null {
		return this.startRectangle ? this.startRectangle.clone() : null;
	}

	public static getEndRectangle(): cool.SimpleRectangle | null {
		return this.endRectangle ? this.endRectangle.clone() : null;
	}

	/** Return the current logical endpoint rectangle for a live handle instance. */
	public static getRectangleForHandle(handle: TextSelectionHandle): cool.SimpleRectangle | null {
		if (this.start === handle) return this.startRectangle?.clone() ?? null;
		if (this.end === handle) return this.endRectangle?.clone() ?? null;
		return null;
	}

	public static setStartRectangle(rectangle: cool.SimpleRectangle) {
		this.startRectangle = rectangle;
		this.updateMarkers();
	}

	public static setEndRectangle(rectangle: cool.SimpleRectangle) {
		this.endRectangle = rectangle;
		this.updateMarkers();
	}

	private static updateMarkers() {
		if (!this.isActive()) {
			this.publishAndroidHandleRects(false);
			return;
		}

		if (this.startRectangle === null || this.endRectangle === null) {
			this.publishAndroidHandleRects(false);
			return;
		}

		var startPos = { x: this.startRectangle.pX1, y: this.startRectangle.pY2 };
		var endPos = { x: this.endRectangle.pX1, y: this.endRectangle.pY2 };

		if (app.map._docLayer.isCalcRTL()) {
			Util.ensureValue(app.activeDocument);
			// Mirror position from right to left.
			startPos.x =
				app.activeDocument.activeLayout.viewedRectangle.pX2 -
				(startPos.x - app.activeDocument.activeLayout.viewedRectangle.pX1);
			endPos.x =
				app.activeDocument.activeLayout.viewedRectangle.pX2 -
				(endPos.x - app.activeDocument.activeLayout.viewedRectangle.pX1);
		}

		const oldStart = this.start.getPosition();
		const oldEnd = this.end.getPosition();

		startPos.x -= 30 * app.dpiScale;
		this.start.setPosition(startPos.x, startPos.y);
		const newStart = this.start.getPosition();

		this.end.setPosition(endPos.x, endPos.y);
		const newEnd = this.end.getPosition();

		if (
			app.map._docLayer.isCalcRTL() &&
			(newStart.y < newEnd.y ||
				(newStart.y <= newEnd.y && newStart.x < newEnd.x))
		) {
			// If the start handle is actually closer to the end of the selection, reverse positions (Right To Left case).
			this.start.setPosition(newEnd.pX, newEnd.pY);
			this.end.setPosition(newStart.pX, newStart.pY);
		} else if (
			!app.map._docLayer.isCalcRTL() &&
			(oldEnd.distanceTo(newStart.toArray()) < 20 ||
				oldStart.distanceTo(newEnd.toArray()) < 20)
		) {
			/*
				If the start handle is actually closer to the end of the selection, reverse positions.
				This seems to be a core side issue to me. I think the start and end positions are switched but the handlers aren't on the core side.
			*/
			this.switchStartEndHandles();
		}

		// Schedule a redraw if there's not any.
		app.sectionContainer.requestReDraw();
		this.publishAndroidHandleRects(true);
	}

	private static publishAndroidHandleRects(visible: boolean) {
		if (typeof window === 'undefined' || !(window as any).ThisIsTheAndroidApp) return;
		visible = visible && app.map?.getDocType() === 'text';
		const bridge = (window as any).COOLMessageHandler;
		if (!this.nativeHandleSession || !bridge || typeof bridge.updateWriterSelectionHandleRects !== 'function') return;
		const viewport = window.visualViewport;
		const cssRects = visible
			? [this.start, this.end].map(handle => {
				const element = handle.getHTMLObject();
				// setPosition updates the section model; onDraw normally moves the DOM
				// element later. Native DOWN hit-testing needs the just-updated bounds now.
				handle.adjustHTMLObjectPosition();
				const rect = element.getBoundingClientRect();
				const style = window.getComputedStyle(element);
				if (style.display === 'none' || style.visibility === 'hidden' ||
					Number(style.opacity) === 0 || rect.width <= 0 || rect.height <= 0) {
					return { left: 0, top: 0, right: 0, bottom: 0 };
				}
				return {
					left: rect.left,
					top: rect.top,
					right: rect.right,
					bottom: rect.bottom,
				};
			})
			: [
				{ left: 0, top: 0, right: 0, bottom: 0 },
				{ left: 0, top: 0, right: 0, bottom: 0 },
			];
		try {
			bridge.updateWriterSelectionHandleRects(JSON.stringify({
				session: this.nativeHandleSession,
				epoch: this.nativeHandleEpoch,
				viewport: {
					width: viewport?.width || window.innerWidth,
					height: viewport?.height || window.innerHeight,
					offsetLeft: viewport?.offsetLeft || 0,
					offsetTop: viewport?.offsetTop || 0,
				},
				rects: cssRects,
			}));
		} catch (_e) {
			// The native bridge is best-effort; the DOM remains the source of truth.
		}
	}

	public static switchStartEndHandles() {
		const temp = this.start;
		this.start = this.end;
		this.end = temp;
	}

	public static dispose() {
		this.publishAndroidHandleRects(false);
		app.sectionContainer.removeSection(this.start.name);
		app.sectionContainer.removeSection(this.end.name);
	}
}
