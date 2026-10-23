/* -*- tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.libreoffice.androidlib;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Magnifier;

import androidx.annotation.NonNull;

public class COWebView extends WebView {
    private static final String TAG = "COWebView";

    public interface OnDocumentLongPressListener {
        boolean isWriterDocument();

        void onDocumentLongPress(float viewX, float viewY);

        void onDocumentSelectionDrag(float viewX, float viewY);

        void onDocumentSelectionDragEnd(float viewX, float viewY);

        void onDocumentSelectionDragCancel();
    }

    public interface OnDocumentTapListener {
        void onDocumentTap(float viewX, float viewY);
    }

    private Context mContext;
    private boolean documentGestureGuardEnabled = false;
    private boolean consumeWebViewLongClick = false;
    private final GestureDetector documentGestureDetector;
    private OnDocumentLongPressListener documentLongPressListener;
    private Magnifier documentMagnifier;
    private long touchDownAt = 0L;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private boolean magnifierShown = false;
    private volatile boolean slideSorterGestureActive = false;
    private boolean nativeSelectionDragActive = false;
    private boolean writerSelectionHandleTouchActive = false;
    private boolean suppressDocumentTapUntilGestureEnd = false;
    private final RectF[] writerSelectionHandleRects = {new RectF(), new RectF()};
    private float writerSelectionViewportWidth;
    private float writerSelectionViewportHeight;
    private float writerSelectionViewportOffsetLeft;
    private float writerSelectionViewportOffsetTop;
    private String writerSelectionSession = "";
    private long writerSelectionHandleEpoch = -1L;
    private long lastNativeSelectionDragAt = 0L;
    /** When false, WebView is not treated as a text editor and IME won't auto-popup on tap.
     *  LOActivity toggles this only transiently so Calc canvas taps still select cells. */
    private boolean imeAllowedByUser = false;
    private boolean imeAllowedBeforeDocumentTouch = false;
    private long documentTouchGeneration = 0L;
    private Runnable touchEndImeRestoreCallback = null;
    private OnDocumentTapListener documentTapListener;
    private float documentTapDownX = 0f;
    private float documentTapDownY = 0f;
    private long documentTapDownAt = 0L;
    private static final float MAGNIFIER_MOVE_THRESHOLD_PX = 12f;
    private static final long NATIVE_SELECTION_DRAG_THROTTLE_MS = 60L;
    private static final long DOCUMENT_TAP_MAX_DURATION_MS = 450L;
    private static final float DOCUMENT_TAP_SLOP_PX = 24f;

    public COWebView(Context context) {
        super(context);
        mContext = context;
        documentGestureDetector = createDocumentGestureDetector(context);
        setWebViewClient(new COWebViewClient());
        initLongClickHandling();
    }

    public COWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        documentGestureDetector = createDocumentGestureDetector(context);
        setWebViewClient(new COWebViewClient());
        initLongClickHandling();
    }

    public COWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        documentGestureDetector = createDocumentGestureDetector(context);
        setWebViewClient(new COWebViewClient());
        initLongClickHandling();
    }

    public COWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        documentGestureDetector = createDocumentGestureDetector(context);
        setWebViewClient(new COWebViewClient());
        initLongClickHandling();
    }

    private void initLongClickHandling() {
        setOnLongClickListener(v -> consumeWebViewLongClick && documentLongPressListener != null);
    }

    private GestureDetector createDocumentGestureDetector(Context context) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (slideSorterGestureActive) {
                    Log.i(TAG, "native_long_press skipped slide_sorter_gesture");
                    return;
                }
                if (consumeWebViewLongClick && documentLongPressListener != null) {
                    nativeSelectionDragActive = true;
                    lastNativeSelectionDragAt = 0L;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            && documentLongPressListener.isWriterDocument()) {
                        getDocumentMagnifier().show(e.getX(), e.getY());
                        magnifierShown = true;
                    }
                    Log.i(TAG, "native_selection_drag start consumed=true");
                    documentLongPressListener.onDocumentLongPress(e.getX(), e.getY());
                }
            }
        });
    }

    public void setOnDocumentLongPressListener(OnDocumentLongPressListener listener) {
        documentLongPressListener = listener;
    }

    public void setOnDocumentTapListener(OnDocumentTapListener listener) {
        documentTapListener = listener;
    }

    /** Reset cached handles and touch ownership before each WebView document session. */
    public synchronized void setWriterSelectionSession(String session) {
        if (session == null || session.isEmpty() || session.equals(writerSelectionSession)) return;
        writerSelectionSession = session;
        writerSelectionHandleEpoch = -1L;
        writerSelectionViewportWidth = 0f;
        writerSelectionViewportHeight = 0f;
        writerSelectionViewportOffsetLeft = 0f;
        writerSelectionViewportOffsetTop = 0f;
        for (RectF rect : writerSelectionHandleRects) rect.setEmpty();
        writerSelectionHandleTouchActive = false;
        suppressDocumentTapUntilGestureEnd = false;
        nativeSelectionDragActive = false;
        hideDocumentMagnifier();
    }

    /** Cache DOM handle bounds in CSS viewport coordinates for synchronous ACTION_DOWN hit testing. */
    public synchronized void setWriterSelectionHandleRects(String session, float[] rects,
            float viewportWidth, float viewportHeight, float viewportOffsetLeft,
            float viewportOffsetTop, long epoch) {
        if (rects == null || rects.length != 8 || viewportWidth <= 0f || viewportHeight <= 0f
                || session == null || !session.equals(writerSelectionSession)
                || epoch < writerSelectionHandleEpoch) {
            return;
        }
        writerSelectionHandleEpoch = epoch;
        writerSelectionViewportWidth = viewportWidth;
        writerSelectionViewportHeight = viewportHeight;
        writerSelectionViewportOffsetLeft = viewportOffsetLeft;
        writerSelectionViewportOffsetTop = viewportOffsetTop;
        for (int i = 0; i < writerSelectionHandleRects.length; i++) {
            int offset = i * 4;
            RectF rect = writerSelectionHandleRects[i];
            rect.set(rects[offset], rects[offset + 1], rects[offset + 2], rects[offset + 3]);
        }
    }

    /**
     * When true (preview mode), consume WebView long-click so the browser does not
     * emit a right-click (buttons=4) and route long-press to the native bridge.
     */
    public void setConsumeWebViewLongClick(boolean consume) {
        consumeWebViewLongClick = consume;
    }

    /**
     * Only report as text editor after native code allows a confirmed text-caret tap
     * or while the existing document input connection is active.
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return imeAllowedByUser;
    }

    public void setImeAllowedByUser(boolean allowed) {
        imeAllowedByUser = allowed;
        if (!allowed) {
            imeAllowedBeforeDocumentTouch = false;
        }
    }

    public boolean isImeAllowedByUser() {
        return imeAllowedByUser;
    }

    /** Monotonic native touch generation used to reject stale asynchronous IME requests. */
    public long getDocumentTouchGeneration() {
        return documentTouchGeneration;
    }

    /** Called after a document touch ends to restore IME editor mode if keyboard stays open. */
    public void setTouchEndImeRestoreCallback(Runnable callback) {
        touchEndImeRestoreCallback = callback;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // TODO FIXME
        //return new BaseInputConnection(this, false);
        // ^ caused a regression on inserting special characters like Non-English ones

        return super.onCreateInputConnection(outAttrs);
    }

    @NonNull
    @Override
    public COWebViewClient getWebViewClient() {
        return (COWebViewClient) super.getWebViewClient();
    }

    public void setDocumentGestureGuardEnabled(boolean enabled) {
        if (documentGestureGuardEnabled == enabled) {
            return;
        }
        documentGestureGuardEnabled = enabled;
        if (enabled) {
            abortDocumentScroll();
        }
    }

    public boolean isDocumentGestureGuardEnabled() {
        return documentGestureGuardEnabled;
    }

    public void setSlideSorterGestureActive(boolean active) {
        slideSorterGestureActive = active;
        if (active) {
            hideDocumentMagnifier();
            if (nativeSelectionDragActive && documentLongPressListener != null) {
                documentLongPressListener.onDocumentSelectionDragCancel();
            }
            nativeSelectionDragActive = false;
        }
    }

    public boolean isSlideSorterGestureActive() {
        return slideSorterGestureActive;
    }

    public void abortDocumentScroll() {
        stopNestedScroll();
        post(() -> {
            if (documentGestureGuardEnabled) {
                flingScroll(0, 0);
            }
            scrollTo(getScrollX(), getScrollY());
        });
    }

    @Override
    public void flingScroll(int vx, int vy) {
        if (documentGestureGuardEnabled) {
            return;
        }
        super.flingScroll(vx, vy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            suppressDocumentTapUntilGestureEnd = false;
        }
        if (action == MotionEvent.ACTION_DOWN && hitWriterSelectionHandle(event.getX(), event.getY())) {
            writerSelectionHandleTouchActive = true;
            suppressDocumentTapUntilGestureEnd = true;
            nativeSelectionDragActive = false;
            hideDocumentMagnifier();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getDocumentMagnifier().show(event.getX(), selectionHandleMagnifierY(event.getY()));
                magnifierShown = true;
            }
            Log.i(TAG, "writer_selection_handle_touch start consumed=true");
        }
        if (writerSelectionHandleTouchActive) {
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                // The second finger changes this into a pinch. Drop handle
                // ownership and magnification, then let WebView receive the
                // pointer event and all subsequent motion for its normal zoom path.
                writerSelectionHandleTouchActive = false;
                hideDocumentMagnifier();
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                documentGestureDetector.onTouchEvent(cancel);
                cancel.recycle();
                Log.i(TAG, "writer_selection_handle_touch handoff=pinch");
            } else {
                // Let WebView deliver the entire stream to the DOM handle. Bypass the
                // document long-press/tap state machines, which otherwise race it.
                if (action == MotionEvent.ACTION_MOVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    getDocumentMagnifier().show(event.getX(), selectionHandleMagnifierY(event.getY()));
                }
                super.onTouchEvent(event);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    writerSelectionHandleTouchActive = false;
                    suppressDocumentTapUntilGestureEnd = false;
                    hideDocumentMagnifier();
                    Log.i(TAG, "writer_selection_handle_touch end consumed=true");
                }
                return true;
            }
        }
        documentGestureDetector.onTouchEvent(event);
        updateDocumentMagnifier(event);
        boolean handledNativeSelection = updateNativeSelectionDrag(event);

        if (action == MotionEvent.ACTION_DOWN) {
            documentTouchGeneration++;
            documentTapDownX = event.getX();
            documentTapDownY = event.getY();
            documentTapDownAt = event.getEventTime();
            // Drop View-level editor mode for this gesture so canvas taps can switch cells
            // even when the soft keyboard is open. IME text commits still flow via the
            // focused contenteditable inside the WebView.
            if (imeAllowedByUser) {
                imeAllowedBeforeDocumentTouch = true;
                imeAllowedByUser = false;
            } else {
                imeAllowedBeforeDocumentTouch = false;
            }
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && imeAllowedBeforeDocumentTouch) {
            imeAllowedBeforeDocumentTouch = false;
            if (touchEndImeRestoreCallback != null) {
                touchEndImeRestoreCallback.run();
            }
        }

        if (consumeWebViewLongClick && handledNativeSelection) {
            if (documentGestureGuardEnabled &&
                    (event.getActionMasked() == MotionEvent.ACTION_MOVE ||
                            event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                abortDocumentScroll();
            }
            return true;
        }

        boolean handled = super.onTouchEvent(event);

        if (action == MotionEvent.ACTION_UP && documentTapListener != null && !nativeSelectionDragActive
                && !suppressDocumentTapUntilGestureEnd) {
            long duration = event.getEventTime() - documentTapDownAt;
            float dx = event.getX() - documentTapDownX;
            float dy = event.getY() - documentTapDownY;
            if (duration <= DOCUMENT_TAP_MAX_DURATION_MS
                    && (dx * dx + dy * dy) <= (DOCUMENT_TAP_SLOP_PX * DOCUMENT_TAP_SLOP_PX)) {
                final float tapX = event.getX();
                final float tapY = event.getY();
                post(() -> documentTapListener.onDocumentTap(tapX, tapY));
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            suppressDocumentTapUntilGestureEnd = false;
        }

        if (!documentGestureGuardEnabled) {
            return handled;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE ||
                event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            abortDocumentScroll();
        }
        return handled;
    }

    private boolean updateNativeSelectionDrag(MotionEvent event) {
        if (documentLongPressListener == null) {
            return false;
        }
        if (slideSorterGestureActive) {
            if (nativeSelectionDragActive) {
                nativeSelectionDragActive = false;
                documentLongPressListener.onDocumentSelectionDragCancel();
                Log.i(TAG, "native_selection_drag cancel slide_sorter_gesture");
            }
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (nativeSelectionDragActive) {
                    nativeSelectionDragActive = false;
                    documentLongPressListener.onDocumentSelectionDragCancel();
                    Log.i(TAG, "native_selection_drag down_cancel consumed=false");
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!nativeSelectionDragActive) {
                    return false;
                }
                if (event.getPointerCount() == 1) {
                    long now = event.getEventTime();
                    if (now - lastNativeSelectionDragAt < NATIVE_SELECTION_DRAG_THROTTLE_MS) {
                        return true;
                    }
                    lastNativeSelectionDragAt = now;
                    documentLongPressListener.onDocumentSelectionDrag(event.getX(), event.getY());
                }
                Log.i(TAG, "native_selection_drag move consumed=true");
                return true;
            case MotionEvent.ACTION_UP:
                if (!nativeSelectionDragActive) {
                    return false;
                }
                nativeSelectionDragActive = false;
                documentLongPressListener.onDocumentSelectionDragEnd(event.getX(), event.getY());
                Log.i(TAG, "native_selection_drag end consumed=true");
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!nativeSelectionDragActive) {
                    return false;
                }
                nativeSelectionDragActive = false;
                documentLongPressListener.onDocumentSelectionDragCancel();
                Log.i(TAG, "native_selection_drag cancel consumed=false");
                return false;
            default:
                break;
        }
        return false;
    }

    private void updateDocumentMagnifier(MotionEvent event) {
        if (slideSorterGestureActive) {
            hideDocumentMagnifier();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || event.getPointerCount() != 1) {
            hideDocumentMagnifier();
            return;
        }

        boolean writerDocument = documentLongPressListener != null
                && documentLongPressListener.isWriterDocument();
        if (writerDocument && !nativeSelectionDragActive) {
            hideDocumentMagnifier();
            return;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownAt = event.getEventTime();
                touchDownX = event.getX();
                touchDownY = event.getY();
                magnifierShown = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (shouldShowMagnifier(event)) {
                    getDocumentMagnifier().show(event.getX(), event.getY());
                    magnifierShown = true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                hideDocumentMagnifier();
                break;
            default:
                break;
        }
    }

    private synchronized boolean hitWriterSelectionHandle(float viewX, float viewY) {
        if (writerSelectionViewportWidth <= 0f || writerSelectionViewportHeight <= 0f
                || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        float cssX = viewX * writerSelectionViewportWidth / getWidth()
                + writerSelectionViewportOffsetLeft;
        float cssY = viewY * writerSelectionViewportHeight / getHeight()
                + writerSelectionViewportOffsetTop;
        for (RectF rect : writerSelectionHandleRects) {
            if (!rect.isEmpty() && rect.contains(cssX, cssY)) {
                return true;
            }
        }
        return false;
    }

    private float selectionHandleMagnifierY(float viewY) {
        if (writerSelectionViewportHeight <= 0f || getHeight() <= 0) {
            return viewY;
        }
        return viewY - 22f * getHeight() / writerSelectionViewportHeight;
    }

    private boolean shouldShowMagnifier(MotionEvent event) {
        if (magnifierShown) {
            return true;
        }

        long duration = event.getEventTime() - touchDownAt;
        float dx = event.getX() - touchDownX;
        float dy = event.getY() - touchDownY;
        float distanceSquared = dx * dx + dy * dy;
        return duration >= ViewConfiguration.getLongPressTimeout() ||
                distanceSquared >= MAGNIFIER_MOVE_THRESHOLD_PX * MAGNIFIER_MOVE_THRESHOLD_PX;
    }

    private Magnifier getDocumentMagnifier() {
        if (documentMagnifier == null) {
            documentMagnifier = new Magnifier(this);
        }
        return documentMagnifier;
    }

    private void hideDocumentMagnifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && documentMagnifier != null) {
            documentMagnifier.dismiss();
        }
        magnifierShown = false;
    }
}
