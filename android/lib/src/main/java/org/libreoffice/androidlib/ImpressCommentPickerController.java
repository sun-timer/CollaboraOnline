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
import android.widget.TextView;

import org.libreoffice.androidlib.impress.ImpressSubpageHeader;
import org.libreoffice.androidlib.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Impress 功能面板 — 插入批注二级页。
 */
final class ImpressCommentPickerController {

    private static final String TAG = "ImpressCommentPicker";

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        String getCommentAuthorName();

        void toastTodo(String text);

        void insertCommentWithText(String text);

        void onBack();
    }

    private final Host host;
    private View rootView;
    private EditText contentInput;

    ImpressCommentPickerController(Host host) {
        this.host = host;
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
        root.addView(ImpressSubpageHeader.createDivider(host.getContext()));
        root.addView(createCommentCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(createButtonRow());

        rootView = root;
        return rootView;
    }

    private View createHeader() {
        return ImpressSubpageHeader.create(
                host.getContext(), host::dpToPx, "批注", v -> host.onBack());
    }

    private View createCommentCard() {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_impress_comment_card);
        int pad = host.dpToPx(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(host.dpToPx(16), host.dpToPx(8), host.dpToPx(16), host.dpToPx(8));
        card.setLayoutParams(cardLp);

        card.addView(createAuthorRow());

        contentInput = new EditText(host.getContext());
        contentInput.setHint("描述内容");
        contentInput.setHintTextColor(Color.parseColor("#999999"));
        contentInput.setTextColor(Color.parseColor("#101010"));
        contentInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        contentInput.setBackgroundResource(R.drawable.lolib_bg_impress_comment_input);
        contentInput.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        contentInput.setGravity(Gravity.TOP | Gravity.START);
        contentInput.setMinLines(4);
        contentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = host.dpToPx(12);
        card.addView(contentInput, inputLp);
        return card;
    }

    private View createAuthorRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout avatarWrap = new FrameLayout(host.getContext());
        int avatarSize = host.dpToPx(40);
        avatarWrap.setBackgroundResource(R.drawable.lolib_bg_impress_comment_avatar);
        ImageView avatar = new ImageView(host.getContext());
        avatar.setImageResource(R.drawable.lolib_ic_impress_insert_comment);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconPad = host.dpToPx(8);
        avatar.setPadding(iconPad, iconPad, iconPad, iconPad);
        avatarWrap.addView(avatar, new FrameLayout.LayoutParams(avatarSize, avatarSize));
        row.addView(avatarWrap, new LinearLayout.LayoutParams(avatarSize, avatarSize));

        LinearLayout meta = new LinearLayout(host.getContext());
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaLp.setMarginStart(host.dpToPx(12));
        meta.setLayoutParams(metaLp);

        String author = host.getCommentAuthorName();
        if (author == null || author.trim().isEmpty()) {
            author = "用户昵称";
        }
        TextView nameView = new TextView(host.getContext());
        nameView.setText(author);
        nameView.setTextColor(Color.parseColor("#101010"));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameView.setTypeface(null, Typeface.BOLD);
        meta.addView(nameView);

        TextView dateView = new TextView(host.getContext());
        dateView.setText(formatCommentDate(new Date()));
        dateView.setTextColor(Color.parseColor("#80868B"));
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = host.dpToPx(2);
        meta.addView(dateView, dateLp);

        row.addView(meta);
        return row;
    }

    private View createButtonRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        int hPad = host.dpToPx(16);
        row.setPadding(hPad, host.dpToPx(8), hPad, host.dpToPx(16));

        TextView cancel = createFooterButton("取消", false);
        cancel.setOnClickListener(v -> host.onBack());
        row.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = createFooterButton("保存", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveLp.setMarginStart(host.dpToPx(12));
        save.setLayoutParams(saveLp);
        save.setOnClickListener(v -> onSaveClicked());
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

    private void onSaveClicked() {
        String text = contentInput != null && contentInput.getText() != null
                ? contentInput.getText().toString().trim() : "";
        if (text.isEmpty()) {
            host.toastTodo("请填写批注内容");
            return;
        }
        Log.i(TAG, "save_comment chars=" + text.length());
        host.insertCommentWithText(text);
    }

    static String formatCommentDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
        return formatter.format(date != null ? date : new Date());
    }

    /** 将 Core/Web 批注原始 dateTime 格式化为列表展示文案。 */
    static String formatCommentDateFromRaw(String dateTime) {
        if (dateTime == null || dateTime.trim().isEmpty()) {
            return formatCommentDate(new Date());
        }
        String normalized = dateTime.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) {
            normalized = normalized.substring(0, comma);
        }
        if (!normalized.endsWith("Z") && normalized.contains("T")) {
            normalized = normalized + "Z";
        }
        try {
            SimpleDateFormat isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoParser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date parsed = isoParser.parse(normalized);
            if (parsed != null) {
                return formatCommentDate(parsed);
            }
        } catch (java.text.ParseException ignored) {
            // fall through
        }
        try {
            SimpleDateFormat isoParserNoMs = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoParserNoMs.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date parsed = isoParserNoMs.parse(normalized);
            if (parsed != null) {
                return formatCommentDate(parsed);
            }
        } catch (java.text.ParseException ignored) {
            // fall through
        }
        return dateTime.trim();
    }
}
