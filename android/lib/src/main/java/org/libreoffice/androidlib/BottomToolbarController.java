package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;

import java.util.HashMap;
import java.util.Map;

public class BottomToolbarController {
    private static final String TAG = "BottomToolbarController";

    private static final int[] PREVIEW_MODE_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_function,
            R.id.toolbar_item_mobile_preview,
            R.id.toolbar_item_ai_assistant
    };
    private static final int[] EDIT_MODE_EXTRA_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_ai_feature,
            R.id.toolbar_item_keyboard,
            R.id.toolbar_item_character,
            R.id.toolbar_item_paragraph,
            R.id.toolbar_item_insert_image
    };
    private static final int[] ALL_TOOLBAR_ITEM_IDS = new int[] {
            R.id.toolbar_item_function,
            R.id.toolbar_item_mobile_preview,
            R.id.toolbar_item_ai_assistant,
            R.id.toolbar_item_ai_feature,
            R.id.toolbar_item_keyboard,
            R.id.toolbar_item_character,
            R.id.toolbar_item_paragraph,
            R.id.toolbar_item_insert_image
    };
    private static final int TOOLBAR_DEFAULT_HEIGHT_DP = 82;
    private static final int TOOLBAR_COMPACT_HEIGHT_DP = 64;
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

        void showNativeAiPanel();

        void toastTodo(String text);

        void focusDocumentAndShowIme();

        void openLocalImagePickerFromWeb();

        void executeUnoCommand(String command);
    }

    private final Host host;
    private LinearLayout bottomToolbarView;
    private View quickActionOverlayView;
    private LinearLayout quickActionPanelView;
    private LinearLayout quickActionActionsRow;
    private final Map<Integer, Integer> toolbarBaseItemWidths = new HashMap<>();
    private boolean bottomToolbarCompactMode = false;
    private int bottomToolbarBaseHeightPx = -1;
    private int bottomToolbarImeInsetPx = 0;
    private boolean isImeVisibleForToolbar = false;
    private boolean isEditModeActive = false;
    private QuickActionGroup activeQuickActionGroup = QuickActionGroup.NONE;

    public BottomToolbarController(Host host) {
        this.host = host;
    }

    public void setup() {
        bottomToolbarView = asLinearLayout(host.findViewById(R.id.doc_bottom_toolbar));
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
            host.switchToViewingMode();
        });
        bindToolbarClick(R.id.toolbar_item_ai_assistant, v -> {
            hideQuickActionPanel();
            host.showNativeAiPanel();
        });
        bindToolbarClick(R.id.toolbar_item_ai_feature, v -> {
            hideQuickActionPanel();
            host.toastTodo("AI功能后续逐步接入。");
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

        applyImeState(isImeVisibleForToolbar, bottomToolbarImeInsetPx);
        updateEditModeState(isEditModeActive, "toolbar_setup");
    }

    public void updateEditModeState(boolean isEditMode, String reason) {
        isEditModeActive = isEditMode;
        Runnable applyTask = () -> {
            applyBottomToolbarMode(isEditMode);
            applyImeState(isImeVisibleForToolbar, bottomToolbarImeInsetPx);
            Log.i(TAG, "bottom_toolbar_mode edit=" + isEditMode + " reason=" + reason);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyTask.run();
        } else {
            host.runOnUiThread(applyTask);
        }
    }

    public void applyImeState(boolean imeVisible, int imeInsetBottom) {
        isImeVisibleForToolbar = imeVisible;
        bottomToolbarImeInsetPx = Math.max(0, imeInsetBottom);
        setBottomToolbarBottomMargin(imeVisible ? bottomToolbarImeInsetPx : 0);
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
            view.setOnClickListener(listener);
        }
    }

    private void applyBottomToolbarMode(boolean isEditMode) {
        setBottomToolbarItemsVisible(PREVIEW_MODE_TOOLBAR_ITEM_IDS, true);
        setBottomToolbarItemsVisible(EDIT_MODE_EXTRA_TOOLBAR_ITEM_IDS, isEditMode);
        if (!isEditMode) {
            hideQuickActionPanel();
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
                    : toolbarBaseItemWidths.getOrDefault(itemId, host.dpToPx(92));
            setToolbarItemWidth(itemId, targetWidth);
            setToolbarItemLabelVisibility(itemId, !compactMode);
        }
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
