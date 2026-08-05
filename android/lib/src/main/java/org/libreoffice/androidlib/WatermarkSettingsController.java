package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
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

/**
 * Writer 功能面板 — 水印设置二级页（文本 / 字体 / 角度 / 透明度）。
 */
final class WatermarkSettingsController {

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void onBack();

        void onConfirm(String text, String font, int angle, int transparency);

        void pickFont(String currentFont, FontPickCallback callback);

        android.graphics.Typeface resolveFontPreviewTypeface(String fontName);
    }

    interface FontPickCallback {
        void onFontPicked(String font);
    }

    private static final int MIN_ANGLE = 0;
    private static final int MAX_ANGLE = 360;
    private static final int MIN_TRANSPARENCY = 0;
    private static final int MAX_TRANSPARENCY = 100;

    private final Host host;
    private String text;
    private String font;
    private int angle;
    private int transparency;

    private View rootView;
    private EditText textInput;
    private TextView fontValueView;
    private TextView angleValueView;
    private TextView opacityValueView;

    WatermarkSettingsController(Host host, String text, String font, int angle, int transparency) {
        this.host = host;
        this.text = text == null ? "" : text;
        this.font = font == null || font.isEmpty() ? "" : font;
        this.angle = clamp(angle, MIN_ANGLE, MAX_ANGLE);
        this.transparency = clamp(transparency, MIN_TRANSPARENCY, MAX_TRANSPARENCY);
    }

    View buildRootView() {
        if (rootView != null) {
            refreshValues();
            return rootView;
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);

        root.addView(createHeader());
        root.addView(createScrollContent());
        root.addView(createConfirmButton());

        rootView = root;
        refreshValues();
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
        back.setOnClickListener(v -> host.onBack());
        header.addView(back, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView title = new TextView(host.getContext());
        title.setText("水印");
        title.setTextColor(Color.parseColor("#333333"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(8));
        header.addView(title, titleLp);

        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        LinearLayout wrapper = new LinearLayout(host.getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(header);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return wrapper;
    }

    private View createScrollContent() {
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(host.getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int hPad = host.dpToPx(16);
        content.setPadding(hPad, host.dpToPx(20), hPad, host.dpToPx(8));

        content.addView(createSection("文本", createTextField()));
        content.addView(createSectionSpacer());
        content.addView(createSection("字体", createFontRow()));
        content.addView(createSectionSpacer());
        content.addView(createSection("角度", createStepperRow(
                R.drawable.lolib_ic_watermark_angle, true, "度")));
        content.addView(createSectionSpacer());
        content.addView(createSection("透明度", createStepperRow(
                R.drawable.lolib_ic_watermark_opacity, false, "%")));

        scroll.addView(content);
        return scroll;
    }

    private View createSection(String label, View body) {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        TextView caption = new TextView(host.getContext());
        caption.setText(label);
        caption.setTextColor(Color.parseColor("#101010"));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.bottomMargin = host.dpToPx(12);
        section.addView(caption, captionLp);
        section.addView(body);
        return section;
    }

    private View createSectionSpacer() {
        View spacer = new View(host.getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(20)));
        return spacer;
    }

    private View createTextField() {
        textInput = new EditText(host.getContext());
        textInput.setHint("水印文本");
        textInput.setTextColor(Color.parseColor("#101010"));
        textInput.setHintTextColor(Color.parseColor("#80868B"));
        textInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textInput.setSingleLine(true);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT);
        textInput.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int pad = host.dpToPx(16);
        textInput.setPadding(pad, host.dpToPx(14), pad, host.dpToPx(14));
        textInput.setMinimumHeight(host.dpToPx(52));
        return textInput;
    }

    private View createFontRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        row.setPadding(host.dpToPx(16), host.dpToPx(14), host.dpToPx(12), host.dpToPx(14));
        row.setMinimumHeight(host.dpToPx(52));

        fontValueView = new TextView(host.getContext());
        fontValueView.setTextColor(Color.parseColor("#333333"));
        fontValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(fontValueView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(host.getContext());
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#80868B"));
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(arrow);

        row.setOnClickListener(v -> host.pickFont(font, picked -> {
            font = picked;
            refreshValues();
        }));
        return row;
    }

    private View createStepperRow(int iconRes, boolean angleRow, String suffix) {
        LinearLayout track = new LinearLayout(host.getContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int trackPad = host.dpToPx(8);
        track.setPadding(trackPad, trackPad, trackPad, trackPad);
        track.setMinimumHeight(host.dpToPx(56));

        LinearLayout valueBox = new LinearLayout(host.getContext());
        valueBox.setOrientation(LinearLayout.HORIZONTAL);
        valueBox.setGravity(Gravity.CENTER_VERTICAL);
        valueBox.setBackgroundResource(R.drawable.lolib_bg_impress_table_stepper_value);
        int valuePadH = host.dpToPx(12);
        valueBox.setPadding(valuePadH, host.dpToPx(10), valuePadH, host.dpToPx(10));
        valueBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconSize = host.dpToPx(24);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(host.dpToPx(8));
        valueBox.addView(icon, iconLp);

        TextView value = new TextView(host.getContext());
        value.setGravity(Gravity.CENTER);
        value.setTextColor(Color.parseColor("#333333"));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        value.setTypeface(null, Typeface.BOLD);
        valueBox.addView(value, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView unit = new TextView(host.getContext());
        unit.setText(suffix);
        unit.setTextColor(Color.parseColor("#333333"));
        unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueBox.addView(unit);

        if (angleRow) {
            angleValueView = value;
        } else {
            opacityValueView = value;
        }

        ImageView minus = createStepperButton(R.drawable.lolib_ic_calc_stepper_minus);
        minus.setOnClickListener(v -> adjustValue(angleRow, -1));
        ImageView plus = createStepperButton(R.drawable.lolib_ic_calc_stepper_plus);
        plus.setOnClickListener(v -> adjustValue(angleRow, 1));

        track.addView(valueBox);
        track.addView(minus);
        track.addView(plus);
        return track;
    }

    private ImageView createStepperButton(int iconRes) {
        ImageView button = new ImageView(host.getContext());
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int size = host.dpToPx(48);
        button.setMinimumWidth(size);
        button.setMinimumHeight(size);
        button.setPadding(host.dpToPx(12), host.dpToPx(12), host.dpToPx(12), host.dpToPx(12));
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            button.setBackgroundResource(rippleAttr.resourceId);
        }
        return button;
    }

    private View createConfirmButton() {
        TextView button = new TextView(host.getContext());
        button.setText("确定");
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setBackgroundResource(R.drawable.lolib_bg_writer_primary_button);
        int vPad = host.dpToPx(14);
        button.setPadding(host.dpToPx(16), vPad, host.dpToPx(16), vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(host.dpToPx(32), host.dpToPx(16), host.dpToPx(32), host.dpToPx(24));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> {
            if (textInput != null && textInput.getText() != null) {
                text = textInput.getText().toString().trim();
            }
            host.onConfirm(text, font, angle, transparency);
        });
        return button;
    }

    private void adjustValue(boolean angleRow, int delta) {
        if (angleRow) {
            angle = clamp(angle + delta, MIN_ANGLE, MAX_ANGLE);
        } else {
            transparency = clamp(transparency + delta, MIN_TRANSPARENCY, MAX_TRANSPARENCY);
        }
        refreshValues();
    }

    private void refreshValues() {
        if (textInput != null) {
            CharSequence current = textInput.getText();
            if (current == null || !text.contentEquals(current)) {
                textInput.setText(text);
            }
        }
        if (fontValueView != null) {
            fontValueView.setText(font);
            android.graphics.Typeface preview = host.resolveFontPreviewTypeface(font);
            if (preview != null) {
                fontValueView.setTypeface(preview);
            }
        }
        if (angleValueView != null) {
            angleValueView.setText(String.valueOf(angle));
        }
        if (opacityValueView != null) {
            opacityValueView.setText(String.valueOf(transparency));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
