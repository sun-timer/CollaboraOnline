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

/**
 * Impress 功能面板 — 插入超链接二级页（互联网 / 邮件 / 文档）。
 */
final class ImpressHyperlinkPickerController {

    private static final String TAG = "ImpressHyperlinkPicker";

    enum Tab {
        INTERNET, MAIL, DOCUMENT
    }

    private enum DocumentTargetKind {
        SLIDE, NOTES, HANDOUTS, MASTER
    }

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void toastTodo(String text);

        void insertHyperlink(String displayText, String url);

        void fetchImpressHyperlinkContext(HyperlinkContextCallback callback);

        void onBack();
    }

    interface HyperlinkContextCallback {
        void onContext(int activeSlideIndex, String[] slideNames);
    }

    private final Host host;
    private Tab selectedTab = Tab.INTERNET;
    private boolean documentTargetPickerVisible;
    private DocumentTargetKind documentTargetKind = DocumentTargetKind.SLIDE;
    private String pendingDocumentTargetUrl = "";
    private String pendingDocumentTargetLabel = "";
    private String selectedDocumentUrl = "";
    private String selectedDocumentLabel = "";
    private int activeSlideIndex;
    private String[] slideNames = new String[0];

    private View rootView;
    private FrameLayout fieldsContainer;
    private View internetFields;
    private View mailFields;
    private View documentFormFields;
    private View documentSlidePickerFields;
    private TextView primaryButton;
    private final java.util.List<TextView> segmentTabs = new java.util.ArrayList<>();
    private LinearLayout slideListContainer;

    private EditText webTextInput;
    private EditText webLinkInput;
    private EditText webNameInput;

    private EditText mailRecipientInput;
    private EditText mailSubjectInput;
    private EditText mailBodyInput;
    private EditText mailNameInput;

    private EditText docTextInput;
    private EditText docNameInput;

    ImpressHyperlinkPickerController(Host host) {
        this.host = host;
    }

    void onPickerShown() {
        host.fetchImpressHyperlinkContext((slideIndex, slides) -> {
            activeSlideIndex = Math.max(0, slideIndex);
            slideNames = slides != null ? slides : new String[0];
            if (selectedDocumentUrl.isEmpty() && slideNames.length > 0) {
                int idx = Math.min(activeSlideIndex, slideNames.length - 1);
                selectedDocumentUrl = buildSlideUrl(idx);
                selectedDocumentLabel = formatSlideLabel(idx, slideNames[idx]);
            }
            rebuildSlideList();
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
        documentSlidePickerFields = buildDocumentSlidePickerFields();
        fieldsContainer.addView(internetFields);
        fieldsContainer.addView(mailFields);
        fieldsContainer.addView(documentFormFields);
        fieldsContainer.addView(documentSlidePickerFields);
        return fieldsContainer;
    }

    private View buildInternetFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();
        webTextInput = addField(content, "文本");
        webLinkInput = addField(content, "链接");
        webNameInput = addField(content, "姓名");
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
        mailNameInput = addField(content, "姓名");
        scroll.addView(content);
        return scroll;
    }

    private View buildDocumentFormFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();

        TextView targetHeading = new TextView(host.getContext());
        targetHeading.setText("目标");
        targetHeading.setTextColor(Color.parseColor("#101010"));
        targetHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        targetHeading.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingLp.bottomMargin = host.dpToPx(8);
        content.addView(targetHeading, headingLp);

        content.addView(createTargetRow("幻灯片", () -> showDocumentTargetPicker(DocumentTargetKind.SLIDE)));
        content.addView(createTargetRow("备注", () -> host.toastTodo("备注目标暂不支持")));
        content.addView(createTargetRow("讲义", () -> host.toastTodo("讲义目标暂不支持")));
        content.addView(createTargetRow("主页面", () -> host.toastTodo("主页面目标暂不支持")));

        docTextInput = addField(content, "文本");
        docNameInput = addField(content, "姓名");
        scroll.addView(content);
        return scroll;
    }

    private View buildDocumentSlidePickerFields() {
        ScrollView scroll = createFieldsScroll();
        LinearLayout content = createFieldsColumn();

        TextView title = new TextView(host.getContext());
        title.setText("幻灯片");
        title.setTextColor(Color.parseColor("#101010"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);

        slideListContainer = new LinearLayout(host.getContext());
        slideListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = host.dpToPx(12);
        slideListContainer.setLayoutParams(listLp);
        content.addView(slideListContainer);

        scroll.addView(content);
        return scroll;
    }

    private LinearLayout createTargetRow(String label, Runnable onClick) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.lolib_bg_hyperlink_target_row);
        int hPad = host.dpToPx(16);
        int vPad = host.dpToPx(14);
        row.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = host.dpToPx(8);
        row.setLayoutParams(rowLp);

        TextView text = new TextView(host.getContext());
        text.setText(label);
        text.setTextColor(Color.parseColor("#101010"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = new ImageView(host.getContext());
        chevron.setImageResource(R.drawable.lolib_ic_chevron_right);
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int chevronSize = host.dpToPx(16);
        row.addView(chevron, new LinearLayout.LayoutParams(chevronSize, chevronSize));

        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private void rebuildSlideList() {
        if (slideListContainer == null) {
            return;
        }
        slideListContainer.removeAllViews();
        if (slideNames.length == 0) {
            TextView empty = new TextView(host.getContext());
            empty.setText("暂无幻灯片");
            empty.setTextColor(Color.parseColor("#80868B"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            slideListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < slideNames.length; i++) {
            final int slideIndex = i;
            String slideName = slideNames[i];
            String label = formatSlideLabel(slideIndex, slideName);
            LinearLayout row = createTargetRow(label, () -> selectSlideTarget(slideIndex, label));
            slideListContainer.addView(row);
        }
    }

    private void selectSlideTarget(int slideIndex, String label) {
        pendingDocumentTargetLabel = label;
        pendingDocumentTargetUrl = buildSlideUrl(slideIndex);
        highlightSelectedSlideRow(slideIndex);
    }

    private void highlightSelectedSlideRow(int slideIndex) {
        if (slideListContainer == null) {
            return;
        }
        for (int i = 0; i < slideListContainer.getChildCount(); i++) {
            View child = slideListContainer.getChildAt(i);
            boolean selected = i == slideIndex;
            child.setBackgroundResource(selected
                    ? R.drawable.lolib_bg_impress_transition_cell_selected
                    : R.drawable.lolib_bg_hyperlink_target_row);
        }
    }

    private void showDocumentTargetPicker(DocumentTargetKind kind) {
        if (kind != DocumentTargetKind.SLIDE) {
            return;
        }
        documentTargetKind = kind;
        documentTargetPickerVisible = true;
        pendingDocumentTargetLabel = selectedDocumentLabel;
        pendingDocumentTargetUrl = selectedDocumentUrl;
        rebuildSlideList();
        if (!pendingDocumentTargetUrl.isEmpty() && slideNames.length > 0) {
            for (int i = 0; i < slideNames.length; i++) {
                if (buildSlideUrl(i).equals(pendingDocumentTargetUrl)) {
                    highlightSelectedSlideRow(i);
                    break;
                }
            }
        }
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
            host.toastTodo("请选择幻灯片");
            return;
        }
        selectedDocumentUrl = pendingDocumentTargetUrl;
        selectedDocumentLabel = pendingDocumentTargetLabel;
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
        button.setBackgroundResource(R.drawable.lolib_bg_impress_primary_button);
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
        documentSlidePickerFields.setVisibility(documentTab && documentTargetPickerVisible
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
        String link = safeText(webLinkInput);
        if (link.isEmpty()) {
            host.toastTodo("请填写链接");
            return;
        }
        String url = CalcHyperlinkPickerController.buildWebUrl(link);
        String display = resolveDisplayText(webTextInput, webNameInput, link);
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
        String url = CalcHyperlinkPickerController.buildMailUrl(recipient, subject, body);
        String display = safeText(mailNameInput);
        if (display.isEmpty()) {
            display = recipient;
        }
        Log.i(TAG, "insert_mail text=" + display + " url=" + url);
        host.insertHyperlink(display, url);
    }

    private void submitDocument() {
        if (selectedDocumentUrl == null || selectedDocumentUrl.isEmpty()) {
            host.toastTodo("请选择目标，点击「幻灯片」");
            return;
        }
        String display = resolveDisplayText(docTextInput, docNameInput, selectedDocumentLabel);
        if (display.isEmpty()) {
            display = selectedDocumentLabel;
        }
        Log.i(TAG, "insert_document text=" + display + " url=" + selectedDocumentUrl);
        host.insertHyperlink(display, selectedDocumentUrl);
    }

    private static String resolveDisplayText(EditText textInput, EditText nameInput, String fallback) {
        String text = safeText(textInput);
        if (!text.isEmpty()) {
            return text;
        }
        String name = safeText(nameInput);
        if (!name.isEmpty()) {
            return name;
        }
        return fallback != null ? fallback : "";
    }

    private static String safeText(EditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    static String buildSlideUrl(int slideIndex) {
        return "#Slide " + (slideIndex + 1);
    }

    private static String formatSlideLabel(int slideIndex, String slideName) {
        String name = slideName != null ? slideName.trim() : "";
        if (name.isEmpty()) {
            return "幻灯片 " + (slideIndex + 1);
        }
        return (slideIndex + 1) + ". " + name;
    }
}
