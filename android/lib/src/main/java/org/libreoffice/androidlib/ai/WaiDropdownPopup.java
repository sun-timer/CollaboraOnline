package org.libreoffice.androidlib.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.libreoffice.androidlib.R;

/**
 * Writer(文档)AI 弹窗通用下拉浮层 — Figma「AI Office移动端 (Copy)」设计体系:
 * 白底 / 1dp #CBD1D7 描边 / 圆角 8dp / 阴影 22.35dp #0000004D(用 elevation 近似),
 * 行高 48.5dp、文字 16sp #101010,容器内边距 8dp。
 *
 * 两种选中样式:
 * - {@link Style#CHECK}: 选中行右侧 24dp 蓝色对勾 #1278D9(语言/比例选择)。
 * - {@link Style#GRADIENT}: 选中行 = 渐变 8% 底 + 1dp 渐变描边、圆角 4dp(文案类型/润色风格)。
 */
public final class WaiDropdownPopup {

    /** 选中态样式。 */
    public enum Style {
        CHECK,
        GRADIENT
    }

    public interface Listener {
        void onItemSelected(int index, String label);
    }

    private static PopupWindow activePopup;

    private WaiDropdownPopup() {
    }

    public static void dismissIfShowing() {
        if (activePopup != null && activePopup.isShowing()) {
            activePopup.dismiss();
        }
        activePopup = null;
    }

    /**
     * @param widthDp 浮层宽度(dp);<=0 时取锚点宽度。
     * @param icons   可选行首图标(比例选择用);null 表示无图标。
     */
    public static void show(Activity activity, View anchor, String[] labels,
            int selectedIndex, int widthDp, Style style, Drawable[] icons,
            Listener listener) {
        show(activity, anchor, labels, selectedIndex, widthDp, style, icons, listener, null);
    }

    public static void show(Activity activity, View anchor, String[] labels,
            int selectedIndex, int widthDp, Style style, Drawable[] icons,
            Listener listener, Runnable onDismiss) {
        show(activity, anchor, anchor == null ? null : anchor.getRootView(), labels,
                selectedIndex, widthDp, style, icons, listener, onDismiss);
    }

    public static void show(Activity activity, View anchor, View boundary, String[] labels,
            int selectedIndex, int widthDp, Style style, Drawable[] icons,
            Listener listener, Runnable onDismiss) {
        if (activity == null || anchor == null || labels == null || labels.length == 0) {
            return;
        }
        dismissIfShowing();

        float density = activity.getResources().getDisplayMetrics().density;
        int padPx = Math.round(8f * density);
        int rowH = Math.round(48.5f * density);
        int maxListH = Math.round(280f * density);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(padPx, 0, padPx, 0);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            if (i == 0) {
                rowLp.topMargin = 0;
            }
            row.setLayoutParams(rowLp);

            if (icons != null && i < icons.length && icons[i] != null) {
                ImageView icon = new ImageView(activity);
                icon.setImageDrawable(icons[i]);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        Math.round(24f * density), Math.round(24f * density));
                iconLp.rightMargin = padPx;
                icon.setLayoutParams(iconLp);
                row.addView(icon);
            }

            TextView label = new TextView(activity);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            label.setTextColor(Color.parseColor("#101010"));
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labelLp);
            row.addView(label);

            boolean selected = i == selectedIndex;
            if (selected && style == Style.CHECK) {
                ImageView check = new ImageView(activity);
                check.setImageResource(R.drawable.lolib_wai_ic_check_blue);
                LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                        Math.round(24f * density), Math.round(24f * density));
                check.setLayoutParams(checkLp);
                row.addView(check);
            } else if (selected && style == Style.GRADIENT) {
                row.setBackgroundResource(R.drawable.lolib_wai_bg_drop_item_sel);
                row.post(() -> {
                    // 渐变底选中行文字压深
                    label.setTextColor(Color.parseColor("#333333"));
                });
            }

            row.setOnClickListener(v -> {
                dismissIfShowing();
                if (listener != null) {
                    listener.onItemSelected(index, labels[index]);
                }
            });
            list.addView(row);
        }

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        scroller.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.lolib_wai_bg_dropdown);
        content.setPadding(padPx, padPx, padPx, padPx);
        content.addView(scroller, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int listHeight = content.getMeasuredHeight();

        int widthPx;
        if (widthDp > 0) {
            widthPx = Math.round(widthDp * density);
        } else {
            widthPx = Math.max(anchor.getWidth(), Math.round(311f * density));
        }
        // Keep the dropdown inside its host dialog, including when the anchor is
        // close to the dialog's right edge.
        View host = boundary != null ? boundary : anchor.getRootView();
        int[] hostLocation = new int[2];
        int[] anchorLocation = new int[2];
        host.getLocationOnScreen(hostLocation);
        anchor.getLocationOnScreen(anchorLocation);
        int hostWidth = host.getWidth();
        if (hostWidth <= 0) {
            hostWidth = activity.getResources().getDisplayMetrics().widthPixels;
            hostLocation[0] = 0;
        }
        int leftInset = Math.max(padPx, host.getPaddingLeft());
        int rightInset = Math.max(padPx, host.getPaddingRight());
        int availableWidth = hostWidth > 0 ? hostWidth - leftInset - rightInset
                : activity.getResources().getDisplayMetrics().widthPixels - padPx * 2;
        int contentWidth = Math.min(widthPx, Math.max(1, availableWidth));
        int rightEdge = Math.min(hostLocation[0] + hostWidth - rightInset,
                activity.getResources().getDisplayMetrics().widthPixels - padPx);
        int leftEdge = Math.max(padPx, hostLocation[0] + leftInset);
        contentWidth = Math.min(contentWidth, Math.max(1, rightEdge - leftEdge));
        int popupLeft = Math.max(leftEdge, Math.min(anchorLocation[0], rightEdge - contentWidth));
        int horizontalOffset = popupLeft - anchorLocation[0];
        int contentHeight = Math.min(listHeight, maxListH);

        PopupWindow popup = new PopupWindow(content, contentWidth, contentHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(6f * density);
        popup.setOnDismissListener(() -> {
            activePopup = null;
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        activePopup = popup;

        Runnable showPopup = () -> {
            if (anchor.getWindowToken() == null) {
                return;
            }
            // Figma 326:25950 ends at y=864 and 326:26075 starts at y=874.
            // The 750px canvas uses 2px per dp, so the polish list gap is 5dp.
            int gap = Math.round((anchor.getId() == R.id.wai_text_op_polish_style_card
                    ? 5f : 8f) * density);
            popup.showAsDropDown(anchor, horizontalOffset, gap);
            android.util.Log.d("WaiDropdownPopup", "dropdown_position anchor=" + anchor.getId()
                    + " bottom=" + (anchorLocation[1] + anchor.getHeight())
                    + " gap=" + gap + " height=" + contentHeight);
        };
        if (anchor.getWidth() > 0) {
            showPopup.run();
        } else {
            anchor.post(showPopup);
        }
    }
}
