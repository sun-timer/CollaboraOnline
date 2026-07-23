package org.libreoffice.androidlib.ai;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * AI 功能弹窗统一行为：仅右上角关闭按钮可 dismiss，点击外部不关闭。
 * 尺寸规范（设计稿 Hug）：宽 min(670dp, 可用宽-48dp)，输入态高上限 637dp，内容/结果态上限 748dp。
 */
public final class AiDialogHelper {

    /** 设计稿弹窗宽度上限。 */
    public static final int WIDTH_DP = 670;
    /** 左右留白。 */
    public static final int MARGIN_DP = 48;
    /** 输入/简单弹窗高度上限（Hug）。 */
    public static final int MAX_HEIGHT_HUG_DP = 637;
    /** 带滚动内容的结果态高度上限。 */
    public static final int MAX_HEIGHT_CONTENT_DP = 748;
    /** 列表/大纲等多内容态占屏高比例上限。 */
    public static final float MAX_HEIGHT_SCREEN_RATIO = 0.60f;
    public static final int MIN_WIDTH_DP = 280;
    public static final int MIN_HEIGHT_DP = 180;

    private AiDialogHelper() {
    }

    public static int dpToPx(Resources res, int dp) {
        return (int) (dp * res.getDisplayMetrics().density + 0.5f);
    }

    /** 基于屏宽计算目标弹窗宽度。 */
    public static int computeTargetWidthPx(Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        return computeTargetWidthPx(res, dm.widthPixels);
    }

    /** 基于可用宽度计算目标弹窗宽度（overlay 居中面板用 parent 宽）。 */
    public static int computeTargetWidthPx(Resources res, int availableWidthPx) {
        int margin = dpToPx(res, MARGIN_DP);
        int maxW = dpToPx(res, WIDTH_DP);
        int targetWidth = Math.min(maxW, availableWidthPx - margin);
        return Math.max(targetWidth, dpToPx(res, MIN_WIDTH_DP));
    }

    /** 输入/简单弹窗（Hug）高度上限。 */
    public static int computeMaxHeightHugPx(Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        int maxHeight = dpToPx(res, MAX_HEIGHT_HUG_DP);
        maxHeight = Math.min(maxHeight, dm.heightPixels - dpToPx(res, 24));
        return Math.max(maxHeight, dpToPx(res, MIN_HEIGHT_DP));
    }

    /** 结果/滚动内容弹窗高度上限（较 Hug 略高，但仍低于旧 80% 屏高）。 */
    public static int computeMaxHeightContentPx(Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        int maxHeight = Math.min(dpToPx(res, MAX_HEIGHT_CONTENT_DP),
                (int) (dm.heightPixels * MAX_HEIGHT_SCREEN_RATIO));
        maxHeight = Math.min(maxHeight, dm.heightPixels - dpToPx(res, 24));
        return Math.max(maxHeight, dpToPx(res, 320));
    }

    /** overlay 居中面板高度：内容多时可占屏 60%，否则用 Hug 上限。 */
    public static int computeOverlayPanelHeightPx(Resources res, int parentHeight, boolean contentHeavy) {
        if (parentHeight <= 0) {
            return contentHeavy ? computeMaxHeightContentPx(res) : computeMaxHeightHugPx(res);
        }
        if (contentHeavy) {
            int target = Math.max(dpToPx(res, 400), (int) (parentHeight * MAX_HEIGHT_SCREEN_RATIO));
            return Math.min(computeMaxHeightContentPx(res), target);
        }
        return Math.min(computeMaxHeightHugPx(res), parentHeight - dpToPx(res, 24));
    }

    /** 列表/大纲类 overlay 高度（如 PPT 大纲/模板页）。 */
    public static int computeOverlayListHeightPx(Resources res, int parentHeight) {
        if (parentHeight <= 0) {
            return computeMaxHeightContentPx(res);
        }
        int target = (int) (parentHeight * MAX_HEIGHT_SCREEN_RATIO);
        return Math.min(computeMaxHeightContentPx(res), Math.max(dpToPx(res, 400), target));
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
