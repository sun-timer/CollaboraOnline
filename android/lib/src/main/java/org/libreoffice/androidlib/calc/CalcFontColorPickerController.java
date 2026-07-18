package org.libreoffice.androidlib.calc;

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
import android.widget.ScrollView;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

/**
 * Calc 功能面板 — 颜色二级页（字体 / 背景 / 边框，24 + 12 色块，6 列网格）。
 */
public final class CalcFontColorPickerController {

    private static final String TAG = "CalcColorPicker";
    private static final int GRID_COLS = 6;
    private static final int ROW_HEIGHT_DP = 48;
    private static final int ICON_SIZE_DP = 32;
    private static final int GRID_PAD_DP = 8;
    private static final int ROW_GAP_DP = 4;
    private static final int BLOCK_GAP_DP = 12;

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void onColorSelected(int rgb);

        void onBack();
    }

    private final Host host;

    public CalcFontColorPickerController(Host host) {
        this.host = host;
    }

    public View buildRootView(String title) {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(createHeader(title));

        ScrollView scroll = new ScrollView(host.getContext());
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int sidePad = host.dpToPx(16);
        content.setPadding(sidePad, host.dpToPx(4), sidePad, host.dpToPx(16));

        for (int b = 0; b < CalcFontColorCatalog.BLOCKS.length; b++) {
            if (b > 0) {
                View gap = new View(host.getContext());
                content.addView(gap, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(BLOCK_GAP_DP)));
            }
            content.addView(createColorBlock(CalcFontColorCatalog.BLOCKS[b].entries));
        }
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private View createHeader(String titleText) {
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
        title.setText(titleText);
        title.setTextColor(Color.parseColor("#101010"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(4));
        header.addView(title, titleLp);
        return header;
    }

    private View createColorBlock(CalcFontColorCatalog.Entry[] entries) {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int pad = host.dpToPx(GRID_PAD_DP);
        card.setPadding(pad, pad, pad, pad);

        int rowHeight = host.dpToPx(ROW_HEIGHT_DP);
        int iconSize = host.dpToPx(ICON_SIZE_DP);
        int rowGap = host.dpToPx(ROW_GAP_DP);
        LinearLayout row = null;

        for (int i = 0; i < entries.length; i++) {
            if (i % GRID_COLS == 0) {
                row = new LinearLayout(host.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                if (i > 0) {
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
                    rowLp.topMargin = rowGap;
                    row.setLayoutParams(rowLp);
                } else {
                    row.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
                }
                card.addView(row);
            }
            CalcFontColorCatalog.Entry entry = entries[i];
            FrameLayout cell = new FrameLayout(host.getContext());
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, rowHeight, 1f));
            cell.setForeground(host.getContext().getDrawable(android.R.drawable.list_selector_background));

            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(entry.iconResId);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconLp.gravity = Gravity.CENTER;
            cell.addView(icon, iconLp);

            cell.setOnClickListener(v -> onColorClicked(entry));
            if (row != null) {
                row.addView(cell);
            }
        }
        return card;
    }

    private void onColorClicked(CalcFontColorCatalog.Entry entry) {
        Log.i(TAG, "color_selected index=" + entry.index
                + " rgb=#" + Integer.toHexString(entry.rgb).toUpperCase());
        host.onColorSelected(entry.rgb);
    }
}
