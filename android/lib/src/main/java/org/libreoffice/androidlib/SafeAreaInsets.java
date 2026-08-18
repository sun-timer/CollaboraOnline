package org.libreoffice.androidlib;

import android.content.Context;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

/**
 * Content safe-area snapshot derived from {@link WindowInsetsCompat}.
 * Design toolbar heights stay fixed; these insets describe system-obscured regions.
 */
public final class SafeAreaInsets {
    public static final SafeAreaInsets EMPTY = new SafeAreaInsets(0, 0, 0, 0, false, 0, 0);

    public final int top;
    public final int left;
    public final int right;
    /** Bottom obstruction for floating UI: IME when visible, else navigation + safe margin. */
    public final int bottom;
    public final boolean imeVisible;
    public final int imeBottom;
    /** Navigation / gesture / tappable / cutout bottom without IME override. */
    public final int navigationBottom;

    public SafeAreaInsets(int top, int left, int right, int bottom,
            boolean imeVisible, int imeBottom, int navigationBottom) {
        this.top = Math.max(0, top);
        this.left = Math.max(0, left);
        this.right = Math.max(0, right);
        this.bottom = Math.max(0, bottom);
        this.imeVisible = imeVisible;
        this.imeBottom = Math.max(0, imeBottom);
        this.navigationBottom = Math.max(0, navigationBottom);
    }

    public static SafeAreaInsets from(Context context, WindowInsetsCompat insets) {
        if (insets == null) {
            return EMPTY;
        }
        Insets statusCutout = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
        int imeBottom = imeVisible ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
        int navigationBottom = SystemUiHelper.resolveNavigationBottomInset(context, insets);
        int bottom = imeVisible && imeBottom > 0
                ? imeBottom
                : navigationBottom + SystemUiHelper.getBottomSafeExtraPx(context);
        int top = statusCutout.top + SystemUiHelper.getTopSafeExtraPx(context);
        return new SafeAreaInsets(top, systemBars.left, systemBars.right, bottom,
                imeVisible, imeBottom, navigationBottom);
    }

    /** Space to reserve below a floating view sitting above the bottom toolbar. */
    public int reservedBottomAboveToolbar(int toolbarHeightPx, int toolbarBottomMarginPx, int clearancePx) {
        return Math.max(0, toolbarBottomMarginPx)
                + Math.max(0, toolbarHeightPx)
                + Math.max(0, clearancePx);
    }
}
