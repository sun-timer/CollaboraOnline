/* -*- js-indent-level: 8 -*- */

/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

// This is used for showing mobile cursor handler. User can grab and move this section's HTML object to move the text cursor.

class CursorHandler extends HTMLObjectSection {
	public static objectWidth = 30; // cursor-handler CSS width and height.
	public static objectHeight = 44;

	constructor() {
		super(
			app.CSections.CursorHandler.name,
			CursorHandler.objectWidth,
			CursorHandler.objectHeight,
			new cool.SimplePoint(0, 0),
			'cursor-handler',
			false,
		);

		this.sectionProperties.lastPosition = null;
	}

	onMouseDown(point: cool.SimplePoint, e: MouseEvent): void {
		if (e.type === 'touchstart' && MobileSelectionMagnifier.isIOSImpressTextEditing()) {
			this.sectionProperties.lastPosition = null;
		}
	}

	onMouseMove(
		point: cool.SimplePoint,
		dragDistance: Array<number>,
		e: MouseEvent,
	): void {
		if (
			this.containerObject.isDraggingSomething() &&
			this.containerObject.targetSection === this.name
		) {
			this.stopPropagating();
			e.stopPropagation();

			const dragStarted = !this.sectionProperties.lastPosition;
			if (dragStarted)
				this.sectionProperties.lastPosition = this.position.slice();
			else {
				this.setPosition(
					this.sectionProperties.lastPosition[0] + dragDistance[0],
					this.sectionProperties.lastPosition[1] + dragDistance[1],
				);
			}

			if (e.type === 'touchmove' && MobileSelectionMagnifier.isIOSImpressTextEditing()) {
				const touch = (e as unknown as TouchEvent).touches[0];
				if (touch) {
					// Sample the text above the handle's grab area.
					const sampleY = touch.clientY - CursorHandler.objectHeight / 2;
					if (dragStarted) {
						AndroidSelectionMenu.cancelIOSLongPressForHandle();
						MobileSelectionMagnifier.startAt(touch.clientX, sampleY);
					} else {
						MobileSelectionMagnifier.updateAt(touch.clientX, sampleY);
					}
				}
			}
		}
	}

	onMouseUp(point: cool.SimplePoint, e: MouseEvent): void {
		if (this.sectionProperties.lastPosition && MobileSelectionMagnifier.isIOSImpressTextEditing()) {
			MobileSelectionMagnifier.hide();
		}
		if (
			this.containerObject.isDraggingSomething() &&
			this.containerObject.targetSection === this.name
		) {
			app.map._docLayer._postMouseEvent(
				'buttondown',
				Math.round(this.position[0] * app.pixelsToTwips),
				Math.round(this.position[1] * app.pixelsToTwips),
				1,
				1,
				0,
			);
			app.map._docLayer._postMouseEvent(
				'buttonup',
				Math.round(this.position[0] * app.pixelsToTwips),
				Math.round(this.position[1] * app.pixelsToTwips),
				1,
				1,
				0,
			);
		}
		this.sectionProperties.lastPosition = null;
	}

	setOpacity(value: number) {
		this.getHTMLObject().style.opacity = value;
	}
}
