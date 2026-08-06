package org.libreoffice.androidlib.impress;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

/**
 * 演示文稿二级页标题栏：Figma 258:11323（750×86 → 375×43dp，32px 返回 + 16sp 标题）。
 */
public final class ImpressSubpageHeader {

    public interface Dp {
        int dp(int value);
    }

    private ImpressSubpageHeader() {
    }

    public static LinearLayout create(Context context, Dp dp, String title, View.OnClickListener onBack) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp.dp(43)));
        header.setPadding(dp.dp(16), dp.dp(12), dp.dp(16), dp.dp(12));
        header.setBackgroundColor(Color.WHITE);

        ImageButton back = new ImageButton(context);
        TypedValue rippleAttr = new TypedValue();
        if (context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rippleAttr, true)) {
            back.setBackgroundResource(rippleAttr.resourceId);
        }
        back.setImageResource(R.drawable.lolib_ic_impress_subpage_back);
        back.setContentDescription("返回");
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setOnClickListener(onBack);
        header.addView(back, new LinearLayout.LayoutParams(dp.dp(32), dp.dp(32)));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(dp.dp(8));
        header.addView(titleView, titleLp);

        return header;
    }

    public static View createDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return divider;
    }
}
