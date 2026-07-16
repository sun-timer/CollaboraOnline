package org.libreoffice.androidlib.ai;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * AI 功能弹窗统一行为：仅右上角关闭按钮可 dismiss，点击外部不关闭。
 */
public final class AiDialogHelper {

    private AiDialogHelper() {
    }

    /** AlertDialog：禁止返回键/外部点击关闭（须显式点关闭按钮）。 */
    public static void applyCloseOnlyDismiss(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    /** BottomSheet：禁止点击外部关闭。 */
    public static void applyCloseOnlyDismiss(BottomSheetDialog dialog) {
        if (dialog == null) {
            return;
        }
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    /** 透明圆角背景，配合 lolib_bg_dialog_outline 根布局。 */
    public static void applyTransparentWindow(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    /**
     * 约束宽度、高度随内容（上限 maxHeightPx），避免固定高度裁切底部按钮。
     */
    public static void applyFlexibleWidth(android.view.View root, AlertDialog dialog,
                                          int targetWidthPx, int maxHeightPx) {
        if (dialog == null || dialog.getWindow() == null || root == null) {
            return;
        }
        int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(targetWidthPx,
                android.view.View.MeasureSpec.EXACTLY);
        int heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0,
                android.view.View.MeasureSpec.UNSPECIFIED);
        root.measure(widthSpec, heightSpec);
        int contentHeight = root.getMeasuredHeight();
        int windowHeight = Math.min(Math.max(contentHeight, 1), maxHeightPx);

        dialog.getWindow().setLayout(targetWidthPx, windowHeight);
        android.view.ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp == null) {
            lp = new android.view.ViewGroup.LayoutParams(targetWidthPx,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            lp.width = targetWidthPx;
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        root.setLayoutParams(lp);
    }
}
