package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;
import org.libreoffice.androidlib.calc.CalcFontColorPickerController;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calc edit-mode function panel: 常用 / 文件 / 插入 / 布局 / 数据 / 审阅.
 * UI follows Figma mobile Calc formatting sheet.
 */
public class CalcFunctionPanelController {
    private static final String TAG = "CalcFunctionPanel";

    private static final int COLOR_TAB_ACTIVE = Color.parseColor("#3B8040");
    private static final int COLOR_TAB_INACTIVE = Color.parseColor("#333333");
    private static final int COLOR_SECTION = Color.parseColor("#80868B");
    private static final int COLOR_TITLE = Color.parseColor("#101010");
    private static final int COLOR_VALUE = Color.parseColor("#6A6A6A");
    private static final int COLOR_DIVIDER = Color.parseColor("#E3E3E3");
    /** Figma 750×1624: sheet height 1066px ≈ 65.6% screen. */
    private static final float SHEET_HEIGHT_RATIO = BottomSheetAnchorHelper.FUNCTION_PANEL_HEIGHT_RATIO;
    /** Figma px ÷ 2 → dp. */
    private static final int CHAR_CELL_W_DP = 51;
    private static final int CHAR_CELL_H_DP = 50;
    private static final int CHAR_CELL_PAD_DP = 12;
    private static final int ICON_SIZE_DP = 24;
    /** 对齐/边框等 icon 网格：单行内等分铺满时的单元格高度 */
    private static final int ICON_CELL_FILL_H_DP = 44;
    private static final int NUMFMT_CELL_W_DP = 62;
    private static final int NUMFMT_CELL_H_DP = 80;
    private static final int NUMFMT_CELL_VPAD_DP = 12;
    private static final int GRID_GAP_DP = 5;
    private static final int CARD_INNER_PADDING_DP = 8;

    private enum ColorPickerKind {
        FONT(".uno:Color", "Color.Color"),
        BACKGROUND(".uno:BackgroundColor", "BackgroundColor.Color"),
        BORDER(null, null);

        final String unoCommand;
        final String propertyName;

        ColorPickerKind(String unoCommand, String propertyName) {
            this.unoCommand = unoCommand;
            this.propertyName = propertyName;
        }
    }

    // Interfaces use FunctionPanelController's types (identical signatures) for LOActivity compatibility.
    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void executeUnoCommand(String command);

        void saveDocument();

        void saveDocumentAs();

        void exportDocumentAsPdf();

        void initiatePrint();

        void openLocalImagePickerFromWeb();

        void insertComment();

        String getCommentAuthorName();
        String getCommentAuthorAvatarUri();

        void insertCommentWithText(String text);

        void toastTodo(String text);

        void applyFont(String fontName);

        void applyFontSize(String fontSizePt);

        void fetchFontList(FunctionPanelController.StringListCallback callback);

        void fetchCurrentFormatting(FunctionPanelController.FormattingCallback callback);

        void showAiOperationSheet();

        void focusDocumentAndShowIme();

        /** Run after function panel dismiss animation (avoids UNO lost to focus/socket race). */
        void runAfterFunctionPanelDismiss(Runnable action);

        /** 插入指定类型的 Calc 图表（功能面板图表类型页选中后调用）。 */
        void insertChartWithType(String unoChartType);

        /** 插入超链接（功能面板超链接页「添加」后调用）。 */
        void insertHyperlink(String displayText, String url);

        /** Bottom toolbar + nav spacer height; sheets anchor above this chrome. */
        int getBottomChromeHeightPx();

        /** 预读 Calc 选区 / 工作表，供超链接文档 Tab 默认值。 */
        void fetchCalcHyperlinkContext(CalcHyperlinkPickerController.HyperlinkContextCallback callback);

        /** 应用数据有效性（自定义 UI 确定后）。 */
        void applyCalcDataValidation(CalcDataValidationState state);

        /** 打开 CO 宏浏览对话框。 */
        void openMacroChooser(CalcValidationMacroPickerController.MacroChooseCallback callback);

        /** 读当前选区已有有效性设置，异步回填 target 后回调 onLoaded。 */
        void loadCurrentValidationState(CalcDataValidationState target, Runnable onLoaded);

        /** 关闭可能残留的 CO Validation 原生对话框。 */
        void dismissCoValidationDialog();

        /** 枚举真实宏树，回调 catalog。 */
        void loadMacroCatalog(CalcValidationMacroCatalog.Callback callback);
    }

    private enum ItemType {
        SECTION,
        PICKER_ROW,
        PICKER_PAIR,
        COLOR_PICKER_PAIR,
        TOOL_BUTTONS,
        FORMAT_GRID,
        STEPPER_PAIR,
        TOGGLE,
        TOGGLE_PAIR,
        ICON_GRID,
        ACTION,
        GRID_ACTION,
        /** 图标 + 标签(左) + 状态值(右) + 箭头：布局 Tab 纸张方向 / 打印区域。 */
        ICON_VALUE_ROW
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
        final int[] colsPerRow;
        final boolean wrapInCard;
        final boolean defaultOn;
        final PanelItem[] submenuItems;

        PanelItem(ItemType type, String id, String label) {
            this(type, id, label, "", "", null, 0, null, null, null, 0, null, false, false, null);
        }

        PanelItem(ItemType type, String id, String label, String subtitle) {
            this(type, id, label, subtitle, "", null, 0, null, null, null, 0, null, false, false, null);
        }

        PanelItem(ItemType type, String id, String label, String subtitle, int iconResId) {
            this(type, id, label, subtitle, "", null, iconResId, null, null, null, 0, null, false, false, null);
        }

        PanelItem(ItemType type, String id, String label, String unoCommand, Runnable hostAction) {
            this(type, id, label, "", unoCommand, hostAction, 0, null, null, null, 0, null, false, false, null);
        }

        PanelItem(ItemType type, String id, String label, int iconResId, Runnable hostAction) {
            this(type, id, label, "", "", hostAction, iconResId, null, null, null, 0, null, false, false, null);
        }

        PanelItem(ItemType type, String id, String label, int iconResId, String unoCommand) {
            this(type, id, label, "", unoCommand, null, iconResId, null, null, null, 0, null, false, false, null);
        }

        PanelItem(ItemType type, String id, String label, int iconResId, PanelItem[] submenuItems) {
            this(type, id, label, "", "", null, iconResId, null, null, null, 0, null, false, false,
                    submenuItems);
        }

        PanelItem(ItemType type, String id, String label, String[] gridLabels, String[] gridCommands,
                int[] gridIconRes, int cols) {
            this(type, id, label, "", "", null, 0, gridLabels, gridCommands, gridIconRes, cols, null, false, false,
                    null);
        }

        PanelItem(ItemType type, String id, String label, String[] gridLabels, String[] gridCommands,
                int[] gridIconRes, int cols, int[] colsPerRow, boolean wrapInCard) {
            this(type, id, label, "", "", null, 0, gridLabels, gridCommands, gridIconRes, cols, colsPerRow,
                    wrapInCard, false, null);
        }

        PanelItem(ItemType type, String id, String label, String unoCommand, boolean defaultOn) {
            this(type, id, label, "", unoCommand, null, 0, null, null, null, 0, null, false, defaultOn, null);
        }

        PanelItem(ItemType type, String id, String label, String subtitle, String unoCommand,
                Runnable hostAction, int iconResId, String[] gridLabels, String[] gridCommands, int[] gridIconRes,
                int cols, int[] colsPerRow, boolean wrapInCard, boolean defaultOn) {
            this(type, id, label, subtitle, unoCommand, hostAction, iconResId, gridLabels, gridCommands, gridIconRes,
                    cols, colsPerRow, wrapInCard, defaultOn, null);
        }

        PanelItem(ItemType type, String id, String label, String subtitle, String unoCommand,
                Runnable hostAction, int iconResId, String[] gridLabels, String[] gridCommands, int[] gridIconRes,
                int cols, int[] colsPerRow, boolean wrapInCard, boolean defaultOn, PanelItem[] submenuItems) {
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
            this.colsPerRow = colsPerRow;
            this.wrapInCard = wrapInCard;
            this.defaultOn = defaultOn;
            this.submenuItems = submenuItems;
        }

        boolean hasSubmenu() {
            return submenuItems != null && submenuItems.length > 0;
        }
    }

    private enum GridMode {
        FORMAT_WITH_LABEL,
        ICON_COMPACT
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
    private List<PanelTab> tabs;
    private BottomSheetDialog dialog;
    private PopupWindow fontSizePopup;
    private LinearLayout tabBar;
    private NestedScrollView contentContainer;
    private View tabHeader;
    private View tabIndicatorArea;
    private View fontPickerPanel;
    private View sheetRoot;
    private View tabIndicator;
    private boolean fontPickerVisible;
    private boolean submenuVisible;
    private boolean chartPickerVisible;
    private boolean hyperlinkPickerVisible;
    private CalcHyperlinkPickerController hyperlinkPicker;
    private boolean dataValidationVisible;
    private CalcDataValidationController dataValidationPicker;
    private boolean currentBold;
    private boolean currentItalic;
    private boolean currentUnderline;
    private boolean currentStrikethrough;
    private boolean colorPickerVisible;
    private CalcFontColorPickerController colorPicker;
    private ColorPickerKind activeColorPickerKind;
    private String activeColorPickerId;
    private ImageView activeColorPreviewDot;
    private int activeColorPreviewFallback;
    private ImageView fontColorPreviewDot;
    private ImageView bgColorPreviewDot;
    private ImageView borderColorPreviewDot;
    private int submenuReturnTabIndex = 0;
    private int selectedTabIndex = 0;
    private ImpressCommentPickerController commentPicker;
    private boolean commentPickerVisible;
    private boolean optionPickerVisible;
    private final List<TextView> tabViews = new ArrayList<>();
    private final Map<String, String> pickerValues = new HashMap<>();
    private final Map<String, Integer> pickerColorRgb = new HashMap<>();
    private final Map<String, Integer> pickerColorIndex = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private String[] cachedFontOptions = FALLBACK_FONT_OPTIONS;
    private String[] cachedFontValues = FALLBACK_FONT_VALUES;

    public CalcFunctionPanelController(Host host) {
        this.host = host;
        this.tabs = buildTabs();
        pickerValues.put("font_name", "宋体");
        pickerValues.put("font_size", "4 pt");
        pickerValues.put("font_color", "字体颜色");
        pickerValues.put("bg_color", "背景颜色");
        pickerValues.put("border_color", "边框颜色");
        toggleStates.put("negative_red", true);
        toggleStates.put("thousands_sep", false);
        toggleStates.put("vertical_stack", false);
        toggleStates.put("wrap_text", false);
        toggleStates.put("merge_cells", false);
    }

    public void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        View panel = LayoutInflater.from(host.getContext())
                .inflate(R.layout.lolib_sheet_calc_functions, null, false);
        sheetRoot = panel;
        tabHeader = panel.findViewById(R.id.calc_function_tab_header);
        tabIndicatorArea = panel.findViewById(R.id.calc_function_tab_indicator_area);
        tabBar = panel.findViewById(R.id.calc_function_tab_bar);
        contentContainer = panel.findViewById(R.id.calc_function_content_container);
        tabIndicator = panel.findViewById(R.id.calc_function_tab_indicator);
        fontPickerPanel = panel.findViewById(R.id.calc_function_font_picker_panel);

        ImageButton aiBtn = panel.findViewById(R.id.calc_function_btn_ai);
        ImageButton keyboardBtn = panel.findViewById(R.id.calc_function_btn_keyboard);
        ImageButton collapseBtn = panel.findViewById(R.id.calc_function_btn_collapse);
        if (aiBtn != null) {
            aiBtn.setOnClickListener(v -> {
                dismiss();
                host.showAiOperationSheet();
            });
        }
        if (keyboardBtn != null) {
            keyboardBtn.setOnClickListener(v -> {
                dismiss();
                host.runAfterFunctionPanelDismiss(() -> host.focusDocumentAndShowIme());
            });
        }
        if (collapseBtn != null) {
            collapseBtn.setOnClickListener(v -> dismiss());
        }

        tabs = buildTabs();
        buildTabBar();
        selectTab(0);
        syncCurrentFormatting();

        dialog = new BottomSheetDialog(host.getContext());
        dialog.setContentView(panel);
        AiDialogHelper.applyCloseOnlyDismiss(dialog);
        dialog.setOnDismissListener(d -> {
            BottomSheetAnchorHelper.clearAppliedHeight(dialog);
            dialog = null;
        });
        dialog.setOnShowListener(d -> applyAdaptiveSheetHeight());
        dialog.show();
    }

    private void applyAdaptiveSheetHeight() {
        if (dialog == null) {
            return;
        }
        BottomSheetAnchorHelper.clearAppliedHeight(dialog);
        AiDialogHelper.applyNoDimScrim(dialog);
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(R.drawable.lolib_bg_calc_bottom_sheet);
        }
        BottomSheetAnchorHelper.Options options = new BottomSheetAnchorHelper.Options();
        options.logTag = TAG;
        BottomSheetAnchorHelper.expandFunctionPanel(dialog, SHEET_HEIGHT_RATIO, options);
    }

    public void dismiss() {
        dismissFontPicker();
        dismissSubmenuPage();
        dismissChartPickerPage();
        dismissHyperlinkPickerPage();
        dismissColorPickerPage();
        dismissOptionPickerPage();
        dismissCommentPickerPage();
        dismissFontSizePopup();
        if (dataValidationVisible) {
            dataValidationVisible = false;
            host.dismissCoValidationDialog();
        }
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    /** 横竖屏切换：关闭锚点浮层/二级页，并重算 BottomSheet 高度。 */
    public void onConfigurationChanged() {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        dismissFontPicker();
        dismissSubmenuPage();
        dismissChartPickerPage();
        dismissHyperlinkPickerPage();
        dismissColorPickerPage();
        dismissOptionPickerPage();
        dismissCommentPickerPage();
        dismissFontSizePopup();
        applyAdaptiveSheetHeight();
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            tabView.setLayoutParams(lp);
            tabView.setOnClickListener(v -> selectTab(index));
            tabBar.addView(tabView);
            tabViews.add(tabView);
        }
    }

    private void selectTab(int index) {
        if (submenuVisible) {
            submenuVisible = false;
            setTabChromeVisible(true);
        }
        if (chartPickerVisible) {
            chartPickerVisible = false;
            setTabChromeVisible(true);
        }
        if (hyperlinkPickerVisible) {
            hyperlinkPickerVisible = false;
            setTabChromeVisible(true);
        }
        if (colorPickerVisible) {
            colorPickerVisible = false;
            setTabChromeVisible(true);
        }
        if (optionPickerVisible) {
            optionPickerVisible = false;
            setTabChromeVisible(true);
        }
        if (commentPickerVisible) {
            commentPickerVisible = false;
            setTabChromeVisible(true);
        }
        dismissFontPicker();
        selectedTabIndex = index;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tabView = tabViews.get(i);
            tabView.setTextColor(i == index ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
            tabView.setTypeface(null, Typeface.NORMAL);
        }
        renderTabContent(tabs.get(index));
        if (contentContainer != null) {
            contentContainer.scrollTo(0, 0);
        }
        if (dialog != null && dialog.isShowing()) {
            final int tabIndex = index;
            View anchor = contentContainer != null ? contentContainer : tabBar;
            anchor.post(() -> {
                applyAdaptiveSheetHeight();
                updateTabIndicator(tabIndex);
            });
        } else {
            updateTabIndicator(index);
        }
    }

    private void updateTabIndicator(int index) {
        if (tabIndicator == null || index < 0 || index >= tabViews.size()) {
            return;
        }
        FunctionPanelTabIndicatorHelper.updateForSelectedTab(tabViews.get(index), tabBar, tabIndicator);
    }

    private void renderTabContent(PanelTab tab) {
        contentContainer.removeAllViews();
        if ("insert".equals(tab.id)) {
            Log.i(TAG, "insert_tab_render items=" + tab.items.size()
                    + " firstType=" + (tab.items.isEmpty() ? "none" : tab.items.get(0).type));
        }
        if ("data".equals(tab.id)) {
            Log.i(TAG, "data_tab_render items=" + tab.items.size());
        }
        if ("review".equals(tab.id)) {
            Log.i(TAG, "review_tab_render items=" + tab.items.size());
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        List<PanelItem> pendingTools = new ArrayList<>();
        List<PanelItem> pendingGrid = new ArrayList<>();

        for (PanelItem item : tab.items) {
            switch (item.type) {
                case SECTION:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createSectionLabel(item.label));
                    break;
                case PICKER_ROW:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createFullPickerRow(item));
                    break;
                case ICON_VALUE_ROW:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createIconValueRow(item));
                    break;
                case PICKER_PAIR:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createSplitPickerRow(item));
                    break;
                case COLOR_PICKER_PAIR:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createColorPickerPairRow(item));
                    break;
                case TOOL_BUTTONS:
                    pendingTools.add(item);
                    break;
                case FORMAT_GRID:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createFormatGrid(item));
                    break;
                case STEPPER_PAIR:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createStepperPairRow(item));
                    break;
                case TOGGLE:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createToggleRow(item));
                    break;
                case TOGGLE_PAIR:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createTogglePairRow(item));
                    break;
                case ICON_GRID:
                    pendingGrid.add(item);
                    break;
                case ACTION:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createActionRow(item));
                    if (tab.items.indexOf(item) < tab.items.size() - 1) {
                        root.addView(createDivider());
                    }
                    break;
                case GRID_ACTION:
                    flushToolButtons(root, pendingTools);
                    flushIconGrid(root, pendingGrid);
                    root.addView(createSingleGridCell(item));
                    break;
                default:
                    break;
            }
        }
        flushToolButtons(root, pendingTools);
        flushIconGrid(root, pendingGrid);
        contentContainer.addView(root);
    }

    private void flushToolButtons(LinearLayout root, List<PanelItem> tools) {
        if (tools.isEmpty()) {
            return;
        }
        PanelItem combined = tools.get(0);
        root.addView(createToolButtonRow(combined));
        tools.clear();
    }

    private void flushIconGrid(LinearLayout root, List<PanelItem> grids) {
        if (grids.isEmpty()) {
            return;
        }
        for (PanelItem grid : grids) {
            root.addView(createIconGrid(grid));
        }
        grids.clear();
    }

    private TextView createSectionLabel(String title) {
        TextView label = new TextView(host.getContext());
        label.setText(title);
        label.setTextColor(COLOR_SECTION);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(host.dpToPx(2), host.dpToPx(14), host.dpToPx(2), host.dpToPx(6));
        return label;
    }

    private View createDivider() {
        View divider = new View(host.getContext());
        divider.setBackgroundColor(COLOR_DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return divider;
    }

    private View createFullPickerRow(PanelItem item) {
        LinearLayout card = createCardRow();
        int colorIconRes = 0;
        if ("bg_color".equals(item.id)) {
            colorIconRes = R.drawable.lolib_ic_calc_color_bg_preview;
        } else if ("border_color".equals(item.id)) {
            colorIconRes = R.drawable.lolib_ic_calc_color_border_preview;
        }
        TextView value = new TextView(host.getContext());
        value.setText(pickerValues.getOrDefault(item.id, item.subtitle));
        value.setTextColor(COLOR_TITLE);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(value, lp);
        if (colorIconRes != 0) {
            card.addView(createColorSwatchView(colorIconRes, null));
        }
        card.addView(createChevron());
        card.setOnClickListener(v -> onPickerClick(item, value));
        return wrapBottomMargin(card);
    }

    /** 布局 Tab 行：左侧图标 + 标签(主) + 右侧状态值 + 箭头（Figma 128px=64dp，无卡片底，底部细分隔线）。 */
    private View createIconValueRow(PanelItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(64));
        row.setPadding(host.dpToPx(12), 0, host.dpToPx(12), 0);

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(item.iconResId);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(host.dpToPx(32), host.dpToPx(32));
        iconLp.setMarginEnd(host.dpToPx(12));
        row.addView(icon, iconLp);

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(Color.parseColor("#333333"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(host.getContext());
        value.setText(pickerValues.getOrDefault(item.id, item.subtitle));
        value.setTextColor(COLOR_VALUE);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.setMarginEnd(host.dpToPx(8));
        row.addView(value, valueLp);
        row.addView(createChevron());
        row.setOnClickListener(v -> onPickerClick(item, value));

        LinearLayout container = new LinearLayout(host.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(row);
        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#00000014"));
        container.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return container;
    }

    private View createSplitPickerRow(PanelItem item) {
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
        left.setOnClickListener(v -> showFontSizePopup(sizeValue, left));

        LinearLayout right = createCardRow();
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(host.dpToPx(10));
        right.setLayoutParams(rightLp);
        ImageView colorDot = new ImageView(host.getContext());
        colorDot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fontColorPreviewDot = colorDot;
        updateColorPreviewDot(colorDot, pickerColorRgb.get("font_color"),
                R.drawable.lolib_ic_calc_color_font_preview);
        TextView colorLabel = new TextView(host.getContext());
        colorLabel.setText("字体颜色");
        colorLabel.setTextColor(COLOR_TITLE);
        colorLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        right.addView(createColorSwatchView(0, colorDot));
        right.addView(colorLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        right.addView(createChevron());
        right.setOnClickListener(v -> showColorPickerPage(
                ColorPickerKind.FONT, "font_color", fontColorPreviewDot,
                R.drawable.lolib_ic_calc_color_font_preview, "字体颜色"));

        row.addView(left);
        row.addView(right);
        return wrapBottomMargin(row);
    }

    private View createToolButtonRow(PanelItem item) {
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
            // Apply selected-state visual feedback for toggle-style char format buttons
            boolean isActive = isCharCommandActive(command);
            btn.setSelected(isActive);
            if (isActive) {
                btn.getBackground().mutate().setTint(0xFF1A73E8);
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            }
            btn.setOnClickListener(v -> host.executeUnoCommand(command));
            row.addView(btn);
        }
        return row;
    }

    private boolean isCharCommandActive(String command) {
        switch (command) {
            case ".uno:Bold": return currentBold;
            case ".uno:Italic": return currentItalic;
            case ".uno:Underline": return currentUnderline;
            case ".uno:Strikeout": return currentStrikethrough;
            default: return false;
        }
    }

    private View createFormatGrid(PanelItem item) {
        return createLabeledGrid(item.gridLabels, item.gridCommands, item.gridIconRes, item.cols,
                item.colsPerRow, GridMode.FORMAT_WITH_LABEL);
    }

    private View createIconGrid(PanelItem item) {
        View grid = createLabeledGrid(item.gridLabels, item.gridCommands, item.gridIconRes, item.cols,
                item.colsPerRow, GridMode.ICON_COMPACT);
        if (item.wrapInCard) {
            return wrapInCardContainer(grid);
        }
        return wrapBottomMargin(grid);
    }

    private View wrapInCardContainer(View content) {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int pad = host.dpToPx(CARD_INNER_PADDING_DP);
        card.setPadding(pad, pad, pad, pad);
        card.addView(content);
        return wrapBottomMargin(card);
    }

    private View createLabeledGrid(String[] labels, String[] commands, int[] iconRes, int cols,
            int[] colsPerRow, GridMode mode) {
        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        if (labels == null || labels.length == 0) {
            return grid;
        }
        int gap = host.dpToPx(GRID_GAP_DP);
        int maxCols = computeGridMaxCols(cols, colsPerRow);
        int index = 0;
        int rowIndex = 0;
        while (index < labels.length) {
            int columnCount = colsPerRow != null && rowIndex < colsPerRow.length
                    ? colsPerRow[rowIndex] : (cols > 0 ? cols : 5);
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (mode == GridMode.ICON_COMPACT && rowIndex > 0) {
                rowLp.topMargin = gap;
            }
            row.setLayoutParams(rowLp);
            int rowEnd = Math.min(index + columnCount, labels.length);
            int itemsInRow = rowEnd - index;
            if (mode == GridMode.ICON_COMPACT) {
                int cellHeight = host.dpToPx(ICON_CELL_FILL_H_DP);
                for (int slot = 0; slot < maxCols; slot++) {
                    LinearLayout.LayoutParams slotLp = createEqualWidthSlotParams(
                            maxCols, slot, gap, cellHeight);
                    if (slot < itemsInRow) {
                        int i = index + slot;
                        final String label = labels[i];
                        final String command = commands != null && i < commands.length ? commands[i] : "";
                        row.addView(createGridCell(label, command, iconRes, i, mode, slotLp));
                    } else {
                        row.addView(new View(host.getContext()), slotLp);
                    }
                }
            } else {
                for (int i = index; i < rowEnd; i++) {
                    final String label = labels[i];
                    final String command = commands != null && i < commands.length ? commands[i] : "";
                    row.addView(createGridCell(label, command, iconRes, i, mode, i < rowEnd - 1, gap));
                }
            }
            grid.addView(row);
            index = rowEnd;
            rowIndex++;
        }
        return grid;
    }

    /** 网格最大列数：以 cols 与 colsPerRow 中最大者为准，末行 item 不足时仍按此列宽左对齐。 */
    private int computeGridMaxCols(int cols, int[] colsPerRow) {
        int maxCols = cols > 0 ? cols : 5;
        if (colsPerRow != null) {
            for (int count : colsPerRow) {
                if (count > maxCols) {
                    maxCols = count;
                }
            }
        }
        return maxCols;
    }

    private LinearLayout.LayoutParams createEqualWidthSlotParams(
            int maxCols, int slotIndex, int gap, int heightPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1f);
        if (slotIndex < maxCols - 1) {
            lp.setMarginEnd(gap);
        }
        return lp;
    }

    private View createGridCell(String label, String command, int[] iconRes, int iconIndex,
            GridMode mode, boolean addEndGap, int gap) {
        LinearLayout.LayoutParams lp;
        if (mode == GridMode.FORMAT_WITH_LABEL) {
            lp = new LinearLayout.LayoutParams(0, host.dpToPx(NUMFMT_CELL_H_DP), 1f);
        } else {
            lp = new LinearLayout.LayoutParams(0, host.dpToPx(ICON_CELL_FILL_H_DP), 1f);
        }
        if (addEndGap) {
            lp.setMarginEnd(gap);
        }
        if (mode != GridMode.ICON_COMPACT) {
            lp.bottomMargin = gap;
        }
        return createGridCell(label, command, iconRes, iconIndex, mode, lp);
    }

    private View createGridCell(String label, String command, int[] iconRes, int iconIndex,
            GridMode mode, LinearLayout.LayoutParams lp) {
        boolean showLabelBelow = mode == GridMode.FORMAT_WITH_LABEL;
        LinearLayout cell = new LinearLayout(host.getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        if (mode != GridMode.ICON_COMPACT) {
            cell.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        }
        if (mode == GridMode.FORMAT_WITH_LABEL) {
            int vPad = host.dpToPx(NUMFMT_CELL_VPAD_DP);
            cell.setPadding(host.dpToPx(6), vPad, host.dpToPx(6), vPad);
            cell.setMinimumHeight(host.dpToPx(NUMFMT_CELL_H_DP));
        } else {
            cell.setPadding(host.dpToPx(4), host.dpToPx(4), host.dpToPx(4), host.dpToPx(4));
        }

        if (iconRes != null && iconIndex < iconRes.length && iconRes[iconIndex] != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(iconRes[iconIndex]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cell.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(ICON_SIZE_DP), host.dpToPx(ICON_SIZE_DP)));
        } else {
            TextView iconText = new TextView(host.getContext());
            iconText.setText(extractGridIconText(label));
            iconText.setGravity(Gravity.CENTER);
            iconText.setTextColor(COLOR_TITLE);
            iconText.setTextSize(TypedValue.COMPLEX_UNIT_SP, showLabelBelow ? 18 : 14);
            iconText.setTypeface(Typeface.DEFAULT_BOLD);
            cell.addView(iconText);
        }

        if (showLabelBelow) {
            TextView caption = new TextView(host.getContext());
            caption.setText(label);
            caption.setGravity(Gravity.CENTER);
            caption.setTextColor(COLOR_TITLE);
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            caption.setPadding(0, host.dpToPx(6), 0, 0);
            cell.addView(caption);
        }

        return finishGridCell(cell, command, label, lp);
    }

    private View finishGridCell(LinearLayout cell, String command, String label,
            LinearLayout.LayoutParams lp) {
        cell.setLayoutParams(lp);
        cell.setOnClickListener(v -> onGridCommand(command, label));
        return cell;
    }

    private String extractGridIconText(String label) {
        if (label == null || label.isEmpty()) {
            return "?";
        }
        int space = label.indexOf(' ');
        return space > 0 ? label.substring(0, space) : label.substring(0, Math.min(2, label.length()));
    }

    private View createStepperPairRow(PanelItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        if ("indent_steppers".equals(item.id)) {
            row.addView(createLabeledStepperColumn(
                    "缩进", "0 点(pt)",
                    ".uno:IncrementIndent", ".uno:DecrementIndent"), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMarginStart(host.dpToPx(10));
            View right = createLabeledStepperColumn(
                    "文本方向", "0 °",
                    ".uno:TextDirection", ".uno:TextDirection");
            right.setLayoutParams(rightLp);
            row.addView(right);
        } else {
            row.addView(createLabeledStepperColumn(
                    "小数位数", "0.0 1",
                    ".uno:NumberFormatIncDecimals", ".uno:NumberFormatDecDecimals"),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMarginStart(host.dpToPx(10));
            View right = createLabeledStepperColumn(
                    "前导零", "0.0 1",
                    ".uno:LeadingZeroes", ".uno:LeadingZeroes");
            right.setLayoutParams(rightLp);
            row.addView(right);
        }
        return wrapBottomMargin(row);
    }

    private View createLabeledStepperColumn(String title, String valueText, String incCmd, String decCmd) {
        LinearLayout column = new LinearLayout(host.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(host.getContext());
        label.setText(title);
        label.setTextColor(COLOR_SECTION);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(host.dpToPx(2), 0, host.dpToPx(2), host.dpToPx(6));
        column.addView(label);
        column.addView(createStepperCard(valueText, incCmd, decCmd));
        return column;
    }

    private View createStepperCard(String valueText, String incCmd, String decCmd) {
        LinearLayout card = createCardRow();
        card.setMinimumHeight(host.dpToPx(56));
        TextView value = new TextView(host.getContext());
        applyStepperPreviewText(value, valueText);
        value.setGravity(Gravity.CENTER);
        value.setTextColor(COLOR_TITLE);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setBackgroundResource(R.drawable.lolib_bg_stepper_value);
        int valuePadH = host.dpToPx(10);
        int valuePadV = host.dpToPx(8);
        value.setPadding(valuePadH, valuePadV, valuePadH, valuePadV);

        LinearLayout buttons = new LinearLayout(host.getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        ImageView minus = createStepperIcon(R.drawable.lolib_ic_calc_stepper_minus);
        ImageView plus = createStepperIcon(R.drawable.lolib_ic_calc_stepper_plus);
        minus.setOnClickListener(v -> host.executeUnoCommand(decCmd));
        plus.setOnClickListener(v -> host.executeUnoCommand(incCmd));
        buttons.addView(minus);
        buttons.addView(plus);

        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueLp.setMarginEnd(host.dpToPx(8));
        card.addView(value, valueLp);
        card.addView(buttons);
        return card;
    }

    private void applyStepperPreviewText(TextView value, String valueText) {
        if ("0.0 1".equals(valueText)) {
            SpannableString spannable = new SpannableString(valueText);
            spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#3B8040")),
                    2, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setText(spannable);
            return;
        }
        value.setText(valueText);
    }

    private ImageView createStepperIcon(int iconRes) {
        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(host.dpToPx(6), host.dpToPx(6), host.dpToPx(6), host.dpToPx(6));
        icon.setMinimumWidth(host.dpToPx(32));
        return icon;
    }

    private View createToggleRow(PanelItem item) {
        if ("grid_lines".equals(item.id)) {
            return createGridLinesToggleRow(item);
        }
        return wrapBottomMargin(createToggleCard(item));
    }

    /** 布局 Tab 显示网格线：无卡片底、图标 + 18sp 标签 + 滑块开关（与 ICON_VALUE_ROW 一致，末行无分隔线）。 */
    private View createGridLinesToggleRow(PanelItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(64));
        row.setPadding(host.dpToPx(12), 0, host.dpToPx(12), 0);

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(host.dpToPx(32), host.dpToPx(32));
            iconLp.setMarginEnd(host.dpToPx(12));
            row.addView(icon, iconLp);
        }
        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView indicator = new ImageView(host.getContext());
        boolean initial = toggleStates.getOrDefault(item.id, item.defaultOn);
        indicator.setImageResource(initial
                ? R.drawable.lolib_ic_calc_switch_on
                : R.drawable.lolib_ic_calc_switch_off);
        row.addView(indicator, new LinearLayout.LayoutParams(host.dpToPx(41), host.dpToPx(21)));

        row.setOnClickListener(v -> {
            boolean next = !toggleStates.getOrDefault(item.id, item.defaultOn);
            toggleStates.put(item.id, next);
            indicator.setImageResource(next
                    ? R.drawable.lolib_ic_calc_switch_on
                    : R.drawable.lolib_ic_calc_switch_off);
            onToggle(item, next);
        });
        return row;
    }

    /** 其余开关（常用 Tab 等）用圆形多选框，带卡片底。 */
    private View createToggleCard(PanelItem item) {
        LinearLayout row = createCardRow();
        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView indicator = new ImageView(host.getContext());
        boolean initial = toggleStates.getOrDefault(item.id, item.defaultOn);
        indicator.setImageResource(initial
                ? R.drawable.lolib_ic_calc_toggle_checked
                : R.drawable.lolib_ic_calc_toggle_unchecked);
        row.addView(indicator, new LinearLayout.LayoutParams(host.dpToPx(20), host.dpToPx(20)));

        row.setOnClickListener(v -> {
            boolean next = !toggleStates.getOrDefault(item.id, item.defaultOn);
            toggleStates.put(item.id, next);
            indicator.setImageResource(next
                    ? R.drawable.lolib_ic_calc_toggle_checked
                    : R.drawable.lolib_ic_calc_toggle_unchecked);
            onToggle(item, next);
        });
        return row;
    }

    private View createTogglePairRow(PanelItem item) {
        if ("stack_wrap_toggles".equals(item.id)) {
            return createStackWrapTogglePairRow();
        }
        return createDecimalTogglePairRow();
    }

    private View createStackWrapTogglePairRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        PanelItem verticalStack = new PanelItem(ItemType.TOGGLE, "vertical_stack", "纵向排列",
                ".uno:StackCharacterLeftToRight", false);
        PanelItem wrapText = new PanelItem(ItemType.TOGGLE, "wrap_text", "文本换行",
                ".uno:WrapText", false);
        row.addView(createToggleCard(verticalStack), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(host.dpToPx(10));
        View right = createToggleCard(wrapText);
        right.setLayoutParams(rightLp);
        row.addView(right);
        return wrapBottomMargin(row);
    }

    private View createDecimalTogglePairRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        PanelItem negativeRed = new PanelItem(ItemType.TOGGLE, "negative_red", "负值显示为红色",
                ".uno:NegativeNumberRed", true);
        PanelItem thousandsSep = new PanelItem(ItemType.TOGGLE, "thousands_sep", "千位分隔符",
                ".uno:NumberFormatThousands", false);
        row.addView(createToggleCard(negativeRed), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(host.dpToPx(10));
        View right = createToggleCard(thousandsSep);
        right.setLayoutParams(rightLp);
        row.addView(right);
        return wrapBottomMargin(row);
    }

    private View createColorPickerPairRow(PanelItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createColorPickerHalf("bg_color", "背景颜色", R.drawable.lolib_ic_calc_color_bg_preview),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(host.dpToPx(10));
        View right = createColorPickerHalf("border_color", "边框颜色",
                R.drawable.lolib_ic_calc_color_border_preview);
        right.setLayoutParams(rightLp);
        row.addView(right);
        return wrapBottomMargin(row);
    }

    private View createColorPickerHalf(String id, String title, int fallbackIconRes) {
        LinearLayout card = createCardRow();
        ImageView colorDot = new ImageView(host.getContext());
        colorDot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if ("bg_color".equals(id)) {
            bgColorPreviewDot = colorDot;
        } else if ("border_color".equals(id)) {
            borderColorPreviewDot = colorDot;
        }
        updateColorPreviewDot(colorDot, pickerColorRgb.get(id), fallbackIconRes);
        TextView label = new TextView(host.getContext());
        label.setText(title);
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        card.addView(createColorSwatchView(0, colorDot));
        card.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(createChevron());
        card.setOnClickListener(v -> {
            ColorPickerKind kind = "bg_color".equals(id) ? ColorPickerKind.BACKGROUND : ColorPickerKind.BORDER;
            showColorPickerPage(kind, id, colorDot, fallbackIconRes, title);
        });
        return card;
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

            TextView label = new TextView(host.getContext());
            label.setText(item.label);
            label.setTextColor(COLOR_TITLE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(host.dpToPx(12));
            row.addView(label, labelLp);
        } else {
            row.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
            row.setPadding(host.dpToPx(14), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
            TextView label = new TextView(host.getContext());
            label.setText(item.label);
            label.setTextColor(COLOR_TITLE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, labelLp);
        }

        if (item.hasSubmenu()) {
            ImageView chevron = new ImageView(host.getContext());
            chevron.setImageResource(R.drawable.lolib_ic_calc_row_chevron);
            chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(chevron, new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16)));
            row.setOnClickListener(v -> showSubmenuPage(item));
        } else {
            row.setOnClickListener(v -> runItemAction(item));
        }
        return row;
    }

    private View createSubmenuActionRow(PanelItem item) {
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

    private void setTabChromeVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (tabHeader != null) {
            tabHeader.setVisibility(visibility);
        }
        if (tabIndicatorArea != null) {
            tabIndicatorArea.setVisibility(visibility);
        }
    }

    private void showSubmenuPage(PanelItem parent) {
        if (parent == null || !parent.hasSubmenu()) {
            return;
        }
        dismissFontPicker();
        submenuReturnTabIndex = selectedTabIndex;
        submenuVisible = true;
        setTabChromeVisible(false);

        contentContainer.removeAllViews();
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(host.dpToPx(48));
        header.setPadding(host.dpToPx(4), 0, host.dpToPx(8), 0);

        ImageButton back = new ImageButton(host.getContext());
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            back.setBackgroundResource(rippleAttr.resourceId);
        }
        back.setImageResource(R.drawable.lolib_ic_top_back);
        back.setContentDescription("返回");
        back.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        back.setOnClickListener(v -> dismissSubmenuPage());
        header.addView(back, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView title = new TextView(host.getContext());
        title.setText(parent.label);
        title.setTextColor(COLOR_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, Typeface.NORMAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(title, titleLp);
        root.addView(header);

        View headerDivider = new View(host.getContext());
        headerDivider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        root.addView(headerDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));

        PanelItem[] items = parent.submenuItems;
        for (int i = 0; i < items.length; i++) {
            root.addView(createSubmenuActionRow(items[i]));
            if (i < items.length - 1) {
                root.addView(createDivider());
            }
        }

        contentContainer.addView(root);
        Log.i(TAG, "data_submenu_show parent=" + parent.id + " items=" + items.length);
    }

    private void dismissSubmenuPage() {
        if (!submenuVisible) {
            return;
        }
        submenuVisible = false;
        setTabChromeVisible(true);
        if (dialog != null && dialog.isShowing()
                && selectedTabIndex >= 0 && selectedTabIndex < tabs.size()) {
            renderTabContent(tabs.get(selectedTabIndex));
        }
    }

    /** 纸张方向 / 打印区域二级页：返回头 + 可选项（选中 #1278D9 + 右侧对勾）。Figma 3094:61223。 */
    private void showOptionPickerPage(String title, String[] labels, String[] commands,
            String pickerId, TextView valueView) {
        dismissFontPicker();
        dismissSubmenuPage();
        optionPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        contentContainer.removeAllViews();
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(host.dpToPx(48));
        header.setPadding(host.dpToPx(4), 0, host.dpToPx(8), 0);

        ImageButton back = new ImageButton(host.getContext());
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            back.setBackgroundResource(rippleAttr.resourceId);
        }
        back.setImageResource(R.drawable.lolib_ic_top_back);
        back.setContentDescription("返回");
        back.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        back.setOnClickListener(v -> dismissOptionPickerPage());
        header.addView(back, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView titleView = new TextView(host.getContext());
        titleView.setText(title);
        titleView.setTextColor(COLOR_TITLE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(titleView, titleLp);
        root.addView(header);

        View headerDivider = new View(host.getContext());
        headerDivider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        root.addView(headerDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));

        String selectedLabel = pickerValues.getOrDefault(pickerId,
                labels.length > 0 ? labels[0] : "");
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final int index = i;
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(host.dpToPx(48));
            row.setPadding(host.dpToPx(16), host.dpToPx(6), host.dpToPx(16), host.dpToPx(6));
            TypedValue rowRipple = new TypedValue();
            if (host.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, rowRipple, true)) {
                row.setBackgroundResource(rowRipple.resourceId);
            }

            TextView name = new TextView(host.getContext());
            name.setText(label);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            boolean selected = label.equals(selectedLabel);
            name.setTextColor(selected
                    ? Color.parseColor("#1278D9") : Color.parseColor("#333333"));
            row.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView check = new ImageView(host.getContext());
            check.setImageResource(R.drawable.lolib_ic_font_picker_check);
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
            row.addView(check, new LinearLayout.LayoutParams(
                    host.dpToPx(24), host.dpToPx(24)));

            row.setOnClickListener(v -> {
                pickerValues.put(pickerId, label);
                if (valueView != null) {
                    valueView.setText(label);
                }
                if (index < commands.length && commands[index] != null
                        && !commands[index].isEmpty()) {
                    host.executeUnoCommand(commands[index]);
                }
                dismissOptionPickerPage();
            });
            root.addView(row);

            if (i < labels.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(Color.parseColor("#00000014"));
                root.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }

        contentContainer.addView(root);
        Log.i(TAG, "option_picker_show title=" + title + " items=" + labels.length);
    }

    private void dismissOptionPickerPage() {
        if (!optionPickerVisible) {
            return;
        }
        optionPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    // === Comment picker — reuses ImpressCommentPickerController（native 输入框 → UNO 插入） ===

    private void showCommentPickerPage() {
        dismissFontPicker();
        commentPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        if (commentPicker == null) {
            commentPicker = new ImpressCommentPickerController(new ImpressCommentPickerController.Host() {
                @Override
                public android.content.Context getContext() {
                    return host.getContext();
                }

                @Override
                public int dpToPx(int dp) {
                    return host.dpToPx(dp);
                }

                @Override
                public String getCommentAuthorName() {
                    return host.getCommentAuthorName();
                }

                @Override
                public String getCommentAuthorAvatarUri() {
                    return host.getCommentAuthorAvatarUri();
                }

                @Override
                public void toastTodo(String text) {
                    host.toastTodo(text);
                }

                @Override
                public void insertCommentWithText(String text) {
                    dismiss();
                    host.runAfterFunctionPanelDismiss(() -> host.insertCommentWithText(text));
                }

                @Override
                public void onBack() {
                    dismissCommentPickerPage();
                }
            });
        }

        contentContainer.removeAllViews();
        contentContainer.addView(commentPicker.buildRootView());
        Log.i(TAG, "comment_picker_show");
    }

    private void dismissCommentPickerPage() {
        if (!commentPickerVisible) {
            return;
        }
        commentPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showChartTypePickerPage() {
        dismissFontPicker();
        chartPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        contentContainer.removeAllViews();
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(createChartPickerHeader());

        root.addView(ChartTypePickerUi.buildPickerBody(host.getContext(), host::dpToPx,
                (unoType, label) -> {
                    Log.i(TAG, "chart_type_selected type=" + unoType + " label=" + label);
                    dismiss();
                    host.runAfterFunctionPanelDismiss(() -> host.insertChartWithType(unoType));
                }));

        contentContainer.addView(root);
        Log.i(TAG, "chart_type_picker_show");
    }

    private View createChartPickerHeader() {
        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(host.dpToPx(48));
        header.setPadding(host.dpToPx(4), 0, host.dpToPx(8), 0);

        ImageButton back = new ImageButton(host.getContext());
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            back.setBackgroundResource(rippleAttr.resourceId);
        }
        back.setImageResource(R.drawable.lolib_ic_top_back);
        back.setContentDescription("返回");
        back.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        back.setOnClickListener(v -> dismissChartPickerPage());
        header.addView(back, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView title = new TextView(host.getContext());
        title.setText("图表");
        title.setTextColor(COLOR_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(title, titleLp);
        return header;
    }

    private void dismissChartPickerPage() {
        if (!chartPickerVisible) {
            return;
        }
        chartPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void dismissHyperlinkPickerPage() {
        if (!hyperlinkPickerVisible) {
            return;
        }
        hyperlinkPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showHyperlinkPickerPage() {
        dismissFontPicker();
        hyperlinkPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        if (hyperlinkPicker == null) {
            hyperlinkPicker = new CalcHyperlinkPickerController(new CalcHyperlinkPickerController.Host() {
                @Override
                public android.content.Context getContext() {
                    return host.getContext();
                }

                @Override
                public int dpToPx(int dp) {
                    return host.dpToPx(dp);
                }

                @Override
                public void toastTodo(String text) {
                    host.toastTodo(text);
                }

                @Override
                public void insertHyperlink(String displayText, String url) {
                    dismiss();
                    host.runAfterFunctionPanelDismiss(
                            () -> host.insertHyperlink(displayText, url));
                }

                @Override
                public void fetchCalcHyperlinkContext(
                        CalcHyperlinkPickerController.HyperlinkContextCallback callback) {
                    host.fetchCalcHyperlinkContext(callback);
                }

                @Override
                public void onBack() {
                    dismissHyperlinkPickerPage();
                }
            });
        }

        contentContainer.removeAllViews();
        contentContainer.addView(hyperlinkPicker.buildRootView());
        hyperlinkPicker.onPickerShown();
        Log.i(TAG, "hyperlink_picker_show");
    }

    private void dismissDataValidationPage() {
        if (!dataValidationVisible) {
            return;
        }
        dataValidationVisible = false;
        host.dismissCoValidationDialog();
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showDataValidationPage() {
        dismissFontPicker();
        dataValidationVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        if (dataValidationPicker == null) {
            dataValidationPicker = new CalcDataValidationController(new CalcDataValidationController.Host() {
                @Override
                public android.content.Context getContext() {
                    return host.getContext();
                }

                @Override
                public int dpToPx(int dp) {
                    return host.dpToPx(dp);
                }

                @Override
                public void onBack() {
                    dismissDataValidationPage();
                }

                @Override
                public void applyValidation(CalcDataValidationState state) {
                    dismiss();
                    host.runAfterFunctionPanelDismiss(
                            () -> host.applyCalcDataValidation(state));
                }

                @Override
                public void openMacroChooser(CalcValidationMacroPickerController.MacroChooseCallback callback) {
                    host.openMacroChooser(callback);
                }

                @Override
                public void loadCurrentValidationState(CalcDataValidationState target, Runnable onLoaded) {
                    host.loadCurrentValidationState(target, onLoaded);
                }

                @Override
                public void dismissCoValidationDialog() {
                    host.dismissCoValidationDialog();
                }

                @Override
                public void loadMacroCatalog(CalcValidationMacroCatalog.Callback callback) {
                    host.loadMacroCatalog(callback);
                }
            });
        }

        contentContainer.removeAllViews();
        contentContainer.addView(dataValidationPicker.buildRootView());
        Log.i(TAG, "data_validation_picker_show");
    }

    private void dismissColorPickerPage() {
        if (!colorPickerVisible) {
            return;
        }
        colorPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showColorPickerPage(ColorPickerKind kind, String pickerId, ImageView previewDot,
            int fallbackIconRes, String title) {
        dismissFontPicker();
        colorPickerVisible = true;
        activeColorPickerKind = kind;
        activeColorPickerId = pickerId;
        activeColorPreviewDot = previewDot;
        activeColorPreviewFallback = fallbackIconRes;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        colorPicker = new CalcFontColorPickerController(buildColorPickerHost());

        contentContainer.removeAllViews();
        contentContainer.addView(colorPicker.buildRootView(title));
        Log.i(TAG, "color_picker_show kind=" + kind.name() + " id=" + pickerId);
    }

    private CalcFontColorPickerController.Host buildColorPickerHost() {
        return new CalcFontColorPickerController.Host() {
            @Override
            public android.content.Context getContext() {
                return host.getContext();
            }

            @Override
            public int dpToPx(int dp) {
                return host.dpToPx(dp);
            }

            @Override
            public Integer getSelectedIndex() {
                return pickerColorIndex.get(activeColorPickerId);
            }

            @Override
            public void onColorSelected(int index, int rgb) {
                applyCalcColorSelection(activeColorPickerKind, rgb);
                pickerColorRgb.put(activeColorPickerId, rgb);
                pickerColorIndex.put(activeColorPickerId, index);
                updateColorPreviewDot(activeColorPreviewDot, rgb, activeColorPreviewFallback);
                Log.i(TAG, "calc_color_picked kind=" + activeColorPickerKind.name()
                        + " id=" + activeColorPickerId
                        + " index=" + index
                        + " rgb=#" + Integer.toHexString(rgb).toUpperCase());
            }

            @Override
            public void onBack() {
                dismissColorPickerPage();
            }
        };
    }

    private View createSingleGridCell(PanelItem item) {
        return createLabeledGrid(
                new String[] { item.label },
                new String[] { item.unoCommand },
                item.gridIconRes,
                1,
                null,
                GridMode.FORMAT_WITH_LABEL);
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

    private void onPickerClick(PanelItem item, TextView valueView) {
        if ("font_name".equals(item.id)) {
            showFontPicker(valueView);
            return;
        }
        if ("bg_color".equals(item.id)) {
            showColorPickerPage(ColorPickerKind.BACKGROUND, "bg_color", bgColorPreviewDot,
                    R.drawable.lolib_ic_calc_color_bg_preview, "背景颜色");
            return;
        }
        if ("border_color".equals(item.id)) {
            showColorPickerPage(ColorPickerKind.BORDER, "border_color", borderColorPreviewDot,
                    R.drawable.lolib_ic_calc_color_border_preview, "边框颜色");
            return;
        }
        if ("font_color".equals(item.id)) {
            showColorPickerPage(ColorPickerKind.FONT, "font_color", fontColorPreviewDot,
                    R.drawable.lolib_ic_calc_color_font_preview, "字体颜色");
            return;
        }
        if ("paper_orientation".equals(item.id)) {
            showOptionPickerPage("纸张方向", ORIENTATION_LABELS, ORIENTATION_COMMANDS,
                    "paper_orientation", valueView);
            return;
        }
        if ("print_area".equals(item.id)) {
            showOptionPickerPage("打印区域", PRINT_AREA_LABELS, PRINT_AREA_COMMANDS,
                    "print_area", valueView);
            return;
        }
        host.toastTodo(item.label + " 后续接入");
    }

    private void applyCalcColorSelection(ColorPickerKind kind, int rgb) {
        if (kind == ColorPickerKind.BORDER) {
            String command = buildBorderColorUnoCommand(rgb);
            if (!command.isEmpty()) {
                host.executeUnoCommand(command);
            }
            return;
        }
        host.executeUnoCommand(buildColorUnoCommand(kind.unoCommand, kind.propertyName, rgb));
    }

    private ImageView createColorSwatchView(int fallbackIconRes, ImageView existingDot) {
        ImageView dot = existingDot != null ? existingDot : new ImageView(host.getContext());
        dot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (existingDot == null && fallbackIconRes != 0) {
            dot.setImageResource(fallbackIconRes);
        }
        int size = host.dpToPx(20);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
        dotLp.setMarginEnd(host.dpToPx(8));
        dot.setLayoutParams(dotLp);
        return dot;
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
        int size = host.dpToPx(20);
        GradientDrawable drawable = createCircleSwatchDrawable(rgb);
        dot.setBackground(drawable);
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

    private String buildColorUnoCommand(String unoCommand, String propertyName, int rgb) {
        return unoCommand + " {\"" + propertyName + "\":{\"type\":\"long\",\"value\":" + rgb + "}}";
    }

    private String buildBorderColorUnoCommand(int rgb) {
        try {
            JSONArray outer = new JSONArray();
            for (int i = 0; i < 4; i++) {
                outer.put(makeBorderLineJson(rgb, 1));
            }
            outer.put(typedLong(0));
            outer.put(typedLong(0));
            outer.put(typedLong(0));
            outer.put(typedLong(0));
            outer.put(typedLong(0));

            JSONArray inner = new JSONArray();
            inner.put(makeBorderLineJson(rgb, 0));
            inner.put(makeBorderLineJson(rgb, 0));
            inner.put(typedShort(0));
            inner.put(typedShort(127));
            inner.put(typedLong(0));

            JSONObject root = new JSONObject();
            root.put("OuterBorder", typedArray(outer));
            root.put("InnerBorder", typedArray(inner));
            return ".uno:SetBorderStyle " + root.toString();
        } catch (JSONException e) {
            Log.w(TAG, "build_border_color_failed", e);
            return "";
        }
    }

    private JSONObject makeBorderLineJson(int rgb, int outerWidth) throws JSONException {
        JSONObject color = new JSONObject();
        color.put("type", "com.sun.star.util.Color");
        color.put("value", rgb);

        JSONObject value = new JSONObject();
        value.put("Color", color);
        value.put("InnerLineWidth", typedShort(0));
        value.put("OuterLineWidth", typedShort(outerWidth));
        value.put("LineDistance", typedShort(0));
        value.put("LineStyle", typedShort(0));
        value.put("LineWidth", typedULong(0));

        JSONObject line = new JSONObject();
        line.put("type", "com.sun.star.table.BorderLine2");
        line.put("value", value);
        return line;
    }

    private JSONObject typedShort(int value) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "short");
        o.put("value", value);
        return o;
    }

    private JSONObject typedLong(int value) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "long");
        o.put("value", value);
        return o;
    }

    private JSONObject typedULong(int value) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "unsigned long");
        o.put("value", value);
        return o;
    }

    private JSONObject typedArray(JSONArray value) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "[]any");
        o.put("value", value);
        return o;
    }

    // === 字号浮层：与 Writer 文档面板同款（PopupWindow 圆角卡片，锚定在字号框下方） ===

    private void showFontSizePopup(TextView valueView, View anchor) {
        dismissFontSizePopup();
        LinearLayout rows = new LinearLayout(host.getContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(host.dpToPx(8), host.dpToPx(8), host.dpToPx(8), host.dpToPx(8));

        String selectedLabel = pickerValues.getOrDefault("font_size", "");
        for (int i = 0; i < SIZE_OPTIONS.length; i++) {
            final String label = SIZE_OPTIONS[i];
            final String value = SIZE_VALUES[i];
            rows.addView(createFontSizeRow(label, value, selectedLabel, valueView));
            if (i < SIZE_OPTIONS.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x1F000000);
                rows.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }

        NestedScrollView scroll = new NestedScrollView(host.getContext());
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackgroundResource(R.drawable.lolib_bg_font_size_popup);
        scroll.setClipToOutline(true);
        scroll.addView(rows);

        int width = host.dpToPx(160);
        int height = host.dpToPx(230);
        fontSizePopup = new PopupWindow(scroll, width, height, true);
        fontSizePopup.setElevation(host.dpToPx(16));
        fontSizePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        fontSizePopup.setOutsideTouchable(true);
        fontSizePopup.showAsDropDown(anchor, 0, -host.dpToPx(4));
    }

    private LinearLayout createFontSizeRow(String label, String value, String selectedLabel,
            TextView valueView) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.dpToPx(12), host.dpToPx(10), host.dpToPx(12), host.dpToPx(10));
        row.setMinimumHeight(host.dpToPx(40));
        boolean selected = label.equals(selectedLabel) || value.equals(selectedLabel);

        TextView text = new TextView(host.getContext());
        text.setText(label);
        text.setTextSize(14);
        text.setTextColor(selected ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView check = new ImageView(host.getContext());
        check.setImageResource(selected
                ? R.drawable.lolib_ic_option_circle_checked
                : R.drawable.lolib_ic_option_circle_unchecked);
        int checkSize = host.dpToPx(16);
        check.setLayoutParams(new LinearLayout.LayoutParams(checkSize, checkSize));
        row.addView(check);

        row.setOnClickListener(v -> {
            pickerValues.put("font_size", label);
            valueView.setText(label);
            dismissFontSizePopup();
            host.applyFontSize(value);
        });
        return row;
    }

    private void dismissFontSizePopup() {
        if (fontSizePopup != null) {
            fontSizePopup.dismiss();
            fontSizePopup = null;
        }
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
        if (tabHeader != null) tabHeader.setVisibility(View.GONE);
        if (tabIndicatorArea != null) tabIndicatorArea.setVisibility(View.GONE);
        if (contentContainer != null) contentContainer.setVisibility(View.GONE);
        fontPickerPanel.setVisibility(View.VISIBLE);
        fontPickerVisible = true;
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
            if (tf != null) name.setTypeface(tf);
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
        if (tabHeader != null) tabHeader.setVisibility(View.VISIBLE);
        if (tabIndicatorArea != null) tabIndicatorArea.setVisibility(View.VISIBLE);
        if (contentContainer != null) contentContainer.setVisibility(View.VISIBLE);
        if (fontPickerPanel != null) fontPickerPanel.setVisibility(View.GONE);
    }

    private void syncCurrentFormatting() {
        host.fetchCurrentFormatting((styleName, fontName, fontSizePt, paragraphAlignment,
                                    bold, italic, underline, strikethrough) -> {
            if (fontName != null && !fontName.trim().isEmpty()) {
                pickerValues.put("font_name", fontName.trim());
            }
            String sizeLabel = displayFontSize(fontSizePt);
            if (!TextUtils.isEmpty(sizeLabel)) {
                pickerValues.put("font_size", sizeLabel);
            }
            currentBold = bold;
            currentItalic = italic;
            currentUnderline = underline;
            currentStrikethrough = strikethrough;
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

    private void onGridCommand(String command, String label) {
        if (command == null || command.isEmpty()) {
            host.toastTodo("「" + label + "」后续接入");
            return;
        }
        host.executeUnoCommand(command);
    }

    private void onToggle(PanelItem item, boolean enabled) {
        if (item.unoCommand == null || item.unoCommand.isEmpty()) {
            host.toastTodo("「" + item.label + "」后续接入");
            return;
        }
        host.executeUnoCommand(item.unoCommand);
    }

    private void runItemAction(PanelItem item) {
        if ("insert_shape".equals(item.id)) {
            showShapePickerDialog();
            return;
        }
        if ("insert_chart".equals(item.id)) {
            showChartTypePickerPage();
            return;
        }
        if ("insert_hyperlink".equals(item.id)) {
            showHyperlinkPickerPage();
            return;
        }
        if ("data_validation".equals(item.id)) {
            showDataValidationPage();
            return;
        }
        if ("insert_comment".equals(item.id) || "review_comment".equals(item.id)) {
            showCommentPickerPage();
            return;
        }
        Runnable action = () -> {
            if (item.hostAction != null) {
                item.hostAction.run();
            } else if (item.unoCommand != null && !item.unoCommand.isEmpty()) {
                host.executeUnoCommand(item.unoCommand);
            }
        };
        dismiss();
        if (FunctionPanelSpellCheckHelper.needsDeferredUnoAfterPanelDismiss(item.unoCommand)) {
            host.runAfterFunctionPanelDismiss(action);
        } else {
            action.run();
        }
    }

    private void showShapePickerDialog() {
        new AlertDialog.Builder(host.getContext())
                .setTitle("插入形状")
                .setItems(SHAPE_LABELS, (dialog, which) -> {
                    if (which >= 0 && which < SHAPE_COMMANDS.length) {
                        dismiss();
                        host.executeUnoCommand(SHAPE_COMMANDS[which]);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private List<PanelTab> buildTabs() {
        List<PanelTab> result = new ArrayList<>();

        List<PanelItem> common = new ArrayList<>();
        common.add(new PanelItem(ItemType.SECTION, "sec_char", "字符"));
        common.add(new PanelItem(ItemType.PICKER_ROW, "font_name", "字体", "宋体"));
        common.add(new PanelItem(ItemType.PICKER_PAIR, "font_size_color", "字号颜色"));
        common.add(new PanelItem(ItemType.TOOL_BUTTONS, "char_tools", "字符样式"));
        common.add(new PanelItem(ItemType.SECTION, "sec_numfmt", "数值格式"));
        common.add(new PanelItem(ItemType.FORMAT_GRID, "numfmt_grid", "数值格式",
                NUMFMT_LABELS, NUMFMT_COMMANDS, NUMFMT_ICONS, 5));
        common.add(new PanelItem(ItemType.STEPPER_PAIR, "decimal_steppers", "小数"));
        common.add(new PanelItem(ItemType.TOGGLE_PAIR, "decimal_toggles", "数值开关"));
        common.add(new PanelItem(ItemType.SECTION, "sec_align", "对齐"));
        common.add(new PanelItem(ItemType.ICON_GRID, "align_grid", "对齐",
                ALIGN_LABELS, ALIGN_COMMANDS, ALIGN_ICONS, 6, new int[] { 6, 5 }, true));
        common.add(new PanelItem(ItemType.STEPPER_PAIR, "indent_steppers", "缩进"));
        common.add(new PanelItem(ItemType.TOGGLE_PAIR, "stack_wrap_toggles", "排列换行"));
        common.add(new PanelItem(ItemType.TOGGLE, "merge_cells", "合并单元格",
                ".uno:ToggleMergeCells", false));
        common.add(new PanelItem(ItemType.SECTION, "sec_border", "边框"));
        common.add(new PanelItem(ItemType.ICON_GRID, "border_styles", "边框样式",
                BORDER_LABELS, BORDER_COMMANDS, BORDER_ICONS, 6, new int[] { 6, 6 }, true));
        common.add(new PanelItem(ItemType.COLOR_PICKER_PAIR, "color_pickers", "颜色"));
        common.add(new PanelItem(ItemType.SECTION, "sec_sheet", "工作表"));
        common.add(new PanelItem(ItemType.ICON_GRID, "sheet_ops", "工作表",
                SHEET_LABELS, SHEET_COMMANDS, SHEET_ICONS, 7, null, true));
        result.add(new PanelTab("common", "常用", common));

        List<PanelItem> file = new ArrayList<>();
        file.add(new PanelItem(ItemType.ACTION, "save", "保存",
                R.drawable.lolib_ic_calc_file_save, host::saveDocument));
        file.add(new PanelItem(ItemType.ACTION, "save_as", "另存为",
                R.drawable.lolib_ic_calc_file_save_as, host::saveDocumentAs));
        file.add(new PanelItem(ItemType.ACTION, "export", "导出为",
                R.drawable.lolib_ic_calc_file_export, host::exportDocumentAsPdf));
        file.add(new PanelItem(ItemType.ACTION, "print", "打印",
                R.drawable.lolib_ic_calc_file_print, host::initiatePrint));
        result.add(new PanelTab("file", "文件", file));

        List<PanelItem> insert = new ArrayList<>();
        insert.add(new PanelItem(ItemType.ACTION, "insert_local_image", "本地图像",
                R.drawable.lolib_ic_calc_insert_local_image, (Runnable) host::openLocalImagePickerFromWeb));
        insert.add(new PanelItem(ItemType.ACTION, "insert_chart", "图表",
                R.drawable.lolib_ic_calc_insert_chart, ""));
        insert.add(new PanelItem(ItemType.ACTION, "insert_comment", "批注",
                R.drawable.lolib_ic_calc_insert_comment, ""));
        insert.add(new PanelItem(ItemType.ACTION, "insert_hyperlink", "超链接",
                R.drawable.lolib_ic_calc_insert_hyperlink, ""));
        insert.add(new PanelItem(ItemType.ACTION, "insert_shape", "形状",
                R.drawable.lolib_ic_calc_insert_shape, ""));
        insert.add(new PanelItem(ItemType.ACTION, "insert_date", "日期",
                R.drawable.lolib_ic_calc_insert_date, ".uno:InsertCurrentDate"));
        insert.add(new PanelItem(ItemType.ACTION, "insert_time", "时间",
                R.drawable.lolib_ic_calc_insert_time, ".uno:InsertCurrentTime"));
        Log.i(TAG, "buildTabs insert_items=" + insert.size() + " layout=list+icons");
        result.add(new PanelTab("insert", "插入", insert));

        List<PanelItem> layout = new ArrayList<>();
        layout.add(new PanelItem(ItemType.ICON_VALUE_ROW, "paper_orientation", "纸张方向", "纵向",
                R.drawable.lolib_ic_calc_paper_orientation));
        layout.add(new PanelItem(ItemType.ICON_VALUE_ROW, "print_area", "打印区域", "A4",
                R.drawable.lolib_ic_calc_print_area));
        layout.add(new PanelItem(ItemType.TOGGLE, "grid_lines", "显示网格线", "", ".uno:ToggleSheetGrid",
                null, R.drawable.lolib_ic_calc_grid_lines, null, null, null, 0, null, false, true, null));
        result.add(new PanelTab("layout", "布局", layout));

        List<PanelItem> data = new ArrayList<>();
        PanelItem[] groupOutlineSubmenu = new PanelItem[] {
                new PanelItem(ItemType.ACTION, "group", "组合",
                        R.drawable.lolib_ic_calc_group, ".uno:Group"),
                new PanelItem(ItemType.ACTION, "ungroup", "取消组合",
                        R.drawable.lolib_ic_calc_ungroup, ".uno:Ungroup"),
                new PanelItem(ItemType.ACTION, "clear_outline", "移除大纲",
                        R.drawable.lolib_ic_calc_clear_outline, ".uno:ClearOutline"),
                new PanelItem(ItemType.ACTION, "hide_detail", "隐藏明细数据",
                        R.drawable.lolib_ic_calc_hide_detail, ".uno:HideDetail"),
                new PanelItem(ItemType.ACTION, "show_detail", "显示明细数据",
                        R.drawable.lolib_ic_calc_show_detail, ".uno:ShowDetail"),
        };
        data.add(new PanelItem(ItemType.ACTION, "data_validation", "数据有效性",
                R.drawable.lolib_ic_calc_data_validation, ".uno:Validation"));
        data.add(new PanelItem(ItemType.ACTION, "sort_asc", "升序",
                R.drawable.lolib_ic_calc_sort_asc, ".uno:SortAscending"));
        data.add(new PanelItem(ItemType.ACTION, "sort_desc", "降序",
                R.drawable.lolib_ic_calc_sort_desc, ".uno:SortDescending"));
        data.add(new PanelItem(ItemType.ACTION, "group_outline", "分组及分级显示",
                R.drawable.lolib_ic_calc_group_outline, groupOutlineSubmenu));
        Log.i(TAG, "buildTabs data_items=" + data.size() + " layout=list+icons");
        result.add(new PanelTab("data", "数据", data));

        List<PanelItem> review = new ArrayList<>();
        review.add(new PanelItem(ItemType.ACTION, "spell_check", "拼写检查",
                R.drawable.lolib_ic_calc_spell_check, ".uno:SpellDialog"));
        review.add(new PanelItem(ItemType.ACTION, "review_comment", "批注",
                R.drawable.lolib_ic_calc_review_comment, ""));
        Log.i(TAG, "buildTabs review_items=" + review.size() + " layout=list+icons");
        result.add(new PanelTab("review", "审阅", review));

        return result;
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

    private static final String[] NUMFMT_LABELS = {
            "常规", "数字", "百分比", "货币", "日期",
            "时间", "科学", "分数", "布尔值", "文本"
    };
    private static final int[] NUMFMT_ICONS = {
            R.drawable.lolib_ic_calc_num_general,
            R.drawable.lolib_ic_calc_num_number,
            R.drawable.lolib_ic_calc_num_percent,
            R.drawable.lolib_ic_calc_num_currency,
            R.drawable.lolib_ic_calc_num_date,
            R.drawable.lolib_ic_calc_num_time,
            R.drawable.lolib_ic_calc_num_scientific,
            R.drawable.lolib_ic_calc_num_fraction,
            R.drawable.lolib_ic_calc_num_boolean,
            R.drawable.lolib_ic_calc_num_text,
    };
    private static final String[] NUMFMT_COMMANDS = {
            ".uno:NumberFormatStandard",
            ".uno:NumberFormatDecimal",
            ".uno:NumberFormatPercent",
            ".uno:NumberFormatCurrency",
            ".uno:NumberFormatDate",
            ".uno:NumberFormatTime",
            ".uno:NumberFormatScientific",
            ".uno:FormatCellDialog",
            ".uno:FormatCellDialog",
            ".uno:NumberFormatText"
    };

    private static final String[] ALIGN_LABELS = {
            "左", "中", "右", "两端", "减缩进", "增缩进",
            "顶", "中", "底", "增行缩", "减行缩"
    };
    private static final int[] ALIGN_ICONS = {
            R.drawable.lolib_ic_calc_align_left,
            R.drawable.lolib_ic_calc_align_center_h,
            R.drawable.lolib_ic_calc_align_right,
            R.drawable.lolib_ic_calc_align_justify,
            R.drawable.lolib_ic_calc_indent_decrease,
            R.drawable.lolib_ic_calc_indent_increase,
            R.drawable.lolib_ic_calc_align_top,
            R.drawable.lolib_ic_calc_align_center_v,
            R.drawable.lolib_ic_calc_align_bottom,
            R.drawable.lolib_ic_calc_indent_increase_row,
            R.drawable.lolib_ic_calc_indent_decrease_row,
    };
    private static final String[] ALIGN_COMMANDS = {
            ".uno:AlignLeft",
            ".uno:AlignHorizontalCenter",
            ".uno:AlignRight",
            ".uno:AlignBlock",
            ".uno:DecrementIndent",
            ".uno:IncrementIndent",
            ".uno:AlignTop",
            ".uno:AlignVCenter",
            ".uno:AlignBottom",
            ".uno:IncrementIndent",
            ".uno:DecrementIndent"
    };

    private static final String[] BORDER_LABELS = {
            "全", "外", "框", "粗",
            "上", "下", "左", "右", "竖", "内横", "↘", "↙"
    };
    private static final int[] BORDER_ICONS = {
            R.drawable.lolib_ic_calc_border_all_dashed,
            R.drawable.lolib_ic_calc_border_all_solid,
            R.drawable.lolib_ic_calc_border_outer_solid_inner_dashed,
            R.drawable.lolib_ic_calc_border_outer_thick,
            R.drawable.lolib_ic_calc_border_top,
            R.drawable.lolib_ic_calc_border_bottom,
            R.drawable.lolib_ic_calc_border_left,
            R.drawable.lolib_ic_calc_border_right,
            R.drawable.lolib_ic_calc_border_inner_vertical,
            R.drawable.lolib_ic_calc_border_inner_horizontal,
            R.drawable.lolib_ic_calc_border_diag_tl_br,
            R.drawable.lolib_ic_calc_border_diag_tr_bl,
    };
    private static final String[] BORDER_COMMANDS = {
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
            ".uno:SetBorderStyle",
    };

    private static final String[] SHEET_LABELS = {
            "插入行", "插入列", "删除行", "删除列", "冻结行列", "冻结列", "冻结行"
    };
    private static final int[] SHEET_ICONS = {
            R.drawable.lolib_ic_calc_sheet_insert_row,
            R.drawable.lolib_ic_calc_sheet_insert_col,
            R.drawable.lolib_ic_calc_sheet_delete_row,
            R.drawable.lolib_ic_calc_sheet_delete_col,
            R.drawable.lolib_ic_calc_sheet_freeze_panes,
            R.drawable.lolib_ic_calc_sheet_freeze_col,
            R.drawable.lolib_ic_calc_sheet_freeze_row,
    };

    private static final String[] SHEET_COMMANDS = {
            ".uno:InsertRowsBefore",
            ".uno:InsertColumnsAfter",
            ".uno:DeleteRows",
            ".uno:DeleteColumns",
            ".uno:FreezePanes",
            ".uno:FreezePanesColumn",
            ".uno:FreezePanesRow"
    };

    private static final String[] FALLBACK_FONT_OPTIONS = {
            "宋体", "黑体", "Arial", "Liberation Sans", "Times New Roman"
    };
    private static final String[] FALLBACK_FONT_VALUES = FALLBACK_FONT_OPTIONS;

    private static final String[] SIZE_OPTIONS = {
            "初号", "小初", "一号", "小一", "二号", "小二", "三号", "小三", "四号", "小四", "五号", "小五"
    };
    private static final String[] SIZE_VALUES = {
            "42", "36", "26", "24", "22", "18", "16", "15", "14", "12", "10.5", "9"
    };

    private static final String[] ORIENTATION_LABELS = {
            "纵向", "横向"
    };
    private static final String[] ORIENTATION_COMMANDS = {
            ".uno:Orientation",
            ".uno:Orientation"
    };

    private static final String[] PRINT_AREA_LABELS = {
            "设置打印区域", "清除打印区域"
    };
    private static final String[] PRINT_AREA_COMMANDS = {
            ".uno:DefinePrintArea",
            ".uno:ResetAttributes"
    };

    private static final String[] SHAPE_LABELS = {
            "矩形", "椭圆", "圆角矩形", "等腰三角形", "直线", "箭头"
    };
    private static final String[] SHAPE_COMMANDS = {
            ".uno:BasicShapes.rectangle",
            ".uno:BasicShapes.ellipse",
            ".uno:BasicShapes.round-rectangle",
            ".uno:BasicShapes.isosceles-triangle",
            ".uno:BasicShapes.line",
            ".uno:BasicShapes.arrow"
    };
}
