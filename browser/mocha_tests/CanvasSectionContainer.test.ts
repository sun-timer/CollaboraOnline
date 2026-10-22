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

describe('CanvasSectionContainer', function() {

var jsdom = require('jsdom');
var assert = require('assert').strict;

var dom = new jsdom.JSDOM(canvasDomString(), { pretendToBeVisual: true });

addMockCanvas(dom.window);
global.window = dom.window;
global.document = dom.window.document;
global.requestAnimationFrame = dom.window.requestAnimationFrame;
global.cancelAnimationFrame = dom.window.cancelAnimationFrame;

const canvasWidth = 1024;
const canvasHeight = 768;
const halfWidth = Math.floor(canvasWidth / 2);
const halfHeight = Math.floor(canvasHeight / 2);
const originX = 0;
const originY = 0;

describe('Singleton section container', function() {

    beforeEach(function () {
        setupCanvasContainer(canvasWidth, canvasHeight);

        app.sectionContainer.enableDrawing();

        const docLayer = {};
        const tsManager = {};

        let onlySection = new CanvasSectionObject('OnlySection');
        onlySection.anchor = ['top', 'left'];
        onlySection.position = [originX, originY];
        onlySection.size = [1, 1];
        onlySection.expand = ['bottom', 'right'];
        onlySection.processingOrder = onlySection.drawingOrder = onlySection.zIndex = 1;
        onlySection.interactable = false;
        onlySection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };

        app.sectionContainer.addSection(onlySection);
    });

    it('Container should have OnlySection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('OnlySection'));
    });

    it('OnlySection PosSize checks', function () {
        const only = app.sectionContainer.getSectionWithName('OnlySection');
        const onlyRect = getSectionRectangle(only);
        assertPosSize(onlyRect,
            {
                x: originX,
                y: originY,
                width: canvasWidth - originX,
                height: canvasHeight - originY
            });
    });
});

describe('Mobile zoom redraw coordination', function() {
    function setupContainerWithoutInitialRedraw() {
        const canvas = <HTMLCanvasElement>document.getElementById('document-canvas');
        app.sectionContainer = new CanvasSectionContainer(canvas);
    }

    it('defers normal redraws and replays one after the zoom animation', function() {
        setupContainerWithoutInitialRedraw();
        const previousMode = (window as any).mode;
        try {
            let drawCount = 0;
            let resizeCount = 0;
            let flushCount = 0;
            let scheduledRedrawCount = 0;
            (window as any).mode = { isMobile: () => true, isTablet: () => false };

            const container: any = app.sectionContainer;
            const requestReDraw = container.requestReDraw.bind(container);
            container.resizeCanvas = () => resizeCount++;
            container.drawSections = () => drawCount++;
            container.flushLayoutingTasks = () => flushCount++;
            container.animate = () => {};

            container.setInZoomAnimation(true);
            container.drawRequest = 1; // Simulate a RAF queued before the painter took ownership.
            container.redrawCallback(0);
            requestReDraw();
            assert.equal(drawCount, 0, 'normal redraw must not clear the zoom painter frame');
            assert.equal(resizeCount, 0, 'resize waits until the zoom painter releases the canvas');
            assert.equal(flushCount, 0, 'layout tasks remain queued until the deferred redraw');

            container.requestReDraw = () => scheduledRedrawCount++;
            container.setInZoomAnimation(false);
            assert.equal(scheduledRedrawCount, 1, 'ending zoom schedules the deferred redraw');
            container.redrawCallback(16);
            assert.equal(drawCount, 1, 'interleaved requests coalesce to one draw');
            assert.equal(resizeCount, 1, 'deferred redraw applies the pending resize');
            assert.equal(flushCount, 1, 'deferred redraw runs pending layout work');
            assert.equal(container.redrawAfterZoom, false, 'pending redraw lock is released');

            // A second gesture can take and release the canvas again.
            container.setInZoomAnimation(true);
            requestReDraw();
            container.setInZoomAnimation(false);
            assert.equal(scheduledRedrawCount, 2, 'a repeated gesture schedules its own redraw');
            container.redrawCallback(32);
            assert.equal(drawCount, 2);
            assert.equal(container.redrawAfterZoom, false);
        } finally {
            (window as any).mode = previousMode;
        }
    });

    it('finishes the pinch once when one finger remains and consumes its final touchend', function() {
        setupContainerWithoutInitialRedraw();
        const container: any = app.sectionContainer;
        let pinchEndCount = 0;
        let mouseUpCount = 0;
        container.multiTouch = true;
        container.sectionOnMouseDown = 'OnlySection';
        container.getSectionWithName = () => ({});
        container.propagateOnMultiTouchEnd = () => pinchEndCount++;
        container.propagateOnMouseUp = () => mouseUpCount++;

        container.onTouchEnd({ touches: [{}] } as any);
        assert.equal(pinchEndCount, 1, 'lifting either finger finishes the pinch');
        assert.equal(container.multiTouch, true, 'remaining finger stays consumed as part of the pinch');
        container.onTouchEnd({ touches: [] } as any);
        assert.equal(pinchEndCount, 1, 'final finger does not finish the pinch twice');
        assert.equal(mouseUpCount, 0, 'final finger does not synthesize a single-finger mouseup');
        assert.equal(container.multiTouch, false, 'all touch state is released');
    });

    it('starts a fresh pinch when a second finger rejoins before the first lifts', function() {
        setupContainerWithoutInitialRedraw();
        const container: any = app.sectionContainer;
        const previousMouseControlSection = app.CSections.MouseControl;
        let pinchStartCount = 0;
        let pinchEndCount = 0;
        let mouseDownCount = 0;
        try {
            app.CSections.MouseControl = { name: 'mouse-control' };
            container.sectionOnMouseDown = 'mouse-control';
            container.positionOnMouseDown = [10, 10];
            container.getSectionWithName = () => ({});
            container.propagateOnMultiTouchStart = () => pinchStartCount++;
            container.propagateOnMultiTouchEnd = () => pinchEndCount++;
            container.propagateOnMouseDown = () => mouseDownCount++;
            container.multiTouch = true;
            container.multiTouchEndSent = false;

            container.onTouchEnd({ touches: [{}] } as any);
            assert.equal(container.multiTouchEndSent, true);
            container.onTouchStart({ touches: [{}] } as any);
            assert.equal(mouseDownCount, 0, 'the remaining finger stays consumed');

            container.touchCenter = [50, 50];
            container.onTouchStart({
                touches: [
                    { clientX: 10, clientY: 20 },
                    { clientX: 30, clientY: 40 },
                ],
            } as any);
            assert.equal(pinchStartCount, 1, 'a new pinch start is propagated to MouseControl');
            assert.equal(container.multiTouchEndSent, false);
            assert.equal(container.touchCenter, null, 'the new pinch samples a fresh center on move');
            container.onTouchEnd({ touches: [{}] } as any);
            assert.equal(pinchEndCount, 2, 'the rejoined pinch can end independently');
        } finally {
            app.CSections.MouseControl = previousMouseControlSection;
        }
    });

    it('starts a pinch when both touches arrive together without sending single-touch down', function() {
        setupContainerWithoutInitialRedraw();
        const container: any = app.sectionContainer;
        const previousMouseControlSection = app.CSections.MouseControl;
        let requestedSection: string = null;
        let transitionCleanupCount = 0;
        let pinchStartCount = 0;
        let mouseDownCount = 0;
        container.convertPointToCanvasLocale = (point: number[]) => point;
        app.CSections.MouseControl = { name: 'mouse-control' };
        container.getSectionWithName = (name: string) => {
            requestedSection = name;
            return { cancelSingleTouchForMultiTouch: () => transitionCleanupCount++ };
        };
        container.propagateOnMultiTouchStart = () => pinchStartCount++;
        container.propagateOnMouseDown = () => mouseDownCount++;

        try {
            container.onTouchStart({
                touches: [
                    { clientX: 10, clientY: 20 },
                    { clientX: 30, clientY: 40 },
                ],
            } as any);

            assert.deepEqual(container.positionOnMouseDown, [20, 30], 'the touch center unlocks touchmove routing');
            assert.equal(requestedSection, 'mouse-control', 'the zoom handler owns multi-touch even over a selection handle');
            assert.equal(pinchStartCount, 1);
            assert.equal(mouseDownCount, 0, 'no first-finger text selection is started');
            assert.equal(container.multiTouch, true);

            container.multiTouch = false;
            container.positionOnMouseDown = [8, 9];
            container.sectionOnMouseDown = 'selection_start_handle';
            requestedSection = null;
            container.onTouchStart({
                touches: [
                    { clientX: 8, clientY: 9 },
                    { clientX: 28, clientY: 29 },
                ],
            } as any);
            assert.equal(requestedSection, 'mouse-control', 'a second finger moves routing away from text handles');
            assert.equal(mouseDownCount, 0, 'the second finger never emits a selection down');
            assert.equal(transitionCleanupCount, 1, 'single-touch state is cancelled before zoom routing');
        } finally {
            app.CSections.MouseControl = previousMouseControlSection;
        }
    });

    it('finishes a cancelled pinch and resets end state for the next gesture', function() {
        setupContainerWithoutInitialRedraw();
        const container: any = app.sectionContainer;
        let pinchEndCount = 0;
        container.multiTouch = true;
        container.multiTouchEndSent = false;
        container.sectionOnMouseDown = 'OnlySection';
        container.getSectionWithName = () => ({});
        container.propagateOnMultiTouchEnd = () => pinchEndCount++;

        container.onTouchCancel({ touches: [], changedTouches: [{}] } as any);
        assert.equal(pinchEndCount, 1);
        assert.equal(container.multiTouch, false);
        assert.equal(container.multiTouchEndSent, false);

        container.multiTouch = true;
        container.sectionOnMouseDown = 'OnlySection';
        container.onTouchCancel({ touches: [], changedTouches: [{}] } as any);
        assert.equal(pinchEndCount, 2, 'a subsequent cancelled gesture is also finalized');
    });

    it('clears an unmoved pinch without trying to finish a zoom animation', function() {
        const mouseControl: any = Object.create(MouseControl.prototype);
        const previousMap = app.map;
        const previousTimerRegistry = app.timerRegistry;
        const previousLOButtons = app.LOButtons;
        let preZoomCount = 0;
        let postZoomCount = 0;
        let clearedClickTimer: any = null;
        try {
            app.timerRegistry = { clearTimeout: (id: any) => clearedClickTimer = id };
            app.map = {
                _docLayer: {
                    preZoomAnimation: () => preZoomCount++,
                    postZoomAnimation: () => postZoomCount++,
                },
            };
            mouseControl.pinchStartCenter = undefined;
            mouseControl.zoom = undefined;
            mouseControl.origCenter = undefined;
            mouseControl.pinchLength = 0;
            mouseControl.pinchPreAnimationStarted = false;
            mouseControl.pinchZoomStarted = false;
            mouseControl.inSwipeAction = false;
            mouseControl.clickTimer = 42;
            mouseControl.clickCount = 2;

            mouseControl.onMultiTouchStart({
                touches: [
                    { clientX: 10, clientY: 20 },
                    { clientX: 30, clientY: 40 },
                ],
            } as any);
            mouseControl.onMultiTouchEnd({ touches: [] } as any);
        } finally {
            app.map = previousMap;
            app.timerRegistry = previousTimerRegistry;
        }

        assert.equal(mouseControl.pinchStartCenter, undefined);
        assert.equal(mouseControl.zoom, undefined);
        assert.equal(mouseControl.origCenter, undefined);
        assert.equal(mouseControl.pinchLength, 0);
        assert.equal(preZoomCount, 0, 'a stationary two-finger touch never hides selection handles');
        assert.equal(postZoomCount, 0, 'stationary touch has no zoom cleanup to run');
        assert.equal(clearedClickTimer, 42, 'a pending click cannot fire during the two-finger gesture');
        assert.equal(mouseControl.clickCount, 0, 'a pending double-click sequence is discarded');
    });

    it('cancels an active single-touch drag without invoking section mouseup handlers', function() {
        const mouseControl: any = Object.create(MouseControl.prototype);
        const previousMap = app.map;
        const previousTimerRegistry = app.timerRegistry;
        const previousLOButtons = app.LOButtons;
        const events: string[] = [];
        const mapEvents: string[] = [];
        try {
            app.timerRegistry = { clearTimeout: () => {} };
            app.LOButtons = { left: 1 };
            app.map = { fire: (name: string) => mapEvents.push(name) };
            mouseControl.clickTimer = 1;
            mouseControl.clickCount = 2;
            mouseControl.mouseMoveTimer = 2;
            mouseControl.mouseDownSent = true;
            mouseControl.currentPosition = {};
            mouseControl.positionOnMouseDown = {};
            mouseControl.localPositionOnMouseDown = {};
            mouseControl.touchstart = 123;
            mouseControl.inSwipeAction = false;
            mouseControl.postCoreMouseEvent = (type: string) => events.push(type);

            mouseControl.cancelSingleTouchForMultiTouch();
        } finally {
            app.map = previousMap;
            app.timerRegistry = previousTimerRegistry;
            app.LOButtons = previousLOButtons;
        }

        assert.deepEqual(events, ['buttonup'], 'only a pending core buttondown is balanced');
        assert.deepEqual(mapEvents, ['scrollvelocity'], 'selection-handle edge autoscroll is stopped');
        assert.equal(mouseControl.mouseDownSent, false);
        assert.equal(mouseControl.positionOnMouseDown, null);
        assert.equal(mouseControl.localPositionOnMouseDown, null);
        assert.equal(mouseControl.clickTimer, null);
        assert.equal(mouseControl.clickCount, 0);
    });

    it('recovers the pinch overlay on cancel after movement', function() {
        const mouseControl: any = Object.create(MouseControl.prototype);
        const previousMap = app.map;
        const previousIdleHandler = app.idleHandler;
        const previousMode = (window as any).mode;
        let preZoomCount = 0;
        let postZoomCount = 0;
        let zoomEndCount = 0;
        try {
            (window as any).mode = { isMobile: () => true, isTablet: () => false };
            app.map = {
                getZoom: () => 2,
                _limitZoom: (zoom: number) => zoom,
                mouseEventToLatLng: () => ({}),
                _docLayer: {
                    preZoomAnimation: () => preZoomCount++,
                    zoomStep: () => {},
                    zoomStepEnd: (_zoom: number, _center: any, _update: any, runAtFinish: () => void) => {
                        zoomEndCount++;
                        runAtFinish();
                    },
                    postZoomAnimation: () => postZoomCount++,
                },
            };
            app.idleHandler = { notifyActive: () => {} };
            mouseControl.inSwipeAction = false;
            mouseControl.onMultiTouchStart({
                touches: [
                    { clientX: 10, clientY: 20 },
                    { clientX: 30, clientY: 40 },
                ],
            } as any);
            mouseControl.onMultiTouchMove(null, 0, {
                touches: [
                    { clientX: 5, clientY: 20 },
                    { clientX: 35, clientY: 40 },
                ],
            } as any);
            mouseControl.onMultiTouchEnd({ touches: [] } as any);
        } finally {
            app.map = previousMap;
            app.idleHandler = previousIdleHandler;
            (window as any).mode = previousMode;
        }

        assert.equal(preZoomCount, 1, 'selection handles are hidden only once movement begins');
        assert.equal(zoomEndCount, 1, 'cancel after movement runs the zoom finish path');
        assert.equal(postZoomCount, 1, 'the zoom finish restores selection handles');
        assert.equal(mouseControl.pinchStartCenter, undefined);
        assert.equal(mouseControl.pinchPreAnimationStarted, false);
    });

    it('lets a new mobile pinch take over the tile wait from the previous zoom', function() {
        const previousMode = (window as any).mode;
        const previousRequestAnimationFrame = global.requestAnimationFrame;
        const previousCancelAnimationFrame = global.cancelAnimationFrame;
        const previousGlobalApp = (global as any).app;
        const previousGlobalClamp = (global as any).clamp;
        const previousWindowL = (window as any).L;
        const previousLClass = L.Class;
        const previousLLayer = L.Layer;
        let loadedTileSource = false;
        const previousSetInZoomAnimation = app.sectionContainer.setInZoomAnimation;
        const previousSetZoomChanged = app.sectionContainer.setZoomChanged;
        try {
            (window as any).mode = { isMobile: () => true, isTablet: () => false };
            (global as any).app = app;
            if (!L.TileSectionManager) {
                const fs = require('fs');
                const vm = require('vm');
                const extendMockClass = (properties: any) => {
                    function MockClass() {}
                    MockClass.prototype = properties;
                    return MockClass;
                };
                L.Class = { extend: extendMockClass };
                L.Layer = { extend: extendMockClass };
                (window as any).L = L;
                vm.runInThisContext(fs.readFileSync('src/layer/tile/CanvasTileLayer.js', 'utf8'));
                loadedTileSource = true;
            }
            const manager: any = Object.create(L.TileSectionManager.prototype);
            let cancelledFrame: number = null;
            let animationCount = 0;
            let zoomLockCount = 0;
            manager._finishingZoom = true;
            manager._inZoomAnim = false;
            manager._waitForTiles = true;
            manager._zoomFinishRAF = 12;
            manager.setWaitForTiles = (wait: boolean) => manager._waitForTiles = wait;
            manager.waitForTiles = () => manager._waitForTiles;
            manager._calcZoomFrameParams = () => {};
            manager._zoomAnimation = () => animationCount++;
            app.sectionContainer.setInZoomAnimation = (inZoom: boolean) => {
                if (inZoom) zoomLockCount++;
            };
            app.sectionContainer.setZoomChanged = () => {};
            global.cancelAnimationFrame = (id: number) => cancelledFrame = id;
            global.requestAnimationFrame = () => 13;

            manager.zoomStep(3, {});

            assert.equal(cancelledFrame, 12, 'the previous tile wait polling frame is cancelled');
            assert.equal(manager._finishingZoom, false, 'the previous gesture no longer holds the finish lock');
            assert.equal(manager.waitForTiles(), false, 'the old tile wait is cleared');
            assert.equal(manager._inZoomAnim, true, 'the new pinch enters the zoom painter');
            assert.equal(animationCount, 1, 'the new pinch starts drawing immediately');

            // Model a second quick pinch after the first replacement zoom settles.
            manager._inZoomAnim = false;
            manager._finishingZoom = true;
            manager._waitForTiles = true;
            manager._zoomFinishRAF = 20;
            manager.zoomStep(2, {});
            assert.equal(cancelledFrame, 20, 'the next prior wait is also cancelled');
            assert.equal(manager._finishingZoom, false, 'repeated takeover releases the prior finish lock');
            assert.equal(manager.waitForTiles(), false, 'repeated takeover does not retain a tile wait');
            assert.equal(animationCount, 2, 'the next pinch starts painting immediately');
            assert.equal(zoomLockCount, 2, 'repeated pinches acquire one fresh drawing lock each');
        } finally {
            (window as any).mode = previousMode;
            global.requestAnimationFrame = previousRequestAnimationFrame;
            global.cancelAnimationFrame = previousCancelAnimationFrame;
            (global as any).app = previousGlobalApp;
            app.sectionContainer.setInZoomAnimation = previousSetInZoomAnimation;
            app.sectionContainer.setZoomChanged = previousSetZoomChanged;
            if (loadedTileSource) {
                delete L.TileSectionManager;
                delete L.CanvasTileLayer;
                delete L.MessageStore;
                L.Class = previousLClass;
                L.Layer = previousLLayer;
                (window as any).L = previousWindowL;
                if (previousGlobalClamp === undefined) delete (global as any).clamp;
                else (global as any).clamp = previousGlobalClamp;
            }
        }
    });

});

describe('Horizontally packed two section container', function() {

    beforeEach(function () {
        setupCanvasContainer(canvasWidth, canvasHeight);

        const docLayer = {};
        const tsManager = {};

        let leftSection = new CanvasSectionObject('LeftSection');
        leftSection.anchor = ['top', 'left'];
        leftSection.position = [originX, originY];
        leftSection.size = [halfWidth, 1];
        leftSection.expand = ['bottom'];
        leftSection.processingOrder = leftSection.drawingOrder = leftSection.zIndex = 1;
        leftSection.interactable = false;
        leftSection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };
        app.sectionContainer.addSection(leftSection);

        let rightSection = new CanvasSectionObject('RightSection');
        rightSection.anchor = ['top', ['LeftSection', 'right', 'left']] as (string[] | Array<Array<string>>);
        rightSection.position = [originX, originY];
        rightSection.size = [1, 1];
        rightSection.expand = ['bottom', 'right'];
        rightSection.processingOrder = rightSection.drawingOrder = 2;
        rightSection.zIndex = 1;
        rightSection.interactable = false;
        rightSection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };
        app.sectionContainer.addSection(rightSection);

        app.sectionContainer.enableDrawing();
    });

    it('Container should have LeftSection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('LeftSection'));
    });

    it('Container should have RightSection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('RightSection'));
    });

    it('LeftSection PosSize checks', function () {
        const left = app.sectionContainer.getSectionWithName('LeftSection');
        const leftRect = getSectionRectangle(left);
        assertPosSize(leftRect,
            {
                x: originX,
                y: originY,
                width: halfWidth,
                height: canvasHeight - originY
            });
    });

    it('RightSection PosSize checks', function () {
        const right = app.sectionContainer.getSectionWithName('RightSection');
        const rightRect = getSectionRectangle(right);
        assertPosSize(rightRect,
            {
                x: 1 + halfWidth + originX,
                y: originY,
                width: halfWidth - 1 - originX,
                height: canvasHeight - originY
            });
    });
});

describe('Vertically packed two section container', function() {

    beforeEach(function () {
        setupCanvasContainer(canvasWidth, canvasHeight);

        const docLayer = {};
        const tsManager = {};

        let topSection = new CanvasSectionObject('TopSection');
        topSection.anchor = ['top', 'left'];
        topSection.position = [originX, originY];
        topSection.size = [1, halfHeight];
        topSection.expand = ['right'];
        topSection.processingOrder = topSection.drawingOrder = topSection.zIndex = 1;
        topSection.interactable = false;
        topSection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };
        app.sectionContainer.addSection(topSection);

        let bottomSection = new CanvasSectionObject('BottomSection');
        bottomSection.anchor = [['TopSection', 'bottom', 'top'], 'left'] as (string[] | Array<Array<string>>);
        bottomSection.position = [originX, originY];
        bottomSection.size = [1, 1];
        bottomSection.expand = ['bottom', 'right'];
        bottomSection.processingOrder = bottomSection.drawingOrder = 2;
        bottomSection.zIndex = 1;
        bottomSection.interactable = false;
        bottomSection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };
        app.sectionContainer.addSection(bottomSection);

        app.sectionContainer.enableDrawing();
    });

    it('Container should have TopSection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('TopSection'));
    });

    it('Container should have BottomSection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('BottomSection'));
    });

    it('TopSection PosSize checks', function () {
        const top = app.sectionContainer.getSectionWithName('TopSection');
        const topRect = getSectionRectangle(top);
        assertPosSize(topRect,
            {
                x: originX,
                y: originY,
                width: canvasWidth - originX,
                height: halfHeight
            });
    });

    it('BottomSection PosSize checks', function () {
        const bottom = app.sectionContainer.getSectionWithName('BottomSection');
        const bottomRect = getSectionRectangle(bottom);
        assertPosSize(bottomRect,
            {
                x: originX,
                y: 1 + halfHeight + originY,
                width: canvasWidth - originX,
                height: halfHeight - 1 - originY
            });
    });
});

// '-left' layout is usually used for RTL where it is used to attach a section's right to the left of another section.
describe('Horizontally packed two section container with -left layout', function() {

    beforeEach(function () {
        setupCanvasContainer(canvasWidth, canvasHeight);

        const docLayer = {};
        const tsManager = {};

        let rightSection = new CanvasSectionObject('RightSection');
        rightSection.anchor = ['top', 'right'];
        rightSection.position = [originX, originY];
        rightSection.size = [halfWidth, 1];
        rightSection.expand = ['bottom'];
        rightSection.processingOrder = rightSection.drawingOrder = rightSection.zIndex = 1;
        rightSection.interactable = false;
        rightSection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };
        app.sectionContainer.addSection(rightSection);

        let leftSection = new CanvasSectionObject('LeftSection');
        leftSection.anchor = ['top', ['RightSection', '-left', 'right']] as (string[] | Array<Array<string>>); // Attach LeftSection's right to left of RightSection.
        leftSection.position = [originX, originY];
        leftSection.size = [1, 1];
        leftSection.expand = ['bottom', 'left'];
        leftSection.processingOrder = leftSection.drawingOrder = 2;
        leftSection.zIndex = 1;
        leftSection.interactable = false;
        leftSection.sectionProperties = {
            docLayer: docLayer,
            tsManager: tsManager,
            strokeStyle: '#c0c0c0'
        };
        app.sectionContainer.addSection(leftSection);

        app.sectionContainer.enableDrawing();
    });


    it('Container should have LeftSection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('LeftSection'));
    });

    it('Container should have RightSection', function() {
        assert.ok(app.sectionContainer.doesSectionExist('RightSection'));
    });

    it('LeftSection PosSize checks', function () {
        const left = app.sectionContainer.getSectionWithName('LeftSection');
        const leftRect = getSectionRectangle(left);
        assertPosSize(leftRect,
            {
                x: 0,
                y: originY,
                width: halfWidth - 1,
                height: canvasHeight - originY
            });
    });

    it('RightSection PosSize checks', function () {
        const right = app.sectionContainer.getSectionWithName('RightSection');
        const rightRect = getSectionRectangle(right);
        assertPosSize(rightRect,
            {
                x: canvasWidth - originX - halfWidth,
                y: originY,
                width: halfWidth,
                height: canvasHeight - originY
            });
    });
});
});
