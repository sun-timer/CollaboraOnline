package org.libreoffice.androidlib;

import android.view.View;

/**
 * Resolves bottom reserved space and clamps floating overlays above the document chrome.
 */
public final class DocumentOverlayInsets {

    private DocumentOverlayInsets() {
    }

    /**
     * Vertical space occupied from the parent bottom (bottom toolbar, IME lift, nav inset).
     */
    public static int resolveBottomReservedPx(View parent, View bottomToolbar, int fallbackPx) {
        if (parent != null && bottomToolbar != null && bottomToolbar.getVisibility() == View.VISIBLE) {
            int toolbarTop = bottomToolbar.getTop();
            if (toolbarTop > 0) {
                return Math.max(fallbackPx, parent.getHeight() - toolbarTop);
            }
        }
        return Math.max(0, fallbackPx);
    }

    /** Clamp overlay top edge in parent coordinates. */
    public static float clampTopInParent(float top, float overlayHeight, int parentHeight,
            float marginPx, int bottomReservedPx) {
        float maxTop = parentHeight - bottomReservedPx - overlayHeight - marginPx;
        return Math.max(marginPx, Math.min(top, maxTop));
    }

    /** Clamp overlay top edge when margins are expressed in window coordinates. */
    public static float clampTopInWindow(float top, float overlayHeight, View bottomToolbar,
            float gapPx, float minTopPx) {
        float clamped = top;
        if (bottomToolbar != null && bottomToolbar.getVisibility() == View.VISIBLE) {
            int[] loc = new int[2];
            bottomToolbar.getLocationInWindow(loc);
            float maxBottom = loc[1] - gapPx;
            clamped = Math.min(clamped, maxBottom - overlayHeight);
        }
        return Math.max(minTopPx, clamped);
    }
}
