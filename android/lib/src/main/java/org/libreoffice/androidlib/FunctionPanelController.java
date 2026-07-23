package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    /** Figma 750×1624 canvas: sheet height 1066px ≈ 65.6% screen. */
    private static final float FONT_PICKER_HEIGHT_RATIO = 1066f / 1624f;

    public interface StringListCallback {
        void onResult(List<String> labels, List<String> values);
    }

    public interface FormattingCallback {
        void onResult(String styleName, String fontName, String fontSizePt, String paragraphAlignment);
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

        void showWatermarkDialog(boolean enabled);

        void applyParagraphStyle(String styleName);

        void applyFont(String fontName);

        void applyFontSize(String fontSizePt);

        void insertComment();

        void fetchStyleList(StringListCallback callback);

        void fetchFontList(StringListCallback callback);

        void fetchCurrentFormatting(FormattingCallback callback);

        void showAiOperationSheet();

        void focusDocumentAndShowIme();
    }

    private enum ItemType {
        SECTION,
        STYLE_SECTION,
        STYLE_CHIP,
        PICKER,
        PARAGRAPH_CHIP,
        ACTION,
        GRID_ACTION,
        TOGGLE
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
    private View sheetContentRoot;
    private boolean fontPickerVisible;
    private final List<TextView> tabViews = new ArrayList<>();
    private View tabIndicator;
    private final List<FunctionTab> tabs;
    private int selectedTabIndex = 0;
    private final Map<String, String> pickerValues = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private String[] cachedFontOptions = FALLBACK_FONT_OPTIONS;
    private String[] cachedFontValues = FALLBACK_FONT_VALUES;
    private String currentStyleName = "";
    private String currentParagraphAlignment = "";

    public FunctionPanelController(Host host) {
        this.host = host;
        this.tabs = buildTabs();
        pickerValues.put("font_name", "字体");
        pickerValues.put("font_size", "4号");
        pickerValues.put("page_margins", "默认");
        pickerValues.put("paper_size", "A4");
        pickerValues.put("paper_orientation", "纵向");
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
        dialog.show();
        expandSheet();
    }

    public void dismiss() {
        dismissFontPickerDialog();
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
        updateTabIndicator(index);
        renderTabContent(tabs.get(index));
        if (dialog != null) {
            expandSheet();
        }
    }

    private void updateTabIndicator(int index) {
        if (tabIndicator == null || index < 0 || index >= tabViews.size()) return;
        tabIndicator.setVisibility(View.VISIBLE);
        TextView selectedTab = tabViews.get(index);
        selectedTab.post(() -> {
            if (tabIndicator == null || selectedTab.getWidth() == 0) return;
            // Calculate tab center relative to the FrameLayout parent
            int[] tabLoc = new int[2];
            int[] parentLoc = new int[2];
            selectedTab.getLocationInWindow(tabLoc);
            View parent = (View) tabIndicator.getParent();
            if (parent != null) {
                parent.getLocationInWindow(parentLoc);
                float tabCenterX = tabLoc[0] - parentLoc[0] + selectedTab.getWidth() / 2f;
                int indicatorW = host.dpToPx(24);
                int leftMargin = (int) (tabCenterX - indicatorW / 2f);
                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) tabIndicator.getLayoutParams();
                lp.leftMargin = Math.max(0, leftMargin);
                lp.width = indicatorW;
                tabIndicator.setLayoutParams(lp);
            }
        });
    }

    private void renderTabContent(FunctionTab tab) {
        contentContainer.removeAllViews();
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        List<FunctionItem> pendingStyleChips = new ArrayList<>();
        List<FunctionItem> pendingParagraphChips = new ArrayList<>();
        List<FunctionItem> pendingGridActions = new ArrayList<>();

        for (FunctionItem item : tab.items) {
            switch (item.type) {
                case SECTION:
                    flushStyleChips(root, pendingStyleChips);
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    root.addView(createSectionHeader(item.label));
                    break;
                case STYLE_SECTION:
                    flushStyleChips(root, pendingStyleChips);
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    root.addView(createSectionHeader(item.label));
                    root.addView(createDynamicStylePlaceholder());
                    break;
                case STYLE_CHIP:
                    pendingStyleChips.add(item);
                    break;
                case PARAGRAPH_CHIP:
                    pendingParagraphChips.add(item);
                    break;
                case GRID_ACTION:
                    pendingGridActions.add(item);
                    break;
                case PICKER:
                    flushStyleChips(root, pendingStyleChips);
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    root.addView(createPickerRow(item));
                    break;
                case ACTION:
                    flushStyleChips(root, pendingStyleChips);
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    root.addView(createActionRow(item));
                    root.addView(createDivider());
                    break;
                case TOGGLE:
                    flushStyleChips(root, pendingStyleChips);
                    flushParagraphChips(root, pendingParagraphChips);
                    flushGridActions(root, pendingGridActions);
                    root.addView(createToggleRow(item));
                    break;
                default:
                    break;
            }
        }
        flushStyleChips(root, pendingStyleChips);
        flushParagraphChips(root, pendingParagraphChips);
        flushGridActions(root, pendingGridActions);

        contentContainer.addView(root);
    }

    private void flushStyleChips(LinearLayout root, List<FunctionItem> chips) {
        if (!chips.isEmpty()) {
            root.addView(createStyleChipRow(chips));
            chips.clear();
        }
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

    private View createDynamicStylePlaceholder() {
        FrameLayout container = new FrameLayout(host.getContext());
        container.setTag("dynamic_style_container");
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        populateDynamicStyleContainer(container, buildStyleLabels(), buildStyleValues());
        return container;
    }

    private List<String> buildStyleLabels() {
        List<String> labels = new ArrayList<>();
        // Figma: clean 3-item layout, no "当前" prefix
        for (String fallback : FALLBACK_STYLE_LABELS) {
            labels.add(fallback);
        }
        return labels;
    }

    private List<String> buildStyleValues() {
        List<String> values = new ArrayList<>();
        for (String fallback : FALLBACK_STYLE_VALUES) {
            values.add(fallback);
        }
        return values;
    }

    private void populateDynamicStyleContainer(FrameLayout container, List<String> labels, List<String> values) {
        container.removeAllViews();
        if (labels == null || values == null || labels.isEmpty()) {
            labels = new ArrayList<>();
            values = new ArrayList<>();
            for (int i = 0; i < FALLBACK_STYLE_LABELS.length; i++) {
                labels.add(FALLBACK_STYLE_LABELS[i]);
                values.add(FALLBACK_STYLE_VALUES[i]);
            }
        }
        container.addView(createHorizontalStyleChipRow(labels, values));
    }

    private View createHorizontalStyleChipRow(List<String> labels, List<String> styleIds) {
        // Legacy path: no icons (used by dynamic style placeholder, no longer primary path)
        List<Integer> emptyIcons = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) emptyIcons.add(0);
        return createStyleCardGrid(labels, styleIds, emptyIcons);
    }

    private View createStyleChipRow(List<FunctionItem> chips) {
        List<String> labels = new ArrayList<>();
        List<String> styleIds = new ArrayList<>();
        List<Integer> iconIds = new ArrayList<>();
        for (FunctionItem chip : chips) {
            labels.add(chip.label);
            styleIds.add(chip.unoCommand);
            iconIds.add(chip.iconResId);
        }
        return createStyleCardGrid(labels, styleIds, iconIds);
    }

    /**
     * Creates a 3-column card grid matching Figma style cards.
     * Cards: bg #F2F3F5, no border, rounded 12dp, icon 24dp + label 14sp.
     */
    private View createStyleCardGrid(List<String> labels, List<String> styleIds,
                                      List<Integer> iconIds) {
        LinearLayout grid = new LinearLayout(host.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int cols = 3;
        for (int rowStart = 0; rowStart < labels.size(); rowStart += cols) {
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = rowStart; i < Math.min(rowStart + cols, labels.size()); i++) {
                final String label = labels.get(i);
                final String styleId = i < styleIds.size() ? styleIds.get(i) : label;
                final int iconRes = i < iconIds.size() ? iconIds.get(i) : 0;

                LinearLayout card = new LinearLayout(host.getContext());
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
                card.setPadding(host.dpToPx(10), host.dpToPx(18), host.dpToPx(10), host.dpToPx(16));
                card.setMinimumHeight(host.dpToPx(80));

                // Icon (24dp, matching Figma 48px at ~2x scale)
                if (iconRes != 0) {
                    ImageView icon = new ImageView(host.getContext());
                    icon.setImageResource(iconRes);
                    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                            host.dpToPx(24), host.dpToPx(24));
                    card.addView(icon, iconLp);
                }

                TextView text = new TextView(host.getContext());
                text.setText(label);
                text.setGravity(Gravity.CENTER);
                text.setTextColor(COLOR_SECTION_TITLE);
                text.setTextSize(14);
                text.setPadding(0, iconRes != 0 ? host.dpToPx(8) : 0, 0, 0);
                card.addView(text);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                int lastInRow = Math.min(rowStart + cols, labels.size()) - 1;
                lp.setMarginEnd(host.dpToPx(i < lastInRow ? 12 : 0));
                card.setLayoutParams(lp);
                card.setOnClickListener(v -> runAndDismiss(() -> host.applyParagraphStyle(styleId)));
                row.addView(card);
            }
            grid.addView(row);
        }
        return grid;
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

            TextView arrow = new TextView(host.getContext());
            arrow.setText("›");
            arrow.setTextColor(COLOR_TAB_UNSELECTED_TEXT);
            arrow.setTextSize(18);
            arrow.setPadding(host.dpToPx(4), 0, 0, 0);
            valueBox.addView(arrow);

            valueBox.setOnClickListener(v -> showPickerDialog(item, value));
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

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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
        if (item.pickerOptions == null || item.pickerOptions.length == 0) {
            host.toastTodo(item.label + " 后续接入");
            return;
        }
        new AlertDialog.Builder(host.getContext())
                .setTitle(item.label)
                .setItems(item.pickerOptions, (dialog, which) -> {
                    String label = item.pickerOptions[which];
                    String value = item.pickerValues != null && which < item.pickerValues.length
                            ? item.pickerValues[which] : label;
                    pickerValues.put(item.id, label);
                    valueView.setText(label);
                    applyPickerValue(item.id, value);
                })
                .setNegativeButton("取消", null)
                .show();
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

        if (tabHeader != null) {
            tabHeader.setVisibility(View.GONE);
        }
        if (functionTabArea != null) {
            functionTabArea.setVisibility(View.GONE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
        fontPickerPanel.setVisibility(View.VISIBLE);
        fontPickerVisible = true;
        setSheetContentHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        expandSheet(FONT_PICKER_HEIGHT_RATIO);
    }

    private void setSheetContentHeight(int height) {
        if (sheetContentRoot == null) {
            return;
        }
        ViewGroup.LayoutParams params = sheetContentRoot.getLayoutParams();
        if (params != null) {
            params.height = height;
            sheetContentRoot.setLayoutParams(params);
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
            Typeface previewTypeface = Typeface.create(label, Typeface.NORMAL);
            if (previewTypeface != null) {
                name.setTypeface(previewTypeface);
            }
            boolean selected = label.equals(selectedLabel) || value.equals(selectedLabel);
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> {
                pickerValues.put("font_name", label);
                valueView.setText(label);
                applyPickerValue("font_name", value);
                dismissFontPickerDialog();
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

    private void dismissFontPickerDialog() {
        if (!fontPickerVisible) {
            return;
        }
        fontPickerVisible = false;
        if (tabHeader != null) {
            tabHeader.setVisibility(View.VISIBLE);
        }
        if (functionTabArea != null) {
            functionTabArea.setVisibility(View.VISIBLE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
        if (fontPickerPanel != null) {
            fontPickerPanel.setVisibility(View.GONE);
        }
        setSheetContentHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        expandSheet(0f);
    }

    private void syncCurrentFormatting() {
        host.fetchCurrentFormatting((styleName, fontName, fontSizePt, paragraphAlignment) -> {
            applyCurrentFormatting(styleName, fontName, fontSizePt, paragraphAlignment);
            if (dialog != null && dialog.isShowing() && selectedTabIndex == 0) {
                renderTabContent(tabs.get(selectedTabIndex));
                expandSheet();
            }
        });
    }

    private void applyCurrentFormatting(String styleName, String fontName, String fontSizePt,
            String paragraphAlignment) {
        if (styleName != null && !styleName.trim().isEmpty()) {
            currentStyleName = styleName.trim();
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

    private void showShapePickerDialog() {
        final String[] labels = new String[SHAPE_LABELS.length];
        System.arraycopy(SHAPE_LABELS, 0, labels, 0, SHAPE_LABELS.length);
        new AlertDialog.Builder(host.getContext())
                .setTitle("插入形状")
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < SHAPE_COMMANDS.length) {
                        runAndDismiss(() -> host.executeUnoCommand(SHAPE_COMMANDS[which]));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
        if ("watermark".equals(item.id)) {
            host.showWatermarkDialog(enabled);
            return;
        }
        switch (item.id) {
            case "watermark":
                if (enabled) {
                    host.toastTodo("水印设置需要对话框，移动端后续接入");
                    toggleStates.put(item.id, false);
                    if (buttonView != null) {
                        buttonView.setChecked(false);
                    }
                } else {
                    host.toastTodo("关闭水印后续接入");
                }
                break;
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

    private void applyPageMargins(String value) {
        int left = 2000;
        int right = 2000;
        int top = 2000;
        int bottom = 2000;
        if ("narrow".equals(value)) {
            left = right = top = bottom = 1270;
        } else if ("moderate".equals(value)) {
            left = right = 1905;
            top = bottom = 2540;
        } else if ("wide".equals(value)) {
            left = right = 5080;
            top = bottom = 2540;
        }
        host.executeUnoCommand(".uno:PageLRMargin?Page.Left:long=" + left + "&Page.Right:long=" + right);
        host.executeUnoCommand(".uno:PageULMargin?Page.Upper:long=" + top + "&Page.Lower:long=" + bottom);
    }

    private void applyPaperSize(String value) {
        int paperFormat = 4; // A4
        if ("A3".equals(value)) {
            paperFormat = 3;
        } else if ("Letter".equals(value)) {
            paperFormat = 8;
        } else if ("Legal".equals(value)) {
            paperFormat = 9;
        }
        host.executeUnoCommand(".uno:AttributePageSize?PaperFormat:short=" + paperFormat);
    }

    private void runItemAction(FunctionItem item) {
        if ("insert_shape".equals(item.id)) {
            showShapePickerDialog();
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
        action.run();
    }

    private void expandSheet() {
        expandSheet(0f);
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
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        if (heightRatio > 0f) {
            int screenHeight = host.getContext().getResources().getDisplayMetrics().heightPixels;
            int targetHeight = Math.round(screenHeight * heightRatio);
            if (layoutParams != null) {
                layoutParams.height = targetHeight;
                bottomSheet.setLayoutParams(layoutParams);
            }
            bottomSheet.setBackgroundResource(R.drawable.lolib_bg_font_picker_sheet);
            bottomSheet.setElevation(host.dpToPx(28));
            behavior.setFitToContents(false);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(true);
            behavior.setDraggable(true);
            behavior.setPeekHeight(targetHeight, false);
            behavior.setMaxHeight(targetHeight);
        } else {
            if (layoutParams != null) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                bottomSheet.setLayoutParams(layoutParams);
            }
            bottomSheet.setBackgroundColor(Color.WHITE);
            bottomSheet.setElevation(0f);
            behavior.setFitToContents(true);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(false);
            behavior.setDraggable(false);
        }
        bottomSheet.post(() -> {
            if (heightRatio <= 0f) {
                behavior.setPeekHeight(bottomSheet.getHeight(), false);
            }
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            Log.i(TAG, "function_edit_sheet_expanded height=" + bottomSheet.getHeight()
                    + " ratio=" + heightRatio);
        });
    }

    private List<FunctionTab> buildTabs() {
        List<FunctionTab> result = new ArrayList<>();

        List<FunctionItem> commonItems = new ArrayList<>();
        commonItems.add(new FunctionItem(ItemType.SECTION, "section_style", "样式"));
        commonItems.add(new FunctionItem(ItemType.STYLE_CHIP, "style_title1", "标题1",
                R.drawable.lolib_ic_style_title1, "Heading 1"));
        commonItems.add(new FunctionItem(ItemType.STYLE_CHIP, "style_title2", "标题2",
                R.drawable.lolib_ic_style_title2, "Heading 2"));
        commonItems.add(new FunctionItem(ItemType.STYLE_CHIP, "style_body", "正文",
                R.drawable.lolib_ic_style_body, "Default Paragraph Style"));
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
        layoutItems.add(new FunctionItem(ItemType.TOGGLE, "watermark", "水印", "",
                R.drawable.lolib_ic_layout_watermark, ".uno:Watermark",
                null, null, null, false));
        layoutItems.add(new FunctionItem(ItemType.PICKER, "page_margins", "页边距", "默认",
                R.drawable.lolib_ic_layout_page_margins, "", null,
                MARGIN_OPTIONS, MARGIN_VALUES, false));
        layoutItems.add(new FunctionItem(ItemType.PICKER, "paper_size", "纸张大小", "A4",
                R.drawable.lolib_ic_layout_paper_size, "", null,
                PAPER_SIZE_OPTIONS, PAPER_SIZE_VALUES, false));
        layoutItems.add(new FunctionItem(ItemType.PICKER, "paper_orientation", "纸张方向", "纵向",
                R.drawable.lolib_ic_layout_paper_orientation, "", null,
                ORIENTATION_OPTIONS, ORIENTATION_VALUES, false));
        result.add(new FunctionTab("layout", "布局", layoutItems));

        List<FunctionItem> reviewItems = new ArrayList<>();
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

    private static final String[] FALLBACK_STYLE_LABELS = new String[] {
            "标题1", "标题2", "正文"
    };
    private static final String[] FALLBACK_STYLE_VALUES = new String[] {
            "Heading 1", "Heading 2", "Default Paragraph Style"
    };

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

    private static final String[] MARGIN_OPTIONS = new String[] { "默认", "窄", "适中", "宽" };
    private static final String[] MARGIN_VALUES = new String[] { "default", "narrow", "moderate", "wide" };

    private static final String[] PAPER_SIZE_OPTIONS = new String[] { "A4", "A3", "Letter", "Legal" };
    private static final String[] PAPER_SIZE_VALUES = new String[] { "A4", "A3", "Letter", "Legal" };

    private static final String[] ORIENTATION_OPTIONS = new String[] { "纵向", "横向" };
    private static final String[] ORIENTATION_VALUES = new String[] { "portrait", "landscape" };
}
