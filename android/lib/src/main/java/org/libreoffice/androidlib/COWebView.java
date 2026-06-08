/* -*- tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.libreoffice.androidlib;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
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
    public interface OnDocumentLongPressListener {
        void onDocumentLongPress(float viewX, float viewY);

        void onDocumentSelectionDrag(float viewX, float viewY);

        void onDocumentSelectionDragEnd(float viewX, float viewY);

        void onDocumentSelectionDragCancel();
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
    private boolean nativeSelectionDragActive = false;
    private long lastNativeSelectionDragAt = 0L;
    private static final float MAGNIFIER_MOVE_THRESHOLD_PX = 12f;
    private static final long NATIVE_SELECTION_DRAG_THROTTLE_MS = 60L;

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
                if (consumeWebViewLongClick && documentLongPressListener != null) {
                    nativeSelectionDragActive = true;
                    lastNativeSelectionDragAt = 0L;
                    documentLongPressListener.onDocumentLongPress(e.getX(), e.getY());
                }
            }
        });
    }

    public void setOnDocumentLongPressListener(OnDocumentLongPressListener listener) {
        documentLongPressListener = listener;
    }

    /**
     * When true (preview mode), consume WebView long-click so the browser does not
     * emit a right-click (buttons=4) and route long-press to the native bridge.
     */
    public void setConsumeWebViewLongClick(boolean consume) {
        consumeWebViewLongClick = consume;
    }

    /**
     * Always return true so the WebView keeps full editing capability
     * (input[type=file], contentEditable, text selection, etc.).
     * IME show/hide is controlled via InputMethodManager from LOActivity.
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return true;
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
        documentGestureDetector.onTouchEvent(event);
        updateDocumentMagnifier(event);
        updateNativeSelectionDrag(event);

        boolean handled = super.onTouchEvent(event);
        if (!documentGestureGuardEnabled) {
            return handled;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE ||
                event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            abortDocumentScroll();
        }
        return handled;
    }

    private void updateNativeSelectionDrag(MotionEvent event) {
        if (documentLongPressListener == null) {
            return;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (nativeSelectionDragActive) {
                    nativeSelectionDragActive = false;
                    documentLongPressListener.onDocumentSelectionDragCancel();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!nativeSelectionDragActive) {
                    return;
                }
                if (event.getPointerCount() == 1) {
                    long now = event.getEventTime();
                    if (now - lastNativeSelectionDragAt < NATIVE_SELECTION_DRAG_THROTTLE_MS) {
                        return;
                    }
                    lastNativeSelectionDragAt = now;
                    documentLongPressListener.onDocumentSelectionDrag(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!nativeSelectionDragActive) {
                    return;
                }
                nativeSelectionDragActive = false;
                documentLongPressListener.onDocumentSelectionDragEnd(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_CANCEL:
                if (!nativeSelectionDragActive) {
                    return;
                }
                nativeSelectionDragActive = false;
                documentLongPressListener.onDocumentSelectionDragCancel();
                break;
            default:
                break;
        }
    }

    private void updateDocumentMagnifier(MotionEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || event.getPointerCount() != 1) {
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
