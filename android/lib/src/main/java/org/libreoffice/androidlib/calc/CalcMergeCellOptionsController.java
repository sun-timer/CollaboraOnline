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

import org.libreoffice.androidlib.R;
import org.libreoffice.androidlib.ai.AiDialogHelper;
import org.libreoffice.androidlib.impress.ImpressSubpageHeader;

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
        final AiDialogHelper.CompactPanelSession[] dialogRef = new AiDialogHelper.CompactPanelSession[1];

        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        root.addView(ImpressSubpageHeader.create(
                host.getContext(), host::dpToPx, "合并单元格",
                v -> {
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                }));
        root.addView(ImpressSubpageHeader.createDivider(host.getContext()));

        LinearLayout content = new LinearLayout(host.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dpToPx(20), host.dpToPx(12), host.dpToPx(20), host.dpToPx(20));

        TextView hint = new TextView(host.getContext());
        hint.setText("部分单元格不为空。");
        hint.setTextColor(Color.parseColor("#101010"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hint.setPadding(0, 0, 0, host.dpToPx(8));
        content.addView(hint);

        final int[] selectedIndex = {0};
        final ImageView[] radioViews = new ImageView[OPTION_LABELS.length];

        for (int i = 0; i < OPTION_LABELS.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, host.dpToPx(8), 0, host.dpToPx(8));

            LinearLayout rowHeader = new LinearLayout(host.getContext());
            rowHeader.setOrientation(LinearLayout.HORIZONTAL);
            rowHeader.setGravity(Gravity.CENTER_VERTICAL);
            rowHeader.setMinimumHeight(host.dpToPx(28));

            TextView label = new TextView(host.getContext());
            label.setText(OPTION_LABELS[i]);
            label.setTextColor(Color.parseColor("#333333"));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rowHeader.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView radio = new ImageView(host.getContext());
            radio.setImageResource(i == 0
                    ? R.drawable.lolib_ic_calc_toggle_checked
                    : R.drawable.lolib_ic_calc_toggle_unchecked);
            radio.setImageTintList(null);
            int radioSize = host.dpToPx(20);
            rowHeader.addView(radio, new LinearLayout.LayoutParams(radioSize, radioSize));
            radioViews[i] = radio;

            ImageView preview = new ImageView(host.getContext());
            preview.setImageResource(PREVIEW_DRAWABLES[i]);
            // 等比缩放至内容区宽度（618×57 资源），不横向拉伸；三图同尺寸 PNG 线宽一致
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.setAdjustViewBounds(true);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            previewLp.topMargin = host.dpToPx(8);
            preview.setLayoutParams(previewLp);

            row.addView(rowHeader);
            row.addView(preview);

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
        confirmLp.topMargin = host.dpToPx(8);
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
        Log.i(TAG, "show_merge_options");
    }

    private GradientDrawable createConfirmBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#3B8040"));
        drawable.setCornerRadius(host.dpToPx(22));
        return drawable;
    }
}
