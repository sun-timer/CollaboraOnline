package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Writer 功能面板 — 纸张大小二级页（自定义宽/高 + CO 预设列表）。
 */
final class PaperSizePickerController {

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void onBack();

        void onPresetSelected(WriterLayoutCatalog.PaperSizeOption option);

        void onCustomSizeApplied(double widthCm, double heightCm);
    }

    private static final double MIN_CM = 5.0;
    private static final double MAX_CM = 120.0;
    private static final double STEP_CM = 0.1;

    private final Host host;
    private String selectedPresetId;
    private boolean customSelected;
    private double widthCm;
    private double heightCm;

    private View rootView;
    private LinearLayout presetList;
    private TextView widthValueView;
    private TextView heightValueView;

    PaperSizePickerController(Host host, String currentLabel, double initialWidthCm, double initialHeightCm) {
        this.host = host;
        WriterLayoutCatalog.PaperSizeOption matched = WriterLayoutCatalog.findPaperByLabel(currentLabel);
        if (matched.label.equals(currentLabel) || matched.id.equals(currentLabel)) {
            selectedPresetId = matched.id;
            customSelected = false;
        } else {
            selectedPresetId = null;
            customSelected = true;
        }
        this.widthCm = initialWidthCm;
        this.heightCm = initialHeightCm;
    }

    View buildRootView() {
        if (rootView != null) {
            refreshPresetList();
            refreshStepperValues();
            return rootView;
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);

        root.addView(createHeader());
        root.addView(createScrollBody());

        rootView = root;
        refreshPresetList();
        refreshStepperValues();
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
        title.setText("纸张大小");
        title.setTextColor(Color.parseColor("#333333"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(8));
        header.addView(title, titleLp);

        LinearLayout wrapper = new LinearLayout(host.getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(header);
        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return wrapper;
    }

    private View createScrollBody() {
        androidx.core.widget.NestedScrollView scroll =
                new androidx.core.widget.NestedScrollView(host.getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int hPad = host.dpToPx(16);
        content.setPadding(hPad, host.dpToPx(16), hPad, host.dpToPx(16));

        content.addView(createCustomSection());
        content.addView(createSectionDivider());
        presetList = new LinearLayout(host.getContext());
        presetList.setOrientation(LinearLayout.VERTICAL);
        content.addView(presetList);

        scroll.addView(content);
        return scroll;
    }

    private View createCustomSection() {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);

        TextView caption = new TextView(host.getContext());
        caption.setText("自定义");
        caption.setTextColor(Color.parseColor("#101010"));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.bottomMargin = host.dpToPx(12);
        section.addView(caption, captionLp);

        section.addView(createStepperRow(true));
        View spacer = new View(host.getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(12)));
        section.addView(spacer);
        section.addView(createStepperRow(false));
        return section;
    }

    private View createStepperRow(boolean widthRow) {
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
        icon.setImageResource(widthRow ? R.drawable.lolib_ic_paper_width : R.drawable.lolib_ic_paper_height);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconSize = host.dpToPx(24);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(host.dpToPx(8));
        valueBox.addView(icon, iconLp);

        TextView label = new TextView(host.getContext());
        label.setText(widthRow ? "宽" : "高");
        label.setTextColor(Color.parseColor("#333333"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueBox.addView(label);

        TextView value = new TextView(host.getContext());
        value.setGravity(Gravity.END);
        value.setTextColor(Color.parseColor("#333333"));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        value.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueLp.setMarginStart(host.dpToPx(8));
        valueBox.addView(value, valueLp);

        TextView unit = new TextView(host.getContext());
        unit.setText(" cm");
        unit.setTextColor(Color.parseColor("#333333"));
        unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueBox.addView(unit);

        if (widthRow) {
            widthValueView = value;
        } else {
            heightValueView = value;
        }

        ImageView minus = createStepperButton(R.drawable.lolib_ic_calc_stepper_minus);
        minus.setOnClickListener(v -> adjustDimension(widthRow, -STEP_CM));
        ImageView plus = createStepperButton(R.drawable.lolib_ic_calc_stepper_plus);
        plus.setOnClickListener(v -> adjustDimension(widthRow, STEP_CM));

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

    private View createSectionDivider() {
        View divider = new View(host.getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1));
        lp.topMargin = host.dpToPx(20);
        lp.bottomMargin = host.dpToPx(12);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(Color.parseColor("#14000000"));
        return divider;
    }

    private void adjustDimension(boolean widthRow, double deltaCm) {
        if (widthRow) {
            widthCm = clampCm(widthCm + deltaCm);
        } else {
            heightCm = clampCm(heightCm + deltaCm);
        }
        customSelected = true;
        selectedPresetId = null;
        refreshStepperValues();
        refreshPresetList();
        host.onCustomSizeApplied(widthCm, heightCm);
    }

    private void refreshStepperValues() {
        if (widthValueView != null) {
            widthValueView.setText(formatCm(widthCm));
        }
        if (heightValueView != null) {
            heightValueView.setText(formatCm(heightCm));
        }
    }

    private void refreshPresetList() {
        if (presetList == null) {
            return;
        }
        presetList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        WriterLayoutCatalog.PaperSizeOption[] options = WriterLayoutCatalog.PAPER_SIZES;
        for (int i = 0; i < options.length; i++) {
            WriterLayoutCatalog.PaperSizeOption option = options[i];
            View row = inflater.inflate(R.layout.lolib_item_option_picker_row, presetList, false);
            TextView name = row.findViewById(R.id.option_picker_item_name);
            ImageView check = row.findViewById(R.id.option_picker_item_check);
            name.setText(option.label);
            boolean selected = !customSelected && option.id.equals(selectedPresetId);
            if (selected) {
                name.setTextColor(Color.parseColor("#1278D9"));
                check.setVisibility(View.VISIBLE);
            } else {
                name.setTextColor(Color.parseColor("#333333"));
                check.setVisibility(View.GONE);
            }
            row.setOnClickListener(v -> {
                selectedPresetId = option.id;
                customSelected = false;
                refreshPresetList();
                host.onPresetSelected(option);
            });
            presetList.addView(row);
            if (i < options.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(Color.parseColor("#14000000"));
                presetList.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    static String formatCustomLabel(double widthCm, double heightCm) {
        return formatCm(widthCm) + " × " + formatCm(heightCm) + " cm";
    }

    static int cmToHmm(double cm) {
        return (int) Math.round(cm * 1000);
    }

    private static String formatCm(double cm) {
        if (Math.abs(cm - Math.rint(cm)) < 0.001) {
            return String.valueOf((int) Math.rint(cm));
        }
        return String.format(java.util.Locale.US, "%.1f", cm);
    }

    private static double clampCm(double value) {
        return Math.max(MIN_CM, Math.min(MAX_CM, Math.round(value * 10.0) / 10.0));
    }
}
