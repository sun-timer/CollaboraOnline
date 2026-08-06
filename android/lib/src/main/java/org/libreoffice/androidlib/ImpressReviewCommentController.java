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

import org.libreoffice.androidlib.impress.ImpressSubpageHeader;
import org.libreoffice.androidlib.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Impress 功能面板 — 审阅批注列表（查看 / 编辑 / 删除）。
 */
final class ImpressReviewCommentController {

    private static final String TAG = "ImpressReviewComment";

    static final class CommentEntry {
        final String id;
        final String author;
        final String text;
        final String dateTime;

        CommentEntry(String id, String author, String text, String dateTime) {
            this.id = id != null ? id : "";
            this.author = author != null ? author : "";
            this.text = text != null ? text : "";
            this.dateTime = dateTime != null ? dateTime : "";
        }
    }

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        String getCommentAuthorName();

        void toastTodo(String text);

        void fetchReviewComments(ReviewCommentsCallback callback);

        void editCommentWithText(String id, String author, String text);

        void deleteCommentWithId(String id);

        void onBack();
    }

    interface ReviewCommentsCallback {
        void onComments(List<CommentEntry> comments);
    }

    private final Host host;
    private View rootView;
    private LinearLayout listContainer;
    private View listPanel;
    private View editPanel;
    private EditText editInput;
    private CommentEntry editingEntry;
    private final List<CommentEntry> comments = new ArrayList<>();

    ImpressReviewCommentController(Host host) {
        this.host = host;
    }

    void onPickerShown() {
        reloadComments();
    }

    View buildRootView() {
        if (rootView != null) {
            return rootView;
        }
        FrameLayout root = new FrameLayout(host.getContext());
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        listPanel = buildListPanel();
        editPanel = buildEditPanel();
        editPanel.setVisibility(View.GONE);

        root.addView(listPanel);
        root.addView(editPanel);
        rootView = root;
        return rootView;
    }

    private View buildListPanel() {
        LinearLayout panel = new LinearLayout(host.getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(createHeader(false));
        panel.addView(ImpressSubpageHeader.createDivider(host.getContext()));

        ScrollView scroll = new ScrollView(host.getContext());
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(host.getContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        int hPad = host.dpToPx(16);
        listContainer.setPadding(hPad, host.dpToPx(8), hPad, host.dpToPx(16));
        scroll.addView(listContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    private View buildEditPanel() {
        LinearLayout panel = new LinearLayout(host.getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(createHeader(true));
        panel.addView(ImpressSubpageHeader.createDivider(host.getContext()));
        panel.addView(createEditCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(createEditButtonRow());
        return panel;
    }

    private View createHeader(boolean editMode) {
        String title = editMode ? "编辑批注" : "批注";
        return ImpressSubpageHeader.create(host.getContext(), host::dpToPx, title, v -> {
            if (editMode) {
                hideEditPanel();
            } else {
                host.onBack();
            }
        });
    }

    private void reloadComments() {
        host.fetchReviewComments(entries -> {
            comments.clear();
            if (entries != null) {
                comments.addAll(entries);
            }
            renderCommentList();
        });
    }

    private void renderCommentList() {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        if (comments.isEmpty()) {
            TextView empty = new TextView(host.getContext());
            empty.setText("当前幻灯片暂无批注");
            empty.setTextColor(Color.parseColor("#80868B"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, host.dpToPx(32), 0, host.dpToPx(32));
            listContainer.addView(empty);
            return;
        }
        for (CommentEntry entry : comments) {
            listContainer.addView(createCommentCard(entry));
        }
    }

    private View createCommentCard(CommentEntry entry) {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_impress_comment_card);
        int pad = host.dpToPx(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = host.dpToPx(12);
        card.setLayoutParams(cardLp);

        LinearLayout topRow = new LinearLayout(host.getContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout meta = new LinearLayout(host.getContext());
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView authorView = new TextView(host.getContext());
        authorView.setText(entry.author.isEmpty() ? "用户昵称" : entry.author);
        authorView.setTextColor(Color.parseColor("#101010"));
        authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        authorView.setTypeface(null, Typeface.BOLD);
        meta.addView(authorView);

        TextView dateView = new TextView(host.getContext());
        dateView.setText(formatDateLabel(entry.dateTime));
        dateView.setTextColor(Color.parseColor("#80868B"));
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = host.dpToPx(2);
        meta.addView(dateView, dateLp);
        topRow.addView(meta);

        topRow.addView(createIconActionButton(
                R.drawable.lolib_ic_impress_comment_edit, "编辑", () -> showEditPanel(entry)));
        topRow.addView(createIconActionButton(
                R.drawable.lolib_ic_impress_comment_delete, "删除", () -> onDeleteClicked(entry)));

        card.addView(topRow);

        if (!entry.text.isEmpty()) {
            TextView body = new TextView(host.getContext());
            body.setText(entry.text);
            body.setTextColor(Color.parseColor("#333333"));
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyLp.topMargin = host.dpToPx(12);
            card.addView(body, bodyLp);
        }
        return card;
    }

    private ImageButton createIconActionButton(int iconRes, String description, Runnable action) {
        ImageButton button = new ImageButton(host.getContext());
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setBackgroundResource(android.R.color.transparent);
        int size = host.dpToPx(40);
        button.setPadding(host.dpToPx(8), host.dpToPx(8), host.dpToPx(8), host.dpToPx(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(host.dpToPx(4));
        button.setLayoutParams(lp);
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            button.setBackgroundResource(rippleAttr.resourceId);
        }
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private View createEditCard() {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_impress_comment_card);
        int pad = host.dpToPx(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(8));
        card.setLayoutParams(cardLp);

        editInput = new EditText(host.getContext());
        editInput.setHint("描述内容");
        editInput.setHintTextColor(Color.parseColor("#999999"));
        editInput.setTextColor(Color.parseColor("#101010"));
        editInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        editInput.setBackgroundResource(R.drawable.lolib_bg_impress_comment_input);
        editInput.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        editInput.setGravity(Gravity.TOP | Gravity.START);
        editInput.setMinLines(4);
        editInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        card.addView(editInput);
        return card;
    }

    private View createEditButtonRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        int hPad = host.dpToPx(16);
        row.setPadding(hPad, host.dpToPx(8), hPad, host.dpToPx(16));

        TextView cancel = createFooterButton("取消", false);
        cancel.setOnClickListener(v -> hideEditPanel());
        row.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = createFooterButton("保存", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveLp.setMarginStart(host.dpToPx(12));
        save.setLayoutParams(saveLp);
        save.setOnClickListener(v -> onSaveEditClicked());
        row.addView(save);
        return row;
    }

    private TextView createFooterButton(String label, boolean primary) {
        TextView button = new TextView(host.getContext());
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(null, Typeface.BOLD);
        int vPad = host.dpToPx(14);
        button.setPadding(host.dpToPx(16), vPad, host.dpToPx(16), vPad);
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackgroundResource(R.drawable.lolib_bg_impress_primary_button);
        } else {
            button.setTextColor(Color.parseColor("#101010"));
            button.setBackgroundResource(R.drawable.lolib_bg_impress_comment_cancel_button);
        }
        return button;
    }

    private void showEditPanel(CommentEntry entry) {
        editingEntry = entry;
        if (editInput != null) {
            editInput.setText(entry.text);
            editInput.setSelection(entry.text.length());
        }
        if (listPanel != null) {
            listPanel.setVisibility(View.GONE);
        }
        if (editPanel != null) {
            editPanel.setVisibility(View.VISIBLE);
        }
    }

    private void hideEditPanel() {
        editingEntry = null;
        if (editPanel != null) {
            editPanel.setVisibility(View.GONE);
        }
        if (listPanel != null) {
            listPanel.setVisibility(View.VISIBLE);
        }
    }

    private void onSaveEditClicked() {
        if (editingEntry == null || editInput == null) {
            return;
        }
        String text = editInput.getText() != null ? editInput.getText().toString().trim() : "";
        if (text.isEmpty()) {
            host.toastTodo("请填写批注内容");
            return;
        }
        String author = editingEntry.author.isEmpty()
                ? host.getCommentAuthorName() : editingEntry.author;
        Log.i(TAG, "edit_comment id=" + editingEntry.id + " chars=" + text.length());
        host.editCommentWithText(editingEntry.id, author, text);
        hideEditPanel();
        scheduleReload();
    }

    private void onDeleteClicked(CommentEntry entry) {
        if (entry.id.isEmpty()) {
            return;
        }
        Log.i(TAG, "delete_comment id=" + entry.id);
        host.deleteCommentWithId(entry.id);
        scheduleReload();
    }

    private void scheduleReload() {
        if (listContainer != null) {
            listContainer.postDelayed(this::reloadComments, 450L);
        } else {
            reloadComments();
        }
    }

    private static String formatDateLabel(String dateTime) {
        return ImpressCommentPickerController.formatCommentDateFromRaw(dateTime);
    }
}
