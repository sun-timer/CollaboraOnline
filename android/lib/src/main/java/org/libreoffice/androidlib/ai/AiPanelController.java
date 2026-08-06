package org.libreoffice.androidlib.ai;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.R;

public class AiPanelController {
    private static final String TAG = "AiPanelController";
    /** Figma 129:7199：750×960 面板在 1624 屏上 y=664 → 960/1624。 */
    private static final float SHEET_HEIGHT_RATIO = 960f / 1624f;

    public interface ScrollCallbacks {
        boolean canMessagesScrollConsume(float deltaY);

        void onTouchCancelled();
    }

    private float scrollLastY = Float.NaN;
    private boolean scrollLastDisallow = false;
    private long scrollInterceptLogAt = 0L;

    private int lastTargetHeight = 0;
    private int lastScreenHeight = 0;
    private int lastScreenWidth = 0;
    private int lastNavBarHeight = 0;
    private int lastImeBottom = 0;
    private boolean lastImeVisible = false;
    private int baseExpandedOffset = 0;
    private BottomSheetBehavior<View> anchoredBehavior;
    private boolean sheetExpandedOnce = false;
    private BottomSheetDialog insetDialog;
    private View insetContentRoot;

    public void configureBottomSheet(BottomSheetDialog dialog, View contentRoot,
            int screenHeight, int screenWidth, int orientation) {
        configureBottomSheet(dialog, contentRoot, screenHeight, screenWidth, orientation, null);
    }

    public void configureBottomSheet(BottomSheetDialog dialog, View contentRoot,
            int screenHeight, int screenWidth, int orientation, Runnable onExpanded) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        sheetExpandedOnce = false;
        lastScreenHeight = screenHeight;
        lastScreenWidth = screenWidth;
        ensureBottomSheetContentMatchParent(contentRoot);
        prepareDialogWindow(dialog, screenWidth, screenHeight);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        anchoredBehavior = behavior;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidth > screenHeight;
        float targetRatio = isLandscape ? 0.52f : SHEET_HEIGHT_RATIO;
        int ratioHeight = Math.round(screenHeight * targetRatio);
        Resources res = contentRoot != null ? contentRoot.getResources() : bottomSheet.getResources();
        int minHeight = computeMinPanelHeightPx(res);
        int navBarHeight = getNavigationBarHeightPx(res);
        int targetHeight = Math.max(ratioHeight, minHeight);
        lastTargetHeight = targetHeight;
        lastNavBarHeight = navBarHeight;

        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        applyBottomSheetLayout(bottomSheet, layoutParams, targetHeight);
        bottomSheet.setBackgroundResource(android.R.color.transparent);
        bottomSheet.setClipToPadding(false);
        applySheetScrim(dialog);
        if (contentRoot != null) {
            contentRoot.setElevation(dpToPx(contentRoot, 8));
            if (contentRoot instanceof ViewGroup) {
                ((ViewGroup) contentRoot).setClipToPadding(false);
            }
            applyContentBottomPadding(contentRoot, navBarHeight);
        }

        behavior.setFitToContents(false);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(false);
        behavior.setDraggable(false);
        installSheetInsets(dialog, contentRoot);

        bottomSheet.post(() -> {
            refreshExpandedOffset(bottomSheet, "configure");
            applySheetAnchor(bottomSheet, behavior, "ai_sheet_expanded", true);
            logLayoutMetrics("ai_sheet_expanded", bottomSheet, contentRoot);
            if (onExpanded != null) {
                onExpanded.run();
            }
        });
    }

    /** Tab 切换后仅同步 offset/peek，不触发 setState 动画。 */
    public void syncSheetPosition(BottomSheetDialog dialog) {
        if (dialog == null || anchoredBehavior == null || lastTargetHeight <= 0) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        refreshExpandedOffset(bottomSheet, "tab_sync");
        applySheetAnchor(bottomSheet, anchoredBehavior, "ai_sheet_sync", false);
        View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : null;
        if (contentRoot != null) {
            applyContentBottomPadding(contentRoot, lastNavBarHeight);
        }
        logLayoutMetrics("ai_sheet_sync", bottomSheet, contentRoot);
    }

    private void refreshExpandedOffset(FrameLayout bottomSheet, String reason) {
        int coordinatorHeight = lastScreenHeight;
        View parent = bottomSheet != null ? (View) bottomSheet.getParent() : null;
        if (parent != null && parent.getHeight() > 0) {
            coordinatorHeight = parent.getHeight();
        }
        // 可见区域以 screen 为准；parent 可能比 screen 更高（日志 sheetBottom=3858 > screen 2880）
        int visibleBottom = Math.min(coordinatorHeight, lastScreenHeight);
        int bottomInset = lastImeVisible && lastImeBottom > 0 ? lastImeBottom : lastNavBarHeight;
        baseExpandedOffset = Math.max(0, visibleBottom - lastTargetHeight - bottomInset);
        if (anchoredBehavior != null) {
            anchoredBehavior.setExpandedOffset(baseExpandedOffset);
        }
        Log.i(TAG, "ai_sheet_offset reason=" + reason
                + " coordinatorH=" + coordinatorHeight
                + " visibleBottom=" + visibleBottom
                + " target=" + lastTargetHeight
                + " navBar=" + lastNavBarHeight
                + " imeVisible=" + lastImeVisible
                + " imeBottom=" + lastImeBottom
                + " bottomInset=" + bottomInset
                + " expandedOffset=" + baseExpandedOffset);
    }

    private void applySheetAnchor(FrameLayout bottomSheet, BottomSheetBehavior<View> behavior,
            String reason, boolean allowExpandState) {
        if (bottomSheet == null || behavior == null || lastTargetHeight <= 0) {
            return;
        }
        behavior.setFitToContents(false);
        behavior.setPeekHeight(lastTargetHeight, false);
        behavior.setExpandedOffset(baseExpandedOffset);
        if (allowExpandState && !sheetExpandedOnce) {
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            sheetExpandedOnce = true;
        }
        Log.i(TAG, reason + " peek=" + lastTargetHeight
                + " expandedOffset=" + baseExpandedOffset
                + " sheetTop=" + bottomSheet.getTop()
                + " allowExpandState=" + allowExpandState);
    }

    /**
     * 禁止 gravity=BOTTOM：会与 expandedOffset 冲突，导致 sheet 贴 parent 底（3858）而非屏幕底（2880）。
     * 日志证据：expandedOffset=1138 但 sheetTop=2156 (=3858-1702)。
     */
    private static void applyBottomSheetLayout(FrameLayout bottomSheet, ViewGroup.LayoutParams layoutParams,
            int targetHeight) {
        if (layoutParams == null) {
            return;
        }
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = targetHeight;
        if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) layoutParams;
            clp.gravity = android.view.Gravity.NO_GRAVITY;
            clp.leftMargin = 0;
            clp.rightMargin = 0;
            clp.topMargin = 0;
            clp.bottomMargin = 0;
        } else if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
            marginParams.leftMargin = 0;
            marginParams.rightMargin = 0;
            marginParams.topMargin = 0;
            marginParams.bottomMargin = 0;
        }
        bottomSheet.setLayoutParams(layoutParams);
    }

    public void configureBottomSheetFitContent(BottomSheetDialog dialog, View contentRoot,
            int screenHeight, int screenWidth, int orientation, float maxScreenRatio) {
        if (dialog == null || contentRoot == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        sheetExpandedOnce = false;
        lastScreenHeight = screenHeight;
        lastScreenWidth = screenWidth;
        ensureBottomSheetContentWrapHeight(contentRoot);
        prepareDialogWindow(dialog, screenWidth, screenHeight);
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidth > screenHeight;
        float ratioCap = maxScreenRatio > 0f ? maxScreenRatio : (isLandscape ? 0.52f : 0.55f);
        int maxHeight = (int) (screenHeight * ratioCap);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        contentRoot.measure(widthSpec, heightSpec);
        int contentHeight = contentRoot.getMeasuredHeight();
        int minHeight = computeMinPanelHeightPx(contentRoot.getResources());
        View calcBlock = contentRoot.findViewById(R.id.ai_op_calc_block);
        if (calcBlock != null && calcBlock.getVisibility() == View.VISIBLE) {
            minHeight = Math.max(minHeight,
                    contentRoot.getResources().getDimensionPixelSize(R.dimen.ai_sheet_calc_min_height));
        }
        int navBarHeight = getNavigationBarHeightPx(contentRoot.getResources());
        int targetHeight = Math.min(Math.max(Math.max(contentHeight, minHeight), 1), maxHeight);

        lastTargetHeight = targetHeight;
        lastNavBarHeight = navBarHeight;

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        anchoredBehavior = behavior;
        ensureBottomSheetContentMatchParent(contentRoot);
        applyBottomSheetLayout(bottomSheet, bottomSheet.getLayoutParams(), targetHeight);
        bottomSheet.setBackgroundResource(android.R.color.transparent);
        applySheetScrim(dialog);
        applyContentBottomPadding(contentRoot, navBarHeight);

        behavior.setFitToContents(false);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(false);
        behavior.setDraggable(false);
        installSheetInsets(dialog, contentRoot);

        bottomSheet.post(() -> {
            refreshExpandedOffset(bottomSheet, "fit_content");
            applySheetAnchor(bottomSheet, behavior, "ai_sheet_fit_content", true);
            logLayoutMetrics("ai_sheet_fit_content", bottomSheet, contentRoot);
        });
    }

    private static void prepareDialogWindow(BottomSheetDialog dialog, int screenWidth, int screenHeight) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(dialog.getWindow(), false);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        dialog.getWindow().setLayout(screenWidth, screenHeight);
    }

    private static void applyContentBottomPadding(View contentRoot, int navBarHeight) {
        if (contentRoot == null) {
            return;
        }
        contentRoot.setPadding(0, 0, 0, Math.max(0, navBarHeight));
    }

    private void installSheetInsets(BottomSheetDialog dialog, View contentRoot) {
        if (contentRoot == null) {
            return;
        }
        insetDialog = dialog;
        insetContentRoot = contentRoot;
        Resources res = contentRoot.getResources();
        final int navFallback = getNavigationBarHeightPx(res);
        androidx.core.view.OnApplyWindowInsetsListener insetListener = (v, insets) -> {
            int navBottom = Math.max(
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom, navFallback);
            lastNavBarHeight = navBottom;
            lastImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            lastImeBottom = lastImeVisible
                    ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            FrameLayout bottomSheet = insetDialog != null
                    ? insetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet) : null;
            if (bottomSheet != null) {
                refreshExpandedOffset(bottomSheet, "insets");
                applySheetAnchor(bottomSheet, anchoredBehavior, "ai_sheet_insets", false);
            }
            if (insetContentRoot != null) {
                applyContentBottomPadding(insetContentRoot, lastImeVisible ? 0 : navBottom);
            }
            Log.i(TAG, "ai_sheet_insets imeVisible=" + lastImeVisible
                    + " imeBottom=" + lastImeBottom
                    + " navBottom=" + navBottom
                    + " expandedOffset=" + baseExpandedOffset
                    + " contentPadBottom=" + (insetContentRoot != null
                            ? insetContentRoot.getPaddingBottom() : -1));
            return insets;
        };
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot, insetListener);
        if (dialog != null && dialog.getWindow() != null) {
            View decor = dialog.getWindow().getDecorView();
            ViewCompat.setOnApplyWindowInsetsListener(decor, insetListener);
        }
        requestInsetsRefresh();
    }

    /** 输入框聚焦等场景主动刷新 IME 上抬。 */
    public void requestInsetsRefresh() {
        if (insetContentRoot != null) {
            ViewCompat.requestApplyInsets(insetContentRoot);
        }
        if (insetDialog != null && insetDialog.getWindow() != null) {
            ViewCompat.requestApplyInsets(insetDialog.getWindow().getDecorView());
        }
    }

    private static int computeMinPanelHeightPx(Resources res) {
        if (res == null) {
            return 0;
        }
        int header = res.getDimensionPixelSize(R.dimen.ai_sheet_header_height);
        int tabBlock = dpToPxRes(res, 12) + res.getDimensionPixelSize(R.dimen.ai_panel_tab_container_height);
        int scrollBlock = dpToPxRes(res, 12) + dpToPxRes(res, 120);
        int inputBlock = dpToPxRes(res, 10)
                + res.getDimensionPixelSize(R.dimen.ai_panel_input_height)
                + dpToPxRes(res, 12);
        return header + tabBlock + scrollBlock + inputBlock;
    }

    private static int getNavigationBarHeightPx(Resources res) {
        int id = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0) {
            return res.getDimensionPixelSize(id);
        }
        return dpToPxRes(res, 48);
    }

    private static int dpToPxRes(Resources res, int dp) {
        return Math.round(dp * res.getDisplayMetrics().density);
    }

    private void logLayoutMetrics(String reason, View bottomSheet, View contentRoot) {
        int sheetH = bottomSheet != null ? bottomSheet.getHeight() : -1;
        int contentH = contentRoot != null ? contentRoot.getHeight() : -1;
        int sheetTop = bottomSheet != null ? bottomSheet.getTop() : -1;
        int sheetBottom = bottomSheet != null ? bottomSheet.getBottom() : -1;
        int parentH = -1;
        if (bottomSheet != null && bottomSheet.getParent() instanceof View) {
            parentH = ((View) bottomSheet.getParent()).getHeight();
        }
        Log.i(TAG, reason + " target=" + lastTargetHeight
                + " screenH=" + lastScreenHeight
                + " parentH=" + parentH
                + " sheetH=" + sheetH
                + " sheetTop=" + sheetTop
                + " expectedTop=" + baseExpandedOffset
                + " sheetBottom=" + sheetBottom
                + " contentH=" + contentH
                + " contentPadBottom=" + (contentRoot != null ? contentRoot.getPaddingBottom() : -1));
    }

    private static void applySheetScrim(BottomSheetDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.dimAmount = 0.28f;
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private static int dpToPx(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    private static void ensureBottomSheetContentMatchParent(View contentRoot) {
        if (contentRoot == null) {
            return;
        }
        ViewGroup.LayoutParams lp = contentRoot.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        contentRoot.setLayoutParams(lp);
    }

    /** 仅用于 fit-content 测量前：保持宽度 match，高度随内容 Hug。 */
    private static void ensureBottomSheetContentWrapHeight(View contentRoot) {
        if (contentRoot == null) {
            return;
        }
        ViewGroup.LayoutParams lp = contentRoot.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        contentRoot.setLayoutParams(lp);
    }

    public void installMessageScrollTouchPolicy(ScrollView scrollView, ScrollCallbacks callbacks) {
        if (scrollView == null || callbacks == null) {
            return;
        }
        scrollView.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null) {
                final int action = event.getActionMasked();
                boolean disallow = false;
                if (action == MotionEvent.ACTION_DOWN) {
                    scrollLastY = event.getY();
                    disallow = callbacks.canMessagesScrollConsume(0f);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float previousY = Float.isNaN(scrollLastY) ? event.getY() : scrollLastY;
                    float deltaY = event.getY() - previousY;
                    scrollLastY = event.getY();
                    disallow = callbacks.canMessagesScrollConsume(deltaY);
                } else if (action == MotionEvent.ACTION_UP) {
                    scrollLastY = Float.NaN;
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    scrollLastY = Float.NaN;
                    callbacks.onTouchCancelled();
                }

                parent.requestDisallowInterceptTouchEvent(disallow);
                maybeLogScrollIntercept(action, disallow);
            }
            return false;
        });
    }

    public void resetTransientState() {
        scrollLastY = Float.NaN;
        scrollLastDisallow = false;
        scrollInterceptLogAt = 0L;
        lastTargetHeight = 0;
        lastScreenHeight = 0;
        lastScreenWidth = 0;
        lastNavBarHeight = 0;
        lastImeBottom = 0;
        lastImeVisible = false;
        baseExpandedOffset = 0;
        anchoredBehavior = null;
        sheetExpandedOnce = false;
        insetDialog = null;
        insetContentRoot = null;
    }

    private void maybeLogScrollIntercept(int action, boolean disallow) {
        long now = android.os.SystemClock.uptimeMillis();
        if (disallow != scrollLastDisallow || now - scrollInterceptLogAt > 1200) {
            Log.i(TAG, "ai_scroll_disallow_intercept action=" + action + " disallow=" + disallow);
            scrollLastDisallow = disallow;
            scrollInterceptLogAt = now;
        }
    }
}
