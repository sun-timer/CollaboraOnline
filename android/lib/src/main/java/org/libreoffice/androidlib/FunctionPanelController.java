package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.core.widget.NestedScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.CompoundButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;
import org.libreoffice.androidlib.impress.ImpressShapePickerController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Edit-mode function panel with five tabs: 常用 / 文件 / 插入 / 布局 / 审阅.
 */
public class FunctionPanelController {
    private static final String TAG = "FunctionPanelController";

    // Figma style: tab selected = blue text + underline, unselected = dark gray text
    private static final int COLOR_TAB_SELECTED_BG = Color.parseColor("#F4F5F7");
    private static final int COLOR_TAB_UNSELECTED_BG = Color.parseColor("#E4E4E6");
    private static final int COLOR_TAB_ACTIVE_TEXT = Color.parseColor("#1278D9");
    private static final int COLOR_TAB_INACTIVE_TEXT = Color.parseColor("#333333");
    private static final int COLOR_TAB_SELECTED_TEXT = Color.parseColor("#202124");
    private static final int COLOR_TAB_UNSELECTED_TEXT = Color.parseColor("#80868B");
    private static final int COLOR_DIVIDER = Color.parseColor("#E3E3E3");
    private static final int COLOR_SECTION = Color.parseColor("#80868B");
    private static final int COLOR_SECTION_TITLE = Color.parseColor("#101010");
    private static final int COLOR_CARD_BG = Color.parseColor("#F2F3F5");
    private static final int COLOR_TAB_BAR_BORDER = Color.parseColor("#A2A9B2");
    /** Figma 750×1624 canvas: 功能面板统一固定高度（切换 tab 不变；内容滚动/留白）。 */
    private static final float SHEET_HEIGHT_RATIO = BottomSheetAnchorHelper.FUNCTION_PANEL_HEIGHT_RATIO;

    public interface StringListCallback {
        void onResult(List<String> labels, List<String> values);
    }

    public interface FormattingCallback {
        void onResult(String styleName, String fontName, String fontSizePt, String paragraphAlignment,
                      boolean bold, boolean italic, boolean underline, boolean strikethrough);
    }

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void executeUnoCommand(String command);

        void saveDocument();

        void saveDocumentAs();

        void exportDocumentAsPdf();

        void initiatePrint();

        void openLocalImagePickerFromWeb();

        void toastTodo(String text);

        void applyWatermark(String text, String font, int angle, int transparency);

        void applyParagraphStyle(String styleName);

        void applyFont(String fontName);

        void applyFontSize(String fontSizePt);

        void insertComment();

        String getCommentAuthorName();

        void insertCommentWithText(String text);

        void insertChartWithType(String unoChartType);

        void runAfterFunctionPanelDismiss(Runnable action);

        void fetchStyleList(StringListCallback callback);

        void fetchFontList(StringListCallback callback);

        void fetchCurrentFormatting(FormattingCallback callback);

        void showAiOperationSheet();

        void focusDocumentAndShowIme();

        /** Bottom toolbar + nav spacer height; sheets anchor above this chrome. */
        int getBottomChromeHeightPx();
    }

    private enum ItemType {
        SECTION,
        STYLE_SECTION,
        STYLE_CHIP,
        PICKER,
        PARAGRAPH_CHIP,
        ACTION,
        GRID_ACTION,
        TOGGLE,
        WATERMARK
    }

    private static final class FunctionItem {
        final ItemType type;
        final String id;
        final String label;
        final String subtitle;
        final int iconResId;
        final String unoCommand;
        final Runnable hostAction;
        final String[] pickerOptions;
        final String[] pickerValues;
        final boolean defaultToggleOn;

        FunctionItem(ItemType type, String id, String label) {
            this(type, id, label, "", 0, "", null, null, null, false);
        }

        FunctionItem(ItemType type, String id, String label, int iconResId, String unoCommand) {
            this(type, id, label, "", iconResId, unoCommand, null, null, null, false);
        }

        FunctionItem(ItemType type, String id, String label, int iconResId, Runnable hostAction) {
            this(type, id, label, "", iconResId, "", hostAction, null, null, false);
        }

        FunctionItem(ItemType type, String id, String label, String subtitle, int iconResId,
                String unoCommand, Runnable hostAction, String[] pickerOptions,
                String[] pickerValues, boolean defaultToggleOn) {
            this.type = type;
            this.id = id;
            this.label = label;
            this.subtitle = subtitle;
            this.iconResId = iconResId;
            this.unoCommand = unoCommand;
            this.hostAction = hostAction;
            this.pickerOptions = pickerOptions;
            this.pickerValues = pickerValues;
            this.defaultToggleOn = defaultToggleOn;
        }
    }

    private static final class FunctionTab {
        final String id;
        final String title;
        final List<FunctionItem> items;

        FunctionTab(String id, String title, List<FunctionItem> items) {
            this.id = id;
            this.title = title;
            this.items = items;
        }
    }

    private final Host host;
    private BottomSheetDialog dialog;
    private LinearLayout tabBar;
    private NestedScrollView contentContainer;
    private View tabHeader;
    private View functionTabArea;
    private View fontPickerPanel;
    private View optionPickerPanel;
    private PopupWindow fontSizePopup;
    private View sheetContentRoot;
    private boolean fontPickerVisible;
    private boolean optionPickerVisible;
    private final List<TextView> tabViews = new ArrayList<>();
    private View tabIndicator;
    private final List<FunctionTab> tabs;
    private int selectedTabIndex = 0;
    private final Map<String, String> pickerValues = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private String[] cachedFontOptions = FALLBACK_FONT_OPTIONS;
    private String[] cachedFontValues = FALLBACK_FONT_VALUES;
    private String[] cachedStyleLabels;
    private String[] cachedStyleValues;
    private ImpressInsertTablePickerController tablePicker;
    private ImpressShapePickerController shapePicker;
    private ImpressCommentPickerController commentPicker;
    private boolean commentPickerVisible;
    private boolean chartPickerVisible;
    private WatermarkSettingsController watermarkPicker;
    private PaperSizePickerController paperSizePicker;
    private double customPaperWidthCm = 21.0;
    private double customPaperHeightCm = 29.7;
    private String currentStyleName = "";
    private String currentParagraphAlignment = "";
    private String watermarkText = "水印文本";
    private String watermarkFont = "";
    private int watermarkAngle = 45;
    private int watermarkTransparency = 50;
    private SwitchCompat watermarkToggleView;

    public FunctionPanelController(Host host) {
        this.host = host;
        this.tabs = buildTabs();
        pickerValues.put("font_name", "字体");
        pickerValues.put("font_size", "4号");
        pickerValues.put("page_margins", WriterLayoutCatalog.MARGINS[0].label);
        pickerValues.put("paper_size", "A4");
        pickerValues.put("paper_orientation", "纵向");
        pickerValues.put("style_picker", "正文");
        toggleStates.put("watermark", false);
        toggleStates.put("track_changes", false);
        toggleStates.put("show_changes", true);
    }

    public void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_functions_edit, null, false);
        sheetContentRoot = panel;
        tabHeader = panel.findViewById(R.id.function_edit_tab_header);
        functionTabArea = panel.findViewById(R.id.function_edit_tab_area);
        fontPickerPanel = panel.findViewById(R.id.function_font_picker_panel);
        optionPickerPanel = panel.findViewById(R.id.function_option_picker_panel);
        tabBar = panel.findViewById(R.id.function_edit_tab_bar);
        contentContainer = panel.findViewById(R.id.function_edit_content_container);
        tabIndicator = panel.findViewById(R.id.function_edit_tab_indicator);
        ImageButton aiBtn = panel.findViewById(R.id.function_edit_btn_ai);
        ImageButton keyboardBtn = panel.findViewById(R.id.function_edit_btn_keyboard);
        ImageButton collapseBtn = panel.findViewById(R.id.function_edit_btn_collapse);
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

    public void dismiss() {
        dismissFontPickerDialog();
        dismissOptionPicker();
        dismissFontSizePopup();
        dismissCommentPickerPage();
        dismissChartPickerPage();
        dismissWatermarkSettingsPage();
        dismissPaperSizePickerPage();
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
        dismissFontPickerDialog();
        dismissOptionPicker();
        dismissFontSizePopup();
        dismissCommentPickerPage();
        dismissChartPickerPage();
        dismissWatermarkSettingsPage();
        dismissPaperSizePickerPage();
        dismissTablePickerPage();
        if (shapePicker != null) {
            shapePicker.onConfigurationChanged();
        }
        applyAdaptiveSheetHeight();
    }

    private void buildTabBar() {
        tabBar.removeAllViews();
        tabViews.clear();
        tabBar.setBackgroundColor(Color.TRANSPARENT);
        tabBar.setPadding(0, 0, 0, 0);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);

        // Build tabs with Figma style: 17sp text, blue active / gray inactive
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            FunctionTab tab = tabs.get(i);
            TextView tabView = new TextView(host.getContext());
            tabView.setText(tab.title);
            tabView.setGravity(Gravity.CENTER);
            tabView.setTextSize(17);
            tabView.setPadding(host.dpToPx(16), 0, host.dpToPx(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            if (i > 0) {
                lp.setMarginStart(host.dpToPx(4));
            }
            tabView.setLayoutParams(lp);
            tabView.setOnClickListener(v -> selectTab(index));
            tabBar.addView(tabView);
            tabViews.add(tabView);
        }
    }

    private void selectTab(int index) {
        selectedTabIndex = index;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tabView = tabViews.get(i);
            boolean selected = i == index;
            tabView.setBackgroundColor(Color.TRANSPARENT);
            tabView.setTextColor(selected ? COLOR_TAB_ACTIVE_TEXT : COLOR_TAB_INACTIVE_TEXT);
        }
        // Update indicator position to center under the selected tab
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

    private void applyAdaptiveSheetHeight() {
        if (dialog == null) {
            return;
        }
        BottomSheetAnchorHelper.clearAppliedHeight(dialog);
        AiDialogHelper.applyNoDimScrim(dialog);
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(R.drawable.lolib_bg_font_picker_sheet);
            bottomSheet.setElevation(host.dpToPx(28));
        }
        BottomSheetAnchorHelper.Options options = new BottomSheetAnchorHelper.Options();
        options.logTag = TAG;
        BottomSheetAnchorHelper.expandFunctionPanel(dialog, SHEET_HEIGHT_RATIO, options);
    }

    private void updateTabIndicator(int index) {
        if (tabIndicator == null || index < 0 || index >= tabViews.size()) {
            return;
        }
        FunctionPanelTabIndicatorHelper.updateForSelectedTab(tabViews.get(index), tabBar, tabIndicator);
    }

    private void renderTabContent(FunctionTab tab) {
        contentContainer.removeAllViews();
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        List<FunctionItem> pendingParagraphChips = new ArrayList<>();
        List<FunctionItem> pendingGridActions = new ArrayList<>();
        List<FunctionItem> pendingLayoutPickers = new ArrayList<>();

        for (FunctionItem item : tab.items) {
            switch (item.type) {
                case SECTION:
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    flushLayoutPickers(root, pendingLayoutPickers);
                    root.addView(createSectionHeader(item.label));
                    break;
                case PARAGRAPH_CHIP:
                    pendingParagraphChips.add(item);
                    break;
                case GRID_ACTION:
                    pendingGridActions.add(item);
                    break;
                case PICKER:
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    if ("layout".equals(tab.id)) {
                        pendingLayoutPickers.add(item);
                    } else {
                        flushLayoutPickers(root, pendingLayoutPickers);
                        root.addView(createPickerRow(item));
                    }
                    break;
                case ACTION:
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    flushLayoutPickers(root, pendingLayoutPickers);
                    root.addView(createActionRow(item));
                    root.addView(createDivider());
                    break;
                case TOGGLE:
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    flushLayoutPickers(root, pendingLayoutPickers);
                    root.addView(createToggleRow(item));
                    break;
                case WATERMARK:
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    flushLayoutPickers(root, pendingLayoutPickers);
                    root.addView(createWatermarkRow(item));
                    root.addView(createLayoutSectionSpacer());
                    break;
                default:
                    break;
            }
        }
        flushParagraphChips(root, pendingParagraphChips);
        flushGridActions(root, pendingGridActions);
        flushLayoutPickers(root, pendingLayoutPickers);

        contentContainer.addView(root);
    }

    private void flushLayoutPickers(LinearLayout root, List<FunctionItem> pickers) {
        if (pickers.isEmpty()) {
            return;
        }
        root.addView(createGroupedLayoutPickers(pickers));
        pickers.clear();
    }

    private View createLayoutSectionSpacer() {
        View spacer = new View(host.getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(40));
        spacer.setLayoutParams(lp);
        return spacer;
    }

    private View createGroupedLayoutPickers(List<FunctionItem> pickers) {
        LinearLayout group = new LinearLayout(host.getContext());
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        group.setLayoutParams(groupLp);

        for (int i = 0; i < pickers.size(); i++) {
            group.addView(createGroupedPickerRow(pickers.get(i)));
            if (i < pickers.size() - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x14000000);
                group.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
        return group;
    }

    private View createGroupedPickerRow(FunctionItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.dpToPx(16), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        row.setMinimumHeight(host.dpToPx(56));

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
        }

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TAB_INACTIVE_TEXT);
        label.setTextSize(16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(host.dpToPx(item.iconResId != 0 ? 16 : 0));
        row.addView(label, labelLp);

        TextView valueView = null;
        if ("paper_size".equals(item.id) || "paper_orientation".equals(item.id)) {
            String currentValue = pickerValues.getOrDefault(item.id, item.subtitle);
            valueView = new TextView(host.getContext());
            valueView.setText(currentValue);
            valueView.setTextColor(Color.parseColor("#6A6A6A"));
            valueView.setTextSize(14);
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueLp.setMarginEnd(host.dpToPx(8));
            row.addView(valueView, valueLp);
        }

        ImageView chevron = new ImageView(host.getContext());
        chevron.setImageResource(R.drawable.lolib_ic_chevron_right);
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int chevronSize = host.dpToPx(16);
        row.addView(chevron, new LinearLayout.LayoutParams(chevronSize, chevronSize));

        final TextView valueTarget = valueView;
        row.setOnClickListener(v -> showPickerDialog(item, valueTarget != null ? valueTarget : label));
        return row;
    }

    private void flushParagraphChips(LinearLayout root, List<FunctionItem> chips) {
        if (!chips.isEmpty()) {
            root.addView(createParagraphChipGrid(chips));
            chips.clear();
        }
    }

    private void flushGridActions(LinearLayout root, List<FunctionItem> actions) {
        if (!actions.isEmpty()) {
            root.addView(createGridActions(actions));
            actions.clear();
        }
    }

    private TextView createSectionHeader(String title) {
        TextView header = new TextView(host.getContext());
        header.setText(title);
        header.setTextColor(COLOR_SECTION_TITLE);
        header.setTextSize(16);
        header.setPadding(host.dpToPx(4), host.dpToPx(12), host.dpToPx(4), host.dpToPx(10));
        return header;
    }

    private View createDivider() {
        View divider = new View(host.getContext());
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1));
        divider.setLayoutParams(lp);
        return divider;
    }

    private View createParagraphChipGrid(List<FunctionItem> chips) {
        // Figma: 3-column grid of cards (icon 24dp + label 14sp), bg #F2F3F5, no border
        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int cols = 3;
        for (int rowStart = 0; rowStart < chips.size(); rowStart += cols) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = rowStart; i < Math.min(rowStart + cols, chips.size()); i++) {
                FunctionItem chip = chips.get(i);
                LinearLayout card = new LinearLayout(host.getContext());
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                // Figma card: #F2F3F5, no border. Selected = blue tint.
                card.setBackgroundResource(isCurrentParagraphAlignment(chip.id)
                        ? R.drawable.lolib_bg_function_chip_selected
                        : R.drawable.lolib_bg_function_card_figma);
                card.setPadding(host.dpToPx(8), host.dpToPx(16), host.dpToPx(8), host.dpToPx(14));
                card.setMinimumHeight(host.dpToPx(80));

                ImageView icon = new ImageView(host.getContext());
                icon.setImageResource(chip.iconResId);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        host.dpToPx(24), host.dpToPx(24));
                card.addView(icon, iconLp);

                TextView label = new TextView(host.getContext());
                label.setText(chip.label);
                label.setGravity(Gravity.CENTER);
                label.setTextColor(COLOR_SECTION_TITLE);
                label.setTextSize(14);
                label.setPadding(0, host.dpToPx(8), 0, 0);
                card.addView(label);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginEnd(host.dpToPx(i < Math.min(rowStart + cols, chips.size()) - 1 ? 12 : 0));
                lp.bottomMargin = host.dpToPx(10);
                card.setLayoutParams(lp);
                card.setOnClickListener(v -> runAndDismiss(() -> host.executeUnoCommand(chip.unoCommand)));
                row.addView(card);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return grid;
    }

    private View createPickerRow(FunctionItem item) {
        // Figma picker: entire row in a bg #F2F3F5 rounded card, no border
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        card.setPadding(host.dpToPx(16), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        card.setMinimumHeight(host.dpToPx(52));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = host.dpToPx(10);
        card.setLayoutParams(cardLp);

        // Icon 24dp (Figma 48px at ~2x scale)
        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    host.dpToPx(24), host.dpToPx(24));
            card.addView(icon, iconLp);
        }

        String currentValue = pickerValues.getOrDefault(item.id, item.subtitle);
        if ("font_size".equals(item.id)) {
            // Figma字号: icon + "大小" label + [bordered value box "4号" + arrow]
            TextView label = new TextView(host.getContext());
            label.setText("大小");
            label.setTextColor(COLOR_TAB_INACTIVE_TEXT);
            label.setTextSize(16);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(host.dpToPx(16));
            card.addView(label, labelLp);

            LinearLayout valueBox = new LinearLayout(host.getContext());
            valueBox.setOrientation(LinearLayout.HORIZONTAL);
            valueBox.setGravity(Gravity.CENTER_VERTICAL);
            valueBox.setBackgroundResource(R.drawable.lolib_bg_function_value_box);
            valueBox.setPadding(host.dpToPx(12), host.dpToPx(4), host.dpToPx(4), host.dpToPx(4));
            valueBox.setMinimumWidth(host.dpToPx(80));

            TextView value = new TextView(host.getContext());
            value.setText(currentValue);
            value.setTextColor(Color.parseColor("#6A6A6A"));
            value.setTextSize(14);
            valueBox.addView(value);

            // 字号箭头：开口朝下（Figma 3082:59940），点击弹出字号浮层
            ImageView arrow = new ImageView(host.getContext());
            arrow.setImageResource(R.drawable.lolib_ic_chevron_down);
            arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            arrow.setContentDescription("选择字号");
            int arrowSize = host.dpToPx(20);
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(arrowSize, arrowSize);
            arrowLp.setMarginStart(host.dpToPx(6));
            valueBox.addView(arrow, arrowLp);

            valueBox.setOnClickListener(v -> showFontSizePopup(value, valueBox));
            card.addView(valueBox);
        } else {
            // Figma字体: icon + current font name (e.g. "宋体") + arrow
            TextView fontName = new TextView(host.getContext());
            fontName.setText(currentValue);
            fontName.setTextColor(COLOR_TAB_INACTIVE_TEXT);
            fontName.setTextSize(16);
            LinearLayout.LayoutParams fontLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            fontLp.setMarginStart(host.dpToPx(16));
            card.addView(fontName, fontLp);

            TextView arrow = new TextView(host.getContext());
            arrow.setText("›");
            arrow.setTextColor(COLOR_TAB_UNSELECTED_TEXT);
            arrow.setTextSize(18);
            arrow.setPadding(host.dpToPx(8), 0, 0, 0);
            card.addView(arrow);

            card.setOnClickListener(v -> showPickerDialog(item, fontName));
        }
        return card;
    }

    private View createActionRow(FunctionItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        row.setPadding(host.dpToPx(16), host.dpToPx(14), host.dpToPx(16), host.dpToPx(14));
        row.setMinimumHeight(host.dpToPx(56));

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setLayoutParams(new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
            row.addView(icon);
        }

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TAB_INACTIVE_TEXT);
        label.setTextSize(16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMarginStart(host.dpToPx(14));
        row.addView(label, labelLp);

        row.setOnClickListener(v -> runItemAction(item));
        return row;
    }

    private View createToggleRow(FunctionItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        row.setPadding(host.dpToPx(16), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        row.setMinimumHeight(host.dpToPx(52));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = host.dpToPx(10);
        row.setLayoutParams(rowLp);

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
        }

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TAB_INACTIVE_TEXT);
        label.setTextSize(16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(host.dpToPx(item.iconResId != 0 ? 16 : 0));
        row.addView(label, labelLp);

        SwitchCompat toggle = new SwitchCompat(host.getContext());
        boolean initial = toggleStates.getOrDefault(item.id, item.defaultToggleOn);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleStates.put(item.id, isChecked);
            dispatchToggle(item, isChecked, buttonView);
        });
        row.addView(toggle);
        return row;
    }

    private View createWatermarkRow(FunctionItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        row.setPadding(host.dpToPx(16), host.dpToPx(12), host.dpToPx(8), host.dpToPx(12));
        row.setMinimumHeight(host.dpToPx(56));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        if (item.iconResId != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(item.iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
        }

        TextView label = new TextView(host.getContext());
        label.setText(item.label);
        label.setTextColor(COLOR_TAB_INACTIVE_TEXT);
        label.setTextSize(16);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(host.dpToPx(item.iconResId != 0 ? 16 : 0));
        row.addView(label, labelLp);

        SwitchCompat toggle = new SwitchCompat(host.getContext());
        boolean initial = toggleStates.getOrDefault(item.id, item.defaultToggleOn);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleStates.put(item.id, isChecked);
            dispatchWatermarkToggle(isChecked, buttonView);
        });
        watermarkToggleView = toggle;
        row.addView(toggle);

        ImageView chevron = new ImageView(host.getContext());
        chevron.setImageResource(R.drawable.lolib_ic_chevron_right);
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        chevron.setContentDescription("水印设置");
        int chevronSize = host.dpToPx(32);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(chevronSize, chevronSize);
        chevronLp.setMarginStart(host.dpToPx(4));
        row.addView(chevron, chevronLp);
        chevron.setOnClickListener(v -> showWatermarkSettingsPage());

        return row;
    }

    private View createGridActions(List<FunctionItem> actions) {
        // Figma-style: 3-column grid of large cards (icon 24dp + label 14sp), h ~84dp
        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int cols = 3;
        for (int rowStart = 0; rowStart < actions.size(); rowStart += cols) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = rowStart; i < Math.min(rowStart + cols, actions.size()); i++) {
                FunctionItem action = actions.get(i);
                LinearLayout cell = new LinearLayout(host.getContext());
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
                cell.setPadding(host.dpToPx(8), host.dpToPx(14), host.dpToPx(8), host.dpToPx(14));
                cell.setMinimumHeight(host.dpToPx(80));

                ImageView icon = new ImageView(host.getContext());
                icon.setImageResource(action.iconResId);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        host.dpToPx(24), host.dpToPx(24));
                cell.addView(icon, iconLp);

                TextView label = new TextView(host.getContext());
                label.setText(action.label);
                label.setTextColor(COLOR_SECTION_TITLE);
                label.setTextSize(14);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, host.dpToPx(8), 0, 0);
                cell.addView(label);

                // 显式固定高度（不再依赖 minHeight），保证卡片接近方形，避免内容不足时高度塌缩成宽扁长方形
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, host.dpToPx(84), 1f);
                lp.setMarginEnd(host.dpToPx(
                        i < Math.min(rowStart + cols, actions.size()) - 1 ? 12 : 0));
                lp.bottomMargin = host.dpToPx(10);
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> runItemAction(action));
                row.addView(cell);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return grid;
    }

    private void showPickerDialog(FunctionItem item, TextView valueView) {
        if ("font_name".equals(item.id)) {
            showFontPickerDialog(valueView);
            return;
        }
        if ("style_picker".equals(item.id)) {
            showStylePickerPage(valueView);
            return;
        }
        if ("page_margins".equals(item.id)) {
            showPageMarginsPickerPage(valueView);
            return;
        }
        if ("paper_size".equals(item.id)) {
            showPaperSizePickerPage(valueView);
            return;
        }
        if ("paper_orientation".equals(item.id)) {
            showPaperOrientationPickerPage(valueView);
            return;
        }
        if (item.pickerOptions == null || item.pickerOptions.length == 0) {
            host.toastTodo(item.label + " 后续接入");
            return;
        }
        showOptionPickerPage(item.label, item.pickerOptions,
                item.pickerValues != null ? item.pickerValues : item.pickerOptions,
                item.id, valueView, SHEET_HEIGHT_RATIO);
    }

    // === 字号浮层（Figma 3082:60036：320×460 圆角卡片，锚定在字号框下方，不用二级页） ===

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
        text.setTextColor(selected ? COLOR_TAB_ACTIVE_TEXT : COLOR_TAB_INACTIVE_TEXT);
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
            applyPickerValue("font_size", value);
        });
        return row;
    }

    private void dismissFontSizePopup() {
        if (fontSizePopup != null) {
            fontSizePopup.dismiss();
            fontSizePopup = null;
        }
    }

    private void showFontPickerDialog(TextView valueView) {
        Runnable openSheet = () -> showFontPickerSheet(valueView);
        if (cachedFontOptions != null && cachedFontOptions.length > FALLBACK_FONT_OPTIONS.length) {
            openSheet.run();
            return;
        }
        host.fetchFontList((labels, values) -> {
            if (labels != null && !labels.isEmpty()) {
                cachedFontOptions = labels.toArray(new String[0]);
                cachedFontValues = values != null && !values.isEmpty()
                        ? values.toArray(new String[0]) : cachedFontOptions;
            }
            openSheet.run();
        });
    }

    private void showFontPickerSheet(TextView valueView) {
        if (fontPickerPanel == null || dialog == null) {
            return;
        }
        ImageButton back = fontPickerPanel.findViewById(R.id.font_picker_back);
        LinearLayout list = fontPickerPanel.findViewById(R.id.font_picker_list);
        if (back != null) {
            back.setOnClickListener(v -> dismissFontPickerDialog());
        }
        populateFontPickerList(list, valueView);

        dismissOptionPicker();
        setTabChromeVisible(false);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
        fontPickerPanel.setVisibility(View.VISIBLE);
        fontPickerVisible = true;
        if (dialog != null && dialog.isShowing()) {
            fontPickerPanel.post(this::applyAdaptiveSheetHeight);
        }
    }

    private void populateFontPickerList(LinearLayout list, TextView valueView) {
        list.removeAllViews();
        String selectedLabel = pickerValues.get("font_name");
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < cachedFontOptions.length; i++) {
            final String label = cachedFontOptions[i];
            final String value = i < cachedFontValues.length ? cachedFontValues[i] : label;
            View row = inflater.inflate(R.layout.lolib_item_font_picker_row, list, false);
            TextView name = row.findViewById(R.id.font_picker_item_name);
            ImageView check = row.findViewById(R.id.font_picker_item_check);
            name.setText(label);
            Typeface previewTypeface = resolveFontTypeface(label);
            if (previewTypeface != null) {
                name.setTypeface(previewTypeface);
            }
            boolean selected = label.equals(selectedLabel) || value.equals(selectedLabel);
            if (selected) {
                name.setTextColor(COLOR_TAB_ACTIVE_TEXT);
                check.setVisibility(View.VISIBLE);
            } else {
                name.setTextColor(COLOR_TAB_INACTIVE_TEXT);
                check.setVisibility(View.GONE);
            }
            row.setOnClickListener(v -> {
                pickerValues.put("font_name", label);
                valueView.setText(label);
                applyPickerValue("font_name", value);
                dismissFontPickerDialog();
            });
            list.addView(row);
            if (i < cachedFontOptions.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x14000000);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    /**
     * CO（LibreOffice core）提供的字体名 → Android assets 里打包的真实字体文件。
     * Android 端在 assets/unpack/user/fonts/ 下打包了 core 的部分字体（Liberation 系列、
     * Caladea、Carlito、Gentium、OpenSymbol），用 createFromAsset 加载，使字体选项行
     * 显示对应真实字形。匹配不到的（如中文 Noto CJK 未打包）再 fallback 系统家族。
     */
    private static final String[][] FONT_ASSET_MAP = new String[][]{
            {"Liberation Serif", "unpack/user/fonts/LiberationSerif-Regular.ttf"},
            {"Liberation Sans", "unpack/user/fonts/LiberationSans-Regular.ttf"},
            {"Liberation Mono", "unpack/user/fonts/LiberationMono-Regular.ttf"},
            {"Liberation Sans Narrow", "unpack/user/fonts/LiberationSansNarrow-Regular.ttf"},
            {"Caladea", "unpack/user/fonts/Caladea-Regular.ttf"},
            {"Carlito", "unpack/user/fonts/Carlito-Regular.ttf"},
            {"Gentium Basic", "unpack/user/fonts/GenBasR.ttf"},
            {"Gentium Book Basic", "unpack/user/fonts/GenBkBasR.ttf"},
            {"OpenSymbol", "unpack/user/fonts/opens___.ttf"},
    };

    /** 常见「Windows 字体名 / 替代字体」→ CO 打包字体名（.uno:CharFontName 可能返回这些）。 */
    private static final String[][] FONT_ALIAS_MAP = new String[][]{
            {"Times New Roman", "Liberation Serif"},
            {"Arial", "Liberation Sans"},
            {"Courier New", "Liberation Mono"},
            {"Calibri", "Carlito"},
            {"Cambria", "Caladea"},
    };

    /** 加载后缓存，避免重复 createFromAsset（该调用耗时）。 */
    private final Map<String, Typeface> fontTypefaceCache = new HashMap<>();

    private Typeface resolveFontTypeface(String fontName) {
        if (fontName == null) {
            return null;
        }
        Typeface cached = fontTypefaceCache.get(fontName);
        if (cached != null) {
            return cached;
        }
        Typeface tf = loadAssetFont(fontName);
        if (tf != null) {
            fontTypefaceCache.put(fontName, tf);
            return tf;
        }
        // 无打包字体文件时，按关键字给一个语义接近的系统 family（衬线/无衬线/等宽）
        tf = Typeface.create(fallbackSystemFamily(fontName), Typeface.NORMAL);
        fontTypefaceCache.put(fontName, tf);
        return tf;
    }

    private Typeface loadAssetFont(String fontName) {
        String target = resolveAssetFamily(fontName);
        if (target == null) {
            return null;
        }
        try {
            Typeface tf = Typeface.createFromAsset(host.getContext().getAssets(), target);
            if (tf != null) {
                return tf;
            }
        } catch (RuntimeException ignored) {
            // 字体文件缺失/损坏 → fallback
        }
        return null;
    }

    private String resolveAssetFamily(String fontName) {
        String n = fontName.trim();
        for (String[] pair : FONT_ASSET_MAP) {
            if (n.equalsIgnoreCase(pair[0])) {
                return pair[1];
            }
        }
        for (String[] pair : FONT_ALIAS_MAP) {
            if (n.equalsIgnoreCase(pair[0])) {
                for (String[] asset : FONT_ASSET_MAP) {
                    if (asset[0].equalsIgnoreCase(pair[1])) {
                        return asset[1];
                    }
                }
            }
        }
        return null;
    }

    private String fallbackSystemFamily(String fontName) {
        String n = fontName.toLowerCase(Locale.US);
        if (n.contains("mono") || n.contains("courier") || n.contains("consolas")
                || n.contains("code") || n.contains("等宽")) {
            return "monospace";
        }
        if (n.contains("serif") || n.contains("song") || n.contains("ming")
                || n.contains("宋") || n.contains("明") || n.contains("楷")
                || n.contains("kai") || n.contains("仿") || n.contains("fangsong")
                || n.contains("times") || n.contains("simsun") || n.contains("cambria")
                || n.contains("georgia") || n.contains("cjk") || n.contains("noto")) {
            return "serif";
        }
        return "sans-serif";
    }

    private void dismissFontPickerDialog() {
        if (!fontPickerVisible) {
            return;
        }
        fontPickerVisible = false;
        setTabChromeVisible(true);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
        if (fontPickerPanel != null) {
            fontPickerPanel.setVisibility(View.GONE);
        }
    }

    private void syncCurrentFormatting() {
        host.fetchCurrentFormatting((styleName, fontName, fontSizePt, paragraphAlignment,
                                    bold, italic, underline, strikethrough) -> {
            applyCurrentFormatting(styleName, fontName, fontSizePt, paragraphAlignment);
            if (dialog != null && dialog.isShowing() && selectedTabIndex == 0) {
                renderTabContent(tabs.get(selectedTabIndex));
            }
        });
    }

    private void applyCurrentFormatting(String styleName, String fontName, String fontSizePt,
            String paragraphAlignment) {
        if (styleName != null && !styleName.trim().isEmpty()) {
            currentStyleName = styleName.trim();
            pickerValues.put("style_picker", styleName.trim());
        }
        if (paragraphAlignment != null && !paragraphAlignment.trim().isEmpty()) {
            currentParagraphAlignment = paragraphAlignment.trim();
        }
        if (fontName != null && !fontName.trim().isEmpty()) {
            pickerValues.put("font_name", fontName.trim());
        }
        String fontSizeLabel = displayFontSize(fontSizePt);
        if (fontSizeLabel != null && !fontSizeLabel.trim().isEmpty()) {
            pickerValues.put("font_size", fontSizeLabel);
        }
        Log.i(TAG, "current_format style=" + currentStyleName
                + " font=" + pickerValues.get("font_name")
                + " size=" + pickerValues.get("font_size")
                + " align=" + currentParagraphAlignment);
    }

    private boolean isCurrentParagraphAlignment(String itemId) {
        if (currentParagraphAlignment == null || currentParagraphAlignment.isEmpty()) {
            return false;
        }
        return currentParagraphAlignment.equals(itemId);
    }

    private String displayFontSize(String fontSizePt) {
        if (fontSizePt == null || fontSizePt.trim().isEmpty()) {
            return "";
        }
        String normalized = fontSizePt.trim().replace("pt", "").replace("号", "").trim();
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            if (SIZE_VALUES[i].equals(normalized)) {
                return SIZE_OPTIONS[i];
            }
        }
        return normalized + " pt";
    }

    // === Shape picker — delegates to ImpressShapePickerController ===

    private void showShapePickerDialog() {
        dismiss();
        if (shapePicker == null) {
            shapePicker = new ImpressShapePickerController(new ImpressShapePickerController.Host() {
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
                    // After function panel dismisses, show the shape picker
                    action.run();
                }
            });
        }
        shapePicker.show();
    }

    // === Table picker — reuses ImpressInsertTablePickerController ===

    private void showTablePickerPage() {
        if (optionPickerVisible) {
            dismissOptionPicker();
        }
        dismissFontPickerDialog();
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
                    runAndDismiss(() -> host.executeUnoCommand(
                            ".uno:InsertTable?Columns=" + columns + "&Rows=" + rows));
                }

                @Override
                public void onBack() {
                    dismissTablePickerPage();
                }
            });
            // 文档主题色：插入按钮用 Writer 蓝，而非 Impress 橙
            tablePicker.setPrimaryButtonBackground(R.drawable.lolib_bg_writer_primary_button);
        }
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            contentContainer.addView(tablePicker.buildRootView());
        }
    }

    private void dismissTablePickerPage() {
        setTabChromeVisible(true);
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(selectedTabIndex));
        }
    }

    // === Comment picker — reuses ImpressCommentPickerController（native 输入框 → UNO 插入） ===

    private void showCommentPickerPage() {
        if (optionPickerVisible) {
            dismissOptionPicker();
        }
        dismissFontPickerDialog();
        setTabChromeVisible(false);
        commentPickerVisible = true;
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
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            contentContainer.addView(commentPicker.buildRootView());
        }
    }

    private void dismissCommentPickerPage() {
        if (!commentPickerVisible) {
            return;
        }
        commentPickerVisible = false;
        setTabChromeVisible(true);
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(selectedTabIndex));
        }
    }

    private void showChartTypePickerPage() {
        if (optionPickerVisible) {
            dismissOptionPicker();
        }
        dismissFontPickerDialog();
        dismissChartPickerPage();
        chartPickerVisible = true;
        setTabChromeVisible(false);

        if (contentContainer != null) {
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
        }
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
        title.setTextColor(COLOR_SECTION_TITLE);
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
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(selectedTabIndex));
        }
    }

    private void showWatermarkSettingsPage() {
        if (optionPickerVisible) {
            dismissOptionPicker();
        }
        dismissFontPickerDialog();
        setTabChromeVisible(false);
        ensureWatermarkFontDefault();
        watermarkPicker = new WatermarkSettingsController(
                new WatermarkSettingsController.Host() {
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
                        dismissWatermarkSettingsPage();
                    }

                    @Override
                    public void onConfirm(String text, String font, int angle, int transparency) {
                        watermarkText = text == null ? "" : text.trim();
                        watermarkFont = font == null || font.isEmpty() ? watermarkFont : font;
                        watermarkAngle = angle;
                        watermarkTransparency = transparency;
                        toggleStates.put("watermark", true);
                        if (watermarkToggleView != null) {
                            watermarkToggleView.setChecked(true);
                        }
                        applyCurrentWatermarkSettings();
                        dismissWatermarkSettingsPage();
                    }

                    @Override
                    public void pickFont(String currentFont, WatermarkSettingsController.FontPickCallback callback) {
                        showWatermarkFontPicker(currentFont, callback);
                    }

                    @Override
                    public android.graphics.Typeface resolveFontPreviewTypeface(String fontName) {
                        return resolveFontTypeface(fontName);
                    }
                },
                watermarkText, watermarkFont, watermarkAngle, watermarkTransparency);
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            contentContainer.addView(watermarkPicker.buildRootView());
        }
    }

    private void showWatermarkFontPicker(String currentFont,
            WatermarkSettingsController.FontPickCallback callback) {
        Runnable openSheet = () -> {
            if (fontPickerPanel == null || dialog == null) {
                return;
            }
            ImageButton back = fontPickerPanel.findViewById(R.id.font_picker_back);
            LinearLayout list = fontPickerPanel.findViewById(R.id.font_picker_list);
            if (back != null) {
                back.setOnClickListener(v -> {
                    dismissFontPickerDialog();
                    if (contentContainer != null && watermarkPicker != null) {
                        contentContainer.setVisibility(View.VISIBLE);
                        contentContainer.removeAllViews();
                        contentContainer.addView(watermarkPicker.buildRootView());
                    }
                    setTabChromeVisible(false);
                });
            }
            populateWatermarkFontList(list, currentFont, callback);
            if (contentContainer != null) {
                contentContainer.setVisibility(View.GONE);
            }
            fontPickerPanel.setVisibility(View.VISIBLE);
            fontPickerVisible = true;
        };
        if (cachedFontOptions != null && cachedFontOptions.length > FALLBACK_FONT_OPTIONS.length) {
            openSheet.run();
            return;
        }
        host.fetchFontList((labels, values) -> {
            if (labels != null && !labels.isEmpty()) {
                cachedFontOptions = labels.toArray(new String[0]);
                cachedFontValues = values != null && !values.isEmpty()
                        ? values.toArray(new String[0]) : cachedFontOptions;
            }
            openSheet.run();
        });
    }

    private void populateWatermarkFontList(LinearLayout list, String currentFont,
            WatermarkSettingsController.FontPickCallback callback) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < cachedFontOptions.length; i++) {
            final String label = cachedFontOptions[i];
            View row = inflater.inflate(R.layout.lolib_item_font_picker_row, list, false);
            TextView name = row.findViewById(R.id.font_picker_item_name);
            ImageView check = row.findViewById(R.id.font_picker_item_check);
            name.setText(label);
            Typeface previewTypeface = resolveFontTypeface(label);
            if (previewTypeface != null) {
                name.setTypeface(previewTypeface);
            }
            boolean selected = label.equals(currentFont);
            name.setTextColor(selected ? COLOR_TAB_ACTIVE_TEXT : COLOR_TAB_INACTIVE_TEXT);
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> {
                callback.onFontPicked(label);
                dismissFontPickerDialog();
                if (contentContainer != null && watermarkPicker != null) {
                    contentContainer.setVisibility(View.VISIBLE);
                    contentContainer.removeAllViews();
                    contentContainer.addView(watermarkPicker.buildRootView());
                }
                setTabChromeVisible(false);
            });
            list.addView(row);
            if (i < cachedFontOptions.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x14000000);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    private void dismissWatermarkSettingsPage() {
        setTabChromeVisible(true);
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(selectedTabIndex));
        }
    }

    private void showPageMarginsPickerPage(TextView valueView) {
        dismissPaperSizePickerPage();
        showOptionPickerPage("页边距", WriterLayoutCatalog.marginLabels(),
                WriterLayoutCatalog.marginIds(), "page_margins", valueView,
                SHEET_HEIGHT_RATIO);
    }

    private void showPaperOrientationPickerPage(TextView valueView) {
        dismissPaperSizePickerPage();
        showOptionPickerPage("纸张方向", ORIENTATION_OPTIONS, ORIENTATION_VALUES,
                "paper_orientation", valueView, SHEET_HEIGHT_RATIO);
    }

    private void showPaperSizePickerPage(TextView valueView) {
        if (optionPickerVisible) {
            dismissOptionPicker();
        }
        dismissFontPickerDialog();
        setTabChromeVisible(false);
        paperSizePicker = new PaperSizePickerController(
                new PaperSizePickerController.Host() {
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
                        dismissPaperSizePickerPage();
                    }

                    @Override
                    public void onPresetSelected(WriterLayoutCatalog.PaperSizeOption option) {
                        pickerValues.put("paper_size", option.label);
                        if (valueView != null) {
                            valueView.setText(option.label);
                        }
                        applyPaperFormat(option.paperFormat);
                        dismissPaperSizePickerPage();
                    }

                    @Override
                    public void onCustomSizeApplied(double widthCm, double heightCm) {
                        customPaperWidthCm = widthCm;
                        customPaperHeightCm = heightCm;
                        String label = PaperSizePickerController.formatCustomLabel(widthCm, heightCm);
                        pickerValues.put("paper_size", label);
                        if (valueView != null) {
                            valueView.setText(label);
                        }
                        applyCustomPaperSize(widthCm, heightCm);
                    }
                },
                pickerValues.getOrDefault("paper_size", "A4"),
                customPaperWidthCm, customPaperHeightCm);
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            contentContainer.setVisibility(View.VISIBLE);
            contentContainer.addView(paperSizePicker.buildRootView());
        }
        if (optionPickerPanel != null) {
            optionPickerPanel.setVisibility(View.GONE);
        }
    }

    private void dismissPaperSizePickerPage() {
        if (paperSizePicker == null) {
            return;
        }
        paperSizePicker = null;
        setTabChromeVisible(true);
        if (dialog != null && dialog.isShowing()) {
            renderTabContent(tabs.get(selectedTabIndex));
        }
    }

    private void ensureWatermarkFontDefault() {
        if (watermarkFont != null && !watermarkFont.isEmpty() && !"宋体".equals(watermarkFont)) {
            return;
        }
        String docFont = pickerValues.get("font_name");
        if (docFont != null && !docFont.isEmpty() && !"字体".equals(docFont)) {
            watermarkFont = docFont;
            return;
        }
        if (cachedFontOptions != null && cachedFontOptions.length > 0) {
            watermarkFont = cachedFontOptions[0];
        } else {
            watermarkFont = FALLBACK_FONT_OPTIONS[0];
        }
    }

    // === Generic option picker page — replaces AlertDialog ===

    /** 打开二级页框架（隐藏 tab chrome、显示 option picker 面板；sheet 高度保持不变）。 */
    private void openOptionPickerFrame(String title, @SuppressWarnings("unused") float heightRatio) {
        if (optionPickerPanel == null || dialog == null) {
            return;
        }
        dismissFontPickerDialog();
        dismissPaperSizePickerPage();
        setTabChromeVisible(false);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
        if (fontPickerPanel != null) {
            fontPickerPanel.setVisibility(View.GONE);
        }
        TextView titleView = optionPickerPanel.findViewById(R.id.option_picker_title);
        if (titleView != null) {
            titleView.setText(title);
        }
        ImageButton back = optionPickerPanel.findViewById(R.id.option_picker_back);
        if (back != null) {
            back.setOnClickListener(v -> dismissOptionPicker());
        }
        optionPickerPanel.setVisibility(View.VISIBLE);
        optionPickerVisible = true;
        if (dialog != null && dialog.isShowing()) {
            optionPickerPanel.post(this::applyAdaptiveSheetHeight);
        }
    }

    private void showOptionPickerPage(String title, String[] labels, String[] values,
            String pickerId, TextView valueView, float heightRatio) {
        openOptionPickerFrame(title, heightRatio);
        LinearLayout list = optionPickerPanel.findViewById(R.id.option_picker_list);
        populateOptionList(list, labels, values, pickerId, valueView);
    }

    private void populateOptionList(LinearLayout list, String[] labels, String[] values,
            String pickerId, TextView valueView) {
        list.removeAllViews();
        String selectedLabel = pickerValues.getOrDefault(pickerId,
                labels.length > 0 ? labels[0] : "");
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final String value = i < values.length ? values[i] : label;
            View row = inflater.inflate(R.layout.lolib_item_option_picker_row, list, false);
            TextView name = row.findViewById(R.id.option_picker_item_name);
            ImageView check = row.findViewById(R.id.option_picker_item_check);
            name.setText(label);
            boolean selected = label.equals(selectedLabel) || value.equals(selectedLabel);
            if (selected) {
                name.setTextColor(COLOR_TAB_ACTIVE_TEXT);
                check.setVisibility(View.VISIBLE);
            } else {
                name.setTextColor(COLOR_TAB_INACTIVE_TEXT);
                check.setVisibility(View.GONE);
            }
            row.setOnClickListener(v -> {
                pickerValues.put(pickerId, label);
                if (valueView != null) {
                    valueView.setText(label);
                }
                dismissOptionPicker();
                applyPickerValue(pickerId, value);
            });
            list.addView(row);
            if (i < labels.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x14000000);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    private void dismissOptionPicker() {
        if (!optionPickerVisible) {
            return;
        }
        optionPickerVisible = false;
        setTabChromeVisible(true);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
        if (optionPickerPanel != null) {
            optionPickerPanel.setVisibility(View.GONE);
        }
    }

    // === Style picker — fetches full Collabora style list, then opens option picker ===

    /** 样式二级页顺序（Figma 5252:56110 + 用户指定）：正文/列表/题注/索引/标题1/2/3/caption，其余 CO 选项追加在后。 */
    private static final String[][] STYLE_ORDER = new String[][]{
            {"正文", "Default Paragraph Style"},
            {"列表", "List"},
            {"题注", "Caption"},
            {"索引", "Index"},
            {"标题1", "Heading 1"},
            {"标题2", "Heading 2"},
            {"标题3", "Heading 3"},
            {"caption", "caption"},
    };

    private void showStylePickerPage(TextView valueView) {
        Runnable openSheet = () -> {
            openOptionPickerFrame("样式", SHEET_HEIGHT_RATIO);
            LinearLayout list = optionPickerPanel.findViewById(R.id.option_picker_list);
            populateStyleOptionList(list, valueView);
        };
        if (cachedStyleLabels != null && cachedStyleLabels.length > 0) {
            openSheet.run();
            return;
        }
        host.fetchStyleList((labels, values) -> {
            if (labels != null && !labels.isEmpty()) {
                reorderStyles(labels.toArray(new String[0]),
                        values != null && !values.isEmpty()
                                ? values.toArray(new String[0])
                                : labels.toArray(new String[0]));
            } else {
                cachedStyleLabels = new String[]{"正文", "列表", "题注", "索引", "标题1", "标题2", "标题3", "caption"};
                cachedStyleValues = new String[]{"Default Paragraph Style", "List", "Caption",
                        "Index", "Heading 1", "Heading 2", "Heading 3", "caption"};
            }
            openSheet.run();
        });
    }

    /** 按 STYLE_ORDER 重排样式，未匹配的 CO 其它样式按原序追加在后。 */
    private void reorderStyles(String[] labels, String[] values) {
        List<String> newLabels = new ArrayList<>();
        List<String> newValues = new ArrayList<>();
        boolean[] used = new boolean[values.length];
        for (String[] pair : STYLE_ORDER) {
            for (int i = 0; i < values.length; i++) {
                if (!used[i] && styleMatches(values[i], pair[1])) {
                    newLabels.add(pair[0]);
                    newValues.add(values[i]);
                    used[i] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < values.length; i++) {
            if (!used[i]) {
                newLabels.add(labels[i]);
                newValues.add(values[i]);
            }
        }
        cachedStyleLabels = newLabels.toArray(new String[0]);
        cachedStyleValues = newValues.toArray(new String[0]);
    }

    private boolean styleMatches(String styleId, String target) {
        if (styleId == null) {
            return false;
        }
        String s = styleId.trim();
        if (s.equalsIgnoreCase(target)) {
            return true;
        }
        String lower = s.toLowerCase(Locale.US);
        if ("list".equalsIgnoreCase(target) && lower.startsWith("list")) {
            return true;
        }
        if ("index".equalsIgnoreCase(target) && lower.startsWith("index")) {
            return true;
        }
        return false;
    }

    private void populateStyleOptionList(LinearLayout list, TextView valueView) {
        list.removeAllViews();
        String selectedLabel = pickerValues.getOrDefault("style_picker", "");
        String[] labels = cachedStyleLabels;
        String[] values = cachedStyleValues;
        if (labels == null || values == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final String styleId = i < values.length ? values[i] : label;
            View row = inflater.inflate(R.layout.lolib_item_option_picker_row, list, false);
            TextView name = row.findViewById(R.id.option_picker_item_name);
            ImageView check = row.findViewById(R.id.option_picker_item_check);
            name.setText(label);
            if (label.equals(selectedLabel) || styleId.equals(selectedLabel)) {
                name.setTextColor(COLOR_TAB_ACTIVE_TEXT);
                check.setVisibility(View.VISIBLE);
            } else {
                name.setTextColor(COLOR_TAB_INACTIVE_TEXT);
                check.setVisibility(View.GONE);
            }
            row.setOnClickListener(v -> {
                pickerValues.put("style_picker", label);
                valueView.setText(label);
                dismissOptionPicker();
                host.applyParagraphStyle(styleId);
            });
            list.addView(row);
            if (i < labels.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x14000000);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    private void setTabChromeVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        if (tabHeader != null) {
            tabHeader.setVisibility(v);
        }
        if (functionTabArea != null) {
            functionTabArea.setVisibility(v);
        }
    }

    private void applyPickerValue(String pickerId, String value) {
        switch (pickerId) {
            case "font_name":
                host.applyFont(value);
                break;
            case "font_size":
                host.applyFontSize(value);
                break;
            case "page_margins":
                applyPageMargins(value);
                break;
            case "paper_size":
                applyPaperSize(value);
                break;
            case "paper_orientation":
                if ("横向".equals(value)) {
                    host.executeUnoCommand(".uno:Orientation?isLandscape:bool=true");
                } else {
                    host.executeUnoCommand(".uno:Orientation?isLandscape:bool=false");
                }
                break;
            default:
                host.toastTodo("暂未接入：" + pickerId);
                break;
        }
    }

    private void dispatchToggle(FunctionItem item, boolean enabled, CompoundButton buttonView) {
        switch (item.id) {
            case "track_changes":
                host.executeUnoCommand(enabled ? ".uno:TrackChangesInAllViews" : ".uno:TrackChanges?TrackChanges:bool=false");
                break;
            case "show_changes":
                host.executeUnoCommand(".uno:ShowTrackedChanges");
                break;
            default:
                if (item.unoCommand != null && !item.unoCommand.isEmpty()) {
                    host.executeUnoCommand(item.unoCommand);
                }
                break;
        }
    }

    private void dispatchWatermarkToggle(boolean enabled, CompoundButton buttonView) {
        if (enabled) {
            applyCurrentWatermarkSettings();
        } else {
            host.applyWatermark("", watermarkFont, watermarkAngle, watermarkTransparency);
        }
    }

    private void applyCurrentWatermarkSettings() {
        host.applyWatermark(watermarkText, resolveWatermarkFontValue(watermarkFont),
                watermarkAngle, watermarkTransparency);
    }

    private String resolveWatermarkFontValue(String label) {
        if (label == null || label.isEmpty()) {
            return FALLBACK_FONT_OPTIONS[0];
        }
        if (cachedFontOptions != null && cachedFontValues != null) {
            for (int i = 0; i < cachedFontOptions.length; i++) {
                if (label.equals(cachedFontOptions[i])) {
                    return i < cachedFontValues.length ? cachedFontValues[i] : label;
                }
            }
        }
        return label;
    }

    private void applyPageMargins(String value) {
        WriterLayoutCatalog.MarginOption option = WriterLayoutCatalog.findMarginByLabel(value);
        host.executeUnoCommand(".uno:PageLRMargin?Page.Left:long=" + option.leftHmm
                + "&Page.Right:long=" + option.rightHmm);
        host.executeUnoCommand(".uno:PageULMargin?Page.Upper:long=" + option.topHmm
                + "&Page.Lower:long=" + option.bottomHmm);
    }

    private void applyPaperSize(String value) {
        WriterLayoutCatalog.PaperSizeOption option = WriterLayoutCatalog.findPaperByLabel(value);
        if (option.label.equals(value) || option.id.equals(value)) {
            applyPaperFormat(option.paperFormat);
            return;
        }
        applyCustomPaperSize(customPaperWidthCm, customPaperHeightCm);
    }

    private void applyPaperFormat(int paperFormat) {
        host.executeUnoCommand(".uno:AttributePageSize?PaperFormat:short=" + paperFormat);
    }

    private void applyCustomPaperSize(double widthCm, double heightCm) {
        int widthHmm = PaperSizePickerController.cmToHmm(widthCm);
        int heightHmm = PaperSizePickerController.cmToHmm(heightCm);
        host.executeUnoCommand(".uno:AttributePageSize?AttributePageSize.Width:long="
                + widthHmm + "&AttributePageSize.Height:long=" + heightHmm);
    }

    private void runItemAction(FunctionItem item) {
        if ("insert_shape".equals(item.id)) {
            showShapePickerDialog();
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
        if ("insert_chart".equals(item.id)) {
            showChartTypePickerPage();
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

    private void runAndDismiss(Runnable action) {
        dismiss();
        action.run();
    }

    private List<FunctionTab> buildTabs() {
        List<FunctionTab> result = new ArrayList<>();

        List<FunctionItem> commonItems = new ArrayList<>();
        commonItems.add(new FunctionItem(ItemType.SECTION, "section_style", "样式"));
        commonItems.add(new FunctionItem(ItemType.PICKER, "style_picker", "样式", "正文",
                R.drawable.lolib_ic_style_body, "", null, null, null, false));
        commonItems.add(new FunctionItem(ItemType.SECTION, "section_font", "字体"));
        commonItems.add(new FunctionItem(ItemType.PICKER, "font_name", "字体", "字体",
                R.drawable.lolib_ic_picker_font, "", null, null, null, false));
        commonItems.add(new FunctionItem(ItemType.SECTION, "section_size", "字号"));
        commonItems.add(new FunctionItem(ItemType.PICKER, "font_size", "字号", "4号",
                R.drawable.lolib_ic_picker_font_size, "", null, SIZE_OPTIONS, SIZE_VALUES, false));
        commonItems.add(new FunctionItem(ItemType.SECTION, "section_para", "段落"));
        commonItems.add(new FunctionItem(ItemType.PARAGRAPH_CHIP, "para_left", "左对齐",
                R.drawable.lolib_ic_quick_align_left, ".uno:LeftPara"));
        commonItems.add(new FunctionItem(ItemType.PARAGRAPH_CHIP, "para_center", "居中对齐",
                R.drawable.lolib_ic_quick_align_center, ".uno:CenterPara"));
        commonItems.add(new FunctionItem(ItemType.PARAGRAPH_CHIP, "para_right", "右对齐",
                R.drawable.lolib_ic_quick_align_right, ".uno:RightPara"));
        commonItems.add(new FunctionItem(ItemType.PARAGRAPH_CHIP, "para_justify", "两端对齐",
                R.drawable.lolib_ic_quick_align_justify, ".uno:JustifyPara"));
        commonItems.add(new FunctionItem(ItemType.PARAGRAPH_CHIP, "para_bullet", "项目符号",
                R.drawable.lolib_ic_quick_bullet, ".uno:DefaultBullet"));
        commonItems.add(new FunctionItem(ItemType.PARAGRAPH_CHIP, "para_number", "编号",
                R.drawable.lolib_ic_quick_numbering, ".uno:DefaultNumbering"));
        result.add(new FunctionTab("common", "常用", commonItems));

        List<FunctionItem> fileItems = new ArrayList<>();
        fileItems.add(new FunctionItem(ItemType.ACTION, "file_save", "保存",
                R.drawable.lolib_ic_function_save, host::saveDocument));
        fileItems.add(new FunctionItem(ItemType.ACTION, "file_save_as", "另存为",
                R.drawable.lolib_ic_function_download, host::saveDocumentAs));
        fileItems.add(new FunctionItem(ItemType.ACTION, "file_export", "导出为",
                R.drawable.lolib_ic_function_download, host::exportDocumentAsPdf));
        fileItems.add(new FunctionItem(ItemType.ACTION, "file_print", "打印",
                R.drawable.lolib_ic_function_print, host::initiatePrint));
        result.add(new FunctionTab("file", "文件", fileItems));

        List<FunctionItem> insertItems = new ArrayList<>();
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_image", "图片",
                R.drawable.lolib_ic_insert_image, host::openLocalImagePickerFromWeb));
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_chart", "图表",
                R.drawable.lolib_ic_calc_insert_chart, ""));
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_table", "表格",
                R.drawable.lolib_ic_insert_table, ".uno:InsertTable?Columns=2&Rows=2"));
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_shape", "形状",
                R.drawable.lolib_ic_insert_shape, ""));
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_comment", "批注",
                R.drawable.lolib_ic_insert_comment, host::insertComment));
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_page_number", "页码",
                R.drawable.lolib_ic_insert_page_number, ".uno:InsertPageNumberField"));
        insertItems.add(new FunctionItem(ItemType.GRID_ACTION, "insert_page_break", "分页符",
                R.drawable.lolib_ic_insert_page_break, ".uno:InsertPagebreak"));
        result.add(new FunctionTab("insert", "插入", insertItems));

        List<FunctionItem> layoutItems = new ArrayList<>();
        layoutItems.add(new FunctionItem(ItemType.WATERMARK, "watermark", "水印", "",
                R.drawable.lolib_ic_layout_watermark, ".uno:Watermark",
                null, null, null, false));
        layoutItems.add(new FunctionItem(ItemType.PICKER, "page_margins", "页边距", WriterLayoutCatalog.MARGINS[0].label,
                R.drawable.lolib_ic_layout_page_margins, "", null,
                WriterLayoutCatalog.marginLabels(), WriterLayoutCatalog.marginIds(), false));
        layoutItems.add(new FunctionItem(ItemType.PICKER, "paper_size", "纸张大小", "A4",
                R.drawable.lolib_ic_layout_paper_size, "", null,
                null, null, false));
        layoutItems.add(new FunctionItem(ItemType.PICKER, "paper_orientation", "纸张方向", "纵向",
                R.drawable.lolib_ic_layout_paper_orientation, "", null,
                ORIENTATION_OPTIONS, ORIENTATION_VALUES, false));
        result.add(new FunctionTab("layout", "布局", layoutItems));

        List<FunctionItem> reviewItems = new ArrayList<>();
        reviewItems.add(new FunctionItem(ItemType.ACTION, "spell_check", "拼写检查",
                R.drawable.lolib_ic_calc_spell_check, ".uno:SpellDialog"));
        reviewItems.add(new FunctionItem(ItemType.TOGGLE, "track_changes", "追踪修订", "",
                R.drawable.lolib_ic_review_track_changes, ".uno:TrackChanges",
                null, null, null, false));
        reviewItems.add(new FunctionItem(ItemType.TOGGLE, "show_changes", "显示修订", "",
                R.drawable.lolib_ic_review_show_changes, ".uno:ShowTrackedChanges",
                null, null, null, true));
        reviewItems.add(new FunctionItem(ItemType.ACTION, "accept_change", "接收修订",
                android.R.drawable.ic_menu_send, ".uno:AcceptTrackedChange"));
        reviewItems.add(new FunctionItem(ItemType.ACTION, "reject_change", "拒绝修订",
                android.R.drawable.ic_menu_close_clear_cancel, ".uno:RejectTrackedChange"));
        result.add(new FunctionTab("review", "审阅", reviewItems));

        return result;
    }

    private static final String[] FALLBACK_FONT_OPTIONS = new String[] {
            "Liberation Serif", "Liberation Sans", "Liberation Mono", "Arial", "Times New Roman"
    };
    private static final String[] FALLBACK_FONT_VALUES = FALLBACK_FONT_OPTIONS;

    private static final String[] SHAPE_LABELS = new String[] {
            "矩形", "椭圆", "圆角矩形", "等腰三角形", "直线", "箭头"
    };
    private static final String[] SHAPE_COMMANDS = new String[] {
            ".uno:BasicShapes.rectangle",
            ".uno:BasicShapes.ellipse",
            ".uno:BasicShapes.round-rectangle",
            ".uno:BasicShapes.isosceles-triangle",
            ".uno:BasicShapes.line",
            ".uno:BasicShapes.arrow"
    };

    private static final String[] SIZE_OPTIONS = new String[] {
            "初号", "小初", "一号", "小一", "二号", "小二", "三号", "小三", "四号", "小四", "五号", "小五"
    };
    private static final String[] SIZE_VALUES = new String[] {
            "42", "36", "26", "24", "22", "18", "16", "15", "14", "12", "10.5", "9"
    };

    private static final String[] ORIENTATION_OPTIONS = new String[] { "纵向", "横向" };
    private static final String[] ORIENTATION_VALUES = new String[] { "portrait", "landscape" };
}
