// @ts-strict-ignore
/* global Proxy _ */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

class TextSelectionHandle extends HTMLObjectSection {
	_showSection: boolean = true; // Store the internal show/hide section through forced non-touchscreen hides...
	private lastSelectionUpdateAt: number = 0;
	private readonly selectionUpdateThrottleMs: number = 100;
	private dragSelectionType: 'start' | 'end' | null = null;
	private dragGrabOffsetTwips: { x: number; y: number } | null = null;
	private useClientDragCoordinates = false;

	private eventClientPoint(e: MouseEvent): { x: number; y: number } | null {
		// CanvasSectionContainer forwards a TouchEvent through this MouseEvent slot;
		// use its touch coordinates before falling back to mouse/pointer fields.
		const touchEvent = e as any;
		const touch = touchEvent.touches?.[0] || touchEvent.changedTouches?.[0];
		if (touch && Number.isFinite(touch.clientX) && Number.isFinite(touch.clientY)) {
			return { x: touch.clientX, y: touch.clientY };
		}
		return Number.isFinite(e.clientX) && Number.isFinite(e.clientY)
			? { x: e.clientX, y: e.clientY }
			: null;
	}

	constructor (sectionName: string, objectWidth: number, objectHeight: number, documentPosition: cool.SimplePoint,  extraClass: string = "", showSection: boolean = false) {
		super(sectionName, objectWidth, objectHeight, documentPosition, extraClass, showSection);
	}

	onDrag(point: cool.SimplePoint, e?: MouseEvent) {
		const candidateX = Math.round((this.myTopLeft[0] + point.pX) / app.dpiScale);
		const candidateY = Math.round((this.myTopLeft[1] + point.pY) / app.dpiScale);

		this.sectionProperties.objectDiv.style.left = candidateX + 'px';
		this.sectionProperties.objectDiv.style.top = candidateY + 'px';

		app.map.fire('handleautoscroll', {pos: { x: candidateX, y: candidateY }, map: app.map});
		this.postSelectionUpdate(point, true, e);
	}

	setShowSection(show: boolean) {
		this._showSection = show;

		if (!window.touch.currentlyUsingTouchscreen()) {
			super.setShowSection(false);
		} else {
			super.setShowSection(this._showSection);
		}
	}

	setOpacity(value: number) {
		this.getHTMLObject().style.opacity = value;
	}

	onDragEnd(point: cool.SimplePoint, e?: MouseEvent) {
		const magnifier = (window as any).MobileSelectionMagnifier;
		if (magnifier) magnifier.hide();
		const x = this.position[0] + point.pX;
		const y = this.position[1] + point.pY;

		app.map.fire('scrollvelocity', {vx: 0, vy: 0});
		this.postSelectionUpdate(point, false, e);
		// A touch's section-local point is an absolute handle hit location, not
		// a movement delta. In the iOS Writer path the core reply repositions the
		// handle; do not seed updateMarkers with a fabricated position.
		if (!this.useClientDragCoordinates) {
			this.setPosition(x, y);
		}
	}

	private postSelectionUpdate(point: cool.SimplePoint, throttle: boolean, e?: MouseEvent) {
		if (throttle) {
			if (!window.touch.currentlyUsingTouchscreen()) {
				return;
			}

			const now = Date.now();
			if (now - this.lastSelectionUpdateAt < this.selectionUpdateThrottleMs) {
				return;
			}
			this.lastSelectionUpdateAt = now;
		} else {
			this.lastSelectionUpdateAt = 0;
		}

		let x = this.position[0] + point.pX;
		const y = this.position[1] + point.pY;
		const type = this.dragSelectionType ||
			(this.name === 'selection_start_handle' ? 'start' : 'end');
		if (this.useClientDragCoordinates && e && this.dragGrabOffsetTwips) {
			const clientPoint = this.eventClientPoint(e);
			const mapped = clientPoint && app.sectionContainer.clientViewPointToDocumentTwips(
				clientPoint.x * app.dpiScale,
				clientPoint.y * app.dpiScale,
			);
			if (!mapped) return;
			const documentTwips = TextSelectionHandle.adjustForGrabOffset(
				mapped,
				this.dragGrabOffsetTwips,
			);
			app.map._docLayer._postSelectTextEvent(
				type,
				Math.round(documentTwips.x),
				Math.round(documentTwips.y),
			);
			return;
		}

		if (type === 'start') {
			// updateMarkers places the start marker 30*dpiScale core pixels left
			// of the text boundary. Preserve the Android legacy calculation; on
			// iOS use the client-point path above whenever possible.
			x += window.ThisIsTheiOSApp && app.map.getDocType() === 'text'
				? 30 * app.dpiScale
				: 30 / app.dpiScale;
		}

		if (!app.map._docLayer.isCalcRTL()) {
			app.map._docLayer._postSelectTextEvent(type, Math.round(x * app.pixelsToTwips), Math.round(y * app.pixelsToTwips));
		}
		else {
			const referenceX = app.activeDocument.activeLayout.viewedRectangle.pX1 + (app.activeDocument.activeLayout.viewedRectangle.pX2 - x);
			app.map._docLayer._postSelectTextEvent(type, Math.round(referenceX * app.pixelsToTwips), Math.round(y * app.pixelsToTwips));
		}
	}

	static adjustForGrabOffset(
		mapped: { x: number; y: number },
		grabOffset: { x: number; y: number },
	): { x: number; y: number } {
		return { x: mapped.x - grabOffset.x, y: mapped.y - grabOffset.y };
	}

	onMouseMove(point: cool.SimplePoint, dragDistance: number[], e: MouseEvent): void {
		e.stopPropagation();
		if (this.containerObject.isDraggingSomething()) {
			this.stopPropagating();
			this.onDrag(point, e);
			const magnifier = (window as any).MobileSelectionMagnifier;
			const clientPoint = this.eventClientPoint(e);
			if (magnifier && clientPoint && window.ThisIsTheiOSApp && app.map.getDocType() === 'text') {
				magnifier.updateAt(clientPoint.x, clientPoint.y - 22);
			}
		}
	}

	onDocumentObjectVisibilityChange(): void {
		if (this.showSection && this.isVisible)
			this.sectionProperties.objectDiv.style.display = '';
		else
			this.sectionProperties.objectDiv.style.display = 'none';
	}

	onClick(point: cool.SimplePoint, e: MouseEvent): void {
		e.stopPropagation();
		this.stopPropagating();
	}

	onMouseDown(point: cool.SimplePoint, e: MouseEvent): void {
		e.stopPropagation();
		this.stopPropagating();
		this.lastSelectionUpdateAt = 0;
		this.dragSelectionType = this.name === 'selection_start_handle' ? 'start' : 'end';
		this.dragGrabOffsetTwips = null;
		this.useClientDragCoordinates = false;
		const magnifier = (window as any).MobileSelectionMagnifier;
		const clientPoint = this.eventClientPoint(e);
		const isIOSWriterTouch = e.type === 'touchstart' &&
			window.ThisIsTheiOSApp && app.map.getDocType() === 'text';
		if (isIOSWriterTouch) {
			AndroidSelectionMenu.beginIOSWriterHandleTouch();
			const mouseControl = app.sectionContainer.getSectionWithName(
				app.CSections.MouseControl.name,
			) as MouseControl;
			mouseControl?.beginWriterSelectionHandleTouch();
			const endpoint = TextSelections.getRectangleForHandle(this);
			if (endpoint && clientPoint) {
				const mapped = app.sectionContainer.clientViewPointToDocumentTwips(
					clientPoint.x * app.dpiScale,
					clientPoint.y * app.dpiScale,
				);
				if (mapped) {
					// The marker is positioned at the endpoint's lower edge, while
					// the text boundary is on the row center. Preserve where the
					// finger grabbed the handle so the first move cannot jump rows.
					const anchor = {
						x: endpoint.x1,
						y: (endpoint.y1 + endpoint.y2) / 2,
					};
					this.dragGrabOffsetTwips = {
						x: mapped.x - anchor.x,
						y: mapped.y - anchor.y,
					};
					this.useClientDragCoordinates = true;
				}
			}
		}
		if (magnifier && clientPoint && window.ThisIsTheiOSApp && app.map.getDocType() === 'text') {
			// The grab handle extends below its text boundary; center the lens on the text row.
			magnifier.startAt(clientPoint.x, clientPoint.y - 22);
		}
	}

	onMouseUp(point: cool.SimplePoint, e: MouseEvent): void {
		e.stopPropagation();
		const magnifier = (window as any).MobileSelectionMagnifier;
		if (magnifier && window.ThisIsTheiOSApp && app.map.getDocType() === 'text') {
			magnifier.hide();
		}
		if (this.containerObject.isDraggingSomething()) {
			this.stopPropagating();
			this.onDragEnd(point, e);
		}
		if (window.ThisIsTheiOSApp && app.map.getDocType() === 'text') {
			AndroidSelectionMenu.endIOSWriterHandleTouch();
			const mouseControl = app.sectionContainer.getSectionWithName(
				app.CSections.MouseControl.name,
			) as MouseControl;
			mouseControl?.endWriterSelectionHandleTouch();
		}
		this.dragSelectionType = null;
		this.dragGrabOffsetTwips = null;
		this.useClientDragCoordinates = false;
	}
}
