package org.libreoffice.androidlib.impress;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
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

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.R;
import org.libreoffice.androidlib.ai.AiDialogHelper;

/**
 * Impress 插入形状选择器：全高 BottomSheet，分块展示 197 个形状图标。
 */
public class ImpressShapePickerController {
    private static final String TAG = "ImpressShapePicker";
    private static final float SHEET_HEIGHT_RATIO = 0.92f;
    private static final int GRID_COLS = 6;
    private static final int ROW_HEIGHT_DP = 48;
    private static final int ICON_SIZE_DP = 32;
    private static final int GRID_H_PAD_DP = 8;
    private static final int GRID_V_PAD_DP = 8;
    private static final int GRID_ROW_GAP_DP = 4;
    private static final int COLOR_SECTION = Color.parseColor("#80868B");

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void executeUnoCommand(String command);

        void runAfterDismiss(Runnable action);
    }

    private final Host host;
    private BottomSheetDialog dialog;

    public ImpressShapePickerController(Host host) {
        this.host = host;
    }

    public void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        View panel = LayoutInflater.from(host.getContext())
                .inflate(R.layout.lolib_sheet_impress_shape_picker, null, false);

        ImageButton backBtn = panel.findViewById(R.id.impress_shape_picker_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> dismiss());
        }

        LinearLayout content = panel.findViewById(R.id.impress_shape_picker_content);
        if (content != null) {
            buildShapeSections(content);
        }

        dialog = new BottomSheetDialog(host.getContext());
        dialog.setContentView(panel);
        AiDialogHelper.applyCloseOnlyDismiss(dialog);
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.setOnShowListener(d -> expandSheet(panel));
        dialog.show();
        Log.i(TAG, "shape_picker_show sections=" + ImpressShapeCatalog.SECTIONS.length);
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    private void expandSheet(View panel) {
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        int screenHeight = host.getContext().getResources().getDisplayMetrics().heightPixels;
        int targetHeight = Math.round(screenHeight * SHEET_HEIGHT_RATIO);
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
            layoutParams.height = targetHeight;
            bottomSheet.setLayoutParams(layoutParams);
        }
        bottomSheet.setBackgroundResource(R.drawable.lolib_bg_calc_bottom_sheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(true);
        behavior.setDraggable(true);
        bottomSheet.post(() -> behavior.setState(BottomSheetBehavior.STATE_EXPANDED));
    }

    private void buildShapeSections(LinearLayout root) {
        root.removeAllViews();
        for (int s = 0; s < ImpressShapeCatalog.SECTIONS.length; s++) {
            ImpressShapeCatalog.Section section = ImpressShapeCatalog.SECTIONS[s];
            root.addView(createSectionLabel(section.title));
            root.addView(createShapeGrid(section.entries));
        }
    }

    private TextView createSectionLabel(String title) {
        TextView label = new TextView(host.getContext());
        label.setText(title);
        label.setTextColor(COLOR_SECTION);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(null, Typeface.NORMAL);
        int hPad = host.dpToPx(16);
        label.setPadding(hPad, host.dpToPx(14), hPad, host.dpToPx(6));
        return label;
    }

    private View createShapeGrid(ImpressShapeCatalog.Entry[] entries) {
        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lolib_bg_function_card_figma);
        int hPad = host.dpToPx(GRID_H_PAD_DP);
        int vPad = host.dpToPx(GRID_V_PAD_DP);
        card.setPadding(hPad, vPad, hPad, vPad);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int sideMargin = host.dpToPx(16);
        cardLp.setMarginStart(sideMargin);
        cardLp.setMarginEnd(sideMargin);
        cardLp.bottomMargin = host.dpToPx(8);
        card.setLayoutParams(cardLp);

        int rowHeight = host.dpToPx(ROW_HEIGHT_DP);
        int iconSize = host.dpToPx(ICON_SIZE_DP);
        int rowGap = host.dpToPx(GRID_ROW_GAP_DP);
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
            ImpressShapeCatalog.Entry entry = entries[i];
            FrameLayout cell = new FrameLayout(host.getContext());
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, rowHeight, 1f);
            cell.setLayoutParams(cellLp);
            cell.setForeground(host.getContext().getDrawable(android.R.drawable.list_selector_background));

            ImageView icon = new ImageView(host.getContext());
            icon.setImageResource(entry.iconResId);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconLp.gravity = Gravity.CENTER;
            cell.addView(icon, iconLp);

            cell.setOnClickListener(v -> onShapeSelected(entry));
            if (row != null) {
                row.addView(cell);
            }
        }
        return card;
    }

    private void onShapeSelected(ImpressShapeCatalog.Entry entry) {
        Log.i(TAG, "shape_selected index=" + entry.index + " cmd=" + entry.unoCommand);
        dismiss();
        host.runAfterDismiss(() -> host.executeUnoCommand(entry.unoCommand));
    }
}
