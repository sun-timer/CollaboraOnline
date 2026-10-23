/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile Selection Menu', function () {
	it('lists all Writer edit-mode AI task types aligned with Android', function () {
		const types = MobileSelectionMenu.aiTaskTypes();
		assert.deepEqual(types, [
			'translate',
			'outline',
			'continue',
			'article_generate',
			'expand',
			'polish',
			'condense',
			'rewrite',
		]);
	});

	it('edit menu exposes clipboard + AI ids in 5+5+2 order', function () {
		const ids = MobileSelectionMenu.editMenuItems().map((item) => item.id);
		assert.deepEqual(ids, [
			'copy',
			'cut',
			'paste',
			'select_all',
			'translate',
			'outline',
			'continue_write',
			'article_generate',
			'expand',
			'polish',
			'condense',
			'rewrite',
		]);
	});

	it('Calc menu exposes clipboard actions and the five Calc AI tasks', function () {
		const ids = MobileSelectionMenu.calcMenuItems().map((item) => item.id);
		assert.deepEqual(ids, ['copy', 'paste', 'cut', 'clear', 'calc_formula', 'calc_cond_format', 'calc_data_analysis', 'calc_data_process', 'calc_chart']);
		const aiRows = MobileSelectionMenu.calcMenuItems().filter((item) =>
			['outline', 'continue_write', 'article_generate', 'expand', 'polish', 'condense', 'rewrite'].includes(item.id),
		);
		assert.equal(aiRows.length, 0);
	});

	it('PPT graphic menu is a compact single row', function () {
		const ids = MobileSelectionMenu.graphicMenuItems().map((item) => item.id);
		assert.deepEqual(ids, [
			'copy',
			'cut',
			'paste',
			'delete',
			'image_edit',
			'save',
		]);
	});

	it('Writer reading menu hugs its two visible actions', function () {
		const previousApp = (window as any).app;
		const previousRaf = window.requestAnimationFrame;
		const menu: any = new MobileSelectionMenu();
		try {
			(window as any).app = {
				isReadOnly: () => true,
				map: { getDocType: () => 'text', isEditMode: () => false },
			};
			window.requestAnimationFrame = () => 0;
			menu.show({ detail: { anchorX: 120, anchorY: 160, anchorBottomY: 170 } });

			const panel = document.querySelector('.fabric-selection-menu') as HTMLElement;
			assert.ok(panel.classList.contains('fabric-selection-menu--text-reading'));
			assert.equal(panel.querySelectorAll('.fabric-selection-menu__button').length, 2);
			assert.equal(panel.style.left, '56px', 'placement uses the compact 128px two-action width');
		} finally {
			menu.dispose();
			(window as any).app = previousApp;
			window.requestAnimationFrame = previousRaf;
		}
	});

	it('ships Figma/Android icons for every menu item', function () {
		const allItems = [
			...MobileSelectionMenu.editMenuItems(),
			...MobileSelectionMenu.calcMenuItems(),
			...MobileSelectionMenu.graphicMenuItems(),
		];
		const seen = new Set<string>();
		allItems.forEach((item) => {
			if (seen.has(item.iconKey)) {
				return;
			}
			seen.add(item.iconKey);
			assert.ok(item.catalogTaskType?.startsWith('calc_')
				? MobileAiOperationIcons.svgFor(item.catalogTaskType).includes('<svg')
				: MobileSelectionMenuIcons.has(item.iconKey), item.iconKey);
		});
	});

	it('shows PPT AI tasks only for editable Impress and never for preview', function () {
		assert.deepEqual(MobileSelectionMenu.menuTaskTypesForDocument('presentation', false), [
			'impress_generate',
			'continue',
			'article_generate',
			'expand',
			'polish',
			'condense',
			'rewrite',
			'text_extract',
			'image_generate',
			'format_batch',
		]);
		assert.deepEqual(MobileSelectionMenu.menuTaskTypesForDocument('presentation', true), []);
	});

	it('spreadsheet selection catalog remains empty but menu items exist', function () {
		assert.deepEqual(MobileSelectionMenu.menuTaskTypesForDocument('spreadsheet', false), []);
		assert.ok(MobileSelectionMenu.calcMenuItems().length > 0);
	});

	it('selection-required AI actions show a toast from the caret menu without opening a task', function () {
		const previousApp = (window as any).app;
		const previousPanel = (window as any).__coolWriterAiPanel;
		const previousMenu = (window as any).__coolMobileSelectionMenu;
		const previousSelectionBridge = (window as any).AndroidSelectionMenu;
		const previousIOSFlag = (window as any).ThisIsTheiOSApp;
		const previousIgnoreActionsUntil = (MobileSelectionMenu as any).ignoreActionsUntil;
		const menu: any = new MobileSelectionMenu();
		let openedTask = '';
		let hasLiveSelection = false;
		try {
			(window as any).ThisIsTheiOSApp = true;
			(window as any).AndroidSelectionMenu = {
				hasVisibleTextSelection: () => hasLiveSelection,
			};
			(window as any).app = {
				isReadOnly: () => false,
				map: { getDocType: () => 'text', isEditMode: () => true },
			};
			(window as any).__coolWriterAiPanel = { openTask: (task: string) => (openedTask = task) };
			(window as any).__coolMobileSelectionMenu = menu;
			menu.show({ detail: { anchorX: 120, anchorY: 160, anchorBottomY: 170, hasSelection: false } });
			(MobileSelectionMenu as any).ignoreActionsUntil = 0;

			const continueButton = document.querySelector<HTMLButtonElement>(
				'.fabric-selection-menu [aria-label="文案续写"]',
			);
			assert.ok(continueButton);
			continueButton!.click();
			assert.equal(openedTask, '');
			assert.equal(document.body.textContent?.includes('需要选中文本'), true);

			hasLiveSelection = true;
			(MobileSelectionMenu as any).onItemTap(
				MobileSelectionMenu.editMenuItems().find((item) => item.id === 'continue_write'),
			);
			assert.equal(openedTask, 'continue');
		} finally {
			menu.dispose();
			(window as any).app = previousApp;
			(window as any).__coolWriterAiPanel = previousPanel;
			(window as any).__coolMobileSelectionMenu = previousMenu;
			(window as any).AndroidSelectionMenu = previousSelectionBridge;
			(window as any).ThisIsTheiOSApp = previousIOSFlag;
			(MobileSelectionMenu as any).ignoreActionsUntil = previousIgnoreActionsUntil;
			document.querySelectorAll('div').forEach((element) => {
				if (element.textContent === '需要选中文本') element.remove();
			});
		}
	});

	it('iOS Writer caret taps open the menu without changing long-press selection', function () {
		const previousApp = (window as any).app;
		const previousIOSFlag = (window as any).ThisIsTheiOSApp;
		const previousTextSelections = (global as any).TextSelections;
		const previousGraphicSelection = (global as any).GraphicSelection;
		const previousSetTimeout = window.setTimeout;
		const previousClearTimeout = window.clearTimeout;
		const previousLongPress = (AndroidSelectionMenu as any).onLongPressAt;
		const selectionEvents: any[] = [];
		const onSelectionShow = (event: Event) => selectionEvents.push((event as CustomEvent).detail);
		window.addEventListener(MobileSelectionEvents.SHOW_EVENT, onSelectionShow);
		const timers = new Map<number, { callback: () => void; delay: number }>();
		let nextTimer = 1;
		let selectedRange = false;
		let graphicSelection = false;
		let longPresses = 0;
		let readOnly = false;
		const canvas = document.createElement('canvas');
		const cursorHandle = document.createElement('div');
		cursorHandle.className = 'cursor-handler';
		cursorHandle.getBoundingClientRect = () => ({
			left: 15, top: 15, right: 45, bottom: 60, width: 30, height: 45,
			x: 15, y: 15, toJSON: () => ({}),
		}) as DOMRect;
		document.body.append(canvas, cursorHandle);
		const dispatchTouch = (type: string, target: HTMLElement, touches: any[], changedTouches: any[]) => {
			const event = new Event(type, { bubbles: true, cancelable: true });
			Object.defineProperties(event, {
				touches: { value: touches },
				changedTouches: { value: changedTouches },
			});
			target.dispatchEvent(event);
		};
		const touch = (x: number, y: number) => ({ clientX: x, clientY: y });
		const tap = (target: HTMLElement, x: number, y: number) => {
			const point = touch(x, y);
			dispatchTouch('touchstart', target, [point], []);
			dispatchTouch('touchend', target, [], [point]);
		};
		let cleanup: (() => void) | undefined;
		try {
			(window as any).ThisIsTheiOSApp = true;
			window.setTimeout = ((callback: TimerHandler, delay?: number) => {
				const id = nextTimer++;
				timers.set(id, { callback: callback as () => void, delay: Number(delay) || 0 });
				return id;
			}) as any;
			window.clearTimeout = ((id: number) => timers.delete(id)) as any;
			(global as any).TextSelections = {
				isActive: () => selectedRange,
				deactivate: () => undefined,
				getStartRectangle: () => null,
				getEndRectangle: () => null,
			};
			(global as any).GraphicSelection = { hasActiveSelection: () => graphicSelection };
			(window as any).app = {
				dpiScale: 2,
				pixelsToTwips: 1,
				sectionContainer: {
					clientViewPointToDocumentTwips: (x: number, y: number) => ({
						x: x === 40 ? 1000 : 3000,
						y: y === 60 ? 2100 : y === 130 ? 2400 : 6000,
					}),
					getCanvasBoundingClientRect: () => ({ x: 7, y: 11 }),
				},
				map: {
					getDocType: () => 'text',
					isReadOnlyMode: () => readOnly,
					fire: () => undefined,
					_docLayer: {},
				},
				file: {
					textCursor: {
						visible: true,
						rectangle: new cool.SimpleRectangle(1000, 2000, 0, 200),
					},
				},
				activeDocument: {
					activeView: {},
					activeLayout: {
						documentToViewX: (point: any) => point.pX + 300,
						documentToViewY: (point: any) => point.pY + 400,
					},
				},
			};
			(AndroidSelectionMenu as any).onLongPressAt = () => longPresses++;
			cleanup = (AndroidSelectionMenu as any).installIosLongPress();
			const runTimer = (delay: number) => {
				const entry = [...timers.entries()].find(([, timer]) => timer.delay === delay);
				assert.ok(entry, `expected a ${delay}ms timer`);
				timers.delete(entry![0]);
				entry![1].callback();
			};

			// A first tap elsewhere leaves normal canvas click/caret placement alone.
			tap(canvas, 80, 90);
			assert.equal(selectionEvents.length, 0);
			assert.equal(timers.size, 0);
			// Same column and old broad line tolerance, but outside the caret slop.
			tap(canvas, 20, 65);
			assert.equal(timers.size, 0);

			// A repeated tap directly on the visible caret suppresses only its click,
			// then opens the same menu at the scrolled caret coordinates.
			tap(canvas, 20, 30);
			const syntheticClick = new MouseEvent('click', { bubbles: true, cancelable: true, detail: 1, clientX: 20, clientY: 30 });
			canvas.dispatchEvent(syntheticClick);
			assert.equal(syntheticClick.defaultPrevented, true);
			runTimer(320);
			assert.equal(selectionEvents.length, 1);
			assert.equal(selectionEvents[0].hasSelection, false);
			assert.deepEqual(
				[selectionEvents[0].anchorX, selectionEvents[0].anchorY, selectionEvents[0].anchorBottomY],
				[657, 1211, 1311],
			);
			// The cursor droplet itself can be tapped even though the point beneath
			// it is outside the narrow black-caret line hit area.
			tap(canvas, 20, 50);
			runTimer(320);
			assert.equal(selectionEvents.length, 2);

			// The cursor droplet is also a valid tap target; a double tap cancels the
			// pending menu, while a drag, pinch, selected range, preview, and graphic
			// selection never enter the caret-menu path.
			tap(cursorHandle, 20, 30);
			tap(canvas, 20, 30);
			assert.equal(timers.size, 0, 'the second tap cancels the pending menu');
			dispatchTouch('touchstart', canvas, [touch(20, 30)], []);
			dispatchTouch('touchmove', canvas, [touch(40, 30)], []);
			dispatchTouch('touchend', canvas, [], [touch(40, 30)]);
			assert.equal(timers.size, 0);
			dispatchTouch('touchstart', canvas, [touch(20, 30), touch(21, 31)], []);
			assert.equal(timers.size, 0);
			dispatchTouch('touchend', canvas, [], [touch(20, 30)]);
			selectedRange = true;
			tap(canvas, 20, 30);
			assert.equal(timers.size, 0);
			selectedRange = false;
			graphicSelection = true;
			tap(canvas, 20, 30);
			assert.equal(timers.size, 0);
			graphicSelection = false;
			readOnly = true;
			tap(canvas, 20, 30);
			assert.equal(timers.size, 0);
			readOnly = false;

			// Long-press remains the original word-selection gesture, even at caret.
			dispatchTouch('touchstart', canvas, [touch(20, 30)], []);
			runTimer(600);
			assert.equal(longPresses, 1);
			assert.equal(selectionEvents.length, 2);
			// Moving/dragging the cursor droplet stays with CursorHandler.
			dispatchTouch('touchstart', cursorHandle, [touch(20, 30)], []);
			runTimer(600);
			assert.equal(longPresses, 1);
		} finally {
			cleanup?.();
			document.body.removeChild(canvas);
			document.body.removeChild(cursorHandle);
			window.removeEventListener(MobileSelectionEvents.SHOW_EVENT, onSelectionShow);
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOSFlag;
			(global as any).TextSelections = previousTextSelections;
			(global as any).GraphicSelection = previousGraphicSelection;
			window.setTimeout = previousSetTimeout;
			window.clearTimeout = previousClearTimeout;
			(AndroidSelectionMenu as any).onLongPressAt = previousLongPress;
		}
	});

	it('iOS Impress image edit keeps the existing Crop UNO action', function () {
		const previousApp = (window as any).app;
		const previousIOSFlag = (window as any).ThisIsTheiOSApp;
		const imageEditEvents: Event[] = [];
		const onImageEdit = (event: Event) => imageEditEvents.push(event);
		window.addEventListener('fabric:image:edit', onImageEdit);
		const commands: string[] = [];
		try {
			(window as any).ThisIsTheiOSApp = true;
			(window as any).app = {
				map: {
					getDocType: () => 'presentation',
					isEditMode: () => true,
					sendUnoCommand: (command: string) => commands.push(command),
				},
			};
			const imageEdit = MobileSelectionMenu.graphicMenuItems().find((item) => item.id === 'image_edit');
			(MobileSelectionMenu as any).runAction(imageEdit);
			assert.deepEqual(commands, ['.uno:Crop']);
			assert.equal(imageEditEvents.length, 0);
		} finally {
			window.removeEventListener('fabric:image:edit', onImageEdit);
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOSFlag;
		}
	});

	it('iOS Writer annotation tap takes priority, then repeated tap reaches the caret', function () {
		const previousApp = (window as any).app;
		const previousIOSFlag = (window as any).ThisIsTheiOSApp;
		const previousComments = (window as any).MobileWriterComments;
		const previousTextSelections = (global as any).TextSelections;
		const previousGraphicSelection = (global as any).GraphicSelection;
		const previousSetTimeout = window.setTimeout;
		const previousClearTimeout = window.clearTimeout;
		const timers = new Map<number, () => void>();
		let nextTimer = 1;
		let activeComment = false;
		const commentMessages: string[] = [];
		let clearedMousePositions = 0;
		let canceledMouseControl = 0;
		let targetTouchEnds = 0;
		const canvas = document.createElement('canvas');
		document.body.append(canvas);
		const dispatchTouch = (type: string, touches: any[], changedTouches: any[]) => {
			const event = new Event(type, { bubbles: true, cancelable: true });
			Object.defineProperties(event, {
				touches: { value: touches },
				changedTouches: { value: changedTouches },
			});
			canvas.dispatchEvent(event);
			return event;
		};
		const touch = { clientX: 20, clientY: 30 };
		const onCanvasTouchEnd = () => targetTouchEnds++;
		canvas.addEventListener('touchend', onCanvasTouchEnd);
		let cleanup: (() => void) | undefined;
		try {
			(window as any).ThisIsTheiOSApp = true;
			window.setTimeout = ((callback: TimerHandler) => {
				const id = nextTimer++;
				timers.set(id, callback as () => void);
				return id;
			}) as any;
			window.clearTimeout = ((id: number) => timers.delete(id)) as any;
			(global as any).TextSelections = { isActive: () => false };
			(global as any).GraphicSelection = { hasActiveSelection: () => false };
			(window as any).MobileWriterComments = {
				findWriterInfoAtDocumentPoint: () => [{ id: '42', kind: 'comment' }],
				isWriterCommentInfoActive: () => activeComment,
				handleWriterCommentTap: () => {
					if (activeComment) {
						activeComment = false;
						commentMessages.push('HIDECOMMENTINFO');
						return 'dismissed';
					}
					activeComment = true;
					commentMessages.push('SHOWCOMMENTINFO {"id":"42","kind":"comment"}');
					return 'shown';
				},
			};
			(window as any).app = {
				dpiScale: 2,
				pixelsToTwips: 1,
				sectionContainer: {
					clientViewPointToDocumentTwips: () => ({ x: 10, y: 20 }),
					clearMousePositions: () => clearedMousePositions++,
				},
				activeDocument: {
					mouseControl: { cancelSingleTouchForMultiTouch: () => canceledMouseControl++ },
				},
				map: {
					getDocType: () => 'text',
					isReadOnlyMode: () => false,
					_docLayer: {},
				},
				file: {
					textCursor: {
						visible: true,
						rectangle: new cool.SimpleRectangle(10, 20, 0, 100),
					},
				},
			};
			cleanup = (AndroidSelectionMenu as any).installIosLongPress();

			dispatchTouch('touchstart', [touch], []);
			assert.equal(
				(AndroidSelectionMenu as any).shouldSuppressWriterCommentClick(
					new MouseEvent('click', { detail: 1, clientX: touch.clientX, clientY: touch.clientY }),
				),
				true,
				'pending first-tap annotation state gates caret/keyboard work in MouseControl',
			);
			const firstEnd = dispatchTouch('touchend', [], [touch]);
			assert.equal(firstEnd.defaultPrevented, true);
			assert.equal(targetTouchEnds, 0, 'first tap is consumed before canvas MouseControl');
			assert.deepEqual(commentMessages, ['SHOWCOMMENTINFO {"id":"42","kind":"comment"}']);
			assert.equal(canceledMouseControl, 1);
			assert.equal(clearedMousePositions, 1);
			assert.equal(timers.size, 0, 'annotation tap does not start the caret menu timer');

			dispatchTouch('touchstart', [touch], []);
			const secondEnd = dispatchTouch('touchend', [], [touch]);
			assert.equal(secondEnd.defaultPrevented, false);
			assert.equal(targetTouchEnds, 1, 'second tap reaches the normal caret and keyboard path');
			assert.deepEqual(commentMessages, ['SHOWCOMMENTINFO {"id":"42","kind":"comment"}', 'HIDECOMMENTINFO']);
			assert.equal(timers.size, 0);
		} finally {
			cleanup?.();
			canvas.removeEventListener('touchend', onCanvasTouchEnd);
			document.body.removeChild(canvas);
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOSFlag;
			(window as any).MobileWriterComments = previousComments;
			(global as any).TextSelections = previousTextSelections;
			(global as any).GraphicSelection = previousGraphicSelection;
			window.setTimeout = previousSetTimeout;
			window.clearTimeout = previousClearTimeout;
		}
	});
});
