package org.libreoffice.androidlib.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

/**
 * 文案生成 / 润色弹窗 — 自定义下拉列表（圆角白底 + 阴影 + 渐变选中态）。
 */
public final class ArticleDropdownPopup {

    public interface Listener {
        void onItemSelected(int index, String label);
    }

    private ArticleDropdownPopup() {
    }

    public static void show(Activity activity, View anchor, String[] labels,
            int selectedIndex, Listener listener) {
        show(activity, anchor, labels, selectedIndex, listener, null);
    }

    public static void show(Activity activity, View anchor, String[] labels,
            int selectedIndex, Listener listener, Runnable onDismiss) {
        if (activity == null || anchor == null || labels == null || labels.length == 0) {
            return;
        }
        int dp = (int) activity.getResources().getDisplayMetrics().density;
        int panelPadH = 16 * dp;
        int panelPadV = 12 * dp;
        int itemPadH = 16 * dp;
        int itemPadV = 20 * dp;
        int itemGap = 4 * dp;
        int itemMinH = 56 * dp;
        int maxListHeight = 280 * dp;

        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final String label = labels[i];
            boolean selected = index == selectedIndex;

            TextView row = new TextView(activity);
            row.setText(label);
            row.setTextColor(Color.parseColor("#333333"));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(itemMinH);
            row.setPadding(itemPadH, itemPadV, itemPadH, itemPadV);
            row.setBackgroundResource(selected
                    ? R.drawable.lolib_bg_article_dropdown_item_selected
                    : android.R.color.transparent);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = itemGap;
            }
            row.setLayoutParams(lp);
            listContainer.addView(row);
        }

        listContainer.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int listHeight = listContainer.getMeasuredHeight();

        View scrollHost;
        if (listHeight > maxListHeight) {
            ScrollView scrollView = new ScrollView(activity);
            scrollView.setVerticalScrollBarEnabled(true);
            scrollView.setScrollbarFadingEnabled(false);
            scrollView.addView(listContainer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            scrollHost = scrollView;
        } else {
            scrollHost = listContainer;
        }

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.lolib_bg_article_dropdown_panel);
        panel.setPadding(panelPadH, panelPadV, panelPadH, panelPadV);
        panel.setClipToOutline(true);
        panel.addView(scrollHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                listHeight > maxListHeight ? maxListHeight : ViewGroup.LayoutParams.WRAP_CONTENT));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            float elevationPx = 12f * activity.getResources().getDisplayMetrics().density;
            panel.setElevation(elevationPx);
        }

        PopupWindow popup = new PopupWindow(panel,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            float elevationPx = 12f * activity.getResources().getDisplayMetrics().density;
            popup.setElevation(elevationPx);
        }
        popup.setOnDismissListener(() -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        for (int i = 0; i < listContainer.getChildCount(); i++) {
            View row = listContainer.getChildAt(i);
            final int index = i;
            final String label = labels[i];
            row.setOnClickListener(v -> {
                popup.dismiss();
                if (listener != null) {
                    listener.onItemSelected(index, label);
                }
            });
        }

        int anchorWidth = anchor.getWidth() > 0
                ? anchor.getWidth()
                : ViewGroup.LayoutParams.MATCH_PARENT;

        panel.measure(
                View.MeasureSpec.makeMeasureSpec(anchorWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = Math.max(anchorWidth, panel.getMeasuredWidth());

        popup.setWidth(popupWidth);
        popup.showAsDropDown(anchor, 0, 4 * dp);
    }
}
