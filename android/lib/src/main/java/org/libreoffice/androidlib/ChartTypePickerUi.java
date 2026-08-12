package org.libreoffice.androidlib;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared chart-type picker grid for Writer / Calc / Impress function panels.
 * Layout follows Figma 258:10319.
 */
public final class ChartTypePickerUi {
    /** Figma 258:10319：预览区高度 144px → 72dp（原 56dp 偏小）。 */
    private static final int PREVIEW_HEIGHT_DP = 72;
    private static final int CARD_PAD_H_DP = 6;
    private static final int CARD_PAD_TOP_DP = 8;
    private static final int CARD_PAD_BOTTOM_DP = 10;
    private static final int GRID_GAP_DP = 8;
    private static final int ROW_GAP_DP = 12;
    private static final int SECTION_GAP_DP = 16;
    private static final int MAX_COLUMNS = 3;
    private static final int COLOR_TITLE = Color.parseColor("#101010");
    private static final int COLOR_SECTION = Color.parseColor("#80868B");

    public interface DpToPx {
        int dpToPx(int dp);
    }

    public interface OnChartTypeSelected {
        void onSelected(String unoChartType, String label);
    }

    public static final class ChartTypeOption {
        public final String label;
        public final int previewRes;
        public final String unoType;

        public ChartTypeOption(String label, int previewRes, String unoType) {
            this.label = label;
            this.previewRes = previewRes;
            this.unoType = unoType;
        }
    }

    public static final String[] CATEGORY_TITLES = {"饼图", "线图", "柱图"};

    public static final ChartTypeOption[][] TYPE_ROWS = new ChartTypeOption[][] {
            {
                    new ChartTypeOption("基础饼图", R.drawable.lolib_chart_preview_pie_basic, "pie"),
                    new ChartTypeOption("基础饼图(圆角)", R.drawable.lolib_chart_preview_pie_rounded, "pie-rounded"),
                    new ChartTypeOption("变形饼图", R.drawable.lolib_chart_preview_pie_exploded, "pie-exploded"),
            },
            {
                    new ChartTypeOption("折线图", R.drawable.lolib_chart_preview_line_basic, "line"),
                    new ChartTypeOption("曲线折线图", R.drawable.lolib_chart_preview_line_curve, "line-curve"),
            },
            {
                    new ChartTypeOption("基础柱状图", R.drawable.lolib_chart_preview_column_basic, "column"),
                    new ChartTypeOption("基础条形图", R.drawable.lolib_chart_preview_bar_basic, "bar"),
                    new ChartTypeOption("堆叠柱状图", R.drawable.lolib_chart_preview_column_stacked, "column-stacked"),
            },
    };

    private ChartTypePickerUi() {
    }

    public static View buildPickerBody(Context context, DpToPx dpToPx, OnChartTypeSelected listener) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int sectionGap = dpToPx.dpToPx(SECTION_GAP_DP);
        int rowGap = dpToPx.dpToPx(ROW_GAP_DP);
        for (int section = 0; section < TYPE_ROWS.length; section++) {
            root.addView(createSectionLabel(context, dpToPx, CATEGORY_TITLES[section]));
            LinearLayout row = createChartTypeRow(context, dpToPx, TYPE_ROWS[section], listener);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (section > 0) {
                rowLp.topMargin = rowGap;
            }
            row.setLayoutParams(rowLp);
            root.addView(row);
            if (section < TYPE_ROWS.length - 1) {
                View spacer = new View(context);
                root.addView(spacer, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, sectionGap));
            }
        }
        return root;
    }

    private static TextView createSectionLabel(Context context, DpToPx dpToPx, String title) {
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(COLOR_SECTION);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(dpToPx.dpToPx(2), dpToPx.dpToPx(14), dpToPx.dpToPx(2), dpToPx.dpToPx(6));
        return label;
    }

    private static LinearLayout createChartTypeRow(Context context, DpToPx dpToPx,
            ChartTypeOption[] options, OnChartTypeSelected listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int gap = dpToPx.dpToPx(GRID_GAP_DP);
        for (int slot = 0; slot < MAX_COLUMNS; slot++) {
            LinearLayout.LayoutParams lp = createEqualWidthSlotParams(
                    MAX_COLUMNS, slot, gap, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (slot < options.length) {
                row.addView(createChartTypeCard(context, dpToPx, options[slot], listener), lp);
            } else {
                row.addView(new View(context), lp);
            }
        }
        return row;
    }

    public static View createChartTypeCard(Context context, DpToPx dpToPx, ChartTypeOption option,
            OnChartTypeSelected listener) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.lolib_bg_chart_type_card);
        int padH = dpToPx.dpToPx(CARD_PAD_H_DP);
        card.setPadding(padH, dpToPx.dpToPx(CARD_PAD_TOP_DP), padH, dpToPx.dpToPx(CARD_PAD_BOTTOM_DP));

        ImageView preview = new ImageView(context);
        preview.setImageResource(option.previewRes);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx.dpToPx(PREVIEW_HEIGHT_DP)));

        TextView label = new TextView(context);
        label.setText(option.label);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(COLOR_TITLE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setMaxLines(2);
        label.setPadding(0, dpToPx.dpToPx(4), 0, 0);
        card.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> listener.onSelected(option.unoType, option.label));
        return card;
    }

    private static LinearLayout.LayoutParams createEqualWidthSlotParams(
            int maxCols, int slotIndex, int gap, int heightPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1f);
        if (slotIndex < maxCols - 1) {
            lp.setMarginEnd(gap);
        }
        return lp;
    }
}
