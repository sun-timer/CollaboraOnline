/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

// Dependencies used by other AndroidSelectionMenu methods, outside this test path.
var JSDialog: any;
var GraphicSelection: any;
var TextSelections: any;
interface Window {
	postMobileMessage?: (message: string) => void;
}

describe('AndroidSelectionMenu iOS touch routing', function () {
	it('cancels the pending long press throughout a multi-touch sequence', function () {
		const menu: any = AndroidSelectionMenu;
		const originalSetTimeout = window.setTimeout;
		const originalClearTimeout = window.clearTimeout;
		const originalIsSelectionDoc = menu.isSelectionDoc;
		const originalLongPress = menu.onLongPressAt;
		const originalCancel = menu.cancelGesture;
		const originalUpdate = menu.updateTextSelectionEndAt;
		const originalFinish = menu.finishTextSelectionDrag;
		const originalDragActive = menu.nativeSelectionDragActive;
		const originalPending = menu.pendingLongPressSelection;
		const originalGestureComplete = menu.selectionGestureComplete;
		const originalPinchCancelled = menu.iosPinchCancelledLongPress;
		const originalHide = menu.hide;
		const originalSocket = app.socket;
		const originalDeactivateTextSelections = TextSelections;
		const originalAddEventListener = document.addEventListener;
		const originalRemoveEventListener = document.removeEventListener;
		const listeners: Array<{ type: string; listener: EventListenerOrEventListenerObject; options: any }> = [];
		const callbacks = new Map<number, () => void>();
		const cleared = new Set<number>();
		let nextTimer = 1;
		let longPresses = 0;
		let updates = 0;
		let finishes = 0;
		let deactivations = 0;
		let hides = 0;
		const messages: string[] = [];

		const touch = (x: number, y: number) => ({ clientX: x, clientY: y });
		const dispatchTouch = (type: string, touches: any[], changedTouches: any[] = touches) => {
			const event = new window.Event(type, { bubbles: true, cancelable: true });
			Object.defineProperties(event, {
				touches: { value: touches },
				changedTouches: { value: changedTouches },
				targetTouches: { value: touches },
			});
			canvas.dispatchEvent(event);
		};

		const canvas = document.createElement('canvas');
		document.body.appendChild(canvas);
		try {
			window.setTimeout = ((callback: TimerHandler) => {
				const id = nextTimer++;
				callbacks.set(id, callback as () => void);
				return id;
			}) as any;
			window.clearTimeout = ((id: number) => {
				cleared.add(id);
			}) as any;
			document.addEventListener = function (type: string, listener: any, options: any) {
				listeners.push({ type, listener, options });
				return originalAddEventListener.call(this, type, listener, options);
			} as any;
			document.removeEventListener = function (type: string, listener: any, options: any) {
				return originalRemoveEventListener.call(this, type, listener, options);
			} as any;
			menu.isSelectionDoc = () => true;
			menu.onLongPressAt = () => {
				longPresses++;
				menu.nativeSelectionDragActive = true;
				menu.pendingLongPressSelection = true;
				menu.selectionGestureComplete = false;
			};
			menu.hide = () => hides++;
			menu.updateTextSelectionEndAt = () => updates++;
			menu.finishTextSelectionDrag = () => finishes++;
			TextSelections = { deactivate: () => deactivations++ };
			app.socket = { sendMessage: (message: string) => messages.push(message) };
			menu.nativeSelectionDragActive = false;
			menu.installIosLongPress();

			// A delayed long press from the first finger must not survive the second.
			dispatchTouch('touchstart', [touch(20, 30)]);
			assert.equal(callbacks.size, 1);
			const orphanCandidate = [...callbacks.keys()][0];
			dispatchTouch('touchstart', [touch(20, 30), touch(80, 30)]);
			assert.ok(cleared.has(orphanCandidate));
			assert.equal(callbacks.size, 1, 'a second timer is not scheduled for the pinch');

			// Slow/small multi-touch motion is still a pinch, not a long press or drag.
			dispatchTouch('touchmove', [touch(21, 30), touch(79, 30)]);
			assert.equal(longPresses, 0);
			assert.equal(updates, 0);
			dispatchTouch('touchend', [touch(21, 30)], [touch(79, 30)]);
			assert.equal(finishes, 0, 'the remaining finger stays consumed');
			dispatchTouch('touchend', [], [touch(21, 30)]);
			assert.equal(finishes, 0);

			// A fresh single-finger gesture after the pinch still works.
			dispatchTouch('touchstart', [touch(24, 35)]);
			const nextId = nextTimer - 1;
			callbacks.get(nextId)!();
			assert.equal(longPresses, 1);
			dispatchTouch('touchend', [], [touch(24, 35)]);
			assert.equal(finishes, 1);

			// Let a long press start its range, then upgrade to pinch. The real
			// cancelGesture path must clear only the unfinished range; a delayed
			// core textselection reply must remain suppressed too.
			dispatchTouch('touchstart', [touch(10, 10)]);
			const activeTimer = nextTimer - 1;
			callbacks.get(activeTimer)!();
			assert.equal(longPresses, 2);
			dispatchTouch('touchstart', [touch(10, 10), touch(30, 10)]);
			assert.equal(menu.nativeSelectionDragActive, false);
			assert.equal(menu.pendingLongPressSelection, false);
			assert.equal(menu.iosPinchCancelledLongPress, true);
			assert.ok(messages.includes('resetselection'), 'the unfinished core range is reset');
			assert.ok(deactivations > 0, 'the local selection handles are deactivated');
			assert.equal(menu.consumeIosPinchCancelledTextSelection('late range'), true);
			assert.equal(menu.iosPinchCancelledLongPress, true, 'nonempty late replies remain suppressed');
			assert.equal(menu.consumeIosPinchCancelledTextSelection('EMPTY'), true);
			assert.equal(menu.iosPinchCancelledLongPress, false, 'empty reset acknowledgement releases the latch');
			assert.equal(menu.consumeIosPinchCancelledTextSelection('programmatic range'), false,
				'a later programmatic selection is allowed through');
			assert.ok(hides > 0, 'a late textselection reply cannot reopen the menu');
			dispatchTouch('touchmove', [touch(11, 10), touch(29, 10)]);
			dispatchTouch('touchcancel', [], [touch(11, 10), touch(29, 10)]);
			assert.equal(finishes, 1);
		} finally {
			for (const listener of listeners) {
				originalRemoveEventListener.call(document, listener.type, listener.listener, listener.options);
			}
			document.addEventListener = originalAddEventListener;
			document.removeEventListener = originalRemoveEventListener;
			window.setTimeout = originalSetTimeout;
			window.clearTimeout = originalClearTimeout;
			menu.isSelectionDoc = originalIsSelectionDoc;
			menu.onLongPressAt = originalLongPress;
			menu.cancelGesture = originalCancel;
			menu.updateTextSelectionEndAt = originalUpdate;
			menu.finishTextSelectionDrag = originalFinish;
			menu.nativeSelectionDragActive = originalDragActive;
			menu.pendingLongPressSelection = originalPending;
			menu.selectionGestureComplete = originalGestureComplete;
			menu.iosPinchCancelledLongPress = originalPinchCancelled;
			menu.hide = originalHide;
			app.socket = originalSocket;
			TextSelections = originalDeactivateTextSelections;
			canvas.remove();
		}
	});

	it('accepts a visible Writer caret anywhere on the tapped line', function () {
		const menu: any = AndroidSelectionMenu;
		const original = {
			map: app.map,
			file: app.file,
			pixelsToTwips: app.pixelsToTwips,
			iOS: (window as any).ThisIsTheiOSApp,
			keyboardOpen: menu.isKeyboardOpen,
			releaseKeyboardPreservation: menu.releaseKeyboardPreservation,
			pendingRequest: menu.pendingTextInputRequest,
		};
		let acceptedInput = 0;
		app.map = {
			_docLayer: {},
			getDocType: () => 'text',
			isReadOnlyMode: () => false,
			focus: (acceptInput: boolean) => { if (acceptInput) acceptedInput++; },
		};
		app.file = {
			textCursor: {
				visible: true,
				rectangle: { x1: 12, y1: 100, y2: 120 },
			},
		};
		app.pixelsToTwips = 1;
		(window as any).ThisIsTheiOSApp = true;
		menu.isKeyboardOpen = () => false;
		menu.releaseKeyboardPreservation = () => {};
		try {
			// Core can keep the caret at an empty paragraph's leading edge while
			// the tap lands farther along the same visual line.
			menu.beginTextInputRequest({ x: 300, y: 110 });
			menu.resolveTextInputTap();
			assert.equal(acceptedInput, 1, 'the existing caret on that line accepts keyboard input');

			menu.beginTextInputRequest({ x: 300, y: 150 });
			menu.resolveTextInputTap();
			assert.equal(acceptedInput, 1, 'a tap on another line cannot reuse the old caret');
		} finally {
			menu.cancelTextInputRequest('test_cleanup');
			menu.isKeyboardOpen = original.keyboardOpen;
			menu.releaseKeyboardPreservation = original.releaseKeyboardPreservation;
			menu.pendingTextInputRequest = original.pendingRequest;
			app.map = original.map;
			app.file = original.file;
			app.pixelsToTwips = original.pixelsToTwips;
			(window as any).ThisIsTheiOSApp = original.iOS;
		}
	});

	it('accepts an empty iOS Writer line when the tap is in its blank body', function () {
		const menu: any = AndroidSelectionMenu;
		const originalIsIOS = menu.isIOS;
		const originalIsWriterDoc = menu.isWriterDoc;
		const previousPixelsToTwips = app.pixelsToTwips;
		const caret = { x1: 500, x2: 520, y1: 1000, y2: 1400 };
		const blankBodyTap = { x: 8000, y: 1600 };
		const belowLineTap = { x: 8000, y: 2000 };
		app.pixelsToTwips = 15;
		try {
			assert.equal(menu.isCaretOnTapLine(caret, blankBodyTap), true,
				'a tap in the blank body within one line height is on the empty line');
			assert.equal(menu.isCaretOnTapLine(caret, belowLineTap), false,
				'a tap more than one line height below the line box is not on the line');

			menu.isIOS = () => true;
			menu.isWriterDoc = () => true;
			assert.equal(menu.isCaretAtTarget(caret, blankBodyTap), true,
				'iOS Writer accepts the blank body tap on an empty line');

			menu.isIOS = () => false;
			menu.isWriterDoc = () => false;
			assert.equal(menu.isCaretAtTarget(caret, blankBodyTap), false,
				'non-iOS cannot use the empty-line exception');
		} finally {
			menu.isIOS = originalIsIOS;
			menu.isWriterDoc = originalIsWriterDoc;
			app.pixelsToTwips = previousPixelsToTwips;
		}
	});
});
