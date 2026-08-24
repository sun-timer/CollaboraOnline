package org.libreoffice.androidapp.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.BottomSheetAnchorHelper;

/**
 * Window size class helpers for phone / tablet layouts.
 * Compact: sw &lt; 600dp, Medium: 600–839dp, Expanded: sw &gt;= 840dp.
 */
public final class ResponsiveUiHelper {
    private static final int COMPACT_WIDTH_SW_DP = 600;
    private static final int EXPANDED_WIDTH_SW_DP = 840;

    private ResponsiveUiHelper() {
    }

    public static boolean isCompactWidth(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp < COMPACT_WIDTH_SW_DP;
    }

    public static boolean isMediumWidth(Context context) {
        int sw = context.getResources().getConfiguration().smallestScreenWidthDp;
        return sw >= COMPACT_WIDTH_SW_DP && sw < EXPANDED_WIDTH_SW_DP;
    }

    public static boolean isExpandedWidth(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp >= EXPANDED_WIDTH_SW_DP;
    }

    /** Bottom sheet on phones; centered dialog card on tablets. */
    public static boolean useBottomSheetPresentation(Context context) {
        return isCompactWidth(context);
    }

    public static void applyOverlayDialogWindow(Dialog dialog) {
        applyOverlayDialogWindow(dialog == null ? null : dialog.getWindow());
    }

    public static void applyOverlayDialogWindow(Window window) {
        if (window == null) {
            return;
        }
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.CENTER);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.3f;
        window.setAttributes(params);
    }

    public static void applyBottomSheetWindow(Window window) {
        if (window == null) {
            return;
        }
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.BOTTOM);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.3f;
        window.setAttributes(params);
    }

    /**
     * Sheet stays flush to screen bottom; only the white card gains internal bottom padding
     * so action buttons clear nav / gesture area (no external white strip).
     */
    public static void applyBottomSheetContentSafePadding(View sheetContent, int designBottomPadPx) {
        BottomSheetAnchorHelper.installInternalBottomSafePadding(sheetContent, designBottomPadPx);
    }

    public static void applyAdaptiveSheetWindow(Context context, Window window) {
        if (useBottomSheetPresentation(context)) {
            applyBottomSheetWindow(window);
        } else {
            applyOverlayDialogWindow(window);
        }
    }

    public static void applyAdaptiveSheetWindow(Context context, Dialog dialog) {
        applyAdaptiveSheetWindow(context, dialog == null ? null : dialog.getWindow());
    }

    public static void applyKeyboardFriendlyDialogWindow(Dialog dialog) {
        applyOverlayDialogWindow(dialog);
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    public static void applyAdaptiveSheetCardLayout(Context context, View card) {
        if (card == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = card.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
        if (useBottomSheetPresentation(context)) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.BOTTOM;
        } else {
            params.width = resolveMaxContentWidthPx(context, R.dimen.bottom_sheet_max_width);
            params.gravity = Gravity.CENTER;
        }
        card.setLayoutParams(params);
    }

    public static void applyAdaptiveBottomSheetLayout(Context context, View sheet) {
        if (sheet == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = sheet.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
        if (useBottomSheetPresentation(context)) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.BOTTOM;
        } else {
            params.width = resolveMaxContentWidthPx(context, R.dimen.bottom_sheet_max_width);
            params.gravity = Gravity.CENTER;
        }
        sheet.setLayoutParams(params);
    }

    public static void applyDialogCardMaxWidth(Context context, View card) {
        if (card == null) {
            return;
        }
        int maxWidthPx = resolveMaxContentWidthPx(context, R.dimen.dialog_content_max_width);
        ViewGroup.LayoutParams layoutParams = card.getLayoutParams();
        if (layoutParams == null) {
            return;
        }
        layoutParams.width = maxWidthPx;
        card.setLayoutParams(layoutParams);
    }

    private static int resolveMaxContentWidthPx(Context context, int maxWidthDimenResId) {
        int maxWidthPx = context.getResources().getDimensionPixelSize(maxWidthDimenResId);
        int shellPaddingPx = context.getResources().getDimensionPixelSize(
                R.dimen.dialog_shell_padding_horizontal) * 2;
        int screenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        return Math.min(maxWidthPx, Math.max(0, screenWidthPx - shellPaddingPx));
    }
}
