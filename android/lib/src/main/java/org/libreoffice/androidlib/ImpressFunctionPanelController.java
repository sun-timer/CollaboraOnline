package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;
import org.libreoffice.androidlib.impress.ImpressShapePickerController;
import org.libreoffice.androidlib.impress.ImpressSolidColorPickerController;
import org.libreoffice.androidlib.impress.ImpressTransitionCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Impress edit-mode function panel: 常用 / 文件 / 插入 / 切换 / 布局 / 审阅.
 * Header matches Calc (tabs + AI / keyboard / collapse). Common tab follows Figma.
 */
public class ImpressFunctionPanelController {
    private static final String TAG = "ImpressFunctionPanel";

    private static final int COLOR_TAB_ACTIVE = Color.parseColor("#EC5D1F");
    private static final int COLOR_TAB_INACTIVE = Color.parseColor("#333333");
    private static final int COLOR_SECTION = Color.parseColor("#80868B");
    private static final int COLOR_TITLE = Color.parseColor("#101010");
    private static final int COLOR_VALUE = Color.parseColor("#6A6A6A");
    private static final int COLOR_DIVIDER = Color.parseColor("#E3E3E3");
    private static final float SHEET_HEIGHT_RATIO = 1066f / 1624f;
    private static final float SOLID_COLOR_SHEET_HEIGHT_RATIO = 0.92f;
    private static final int CHAR_CELL_W_DP = 51;
    private static final int CHAR_CELL_H_DP = 50;
    private static final int CHAR_CELL_PAD_DP = 12;
    private static final int ICON_SIZE_DP = 24;
    private static final int NUMFMT_CELL_H_DP = 80;
    private static final int NUMFMT_CELL_VPAD_DP = 12;
    private static final int GRID_GAP_DP = 5;
    private static final int COLOR_SWATCH_SIZE_DP = 40;
    private static final int COLOR_SWATCH_GAP_DP = 10;
    private static final int COLOR_SWATCH_COLS = 6;
    private static final int TRANSITION_GRID_COLS = 6;
    private static final int TRANSITION_ICON_SIZE_DP = 40;
    private static final int TRANSITION_CELL_MIN_H_DP = 72;
    private static final int TRANSITION_CELL_VPAD_DP = 6;
    private static final int DEFAULT_SELECTED_TRANSITION_INDEX = 8;

    private static final class ColorSwatch {
        final String label;
        final int rgb;

        ColorSwatch(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }
    }

    private static final ColorSwatch[] COMMON_COLOR_SWATCHES = new ColorSwatch[] {
            new ColorSwatch("暗红", 0xD20000),
            new ColorSwatch("琥珀", 0xFFBD00),
            new ColorSwatch("草绿", 0x7ED330),
            new ColorSwatch("天蓝", 0x00B3F7),
            new ColorSwatch("紫色", 0x792BA6),
            new ColorSwatch("白色", 0xFFFFFF),
            new ColorSwatch("红色", 0xFF0000),
            new ColorSwatch("黄色", 0xFFFF00),
            new ColorSwatch("绿色", 0x00B242),
            new ColorSwatch("蓝色", 0x0073C7),
            new ColorSwatch("深蓝", 0x002164),
            new ColorSwatch("黑色", 0x000000),
    };

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void executeUnoCommand(String command);

        void toastTodo(String text);

        void applyFont(String fontName);

        void applyFontSize(String fontSizePt);

        void fetchFontList(FunctionPanelController.StringListCallback callback);

        void fetchCurrentFormatting(FunctionPanelController.FormattingCallback callback);

        void showAiOperationSheet();

        void focusDocumentAndShowIme();

        void openLocalImagePickerFromWeb();

        /** 功能面板关闭后再执行（避免 UNO 与 dismiss 竞态）。 */
        void runAfterFunctionPanelDismiss(Runnable action);

        /** 应用幻灯片切换动画（iconViewIndex = transitions_icons 行号）。 */
        void applySlideTransition(int iconViewIndex, boolean applyToAll);
    }

    private enum ItemType {
        SECTION,
        SLIDE_PICKER,
        PICKER_ROW,
        PICKER_PAIR,
        TOOL_BUTTONS,
        FORMAT_GRID,
        ACTION,
        STUB
    }

    private static final class PanelItem {
        final ItemType type;
        final String id;
        final String label;
        final String subtitle;
        final String unoCommand;
        final Runnable hostAction;
        final int iconResId;
        final String[] gridLabels;
        final String[] gridCommands;
        final int[] gridIconRes;
        final int cols;

        PanelItem(ItemType type, String id, String label) {
            this(type, id, label, "", "", null, 0, null, null, null, 0);
        }

        PanelItem(ItemType type, String id, String label, String subtitle) {
            this(type, id, label, subtitle, "", null, 0, null, null, null, 0);
        }

        PanelItem(ItemType type, String id, String label, String subtitle, int iconResId) {
            this(type, id, label, subtitle, "", null, iconResId, null, null, null, 0);
        }

        PanelItem(ItemType type, String id, String label, int iconResId, Runnable hostAction) {
            this(type, id, label, "", "", hostAction, iconResId, null, null, null, 0);
        }

        PanelItem(ItemType type, String id, String label, int iconResId, String unoCommand) {
            this(type, id, label, "", unoCommand, null, iconResId, null, null, null, 0);
        }

        PanelItem(ItemType type, String id, String label, String[] gridLabels, String[] gridCommands,
                int[] gridIconRes, int cols) {
            this(type, id, label, "", "", null, 0, gridLabels, gridCommands, gridIconRes, cols);
        }

        PanelItem(ItemType type, String id, String label, String subtitle, int iconResId,
                String[] gridLabels, String[] gridCommands, int[] gridIconRes, int cols) {
            this(type, id, label, subtitle, "", null, iconResId, gridLabels, gridCommands, gridIconRes, cols);
        }

        PanelItem(ItemType type, String id, String label, String subtitle, String unoCommand,
                Runnable hostAction, int iconResId, String[] gridLabels, String[] gridCommands,
                int[] gridIconRes, int cols) {
            this.type = type;
            this.id = id;
            this.label = label;
            this.subtitle = subtitle;
            this.unoCommand = unoCommand;
            this.hostAction = hostAction;
            this.iconResId = iconResId;
            this.gridLabels = gridLabels;
            this.gridCommands = gridCommands;
            this.gridIconRes = gridIconRes;
            this.cols = cols;
        }
    }

    private static final class PanelTab {
        final String id;
        final String title;
        final List<PanelItem> items;

        PanelTab(String id, String title, List<PanelItem> items) {
            this.id = id;
            this.title = title;
            this.items = items;
        }
    }

    private final Host host;
    private BottomSheetDialog dialog;
    private View sheetRoot;
    private View tabHeader;
    private View tabIndicatorArea;
    private LinearLayout tabBar;
    private NestedScrollView contentContainer;
    private View tabIndicator;
    private View fontPickerPanel;
    private final List<TextView> tabViews = new ArrayList<>();
    private final List<PanelTab> tabs;
    private int selectedTabIndex = 0;
    private boolean fontPickerVisible;
    private final Map<String, String> pickerValues = new HashMap<>();
    private final Map<String, Integer> pickerColorRgb = new HashMap<>();
    private String[] cachedFontOptions = FALLBACK_FONT_OPTIONS;
    private String[] cachedFontValues = FALLBACK_FONT_VALUES;
    private ImpressShapePickerController shapePickerController;
    private ImpressSolidColorPickerController solidColorPickerController;
    private boolean solidColorPickerVisible;
    private int submenuReturnTabIndex = -1;
    private TextView slideMasterValueView;
    private Integer selectedMasterSolidRgb;
    private int selectedTransitionIndex = DEFAULT_SELECTED_TRANSITION_INDEX;
    private LinearLayout transitionGridRoot;

    public ImpressFunctionPanelController(Host host) {
        this.host = host;
        this.tabs = buildTabs();
        pickerValues.put("slide_format", "16:9 屏幕");
        pickerValues.put("slide_orientation", "横向");
        pickerValues.put("slide_background", "无");
        pickerValues.put("slide_master", "默认");
        pickerValues.put("font_name", "宋体");
        pickerValues.put("font_size", "4 pt");
        pickerColorRgb.put("font_color", 0x000000);
    }

    public void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        View panel = LayoutInflater.from(host.getContext())
                .inflate(R.layout.lolib_sheet_impress_functions, null, false);
        sheetRoot = panel;
        tabHeader = panel.findViewById(R.id.impress_function_tab_header);
        tabIndicatorArea = panel.findViewById(R.id.impress_function_tab_indicator_area);
        tabBar = panel.findViewById(R.id.impress_function_tab_bar);
        contentContainer = panel.findViewById(R.id.impress_function_content_container);
        tabIndicator = panel.findViewById(R.id.impress_function_tab_indicator);
        fontPickerPanel = panel.findViewById(R.id.impress_function_font_picker_panel);

        ImageButton aiBtn = panel.findViewById(R.id.impress_function_btn_ai);
        ImageButton keyboardBtn = panel.findViewById(R.id.impress_function_btn_keyboard);
        ImageButton collapseBtn = panel.findViewById(R.id.impress_function_btn_collapse);
        if (aiBtn != null) {
            aiBtn.setOnClickListener(v -> {
                dismiss();
                host.showAiOperationSheet();
            });
        }
        if (keyboardBtn != null) {
            keyboardBtn.setOnClickListener(v -> {
                dismiss();
                host.focusDocumentAndShowIme();
            });
        }
        if (collapseBtn != null) {
            collapseBtn.setOnClickListener(v -> dismiss());
        }

        buildTabBar();
        selectTab(0);
        syncCurrentFormatting();

        dialog = new BottomSheetDialog(host.getContext());
        dialog.setContentView(panel);
        AiDialogHelper.applyCloseOnlyDismiss(dialog);
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.setOnShowListener(d -> expandSheet(SHEET_HEIGHT_RATIO));
        dialog.show();
    }

    public void dismiss() {
        dismissFontPicker();
        dismissSolidColorPickerPage();
        if (shapePickerController != null) {
            shapePickerController.dismiss();
        }
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    private void buildTabBar() {
        tabBar.removeAllViews();
        tabViews.clear();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TextView tabView = new TextView(host.getContext());
            tabView.setText(tabs.get(i).title);
            tabView.setGravity(Gravity.CENTER);
            tabView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tabView.setPadding(host.dpToPx(12), 0, host.dpToPx(12), 0);
            tabView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            tabView.setOnClickListener(v -> selectTab(index));
            tabBar.addView(tabView);
            tabViews.add(tabView);
        }
    }

    private void selectTab(int index) {
        dismissFontPicker();
        dismissSolidColorPickerPage();
        selectedTabIndex = index;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tabView = tabViews.get(i);
            tabView.setTextColor(i == index ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
            tabView.setTypeface(null, i == index ? Typeface.BOLD : Typeface.NORMAL);
        }
        updateTabIndicator(index);
        renderTabContent(tabs.get(index));
    }

    private void updateTabIndicator(int index) {
        if (tabIndicator == null || index < 0 || index >= tabViews.size()) {
            return;
        }
        tabIndicator.setVisibility(View.VISIBLE);
        TextView selectedTab = tabViews.get(index);
        selectedTab.post(() -> {
            if (tabIndicator == null || selectedTab.getWidth() == 0) {
                return;
            }
            int[] tabLoc = new int[2];
            int[] parentLoc = new int[2];
            selectedTab.getLocationInWindow(tabLoc);
            View parent = (View) tabIndicator.getParent();
            if (parent == null) {
                return;
            }
            parent.getLocationInWindow(parentLoc);
            float tabCenterX = tabLoc[0] - parentLoc[0] + selectedTab.getWidth() / 2f;
            int indicatorW = host.dpToPx(24);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tabIndicator.getLayoutParams();
            lp.leftMargin = Math.max(0, (int) (tabCenterX - indicatorW / 2f));
            lp.width = indicatorW;
            tabIndicator.setLayoutParams(lp);
        });
    }

    private void renderTabContent(PanelTab tab) {
        contentContainer.removeAllViews();
        transitionGridRoot = null;
        if ("transition".equals(tab.id)) {
            contentContainer.addView(buildTransitionTabContent());
            return;
        }
        if (!tab.items.isEmpty() && tab.items.get(0).type == ItemType.STUB) {
            TextView stub = new TextView(host.getContext());
            stub.setText("功能开发中");
            stub.setGravity(Gravity.CENTER);
            stub.setTextColor(COLOR_VALUE);
            stub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            stub.setPadding(0, host.dpToPx(48), 0, host.dpToPx(48));
            contentContainer.addView(stub);
            return;
        }

        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        for (PanelItem item : tab.items) {
            switch (item.type) {
                case SECTION:
                    root.addView(createSectionLabel(item.label));
                    break;
                case SLIDE_PICKER:
                    root.addView(createSlidePickerRow(item));
                    break;
                case PICKER_ROW:
                    root.addView(createFullPickerRow(item));
                    break;
                case PICKER_PAIR:
                    root.addView(createSplitPickerRow());
                    break;
                case TOOL_BUTTONS:
                    root.addView(createToolButtonRow());
                    break;
                case FORMAT_GRID:
                    root.addView(createLabeledGrid(
                            item.gridLabels, item.gridCommands, item.gridIconRes, item.cols));
                    break;
                case ACTION:
                    root.addView(createActionRow(item));
                    break;
                default:
                    break;
            }
        }
        contentContainer.addView(root);
    }

    private TextView createSectionLabel(String title) {
        TextView label = new TextView(host.getContext());
        label.setText(title);
        label.setTextColor(COLOR_SECTION);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(host.dpToPx(2), host.dpToPx(14), host.dpToPx(2), host.dpToPx(6));
        return label;
    }

    private View createSlidePickerRow(PanelItem item) {
        LinearLayout card = createCardRow();
        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    host.dpToPx(ICON_SIZE_DP), host.dpToPx(ICON_SIZE_DP));
            iconLp.setMarginEnd(host.dpToPx(12));
            card.addView(icon, iconLp);
        }
        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        card.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView value = new TextView(host.getContext());
        value.setText(pickerValues.getOrDefault(item.id, item.subtitle));
        value.setTextColor(COLOR_VALUE);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueLp.setMarginStart(host.dpToPx(8));
        card.addView(value, valueLp);
        card.addView(createChevron());
        card.setOnClickListener(v -> onSlidePickerClick(item, value));
        return wrapBottomMargin(card);
    }

    private void onSlidePickerClick(PanelItem item, TextView valueView) {
        if ("slide_format".equals(item.id)) {
            showLabelPicker("格式", FORMAT_LABELS, null, item.id, valueView);
            return;
        }
        if ("slide_orientation".equals(item.id)) {
            showLabelPicker("方向", ORIENTATION_LABELS, ORIENTATION_COMMANDS, item.id, valueView);
            return;
        }
        if ("slide_master".equals(item.id)) {
            showMasterSlidePicker(valueView);
            return;
        }
        host.toastTodo(item.label + " 后续接入");
    }

    private static final String[] MASTER_SLIDE_LABELS = { "默认", "纯色" };

    private void showMasterSlidePicker(TextView valueView) {
        new AlertDialog.Builder(host.getContext())
                .setTitle("母版幻灯片")
                .setItems(MASTER_SLIDE_LABELS, (d, which) -> {
                    String label = MASTER_SLIDE_LABELS[which];
                    if ("纯色".equals(label)) {
                        slideMasterValueView = valueView;
                        showSolidColorPickerPage();
                        return;
                    }
                    pickerValues.put("slide_master", label);
                    selectedMasterSolidRgb = null;
                    valueView.setText(label);
                    Log.i(TAG, "slide_master_picked label=" + label);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setTabChromeVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (tabHeader != null) {
            tabHeader.setVisibility(visibility);
        }
        if (tabIndicatorArea != null) {
            tabIndicatorArea.setVisibility(visibility);
        }
    }

    private void dismissSolidColorPickerPage() {
        if (!solidColorPickerVisible) {
            return;
        }
        solidColorPickerVisible = false;
        setTabChromeVisible(true);
        expandSheet(SHEET_HEIGHT_RATIO);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showSolidColorPickerPage() {
        dismissFontPicker();
        solidColorPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);
        expandSheet(SOLID_COLOR_SHEET_HEIGHT_RATIO);

        solidColorPickerController = new ImpressSolidColorPickerController(
                new ImpressSolidColorPickerController.Host() {
                    @Override
                    public android.content.Context getContext() {
                        return host.getContext();
                    }

                    @Override
                    public int dpToPx(int dp) {
                        return host.dpToPx(dp);
                    }

                    @Override
                    public Integer getSelectedRgb() {
                        return selectedMasterSolidRgb;
                    }

                    @Override
                    public void onColorSelected(int index, int rgb) {
                        applyMasterSolidColor(rgb);
                        pickerValues.put("slide_master", "纯色");
                        if (slideMasterValueView != null) {
                            slideMasterValueView.setText("纯色");
                        }
                        Log.i(TAG, "slide_master_solid index=" + index
                                + " rgb=#" + Integer.toHexString(rgb).toUpperCase());
                    }

                    @Override
                    public void onBack() {
                        dismissSolidColorPickerPage();
                    }
                });

        contentContainer.removeAllViews();
        contentContainer.addView(solidColorPickerController.buildRootView());
        Log.i(TAG, "solid_color_picker_show");
    }

    private void applyMasterSolidColor(int rgb) {
        selectedMasterSolidRgb = rgb;
        host.executeUnoCommand(buildBackgroundColorUnoCommand(rgb));
    }

    private String buildBackgroundColorUnoCommand(int rgb) {
        return ".uno:BackgroundColor {\"BackgroundColor.Color\":{\"type\":\"long\",\"value\":" + rgb + "}}";
    }

    private void showLabelPicker(String title, String[] labels, String[] commands,
            String pickerId, TextView valueView) {
        new AlertDialog.Builder(host.getContext())
                .setTitle(title)
                .setItems(labels, (d, which) -> {
                    String label = labels[which];
                    pickerValues.put(pickerId, label);
                    valueView.setText(label);
                    if (commands != null && which < commands.length
                            && commands[which] != null && !commands[which].isEmpty()) {
                        host.executeUnoCommand(commands[which]);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View createFullPickerRow(PanelItem item) {
        LinearLayout card = createCardRow();
        TextView value = new TextView(host.getContext());
        value.setText(pickerValues.getOrDefault(item.id, item.subtitle));
        value.setTextColor(COLOR_TITLE);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        card.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(createChevron());
        card.setOnClickListener(v -> showFontPicker(value));
        return wrapBottomMargin(card);
    }

    private View createSplitPickerRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left = createCardRow();
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView sizeValue = new TextView(host.getContext());
        sizeValue.setText(pickerValues.getOrDefault("font_size", "4 pt"));
        sizeValue.setTextColor(COLOR_VALUE);
        sizeValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        left.addView(sizeValue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        left.addView(createChevron());
        left.setOnClickListener(v -> showSizePicker(sizeValue));

        LinearLayout right = createCardRow();
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(host.dpToPx(10));
        right.setLayoutParams(rightLp);
        ImageView colorDot = new ImageView(host.getContext());
        colorDot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        updateColorPreviewDot(colorDot, pickerColorRgb.get("font_color"),
                R.drawable.lolib_ic_calc_color_font_preview);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(host.dpToPx(20), host.dpToPx(20));
        dotLp.setMarginEnd(host.dpToPx(8));
        TextView colorLabel = new TextView(host.getContext());
        colorLabel.setText("字体颜色");
        colorLabel.setTextColor(COLOR_TITLE);
        colorLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        right.addView(colorDot, dotLp);
        right.addView(colorLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        right.addView(createChevron());
        right.setOnClickListener(v -> showFontColorPicker(colorLabel, colorDot));

        row.addView(left);
        row.addView(right);
        return wrapBottomMargin(row);
    }

    private View createToolButtonRow() {
        LinearLayout container = new LinearLayout(host.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(buildCharToolRow(CHAR_TOOL_ICONS_ROW1, CHAR_TOOL_COMMANDS_ROW1));
        container.addView(buildCharToolRow(CHAR_TOOL_ICONS_ROW2, CHAR_TOOL_COMMANDS_ROW2));
        return container;
    }

    private View buildCharToolRow(int[] iconResIds, String[] commands) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        int cellW = host.dpToPx(CHAR_CELL_W_DP);
        int cellH = host.dpToPx(CHAR_CELL_H_DP);
        int pad = host.dpToPx(CHAR_CELL_PAD_DP);
        int gap = host.dpToPx(GRID_GAP_DP);
        for (int i = 0; i < commands.length; i++) {
            final String command = commands[i];
            LinearLayout btn = new LinearLayout(host.getContext());
            btn.setOrientation(LinearLayout.VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
            btn.setPadding(pad, pad, pad, pad);
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(iconResIds[i]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(ICON_SIZE_DP), host.dpToPx(ICON_SIZE_DP)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellW, cellH);
            if (i < commands.length - 1) {
                lp.setMarginEnd(gap);
            }
            lp.bottomMargin = gap;
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> host.executeUnoCommand(command));
            row.addView(btn);
        }
        return row;
    }

    private View createLabeledGrid(String[] labels, String[] commands, int[] iconRes, int cols) {
        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        if (labels == null || labels.length == 0) {
            return grid;
        }
        int gap = host.dpToPx(GRID_GAP_DP);
        for (int rowStart = 0; rowStart < labels.length; rowStart += cols) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int rowEnd = Math.min(rowStart + cols, labels.length);
            for (int i = rowStart; i < rowEnd; i++) {
                final String label = labels[i];
                final String command = commands != null && i < commands.length ? commands[i] : "";
                row.addView(createGridCell(label, command,
                        iconRes != null && i < iconRes.length ? iconRes[i] : 0, i < rowEnd - 1, gap));
            }
            if (rowStart > 0) {
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = gap;
                row.setLayoutParams(rowLp);
            }
            grid.addView(row);
        }
        return wrapBottomMargin(grid);
    }

    private View createGridCell(String label, String command, int iconRes, boolean addEndGap, int gap) {
        LinearLayout cell = new LinearLayout(host.getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int vPad = host.dpToPx(NUMFMT_CELL_VPAD_DP);
        cell.setPadding(host.dpToPx(6), vPad, host.dpToPx(6), vPad);
        cell.setMinimumHeight(host.dpToPx(NUMFMT_CELL_H_DP));

        if (iconRes != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(iconRes);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cell.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(ICON_SIZE_DP), host.dpToPx(ICON_SIZE_DP)));
        }

        TextView caption = new TextView(host.getContext());
        caption.setText(label);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(COLOR_TITLE);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        caption.setPadding(0, host.dpToPx(6), 0, 0);
        cell.addView(caption);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, host.dpToPx(NUMFMT_CELL_H_DP), 1f);
        if (addEndGap) {
            lp.setMarginEnd(gap);
        }
        lp.bottomMargin = gap;
        cell.setLayoutParams(lp);
        cell.setOnClickListener(v -> onGridCommand(command, label));
        return cell;
    }

    private void onGridCommand(String command, String label) {
        if (command != null && !command.isEmpty()) {
            host.executeUnoCommand(command);
        } else {
            host.toastTodo(label + " 后续接入");
        }
    }

    private LinearLayout createCardRow() {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        card.setPadding(host.dpToPx(14), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        card.setMinimumHeight(host.dpToPx(48));
        return card;
    }

    private View wrapBottomMargin(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = host.dpToPx(8);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView createChevron() {
        TextView arrow = new TextView(host.getContext());
        arrow.setText("›");
        arrow.setTextColor(COLOR_SECTION);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        return arrow;
    }

    private void showSizePicker(TextView valueView) {
        new AlertDialog.Builder(host.getContext())
                .setTitle("字号")
                .setItems(SIZE_OPTIONS, (d, which) -> {
                    String label = SIZE_OPTIONS[which];
                    String value = SIZE_VALUES[which];
                    pickerValues.put("font_size", label);
                    valueView.setText(label);
                    host.applyFontSize(value);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showFontPicker(TextView valueView) {
        Runnable open = () -> openFontPickerPanel(valueView);
        if (cachedFontOptions.length > FALLBACK_FONT_OPTIONS.length) {
            open.run();
            return;
        }
        host.fetchFontList((labels, values) -> {
            if (labels != null && !labels.isEmpty()) {
                cachedFontOptions = labels.toArray(new String[0]);
                cachedFontValues = values != null && !values.isEmpty()
                        ? values.toArray(new String[0]) : cachedFontOptions;
            }
            open.run();
        });
    }

    private void openFontPickerPanel(TextView valueView) {
        if (fontPickerPanel == null || dialog == null) {
            return;
        }
        ImageButton back = fontPickerPanel.findViewById(R.id.font_picker_back);
        LinearLayout list = fontPickerPanel.findViewById(R.id.font_picker_list);
        if (back != null) {
            back.setOnClickListener(v -> dismissFontPicker());
        }
        populateFontList(list, valueView);
        if (tabHeader != null) {
            tabHeader.setVisibility(View.GONE);
        }
        if (tabIndicatorArea != null) {
            tabIndicatorArea.setVisibility(View.GONE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
        fontPickerPanel.setVisibility(View.VISIBLE);
        fontPickerVisible = true;
        expandSheet(SHEET_HEIGHT_RATIO);
    }

    private void populateFontList(LinearLayout list, TextView valueView) {
        list.removeAllViews();
        String selected = pickerValues.get("font_name");
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < cachedFontOptions.length; i++) {
            final String label = cachedFontOptions[i];
            final String value = i < cachedFontValues.length ? cachedFontValues[i] : label;
            View row = inflater.inflate(R.layout.lolib_item_font_picker_row, list, false);
            TextView name = row.findViewById(R.id.font_picker_item_name);
            ImageView check = row.findViewById(R.id.font_picker_item_check);
            name.setText(label);
            Typeface tf = Typeface.create(label, Typeface.NORMAL);
            if (tf != null) {
                name.setTypeface(tf);
            }
            check.setVisibility(label.equals(selected) || value.equals(selected)
                    ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> {
                pickerValues.put("font_name", label);
                valueView.setText(label);
                host.applyFont(value);
                dismissFontPicker();
            });
            list.addView(row);
            if (i < cachedFontOptions.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(COLOR_DIVIDER);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    private void dismissFontPicker() {
        if (!fontPickerVisible) {
            return;
        }
        fontPickerVisible = false;
        if (tabHeader != null) {
            tabHeader.setVisibility(View.VISIBLE);
        }
        if (tabIndicatorArea != null) {
            tabIndicatorArea.setVisibility(View.VISIBLE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
        if (fontPickerPanel != null) {
            fontPickerPanel.setVisibility(View.GONE);
        }
        expandSheet(SHEET_HEIGHT_RATIO);
    }

    private void showFontColorPicker(TextView labelView, ImageView previewDot) {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(12));

        TextView section = new TextView(host.getContext());
        section.setText("标准色");
        section.setTextColor(COLOR_SECTION);
        section.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        root.addView(section);

        LinearLayout gridCard = new LinearLayout(host.getContext());
        gridCard.setOrientation(LinearLayout.VERTICAL);
        gridCard.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int cardPad = host.dpToPx(12);
        gridCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = host.dpToPx(8);
        root.addView(gridCard, cardLp);

        Integer currentRgb = pickerColorRgb.get("font_color");
        final AlertDialog[] dialogRef = new AlertDialog[1];
        int swatchSize = host.dpToPx(COLOR_SWATCH_SIZE_DP);
        int gap = host.dpToPx(COLOR_SWATCH_GAP_DP);
        LinearLayout row = null;
        for (int i = 0; i < COMMON_COLOR_SWATCHES.length; i++) {
            if (i % COLOR_SWATCH_COLS == 0) {
                row = new LinearLayout(host.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                if (i > 0) {
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.topMargin = gap;
                    row.setLayoutParams(rowLp);
                }
                gridCard.addView(row);
            }
            ColorSwatch swatch = COMMON_COLOR_SWATCHES[i];
            boolean selected = currentRgb != null && currentRgb == swatch.rgb;
            FrameLayout chip = createColorSwatchChip(swatch, selected, swatchSize);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            if (i % COLOR_SWATCH_COLS < COLOR_SWATCH_COLS - 1) {
                lp.setMarginEnd(gap);
            }
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                int rgb = swatch.rgb;
                host.executeUnoCommand(".uno:Color {\"Color.Color\":{\"type\":\"long\",\"value\":" + rgb + "}}");
                pickerColorRgb.put("font_color", rgb);
                updateColorPreviewDot(previewDot, rgb, R.drawable.lolib_ic_calc_color_font_preview);
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
            });
            if (row != null) {
                row.addView(chip);
            }
        }

        AlertDialog colorDialog = new AlertDialog.Builder(host.getContext())
                .setTitle("字体颜色")
                .setView(root)
                .setNegativeButton("取消", null)
                .create();
        dialogRef[0] = colorDialog;
        colorDialog.show();
    }

    private FrameLayout createColorSwatchChip(ColorSwatch swatch, boolean selected, int size) {
        FrameLayout wrap = new FrameLayout(host.getContext());
        View circle = new View(host.getContext());
        circle.setBackground(createCircleSwatchDrawable(swatch.rgb));
        wrap.addView(circle, new FrameLayout.LayoutParams(size, size));
        if (selected) {
            TextView check = new TextView(host.getContext());
            check.setText("✓");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setTextColor(isLightSwatch(swatch.rgb) ? Color.BLACK : Color.WHITE);
            check.setGravity(Gravity.CENTER);
            wrap.addView(check, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return wrap;
    }

    private void updateColorPreviewDot(ImageView dot, Integer rgb, int fallbackIconRes) {
        if (dot == null) {
            return;
        }
        if (rgb == null) {
            dot.setImageResource(fallbackIconRes);
            dot.setBackground(null);
            return;
        }
        dot.setImageDrawable(null);
        dot.setBackground(createCircleSwatchDrawable(rgb));
        int size = host.dpToPx(20);
        dot.setMinimumWidth(size);
        dot.setMinimumHeight(size);
    }

    private GradientDrawable createCircleSwatchDrawable(int rgb) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xFF000000 | (rgb & 0xFFFFFF));
        if ((rgb & 0xFFFFFF) == 0xFFFFFF) {
            drawable.setStroke(host.dpToPx(1), Color.parseColor("#D0D0D0"));
        }
        return drawable;
    }

    private boolean isLightSwatch(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 186;
    }

    private void syncCurrentFormatting() {
        host.fetchCurrentFormatting((styleName, fontName, fontSizePt, paragraphAlignment) -> {
            if (fontName != null && !fontName.trim().isEmpty()) {
                pickerValues.put("font_name", fontName.trim());
            }
            String sizeLabel = displayFontSize(fontSizePt);
            if (!TextUtils.isEmpty(sizeLabel)) {
                pickerValues.put("font_size", sizeLabel);
            }
            if (dialog != null && dialog.isShowing() && selectedTabIndex == 0) {
                renderTabContent(tabs.get(selectedTabIndex));
            }
        });
    }

    private String displayFontSize(String fontSizePt) {
        if (TextUtils.isEmpty(fontSizePt)) {
            return "4 pt";
        }
        String normalized = fontSizePt.trim().replace("pt", "").replace("号", "").trim();
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            if (SIZE_VALUES[i].equals(normalized)) {
                return SIZE_OPTIONS[i];
            }
        }
        return normalized + " pt";
    }

    private void expandSheet(float heightRatio) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        int screenHeight = host.getContext().getResources().getDisplayMetrics().heightPixels;
        int targetHeight = Math.round(screenHeight * heightRatio);
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
            layoutParams.height = targetHeight;
            bottomSheet.setLayoutParams(layoutParams);
        }
        bottomSheet.setBackgroundResource(R.drawable.lolib_bg_calc_bottom_sheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(true);
        behavior.setDraggable(true);
        bottomSheet.post(() -> {
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            Log.i(TAG, "impress_function_sheet_expanded height=" + bottomSheet.getHeight()
                    + " target=" + targetHeight);
        });
    }

    private List<PanelTab> buildTabs() {
        List<PanelTab> result = new ArrayList<>();

        List<PanelItem> common = new ArrayList<>();
        common.add(new PanelItem(ItemType.SECTION, "sec_slide", "幻灯片"));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_format", "格式", "16:9 屏幕",
                R.drawable.lolib_ic_impress_slide_format));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_orientation", "方向", "横向",
                R.drawable.lolib_ic_impress_slide_orientation));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_background", "背景", "无",
                R.drawable.lolib_ic_impress_slide_background));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_master", "母版幻灯片", "默认",
                R.drawable.lolib_ic_impress_slide_master));

        common.add(new PanelItem(ItemType.SECTION, "sec_layout", "布局"));
        common.add(new PanelItem(ItemType.FORMAT_GRID, "layout_grid", "布局",
                LAYOUT_LABELS, LAYOUT_COMMANDS, LAYOUT_ICONS, 3));

        common.add(new PanelItem(ItemType.SECTION, "sec_char", "字符"));
        common.add(new PanelItem(ItemType.PICKER_ROW, "font_name", "字体", "宋体"));
        common.add(new PanelItem(ItemType.PICKER_PAIR, "font_size_color", "字号颜色"));
        common.add(new PanelItem(ItemType.TOOL_BUTTONS, "char_tools", "字符样式"));

        common.add(new PanelItem(ItemType.SECTION, "sec_para", "段落"));
        common.add(new PanelItem(ItemType.FORMAT_GRID, "para_grid", "段落",
                PARA_LABELS, PARA_COMMANDS, PARA_ICONS, 3));

        result.add(new PanelTab("common", "常用", common));
        result.add(new PanelTab("file", "文件", stubItems()));
        result.add(new PanelTab("insert", "插入", buildInsertItems()));
        result.add(new PanelTab("transition", "切换", stubItems()));
        result.add(new PanelTab("layout_tab", "布局", stubItems()));
        result.add(new PanelTab("review", "审阅", stubItems()));
        return result;
    }

    private List<PanelItem> stubItems() {
        List<PanelItem> items = new ArrayList<>();
        items.add(new PanelItem(ItemType.STUB, "stub", ""));
        return items;
    }

    private List<PanelItem> buildInsertItems() {
        List<PanelItem> insert = new ArrayList<>();
        insert.add(new PanelItem(ItemType.ACTION, "insert_local_image", "本地图像",
                R.drawable.lolib_ic_calc_insert_local_image, (Runnable) host::openLocalImagePickerFromWeb));
        insert.add(new PanelItem(ItemType.ACTION, "insert_chart", "图表",
                R.drawable.lolib_ic_calc_insert_chart, ".uno:InsertObjectChart"));
        insert.add(new PanelItem(ItemType.ACTION, "insert_table", "表格",
                R.drawable.lolib_ic_insert_table, ".uno:InsertTable?Columns=2&Rows=2"));
        insert.add(new PanelItem(ItemType.ACTION, "insert_shape", "形状",
                R.drawable.lolib_ic_insert_shape, ""));
        insert.add(new PanelItem(ItemType.ACTION, "insert_comment", "批注",
                R.drawable.lolib_ic_calc_insert_comment, ".uno:InsertAnnotation"));
        insert.add(new PanelItem(ItemType.ACTION, "insert_hyperlink", "超链接",
                R.drawable.lolib_ic_calc_insert_hyperlink, ".uno:HyperlinkDialog"));
        Log.i(TAG, "buildTabs insert_items=" + insert.size());
        return insert;
    }

    private View createActionRow(PanelItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(56));
        row.setPadding(host.dpToPx(16), host.dpToPx(14), host.dpToPx(16), host.dpToPx(14));

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(32), host.dpToPx(32)));
        }

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (item.iconResId != 0) {
            labelLp.setMarginStart(host.dpToPx(12));
        }
        row.addView(label, labelLp);
        row.setOnClickListener(v -> runItemAction(item));
        return row;
    }

    private void runItemAction(PanelItem item) {
        if ("insert_shape".equals(item.id)) {
            showShapePicker();
            return;
        }
        runAndDismiss(() -> {
            if (item.hostAction != null) {
                item.hostAction.run();
            } else if (item.unoCommand != null && !item.unoCommand.isEmpty()) {
                host.executeUnoCommand(item.unoCommand);
            }
        });
    }

    private void runAndDismiss(Runnable action) {
        dismiss();
        host.runAfterFunctionPanelDismiss(action);
    }

    private void showShapePicker() {
        if (shapePickerController == null) {
            shapePickerController = new ImpressShapePickerController(new ImpressShapePickerController.Host() {
                @Override
                public android.content.Context getContext() {
                    return host.getContext();
                }

                @Override
                public int dpToPx(int dp) {
                    return host.dpToPx(dp);
                }

                @Override
                public void executeUnoCommand(String command) {
                    host.executeUnoCommand(command);
                }

                @Override
                public void runAfterDismiss(Runnable action) {
                    dismiss();
                    host.runAfterFunctionPanelDismiss(action);
                }
            });
        }
        shapePickerController.show();
        Log.i(TAG, "insert_shape_picker_open");
    }

    private View buildTransitionTabContent() {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(createApplyToAllRow());
        root.addView(createTransitionGrid());
        return root;
    }

    private View createApplyToAllRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(56));
        row.setPadding(host.dpToPx(16), host.dpToPx(12), host.dpToPx(16), host.dpToPx(8));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(R.drawable.lolib_ic_impress_apply_transition_all);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(32), host.dpToPx(32)));

        TextView label = new TextView(host.getContext());
        label.setText("应用到全部幻灯片");
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(host.dpToPx(12));
        row.addView(label, labelLp);

        row.setOnClickListener(v -> onApplyTransitionToAll());
        return row;
    }

    private View createTransitionGrid() {
        transitionGridRoot = new LinearLayout(host.getContext());
        transitionGridRoot.setOrientation(LinearLayout.VERTICAL);
        int gap = host.dpToPx(GRID_GAP_DP);
        int sidePad = host.dpToPx(12);
        transitionGridRoot.setPadding(sidePad, 0, sidePad, host.dpToPx(12));

        ImpressTransitionCatalog.Entry[] entries = ImpressTransitionCatalog.ENTRIES;
        for (int rowStart = 0; rowStart < entries.length; rowStart += TRANSITION_GRID_COLS) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int rowEnd = Math.min(rowStart + TRANSITION_GRID_COLS, entries.length);
            for (int i = rowStart; i < rowEnd; i++) {
                ImpressTransitionCatalog.Entry entry = entries[i];
                row.addView(createTransitionCell(entry, i < rowEnd - 1, gap));
            }
            if (rowStart > 0) {
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = gap;
                row.setLayoutParams(rowLp);
            }
            transitionGridRoot.addView(row);
        }
        return transitionGridRoot;
    }

    private View createTransitionCell(ImpressTransitionCatalog.Entry entry, boolean addEndGap, int gap) {
        LinearLayout cell = new LinearLayout(host.getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean selected = entry.index == selectedTransitionIndex;
        cell.setBackgroundResource(selected
                ? R.drawable.lolib_bg_impress_transition_cell_selected
                : R.drawable.lolib_bg_impress_transition_cell);
        int vPad = host.dpToPx(TRANSITION_CELL_VPAD_DP);
        cell.setPadding(host.dpToPx(4), vPad, host.dpToPx(4), vPad);
        cell.setMinimumHeight(host.dpToPx(TRANSITION_CELL_MIN_H_DP));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(entry.iconResId);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cell.addView(icon, new LinearLayout.LayoutParams(
                host.dpToPx(TRANSITION_ICON_SIZE_DP), host.dpToPx(TRANSITION_ICON_SIZE_DP)));

        TextView caption = new TextView(host.getContext());
        caption.setText(entry.label);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(COLOR_TITLE);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        caption.setMaxLines(1);
        caption.setPadding(0, host.dpToPx(4), 0, 0);
        cell.addView(caption);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (addEndGap) {
            lp.setMarginEnd(gap);
        }
        cell.setLayoutParams(lp);
        cell.setTag(entry.index);
        cell.setOnClickListener(v -> onTransitionCellClick(entry));
        return cell;
    }

    private void onTransitionCellClick(ImpressTransitionCatalog.Entry entry) {
        selectedTransitionIndex = entry.index;
        refreshTransitionSelection();
        host.applySlideTransition(entry.iconViewIndex, false);
        Log.i(TAG, "transition_selected index=" + entry.index
                + " label=" + entry.label + " iconViewIndex=" + entry.iconViewIndex);
    }

    private void onApplyTransitionToAll() {
        ImpressTransitionCatalog.Entry entry = ImpressTransitionCatalog.byIndex(selectedTransitionIndex);
        if (entry == null) {
            host.toastTodo("请先选择切换效果");
            return;
        }
        host.applySlideTransition(entry.iconViewIndex, true);
        Log.i(TAG, "transition_apply_all index=" + entry.index + " label=" + entry.label);
    }

    private void refreshTransitionSelection() {
        if (transitionGridRoot == null) {
            return;
        }
        for (int r = 0; r < transitionGridRoot.getChildCount(); r++) {
            View rowView = transitionGridRoot.getChildAt(r);
            if (!(rowView instanceof LinearLayout)) {
                continue;
            }
            LinearLayout row = (LinearLayout) rowView;
            for (int c = 0; c < row.getChildCount(); c++) {
                View cell = row.getChildAt(c);
                Object tag = cell.getTag();
                boolean selected = tag instanceof Integer && ((Integer) tag) == selectedTransitionIndex;
                cell.setBackgroundResource(selected
                        ? R.drawable.lolib_bg_impress_transition_cell_selected
                        : R.drawable.lolib_bg_impress_transition_cell);
            }
        }
    }

    private static final int[] CHAR_TOOL_ICONS_ROW1 = {
            R.drawable.lolib_ic_calc_bold,
            R.drawable.lolib_ic_calc_italic,
            R.drawable.lolib_ic_calc_underline,
            R.drawable.lolib_ic_calc_strikethrough,
            R.drawable.lolib_ic_calc_shadow,
            R.drawable.lolib_ic_calc_highlight,
    };
    private static final String[] CHAR_TOOL_COMMANDS_ROW1 = {
            ".uno:Bold",
            ".uno:Italic",
            ".uno:Underline",
            ".uno:Strikeout",
            ".uno:Shadowed",
            ".uno:CharBackColor",
    };
    private static final int[] CHAR_TOOL_ICONS_ROW2 = {
            R.drawable.lolib_ic_calc_superscript,
            R.drawable.lolib_ic_calc_subscript,
    };
    private static final String[] CHAR_TOOL_COMMANDS_ROW2 = {
            ".uno:SuperScript",
            ".uno:SubScript",
    };

    private static final String[] LAYOUT_LABELS = {
            "标题幻灯片", "标题和内容", "节标题"
    };
    private static final int[] LAYOUT_ICONS = {
            R.drawable.lolib_ic_impress_layout_title,
            R.drawable.lolib_ic_impress_layout_title_content,
            R.drawable.lolib_ic_impress_layout_section,
    };
    private static final String[] LAYOUT_COMMANDS = {
            ".uno:AssignLayout?WhatLayout:short=0",
            ".uno:AssignLayout?WhatLayout:short=1",
            ".uno:AssignLayout?WhatLayout:short=2",
    };

    private static final String[] PARA_LABELS = {
            "左对齐", "居中对齐", "右对齐", "两端对齐", "无序列表", "有序列表"
    };
    private static final int[] PARA_ICONS = {
            R.drawable.lolib_ic_calc_align_left,
            R.drawable.lolib_ic_calc_align_center_h,
            R.drawable.lolib_ic_calc_align_right,
            R.drawable.lolib_ic_calc_align_justify,
            R.drawable.lolib_ic_impress_bullet_list,
            R.drawable.lolib_ic_impress_number_list,
    };
    private static final String[] PARA_COMMANDS = {
            ".uno:LeftPara",
            ".uno:CenterPara",
            ".uno:RightPara",
            ".uno:JustifyPara",
            ".uno:DefaultBullet",
            ".uno:DefaultNumbering",
    };

    private static final String[] FORMAT_LABELS = { "16:9 屏幕", "4:3 屏幕", "A4 纸张" };
    private static final String[] ORIENTATION_LABELS = { "横向", "纵向" };
    private static final String[] ORIENTATION_COMMANDS = {
            ".uno:Orientation?isLandscape:bool=true",
            ".uno:Orientation?isLandscape:bool=false",
    };

    private static final String[] FALLBACK_FONT_OPTIONS = {
            "宋体", "Liberation Serif", "Liberation Sans", "Arial"
    };
    private static final String[] FALLBACK_FONT_VALUES = FALLBACK_FONT_OPTIONS;

    private static final String[] SIZE_OPTIONS = {
            "初号", "小初", "一号", "小一", "二号", "小二", "三号", "小三", "四号", "小四", "五号", "小五"
    };
    private static final String[] SIZE_VALUES = {
            "42", "36", "26", "24", "22", "18", "16", "15", "14", "12", "10.5", "9"
    };
}
