package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;
import org.libreoffice.androidlib.calc.CalcFontColorPickerController;
import org.libreoffice.androidlib.impress.ImpressShapePickerController;
import org.libreoffice.androidlib.impress.ImpressSlideLayoutCatalog;
import org.libreoffice.androidlib.impress.ImpressSolidColorPickerController;
import org.libreoffice.androidlib.impress.ImpressSubpageHeader;
import org.libreoffice.androidlib.impress.ImpressTransitionCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final int COLOR_SLIDE_VALUE = Color.parseColor("#333333");
    private static final int COLOR_DIVIDER = Color.parseColor("#E3E3E3");
    private static final float SHEET_HEIGHT_RATIO = BottomSheetAnchorHelper.FUNCTION_PANEL_HEIGHT_RATIO;
    /** Figma 207:15020：101×100px → 50×50dp；第一行 weight 铺满，第二行固定左对齐。 */
    private static final int CHAR_CELL_W_DP = 50;
    private static final int CHAR_CELL_H_DP = 50;
    private static final int CHAR_CELL_PAD_DP = 12;
    private static final int CHAR_GRID_GAP_DP = 8;
    private static final int CHAR_ROW_GAP_DP = 12;
    private static final int ICON_SIZE_DP = 24;
    private static final int NUMFMT_CELL_H_DP = 80;
    private static final int NUMFMT_CELL_VPAD_DP = 12;
    private static final int GRID_GAP_DP = 5;
    /** Figma 207:15082：202×160px → 101×80dp，三列 weight 铺满。 */
    private static final int PARA_CELL_W_DP = 101;
    private static final int PARA_CELL_H_DP = 80;
    private static final int PARA_CELL_PAD_DP = 12;
    private static final int PARA_GRID_GAP_H_DP = 20;
    private static final int PARA_GRID_GAP_V_DP = 12;
    /** Figma 圆角 24px → 12dp。 */
    private static final int IMPRESS_GRID_RADIUS_DP = 12;
    private static final int IMPRESS_GRID_CELL_BG = Color.parseColor("#F2F3F5");
    private static final int IMPRESS_GRID_CELL_BG_SELECTED = Color.parseColor("#E5E6E8");
    private static final int COLOR_SWATCH_SIZE_DP = 40;
    private static final int COLOR_SWATCH_GAP_DP = 10;
    private static final int COLOR_SWATCH_COLS = 6;
    private static final int TRANSITION_GRID_COLS = 6;
    private static final int TRANSITION_ICON_SIZE_DP = 40;
    private static final int TRANSITION_CELL_MIN_H_DP = 72;
    private static final int TRANSITION_CELL_VPAD_DP = 6;
    private static final int DEFAULT_SELECTED_TRANSITION_INDEX = 8;
    private static final int INSERT_GRID_COLS = 3;
    private static final int INSERT_GRID_GAP_DP = 12;
    private static final int INSERT_ICON_SIZE_DP = 32;
    private static final int INSERT_CELL_MIN_H_DP = 88;
    private static final int LAYOUT_GRID_COLS = 3;
    private static final int LAYOUT_THUMB_W_DP = 100;
    private static final int LAYOUT_THUMB_H_DP = 56;
    private static final int LAYOUT_CELL_H_DP = 89;
    private static final int LAYOUT_CELL_PAD_DP = 4;
    private static final int LAYOUT_ROW_GAP_DP = 20;
    private static final int LAYOUT_HEADER_GAP_DP = 12;
    private static final int LAYOUT_LABEL_SP = 12;
    private static final int LAYOUT_ALL_BTN_TEXT_SP = 14;
    private static final int COMMON_LAYOUT_PREVIEW_COUNT = 3;
    private static final int FILE_ACTION_ICON_DP = 32;
    private static final int FILE_ACTION_ROW_H_DP = 64;
    private static final int FILE_ACTION_TEXT_SP = 18;
    private static final int FILE_ACTION_ICON_TEXT_GAP_DP = 12;
    private static final int FILE_ACTION_ROW_HPAD_DP = 16;
    private static final int FILE_ACTION_ROW_VPAD_DP = 12;
    private static final int FILE_ACTION_DIVIDER_COLOR = 0x14000000;

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

    /** 与 BottomToolbarController.HIGHLIGHT_COLOR_OPTIONS 保持一致。 */
    private static final ColorSwatch[] HIGHLIGHT_COLOR_SWATCHES = new ColorSwatch[] {
            new ColorSwatch("黄色", 0xFFFF00),
            new ColorSwatch("浅绿", 0xC6EFCE),
            new ColorSwatch("浅蓝", 0xBDD7EE),
            new ColorSwatch("浅红", 0xFFC7CE),
            new ColorSwatch("橙色", 0xF4B183),
            new ColorSwatch("紫色", 0xD9E1F2),
            new ColorSwatch("灰色", 0xD9D9D9),
            new ColorSwatch("白色", 0xFFFFFF),
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

        /** 插入指定类型的图表（与 Calc 功能面板图表类型页一致）。 */
        void insertChartWithType(String unoChartType);

        /** 插入超链接（与 Calc 功能面板超链接页一致）。 */
        void insertHyperlink(String displayText, String url);

        /** 预读幻灯片列表，供超链接文档 Tab 默认值。 */
        void fetchImpressHyperlinkContext(
                ImpressHyperlinkPickerController.HyperlinkContextCallback callback);

        void saveDocument();

        void saveDocumentAs();

        void exportDocumentAsPdf();

        void initiatePrint();

        String getCommentAuthorName();

        void insertCommentWithText(String text);

        void fetchReviewComments(ImpressReviewCommentController.ReviewCommentsCallback callback);

        /** Bottom toolbar + nav spacer height; sheets anchor above this chrome. */
        int getBottomChromeHeightPx();

        void editCommentWithText(String id, String author, String text);

        void deleteCommentWithId(String id);

        void startSlideShow();
    }

    private enum ItemType {
        SECTION,
        SLIDE_PICKER,
        PICKER_ROW,
        PICKER_PAIR,
        TOOL_BUTTONS,
        FORMAT_GRID,
        LAYOUT_SECTION,
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
    private boolean slideOptionPickerVisible;
    private boolean currentBold;
    private boolean currentItalic;
    private boolean currentUnderline;
    private boolean currentStrikethrough;
    private TextView slideOptionValueView;
    private final Map<String, String> pickerValues = new HashMap<>();
    private final Map<String, Integer> pickerColorRgb = new HashMap<>();
    private final Map<String, Integer> pickerColorIndex = new HashMap<>();
    private String[] cachedFontOptions = FALLBACK_FONT_OPTIONS;
    private String[] cachedFontValues = FALLBACK_FONT_VALUES;
    private ImpressSolidColorPickerController solidColorPickerController;
    private boolean solidColorPickerVisible;
    private boolean chartPickerVisible;
    private boolean hyperlinkPickerVisible;
    private boolean tablePickerVisible;
    private ImpressHyperlinkPickerController hyperlinkPicker;
    private ImpressInsertTablePickerController tablePicker;
    private ImpressCommentPickerController commentPicker;
    private boolean commentPickerVisible;
    private ImpressReviewCommentController reviewCommentPicker;
    private boolean reviewCommentPickerVisible;
    private int submenuReturnTabIndex = -1;
    private TextView slideMasterValueView;
    private TextView slideBackgroundValueView;
    private Integer selectedMasterSolidRgb;
    private Integer selectedMasterSolidIndex;
    private boolean solidColorForSlideBackground;
    private int selectedTransitionIndex = DEFAULT_SELECTED_TRANSITION_INDEX;
    private LinearLayout transitionGridRoot;
    private int selectedLayoutIndex = -1;
    private LinearLayout layoutGridRoot;
    private ImpressShapePickerController shapePickerController;
    private PopupWindow fontSizePopup;
    private boolean fontColorPickerVisible;
    private CalcFontColorPickerController fontColorPicker;
    private ImageView fontColorPreviewDot;

    public ImpressFunctionPanelController(Host host) {
        this.host = host;
        this.tabs = buildTabs();
        pickerValues.put("slide_format", "A4");
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
                host.runAfterFunctionPanelDismiss(() -> host.focusDocumentAndShowIme());
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
        dismissSecondaryListPanel();
        dismissFontSizePopup();
        dismissFontColorPickerPage();
        dismissSolidColorPickerPage();
        chartPickerVisible = false;
        hyperlinkPickerVisible = false;
        tablePickerVisible = false;
        commentPickerVisible = false;
        reviewCommentPickerVisible = false;
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
        dismissSecondaryListPanel();
        dismissFontSizePopup();
        dismissFontColorPickerPage();
        dismissSolidColorPickerPage();
        dismissChartPickerPage();
        dismissHyperlinkPickerPage();
        dismissTablePickerPage();
        dismissCommentPickerPage();
        dismissReviewCommentPickerPage();
        dismissSlideOptionPicker();
        if (shapePickerController != null) {
            shapePickerController.onConfigurationChanged();
        }
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
            tabView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            tabView.setOnClickListener(v -> selectTab(index));
            tabBar.addView(tabView);
            tabViews.add(tabView);
        }
        StringBuilder tabTitles = new StringBuilder();
        for (int i = 0; i < tabs.size(); i++) {
            if (i > 0) {
                tabTitles.append('|');
            }
            tabTitles.append(tabs.get(i).title);
        }
        Log.i(TAG, "buildTabBar count=" + tabs.size() + " tabs=" + tabTitles);
    }

    private void selectTab(int index) {
        dismissSecondaryListPanel();
        dismissFontColorPickerPage();
        dismissSolidColorPickerPage();
        dismissChartPickerPage();
        dismissHyperlinkPickerPage();
        dismissTablePickerPage();
        dismissCommentPickerPage();
        dismissReviewCommentPickerPage();
        selectedTabIndex = index;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tabView = tabViews.get(i);
            tabView.setTextColor(i == index ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
            tabView.setTypeface(null, i == index ? Typeface.BOLD : Typeface.NORMAL);
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
        transitionGridRoot = null;
        layoutGridRoot = null;
        if ("transition".equals(tab.id)) {
            contentContainer.addView(buildTransitionTabContent());
            return;
        }
        if ("layout_tab".equals(tab.id)) {
            contentContainer.addView(buildLayoutTabContent());
            return;
        }
        if ("insert".equals(tab.id)) {
            contentContainer.addView(createInsertGrid());
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
                    if ("para_grid".equals(item.id)) {
                        root.addView(createParagraphGrid(
                                item.gridLabels, item.gridCommands, item.gridIconRes));
                    } else {
                        root.addView(createLabeledGrid(
                                item.gridLabels, item.gridCommands, item.gridIconRes, item.cols));
                    }
                    break;
                case LAYOUT_SECTION:
                    root.addView(createCommonLayoutSection());
                    break;
                case ACTION:
                    root.addView(createActionRow(item));
                    if (tab.items.indexOf(item) < tab.items.size() - 1) {
                        root.addView(createDivider());
                    }
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
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setPadding(host.dpToPx(2), host.dpToPx(14), host.dpToPx(2), host.dpToPx(6));
        return label;
    }

    private View createCommonLayoutSection() {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(createLayoutSectionHeader());
        section.addView(createCommonLayoutPreviewRow());
        return wrapBottomMargin(section);
    }

    private View createLayoutSectionHeader() {
        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.dpToPx(2), host.dpToPx(14), host.dpToPx(2), 0);

        TextView title = new TextView(host.getContext());
        title.setText("布局");
        title.setTextColor(COLOR_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout allBtn = new LinearLayout(host.getContext());
        allBtn.setOrientation(LinearLayout.HORIZONTAL);
        allBtn.setGravity(Gravity.CENTER_VERTICAL);
        TextView allLabel = new TextView(host.getContext());
        allLabel.setText("全部");
        allLabel.setTextColor(COLOR_VALUE);
        allLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, LAYOUT_ALL_BTN_TEXT_SP);
        allBtn.addView(allLabel);
        ImageView chevron = new ImageView(host.getContext());
        chevron.setImageResource(R.drawable.lolib_ic_impress_row_chevron);
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16));
        chevronLp.setMarginStart(host.dpToPx(4));
        allBtn.addView(chevron, chevronLp);
        allBtn.setOnClickListener(v -> switchToLayoutTab());
        header.addView(allBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private View createCommonLayoutPreviewRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = host.dpToPx(LAYOUT_HEADER_GAP_DP);
        row.setLayoutParams(rowLp);
        int gap = host.dpToPx(LAYOUT_ROW_GAP_DP);
        int count = Math.min(COMMON_LAYOUT_PREVIEW_COUNT, ImpressSlideLayoutCatalog.ENTRIES.length);
        for (int i = 0; i < count; i++) {
            ImpressSlideLayoutCatalog.Entry entry = ImpressSlideLayoutCatalog.ENTRIES[i];
            row.addView(createLayoutCell(entry, i < count - 1, gap));
        }
        return row;
    }

    private void switchToLayoutTab() {
        for (int i = 0; i < tabs.size(); i++) {
            if ("layout_tab".equals(tabs.get(i).id)) {
                selectTab(i);
                return;
            }
        }
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
        value.setTextColor(COLOR_SLIDE_VALUE);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
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
            showSlideOptionPickerPage("格式", FORMAT_LABELS, FORMAT_COMMANDS, item.id, valueView, null);
            return;
        }
        if ("slide_orientation".equals(item.id)) {
            showSlideOptionPickerPage("方向", ORIENTATION_LABELS, ORIENTATION_COMMANDS, item.id, valueView, null);
            return;
        }
        if ("slide_background".equals(item.id)) {
            showSlideOptionPickerPage("背景", BACKGROUND_LABELS, BACKGROUND_COMMANDS, item.id, valueView,
                    createBackgroundActions());
            return;
        }
        if ("slide_master".equals(item.id)) {
            slideMasterValueView = valueView;
            showSlideOptionPickerPage("母版幻灯片", MASTER_SLIDE_LABELS, null, item.id, valueView,
                    createMasterSlideActions());
            return;
        }
        host.toastTodo(item.label + " 后续接入");
    }

    private static final String[] MASTER_SLIDE_LABELS = { "默认", "纯色" };

    private Runnable[] createMasterSlideActions() {
        return new Runnable[] {
                () -> {
                    selectedMasterSolidRgb = null;
                    selectedMasterSolidIndex = null;
                },
                this::showMasterSolidColorPicker,
        };
    }

    private void showMasterSolidColorPicker() {
        dismissSlideOptionPicker();
        showSolidColorPickerPage();
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
        solidColorForSlideBackground = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showSolidColorPickerPage() {
        showSolidColorPickerPage(false);
    }

    private void showSlideBackgroundColorPicker() {
        dismissSlideOptionPicker();
        showSolidColorPickerPage(true);
    }

    private void showSolidColorPickerPage(boolean forSlideBackground) {
        dismissSecondaryListPanel();
        dismissFontColorPickerPage();
        solidColorPickerVisible = true;
        solidColorForSlideBackground = forSlideBackground;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

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
                    public Integer getSelectedIndex() {
                        return selectedMasterSolidIndex;
                    }

                    @Override
                    public void onColorSelected(int index, int rgb) {
                        selectedMasterSolidIndex = index;
                        if (solidColorForSlideBackground) {
                            applySlideBackgroundColor(rgb);
                            pickerValues.put("slide_background", "颜色");
                            if (slideBackgroundValueView != null) {
                                slideBackgroundValueView.setText("颜色");
                            }
                            Log.i(TAG, "slide_background_color index=" + index
                                    + " rgb=#" + Integer.toHexString(rgb).toUpperCase());
                            return;
                        }
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

    private void applySlideBackgroundColor(int rgb) {
        host.executeUnoCommand(buildFillPageStyleUnoCommand(FILL_STYLE_SOLID));
        host.executeUnoCommand(buildBackgroundColorUnoCommand(rgb));
    }

    private String buildFillPageStyleUnoCommand(int fillStyle) {
        return ".uno:FillPageStyle {\"FillPageStyle\":{\"type\":\"short\",\"value\":" + fillStyle + "}}";
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

    private void showSlideOptionPickerPage(String title, String[] labels, String[] commands,
            String pickerId, TextView valueView, Runnable[] extraActions) {
        if (fontPickerPanel == null || dialog == null) {
            return;
        }
        dismissSolidColorPickerPage();
        dismissFontColorPickerPage();
        slideOptionPickerVisible = true;
        slideOptionValueView = valueView;
        if ("slide_background".equals(pickerId)) {
            slideBackgroundValueView = valueView;
        }

        TextView titleView = fontPickerPanel.findViewById(R.id.font_picker_title);
        if (titleView != null) {
            titleView.setText(title);
        }
        ImageButton back = fontPickerPanel.findViewById(R.id.font_picker_back);
        LinearLayout list = fontPickerPanel.findViewById(R.id.font_picker_list);
        if (back != null) {
            back.setOnClickListener(v -> dismissSlideOptionPicker());
        }
        populateSlideOptionList(list, labels, commands, pickerId, valueView, extraActions);

        setTabChromeVisible(false);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
        fontPickerPanel.setVisibility(View.VISIBLE);
        Log.i(TAG, "slide_option_picker_show pickerId=" + pickerId + " title=" + title);
    }

    private void populateSlideOptionList(LinearLayout list, String[] labels, String[] commands,
            String pickerId, TextView valueView, Runnable[] extraActions) {
        list.removeAllViews();
        String selected = pickerValues.getOrDefault(pickerId, labels.length > 0 ? labels[0] : "");
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final int index = i;
            View row = inflater.inflate(R.layout.lolib_item_impress_option_picker_row, list, false);
            TextView name = row.findViewById(R.id.impress_option_picker_item_name);
            ImageView check = row.findViewById(R.id.impress_option_picker_item_check);
            name.setText(label);
            boolean isSelected = label.equals(selected);
            name.setTextColor(COLOR_TITLE);
            check.setImageResource(isSelected
                    ? R.drawable.lolib_ic_impress_radio_checked
                    : R.drawable.lolib_ic_impress_radio_unchecked);
            row.setOnClickListener(v -> {
                pickerValues.put(pickerId, label);
                valueView.setText(label);
                if (commands != null && index < commands.length
                        && commands[index] != null && !commands[index].isEmpty()) {
                    host.executeUnoCommand(commands[index]);
                }
                if (extraActions != null && index < extraActions.length
                        && extraActions[index] != null) {
                    extraActions[index].run();
                }
                populateSlideOptionList(list, labels, commands, pickerId, valueView, extraActions);
                Log.i(TAG, "slide_option_picked pickerId=" + pickerId + " label=" + label);
            });
            list.addView(row);
            if (i < labels.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(COLOR_DIVIDER);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    private void dismissSlideOptionPicker() {
        if (!slideOptionPickerVisible) {
            return;
        }
        slideOptionPickerVisible = false;
        slideOptionValueView = null;
        setTabChromeVisible(true);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
        if (fontPickerPanel != null) {
            fontPickerPanel.setVisibility(View.GONE);
        }
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
        left.setOnClickListener(v -> showFontSizePopup(sizeValue, left));

        LinearLayout right = createCardRow();
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(host.dpToPx(10));
        right.setLayoutParams(rightLp);
        ImageView colorDot = new ImageView(host.getContext());
        colorDot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fontColorPreviewDot = colorDot;
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
        right.setOnClickListener(v -> showFontColorPickerPage());

        row.addView(left);
        row.addView(right);
        return wrapBottomMargin(row);
    }

    private View createToolButtonRow() {
        LinearLayout container = new LinearLayout(host.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(buildCharToolRow(CHAR_TOOL_ICONS_ROW1, CHAR_TOOL_COMMANDS_ROW1, true));
        View row2 = buildCharToolRow(CHAR_TOOL_ICONS_ROW2, CHAR_TOOL_COMMANDS_ROW2, false);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = host.dpToPx(CHAR_ROW_GAP_DP);
        container.addView(row2, row2Lp);
        return wrapBottomMargin(container);
    }

    private GradientDrawable createImpressGridCellDrawable(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(host.dpToPx(IMPRESS_GRID_RADIUS_DP));
        drawable.setColor(selected ? IMPRESS_GRID_CELL_BG_SELECTED : IMPRESS_GRID_CELL_BG);
        return drawable;
    }

    private View buildCharToolRow(int[] iconResIds, String[] commands, boolean fillRow) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                fillRow ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int cellW = host.dpToPx(CHAR_CELL_W_DP);
        int cellH = host.dpToPx(CHAR_CELL_H_DP);
        int pad = host.dpToPx(CHAR_CELL_PAD_DP);
        int gap = host.dpToPx(CHAR_GRID_GAP_DP);
        for (int i = 0; i < commands.length; i++) {
            final String command = commands[i];
            LinearLayout btn = new LinearLayout(host.getContext());
            btn.setOrientation(LinearLayout.VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(pad, pad, pad, pad);
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(iconResIds[i]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            btn.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(ICON_SIZE_DP), host.dpToPx(ICON_SIZE_DP)));
            boolean isActive = isCharCommandActive(command);
            btn.setBackground(createImpressGridCellDrawable(isActive));
            btn.setSelected(isActive);
            LinearLayout.LayoutParams lp;
            if (fillRow) {
                lp = new LinearLayout.LayoutParams(0, cellH, 1f);
            } else {
                lp = new LinearLayout.LayoutParams(cellW, cellH);
            }
            if (i < commands.length - 1) {
                lp.setMarginEnd(gap);
            }
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> onCharToolClick(command));
            row.addView(btn);
        }
        return row;
    }

    private void onCharToolClick(String command) {
        if (".uno:CharBackColor".equals(command)) {
            showHighlightColorPicker();
            return;
        }
        host.executeUnoCommand(command);
    }

    private View createParagraphGrid(String[] labels, String[] commands, int[] iconRes) {
        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        if (labels == null || labels.length == 0) {
            return grid;
        }
        final int cols = 3;
        int gapH = host.dpToPx(PARA_GRID_GAP_H_DP);
        int gapV = host.dpToPx(PARA_GRID_GAP_V_DP);
        int cellH = host.dpToPx(PARA_CELL_H_DP);
        int pad = host.dpToPx(PARA_CELL_PAD_DP);
        for (int rowStart = 0; rowStart < labels.length; rowStart += cols) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowStart > 0) {
                rowLp.topMargin = gapV;
            }
            row.setLayoutParams(rowLp);
            int rowEnd = Math.min(rowStart + cols, labels.length);
            for (int i = rowStart; i < rowEnd; i++) {
                final String label = labels[i];
                final String command = commands != null && i < commands.length ? commands[i] : "";
                LinearLayout cell = new LinearLayout(host.getContext());
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(createImpressGridCellDrawable(false));
                cell.setPadding(pad, pad, pad, pad);
                if (iconRes != null && i < iconRes.length && iconRes[i] != 0) {
                    ImageView icon = new ImageView(host.getContext());
                    icon.setImageResource(iconRes[i]);
                    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    cell.addView(icon, new LinearLayout.LayoutParams(
                            host.dpToPx(ICON_SIZE_DP), host.dpToPx(ICON_SIZE_DP)));
                }
                TextView caption = new TextView(host.getContext());
                caption.setText(label);
                caption.setGravity(Gravity.CENTER);
                caption.setTextColor(COLOR_TITLE);
                caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                caption.setPadding(0, host.dpToPx(6), 0, 0);
                cell.addView(caption);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cellH, 1f);
                if (i < rowEnd - 1) {
                    lp.setMarginEnd(gapH);
                }
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> onGridCommand(command, label));
                row.addView(cell);
            }
            grid.addView(row);
        }
        return wrapBottomMargin(grid);
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

    private View createChevron() {
        ImageView arrow = new ImageView(host.getContext());
        arrow.setImageResource(R.drawable.lolib_ic_impress_row_chevron);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16));
        lp.setMarginStart(host.dpToPx(12));
        arrow.setLayoutParams(lp);
        return arrow;
    }

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
                filterFonts(labels, values);
                if (!labels.isEmpty()) {
                    cachedFontOptions = labels.toArray(new String[0]);
                    cachedFontValues = values != null && !values.isEmpty()
                            ? values.toArray(new String[0]) : cachedFontOptions;
                }
            }
            open.run();
        });
    }

    private void openFontPickerPanel(TextView valueView) {
        if (fontPickerPanel == null || dialog == null) {
            return;
        }
        dismissSlideOptionPicker();
        dismissFontColorPickerPage();
        TextView titleView = fontPickerPanel.findViewById(R.id.font_picker_title);
        if (titleView != null) {
            titleView.setText("字体");
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
                dismissSecondaryListPanel();
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
    }

    private void dismissSecondaryListPanel() {
        dismissFontPicker();
        dismissSlideOptionPicker();
        dismissFontSizePopup();
    }

    private void showFontColorPickerPage() {
        dismissSecondaryListPanel();
        dismissFontSizePopup();
        if (solidColorPickerVisible) {
            solidColorPickerVisible = false;
            solidColorForSlideBackground = false;
        }
        fontColorPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        fontColorPicker = new CalcFontColorPickerController(buildFontColorPickerHost());
        contentContainer.removeAllViews();
        contentContainer.addView(fontColorPicker.buildRootView("字体颜色"));
        Log.i(TAG, "font_color_picker_show");
    }

    private CalcFontColorPickerController.Host buildFontColorPickerHost() {
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
                return pickerColorIndex.get("font_color");
            }

            @Override
            public void onColorSelected(int index, int rgb) {
                host.executeUnoCommand(buildFontColorUnoCommand(rgb));
                pickerColorRgb.put("font_color", rgb);
                pickerColorIndex.put("font_color", index);
                updateColorPreviewDot(fontColorPreviewDot, rgb,
                        R.drawable.lolib_ic_calc_color_font_preview);
                Log.i(TAG, "font_color_picked index=" + index
                        + " rgb=#" + Integer.toHexString(rgb).toUpperCase());
            }

            @Override
            public void onBack() {
                dismissFontColorPickerPage();
            }
        };
    }

    private void dismissFontColorPickerPage() {
        if (!fontColorPickerVisible) {
            return;
        }
        fontColorPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private String buildFontColorUnoCommand(int rgb) {
        return ".uno:Color {\"Color.Color\":{\"type\":\"long\",\"value\":" + rgb + "}}";
    }

    private void showHighlightColorPicker() {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(12));

        TextView section = new TextView(host.getContext());
        section.setText("荧光颜色");
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

        final AlertDialog[] dialogRef = new AlertDialog[1];
        int swatchSize = host.dpToPx(COLOR_SWATCH_SIZE_DP);
        int gap = host.dpToPx(COLOR_SWATCH_GAP_DP);
        final int cols = 4;
        LinearLayout row = null;
        for (int i = 0; i < HIGHLIGHT_COLOR_SWATCHES.length; i++) {
            if (i % cols == 0) {
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
            ColorSwatch swatch = HIGHLIGHT_COLOR_SWATCHES[i];
            FrameLayout chip = createColorSwatchChip(swatch, false, swatchSize);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            if (i % cols < cols - 1) {
                lp.setMarginEnd(gap);
            }
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                host.executeUnoCommand(buildCharBackColorUnoCommand(swatch.rgb));
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
            });
            if (row != null) {
                row.addView(chip);
            }
        }

        AlertDialog colorDialog = new AlertDialog.Builder(host.getContext())
                .setTitle("荧光")
                .setView(root)
                .setNegativeButton("取消", null)
                .create();
        dialogRef[0] = colorDialog;
        colorDialog.show();
    }

    private static String buildCharBackColorUnoCommand(int rgb) {
        return ".uno:CharBackColor {\"CharBackColor.Color\":{\"type\":\"long\",\"value\":" + rgb + "}}";
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

    private List<PanelTab> buildTabs() {
        List<PanelTab> result = new ArrayList<>();

        List<PanelItem> common = new ArrayList<>();
        common.add(new PanelItem(ItemType.SECTION, "sec_slide", "幻灯片"));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_format", "格式", "A4",
                R.drawable.lolib_ic_impress_slide_format));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_orientation", "方向", "横向",
                R.drawable.lolib_ic_impress_slide_orientation));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_background", "背景", "无",
                R.drawable.lolib_ic_impress_slide_background));
        common.add(new PanelItem(ItemType.SLIDE_PICKER, "slide_master", "母版幻灯片", "默认",
                R.drawable.lolib_ic_impress_slide_master));

        common.add(new PanelItem(ItemType.LAYOUT_SECTION, "layout_preview", "布局"));

        common.add(new PanelItem(ItemType.SECTION, "sec_char", "字符"));
        common.add(new PanelItem(ItemType.PICKER_ROW, "font_name", "字体", "宋体"));
        common.add(new PanelItem(ItemType.PICKER_PAIR, "font_size_color", "字号颜色"));
        common.add(new PanelItem(ItemType.TOOL_BUTTONS, "char_tools", "字符样式"));

        common.add(new PanelItem(ItemType.SECTION, "sec_para", "段落"));
        common.add(new PanelItem(ItemType.FORMAT_GRID, "para_grid", "段落",
                PARA_LABELS, PARA_COMMANDS, PARA_ICONS, 3));

        result.add(new PanelTab("common", "常用", common));
        result.add(new PanelTab("file", "文件", buildFileItems()));
        result.add(new PanelTab("insert", "插入", buildInsertItems()));
        result.add(new PanelTab("transition", "切换", stubItems()));
        result.add(new PanelTab("layout_tab", "布局", stubItems()));
        result.add(new PanelTab("review", "审阅", buildReviewItems()));
        return result;
    }

    private List<PanelItem> buildReviewItems() {
        List<PanelItem> review = new ArrayList<>();
        review.add(new PanelItem(ItemType.ACTION, "spell_check", "拼写检查",
                R.drawable.lolib_ic_impress_spell_check, ".uno:SpellDialog"));
        review.add(new PanelItem(ItemType.ACTION, "review_comment", "批注",
                R.drawable.lolib_ic_impress_review_comment, ""));
        review.add(new PanelItem(ItemType.ACTION, "find", "查找替换",
                R.drawable.lolib_ic_function_find_replace, ".uno:SearchDialog"));
        Log.i(TAG, "buildTabs review_items=" + review.size() + " layout=list+icons");
        return review;
    }

    private List<PanelItem> buildFileItems() {
        List<PanelItem> file = new ArrayList<>();
        file.add(new PanelItem(ItemType.ACTION, "save", "保存",
                R.drawable.lolib_ic_calc_file_save, host::saveDocument));
        file.add(new PanelItem(ItemType.ACTION, "save_as", "另存为",
                R.drawable.lolib_ic_impress_file_save_as, host::saveDocumentAs));
        file.add(new PanelItem(ItemType.ACTION, "export", "导出为",
                R.drawable.lolib_ic_calc_file_export, host::exportDocumentAsPdf));
        file.add(new PanelItem(ItemType.ACTION, "print", "打印",
                R.drawable.lolib_ic_calc_file_print, host::initiatePrint));
        return file;
    }

    private List<PanelItem> stubItems() {
        List<PanelItem> items = new ArrayList<>();
        items.add(new PanelItem(ItemType.STUB, "stub", ""));
        return items;
    }

    private List<PanelItem> buildInsertItems() {
        // Rendered via createInsertGrid(); keep stub list for tab metadata.
        return new ArrayList<>();
    }

    private static final class InsertGridItem {
        final String id;
        final String label;
        final int iconResId;
        final String unoCommand;
        final Runnable hostAction;

        InsertGridItem(String id, String label, int iconResId, String unoCommand) {
            this(id, label, iconResId, unoCommand, null);
        }

        InsertGridItem(String id, String label, int iconResId, Runnable hostAction) {
            this(id, label, iconResId, "", hostAction);
        }

        InsertGridItem(String id, String label, int iconResId, String unoCommand, Runnable hostAction) {
            this.id = id;
            this.label = label;
            this.iconResId = iconResId;
            this.unoCommand = unoCommand;
            this.hostAction = hostAction;
        }
    }

    private View createInsertGrid() {
        InsertGridItem[] items = new InsertGridItem[] {
                new InsertGridItem("insert_local_image", "本地图像",
                        R.drawable.lolib_ic_impress_insert_local_image,
                        (Runnable) host::openLocalImagePickerFromWeb),
                new InsertGridItem("insert_chart", "图表",
                        R.drawable.lolib_ic_impress_insert_chart, ""),
                new InsertGridItem("insert_comment", "批注",
                        R.drawable.lolib_ic_impress_insert_comment, ""),
                new InsertGridItem("insert_table", "表格",
                        R.drawable.lolib_ic_impress_insert_table, ""),
                new InsertGridItem("insert_hyperlink", "超链接",
                        R.drawable.lolib_ic_impress_insert_hyperlink, ""),
                new InsertGridItem("insert_shape", "形状",
                        R.drawable.lolib_ic_impress_insert_shape, ""),
                new InsertGridItem("insert_textbox", "文本框",
                        R.drawable.lolib_ic_impress_insert_textbox, ".uno:DrawText"),
                new InsertGridItem("insert_more_fields", "更多字段",
                        R.drawable.lolib_ic_impress_insert_more_fields, ""),
        };

        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int gap = host.dpToPx(INSERT_GRID_GAP_DP);
        int cols = INSERT_GRID_COLS;
        for (int rowStart = 0; rowStart < items.length; rowStart += cols) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int rowEnd = Math.min(rowStart + cols, items.length);
            for (int i = rowStart; i < rowEnd; i++) {
                InsertGridItem item = items[i];
                LinearLayout cell = new LinearLayout(host.getContext());
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackgroundResource(R.drawable.lolib_bg_impress_insert_card);
                cell.setPadding(host.dpToPx(8), host.dpToPx(16), host.dpToPx(8), host.dpToPx(14));
                cell.setMinimumHeight(host.dpToPx(INSERT_CELL_MIN_H_DP));

                ImageView icon = new ImageView(host.getContext());
                icon.setImageResource(item.iconResId);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.addView(icon, new LinearLayout.LayoutParams(
                        host.dpToPx(INSERT_ICON_SIZE_DP), host.dpToPx(INSERT_ICON_SIZE_DP)));

                TextView label = new TextView(host.getContext());
                label.setText(item.label);
                label.setGravity(Gravity.CENTER);
                label.setTextColor(COLOR_TITLE);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                label.setPadding(0, host.dpToPx(8), 0, 0);
                cell.addView(label);

                LinearLayout.LayoutParams lp = createEqualWidthSlotParams(
                        cols, i - rowStart, gap, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = gap;
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> runInsertGridAction(item));
                row.addView(cell);
            }
            for (int slot = rowEnd - rowStart; slot < cols; slot++) {
                row.addView(new View(host.getContext()),
                        createEqualWidthSlotParams(cols, slot, gap, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Log.i(TAG, "insert_grid_render items=" + items.length);
        return grid;
    }

    private void runInsertGridAction(InsertGridItem item) {
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
        if ("insert_table".equals(item.id)) {
            showTablePickerPage();
            return;
        }
        if ("insert_comment".equals(item.id)) {
            showCommentPickerPage();
            return;
        }
        if ("insert_more_fields".equals(item.id)) {
            host.toastTodo("「更多字段」功能开发中");
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

    private void showShapePickerDialog() {
        dismiss();
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
                public int getBottomChromeHeightPx() {
                    return host.getBottomChromeHeightPx();
                }

                @Override
                public void executeUnoCommand(String command) {
                    host.executeUnoCommand(command);
                }

                @Override
                public void runAfterDismiss(Runnable action) {
                    host.runAfterFunctionPanelDismiss(action);
                }
            });
        }
        host.runAfterFunctionPanelDismiss(() -> shapePickerController.show());
    }

    private void showChartTypePickerPage() {
        dismissSecondaryListPanel();
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
        root.addView(ImpressSubpageHeader.createDivider(host.getContext()));

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
        return ImpressSubpageHeader.create(
                host.getContext(), host::dpToPx, "图表", v -> dismissChartPickerPage());
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
        dismissSecondaryListPanel();
        hyperlinkPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        if (hyperlinkPicker == null) {
            hyperlinkPicker = new ImpressHyperlinkPickerController(new ImpressHyperlinkPickerController.Host() {
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
                public void fetchImpressHyperlinkContext(
                        ImpressHyperlinkPickerController.HyperlinkContextCallback callback) {
                    host.fetchImpressHyperlinkContext(callback);
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

    private void dismissTablePickerPage() {
        if (!tablePickerVisible) {
            return;
        }
        tablePickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showTablePickerPage() {
        dismissSecondaryListPanel();
        tablePickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        if (tablePicker == null) {
            tablePicker = new ImpressInsertTablePickerController(new ImpressInsertTablePickerController.Host() {
                @Override
                public android.content.Context getContext() {
                    return host.getContext();
                }

                @Override
                public int dpToPx(int dp) {
                    return host.dpToPx(dp);
                }

                @Override
                public void insertTable(int rows, int columns) {
                    String command = ".uno:InsertTable?Columns=" + columns + "&Rows=" + rows;
                    runAndDismiss(() -> host.executeUnoCommand(command));
                }

                @Override
                public void onBack() {
                    dismissTablePickerPage();
                }
            });
        }

        contentContainer.removeAllViews();
        contentContainer.addView(tablePicker.buildRootView());
        Log.i(TAG, "table_picker_show");
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

    private void showCommentPickerPage() {
        dismissSecondaryListPanel();
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

    private void dismissReviewCommentPickerPage() {
        if (!reviewCommentPickerVisible) {
            return;
        }
        reviewCommentPickerVisible = false;
        setTabChromeVisible(true);
        int returnIndex = submenuReturnTabIndex >= 0 && submenuReturnTabIndex < tabs.size()
                ? submenuReturnTabIndex : selectedTabIndex;
        selectedTabIndex = returnIndex;
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(returnIndex));
        }
    }

    private void showReviewCommentPickerPage() {
        dismissSecondaryListPanel();
        reviewCommentPickerVisible = true;
        submenuReturnTabIndex = selectedTabIndex;
        setTabChromeVisible(false);

        if (reviewCommentPicker == null) {
            reviewCommentPicker = new ImpressReviewCommentController(
                    new ImpressReviewCommentController.Host() {
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
                        public void toastTodo(String text) {
                            host.toastTodo(text);
                        }

                        @Override
                        public void fetchReviewComments(
                                ImpressReviewCommentController.ReviewCommentsCallback callback) {
                            host.fetchReviewComments(callback);
                        }

                        @Override
                        public void editCommentWithText(String id, String author, String text) {
                            host.editCommentWithText(id, author, text);
                        }

                        @Override
                        public void deleteCommentWithId(String id) {
                            host.deleteCommentWithId(id);
                        }

                        @Override
                        public void onBack() {
                            dismissReviewCommentPickerPage();
                        }
                    });
        }

        contentContainer.removeAllViews();
        contentContainer.addView(reviewCommentPicker.buildRootView());
        reviewCommentPicker.onPickerShown();
        Log.i(TAG, "review_comment_picker_show");
    }

    private LinearLayout.LayoutParams createEqualWidthSlotParams(
            int maxCols, int slotIndex, int gap, int heightPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1f);
        if (slotIndex < maxCols - 1) {
            lp.setMarginEnd(gap);
        }
        return lp;
    }

    private View createDivider() {
        View divider = new View(host.getContext());
        divider.setBackgroundColor(FILE_ACTION_DIVIDER_COLOR);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return divider;
    }

    private View createActionRow(PanelItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(FILE_ACTION_ROW_H_DP));
        row.setPadding(
                host.dpToPx(FILE_ACTION_ROW_HPAD_DP),
                host.dpToPx(FILE_ACTION_ROW_VPAD_DP),
                host.dpToPx(FILE_ACTION_ROW_HPAD_DP),
                host.dpToPx(FILE_ACTION_ROW_VPAD_DP));

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(
                    host.dpToPx(FILE_ACTION_ICON_DP), host.dpToPx(FILE_ACTION_ICON_DP)));
        }

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_SLIDE_VALUE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, FILE_ACTION_TEXT_SP);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (item.iconResId != 0) {
            labelLp.setMarginStart(host.dpToPx(FILE_ACTION_ICON_TEXT_GAP_DP));
        }
        row.addView(label, labelLp);
        row.setOnClickListener(v -> runItemAction(item));
        return row;
    }

    private void runItemAction(PanelItem item) {
        if ("review_comment".equals(item.id)) {
            showReviewCommentPickerPage();
            return;
        }
        FunctionPanelSpellCheckHelper.runPanelActionAndDismiss(
                this::dismiss,
                item.unoCommand,
                item.hostAction,
                new FunctionPanelSpellCheckHelper.Host() {
                    @Override
                    public void executeUnoCommand(String command) {
                        host.executeUnoCommand(command);
                    }

                    @Override
                    public void runAfterFunctionPanelDismiss(Runnable action) {
                        host.runAfterFunctionPanelDismiss(action);
                    }
                });
    }

    /**
     * 与 Writer {@link FunctionPanelController} 一致：图片选择等需立即执行；
     * SpellDialog 走 {@link FunctionPanelSpellCheckHelper} 延迟 UNO。
     */
    private void runAndDismiss(Runnable action) {
        dismiss();
        if (action != null) {
            action.run();
        }
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
        LinearLayout outer = new LinearLayout(host.getContext());
        outer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outerLp.bottomMargin = host.dpToPx(12);
        outer.setLayoutParams(outerLp);

        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.lolib_bg_impress_apply_all_row);
        int hPad = host.dpToPx(16);
        int vPad = host.dpToPx(14);
        card.setPadding(hPad, vPad, hPad, vPad);
        card.setMinimumHeight(host.dpToPx(48));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(R.drawable.lolib_ic_impress_apply_transition_all);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));

        TextView label = new TextView(host.getContext());
        label.setText("应用到全部幻灯片");
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMarginStart(host.dpToPx(8));
        card.addView(label, labelLp);

        card.setOnClickListener(v -> onApplyTransitionToAll());
        outer.addView(card);
        return outer;
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

    private View buildLayoutTabContent() {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(createLayoutGrid());
        return root;
    }

    private View createLayoutGrid() {
        layoutGridRoot = new LinearLayout(host.getContext());
        layoutGridRoot.setOrientation(LinearLayout.VERTICAL);
        int gap = host.dpToPx(LAYOUT_ROW_GAP_DP);
        int sidePad = host.dpToPx(12);
        layoutGridRoot.setPadding(sidePad, 0, sidePad, host.dpToPx(12));

        ImpressSlideLayoutCatalog.Entry[] entries = ImpressSlideLayoutCatalog.ENTRIES;
        for (int rowStart = 0; rowStart < entries.length; rowStart += LAYOUT_GRID_COLS) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int rowEnd = Math.min(rowStart + LAYOUT_GRID_COLS, entries.length);
            for (int i = rowStart; i < rowEnd; i++) {
                ImpressSlideLayoutCatalog.Entry entry = entries[i];
                row.addView(createLayoutCell(entry, i < rowEnd - 1, gap));
            }
            for (int slot = rowEnd - rowStart; slot < LAYOUT_GRID_COLS; slot++) {
                row.addView(new View(host.getContext()),
                        createEqualWidthSlotParams(LAYOUT_GRID_COLS, slot, gap, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (rowStart > 0) {
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = gap;
                row.setLayoutParams(rowLp);
            }
            layoutGridRoot.addView(row);
        }
        Log.i(TAG, "layout_grid_render items=" + entries.length);
        return layoutGridRoot;
    }

    private View createLayoutCell(ImpressSlideLayoutCatalog.Entry entry, boolean addEndGap, int gap) {
        LinearLayout cell = new LinearLayout(host.getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean selected = entry.index == selectedLayoutIndex;
        cell.setBackgroundResource(selected
                ? R.drawable.lolib_bg_impress_transition_cell_selected
                : R.drawable.lolib_bg_impress_insert_card);
        int pad = host.dpToPx(LAYOUT_CELL_PAD_DP);
        cell.setPadding(pad, pad, pad, pad);

        ImageView thumb = new ImageView(host.getContext());
        thumb.setImageResource(entry.iconResId);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(thumb, new LinearLayout.LayoutParams(
                host.dpToPx(LAYOUT_THUMB_W_DP), host.dpToPx(LAYOUT_THUMB_H_DP)));

        TextView caption = new TextView(host.getContext());
        caption.setText(entry.label);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(COLOR_TITLE);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, LAYOUT_LABEL_SP);
        caption.setMaxLines(2);
        caption.setPadding(0, host.dpToPx(6), 0, 0);
        cell.addView(caption);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, host.dpToPx(LAYOUT_CELL_H_DP), 1f);
        if (addEndGap) {
            lp.setMarginEnd(gap);
        }
        cell.setLayoutParams(lp);
        cell.setTag(entry.index);
        cell.setOnClickListener(v -> onLayoutCellClick(entry));
        return cell;
    }

    private void onLayoutCellClick(ImpressSlideLayoutCatalog.Entry entry) {
        selectedLayoutIndex = entry.index;
        String command = ".uno:AssignLayout?WhatLayout:long=" + entry.whatLayout;
        Log.i(TAG, "layout_apply index=" + entry.index
                + " label=" + entry.label + " whatLayout=" + entry.whatLayout);
        runAndDismiss(() -> host.executeUnoCommand(command));
    }

    private void refreshLayoutSelection() {
        if (layoutGridRoot == null) {
            return;
        }
        for (int r = 0; r < layoutGridRoot.getChildCount(); r++) {
            View rowView = layoutGridRoot.getChildAt(r);
            if (!(rowView instanceof LinearLayout)) {
                continue;
            }
            LinearLayout row = (LinearLayout) rowView;
            for (int c = 0; c < row.getChildCount(); c++) {
                View cell = row.getChildAt(c);
                Object tag = cell.getTag();
                boolean selected = tag instanceof Integer && ((Integer) tag) == selectedLayoutIndex;
                cell.setBackgroundResource(selected
                        ? R.drawable.lolib_bg_impress_transition_cell_selected
                        : R.drawable.lolib_bg_impress_insert_card);
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

    private static final int FILL_STYLE_NONE = 0;
    private static final int FILL_STYLE_SOLID = 1;
    private static final int FILL_STYLE_GRADIENT = 2;
    private static final int FILL_STYLE_HATCH = 3;
    private static final int FILL_STYLE_BITMAP = 4;

    private static final String[] FORMAT_LABELS = {
            "A4", "A3", "A5", "A6", "A2", "A1", "A0",
            "B6(ISO)", "B5(ISO)", "B4(ISO)",
            "B6(JIS)", "B5(JIS)", "B4(JIS)",
            "Letter", "Legal", "Tabloid",
            "16开", "32开", "大32开",
            "自定义"
    };
    private static final String[] FORMAT_COMMANDS = {
            ".uno:AttributePageSize?PaperFormat:short=4",   // A4
            ".uno:AttributePageSize?PaperFormat:short=3",   // A3
            ".uno:AttributePageSize?PaperFormat:short=5",   // A5
            ".uno:AttributePageSize?PaperFormat:short=56",  // A6
            ".uno:AttributePageSize?PaperFormat:short=2",   // A2
            ".uno:AttributePageSize?PaperFormat:short=1",   // A1
            ".uno:AttributePageSize?PaperFormat:short=0",   // A0
            ".uno:AttributePageSize?PaperFormat:short=12",  // B6 ISO
            ".uno:AttributePageSize?PaperFormat:short=7",   // B5 ISO
            ".uno:AttributePageSize?PaperFormat:short=6",   // B4 ISO
            ".uno:AttributePageSize?PaperFormat:short=36",  // B6 JIS
            ".uno:AttributePageSize?PaperFormat:short=35",  // B5 JIS
            ".uno:AttributePageSize?PaperFormat:short=34",  // B4 JIS
            ".uno:AttributePageSize?PaperFormat:short=8",   // Letter
            ".uno:AttributePageSize?PaperFormat:short=9",   // Legal
            ".uno:AttributePageSize?PaperFormat:short=10",  // Tabloid
            ".uno:AttributePageSize?PaperFormat:short=31",  // 16开 KAI16
            ".uno:AttributePageSize?PaperFormat:short=32",  // 32开 KAI32
            ".uno:AttributePageSize?PaperFormat:short=33",  // 大32开 KAI32BIG
            ".uno:AttributePageSize?PaperFormat:short=11",  // User
    };
    private static final String[] ORIENTATION_LABELS = { "横向", "纵向" };
    private static final String[] ORIENTATION_COMMANDS = {
            ".uno:Orientation?isLandscape:bool=true",
            ".uno:Orientation?isLandscape:bool=false",
    };
    private static final String[] BACKGROUND_LABELS = {
            "无", "颜色", "渐变", "阴影线", "位图", "图案", "使用幻灯片背景"
    };
    private static final String[] BACKGROUND_COMMANDS = {
            ".uno:FillPageStyle {\"FillPageStyle\":{\"type\":\"short\",\"value\":0}}",
            ".uno:FillPageStyle {\"FillPageStyle\":{\"type\":\"short\",\"value\":1}}",
            ".uno:FillPageStyle {\"FillPageStyle\":{\"type\":\"short\",\"value\":2}}",
            ".uno:FillPageStyle {\"FillPageStyle\":{\"type\":\"short\",\"value\":3}}",
            ".uno:SelectBackground",
            ".uno:FillPageStyle {\"FillPageStyle\":{\"type\":\"short\",\"value\":4}}",
            ".uno:DisplayMasterBackground?DisplayMasterBackground:bool=true",
    };

    private Runnable[] createBackgroundActions() {
        return new Runnable[] {
                null,
                this::showSlideBackgroundColorPicker,
                null,
                null,
                () -> host.runAfterFunctionPanelDismiss(() -> host.openLocalImagePickerFromWeb()),
                null,
                null,
        };
    }

    private static final String[] FALLBACK_FONT_OPTIONS = {
            "宋体", "Liberation Serif", "Liberation Sans", "Arial"
    };
    private static final String[] FALLBACK_FONT_VALUES = FALLBACK_FONT_OPTIONS;

    /** Font names excluded from the picker (case-insensitive). */
    private static final Set<String> FONT_BLOCKLIST = new HashSet<>(Arrays.asList(
            "AndroidClock",
            "droid sans mono",
            "MiSansC Mitype Mono Rounded Normal Cond",
            "Mitype Mono VG",
            "Mitype Rounded Normal",
            "Mitype VF",
            "MIUI EX",
            "Noto Color Emoji Flags",
            "Noto Naskh Arabic UI",
            "Noto Sans Ahom",
            "Noto Sans AnatoHiero",
            "Noto Sans Avestan",
            "Noto Sans Bengali UI",
            "Noto Sans CanAborig",
            "Noto Sans Devanagari",
            "Noto Sans Devanagari UI",
            "OpenSymbol",
            "Liberation Mono",
            "Liberation Sans Narrow",
            "MiClock Extralight",
            "MiClock Mono Extralight"
    ));

    /** For Noto* fonts, only these two are kept; all others are filtered out. */
    private static final Set<String> NOTO_ALLOWED = new HashSet<>(Arrays.asList(
            "Noto Sans", "Noto Serif"
    ));

    /** Returns true if the font name should be hidden from the picker. */
    private static boolean isFontBlocked(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        // 1. Exact-match blocklist
        for (String blocked : FONT_BLOCKLIST) {
            if (lower.equals(blocked.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        // 2. Noto rule: keep only "Noto Sans" and "Noto Serif"
        if (lower.startsWith("noto")) {
            boolean allowed = false;
            for (String a : NOTO_ALLOWED) {
                if (lower.equals(a.toLowerCase(Locale.ROOT))) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return true;
            }
        }
        return false;
    }

    /** Filters a font list, returning only names that pass {@link #isFontBlocked} inverted. */
    private static void filterFonts(List<String> labels, List<String> values) {
        if (labels == null) {
            return;
        }
        int i = 0;
        while (i < labels.size()) {
            if (isFontBlocked(labels.get(i))) {
                labels.remove(i);
                if (values != null && i < values.size()) {
                    values.remove(i);
                }
            } else {
                i++;
            }
        }
    }

    private static final String[] SIZE_OPTIONS = {
            "初号", "小初", "一号", "小一", "二号", "小二", "三号", "小三", "四号", "小四", "五号", "小五"
    };
    private static final String[] SIZE_VALUES = {
            "42", "36", "26", "24", "22", "18", "16", "15", "14", "12", "10.5", "9"
    };
}
