package org.libreoffice.androidlib.ai;

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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.R;
import org.libreoffice.androidlib.BottomSheetAnchorHelper;
import org.libreoffice.androidlib.SystemUiHelper;
import org.libreoffice.androidlib.SafeAreaInsets;

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

    /**
     * 居中 AlertDialog 可用高度：在 Hug/Content 上限基础上再扣除状态栏与导航栏，
     * 避免窗口垂直居中时超出可视区、底部按钮被系统栏裁切。
     */
    public static int computeCenteredDialogMaxHeightPx(Context context, boolean contentHeavy) {
        Resources res = context.getResources();
        int baseMax = contentHeavy
                ? computeMaxHeightContentPx(res)
                : computeMaxHeightHugPx(res);
        DisplayMetrics dm = res.getDisplayMetrics();
        int verticalReserve = dpToPx(res, 48);
        if (context instanceof android.app.Activity) {
            android.view.View decor = ((android.app.Activity) context).getWindow().getDecorView();
            SafeAreaInsets safe = SystemUiHelper.readSafeAreaInsets(decor);
            int insetTotal = safe.top + safe.bottom;
            if (insetTotal > 0) {
                verticalReserve = insetTotal;
            }
        }
        int available = dm.heightPixels - verticalReserve;
        return Math.max(dpToPx(res, MIN_HEIGHT_DP), Math.min(baseMax, available));
    }

    /** overlay 居中面板高度：内容多时可占屏 60%，否则用 Hug 上限。 */
    public static int computeOverlayPanelHeightPx(Resources res, int parentHeight, boolean contentHeavy) {
        if (parentHeight <= 0) {
            return contentHeavy ? computeMaxHeightContentPx(res) : computeMaxHeightHugPx(res);
        }
        if (contentHeavy) {
            int target = Math.max(dpToPx(res, 400), (int) (parentHeight * MAX_HEIGHT_SCREEN_RATIO));
            target = Math.min(computeMaxHeightContentPx(res), target);
            target = Math.min(target, parentHeight - dpToPx(res, 24));
            return target;
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
        Window window = dialog.getWindow();
        // 窗口背景用同款圆角白卡(而非透明):窗口与 root 高度有偏差时(键盘收起/系统 relayout),
        // 底部露出的窗口区域仍是圆角,不会出现「上方圆角、下方直角」。
        android.graphics.drawable.Drawable card =
                dialog.getContext().getDrawable(R.drawable.lolib_wai_bg_dialog);
        window.setBackgroundDrawable(card != null ? card : new ColorDrawable(Color.TRANSPARENT));
        // 设计稿遮罩 #0000004D（30%）；AlertDialog 默认 60% 过重
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.3f;
        window.setAttributes(params);
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        SystemUiHelper.applyDialogChrome(dialog);
        SystemUiHelper.applyCenteredDialogSafeInsets(dialog);
    }

    /** 弹窗内 EditText：点击/获焦时显式唤起软键盘（Activity 为 adjustNothing 时需配合窗口 softInputMode）。 */
    public static void bindDialogTextInput(EditText edit) {
        if (edit == null) {
            return;
        }
        Runnable showIme = () -> {
            edit.requestFocusFromTouch();
            InputMethodManager imm = (InputMethodManager) edit.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            boolean shown = false;
            if (imm != null) {
                // 强制 IMM 重新服务该 view(重建 InputConnection),否则动态/滚动容器内
                // view 可能从未建立连接,showSoftInput 直接失败(immActive=false)。
                imm.restartInput(edit);
                shown = imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
                if (!shown) {
                    shown = imm.showSoftInput(edit, InputMethodManager.SHOW_FORCED);
                }
            }
            android.util.Log.d("LOActivity", "wai_input_bind focused=" + edit.isFocused()
                    + " shown=" + shown
                    + " immActive=" + (imm != null && imm.isActive()));
        };
        edit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                edit.post(showIme);
            }
        });
        edit.setOnClickListener(v -> {
            edit.requestFocusFromTouch();
            edit.post(showIme);
        });
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
     * Writer(文档)AI 弹窗宽度:Figma 设计稿 335dp、左右边距 20dp。
     * 旧 computeTargetWidthPx 的 670dp 上限是 2 倍图错误,勿混用。
     */
    public static int computeWriterDialogWidthPx(Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        int target = Math.min(dpToPx(res, 335), dm.widthPixels - dpToPx(res, 40));
        return Math.max(target, dpToPx(res, MIN_WIDTH_DP));
    }

    /**
     * Writer AI 弹窗固定窗口尺寸:窗口与根布局同时设为 exact 高度,
     * 内部滚动区(0dp+weight)按剩余空间收缩,彻底避免截断。
     * heightPx 已按屏幕上限 clamp 过。
     */
    public static void applyWriterFixedSize(AlertDialog dialog, android.view.View root,
                                            int widthPx, int heightPx) {
        if (dialog == null || dialog.getWindow() == null || root == null) {
            return;
        }
        int h = Math.max(heightPx, dpToPx(root.getResources(), MIN_HEIGHT_DP));
        dialog.getWindow().setLayout(widthPx, h);
        android.view.ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp == null) {
            lp = new android.view.ViewGroup.LayoutParams(widthPx, h);
        } else {
            lp.width = widthPx;
            lp.height = h;
        }
        root.setLayoutParams(lp);
    }

    /**
     * Writer AI 弹窗「内容自适应」尺寸(用户认可的模式):窗口高 = 内容自然测量高,
     * 封顶 maxHeightPx;内容超高时 root 固定为窗口高,由内部滚动区收缩吸收。
     * 与固定设计稿高模式相比,表单/选项内容多高弹窗就多高,底部按钮永不被裁。
     *
     * @param minHeightPx 窗口高度下限(结果态防止文本短时窗口塌得过矮);<=0 表示不设下限
     */
    public static void applyContentAdaptiveSize(AlertDialog dialog, android.view.View root,
                                                int widthPx, int maxHeightPx, int minHeightPx) {
        if (dialog == null || dialog.getWindow() == null || root == null) {
            return;
        }
        // 测量前递归放开 0dp+weight 子项(临时转 wrap),让内容自适应拿到自然高;
        // 测后恢复——运行时 0dp+weight 保持弹性,键盘/窗口压缩时由弹性区收缩吸收,
        // 固定高 CTA 永不溢出窗口被裁。
        java.util.List<android.view.View> relaxed = new java.util.ArrayList<>();
        relaxWeightedChildHeights(root, relaxed);
        int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(widthPx,
                android.view.View.MeasureSpec.EXACTLY);
        int heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0,
                android.view.View.MeasureSpec.UNSPECIFIED);
        root.measure(widthSpec, heightSpec);
        int contentHeight = root.getMeasuredHeight();
        restoreWeightedChildHeights(relaxed);
        int windowHeight = Math.min(Math.max(contentHeight, minHeightPx), maxHeightPx);
        windowHeight = Math.max(windowHeight, dpToPx(root.getResources(), MIN_HEIGHT_DP));
        dialog.getWindow().setLayout(widthPx, windowHeight);
        android.view.ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp == null) {
            lp = new android.view.ViewGroup.LayoutParams(widthPx, windowHeight);
        } else {
            lp.width = widthPx;
        }
        // 根部恒撑满窗口:键盘压缩/结果态下限时内部 weight 弹性区收缩,底部按钮不溢出
        lp.height = windowHeight;
        root.setLayoutParams(lp);
        android.util.Log.d("LOActivity", "ai_dialog_adaptive contentH=" + contentHeight
                + " windowH=" + windowHeight + " min=" + minHeightPx + " cap=" + maxHeightPx);
    }

    /** 递归放开 0dp+weight 子项:高度临时转 wrap_content(原值记录在 relaxed,由恢复方还原)。 */
    public static void relaxWeightedChildHeights(android.view.View view,
            java.util.List<android.view.View> relaxed) {
        if (!(view instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof android.widget.LinearLayout.LayoutParams
                    && lp.height == 0
                    && ((android.widget.LinearLayout.LayoutParams) lp).weight > 0) {
                relaxed.add(child);
                lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            relaxWeightedChildHeights(child, relaxed);
        }
    }

    /** 恢复 {@link #relaxWeightedChildHeights} 临时改动的子项高度(回到 0dp+weight)。 */
    public static void restoreWeightedChildHeights(java.util.List<android.view.View> relaxed) {
        for (android.view.View child : relaxed) {
            child.getLayoutParams().height = 0;
        }
    }


    /**
     * Writer AI 弹窗「结果态」高度上限:结果态无软键盘,允许撑到屏高扣除垂直保留区,
     * 而不是 0.6 屏高比例——否则设计稿较高结果页(image 660 / preview 716 / translate 549)
     * 在 3x 屏(0.6×800=480dp)下底部按钮被裁。
     */
    public static int computeWriterResultMaxHeightPx(Context context) {
        int centeredMax = computeCenteredDialogMaxHeightPx(context, true);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenMax = dm.heightPixels - dpToPx(context.getResources(), 48);
        return Math.max(centeredMax, screenMax);
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
        BottomSheetAnchorHelper.Options options =
                BottomSheetAnchorHelper.overlayDocumentSheetOptions(context,
                        logTag != null ? logTag : "CompactPanel");
        options.draggable = false;
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
                alertDialog.setOnDismissListener(d -> onDismiss.run());
            }
            if (bottomSheetDialog != null) {
                bottomSheetDialog.setOnDismissListener(d -> onDismiss.run());
            }
        }
    }
}
