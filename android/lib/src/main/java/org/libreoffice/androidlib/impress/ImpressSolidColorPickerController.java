package org.libreoffice.androidlib.impress;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
 * Impress 母版幻灯片 — 纯色二级页（12 + 64 色块，6 列，F2F3F5 圆角卡片）。
 */
public final class ImpressSolidColorPickerController {

    private static final String TAG = "ImpressSolidColor";
    private static final int GRID_COLS = 6;
    private static final int ROW_HEIGHT_DP = 48;
    private static final int ICON_SIZE_DP = 32;
    private static final int GRID_PAD_DP = 8;
    private static final int ROW_GAP_DP = 4;
    private static final int BLOCK_GAP_DP = 12;
    private static final int SELECT_RING_DP = 2;
    private static final int COLOR_SELECT_RING = Color.parseColor("#1278D9");

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        Integer getSelectedRgb();

        void onColorSelected(int index, int rgb);

        void onBack();
    }

    private final Host host;
    private Integer selectedRgb;
    private final View[][] checkViews = new View[ImpressSolidColorCatalog.BLOCKS.length][];

    public ImpressSolidColorPickerController(Host host) {
        this.host = host;
        this.selectedRgb = host.getSelectedRgb();
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
        int sidePad = host.dpToPx(16);
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
            ImpressSolidColorCatalog.Entry entry = entries[i];
            FrameLayout cell = createColorCell(entry, iconSize);
            checkViews[blockIndex][i] = cell.findViewWithTag("check");
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, rowHeight, 1f));
            cell.setForeground(host.getContext().getDrawable(android.R.drawable.list_selector_background));
            cell.setOnClickListener(v -> onColorClicked(entry));
            if (row != null) {
                row.addView(cell);
            }
        }
        return card;
    }

    private FrameLayout createColorCell(ImpressSolidColorCatalog.Entry entry, int iconSize) {
        FrameLayout cell = new FrameLayout(host.getContext());
        boolean selected = selectedRgb != null && selectedRgb == entry.rgb;

        if (selected) {
            View ring = new View(host.getContext());
            ring.setBackground(createSelectRingDrawable());
            int ringPad = host.dpToPx(SELECT_RING_DP);
            FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ringLp.setMargins(ringPad, ringPad, ringPad, ringPad);
            cell.addView(ring, ringLp);
        }

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(entry.iconResId);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        cell.addView(icon, iconLp);

        TextView check = new TextView(host.getContext());
        check.setTag("check");
        check.setText("✓");
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        check.setTypeface(Typeface.DEFAULT_BOLD);
        check.setTextColor(isLightSwatch(entry.rgb) ? Color.parseColor("#333333") : Color.WHITE);
        check.setGravity(Gravity.CENTER);
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
        cell.addView(check, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cell;
    }

    private GradientDrawable createSelectRingDrawable() {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(host.dpToPx(SELECT_RING_DP), COLOR_SELECT_RING);
        return ring;
    }

    private void onColorClicked(ImpressSolidColorCatalog.Entry entry) {
        selectedRgb = entry.rgb;
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
                boolean selected = selectedRgb != null && selectedRgb == entries[i].rgb;
                check.setVisibility(selected ? View.VISIBLE : View.GONE);
                FrameLayout cell = (FrameLayout) check.getParent();
                if (cell == null) {
                    continue;
                }
                View existingRing = cell.findViewWithTag("ring");
                if (selected && existingRing == null) {
                    View ring = new View(host.getContext());
                    ring.setTag("ring");
                    ring.setBackground(createSelectRingDrawable());
                    int ringPad = host.dpToPx(SELECT_RING_DP);
                    FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    ringLp.setMargins(ringPad, ringPad, ringPad, ringPad);
                    cell.addView(ring, 0, ringLp);
                } else if (!selected && existingRing != null) {
                    cell.removeView(existingRing);
                }
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
