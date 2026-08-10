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

    private static final WeakHashMap<BottomSheetDialog, Integer> sAppliedHeights = new WeakHashMap<>();

    private BottomSheetAnchorHelper() {
    }

    public static final class Options {
        public boolean draggable = true;
        public boolean hideable = true;
        public boolean skipCollapsed = true;
        public boolean applyNavBarPadding = true;
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
        int navBarHeight = options.applyNavBarPadding ? getNavigationBarHeightPx(context) : 0;

        applyBottomSheetLayout(bottomSheet, targetHeight);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(false);
        behavior.setSkipCollapsed(options.skipCollapsed);
        behavior.setHideable(options.hideable);
        behavior.setDraggable(options.draggable);

        if (options.applyNavBarPadding) {
            View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : null;
            if (contentRoot != null) {
                contentRoot.setPadding(0, 0, 0, navBarHeight);
            }
        }

        AnchorState state = new AnchorState(targetHeight, screenHeight, navBarHeight, options.logTag);
        bottomSheet.post(() -> state.apply(bottomSheet, behavior, navBarHeight, true));
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

    private static int getNavigationBarHeightPx(Context context) {
        int id = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0) {
            return context.getResources().getDimensionPixelSize(id);
        }
        return Math.round(48f * context.getResources().getDisplayMetrics().density);
    }

    private static int computeExpandedOffset(
            FrameLayout bottomSheet, int screenHeight, int targetHeight, int bottomInset) {
        int coordinatorHeight = screenHeight;
        View parent = bottomSheet != null ? (View) bottomSheet.getParent() : null;
        if (parent != null && parent.getHeight() > 0) {
            coordinatorHeight = parent.getHeight();
        }
        int visibleBottom = Math.min(coordinatorHeight, screenHeight);
        return Math.max(0, visibleBottom - targetHeight - bottomInset);
    }

    private static void installInsetsListener(
            BottomSheetDialog dialog,
            FrameLayout bottomSheet,
            BottomSheetBehavior<View> behavior,
            AnchorState state) {
        View contentRoot = bottomSheet.getChildCount() > 0 ? bottomSheet.getChildAt(0) : bottomSheet;
        Context context = bottomSheet.getContext();
        final int navFallback = getNavigationBarHeightPx(context);
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, insets) -> {
            int navBottom = Math.max(
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom, navFallback);
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = imeVisible ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            int bottomInset = imeVisible ? imeBottom : navBottom;
            state.apply(bottomSheet, behavior, bottomInset, false);
            if (state.applyNavBarPadding) {
                v.setPadding(0, 0, 0, imeVisible ? 0 : navBottom);
            }
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
        private final int navBarHeight;
        private final String logTag;
        private final boolean applyNavBarPadding;
        private boolean expandedOnce;

        AnchorState(int targetHeight, int screenHeight, int navBarHeight, String logTag) {
            this.targetHeight = targetHeight;
            this.screenHeight = screenHeight;
            this.navBarHeight = navBarHeight;
            this.logTag = logTag != null ? logTag : TAG;
            this.applyNavBarPadding = true;
        }

        void apply(FrameLayout bottomSheet, BottomSheetBehavior<View> behavior,
                int bottomInset, boolean allowExpandState) {
            if (bottomSheet == null || behavior == null) {
                return;
            }
            int expandedOffset = computeExpandedOffset(
                    bottomSheet, screenHeight, targetHeight, bottomInset);
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
                    + " bottomInset=" + bottomInset);
        }
    }
}
