package org.libreoffice.androidlib.impress;

import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.libreoffice.androidlib.R;

/**
 * Impress 母版幻灯片 — 纯色二级页（12 + 58 色块，6 列，F2F3F5 圆角卡片）。
 */
public final class ImpressSolidColorPickerController {

    private static final String TAG = "ImpressSolidColor";
    private static final int GRID_COLS = 6;
    private static final int SWATCH_SIZE_DP = 32;
    private static final int GRID_PAD_DP = 12;
    private static final int ROW_GAP_DP = 20;
    private static final int BLOCK_GAP_DP = 12;
    private static final int CONTENT_SIDE_PAD_DP = 16;

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        Integer getSelectedIndex();

        void onColorSelected(int index, int rgb);

        void onBack();
    }

    private final Host host;
    private Integer selectedIndex;
    private final View[][] checkViews = new View[ImpressSolidColorCatalog.BLOCKS.length][];

    public ImpressSolidColorPickerController(Host host) {
        this.host = host;
        this.selectedIndex = host.getSelectedIndex();
    }

    public View buildRootView() {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(createHeader());
        root.addView(ImpressSubpageHeader.createDivider(host.getContext()));

        ScrollView scroll = new ScrollView(host.getContext());
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int sidePad = host.dpToPx(CONTENT_SIDE_PAD_DP);
        content.setPadding(sidePad, host.dpToPx(4), sidePad, host.dpToPx(16));

        for (int b = 0; b < ImpressSolidColorCatalog.BLOCKS.length; b++) {
            if (b > 0) {
                View gap = new View(host.getContext());
                content.addView(gap, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(BLOCK_GAP_DP)));
            }
            content.addView(createColorBlock(b, ImpressSolidColorCatalog.BLOCKS[b].entries));
        }
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private View createHeader() {
        return ImpressSubpageHeader.create(
                host.getContext(), host::dpToPx, "纯色", v -> host.onBack());
    }

    private View createColorBlock(int blockIndex, ImpressSolidColorCatalog.Entry[] entries) {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int pad = host.dpToPx(GRID_PAD_DP);
        card.setPadding(pad, pad, pad, pad);

        checkViews[blockIndex] = new View[entries.length];
        int swatchSize = host.dpToPx(SWATCH_SIZE_DP);
        int rowGap = host.dpToPx(ROW_GAP_DP);
        int innerWidth = host.getContext().getResources().getDisplayMetrics().widthPixels
                - host.dpToPx(CONTENT_SIDE_PAD_DP * 2 + GRID_PAD_DP * 2);
        int gap = Math.max(0, (innerWidth - GRID_COLS * swatchSize) / (GRID_COLS - 1));
        LinearLayout row = null;

        for (int i = 0; i < entries.length; i++) {
            if (i % GRID_COLS == 0) {
                row = new LinearLayout(host.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.START);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, swatchSize);
                if (i > 0) {
                    rowLp.topMargin = rowGap;
                }
                row.setLayoutParams(rowLp);
                card.addView(row);
            }
            ImpressSolidColorCatalog.Entry entry = entries[i];
            FrameLayout cell = createColorCell(entry);
            checkViews[blockIndex][i] = cell.findViewWithTag("check");
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            if (i % GRID_COLS > 0) {
                cellLp.setMarginStart(gap);
            }
            cell.setLayoutParams(cellLp);
            cell.setOnClickListener(v -> onColorClicked(entry));
            if (row != null) {
                row.addView(cell);
            }
        }
        return card;
    }

    private FrameLayout createColorCell(ImpressSolidColorCatalog.Entry entry) {
        FrameLayout cell = new FrameLayout(host.getContext());
        boolean selected = selectedIndex != null && selectedIndex == entry.index;

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(entry.iconResId);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int swatchSize = host.dpToPx(SWATCH_SIZE_DP);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(swatchSize, swatchSize);
        iconLp.gravity = Gravity.CENTER;
        cell.addView(icon, iconLp);

        ImageView check = new ImageView(host.getContext());
        check.setTag("check");
        check.setImageResource(R.drawable.lolib_ic_impress_solid_color_check);
        check.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        check.setImageTintList(android.content.res.ColorStateList.valueOf(
                isLightSwatch(entry.rgb) ? Color.parseColor("#333333") : Color.WHITE));
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
        cell.addView(check, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cell;
    }

    private void onColorClicked(ImpressSolidColorCatalog.Entry entry) {
        selectedIndex = entry.index;
        refreshSelectionUi();
        Log.i(TAG, "solid_color_selected index=" + entry.index
                + " rgb=#" + Integer.toHexString(entry.rgb).toUpperCase());
        host.onColorSelected(entry.index, entry.rgb);
    }

    private void refreshSelectionUi() {
        for (int b = 0; b < ImpressSolidColorCatalog.BLOCKS.length; b++) {
            ImpressSolidColorCatalog.Entry[] entries = ImpressSolidColorCatalog.BLOCKS[b].entries;
            View[] checks = checkViews[b];
            if (checks == null) {
                continue;
            }
            for (int i = 0; i < entries.length; i++) {
                View check = checks[i];
                if (check == null) {
                    continue;
                }
                boolean selected = selectedIndex != null && selectedIndex == entries[i].index;
                check.setVisibility(selected ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static boolean isLightSwatch(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 186;
    }
}
