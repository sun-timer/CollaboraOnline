package org.libreoffice.androidlib.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.BottomSheetAnchorHelper;
import org.libreoffice.androidlib.SafeAreaInsets;
import org.libreoffice.androidlib.SystemUiHelper;

import androidx.constraintlayout.widget.ConstraintLayout;

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

    /**
     * overlay 面板在 ConstraintLayout 父级内居中：四边约束 + bias，横竖屏切换时自动重算位置。
     */
    public static void applyOverlayCenterConstraints(ConstraintLayout.LayoutParams lp,
            int widthPx, int heightPx) {
        lp.width = widthPx;
        lp.height = heightPx;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.horizontalBias = 0.5f;
        lp.verticalBias = 0.5f;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
    }

    /** Center overlay between document top and bottom toolbars (avoids status / nav overlap). */
    public static void applyOverlayCenterConstraintsInChromeArea(ConstraintLayout.LayoutParams lp,
            int widthPx, int heightPx, int topToolbarId, int bottomToolbarId) {
        lp.width = widthPx;
        lp.height = heightPx;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToBottom = topToolbarId;
        lp.bottomToTop = bottomToolbarId;
        lp.horizontalBias = 0.5f;
        lp.verticalBias = 0.5f;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
    }

    public static void applyFlexibleDialogSizeOnConfigurationChange(AlertDialog dialog, View root) {
        if (dialog == null || !dialog.isShowing() || dialog.getWindow() == null || root == null) {
            return;
        }
        Resources res = root.getResources();
        applyFlexibleWidth(root, dialog, computeTargetWidthPx(res), computeMaxHeightHugPx(res));
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
        int screenCap = Math.max(dm.heightPixels - dpToPx(res, 24), 1);
        int maxHeight = Math.min(dpToPx(res, MAX_HEIGHT_HUG_DP), screenCap);
        // 下限不得反超屏幕可用高，避免横屏/短屏时弹窗比屏幕还高
        int floor = Math.min(dpToPx(res, MIN_HEIGHT_DP), screenCap);
        return Math.max(maxHeight, floor);
    }

    /** 结果/滚动内容弹窗高度上限（较 Hug 略高，但仍低于旧 80% 屏高）。 */
    public static int computeMaxHeightContentPx(Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        int screenCap = Math.max(dm.heightPixels - dpToPx(res, 24), 1);
        int maxHeight = Math.min(dpToPx(res, MAX_HEIGHT_CONTENT_DP),
                (int) (dm.heightPixels * MAX_HEIGHT_SCREEN_RATIO));
        maxHeight = Math.min(maxHeight, screenCap);
        int floor = Math.min(dpToPx(res, 320), screenCap);
        return Math.max(maxHeight, floor);
    }

    /** 任意目标高度钳制到屏幕可用高（屏高-24dp），防止固定 dp 高度在横屏/短屏溢出。 */
    public static int clampHeightToScreen(Resources res, int heightPx) {
        if (res == null || heightPx <= 0) {
            return heightPx;
        }
        DisplayMetrics dm = res.getDisplayMetrics();
        int screenCap = Math.max(dm.heightPixels - dpToPx(res, 24), 1);
        return Math.min(heightPx, screenCap);
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

    /** BottomSheet：去掉窗口遮罩（功能面板等需保持文档可见）。 */
    public static void applyNoDimScrim(BottomSheetDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        Window window = dialog.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.View touchOutside = dialog.findViewById(
                com.google.android.material.R.id.touch_outside);
        if (touchOutside != null) {
            touchOutside.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /** 透明圆角背景，配合 lolib_bg_dialog_outline 根布局。 */
    public static void applyTransparentWindow(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        SystemUiHelper.applyCenteredDialogSafeInsets(dialog);
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

    /**
     * 小型 Hug 弹窗：竖屏走 BottomSheet（贴底 + {@link BottomSheetAnchorHelper}），
     * 横屏走居中 AlertDialog，避免 BottomSheet 在短屏横屏下只露一条。
     */
    public static CompactPanelSession showCompactPanel(Context context, View panel, String logTag) {
        if (context == null || panel == null) {
            return new CompactPanelSession(null, null);
        }
        if (panel.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) panel.getParent()).removeView(panel);
        }
        if (BottomSheetAnchorHelper.isLandscape(context)) {
            AlertDialog dialog = new AlertDialog.Builder(context).create();
            dialog.setView(panel);
            applyCloseOnlyDismiss(dialog);
            applyTransparentWindow(dialog);
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
                WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                params.gravity = Gravity.CENTER;
                dialog.getWindow().setAttributes(params);
            }
            Resources res = context.getResources();
            applyFlexibleWidth(panel, dialog,
                    computeTargetWidthPx(res), computeMaxHeightHugPx(res));
            CompactPanelSession session = new CompactPanelSession(dialog, null);
            dialog.setOnDismissListener(d -> untrackCompactPanel(session));
            trackCompactPanel(session);
            return session;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(panel);
        applyCloseOnlyDismiss(dialog);
        dialog.show();
        applyNoDimScrim(dialog);
        BottomSheetAnchorHelper.Options options = new BottomSheetAnchorHelper.Options();
        options.draggable = false;
        options.logTag = logTag != null ? logTag : "CompactPanel";
        BottomSheetAnchorHelper.expandWrapContent(dialog, 0.92f, options);
        CompactPanelSession session = new CompactPanelSession(null, dialog);
        dialog.setOnDismissListener(d -> untrackCompactPanel(session));
        trackCompactPanel(session);
        return session;
    }

    /** 横屏 AlertDialog / 竖屏 BottomSheet 统一 dismiss（配置变更时调用）。 */
    private static final java.util.List<CompactPanelSession> ACTIVE_COMPACT_PANELS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void dismissCompactPanelsOnConfigurationChange() {
        for (CompactPanelSession session : ACTIVE_COMPACT_PANELS) {
            session.dismiss();
        }
        ACTIVE_COMPACT_PANELS.clear();
    }

    private static void trackCompactPanel(CompactPanelSession session) {
        if (session != null && (session.alertDialog != null || session.bottomSheetDialog != null)) {
            ACTIVE_COMPACT_PANELS.add(session);
        }
    }

    private static void untrackCompactPanel(CompactPanelSession session) {
        ACTIVE_COMPACT_PANELS.remove(session);
    }

    /** 统一 dismiss 竖屏 BottomSheet / 横屏 AlertDialog。 */
    public static final class CompactPanelSession {
        final AlertDialog alertDialog;
        final BottomSheetDialog bottomSheetDialog;

        CompactPanelSession(AlertDialog alertDialog, BottomSheetDialog bottomSheetDialog) {
            this.alertDialog = alertDialog;
            this.bottomSheetDialog = bottomSheetDialog;
        }

        public boolean isShowing() {
            return (alertDialog != null && alertDialog.isShowing())
                    || (bottomSheetDialog != null && bottomSheetDialog.isShowing());
        }

        public void dismiss() {
            if (alertDialog != null) {
                alertDialog.dismiss();
            }
            if (bottomSheetDialog != null) {
                bottomSheetDialog.dismiss();
            }
        }

        public void setOnDismissListener(Runnable onDismiss) {
            if (onDismiss == null) {
                return;
            }
            if (alertDialog != null) {
                alertDialog.setOnDismissListener(d -> {
                    untrackCompactPanel(this);
                    onDismiss.run();
                });
            }
            if (bottomSheetDialog != null) {
                bottomSheetDialog.setOnDismissListener(d -> {
                    untrackCompactPanel(this);
                    onDismiss.run();
                });
            }
        }
    }

    /** 居中弹窗可用高度上限：内容模式(content)或 Hug 模式；Activity 时扣除状态栏+导航栏。 */
    public static int computeCenteredDialogMaxHeightPx(Context context, boolean contentMode) {
        Resources res = context.getResources();
        int base = contentMode ? computeMaxHeightContentPx(res) : computeMaxHeightHugPx(res);
        DisplayMetrics dm = res.getDisplayMetrics();
        int reserved = dpToPx(res, 48);
        if (context instanceof Activity) {
            View decor = ((Activity) context).getWindow().getDecorView();
            SafeAreaInsets insets = SystemUiHelper.readSafeAreaInsets(decor);
            int total = insets.top + insets.bottom;
            if (total > 0) {
                reserved = total;
            }
        }
        int available = dm.heightPixels - reserved;
        return Math.max(dpToPx(res, 180), Math.min(base, available));
    }
}
