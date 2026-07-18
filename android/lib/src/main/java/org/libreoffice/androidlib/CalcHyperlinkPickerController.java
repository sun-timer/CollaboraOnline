package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Calc 功能面板 — 插入超链接二级页（互联网 / 邮件 / 文档）。
 */
final class CalcHyperlinkPickerController {

    private static final String TAG = "CalcHyperlinkPicker";

    enum Tab {
        INTERNET, MAIL, DOCUMENT
    }

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void toastTodo(String text);

        void insertHyperlink(String displayText, String url);

        void fetchCalcHyperlinkContext(HyperlinkContextCallback callback);

        void onBack();
    }

    interface HyperlinkContextCallback {
        void onContext(String cellRange, String activeSheetName, String[] sheetNames);
    }

    private final Host host;
    private Tab selectedTab = Tab.INTERNET;
    private boolean documentTargetPickerVisible;
    private boolean worksheetTreeExpanded;
    private String pendingDocumentTargetUrl = "";
    private String pendingDocumentTargetLabel = "";
    private String activeSheetName = "";
    private String[] sheetNames = new String[0];

    private View rootView;
    private FrameLayout fieldsContainer;
    private View internetFields;
    private View mailFields;
    private View documentFormFields;
    private View documentTargetPickerFields;
    private TextView primaryButton;
    private final List<TextView> segmentTabs = new ArrayList<>();

    private EditText webTextInput;
    private EditText webLinkInput;

    private EditText mailRecipientInput;
    private EditText mailSubjectInput;
    private EditText mailBodyInput;

    private EditText docTargetInput;
    private EditText docTextInput;
    private String selectedDocumentUrl = "";
    private LinearLayout worksheetChildrenContainer;

    CalcHyperlinkPickerController(Host host) {
        this.host = host;
    }

    void onPickerShown() {
        host.fetchCalcHyperlinkContext((cellRange, sheetName, sheets) -> {
            activeSheetName = sheetName != null ? sheetName : "";
            sheetNames = sheets != null ? sheets : new String[0];
            rebuildWorksheetChildren();
            if (docTargetInput != null && safeText(docTargetInput).isEmpty()
                    && !activeSheetName.isEmpty()) {
                selectedDocumentUrl = buildDocumentSheetUrl(activeSheetName);
                docTargetInput.setText(activeSheetName);
            }
        });
    }

    View buildRootView() {
        if (rootView != null) {
            return rootView;
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(createHeader());
        root.addView(createSegmentControl());
        root.addView(createFieldsArea(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        primaryButton = createPrimaryButton();
        root.addView(primaryButton);

        rootView = root;
        showTab(Tab.INTERNET);
        return rootView;
    }

    private View createHeader() {
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
        back.setOnClickListener(v -> onHeaderBack());
        header.addView(back, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView title = new TextView(host.getContext());
        title.setText("超链接");
        title.setTextColor(Color.parseColor("#101010"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(title, titleLp);
        return header;
    }

    private void onHeaderBack() {
        if (documentTargetPickerVisible) {
            hideDocumentTargetPicker();
            return;
        }
        host.onBack();
    }

    private View createSegmentControl() {
        LinearLayout track = new LinearLayout(host.getContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundResource(R.drawable.lolib_bg_hyperlink_segment_track);
        int pad = host.dpToPx(4);
        track.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(12));
        track.setLayoutParams(trackLp);

        String[] labels = {"互联网", "邮件", "文档"};
        Tab[] tabs = Tab.values();
        for (int i = 0; i < labels.length; i++) {
            final Tab tab = tabs[i];
            TextView tabView = new TextView(host.getContext());
            tabView.setText(labels[i]);
            tabView.setGravity(Gravity.CENTER);
            tabView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tabView.setPadding(host.dpToPx(8), host.dpToPx(10), host.dpToPx(8), host.dpToPx(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tabView.setLayoutParams(lp);
            tabView.setOnClickListener(v -> showTab(tab));
            track.addView(tabView);
            segmentTabs.add(tabView);
        }
        return track;
    }

    private View createFieldsArea() {
        fieldsContainer = new FrameLayout(host.getContext());
        internetFields = buildInternetFields();
        mailFields = buildMailFields();
        documentFormFields = buildDocumentFormFields();
        documentTargetPickerFields = buildDocumentTargetPickerFields();
        fieldsContainer.addView(internetFields);
        fieldsContainer.addView(mailFields);
        fieldsContainer.addView(documentFormFields);
        fieldsContainer.addView(documentTargetPickerFields);
        return fieldsContainer;
    }

    private View buildInternetFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();
        webTextInput = addField(content, "文本");
        webLinkInput = addField(content, "链接");
        scroll.addView(content);
        return scroll;
    }

    private View buildMailFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();
        mailRecipientInput = addField(content, "收件人");
        mailSubjectInput = addField(content, "主题");
        mailBodyInput = addField(content, "正文");
        mailBodyInput.setMinLines(3);
        mailBodyInput.setGravity(Gravity.TOP | Gravity.START);
        mailBodyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        scroll.addView(content);
        return scroll;
    }

    private View buildDocumentFormFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();

        docTargetInput = addField(content, "目标");
        docTargetInput.setFocusable(false);
        docTargetInput.setClickable(false);
        docTargetInput.setLongClickable(false);

        TextView openButton = new TextView(host.getContext());
        openButton.setText("打开");
        openButton.setGravity(Gravity.CENTER);
        openButton.setTextColor(Color.WHITE);
        openButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        openButton.setTypeface(null, Typeface.BOLD);
        openButton.setBackgroundResource(R.drawable.lolib_bg_calc_primary_button);
        int btnVPad = host.dpToPx(14);
        openButton.setPadding(host.dpToPx(16), btnVPad, host.dpToPx(16), btnVPad);
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openLp.setMargins(host.dpToPx(16), host.dpToPx(4), host.dpToPx(16), host.dpToPx(8));
        openButton.setLayoutParams(openLp);
        openButton.setOnClickListener(v -> showDocumentTargetPicker());
        content.addView(openButton);

        docTextInput = addField(content, "文本");
        scroll.addView(content);
        return scroll;
    }

    private View buildDocumentTargetPickerFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();

        LinearLayout titleRow = new LinearLayout(host.getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(host.getContext());
        title.setText("文档中的目标");
        title.setTextColor(Color.parseColor("#101010"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = new TextView(host.getContext());
        chevron.setText("▾");
        chevron.setTextColor(Color.parseColor("#80868B"));
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleRow.addView(chevron);
        content.addView(titleRow);

        LinearLayout treeBox = new LinearLayout(host.getContext());
        treeBox.setOrientation(LinearLayout.VERTICAL);
        treeBox.setBackgroundResource(R.drawable.lolib_bg_hyperlink_tree_box);
        int boxPad = host.dpToPx(12);
        treeBox.setPadding(boxPad, boxPad, boxPad, boxPad);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = host.dpToPx(12);
        treeBox.setLayoutParams(boxLp);

        LinearLayout worksheetRow = createTreeRow("工作表", TreeRowKind.EXPANDABLE_PARENT,
                R.drawable.lolib_ic_hyperlink_target_worksheet);
        worksheetRow.setOnClickListener(v -> toggleWorksheetTree());
        treeBox.addView(worksheetRow);

        worksheetChildrenContainer = new LinearLayout(host.getContext());
        worksheetChildrenContainer.setOrientation(LinearLayout.VERTICAL);
        worksheetChildrenContainer.setVisibility(View.GONE);
        treeBox.addView(worksheetChildrenContainer);

        LinearLayout namedRangeRow = createTreeRow("范围名称", TreeRowKind.PLAIN_PARENT,
                R.drawable.lolib_ic_hyperlink_target_named_range);
        namedRangeRow.setOnClickListener(v -> selectDocumentTargetCategory("范围名称", ""));
        treeBox.addView(namedRangeRow);

        LinearLayout databaseRangeRow = createTreeRow("数据库范围", TreeRowKind.PLAIN_PARENT,
                R.drawable.lolib_ic_hyperlink_target_database_range);
        databaseRangeRow.setOnClickListener(v -> selectDocumentTargetCategory("数据库范围", ""));
        treeBox.addView(databaseRangeRow);

        content.addView(treeBox);
        scroll.addView(content);
        return scroll;
    }

    private static final int CHEVRON_SLOT_DP = 16;
    private static final int TREE_ICON_DP = 20;
    private static final int TREE_ICON_GAP_DP = 8;
    private static final int TREE_CHILD_INDENT_DP = 44;

    private enum TreeRowKind {
        EXPANDABLE_PARENT,
        PLAIN_PARENT,
        CHILD
    }

    private LinearLayout createTreeRow(String label, TreeRowKind kind, int iconRes) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(44));
        int vPad = host.dpToPx(6);
        int leftPad = kind == TreeRowKind.CHILD ? host.dpToPx(TREE_CHILD_INDENT_DP) : 0;
        row.setPadding(leftPad, vPad, 0, vPad);

        if (kind == TreeRowKind.EXPANDABLE_PARENT) {
            ImageView chevron = new ImageView(host.getContext());
            chevron.setImageResource(worksheetTreeExpanded
                    ? R.drawable.lolib_ic_chevron_down
                    : R.drawable.lolib_ic_chevron_right);
            chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            chevron.setTag("worksheet_arrow");
            int chevronSize = host.dpToPx(CHEVRON_SLOT_DP);
            row.addView(chevron, new LinearLayout.LayoutParams(chevronSize, chevronSize));
        } else if (kind == TreeRowKind.PLAIN_PARENT) {
            View spacer = new View(host.getContext());
            row.addView(spacer, new LinearLayout.LayoutParams(host.dpToPx(CHEVRON_SLOT_DP), 1));
        }

        if (iconRes != 0) {
            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(iconRes);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = host.dpToPx(TREE_ICON_DP);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.setMarginEnd(host.dpToPx(TREE_ICON_GAP_DP));
            row.addView(icon, iconLp);
        }

        TextView text = new TextView(host.getContext());
        text.setText(label);
        text.setTextColor(Color.parseColor("#101010"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void rebuildWorksheetChildren() {
        if (worksheetChildrenContainer == null) {
            return;
        }
        worksheetChildrenContainer.removeAllViews();
        for (String sheet : sheetNames) {
            if (sheet == null || sheet.isEmpty()) {
                continue;
            }
            LinearLayout row = createTreeRow(sheet, TreeRowKind.CHILD, 0);
            row.setOnClickListener(v -> selectDocumentTargetCategory(sheet, buildDocumentSheetUrl(sheet)));
            worksheetChildrenContainer.addView(row);
        }
    }

    private void toggleWorksheetTree() {
        worksheetTreeExpanded = !worksheetTreeExpanded;
        if (worksheetChildrenContainer != null) {
            worksheetChildrenContainer.setVisibility(
                    worksheetTreeExpanded ? View.VISIBLE : View.GONE);
        }
        refreshWorksheetArrow();
    }

    private void refreshWorksheetArrow() {
        if (documentTargetPickerFields == null) {
            return;
        }
        View arrow = documentTargetPickerFields.findViewWithTag("worksheet_arrow");
        if (arrow instanceof ImageView) {
            ((ImageView) arrow).setImageResource(worksheetTreeExpanded
                    ? R.drawable.lolib_ic_chevron_down
                    : R.drawable.lolib_ic_chevron_right);
        }
    }

    private void selectDocumentTargetCategory(String label, String url) {
        pendingDocumentTargetLabel = label;
        pendingDocumentTargetUrl = url;
        if (url.isEmpty()) {
            host.toastTodo(label + "暂不支持，请选择工作表");
        }
    }

    private void showDocumentTargetPicker() {
        documentTargetPickerVisible = true;
        pendingDocumentTargetLabel = safeText(docTargetInput);
        pendingDocumentTargetUrl = selectedDocumentUrl;
        worksheetTreeExpanded = sheetNames.length > 0;
        rebuildWorksheetChildren();
        if (worksheetChildrenContainer != null) {
            worksheetChildrenContainer.setVisibility(
                    worksheetTreeExpanded ? View.VISIBLE : View.GONE);
        }
        refreshWorksheetArrow();
        updateFieldsVisibility();
        updatePrimaryButtonLabel();
    }

    private void hideDocumentTargetPicker() {
        documentTargetPickerVisible = false;
        updateFieldsVisibility();
        updatePrimaryButtonLabel();
    }

    private void applyDocumentTargetSelection() {
        if (pendingDocumentTargetUrl == null || pendingDocumentTargetUrl.isEmpty()) {
            host.toastTodo("请选择目标");
            return;
        }
        selectedDocumentUrl = pendingDocumentTargetUrl;
        if (docTargetInput != null) {
            docTargetInput.setText(pendingDocumentTargetLabel);
        }
        hideDocumentTargetPicker();
    }

    private ScrollView createFieldsScroll() {
        ScrollView scroll = new ScrollView(host.getContext());
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(true);
        scroll.setVisibility(View.GONE);
        return scroll;
    }

    private LinearLayout createFieldsColumn() {
        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int hPad = host.dpToPx(16);
        content.setPadding(hPad, 0, hPad, host.dpToPx(16));
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return content;
    }

    private EditText addField(LinearLayout parent, String label) {
        TextView caption = new TextView(host.getContext());
        caption.setText(label);
        caption.setTextColor(Color.parseColor("#101010"));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        caption.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.bottomMargin = host.dpToPx(8);
        if (parent.getChildCount() > 0) {
            captionLp.topMargin = host.dpToPx(12);
        }
        parent.addView(caption, captionLp);

        EditText input = new EditText(host.getContext());
        input.setHint("输入内容");
        input.setHintTextColor(Color.parseColor("#999999"));
        input.setTextColor(Color.parseColor("#101010"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setBackgroundResource(R.drawable.lolib_bg_outline_edit);
        input.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        parent.addView(input, inputLp);
        return input;
    }

    private TextView createPrimaryButton() {
        TextView button = new TextView(host.getContext());
        button.setText("添加");
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.lolib_bg_calc_primary_button);
        int vPad = host.dpToPx(14);
        button.setPadding(host.dpToPx(16), vPad, host.dpToPx(16), vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(16));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> onPrimaryButtonClicked());
        return button;
    }

    private void updatePrimaryButtonLabel() {
        if (primaryButton == null) {
            return;
        }
        primaryButton.setText(selectedTab == Tab.DOCUMENT && documentTargetPickerVisible
                ? "应用" : "添加");
    }

    private void onPrimaryButtonClicked() {
        if (selectedTab == Tab.DOCUMENT && documentTargetPickerVisible) {
            applyDocumentTargetSelection();
            return;
        }
        onAddClicked();
    }

    private void showTab(Tab tab) {
        selectedTab = tab;
        if (tab != Tab.DOCUMENT) {
            documentTargetPickerVisible = false;
        }
        updateFieldsVisibility();
        updatePrimaryButtonLabel();

        for (int i = 0; i < segmentTabs.size(); i++) {
            TextView tabView = segmentTabs.get(i);
            boolean selected = Tab.values()[i] == tab;
            tabView.setBackgroundResource(selected
                    ? R.drawable.lolib_bg_hyperlink_segment_tab_selected
                    : android.R.color.transparent);
            tabView.setTextColor(Color.parseColor(selected ? "#101010" : "#666666"));
            tabView.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void updateFieldsVisibility() {
        internetFields.setVisibility(selectedTab == Tab.INTERNET ? View.VISIBLE : View.GONE);
        mailFields.setVisibility(selectedTab == Tab.MAIL ? View.VISIBLE : View.GONE);
        boolean documentTab = selectedTab == Tab.DOCUMENT;
        documentFormFields.setVisibility(documentTab && !documentTargetPickerVisible
                ? View.VISIBLE : View.GONE);
        documentTargetPickerFields.setVisibility(documentTab && documentTargetPickerVisible
                ? View.VISIBLE : View.GONE);
    }

    private void onAddClicked() {
        switch (selectedTab) {
            case INTERNET:
                submitInternet();
                break;
            case MAIL:
                submitMail();
                break;
            case DOCUMENT:
                submitDocument();
                break;
            default:
                break;
        }
    }

    private void submitInternet() {
        String text = safeText(webTextInput);
        String link = safeText(webLinkInput);
        if (link.isEmpty()) {
            host.toastTodo("请填写链接");
            return;
        }
        String display = !text.isEmpty() ? text : link;
        String url = buildWebUrl(link);
        Log.i(TAG, "insert_web text=" + display + " url=" + url);
        host.insertHyperlink(display, url);
    }

    private void submitMail() {
        String recipient = safeText(mailRecipientInput);
        if (recipient.isEmpty()) {
            host.toastTodo("请填写收件人");
            return;
        }
        String subject = safeText(mailSubjectInput);
        String body = safeText(mailBodyInput);
        String display = recipient;
        String url = buildMailUrl(recipient, subject, body);
        Log.i(TAG, "insert_mail text=" + display + " url=" + url);
        host.insertHyperlink(display, url);
    }

    private void submitDocument() {
        if (selectedDocumentUrl == null || selectedDocumentUrl.isEmpty()) {
            host.toastTodo("请选择目标");
            return;
        }
        String text = safeText(docTextInput);
        String display = !text.isEmpty() ? text : pendingDocumentTargetLabel;
        if (display.isEmpty()) {
            display = safeText(docTargetInput);
        }
        Log.i(TAG, "insert_document text=" + display + " url=" + selectedDocumentUrl);
        host.insertHyperlink(display, selectedDocumentUrl);
    }

    private static String safeText(EditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    static String buildWebUrl(String link) {
        String trimmed = link.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("ftp://") || lower.startsWith("mailto:")) {
            return trimmed;
        }
        if (trimmed.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "mailto:" + trimmed;
        }
        return "http://" + trimmed;
    }

    static String buildMailUrl(String recipient, String subject, String body) {
        StringBuilder sb = new StringBuilder("mailto:");
        sb.append(recipient.trim());
        try {
            boolean hasQuery = false;
            if (subject != null && !subject.isEmpty()) {
                sb.append("?subject=")
                        .append(URLEncoder.encode(subject, StandardCharsets.UTF_8.name()));
                hasQuery = true;
            }
            if (body != null && !body.isEmpty()) {
                sb.append(hasQuery ? "&" : "?")
                        .append("body=")
                        .append(URLEncoder.encode(body, StandardCharsets.UTF_8.name()));
            }
        } catch (Exception ignored) {
            // keep mailto:recipient
        }
        return sb.toString();
    }

    static String buildDocumentSheetUrl(String sheetName) {
        String sheet = formatSheetName(sheetName.trim());
        if (sheet.isEmpty()) {
            return "";
        }
        return "#" + sheet + ".A1";
    }

    private static String formatSheetName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.startsWith("'") && name.endsWith("'")) {
            return name;
        }
        if (name.matches("^[A-Za-z0-9_]+$")) {
            return name;
        }
        return "'" + name.replace("'", "''") + "'";
    }
}
