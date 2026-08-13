package org.libreoffice.androidlib.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

/**
 * 文案生成 / 润色弹窗 — 锚点下拉列表（白底圆角 + 浅描边 + 橙色勾选中态，对齐 Figma 662:32009）。
 */
public final class ArticleDropdownPopup {

    private static PopupWindow activePopup;

    public interface Listener {
        void onItemSelected(int index, String label);
    }

    private ArticleDropdownPopup() {
    }

    public static void dismissIfShowing() {
        if (activePopup != null) {
            if (activePopup.isShowing()) {
                activePopup.dismiss();
            }
            activePopup = null;
        }
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
        dismissIfShowing();

        float density = activity.getResources().getDisplayMetrics().density;
        int gapBelowAnchorPx = Math.round(4f * density);
        int maxListHeightPx = Math.round(280f * density);

        LayoutInflater inflater = LayoutInflater.from(activity);
        View content = inflater.inflate(R.layout.lolib_popup_impress_option_list, null, false);
        LinearLayout list = content.findViewById(R.id.impress_option_list);
        for (int i = 0; i < labels.length; i++) {
            View row = inflater.inflate(R.layout.lolib_item_impress_option_row, list, false);
            TextView labelView = row.findViewById(R.id.impress_option_item_label);
            ImageView check = row.findViewById(R.id.impress_option_item_check);
            labelView.setText(labels[i]);
            check.setVisibility(i == selectedIndex ? View.VISIBLE : View.GONE);
            final int index = i;
            final String label = labels[i];
            row.setOnClickListener(v -> {
                if (activePopup != null) {
                    activePopup.dismiss();
                }
                if (listener != null) {
                    listener.onItemSelected(index, label);
                }
            });
            list.addView(row);
        }

        list.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (list.getMeasuredHeight() > maxListHeightPx) {
            ViewGroup parent = (ViewGroup) list.getParent();
            int childIndex = parent.indexOfChild(list);
            parent.removeView(list);
            ScrollView scrollView = new ScrollView(activity);
            scrollView.setVerticalScrollBarEnabled(true);
            scrollView.setScrollbarFadingEnabled(false);
            scrollView.addView(list, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.addView(scrollView, childIndex, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxListHeightPx));
        }

        PopupWindow popup = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setClippingEnabled(false);
        popup.setElevation(8f * density);
        popup.setOnDismissListener(() -> {
            activePopup = null;
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        Runnable showPopup = () -> {
            int anchorWidth = anchor.getWidth() > 0
                    ? anchor.getWidth()
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(anchorWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int popupWidth = Math.max(anchorWidth, content.getMeasuredWidth());
            popup.setWidth(popupWidth);
            activePopup = popup;
            popup.showAsDropDown(anchor, 0, gapBelowAnchorPx);
        };
        if (anchor.getWidth() > 0) {
            showPopup.run();
        } else {
            anchor.post(showPopup);
        }
    }
}
