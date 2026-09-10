package org.libreoffice.androidlib;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Unified edge-to-edge and system bar styling for document and home activities.
 */
public final class SystemUiHelper {
    /** Bottom toolbar / home FAB chrome. */
    public static final int CHROME_TOOLBAR = 0xFFFFFFFF;
    /** Figma Rectangle 3468920 — status bar + top toolbar plate. */
    public static final int CHROME_STATUS_PLATE = 0xFFF2F2F2;
    /** Softer tone when the IME is visible so the nav area is not a harsh white strip. */
    public static final int CHROME_IME_NAV = 0xFFF0F0F0;

    private SystemUiHelper() {
    }

    public static void enableEdgeToEdge(Activity activity) {
        WindowCompat.enableEdgeToEdge(activity.getWindow());
        View decor = activity.getWindow().getDecorView();
        ViewCompat.requestApplyInsets(decor);
    }

    public static SafeAreaInsets computeSafeAreaInsets(Context context, WindowInsetsCompat insets) {
        return SafeAreaInsets.from(context, insets);
    }

    public static SafeAreaInsets readSafeAreaInsets(View anchor) {
        if (anchor == null) {
            return SafeAreaInsets.EMPTY;
        }
        return computeSafeAreaInsets(anchor.getContext(), ViewCompat.getRootWindowInsets(anchor));
    }

    public static void applyTransparentSystemBars(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    public static void applyLightSystemBarIcons(Window window, boolean lightBackground) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) {
            return;
        }
        controller.setAppearanceLightStatusBars(lightBackground);
        controller.setAppearanceLightNavigationBars(lightBackground);
    }

    /** Default chrome: Figma status plate + white navigation bar (三键区与底栏同色). */
    public static void applyDocumentChrome(Activity activity, boolean lightMode) {
        applyDocumentSystemBarColors(activity.getWindow(), lightMode, false);
    }

    /** Keyboard visible: keep status plate, soften the navigation bar. */
    public static void applyImeChrome(Activity activity, boolean lightMode) {
        applyDocumentSystemBarColors(activity.getWindow(), lightMode, true);
    }

    public static void applyDocumentSystemBarColors(Window window, boolean lightMode, boolean imeVisible) {
        window.setStatusBarColor(CHROME_STATUS_PLATE);
        window.setNavigationBarColor(imeVisible
                ? (lightMode ? CHROME_IME_NAV : Color.BLACK)
                : CHROME_TOOLBAR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        applyLightSystemBarIcons(window, lightMode);
    }

    /**
     * BottomSheet 弹窗窗口：三键导航区与文档底栏同色白底，避免系统默认灰条。
     * Activity 的 {@link #applyDocumentChrome} 不会作用于 Dialog 独立 Window。
     */
    public static void applyBottomSheetChrome(Window window, boolean lightMode) {
        if (window == null) {
            return;
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(CHROME_TOOLBAR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        applyLightSystemBarIcons(window, lightMode);
    }

    public static void applyBottomSheetChrome(Context context, Window window) {
        boolean lightMode = context instanceof Activity
                ? isLightMode((Activity) context)
                : true;
        applyBottomSheetChrome(window, lightMode);
    }

    /** Any {@link android.app.Dialog} window: force white navigation bar (三键区). */
    public static void applyDialogChrome(android.app.Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        applyBottomSheetChrome(dialog.getContext(), dialog.getWindow());
    }

    public static boolean isLightMode(Activity activity) {
        return (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_YES) == 0;
    }

    /**
     * Push content below the status bar. View height must be {@code wrap_content} (optionally with
     * {@code minHeight}); fixed heights will clip children when inset padding is applied.
     */
    public static void applyStatusBarPadding(View view, int extraTopPx) {
        if (view == null) {
            return;
        }
        final int extraTop = Math.max(0, extraTopPx);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            v.setPadding(v.getPaddingLeft(),
                    topPaddingForView(v.getContext(), windowInsets, extraTop),
                    v.getPaddingRight(), v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Keep bottom actions above the navigation / gesture area. */
    public static void applyNavigationBarPadding(View view, int extraBottomPx) {
        if (view == null) {
            return;
        }
        final int extraBottom = Math.max(0, extraBottomPx);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    bottomPaddingForView(v.getContext(), windowInsets, extraBottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Top + bottom (+ horizontal waterfall/cutout) padding on one root — for full-screen secondary
     * pages without a dedicated status plate (Settings, profile edit, HTML viewer, etc.).
     */
    public static void applyVerticalSystemBarPadding(View view, int extraTopPx, int extraBottomPx) {
        if (view == null) {
            return;
        }
        final int extraTop = Math.max(0, extraTopPx);
        final int extraBottom = Math.max(0, extraBottomPx);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Context ctx = v.getContext();
            v.setPadding(
                    resolveHorizontalSafeInsetLeft(windowInsets),
                    topPaddingForView(ctx, windowInsets, extraTop),
                    resolveHorizontalSafeInsetRight(windowInsets),
                    bottomPaddingForView(ctx, windowInsets, extraBottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Edge-to-edge chrome + {@link #applyVerticalSystemBarPadding} for secondary Activities. */
    public static void applySecondaryActivityChrome(Activity activity, View contentRoot,
            int extraTopPx, int extraBottomPx) {
        if (activity == null) {
            return;
        }
        enableEdgeToEdge(activity);
        applyDocumentChrome(activity, isLightMode(activity));
        applyVerticalSystemBarPadding(contentRoot, extraTopPx, extraBottomPx);
    }

    /** Track safe-area changes on a root content view (e.g. for FAB clamping). */
    public static void trackSafeAreaChanges(View root, SafeAreaListener listener) {
        if (root == null || listener == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            listener.onSafeAreaChanged(computeSafeAreaInsets(v.getContext(), windowInsets));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public interface SafeAreaListener {
        void onSafeAreaChanged(SafeAreaInsets insets);
    }

    /**
     * Drawer panels are not the content root; dispatch insets manually so footer buttons
     * receive navigation-bar padding under edge-to-edge.
     */
    public static void installDrawerInsetDispatch(View drawerLayout, View drawerPanel) {
        if (drawerLayout == null || drawerPanel == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            ViewCompat.dispatchApplyWindowInsets(drawerPanel, insets);
            return ViewCompat.onApplyWindowInsets(v, insets);
        });
        ViewCompat.requestApplyInsets(drawerLayout);
    }

    /** Top padding for a drawer header row (model config overlay, etc.). */
    public static void applyDrawerHeaderInsets(View header, SafeAreaInsets insets) {
        if (header == null || insets == null) {
            return;
        }
        header.setPadding(
                header.getPaddingLeft(),
                insets.top,
                header.getPaddingRight(),
                header.getPaddingBottom());
    }

    /** Bottom padding for a drawer footer row (about / save / clear cache). */
    public static void applyDrawerFooterInsets(View footer, SafeAreaInsets insets, int extraBottomPx) {
        if (footer == null || insets == null) {
            return;
        }
        int extra = Math.max(0, extraBottomPx);
        footer.setPadding(
                footer.getPaddingLeft(),
                footer.getPaddingTop(),
                footer.getPaddingRight(),
                insets.navigationBottom + getBottomSafeExtraPx(footer.getContext()) + extra);
    }

    /** Status-bar plate height; toolbar content stays at its XML fixed height. */
    public static void applyStatusBarPlateHeight(View plate, SafeAreaInsets insets) {
        if (plate == null || insets == null) {
            return;
        }
        int plateHeight = statusBarInsetPx(plate.getContext(), insets);
        ViewGroup.LayoutParams lp = plate.getLayoutParams();
        if (lp != null && lp.height != plateHeight) {
            lp.height = plateHeight;
            plate.setLayoutParams(lp);
        }
    }

    /** Resolves document top toolbar when {@code <include android:id=\"doc_top_toolbar_include\">} overrides root id. */
    public static View findDocumentTopToolbar(View root) {
        if (root == null) {
            return null;
        }
        View toolbar = root.findViewById(R.id.doc_top_toolbar_include);
        if (toolbar != null) {
            return toolbar;
        }
        return root.findViewById(R.id.doc_top_toolbar);
    }

    /** Status-bar plate height on {@code doc_top_status_plate}; toolbar content stays 56dp. */
    public static void applyTopToolbarInsets(View topToolbar, SafeAreaInsets insets) {
        if (topToolbar == null || insets == null) {
            return;
        }
        View plate = topToolbar.findViewById(R.id.doc_top_status_plate);
        if (plate != null) {
            applyStatusBarPlateHeight(plate, insets);
        } else {
            topToolbar.setPadding(0, statusBarInsetPx(topToolbar.getContext(), insets), 0, 0);
        }
        applyTopToolbarContentHorizontalInsets(topToolbar, insets);
    }

    /** Keep toolbar buttons out of cutout / waterfall side regions (landscape camera hole, curved edges). */
    public static void applyTopToolbarContentHorizontalInsets(View topToolbar, SafeAreaInsets insets) {
        if (topToolbar == null || insets == null) {
            return;
        }
        int previewBasePad = topToolbar.getResources().getDimensionPixelSize(
                R.dimen.doc_top_toolbar_content_padding_h);
        int editStartPad = topToolbar.getResources().getDimensionPixelSize(
                R.dimen.top_toolbar_edit_padding_start);
        int editEndPad = topToolbar.getResources().getDimensionPixelSize(
                R.dimen.top_toolbar_edit_padding_end);
        applyHorizontalContentPadding(topToolbar.findViewById(R.id.top_toolbar_preview),
                insets.left + previewBasePad, insets.right + previewBasePad);
        applyHorizontalContentPadding(topToolbar.findViewById(R.id.top_toolbar_edit),
                insets.left + editStartPad, insets.right + editEndPad);
    }

    public static void applyHorizontalContentPadding(View view, int leftPx, int rightPx) {
        if (view == null) {
            return;
        }
        view.setPaddingRelative(Math.max(0, leftPx), view.getPaddingTop(),
                Math.max(0, rightPx), view.getPaddingBottom());
    }

    /** Explicit bottom nav reservation strip (三键导航区) below the 82dp toolbar band. */
    public static void applyBottomNavSpacer(View spacer, int navigationInsetPx) {
        if (spacer == null) {
            return;
        }
        int height = Math.max(0, navigationInsetPx);
        ViewGroup.LayoutParams lp = spacer.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            spacer.setLayoutParams(lp);
        }
        spacer.setVisibility(height > 0 ? View.VISIBLE : View.GONE);
    }

    /** Raw status-bar inset without the extra top safe margin. */
    public static int statusBarInsetPx(Context context, SafeAreaInsets insets) {
        if (insets == null) {
            return 0;
        }
        return Math.max(0, insets.top - getTopSafeExtraPx(context));
    }

    /** Bottom sheet / dialog anchor offset (system inset + safe margin, no caller extra). */
    public static int bottomInsetForSheet(Context context, WindowInsetsCompat insets) {
        return resolveNavigationBottomInset(context, insets) + getBottomSafeExtraPx(context);
    }

    /**
     * Inset to subtract in BottomSheet {@code expandedOffset} math.
     * Uses system navigation inset (+ safe margin via {@link #bottomInsetForSheet}).
     * When {@code anchorAboveBottomPx} is non-zero (rare inset-layout sheets), nav is not double-subtracted.
     */
    public static int resolveSheetAnchorBottomInset(boolean imeVisible, int imeBottomPx,
            int navigationInsetPx, int anchorAboveBottomPx) {
        if (imeVisible && imeBottomPx > 0) {
            return imeBottomPx;
        }
        if (anchorAboveBottomPx > 0) {
            return 0;
        }
        return Math.max(0, navigationInsetPx);
    }

    /**
     * Document bottom chrome anchor is ignored while sheet IME is active (keyboard lifts the sheet).
     */
    public static int resolveSheetAnchorAboveBottomPx(int anchorAboveBottomPx,
            boolean imeVisible, int imeBottomPx) {
        if (imeVisible && imeBottomPx > 0) {
            return 0;
        }
        return Math.max(0, anchorAboveBottomPx);
    }

    public static int computeSheetExpandedOffset(int visibleBottom, int targetHeight,
            boolean imeVisible, int imeBottomPx, int navigationInsetPx, int anchorAboveBottomPx) {
        int effectiveAnchor = resolveSheetAnchorAboveBottomPx(
                anchorAboveBottomPx, imeVisible, imeBottomPx);
        int bottomInset = resolveSheetAnchorBottomInset(
                imeVisible, imeBottomPx, navigationInsetPx, effectiveAnchor);
        return Math.max(0, visibleBottom - targetHeight - bottomInset - effectiveAnchor);
    }

    /** Content padding inside a bottom sheet (visual margin only; anchor offset handles nav inset). */
    public static int bottomContentSafePaddingPx(Context context) {
        return getBottomSafeExtraPx(context);
    }

    public static int topPaddingForView(Context context, WindowInsetsCompat insets, int extraTopPx) {
        Insets status = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
        return status.top + Math.max(0, extraTopPx) + getTopSafeExtraPx(context);
    }

    public static int bottomPaddingForView(Context context, WindowInsetsCompat insets, int extraBottomPx) {
        return resolveNavigationBottomInset(context, insets) + Math.max(0, extraBottomPx) + getBottomSafeExtraPx(context);
    }

    /** Bottom inset for toolbar padding / FAB clearance when IME is not replacing layout margin. */
    public static int resolveBottomInset(Context context, WindowInsetsCompat insets) {
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            return insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        }
        return resolveNavigationBottomInset(context, insets);
    }

    /**
     * Bottom navigation / gesture obstruction. Prefers {@link WindowInsetsCompat} (incl.
     * {@code tappableElement}); static fallbacks apply only when the system reports 0.
     */
    public static int resolveNavigationBottomInset(Context context, WindowInsetsCompat insets) {
        if (insets == null) {
            return context != null ? getGestureMinInsetPx(context) : 0;
        }
        Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        Insets gesture = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
        Insets tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement());
        Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
        int resolved = Math.max(nav.bottom,
                Math.max(gesture.bottom, Math.max(tappable.bottom, cutout.bottom)));
        if (resolved > 0) {
            return resolved;
        }
        if (context == null) {
            return 0;
        }
        if (isThreeButtonNavigation(context)) {
            return getNavBarMinInsetPx(context);
        }
        return getGestureMinInsetPx(context);
    }

    /** Left edge: systemBars + displayCutout + waterfall (curved / waterfall displays). */
    public static int resolveHorizontalSafeInsetLeft(WindowInsetsCompat insets) {
        return resolveSideObstructionInsets(insets).left;
    }

    /** Right edge: systemBars + displayCutout + waterfall. */
    public static int resolveHorizontalSafeInsetRight(WindowInsetsCompat insets) {
        return resolveSideObstructionInsets(insets).right;
    }

    private static Insets resolveSideObstructionInsets(WindowInsetsCompat insets) {
        if (insets == null) {
            return Insets.NONE;
        }
        // Waterfall edges are reported as part of displayCutout since API 30,
        // so max(systemBars, cutout) already covers them.
        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
        return Insets.of(
                Math.max(systemBars.left, cutout.left),
                Math.max(systemBars.top, cutout.top),
                Math.max(systemBars.right, cutout.right),
                Math.max(systemBars.bottom, cutout.bottom));
    }

    private static boolean isThreeButtonNavigation(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                int mode = Settings.Secure.getInt(context.getContentResolver(), "navigation_mode", -1);
                return mode == 0;
            } catch (Exception ignored) {
                // fall through
            }
        }
        return false;
    }

    public static int getBottomSafeExtraPx(Context context) {
        if (context == null) {
            return 0;
        }
        return context.getResources().getDimensionPixelSize(R.dimen.lolib_inset_bottom_safe_extra);
    }

    public static int getTopSafeExtraPx(Context context) {
        if (context == null) {
            return 0;
        }
        return context.getResources().getDimensionPixelSize(R.dimen.lolib_inset_top_safe_extra);
    }

    /** Keep centered dialog buttons above navigation / curved-screen edges. */
    public static void applyCenteredDialogSafeInsets(android.app.Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        Window window = dialog.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        View decor = window.getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decor, (v, windowInsets) -> {
            SafeAreaInsets safe = computeSafeAreaInsets(v.getContext(), windowInsets);
            View content = v.findViewById(android.R.id.content);
            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                View root = ((ViewGroup) content).getChildAt(0);
                // 保留布局 XML 的设计底部 padding(如 12dp),按钮不贴底;
                // 另补居中弹窗进导航区的最小补偿 (safe.bottom-safe.top)/2,避免大 padding 留白。
                int bottomPad = Math.max(root.getPaddingBottom(),
                        Math.max(0, (safe.bottom - safe.top) / 2));
                root.setPadding(safe.left, root.getPaddingTop(), safe.right, bottomPad);
            }
            // 仅消费已手动处理的 systemBars / cutout，保留 IME inset 供软键盘布局。
            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                    .build();
        });
        ViewCompat.requestApplyInsets(decor);
    }

    private static int getGestureMinInsetPx(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.lolib_inset_gesture_min);
    }

    private static int getNavBarMinInsetPx(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.lolib_inset_nav_bar_min);
    }
}
