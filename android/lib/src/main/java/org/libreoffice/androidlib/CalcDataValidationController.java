package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import org.libreoffice.androidlib.R;

/**
 * Calc 数据有效性自定义 UI（条件 / 输入帮助 / 错误警告）。
 */
final class CalcDataValidationController {

    private static final int CONTENT_INDENT_DP = 16;

    private enum Tab {
        CRITERIA, INPUT_HELP, ERROR_ALERT
    }

    private enum Overlay {
        NONE, OPTION_PICKER, MACRO_PICKER
    }

    private enum OptionKind {
        ALLOW, DATA, ERROR_ACTION
    }

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void onBack();

        void applyValidation(CalcDataValidationState state);

        void openMacroChooser(CalcValidationMacroPickerController.MacroChooseCallback callback);

        /** 读当前选区已有有效性设置，异步回填 target 后回调 onLoaded。 */
        void loadCurrentValidationState(CalcDataValidationState target, Runnable onLoaded);

        void dismissCoValidationDialog();

        /** 枚举真实宏树，回调 catalog。 */
        void loadMacroCatalog(CalcValidationMacroCatalog.Callback callback);
    }

    private final Host host;
    private final CalcDataValidationState state = new CalcDataValidationState();
    private String macroDisplayName = "";
    private boolean loadRequested = false;

    private View rootView;
    private FrameLayout pageContainer;
    private View mainPage;
    private View optionPickerPage;
    private LinearLayout optionPickerList;
    private TextView optionPickerTitle;
    private CalcValidationMacroPickerController macroPicker;
    private View macroPickerPage;

    private Tab selectedTab = Tab.CRITERIA;
    private OptionKind activeOptionKind = OptionKind.ALLOW;

    private final TextView[] segmentTabs = new TextView[3];
    private View criteriaBody;
    private View inputHelpBody;
    private View errorAlertBody;

    private TextView allowValueView;
    private View allowSection;
    private View allowBlankRow;
    private View dataSection;
    private TextView dataValueView;
    private View valueSection;
    private TextView valueLabel;
    private EditText valueInput;
    private View maxSection;
    private EditText maxInput;
    private View listSection;
    private EditText listInput;
    private LinearLayout listExtraSection;
    private LinearLayout rangeExtraSection;
    private LinearLayout customExtraSection;
    private View customFormulaSection;
    private EditText customFormulaInput;

    private TextView errorActionValueView;
    private View errorContentSection;
    private View macroBrowseSection;
    private View macroBrowseButton;
    private EditText errorTitleInput;
    private EditText errorMessageInput;

    CalcDataValidationController(Host host) {
        this.host = host;
    }

    View buildRootView() {
        if (rootView != null) {
            refreshDynamicFields();
            refreshValueLabels();
            return rootView;
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);

        pageContainer = new FrameLayout(host.getContext());
        pageContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mainPage = buildMainPage();
        optionPickerPage = buildOptionPickerPage();
        macroPickerPage = buildMacroPickerShell();

        pageContainer.addView(mainPage);
        pageContainer.addView(optionPickerPage);
        pageContainer.addView(macroPickerPage);
        showOverlay(Overlay.NONE);

        root.addView(pageContainer);
        root.addView(createPrimaryButton());
        rootView = root;
        showTab(Tab.CRITERIA);
        refreshDynamicFields();
        refreshValueLabels();
        root.post(this::requestLoadCurrentState);
        return rootView;
    }

    /** 打开时读当前选区已有设置回填（异步；用户已编辑过的输入不覆盖）。 */
    private void requestLoadCurrentState() {
        if (loadRequested) {
            return;
        }
        loadRequested = true;
        host.loadCurrentValidationState(state, () -> {
            if (host.getContext() == null) {
                return;
            }
            applyStateToInputs();
            refreshDynamicFields();
            refreshValueLabels();
        });
    }

    private void applyStateToInputs() {
        if (valueInput != null) {
            valueInput.setText(state.minValue);
        }
        if (customFormulaInput != null) {
            customFormulaInput.setText(state.minValue);
        }
        if (maxInput != null) {
            maxInput.setText(state.maxValue);
        }
        if (listInput != null) {
            listInput.setText(state.listEntries);
        }
        if (errorTitleInput != null) {
            errorTitleInput.setText(state.errorTitle);
        }
        if (errorMessageInput != null) {
            errorMessageInput.setText(state.errorMessage);
        }
    }

    private View buildMainPage() {
        LinearLayout page = new LinearLayout(host.getContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(createHeader("数据有效性", this::onMainHeaderBack));
        page.addView(createSegmentControl());

        FrameLayout tabBodyContainer = new FrameLayout(host.getContext());
        tabBodyContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        criteriaBody = buildCriteriaBody();
        inputHelpBody = buildInputHelpBody();
        errorAlertBody = buildErrorAlertBody();
        tabBodyContainer.addView(criteriaBody);
        tabBodyContainer.addView(inputHelpBody);
        tabBodyContainer.addView(errorAlertBody);
        page.addView(tabBodyContainer);
        return page;
    }

    private void onMainHeaderBack() {
        host.onBack();
    }

    private View createHeader(String title, Runnable backAction) {
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
        back.setOnClickListener(v -> backAction.run());
        header.addView(back, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView titleView = new TextView(host.getContext());
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(titleView, titleLp);

        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        LinearLayout wrapper = new LinearLayout(host.getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(header);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return wrapper;
    }

    private View createSegmentControl() {
        LinearLayout track = new LinearLayout(host.getContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundResource(R.drawable.lolib_bg_calc_validation_segment_track);
        int pad = host.dpToPx(4);
        track.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(12));
        track.setLayoutParams(trackLp);

        String[] labels = {"条件", "输入帮助", "错误警告"};
        Tab[] tabs = Tab.values();
        for (int i = 0; i < labels.length; i++) {
            final Tab tab = tabs[i];
            TextView tabView = new TextView(host.getContext());
            tabView.setText(labels[i]);
            tabView.setGravity(Gravity.CENTER);
            tabView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tabView.setPadding(host.dpToPx(8), host.dpToPx(10), host.dpToPx(8), host.dpToPx(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tabView.setLayoutParams(lp);
            tabView.setOnClickListener(v -> showTab(tab));
            track.addView(tabView);
            segmentTabs[i] = tabView;
        }
        return track;
    }

    private void showTab(Tab tab) {
        selectedTab = tab;
        for (int i = 0; i < segmentTabs.length; i++) {
            if (segmentTabs[i] == null) {
                continue;
            }
            boolean selected = Tab.values()[i] == tab;
            segmentTabs[i].setBackgroundResource(selected
                    ? R.drawable.lolib_bg_hyperlink_segment_tab_selected
                    : 0);
            segmentTabs[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            segmentTabs[i].setTextColor(Color.parseColor(selected ? "#101010" : "#6A6A6A"));
        }
        criteriaBody.setVisibility(tab == Tab.CRITERIA ? View.VISIBLE : View.GONE);
        inputHelpBody.setVisibility(tab == Tab.INPUT_HELP ? View.VISIBLE : View.GONE);
        errorAlertBody.setVisibility(tab == Tab.ERROR_ALERT ? View.VISIBLE : View.GONE);
    }

    private View buildCriteriaBody() {
        NestedScrollView scroll = new NestedScrollView(host.getContext());
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(host.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        body.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(16));

        allowValueView = new TextView(host.getContext());
        allowSection = createSectionPicker("允许", allowValueView, () -> openOptionPicker(OptionKind.ALLOW));
        body.addView(allowSection);

        allowBlankRow = wrapWithDivider(createCheckboxRow("允许空白单元格", state.allowEmpty,
                v -> state.allowEmpty = v));
        body.addView(allowBlankRow);

        dataValueView = new TextView(host.getContext());
        dataSection = wrapWithDivider(createSectionPicker("数据", dataValueView,
                () -> openOptionPicker(OptionKind.DATA)));
        body.addView(dataSection);

        valueLabel = sectionLabel("数值");
        valueInput = createValueEdit();
        valueSection = wrapWithDivider(createSectionField(valueLabel, valueInput));
        body.addView(valueSection);

        maxInput = createValueEdit();
        maxSection = wrapWithDivider(createSectionField(sectionLabel("最大值"), maxInput));
        body.addView(maxSection);

        listInput = createMultilineEdit();
        listSection = wrapWithDivider(createSectionField(sectionLabel("来源"), listInput));
        body.addView(listSection);

        listExtraSection = new LinearLayout(host.getContext());
        listExtraSection.setOrientation(LinearLayout.VERTICAL);
        addCheckboxRow((LinearLayout) listExtraSection, "提供下拉列表", state.showDropdownList,
                v -> state.showDropdownList = v);
        addDivider((LinearLayout) listExtraSection);
        addCheckboxRow((LinearLayout) listExtraSection, "升序排序", state.sortAscending,
                v -> state.sortAscending = v);
        addDivider((LinearLayout) listExtraSection);
        addCheckboxRow((LinearLayout) listExtraSection, "区分大小写", state.caseSensitive,
                v -> state.caseSensitive = v);
        body.addView(wrapWithTopDivider(listExtraSection));

        rangeExtraSection = new LinearLayout(host.getContext());
        rangeExtraSection.setOrientation(LinearLayout.VERTICAL);
        addCheckboxRow((LinearLayout) rangeExtraSection, "提供下拉列表", state.showDropdownList,
                v -> state.showDropdownList = v);
        body.addView(wrapWithTopDivider(rangeExtraSection));

        customExtraSection = new LinearLayout(host.getContext());
        customExtraSection.setOrientation(LinearLayout.VERTICAL);
        addCheckboxRow(customExtraSection, "区分大小写", state.caseSensitive,
                v -> state.caseSensitive = v);
        body.addView(wrapWithTopDivider(customExtraSection));

        customFormulaInput = createMultilineEdit();
        customFormulaSection = wrapWithTopDivider(
                createSectionField(sectionLabel("公式"), customFormulaInput));
        body.addView(customFormulaSection);

        scroll.addView(body);
        return scroll;
    }

    private View buildInputHelpBody() {
        NestedScrollView scroll = new NestedScrollView(host.getContext());
        LinearLayout body = new LinearLayout(host.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        body.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(16));

        addCheckboxRow(body, "选中单元格时显示输入提示", state.showInputHelp, v -> state.showInputHelp = v);
        body.addView(createDivider());
        body.addView(sectionLabel("内容"));

        LinearLayout indented = createIndentedBlock();
        EditText title = createValueEdit();
        title.setText(state.inputHelpTitle);
        title.addTextChangedListener(simpleWatcher(s -> state.inputHelpTitle = s));
        indented.addView(createSectionField(fieldLabel("标题"), title));
        EditText text = createMultilineEdit();
        text.setText(state.inputHelpText);
        text.addTextChangedListener(simpleWatcher(s -> state.inputHelpText = s));
        indented.addView(createSectionField(fieldLabel("输入提示"), text));
        body.addView(indented);

        scroll.addView(body);
        return scroll;
    }

    private View buildErrorAlertBody() {
        NestedScrollView scroll = new NestedScrollView(host.getContext());
        LinearLayout body = new LinearLayout(host.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        body.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(16));

        addCheckboxRow(body, "处理无效值", state.showErrorAlert, v -> {
            state.showErrorAlert = v;
            refreshErrorFields();
        });
        body.addView(createDivider());

        // 操作行内：值框 + 浏览按钮同行（Figma 5279:60548：518 宽值框 + 右侧浏览按钮）
        macroBrowseSection = wrapWithDivider(createErrorActionRow());
        body.addView(macroBrowseSection);

        // 标题/错误信息：通栏，与操作同样式（不缩进）
        LinearLayout errorContentInner = new LinearLayout(host.getContext());
        errorContentInner.setOrientation(LinearLayout.VERTICAL);
        errorTitleInput = createValueEdit();
        errorTitleInput.addTextChangedListener(simpleWatcher(s -> state.errorTitle = s));
        errorContentInner.addView(createSectionField(sectionLabel("标题"), errorTitleInput));
        errorMessageInput = createMultilineEdit();
        errorMessageInput.addTextChangedListener(simpleWatcher(s -> state.errorMessage = s));
        errorContentInner.addView(createSectionField(sectionLabel("错误信息"), errorMessageInput));
        errorContentSection = wrapWithDivider(errorContentInner);
        body.addView(errorContentSection);

        scroll.addView(body);
        return scroll;
    }

    /** Figma：标题在上，#F2F3F5 圆角选择框在下、通栏宽。 */
    private View createSectionPicker(String labelText, TextView valueView, Runnable onClick) {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, host.dpToPx(8), 0, host.dpToPx(8));
        section.addView(sectionLabel(labelText));

        LinearLayout box = new LinearLayout(host.getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.lolib_bg_calc_validation_picker);
        box.setMinimumHeight(host.dpToPx(44));
        box.setPadding(host.dpToPx(12), host.dpToPx(10), host.dpToPx(8), host.dpToPx(10));

        valueView.setTextColor(Color.parseColor("#333333"));
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        box.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(host.getContext());
        chevron.setText("›");
        chevron.setTextColor(Color.parseColor("#80868B"));
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        box.addView(chevron);

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = host.dpToPx(8);
        box.setLayoutParams(boxLp);
        box.setOnClickListener(v -> onClick.run());
        section.addView(box);
        return section;
    }

    private View createSectionField(TextView labelView, EditText input) {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, host.dpToPx(8), 0, host.dpToPx(8));
        section.addView(labelView);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = host.dpToPx(8);
        section.addView(input, inputLp);
        return section;
    }

    /** 操作行内：值框 + 浏览按钮同行（Figma 5279:60548：518 值框 + 右侧 144 浏览按钮）。 */
    private View createErrorActionRow() {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, host.dpToPx(8), 0, host.dpToPx(8));
        section.addView(sectionLabel("操作"));

        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(44));

        // 值框：操作类型（停止/警告/信息/宏/默默拒绝）+ chevron，#F2F3F5 圆角 24
        errorActionValueView = new TextView(host.getContext());
        errorActionValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        errorActionValueView.setTextColor(Color.parseColor("#333333"));
        errorActionValueView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout box = new LinearLayout(host.getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.lolib_bg_calc_validation_picker);
        box.setPadding(host.dpToPx(12), host.dpToPx(8), host.dpToPx(8), host.dpToPx(8));
        box.addView(errorActionValueView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = new TextView(host.getContext());
        chevron.setText("›");
        chevron.setTextColor(Color.parseColor("#80868B"));
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        box.addView(chevron);
        box.setOnClickListener(v -> openOptionPicker(OptionKind.ERROR_ACTION));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                0, host.dpToPx(44), 1f);
        boxLp.topMargin = host.dpToPx(8);
        row.addView(box, boxLp);

        // 浏览按钮：144 宽，#3B80401F 半透明绿底圆角 full，绿字；仅操作=宏时显示
        TextView browse = new TextView(host.getContext());
        browse.setText("浏览");
        browse.setTextColor(Color.parseColor("#3B8040"));
        browse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        browse.setGravity(Gravity.CENTER);
        browse.setBackgroundResource(R.drawable.lolib_bg_calc_macro_browse);
        LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(
                host.dpToPx(96), host.dpToPx(40));
        browseLp.topMargin = host.dpToPx(8);
        browseLp.setMarginStart(host.dpToPx(8));
        browse.setOnClickListener(v -> openMacroPicker());
        row.addView(browse, browseLp);
        macroBrowseButton = browse;

        section.addView(row);
        return section;
    }

    private LinearLayout createIndentedBlock() {
        LinearLayout block = new LinearLayout(host.getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(host.dpToPx(CONTENT_INDENT_DP), 0, 0, 0);
        return block;
    }

    private View buildOptionPickerPage() {
        LinearLayout page = new LinearLayout(host.getContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        page.setBackgroundColor(Color.WHITE);

        View header = createHeader("选项", this::closeOptionPicker);
        optionPickerTitle = findTitleInHeader(header);
        page.addView(header);

        NestedScrollView scroll = new NestedScrollView(host.getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        optionPickerList = new LinearLayout(host.getContext());
        optionPickerList.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        optionPickerList.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(16));
        scroll.addView(optionPickerList);
        page.addView(scroll);
        return page;
    }

    private TextView findTitleInHeader(View headerWrapper) {
        if (!(headerWrapper instanceof LinearLayout)) {
            return null;
        }
        LinearLayout wrapper = (LinearLayout) headerWrapper;
        if (wrapper.getChildCount() == 0) {
            return null;
        }
        View header = wrapper.getChildAt(0);
        if (!(header instanceof LinearLayout)) {
            return null;
        }
        LinearLayout headerRow = (LinearLayout) header;
        for (int i = 0; i < headerRow.getChildCount(); i++) {
            View child = headerRow.getChildAt(i);
            if (child instanceof TextView && !(child instanceof ImageButton)) {
                return (TextView) child;
            }
        }
        return null;
    }

    private View buildMacroPickerShell() {
        FrameLayout shell = new FrameLayout(host.getContext());
        shell.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        shell.setBackgroundColor(Color.WHITE);
        return shell;
    }

    private View createPrimaryButton() {
        TextView button = new TextView(host.getContext());
        button.setText("确定");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.lolib_bg_calc_sheet_pill_primary);
        int hPad = host.dpToPx(16);
        int vPad = host.dpToPx(12);
        button.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(16));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> onConfirm());
        return button;
    }

    private void onConfirm() {
        syncInputsToState();
        host.applyValidation(state);
    }

    private void syncInputsToState() {
        if (CalcValidationCatalog.isCustomAllow(state.allowIndex)) {
            if (customFormulaInput != null) {
                state.minValue = safeText(customFormulaInput);
            }
        } else if (valueInput != null) {
            state.minValue = safeText(valueInput);
        }
        if (maxInput != null) {
            state.maxValue = safeText(maxInput);
        }
        if (listInput != null) {
            state.listEntries = safeText(listInput);
        }
    }

    private void openOptionPicker(OptionKind kind) {
        activeOptionKind = kind;
        CalcValidationCatalog.Option[] options;
        String title;
        int selectedIndex;
        switch (kind) {
            case ALLOW:
                title = "允许";
                options = CalcValidationCatalog.ALLOW_OPTIONS;
                selectedIndex = state.allowIndex;
                break;
            case DATA:
                title = "数据";
                options = CalcValidationCatalog.DATA_OPTIONS;
                selectedIndex = state.dataIndex;
                break;
            case ERROR_ACTION:
                title = "操作";
                options = CalcValidationCatalog.ERROR_ACTION_OPTIONS;
                selectedIndex = state.errorActionIndex;
                break;
            default:
                return;
        }
        if (optionPickerTitle != null) {
            optionPickerTitle.setText(title);
        }
        populateOptionList(options, selectedIndex);
        showOverlay(Overlay.OPTION_PICKER);
    }

    private void populateOptionList(CalcValidationCatalog.Option[] options, int selectedIndex) {
        optionPickerList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < options.length; i++) {
            final CalcValidationCatalog.Option option = options[i];
            View row = inflater.inflate(R.layout.lolib_item_option_picker_row, optionPickerList, false);
            TextView name = row.findViewById(R.id.option_picker_item_name);
            ImageView check = row.findViewById(R.id.option_picker_item_check);
            name.setText(option.label);
            boolean selected = option.index == selectedIndex;
            name.setTextColor(Color.parseColor(selected ? "#3B8040" : "#333333"));
            check.setImageResource(R.drawable.lolib_ic_font_picker_check_green);
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> {
                applyOptionSelection(option);
                closeOptionPicker();
            });
            optionPickerList.addView(row);
            if (i < options.length - 1) {
                optionPickerList.addView(createDivider());
            }
        }
    }

    private void applyOptionSelection(CalcValidationCatalog.Option option) {
        switch (activeOptionKind) {
            case ALLOW:
                state.allowIndex = option.index;
                break;
            case DATA:
                state.dataIndex = option.index;
                break;
            case ERROR_ACTION:
                state.errorActionIndex = option.index;
                break;
            default:
                break;
        }
        Log.i("CalcDataValidation", "option_selected kind=" + activeOptionKind
                + " index=" + option.index + " allow=" + state.allowIndex
                + " data=" + state.dataIndex);
        refreshDynamicFields();
        refreshValueLabels();
    }

    private void closeOptionPicker() {
        showOverlay(Overlay.NONE);
    }

    private void openMacroPicker() {
        if (macroPicker == null) {
            macroPicker = new CalcValidationMacroPickerController(new CalcValidationMacroPickerController.Host() {
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
                    closeMacroPicker();
                }

                @Override
                public void onMacroSelected(String macroUrl, String displayName) {
                    state.macroUrl = macroUrl;
                    macroDisplayName = displayName;
                    refreshValueLabels();
                    closeMacroPicker();
                }

                @Override
                public void openMacroChooser(CalcValidationMacroPickerController.MacroChooseCallback callback) {
                    host.openMacroChooser(callback);
                }

                @Override
                public void loadMacroCatalog(CalcValidationMacroCatalog.Callback callback) {
                    host.loadMacroCatalog(callback);
                }
            }, state.macroUrl);
        }
        FrameLayout shell = (FrameLayout) macroPickerPage;
        shell.removeAllViews();
        shell.addView(macroPicker.buildRootView());
        showOverlay(Overlay.MACRO_PICKER);
        // 异步枚举真实宏树回填（回调在主线程 evaluateJavascript 链上）
        host.loadMacroCatalog(cat -> {
            if (cat != null) {
                macroPicker.setCatalog(cat);
            }
        });
    }

    private void closeMacroPicker() {
        showOverlay(Overlay.NONE);
    }

    private void showOverlay(Overlay next) {
        mainPage.setVisibility(next == Overlay.NONE ? View.VISIBLE : View.GONE);
        optionPickerPage.setVisibility(next == Overlay.OPTION_PICKER ? View.VISIBLE : View.GONE);
        macroPickerPage.setVisibility(next == Overlay.MACRO_PICKER ? View.VISIBLE : View.GONE);
    }

    private void refreshValueLabels() {
        if (allowValueView != null) {
            allowValueView.setText(state.allowOption().label);
        }
        if (dataValueView != null) {
            dataValueView.setText(state.dataOption().label);
        }
        if (errorActionValueView != null) {
            errorActionValueView.setText(state.errorActionOption().label);
        }
        refreshErrorFields();
    }

    private void refreshDynamicFields() {
        boolean anyValue = state.allowIndex == 0;
        boolean list = CalcValidationCatalog.isListAllow(state.allowIndex);
        boolean range = CalcValidationCatalog.isRangeAllow(state.allowIndex);
        boolean custom = CalcValidationCatalog.isCustomAllow(state.allowIndex);
        boolean needsBetween = CalcValidationCatalog.needsBetweenValues(state.dataIndex);
        // 数据=… / 数值输入：所有值/整数/小数/日期/时间/文本长度 都显示（Figma 57088 主屏）
        boolean showDataValue = !list && !range && !custom;

        setSectionVisible(dataSection, showDataValue);
        setSectionVisible(valueSection, showDataValue);
        setSectionVisible(maxSection, showDataValue && needsBetween);
        setSectionVisible(listSection, list);
        setSectionVisible(listExtraSection, list);
        setSectionVisible(rangeExtraSection, range);
        setSectionVisible(customExtraSection, custom);
        setSectionVisible(customFormulaSection, custom);

        boolean showAllowBlank = anyValue || showDataValue || custom;
        setSectionVisible(allowBlankRow, showAllowBlank);

        if (valueLabel != null) {
            if (range) {
                valueLabel.setText("来源");
            } else if (CalcValidationCatalog.needsBetweenValues(state.dataIndex)) {
                valueLabel.setText("最小值");
                resetValueInputSingleLine();
            } else {
                valueLabel.setText("数值");
                resetValueInputSingleLine();
            }
        }
        refreshErrorFields();
    }

    private void resetValueInputSingleLine() {
        if (valueInput == null) {
            return;
        }
        valueInput.setMinLines(1);
        valueInput.setInputType(InputType.TYPE_CLASS_TEXT);
    }

    private void refreshErrorFields() {
        boolean macro = state.errorActionIndex == 3;
        boolean silent = state.errorActionIndex == 4;
        // 操作行始终显示；浏览按钮仅操作=宏时显示（Figma 5279:60548）
        setSectionVisible(macroBrowseSection, state.showErrorAlert);
        setViewVisible(macroBrowseButton, macro && state.showErrorAlert);
        if (errorContentSection != null) {
            errorContentSection.setVisibility(!silent && state.showErrorAlert
                    ? View.VISIBLE : View.GONE);
        }
    }

    private static void setViewVisible(View view, boolean visible) {
        if (view == null) {
            return;
        }
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private static void setSectionVisible(View section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** 区块 + 底部分割线，隐藏时一并收起。 */
    private LinearLayout wrapWithDivider(View content) {
        LinearLayout block = new LinearLayout(host.getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(content);
        block.addView(createDivider());
        return block;
    }

    /** 顶部分割线 + 区块（用于条件页后续附加项）。 */
    private LinearLayout wrapWithTopDivider(View content) {
        LinearLayout block = new LinearLayout(host.getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(createDivider());
        block.addView(content);
        return block;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(host.getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#101010"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return tv;
    }

    private TextView fieldLabel(String text) {
        TextView tv = new TextView(host.getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#80868B"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return tv;
    }

    private EditText createValueEdit() {
        EditText edit = new EditText(host.getContext());
        edit.setTextColor(Color.parseColor("#101010"));
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        edit.setBackgroundResource(R.drawable.lolib_bg_calc_validation_input);
        edit.setPadding(host.dpToPx(12), host.dpToPx(10), host.dpToPx(12), host.dpToPx(10));
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        return edit;
    }

    private EditText createMultilineEdit() {
        EditText edit = createValueEdit();
        edit.setMinLines(3);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return edit;
    }

    private View createCheckboxRow(String labelText, boolean initial,
            java.util.function.Consumer<Boolean> onChanged) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(48));
        row.setPadding(0, host.dpToPx(8), 0, host.dpToPx(8));

        TextView label = sectionLabel(labelText);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView check = new ImageView(host.getContext());
        check.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        final boolean[] current = {initial};
        check.setImageResource(current[0]
                ? R.drawable.lolib_ic_checkbox_calc_checked
                : R.drawable.lolib_ic_checkbox_calc_unchecked);
        row.addView(check, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
        row.setOnClickListener(v -> {
            current[0] = !current[0];
            check.setImageResource(current[0]
                    ? R.drawable.lolib_ic_checkbox_calc_checked
                    : R.drawable.lolib_ic_checkbox_calc_unchecked);
            onChanged.accept(current[0]);
        });
        return row;
    }

    private void addCheckboxRow(LinearLayout parent, String labelText, boolean initial,
            java.util.function.Consumer<Boolean> onChanged) {
        parent.addView(createCheckboxRow(labelText, initial, onChanged));
    }

    private void addDivider(LinearLayout parent) {
        parent.addView(createDivider());
    }

    private View createDivider() {
        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#0A000000"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return divider;
    }

    private static String safeText(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private static android.text.TextWatcher simpleWatcher(java.util.function.Consumer<String> onChange) {
        return new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                onChange.accept(s == null ? "" : s.toString());
            }
        };
    }
}
