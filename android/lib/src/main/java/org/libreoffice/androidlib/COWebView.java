/* -*- tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.libreoffice.androidlib;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

public class COWebView extends WebView {
    private Context mContext;
    private boolean documentGestureGuardEnabled = false;

    public COWebView(Context context) {
        super(context);
        mContext = context;
        setWebViewClient(new COWebViewClient());
    }

    public COWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWebViewClient(new COWebViewClient());
    }

    public COWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWebViewClient(new COWebViewClient());
    }

    public COWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWebViewClient(new COWebViewClient());
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
        if (!documentGestureGuardEnabled) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_CANCEL:
                abortDocumentScroll();
                return super.onTouchEvent(event);
            default:
                return super.onTouchEvent(event);
        }
    }
}
