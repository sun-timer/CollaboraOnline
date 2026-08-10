package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.HashMap;
import java.util.Map;

public class BottomToolbarController {
    private static final String TAG = "BottomToolbarController";

    private static final int[] PREVIEW_MODE_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_mobile_preview,
            R.id.toolbar_item_function,
            R.id.toolbar_item_ai_assistant
    };
    private static final int[] EDIT_MODE_COMMON_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_ai_feature,
            R.id.toolbar_item_keyboard,
            R.id.toolbar_item_character
    };
    private static final int[] EDIT_MODE_WRITER_ONLY_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_paragraph,
            R.id.toolbar_item_insert_image
    };
    private static final int[] EDIT_MODE_CALC_ONLY_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_fill_cell,
            R.id.toolbar_item_merge_cell
    };
    private static final int[] TOOLBAR_ITEM_DISPLAY_ORDER = new int[] {
            R.id.toolbar_item_mobile_preview,
            R.id.toolbar_item_function,
            R.id.toolbar_item_ai_assistant,
            R.id.toolbar_item_ai_feature,
            R.id.toolbar_item_keyboard,
            R.id.toolbar_item_character,
            R.id.toolbar_item_paragraph,
            R.id.toolbar_item_insert_image,
            R.id.toolbar_item_fill_cell,
            R.id.toolbar_item_merge_cell
    };
    private static final int[] ALL_TOOLBAR_ITEM_IDS = TOOLBAR_ITEM_DISPLAY_ORDER;
    private static final int TOOLBAR_DEFAULT_HEIGHT_DP = 82;
    private static final int TOOLBAR_LANDSCAPE_HEIGHT_DP = 82;
    private static final int TOOLBAR_LANDSCAPE_ITEM_GAP_DP = 80;
    private static final int TOOLBAR_LANDSCAPE_ITEM_DEFAULT_WIDTH_DP = 108;
    private static final int TOOLBAR_LANDSCAPE_EDGE_PADDING_DP = 12;
    private static final int TOOLBAR_COMPACT_HEIGHT_DP = 48;
    private static final int TOOLBAR_IME_EXTRA_GAP_DP = 4;
    private static final int TOOLBAR_ITEM_COMPACT_WIDTH_DP = 74;
    private static final int QUICK_ACTION_ICON_SIZE_DP = 42;
    private static final int QUICK_ACTION_BUTTON_MIN_WIDTH_DP = 64;
    private static final int QUICK_ACTION_BUTTON_MIN_HEIGHT_DP = 56;
    private static final int QUICK_ACTION_BUTTON_PADDING_H_DP = 8;
    private static final int QUICK_ACTION_BUTTON_PADDING_V_DP = 6;
    private static final int QUICK_ACTION_BUTTON_MARGIN_END_DP = 12;
    private static final int COLOR_CHIP_SIZE_DP = 44;
    private static final int COLOR_CHIP_MARGIN_DP = 8;
    private static final ColorOption[] FONT_COLOR_OPTIONS = new ColorOption[] {
            new ColorOption("黑色", 0x000000),
            new ColorOption("红色", 0xE53935),
            new ColorOption("橙色", 0xFB8C00),
            new ColorOption("黄色", 0xFDD835),
            new ColorOption("绿色", 0x43A047),
            new ColorOption("蓝色", 0x1E88E5),
            new ColorOption("紫色", 0x8E24AA),
            new ColorOption("灰色", 0x757575)
    };
    private static final ColorOption[] HIGHLIGHT_COLOR_OPTIONS = new ColorOption[] {
            new ColorOption("黄色", 0xFFFF00),
            new ColorOption("浅绿", 0xC6EFCE),
            new ColorOption("浅蓝", 0xBDD7EE),
            new ColorOption("浅红", 0xFFC7CE),
            new ColorOption("橙色", 0xF4B183),
            new ColorOption("紫色", 0xD9E1F2),
            new ColorOption("灰色", 0xD9D9D9),
            new ColorOption("白色", 0xFFFFFF)
    };
    private static final ColorOption[] CELL_FILL_COLOR_OPTIONS = HIGHLIGHT_COLOR_OPTIONS;

    private static final QuickActionItem[] CHARACTER_QUICK_ACTION_ITEMS = new QuickActionItem[] {
            new QuickActionItem(R.drawable.lolib_ic_quick_bold, "粗体", ".uno:Bold"),
            new QuickActionItem(R.drawable.lolib_ic_quick_italic, "斜体", ".uno:Italic"),
            new QuickActionItem(R.drawable.lolib_ic_quick_underline, "下划线", ".uno:Underline"),
            new QuickActionItem(R.drawable.lolib_ic_quick_strikeout, "删除线", ".uno:Strikeout"),
            new QuickActionItem(R.drawable.lolib_ic_quick_fontcolor, "字色", QuickActionType.FONT_COLOR),
            new QuickActionItem(R.drawable.lolib_ic_quick_highlight, "高亮", QuickActionType.HIGHLIGHT_COLOR)
    };
    private static final QuickActionItem[] PARAGRAPH_QUICK_ACTION_ITEMS = new QuickActionItem[] {
            new QuickActionItem(R.drawable.lolib_ic_quick_align_left, "左对齐", ".uno:LeftPara"),
            new QuickActionItem(R.drawable.lolib_ic_quick_align_center, "居中对齐", ".uno:CenterPara"),
            new QuickActionItem(R.drawable.lolib_ic_quick_align_right, "右对齐", ".uno:RightPara"),
            new QuickActionItem(R.drawable.lolib_ic_quick_align_justify, "两端对齐", ".uno:JustifyPara"),
            new QuickActionItem(R.drawable.lolib_ic_quick_bullet, "项目符号", ".uno:DefaultBullet"),
            new QuickActionItem(R.drawable.lolib_ic_quick_numbering, "编号", ".uno:DefaultNumbering")
    };

    public interface Host {
        android.content.Context getContext();

        View findViewById(int id);

        int dpToPx(int dp);

        void runOnUiThread(Runnable runnable);

        void showFunctionPanel();

        void switchToViewingMode();

        void toggleMobilePhonePreview();

        void switchToEditMode();

        void showNativeAiPanel();

        void showNativeAiOperationSheet();

        void toastTodo(String text);

        void focusDocumentAndShowIme();

        void openLocalImagePickerFromWeb();

        void executeUnoCommand(String command);

        void fetchCharFormatState(CharFormatCallback callback);
    }

    public interface CharFormatCallback {
        void onResult(boolean bold, boolean italic, boolean underline, boolean strikethrough);
    }

    private final Host host;
    private LinearLayout bottomToolbarView;
    private LinearLayout bottomToolbarItemsRow;
    private View quickActionOverlayView;
    private LinearLayout quickActionPanelView;
    private LinearLayout quickActionActionsRow;
    private final Map<Integer, Integer> toolbarBaseItemWidths = new HashMap<>();
    private boolean bottomToolbarCompactMode = false;
    private int bottomToolbarBaseHeightPx = -1;
    private int bottomToolbarImeInsetPx = 0;
    private int navigationBarInsetPx = 0;
    private boolean isImeVisibleForToolbar = false;
    private boolean isEditModeActive = false;
    private boolean isCalcDocument = false;
    private boolean isImpressDocument = false;
    private QuickActionGroup activeQuickActionGroup = QuickActionGroup.NONE;

    public BottomToolbarController(Host host) {
        this.host = host;
    }

    public void setup() {
        bottomToolbarView = asLinearLayout(host.findViewById(R.id.doc_bottom_toolbar));
        bottomToolbarItemsRow = asLinearLayout(host.findViewById(R.id.doc_bottom_toolbar_items_row));
        quickActionOverlayView = host.findViewById(R.id.toolbar_quick_overlay);
        quickActionPanelView = asLinearLayout(host.findViewById(R.id.toolbar_quick_panel));
        quickActionActionsRow = asLinearLayout(host.findViewById(R.id.toolbar_quick_actions));
        cacheToolbarBaseMetricsIfNeeded();
        setupQuickActionPanel();

        bindToolbarClick(R.id.toolbar_item_function, v -> {
            hideQuickActionPanel();
            host.showFunctionPanel();
        });
        bindToolbarClick(R.id.toolbar_item_mobile_preview, v -> {
            hideQuickActionPanel();
            host.toggleMobilePhonePreview();
        });
        bindToolbarClick(R.id.toolbar_item_ai_assistant, v -> {
            hideQuickActionPanel();
            host.showNativeAiPanel();
        });
        bindToolbarClick(R.id.toolbar_item_ai_feature, v -> {
            hideQuickActionPanel();
            host.showNativeAiOperationSheet();
        });
        bindToolbarClick(R.id.toolbar_item_keyboard, v -> {
            hideQuickActionPanel();
            host.focusDocumentAndShowIme();
        });
        bindToolbarClick(R.id.toolbar_item_character, v -> toggleQuickActionPanel(QuickActionGroup.CHARACTER));
        bindToolbarClick(R.id.toolbar_item_paragraph, v -> toggleQuickActionPanel(QuickActionGroup.PARAGRAPH));
        bindToolbarClick(R.id.toolbar_item_insert_image, v -> {
            hideQuickActionPanel();
            host.openLocalImagePickerFromWeb();
        });
        bindToolbarClick(R.id.toolbar_item_fill_cell, v -> {
            hideQuickActionPanel();
            showColorPicker("选择单元格填充颜色", ".uno:BackgroundColor",
                    "BackgroundColor.Color", CELL_FILL_COLOR_OPTIONS);
        });
        bindToolbarClick(R.id.toolbar_item_merge_cell, v -> {
            hideQuickActionPanel();
            showMergeOptions();
        });

        applyImeState(isImeVisibleForToolbar, bottomToolbarImeInsetPx, navigationBarInsetPx);
        updateEditModeState(isEditModeActive, "toolbar_setup");
        if (bottomToolbarItemsRow != null) {
            bottomToolbarItemsRow.post(() -> applyBottomToolbarItemsAlignment(isEditModeActive));
        }
    }

    /** 横竖屏切换：清空残留 layout 后按当前方向重建（不继承另一方向的间距/宽度）。 */
    public void onConfigurationChanged() {
        Runnable task = () -> {
            resetAllToolbarItemLayoutState();
            applyBottomToolbarMode(isEditModeActive);
            Log.i(TAG, "bottom_toolbar_orientation_change landscape=" + isLandscape()
                    + " edit=" + isEditModeActive);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            host.runOnUiThread(task);
        }
    }

    private void resetAllToolbarItemLayoutState() {
        if (bottomToolbarItemsRow != null) {
            bottomToolbarItemsRow.setPadding(0, 0, 0, 0);
        }
        resetLandscapeToolbarItemMargins();
        for (int itemId : TOOLBAR_ITEM_DISPLAY_ORDER) {
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            lp.weight = 0f;
            lp.setMarginStart(0);
            item.setLayoutParams(lp);
        }
    }

    public void updateDocumentType(boolean isCalc, boolean isImpress) {
        isCalcDocument = isCalc;
        isImpressDocument = isImpress;
        Runnable applyTask = () -> {
            applyBottomToolbarMode(isEditModeActive);
            if (isCalcDocument) {
                hideQuickActionPanel();
            }
            Log.i(TAG, "bottom_toolbar_doc_type isCalc=" + isCalcDocument
                    + " isImpress=" + isImpressDocument);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyTask.run();
        } else {
            host.runOnUiThread(applyTask);
        }
    }

    public void updateEditModeState(boolean isEditMode, String reason) {
        isEditModeActive = isEditMode;
        Runnable applyTask = () -> {
            applyBottomToolbarMode(isEditMode);
            applyImeState(isImeVisibleForToolbar, bottomToolbarImeInsetPx, navigationBarInsetPx);
            Log.i(TAG, "bottom_toolbar_mode edit=" + isEditMode + " reason=" + reason);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyTask.run();
        } else {
            host.runOnUiThread(applyTask);
        }
    }

    public void applyImeState(boolean imeVisible, int imeInsetBottom) {
        applyImeState(imeVisible, imeInsetBottom, navigationBarInsetPx);
    }

    public void applyImeState(boolean imeVisible, int imeInsetBottom, int navigationBarInsetBottom) {
        isImeVisibleForToolbar = imeVisible;
        bottomToolbarImeInsetPx = Math.max(0, imeInsetBottom);
        navigationBarInsetPx = Math.max(0, navigationBarInsetBottom);
        int bottomMargin = imeVisible
                ? Math.max(bottomToolbarImeInsetPx, navigationBarInsetPx)
                        + host.dpToPx(TOOLBAR_IME_EXTRA_GAP_DP)
                : navigationBarInsetPx;
        setBottomToolbarBottomMargin(bottomMargin);
        applyBottomToolbarCompactMode(imeVisible && isEditModeActive);
    }

    public void hideQuickActionPanel() {
        if (quickActionOverlayView != null) {
            quickActionOverlayView.setVisibility(View.GONE);
        }
        if (quickActionPanelView != null) {
            quickActionPanelView.setVisibility(View.GONE);
        }
        activeQuickActionGroup = QuickActionGroup.NONE;
        updateQuickActionToggleState();
    }

    public int getReservedBottomHeightPx() {
        if (bottomToolbarView != null && bottomToolbarView.getVisibility() == View.VISIBLE) {
            return bottomToolbarView.getHeight() + host.dpToPx(16);
        }
        return 0;
    }

    private void bindToolbarClick(int viewId, View.OnClickListener listener) {
        View view = host.findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(v -> {
                Log.i(TAG, "bottom_toolbar_click id=" + viewId);
                listener.onClick(v);
            });
        }
    }

    private void applyBottomToolbarMode(boolean isEditMode) {
        setBottomToolbarItemsVisible(PREVIEW_MODE_TOOLBAR_ITEM_IDS, true);
        setBottomToolbarItemsVisible(EDIT_MODE_COMMON_TOOLBAR_ITEM_IDS, isEditMode);
        setBottomToolbarItemsVisible(EDIT_MODE_WRITER_ONLY_TOOLBAR_ITEM_IDS, isEditMode && !isCalcDocument);
        setBottomToolbarItemsVisible(EDIT_MODE_CALC_ONLY_TOOLBAR_ITEM_IDS, isEditMode && isCalcDocument);
        applyBottomToolbarItemsAlignment(isEditMode);
        updateMobilePreviewToolbarItem(isEditMode);
        if (!isEditMode) {
            hideQuickActionPanel();
        }
    }

    private void updateMobilePreviewToolbarItem(boolean isEditMode) {
        View iconView = host.findViewById(R.id.toolbar_item_mobile_preview_icon);
        View labelView = host.findViewById(R.id.toolbar_item_mobile_preview_label);
        if (iconView instanceof ImageView) {
            ((ImageView) iconView).setImageResource(R.drawable.lolib_ic_toolbar_mobile_preview);
        }
        if (labelView instanceof TextView) {
            ((TextView) labelView).setText("手机预览");
            ((TextView) labelView).setTextColor(0xFF202124);
        }
    }

    private void applyBottomToolbarItemsAlignment(boolean isEditMode) {
        if (bottomToolbarItemsRow == null) {
            return;
        }
        if (isLandscape()) {
            applyLandscapeToolbarLayout(isEditMode);
            return;
        }
        ViewGroup.LayoutParams params = bottomToolbarItemsRow.getLayoutParams();
        if (params == null) {
            return;
        }
        params.width = isEditMode ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        bottomToolbarItemsRow.setLayoutParams(params);
        bottomToolbarItemsRow.setGravity(isEditMode
                ? android.view.Gravity.CENTER_VERTICAL
                : android.view.Gravity.CENTER_VERTICAL);
        bottomToolbarItemsRow.setPadding(
                isEditMode ? host.dpToPx(10) : 0,
                0,
                isEditMode ? host.dpToPx(10) : 0,
                0);
        applyPreviewToolbarItemWidths(!isEditMode);
        restorePortraitToolbarItemSpacing();
        updatePortraitToolbarHeight();
    }

    private void applyLandscapeToolbarLayout(boolean isEditMode) {
        if (bottomToolbarItemsRow == null) {
            return;
        }
        resetLandscapeToolbarItemMargins();
        if (!isEditMode) {
            applyLandscapePreviewLayout();
            return;
        }
        applyLandscapeEditLayout();
    }

    /** 清除所有按钮的横屏间距，避免 GONE 项残留 edit 布局参数。 */
    private void resetLandscapeToolbarItemMargins() {
        for (int itemId : TOOLBAR_ITEM_DISPLAY_ORDER) {
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            lp.setMarginStart(0);
            lp.weight = 0f;
            item.setLayoutParams(lp);
        }
    }

    /** 横屏预览：三等分均布（与竖屏预览一致）。 */
    private void applyLandscapePreviewLayout() {
        ViewGroup.LayoutParams params = bottomToolbarItemsRow.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomToolbarItemsRow.setLayoutParams(params);
        }
        bottomToolbarItemsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottomToolbarItemsRow.setPadding(0, host.dpToPx(6), 0, host.dpToPx(6));

        for (int itemId : PREVIEW_MODE_TOOLBAR_ITEM_IDS) {
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            lp.width = 0;
            lp.weight = 1f;
            lp.setMarginStart(0);
            item.setLayoutParams(lp);
        }

        for (int itemId : TOOLBAR_ITEM_DISPLAY_ORDER) {
            if (isPreviewToolbarItem(itemId)) {
                continue;
            }
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            lp.width = getLandscapeToolbarItemWidthPx(itemId);
            lp.weight = 0f;
            item.setLayoutParams(lp);
        }

        updateLandscapeToolbarHeight();
    }

    /** 横屏编辑：固定间距 + 居中（Figma 快捷功能栏）。 */
    private void applyLandscapeEditLayout() {
        ViewGroup.LayoutParams params = bottomToolbarItemsRow.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            bottomToolbarItemsRow.setLayoutParams(params);
        }
        bottomToolbarItemsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottomToolbarItemsRow.setPadding(0, host.dpToPx(6), 0, host.dpToPx(6));

        int gapPx = host.dpToPx(TOOLBAR_LANDSCAPE_ITEM_GAP_DP);
        boolean firstVisible = true;
        for (int itemId : TOOLBAR_ITEM_DISPLAY_ORDER) {
            View item = host.findViewById(itemId);
            if (item == null || item.getVisibility() != View.VISIBLE) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            lp.width = getLandscapeToolbarItemWidthPx(itemId);
            lp.weight = 0f;
            lp.setMarginStart(firstVisible ? 0 : gapPx);
            item.setLayoutParams(lp);
            configureLandscapeToolbarItemLabel(itemId);
            firstVisible = false;
        }

        updateLandscapeToolbarHeight();
        scheduleLandscapeToolbarCentering();
    }

    private void scheduleLandscapeToolbarCentering() {
        if (bottomToolbarItemsRow == null) {
            return;
        }
        bottomToolbarItemsRow.post(this::centerToolbarItemsRowIfNeeded);
        ViewTreeObserver observer = bottomToolbarItemsRow.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver current = bottomToolbarItemsRow.getViewTreeObserver();
                if (current.isAlive()) {
                    current.removeOnGlobalLayoutListener(this);
                }
                centerToolbarItemsRowIfNeeded();
            }
        });
    }

    private static boolean isPreviewToolbarItem(int itemId) {
        for (int previewId : PREVIEW_MODE_TOOLBAR_ITEM_IDS) {
            if (previewId == itemId) {
                return true;
            }
        }
        return false;
    }

    private void restorePortraitToolbarItemSpacing() {
        for (int itemId : TOOLBAR_ITEM_DISPLAY_ORDER) {
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            lp.setMarginStart(0);
            item.setLayoutParams(lp);
        }
    }

    private void centerToolbarItemsRowIfNeeded() {
        if (!isLandscape() || bottomToolbarItemsRow == null) {
            return;
        }
        View parent = bottomToolbarItemsRow.getParent() instanceof View
                ? (View) bottomToolbarItemsRow.getParent() : null;
        if (parent == null) {
            return;
        }
        int parentWidth = parent.getWidth();
        int contentWidth = bottomToolbarItemsRow.getWidth();
        if (parentWidth <= 0 || contentWidth <= 0) {
            return;
        }
        int edgePad = host.dpToPx(TOOLBAR_LANDSCAPE_EDGE_PADDING_DP);
        int horizontalPad = contentWidth + edgePad * 2 <= parentWidth
                ? Math.max(edgePad, (parentWidth - contentWidth) / 2)
                : edgePad;
        bottomToolbarItemsRow.setPadding(horizontalPad, host.dpToPx(6), horizontalPad, host.dpToPx(6));
    }

    private void updateLandscapeToolbarHeight() {
        if (bottomToolbarView == null || bottomToolbarCompactMode) {
            return;
        }
        ViewGroup.LayoutParams toolbarLp = bottomToolbarView.getLayoutParams();
        if (toolbarLp == null) {
            return;
        }
        int targetHeight = host.dpToPx(TOOLBAR_LANDSCAPE_HEIGHT_DP);
        if (toolbarLp.height != targetHeight) {
            toolbarLp.height = targetHeight;
            bottomToolbarView.setLayoutParams(toolbarLp);
        }
    }

    private void updatePortraitToolbarHeight() {
        if (bottomToolbarView == null || bottomToolbarCompactMode) {
            return;
        }
        ViewGroup.LayoutParams toolbarLp = bottomToolbarView.getLayoutParams();
        if (toolbarLp == null) {
            return;
        }
        int targetHeight = bottomToolbarBaseHeightPx > 0
                ? bottomToolbarBaseHeightPx
                : host.dpToPx(TOOLBAR_DEFAULT_HEIGHT_DP);
        if (toolbarLp.height != targetHeight) {
            toolbarLp.height = targetHeight;
            bottomToolbarView.setLayoutParams(toolbarLp);
        }
    }

    private boolean isLandscape() {
        return host.getContext().getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyPreviewToolbarItemWidths(boolean distributeEvenly) {
        for (int itemId : PREVIEW_MODE_TOOLBAR_ITEM_IDS) {
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams rawParams = item.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawParams;
            if (distributeEvenly) {
                lp.width = 0;
                lp.weight = 1f;
            } else {
                lp.weight = 0f;
                lp.width = toolbarBaseItemWidths.getOrDefault(itemId, host.dpToPx(92));
            }
            item.setLayoutParams(lp);
        }
    }

    private void setBottomToolbarItemsVisible(int[] itemIds, boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        for (int itemId : itemIds) {
            View item = host.findViewById(itemId);
            if (item != null) {
                item.setVisibility(visibility);
            }
        }
    }

    private void setupQuickActionPanel() {
        if (quickActionOverlayView != null) {
            quickActionOverlayView.setOnClickListener(v -> hideQuickActionPanel());
        }
        if (quickActionPanelView != null) {
            quickActionPanelView.setOnClickListener(v -> {
                // Consume clicks so taps inside the panel don't close it.
            });
        }
        hideQuickActionPanel();
    }

    private void toggleQuickActionPanel(QuickActionGroup group) {
        if (!isEditModeActive) {
            return;
        }
        if (activeQuickActionGroup == group && isQuickActionPanelVisible()) {
            hideQuickActionPanel();
            return;
        }
        showQuickActionGroup(group);
    }

    private boolean isQuickActionPanelVisible() {
        return quickActionPanelView != null && quickActionPanelView.getVisibility() == View.VISIBLE;
    }

    private void showQuickActionGroup(QuickActionGroup group) {
        if (quickActionPanelView == null || quickActionActionsRow == null || quickActionOverlayView == null) {
            return;
        }
        final QuickActionItem[] items = group == QuickActionGroup.CHARACTER
                ? CHARACTER_QUICK_ACTION_ITEMS
                : PARAGRAPH_QUICK_ACTION_ITEMS;
        quickActionActionsRow.removeAllViews();
        for (QuickActionItem item : items) {
            quickActionActionsRow.addView(createQuickActionButton(item));
        }

        activeQuickActionGroup = group;
        quickActionOverlayView.setVisibility(View.VISIBLE);
        quickActionPanelView.setVisibility(View.VISIBLE);
        updateQuickActionToggleState();

        // Fetch current char format state to highlight active toggle buttons
        if (group == QuickActionGroup.CHARACTER) {
            host.fetchCharFormatState((bold, italic, underline, strikethrough) -> {
                updateQuickActionToggleVisual(".uno:Bold", bold);
                updateQuickActionToggleVisual(".uno:Italic", italic);
                updateQuickActionToggleVisual(".uno:Underline", underline);
                updateQuickActionToggleVisual(".uno:Strikeout", strikethrough);
            });
        }
    }

    private View createQuickActionButton(QuickActionItem item) {
        AppCompatImageButton button = new AppCompatImageButton(host.getContext());
        button.setImageResource(item.iconResId);
        button.setImageTintList(null);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setAdjustViewBounds(true);
        button.setMaxWidth(host.dpToPx(QUICK_ACTION_ICON_SIZE_DP));
        button.setMaxHeight(host.dpToPx(QUICK_ACTION_ICON_SIZE_DP));
        button.setMinimumWidth(host.dpToPx(QUICK_ACTION_BUTTON_MIN_WIDTH_DP));
        button.setMinimumHeight(host.dpToPx(QUICK_ACTION_BUTTON_MIN_HEIGHT_DP));
        button.setPadding(
                host.dpToPx(QUICK_ACTION_BUTTON_PADDING_H_DP),
                host.dpToPx(QUICK_ACTION_BUTTON_PADDING_V_DP),
                host.dpToPx(QUICK_ACTION_BUTTON_PADDING_H_DP),
                host.dpToPx(QUICK_ACTION_BUTTON_PADDING_V_DP));
        button.setBackgroundResource(R.drawable.lolib_bg_quick_action_chip);
        button.setContentDescription(item.contentDescription);
        if (item.unoCommand != null && !item.unoCommand.isEmpty()) {
            button.setTag(item.unoCommand);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(host.dpToPx(QUICK_ACTION_BUTTON_MARGIN_END_DP));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> executeQuickAction(item));
        return button;
    }

    private void executeQuickAction(QuickActionItem item) {
        if (item.type == QuickActionType.FONT_COLOR) {
            showColorPicker("选择字体颜色", ".uno:FontColor", "FontColor.Color", FONT_COLOR_OPTIONS);
            return;
        }
        if (item.type == QuickActionType.HIGHLIGHT_COLOR) {
            showColorPicker("选择填充颜色", ".uno:CharBackColor", "CharBackColor.Color", HIGHLIGHT_COLOR_OPTIONS);
            return;
        }
        host.executeUnoCommand(item.unoCommand);
    }

    private void updateQuickActionToggleVisual(String unoCommand, boolean active) {
        if (quickActionActionsRow == null) {
            return;
        }
        for (int i = 0; i < quickActionActionsRow.getChildCount(); i++) {
            View child = quickActionActionsRow.getChildAt(i);
            if (!(child instanceof AppCompatImageButton)) {
                continue;
            }
            Object tag = child.getTag();
            if (unoCommand.equals(tag)) {
                child.setSelected(active);
                if (active) {
                    child.getBackground().mutate().setTint(0xFF1A73E8);
                    ((AppCompatImageButton) child).setImageTintList(
                            android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                }
                break;
            }
        }
    }

    private void showColorPicker(String title, String unoCommand, String propertyName, ColorOption[] options) {
        LinearLayout container = new LinearLayout(host.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(host.dpToPx(12), host.dpToPx(8), host.dpToPx(12), host.dpToPx(8));

        LinearLayout row = null;
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (int i = 0; i < options.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(host.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER);
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            ColorOption option = options[i];
            TextView chip = new TextView(host.getContext());
            chip.setContentDescription(option.label);
            chip.setBackground(createColorChipBackground(option.rgb));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    host.dpToPx(COLOR_CHIP_SIZE_DP),
                    host.dpToPx(COLOR_CHIP_SIZE_DP));
            lp.setMargins(
                    host.dpToPx(COLOR_CHIP_MARGIN_DP),
                    host.dpToPx(COLOR_CHIP_MARGIN_DP),
                    host.dpToPx(COLOR_CHIP_MARGIN_DP),
                    host.dpToPx(COLOR_CHIP_MARGIN_DP));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                host.executeUnoCommand(buildColorUnoCommand(unoCommand, propertyName, option.rgb));
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
            });
            if (row != null) {
                row.addView(chip);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(host.getContext())
                .setTitle(title)
                .setView(container)
                .setNegativeButton("取消", null)
                .create();
        dialogRef[0] = dialog;
        dialog.show();
        Log.i(TAG, "show_quick_color_picker command=" + unoCommand);
    }

    private static final String[] MERGE_OPTION_LABELS = {
            "合并内容", "合并单元格", "合并相同单元格"
    };
    private static final String[] MERGE_OPTION_COMMANDS = {
            ".uno:MergeCells?MoveContents:bool=true",
            ".uno:MergeCells?MoveContents:bool=false",
            ".uno:MergeCells?MoveContents:bool=false",
    };

    /** 合并单元格选项弹窗（Figma 5274:56201）：标题栏 + 提示 + 3 单选选项（各带示意图）+ 确定。 */
    private void showMergeOptions() {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // 标题栏：返回箭头 + 标题，底部 1px 灰边（Figma 750×86px → 43dp 高）
        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(host.dpToPx(43));
        header.setPadding(host.dpToPx(8), 0, host.dpToPx(16), 0);
        header.setBackground(createHeaderBottomLineBackground());

        AppCompatImageButton backBtn = new AppCompatImageButton(host.getContext());
        backBtn.setImageResource(R.drawable.lolib_ic_top_back);
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            backBtn.setBackgroundResource(rippleAttr.resourceId);
        }
        backBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backBtn.setPadding(host.dpToPx(10), host.dpToPx(10), host.dpToPx(10), host.dpToPx(10));
        header.addView(backBtn, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView title = new TextView(host.getContext());
        title.setText("合并单元格");
        title.setTextColor(Color.parseColor("#333333"));
        title.setTextSize(16);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dpToPx(20), host.dpToPx(8), host.dpToPx(20), host.dpToPx(20));

        // 提示文字（Figma 38px → 19sp，#101010）
        TextView hint = new TextView(host.getContext());
        hint.setText("部分单元格不为空。");
        hint.setTextColor(Color.parseColor("#101010"));
        hint.setTextSize(16);
        hint.setPadding(0, host.dpToPx(4), 0, host.dpToPx(8));
        content.addView(hint);

        final int[] selectedIndex = {0};
        final ImageView[] radioViews = new ImageView[MERGE_OPTION_LABELS.length];
        final BottomSheetDialog[] dialogRef = new BottomSheetDialog[1];

        for (int i = 0; i < MERGE_OPTION_LABELS.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(host.dpToPx(8), host.dpToPx(4), host.dpToPx(8), host.dpToPx(8));

            LinearLayout rowHeader = new LinearLayout(host.getContext());
            rowHeader.setOrientation(LinearLayout.HORIZONTAL);
            rowHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView label = new TextView(host.getContext());
            label.setText(MERGE_OPTION_LABELS[i]);
            label.setTextColor(Color.parseColor("#333333"));
            label.setTextSize(16);
            rowHeader.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView radio = new ImageView(host.getContext());
            radio.setImageResource(i == 0
                    ? R.drawable.lolib_ic_calc_toggle_checked
                    : R.drawable.lolib_ic_calc_toggle_unchecked);
            rowHeader.addView(radio, new LinearLayout.LayoutParams(host.dpToPx(20), host.dpToPx(20)));
            radioViews[i] = radio;

            View preview = createMergePreviewRow(index);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(32));
            previewLp.topMargin = host.dpToPx(4);

            row.addView(rowHeader);
            row.addView(preview, previewLp);

            row.setOnClickListener(v -> {
                selectedIndex[0] = index;
                for (int j = 0; j < radioViews.length; j++) {
                    radioViews[j].setImageResource(j == index
                            ? R.drawable.lolib_ic_calc_toggle_checked
                            : R.drawable.lolib_ic_calc_toggle_unchecked);
                }
            });
            content.addView(row);
        }

        TextView confirm = new TextView(host.getContext());
        confirm.setText("确定");
        confirm.setTextColor(Color.WHITE);
        confirm.setTextSize(16);
        confirm.setGravity(android.view.Gravity.CENTER);
        confirm.setBackground(createMergeConfirmBackground());
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(44));
        confirmLp.topMargin = host.dpToPx(12);
        confirm.setLayoutParams(confirmLp);
        confirm.setOnClickListener(v -> {
            host.executeUnoCommand(MERGE_OPTION_COMMANDS[selectedIndex[0]]);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        content.addView(confirm);

        root.addView(content);

        final BottomSheetDialog dialog = new BottomSheetDialog(host.getContext());
        dialog.setContentView(root);
        dialogRef[0] = dialog;
        backBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Log.i(TAG, "show_merge_options");
    }

    /** 合并示意图（代码绘制，避免 vector 渲染问题）：[1][2] → [合并后] */
    private View createMergePreviewRow(int mode) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // 合并前两个格子
        row.addView(createPreviewCell(host.dpToPx(36), "1"));
        row.addView(createPreviewCell(host.dpToPx(36), "2"));

        // 箭头
        TextView arrow = new TextView(host.getContext());
        arrow.setText("→");
        arrow.setTextColor(Color.parseColor("#333333"));
        arrow.setTextSize(16);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                host.dpToPx(28), ViewGroup.LayoutParams.WRAP_CONTENT);
        arrow.setGravity(android.view.Gravity.CENTER);
        row.addView(arrow, arrowLp);

        // 合并后格子：content 模式保留两个值，cells/same 只留一个
        if (mode == 0) {
            row.addView(createPreviewCell(host.dpToPx(72), "1 2"));
        } else {
            row.addView(createPreviewCell(host.dpToPx(72), "1"));
        }
        return row;
    }

    /** 单个示意图格子：带边框 + 居中小字。 */
    private View createPreviewCell(int widthDp, String text) {
        TextView cell = new TextView(host.getContext());
        cell.setText(text);
        cell.setTextColor(Color.parseColor("#333333"));
        cell.setTextSize(12);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setBackground(createPreviewCellBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthDp, host.dpToPx(28));
        lp.setMargins(host.dpToPx(2), 0, host.dpToPx(2), 0);
        cell.setLayoutParams(lp);
        return cell;
    }

    private GradientDrawable createPreviewCellBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setStroke(host.dpToPx(1), Color.parseColor("#333333"));
        return drawable;
    }

    private android.graphics.drawable.Drawable createHeaderBottomLineBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.WHITE);
        GradientDrawable line = new GradientDrawable();
        line.setShape(GradientDrawable.RECTANGLE);
        line.setColor(Color.parseColor("#A2A9B2"));
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bg, line});
        layer.setLayerInset(1, 0, host.dpToPx(42), 0, 0);
        return layer;
    }

    private GradientDrawable createMergeConfirmBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#3B8040"));
        drawable.setCornerRadius(host.dpToPx(22));
        return drawable;
    }

    private GradientDrawable createColorChipBackground(int rgb) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(0xFF000000 | rgb);
        drawable.setCornerRadius(host.dpToPx(12));
        drawable.setStroke(host.dpToPx(1), Color.parseColor("#9AA0A6"));
        return drawable;
    }

    private String buildColorUnoCommand(String unoCommand, String propertyName, int rgb) {
        return unoCommand + " {\"" + propertyName + "\":{\"type\":\"long\",\"value\":" + rgb + "}}";
    }

    private void updateQuickActionToggleState() {
        setToolbarItemActivated(R.id.toolbar_item_character, activeQuickActionGroup == QuickActionGroup.CHARACTER);
        setToolbarItemActivated(R.id.toolbar_item_paragraph, activeQuickActionGroup == QuickActionGroup.PARAGRAPH);
    }

    private void setToolbarItemActivated(int viewId, boolean activated) {
        View view = host.findViewById(viewId);
        if (view == null) {
            return;
        }
        view.setSelected(activated);
        view.setBackgroundColor(activated ? Color.parseColor("#EAF2FF") : Color.TRANSPARENT);
    }

    private void setBottomToolbarBottomMargin(int bottomMarginPx) {
        if (bottomToolbarView == null) {
            return;
        }
        ViewGroup.LayoutParams params = bottomToolbarView.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
        if (mlp.bottomMargin == bottomMarginPx) {
            return;
        }
        mlp.bottomMargin = bottomMarginPx;
        bottomToolbarView.setLayoutParams(mlp);
    }

    private void applyBottomToolbarCompactMode(boolean compactMode) {
        cacheToolbarBaseMetricsIfNeeded();
        if (bottomToolbarView == null) {
            return;
        }
        if (bottomToolbarCompactMode == compactMode && toolbarBaseItemWidths.size() == ALL_TOOLBAR_ITEM_IDS.length) {
            return;
        }
        bottomToolbarCompactMode = compactMode;

        ViewGroup.LayoutParams toolbarLp = bottomToolbarView.getLayoutParams();
        int targetToolbarHeight = compactMode ? host.dpToPx(TOOLBAR_COMPACT_HEIGHT_DP)
                : (bottomToolbarBaseHeightPx > 0 ? bottomToolbarBaseHeightPx : host.dpToPx(TOOLBAR_DEFAULT_HEIGHT_DP));
        if (toolbarLp.height != targetToolbarHeight) {
            toolbarLp.height = targetToolbarHeight;
            bottomToolbarView.setLayoutParams(toolbarLp);
        }

        for (int itemId : ALL_TOOLBAR_ITEM_IDS) {
            int targetWidth = compactMode
                    ? host.dpToPx(TOOLBAR_ITEM_COMPACT_WIDTH_DP)
                    : resolveToolbarItemWidthPx(itemId);
            setToolbarItemWidth(itemId, targetWidth);
            setToolbarItemLabelVisibility(itemId, !compactMode);
            setToolbarItemVerticalPadding(itemId, compactMode ? 0 : host.dpToPx(4));
        }
        if (!compactMode) {
            applyBottomToolbarItemsAlignment(isEditModeActive);
        }
    }

    private void setToolbarItemVerticalPadding(int itemId, int paddingTopPx) {
        View item = host.findViewById(itemId);
        if (!(item instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) item;
        int paddingH = group.getPaddingLeft();
        group.setPadding(paddingH, paddingTopPx, paddingH, group.getPaddingBottom());
    }

    private void cacheToolbarBaseMetricsIfNeeded() {
        if (bottomToolbarView != null && bottomToolbarBaseHeightPx <= 0) {
            int configured = bottomToolbarView.getLayoutParams() != null ? bottomToolbarView.getLayoutParams().height : 0;
            bottomToolbarBaseHeightPx = configured > 0 ? configured : host.dpToPx(TOOLBAR_DEFAULT_HEIGHT_DP);
        }
        for (int itemId : ALL_TOOLBAR_ITEM_IDS) {
            if (toolbarBaseItemWidths.containsKey(itemId)) {
                continue;
            }
            View item = host.findViewById(itemId);
            if (item == null) {
                continue;
            }
            ViewGroup.LayoutParams lp = item.getLayoutParams();
            if (lp != null && lp.width > 0) {
                toolbarBaseItemWidths.put(itemId, lp.width);
            }
        }
    }

    private int getLandscapeToolbarItemWidthPx(int itemId) {
        Integer cached = toolbarBaseItemWidths.get(itemId);
        if (cached != null && cached > 0) {
            return cached;
        }
        return host.dpToPx(TOOLBAR_LANDSCAPE_ITEM_DEFAULT_WIDTH_DP);
    }

    /** 仅横屏：标签占满按钮宽度，避免长文案被裁切。 */
    private void configureLandscapeToolbarItemLabel(int itemId) {
        View item = host.findViewById(itemId);
        if (!(item instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) item;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView label = (TextView) child;
            ViewGroup.LayoutParams lp = label.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                label.setLayoutParams(lp);
            }
            label.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(1);
            label.setEllipsize(null);
        }
    }

    private int resolveToolbarItemWidthPx(int itemId) {
        return toolbarBaseItemWidths.getOrDefault(itemId, host.dpToPx(92));
    }

    private void setToolbarItemWidth(int itemId, int targetWidthPx) {
        View item = host.findViewById(itemId);
        if (item == null) {
            return;
        }
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp == null || lp.width == targetWidthPx) {
            return;
        }
        lp.width = targetWidthPx;
        item.setLayoutParams(lp);
    }

    private void setToolbarItemLabelVisibility(int itemId, boolean visible) {
        View item = host.findViewById(itemId);
        if (!(item instanceof ViewGroup)) {
            return;
        }
        final int labelVisibility = visible ? View.VISIBLE : View.GONE;
        ViewGroup group = (ViewGroup) item;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                child.setVisibility(labelVisibility);
            }
        }
    }

    private LinearLayout asLinearLayout(View view) {
        return view instanceof LinearLayout ? (LinearLayout) view : null;
    }

    private enum QuickActionGroup {
        NONE,
        CHARACTER,
        PARAGRAPH
    }

    private enum QuickActionType {
        UNO,
        FONT_COLOR,
        HIGHLIGHT_COLOR
    }

    private static final class QuickActionItem {
        final int iconResId;
        final String contentDescription;
        final String unoCommand;
        final QuickActionType type;

        QuickActionItem(int iconResId, String contentDescription, String unoCommand) {
            this.iconResId = iconResId;
            this.contentDescription = contentDescription;
            this.unoCommand = unoCommand;
            this.type = QuickActionType.UNO;
        }

        QuickActionItem(int iconResId, String contentDescription, QuickActionType type) {
            this.iconResId = iconResId;
            this.contentDescription = contentDescription;
            this.unoCommand = "";
            this.type = type;
        }
    }

    private static final class ColorOption {
        final String label;
        final int rgb;

        ColorOption(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }
    }
}
