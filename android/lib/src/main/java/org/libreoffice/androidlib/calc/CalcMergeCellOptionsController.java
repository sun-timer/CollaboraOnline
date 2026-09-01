package org.libreoffice.androidlib.calc;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;

import org.libreoffice.androidlib.R;
import org.libreoffice.androidlib.ai.AiDialogHelper;

/**
 * Calc 合并单元格选项弹窗（Figma 5274:56201）：标题栏 + 提示 + 3 单选（各带示意图）+ 确定。
 * 供底部工具栏与功能面板共用。
 */
public final class CalcMergeCellOptionsController {

    private static final String TAG = "CalcMergeCellOptions";
    private static final String PANEL_TAG = "BottomToolbarController:merge";

    private static final String[] OPTION_LABELS = {
            "合并内容", "合并单元格", "合并相同单元格"
    };
    private static final String[] OPTION_COMMANDS = {
            ".uno:MergeCells?MoveContents:bool=true",
            ".uno:MergeCells?MoveContents:bool=false",
            ".uno:MergeCells?MoveContents:bool=false",
    };
    private static final int[] PREVIEW_DRAWABLES = {
            R.drawable.lolib_img_merge_preview_content,
            R.drawable.lolib_img_merge_preview_cells,
            R.drawable.lolib_img_merge_preview_same,
    };

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void executeUnoCommand(String command);
    }

    private final Host host;

    public CalcMergeCellOptionsController(Host host) {
        this.host = host;
    }

    public void show() {
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(host.dpToPx(43));
        header.setPadding(host.dpToPx(8), 0, host.dpToPx(16), 0);
        header.setBackground(createHeaderBottomLineBackground());

        AppCompatImageButton backBtn = new AppCompatImageButton(host.getContext());
        backBtn.setImageResource(R.drawable.lolib_ic_top_back);
        TypedValue rippleAttr = new TypedValue();
        if (host.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            backBtn.setBackgroundResource(rippleAttr.resourceId);
        }
        backBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backBtn.setPadding(host.dpToPx(10), host.dpToPx(10), host.dpToPx(10), host.dpToPx(10));
        header.addView(backBtn, new LinearLayout.LayoutParams(host.dpToPx(48), host.dpToPx(48)));

        TextView title = new TextView(host.getContext());
        title.setText("合并单元格");
        title.setTextColor(Color.parseColor("#333333"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dpToPx(20), host.dpToPx(8), host.dpToPx(20), host.dpToPx(20));

        TextView hint = new TextView(host.getContext());
        hint.setText("部分单元格不为空。");
        hint.setTextColor(Color.parseColor("#101010"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hint.setPadding(0, host.dpToPx(4), 0, host.dpToPx(8));
        content.addView(hint);

        final int[] selectedIndex = {0};
        final ImageView[] radioViews = new ImageView[OPTION_LABELS.length];
        final AiDialogHelper.CompactPanelSession[] dialogRef = new AiDialogHelper.CompactPanelSession[1];

        for (int i = 0; i < OPTION_LABELS.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(host.dpToPx(8), host.dpToPx(4), host.dpToPx(8), host.dpToPx(8));

            LinearLayout rowHeader = new LinearLayout(host.getContext());
            rowHeader.setOrientation(LinearLayout.HORIZONTAL);
            rowHeader.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(host.getContext());
            label.setText(OPTION_LABELS[i]);
            label.setTextColor(Color.parseColor("#333333"));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rowHeader.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView radio = new ImageView(host.getContext());
            radio.setImageResource(i == 0
                    ? R.drawable.lolib_ic_calc_toggle_checked
                    : R.drawable.lolib_ic_calc_toggle_unchecked);
            rowHeader.addView(radio, new LinearLayout.LayoutParams(host.dpToPx(20), host.dpToPx(20)));
            radioViews[i] = radio;

            ImageView preview = new ImageView(host.getContext());
            preview.setImageResource(PREVIEW_DRAWABLES[i]);
            preview.setScaleType(ImageView.ScaleType.FIT_START);
            preview.setAdjustViewBounds(true);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(51));
            previewLp.topMargin = host.dpToPx(4);

            row.addView(rowHeader);
            row.addView(preview, previewLp);

            row.setOnClickListener(v -> {
                selectedIndex[0] = index;
                for (int j = 0; j < radioViews.length; j++) {
                    radioViews[j].setImageResource(j == index
                            ? R.drawable.lolib_ic_calc_toggle_checked
                            : R.drawable.lolib_ic_calc_toggle_unchecked);
                }
            });
            content.addView(row);
        }

        TextView confirm = new TextView(host.getContext());
        confirm.setText("确定");
        confirm.setTextColor(Color.WHITE);
        confirm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        confirm.setGravity(Gravity.CENTER);
        confirm.setBackground(createConfirmBackground());
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(44));
        confirmLp.topMargin = host.dpToPx(12);
        confirm.setLayoutParams(confirmLp);
        confirm.setOnClickListener(v -> {
            host.executeUnoCommand(OPTION_COMMANDS[selectedIndex[0]]);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        content.addView(confirm);

        root.addView(content);

        dialogRef[0] = AiDialogHelper.showCompactPanel(host.getContext(), root, PANEL_TAG);
        backBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        Log.i(TAG, "show_merge_options");
    }

    private android.graphics.drawable.Drawable createHeaderBottomLineBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.WHITE);
        GradientDrawable line = new GradientDrawable();
        line.setShape(GradientDrawable.RECTANGLE);
        line.setColor(Color.parseColor("#A2A9B2"));
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bg, line});
        layer.setLayerInset(1, 0, host.dpToPx(42), 0, 0);
        return layer;
    }

    private GradientDrawable createConfirmBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#3B8040"));
        drawable.setCornerRadius(host.dpToPx(22));
        return drawable;
    }
}
