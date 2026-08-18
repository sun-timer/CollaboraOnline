package org.libreoffice.androidlib;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.WeakHashMap;

/**
 * Anchors {@link BottomSheetDialog} to the visible screen bottom in landscape.
 * Without {@code expandedOffset} + {@code Gravity.NO_GRAVITY}, sheets stick to an oversized
 * coordinator parent and only a sliver remains visible on short landscape screens.
 */
public final class BottomSheetAnchorHelper {
    private static final String TAG = "BottomSheetAnchor";
    /** Figma 750×1624：功能面板统一高度 1066px ≈ 65.6% 屏高。 */
    public static final float FUNCTION_PANEL_HEIGHT_RATIO = 1066f / 1624f;
    /** Figma 125:12715 预览态功能弹窗 696px ≈ 42.9% 屏高（内容自适应，此为上限）。 */
    public static final float PREVIEW_FUNCTION_PANEL_HEIGHT_RATIO = 696f / 1624f;

    private static final WeakHashMap<BottomSheetDialog, Integer> sAppliedHeights = new WeakHashMap<>();

    /** Re-anchor after activity IME dismisses when a sheet opens over the keyboard. */
    private static final long SHEET_IME_SETTLE_MS = 200L;

    private BottomSheetAnchorHelper() {
    }

    public static final class Options {
        public boolean draggable = true;
        public boolean hideable = true;
        public boolean skipCollapsed = true;
        public boolean applyNavBarPadding = true;
        /** Overlay sheets leave 0; only inset-layout panels use a non-zero anchor. */
        public int anchorAboveBottomPx = 0;
        public String logTag = TAG;
    }

    public static boolean isLandscape(Context context) {
        if (context == null) {
            return false;
        }
        Configuration configuration = context.getResources().getConfiguration();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                || metrics.widthPixels > metrics.heightPixels;
    }

    public static int resolveTargetHeight(Context context, float heightRatio) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenHeight = metrics.heightPixels;
        float ratio = heightRatio;
        if (isLandscape(context)) {
            ratio = Math.min(ratio, 0.92f);
        }
        return Math.max(1, Math.round(screenHeight * ratio));
    }

    public static void prepareDialogWindow(BottomSheetDialog dialog, Context context) {
        if (dialog == null || dialog.getWindow() == null || context == null) {
            return;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        WindowCompat.setDecorFitsSystemWindows(dialog.getWindow(), false);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        dialog.getWindow().setLayout(metrics.widthPixels, metrics.heightPixels);
        SystemUiHelper.applyBottomSheetChrome(context, dialog.getWindow());
    }

    public static void expandRatio(BottomSheetDialog dialog, float heightRatio, Options options) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        int targetHeight = resolveTargetHeight(bottomSheet.getContext(), heightRatio);
        Integer applied = sAppliedHeights.get(dialog);
        if (applied != null && applied == targetHeight) {
            return;
        }
        expandFixed(dialog, targetHeight, options);
        sAppliedHeights.put(dialog, targetHeight);
    }

    public static void clearAppliedHeight(BottomSheetDialog dialog) {
        if (dialog != null) {
            sAppliedHeights.remove(dialog);
        }
    }

    public static void expandWrapContent(BottomSheetDialog dialog, float maxScreenRatio, Options options) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        Context context = bottomSheet.getContext();
        prepareDialogWindow(dialog, context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        float ratioCap = maxScreenRatio > 0f
                ? maxScreenRatio
                : (isLandscape(context) ? 0.92f : 0.75f);
        int maxHeight = Math.round(screenHeight * ratioCap);

        View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : bottomSheet;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        contentRoot.measure(widthSpec, heightSpec);
        int measuredHeight = contentRoot.getMeasuredHeight();
        int targetHeight = Math.min(Math.max(measuredHeight, 1), maxHeight);
        expandFixed(dialog, targetHeight, options);
    }

    /**
     * Function panels: height follows visible content, capped at {@code maxScreenRatio}.
     * Avoids empty space below short panels on tall or differently proportioned screens.
     */
    public static void expandFunctionPanel(BottomSheetDialog dialog, float maxScreenRatio, Options options) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        Context context = bottomSheet.getContext();
        prepareDialogWindow(dialog, context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        float ratioCap = maxScreenRatio > 0f ? maxScreenRatio : FUNCTION_PANEL_HEIGHT_RATIO;
        if (isLandscape(context)) {
            ratioCap = Math.min(ratioCap, 0.92f);
        }
        int maxHeight = Math.round(screenHeight * ratioCap);

        View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : bottomSheet;
        prepareFunctionPanelForMeasurement(contentRoot, screenWidth);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        contentRoot.measure(widthSpec, heightSpec);
        int measuredHeight = contentRoot.getMeasuredHeight();
        int targetHeight = Math.min(Math.max(measuredHeight, 1), maxHeight);

        if (measuredHeight > maxHeight) {
            applyFunctionPanelScrollCap(contentRoot, targetHeight, screenWidth);
        } else {
            ViewGroup.LayoutParams rootParams = contentRoot.getLayoutParams();
            if (rootParams != null) {
                rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                contentRoot.setLayoutParams(rootParams);
            }
        }

        if (options == null) {
            options = new Options();
        }
        expandFixed(dialog, targetHeight, options);
        sAppliedHeights.put(dialog, targetHeight);
    }

    private static void prepareFunctionPanelForMeasurement(View root, int screenWidth) {
        if (root == null) {
            return;
        }
        ViewGroup.LayoutParams rootParams = root.getLayoutParams();
        if (rootParams != null) {
            rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            root.setLayoutParams(rootParams);
        }
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            ViewGroup.LayoutParams childParams = child.getLayoutParams();
            if (childParams instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams linearParams =
                        (android.widget.LinearLayout.LayoutParams) childParams;
                if (linearParams.height == 0 && linearParams.weight > 0f) {
                    linearParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    linearParams.weight = 0f;
                    child.setLayoutParams(linearParams);
                }
            }
            if (child instanceof androidx.core.widget.NestedScrollView) {
                ViewGroup.LayoutParams scrollParams = child.getLayoutParams();
                scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                child.setLayoutParams(scrollParams);
            } else if (child instanceof ViewGroup) {
                prepareFunctionPanelForMeasurement(child, screenWidth);
            }
        }
    }

    private static void applyFunctionPanelScrollCap(View root, int targetHeight, int screenWidth) {
        if (root == null) {
            return;
        }
        ViewGroup.LayoutParams rootParams = root.getLayoutParams();
        if (rootParams != null) {
            rootParams.height = targetHeight;
            root.setLayoutParams(rootParams);
        }
        if (!(root instanceof ViewGroup)) {
            return;
        }
        int fixedHeight = 0;
        androidx.core.widget.NestedScrollView primaryScroll = null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            if (child instanceof androidx.core.widget.NestedScrollView) {
                primaryScroll = (androidx.core.widget.NestedScrollView) child;
            } else {
                fixedHeight += measureViewHeight(child, screenWidth);
            }
        }
        if (primaryScroll != null) {
            ViewGroup.LayoutParams scrollParams = primaryScroll.getLayoutParams();
            scrollParams.height = Math.max(1, targetHeight - fixedHeight);
            primaryScroll.setLayoutParams(scrollParams);
        }
    }

    private static int measureViewHeight(View view, int screenWidth) {
        if (view == null) {
            return 0;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        if (params != null && params.width > 0 && params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
            widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY);
        }
        view.measure(widthMeasureSpec, heightMeasureSpec);
        return view.getMeasuredHeight();
    }

    public static void expandFixed(BottomSheetDialog dialog, int targetHeight, Options options) {
        if (dialog == null || targetHeight <= 0) {
            return;
        }
        if (options == null) {
            options = new Options();
        }
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        Context context = bottomSheet.getContext();
        prepareDialogWindow(dialog, context);
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;

        applyBottomSheetLayout(bottomSheet, targetHeight);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(false);
        behavior.setSkipCollapsed(options.skipCollapsed);
        behavior.setHideable(options.hideable);
        behavior.setDraggable(options.draggable);

        // Bottom inset is applied via expandedOffset; add only a small content safe pad here.
        View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : null;
        if (contentRoot != null && options.applyNavBarPadding) {
            int safePad = SystemUiHelper.bottomContentSafePaddingPx(context);
            contentRoot.setPadding(contentRoot.getPaddingLeft(), contentRoot.getPaddingTop(),
                    contentRoot.getPaddingRight(), safePad);
        } else if (contentRoot != null) {
            contentRoot.setPadding(contentRoot.getPaddingLeft(), contentRoot.getPaddingTop(),
                    contentRoot.getPaddingRight(), 0);
        }

        AnchorState state = new AnchorState(targetHeight, screenHeight, options.logTag,
                options.applyNavBarPadding, options.anchorAboveBottomPx);
        bottomSheet.post(() -> {
            state.apply(bottomSheet, behavior, false, 0, 0, true);
            bottomSheet.postDelayed(() -> {
                if (dialog.isShowing()) {
                    state.apply(bottomSheet, behavior, false, 0, 0, false);
                    if (contentRoot != null) {
                        ViewCompat.requestApplyInsets(contentRoot);
                    }
                }
            }, SHEET_IME_SETTLE_MS);
        });
        installInsetsListener(dialog, bottomSheet, behavior, state);
    }

    private static void applyBottomSheetLayout(FrameLayout bottomSheet, int targetHeight) {
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        if (layoutParams == null) {
            return;
        }
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = targetHeight;
        if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) layoutParams;
            clp.gravity = Gravity.NO_GRAVITY;
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

    private static int resolveBottomInset(WindowInsetsCompat insets, Context context, boolean applyNavBarPadding) {
        if (!applyNavBarPadding) {
            return 0;
        }
        return SystemUiHelper.bottomInsetForSheet(context, insets);
    }

    private static int getNavigationBarHeightPx(Context context) {
        int id = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0) {
            return context.getResources().getDimensionPixelSize(id);
        }
        return Math.round(48f * context.getResources().getDisplayMetrics().density);
    }

    private static int computeExpandedOffset(
            FrameLayout bottomSheet, int screenHeight, int targetHeight,
            boolean imeVisible, int imeBottomPx, int navigationInsetPx, int anchorAboveBottomPx) {
        int coordinatorHeight = screenHeight;
        View parent = bottomSheet != null ? (View) bottomSheet.getParent() : null;
        if (parent != null && parent.getHeight() > 0) {
            coordinatorHeight = parent.getHeight();
        }
        int visibleBottom = Math.min(coordinatorHeight, screenHeight);
        return SystemUiHelper.computeSheetExpandedOffset(
                visibleBottom, targetHeight, imeVisible, imeBottomPx,
                navigationInsetPx, anchorAboveBottomPx);
    }

    private static void installInsetsListener(
            BottomSheetDialog dialog,
            FrameLayout bottomSheet,
            BottomSheetBehavior<View> behavior,
            AnchorState state) {
        View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : bottomSheet;
        Context context = bottomSheet.getContext();
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = imeVisible
                    ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            int navInset = resolveBottomInset(insets, context, state.applyNavBarPadding);
            state.apply(bottomSheet, behavior, imeVisible, imeBottom, navInset, false);
            return insets;
        });
        if (dialog.getWindow() != null) {
            View decor = dialog.getWindow().getDecorView();
            ViewCompat.setOnApplyWindowInsetsListener(decor, (v, insets) -> {
                ViewCompat.requestApplyInsets(contentRoot);
                return insets;
            });
            ViewCompat.requestApplyInsets(decor);
        }
        ViewCompat.requestApplyInsets(contentRoot);
    }

    private static final class AnchorState {
        private final int targetHeight;
        private final int screenHeight;
        private final String logTag;
        private final boolean applyNavBarPadding;
        private final int anchorAboveBottomPx;
        private boolean expandedOnce;

        AnchorState(int targetHeight, int screenHeight, String logTag,
                boolean applyNavBarPadding, int anchorAboveBottomPx) {
            this.targetHeight = targetHeight;
            this.screenHeight = screenHeight;
            this.logTag = logTag != null ? logTag : TAG;
            this.applyNavBarPadding = applyNavBarPadding;
            this.anchorAboveBottomPx = Math.max(0, anchorAboveBottomPx);
        }

        void apply(FrameLayout bottomSheet, BottomSheetBehavior<View> behavior,
                boolean imeVisible, int imeBottomPx, int navigationInsetPx, boolean allowExpandState) {
            if (bottomSheet == null || behavior == null) {
                return;
            }
            int effectiveAnchor = SystemUiHelper.resolveSheetAnchorAboveBottomPx(
                    anchorAboveBottomPx, imeVisible, imeBottomPx);
            int bottomInset = SystemUiHelper.resolveSheetAnchorBottomInset(
                    imeVisible, imeBottomPx, navigationInsetPx, effectiveAnchor);
            int expandedOffset = computeExpandedOffset(
                    bottomSheet, screenHeight, targetHeight,
                    imeVisible, imeBottomPx, navigationInsetPx, anchorAboveBottomPx);
            behavior.setFitToContents(false);
            behavior.setPeekHeight(targetHeight, false);
            behavior.setExpandedOffset(expandedOffset);
            if (allowExpandState && !expandedOnce) {
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                expandedOnce = true;
            }
            Log.i(logTag, "sheet_anchored target=" + targetHeight
                    + " offset=" + expandedOffset
                    + " sheetTop=" + bottomSheet.getTop()
                    + " bottomInset=" + bottomInset
                    + " anchorAbove=" + anchorAboveBottomPx
                    + " effectiveAnchor=" + effectiveAnchor
                    + " imeVisible=" + imeVisible
                    + " imeBottom=" + imeBottomPx);
        }
    }
}
