package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

/**
 * Impress 功能面板 — 插入表格二级页（行 / 列 stepper + 插入按钮）。
 */
final class ImpressInsertTablePickerController {

    private static final String TAG = "ImpressInsertTablePicker";

    private static final int DEFAULT_ROWS = 2;
    private static final int DEFAULT_COLUMNS = 2;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 20;

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void insertTable(int rows, int columns);

        void onBack();
    }

    private final Host host;
    private int rowCount = DEFAULT_ROWS;
    private int columnCount = DEFAULT_COLUMNS;

    private View rootView;
    private TextView rowValueView;
    private TextView columnValueView;

    ImpressInsertTablePickerController(Host host) {
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
        root.addView(createStepperSection("行", R.drawable.lolib_ic_impress_table_rows, true));
        root.addView(createStepperSection("列", R.drawable.lolib_ic_impress_table_columns, false));
        root.addView(createPrimaryButton());

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
        title.setText("表格");
        title.setTextColor(Color.parseColor("#101010"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(title, titleLp);
        return header;
    }

    private View createStepperSection(String label, int iconRes, boolean rows) {
        LinearLayout section = new LinearLayout(host.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        int hPad = host.dpToPx(16);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionLp.setMargins(hPad, rows ? host.dpToPx(8) : host.dpToPx(16), hPad, 0);
        section.setLayoutParams(sectionLp);

        TextView caption = new TextView(host.getContext());
        caption.setText(label);
        caption.setTextColor(Color.parseColor("#101010"));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        caption.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.bottomMargin = host.dpToPx(8);
        section.addView(caption, captionLp);
        section.addView(createStepperRow(iconRes, rows));
        return section;
    }

    private View createStepperRow(int iconRes, boolean rows) {
        LinearLayout track = new LinearLayout(host.getContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setBackgroundResource(R.drawable.lolib_bg_impress_table_stepper_track);
        int trackPad = host.dpToPx(8);
        track.setPadding(trackPad, trackPad, trackPad, trackPad);
        track.setMinimumHeight(host.dpToPx(56));

        LinearLayout valueBox = new LinearLayout(host.getContext());
        valueBox.setOrientation(LinearLayout.HORIZONTAL);
        valueBox.setGravity(Gravity.CENTER_VERTICAL);
        valueBox.setBackgroundResource(R.drawable.lolib_bg_impress_table_stepper_value);
        int valuePadH = host.dpToPx(12);
        int valuePadV = host.dpToPx(10);
        valueBox.setPadding(valuePadH, valuePadV, valuePadH, valuePadV);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueBox.setLayoutParams(valueLp);

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
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        value.setTypeface(null, Typeface.BOLD);
        valueBox.addView(value, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (rows) {
            rowValueView = value;
        } else {
            columnValueView = value;
        }

        ImageView minus = createStepperButton(R.drawable.lolib_ic_calc_stepper_minus);
        minus.setOnClickListener(v -> adjustCount(rows, -1));

        ImageView plus = createStepperButton(R.drawable.lolib_ic_calc_stepper_plus);
        plus.setOnClickListener(v -> adjustCount(rows, 1));

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

    private void adjustCount(boolean rows, int delta) {
        if (rows) {
            rowCount = clamp(rowCount + delta);
        } else {
            columnCount = clamp(columnCount + delta);
        }
        refreshValues();
    }

    private void refreshValues() {
        if (rowValueView != null) {
            rowValueView.setText(String.valueOf(rowCount));
        }
        if (columnValueView != null) {
            columnValueView.setText(String.valueOf(columnCount));
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN_COUNT, Math.min(MAX_COUNT, value));
    }

    private View createPrimaryButton() {
        TextView button = new TextView(host.getContext());
        button.setText("插入表格");
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.lolib_bg_impress_primary_button);
        int vPad = host.dpToPx(14);
        button.setPadding(host.dpToPx(16), vPad, host.dpToPx(16), vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(host.dpToPx(16), host.dpToPx(24), host.dpToPx(16), host.dpToPx(16));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> onInsertClicked());
        return button;
    }

    private void onInsertClicked() {
        Log.i(TAG, "insert_table rows=" + rowCount + " columns=" + columnCount);
        host.insertTable(rowCount, columnCount);
    }
}
