package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 数据有效性 — 宏选择二级页（我的宏 / 应用程序的宏）。
 */
final class CalcValidationMacroPickerController {

    interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        void onBack();

        void onMacroSelected(String macroUrl, String displayName);

        void openMacroChooser(MacroChooseCallback callback);
    }

    interface MacroChooseCallback {
        void onMacroChosen(String macroUrl, String displayName);
    }

    private static final class MacroEntry {
        final String name;
        final String url;

        MacroEntry(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private final Host host;
    private boolean documentScope = true;
    private boolean standardExpanded = true;
    private String selectedUrl = "";

    private View rootView;
    private LinearLayout treeContainer;
    private final TextView[] tabViews = new TextView[2];

    CalcValidationMacroPickerController(Host host, String currentMacroUrl) {
        this.host = host;
        this.selectedUrl = currentMacroUrl == null ? "" : currentMacroUrl;
    }

    View buildRootView() {
        if (rootView != null) {
            rebuildTree();
            refreshTabs();
            return rootView;
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);
        root.addView(createHeader("宏选择器"));
        root.addView(sectionHeader("程序库"));
        root.addView(createScopeTabs());
        root.addView(sectionHeader("宏名称"));
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(host.getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        treeContainer = new LinearLayout(host.getContext());
        treeContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        treeContainer.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(16));
        scroll.addView(treeContainer);
        root.addView(scroll);
        root.addView(createBottomBar());
        rootView = root;
        rebuildTree();
        refreshTabs();
        return rootView;
    }

    private View createHeader(String title) {
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

        TextView titleView = new TextView(host.getContext());
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginStart(host.dpToPx(8));
        header.addView(titleView, titleLp);

        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#A2A9B2"));
        LinearLayout wrapper = new LinearLayout(host.getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(header);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
        return wrapper;
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(host.getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#101010"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        int pad = host.dpToPx(16);
        tv.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(4));
        return tv;
    }

    private View createScopeTabs() {
        LinearLayout track = new LinearLayout(host.getContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER);
        track.setPadding(host.dpToPx(16), host.dpToPx(12), host.dpToPx(16), host.dpToPx(8));

        View tab0 = createScopeTab("我的宏", R.drawable.lolib_ic_calc_macro_user, true);
        View tab1 = createScopeTab("应用程序的宏", R.drawable.lolib_ic_calc_macro_app, false);
        tabViews[0] = findTabLabel(tab0);
        tabViews[1] = findTabLabel(tab1);
        LinearLayout.LayoutParams lp0 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginStart(host.dpToPx(8));
        track.addView(tab0, lp0);
        track.addView(tab1, lp1);
        return track;
    }

    private TextView findTabLabel(View tabRoot) {
        if (tabRoot instanceof LinearLayout) {
            LinearLayout tab = (LinearLayout) tabRoot;
            for (int i = 0; i < tab.getChildCount(); i++) {
                View child = tab.getChildAt(i);
                if (child instanceof TextView) {
                    return (TextView) child;
                }
            }
        }
        return null;
    }

    private View createScopeTab(String label, int iconRes, boolean documentTab) {
        LinearLayout tab = new LinearLayout(host.getContext());
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER);
        tab.setBackgroundResource(R.drawable.lolib_bg_hyperlink_segment_tab_selected);
        int padH = host.dpToPx(12);
        tab.setPadding(padH, host.dpToPx(10), padH, host.dpToPx(10));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tab.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(20), host.dpToPx(20)));

        TextView text = new TextView(host.getContext());
        text.setText(label);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(host.dpToPx(6));
        tab.addView(text, textLp);

        tab.setOnClickListener(v -> {
            documentScope = documentTab;
            refreshTabs();
            rebuildTree();
        });
        return tab;
    }

    private void refreshTabs() {
        View track = tabViews[0] != null ? (View) tabViews[0].getParent() : null;
        if (!(track instanceof LinearLayout)) {
            return;
        }
        LinearLayout tabTrack = (LinearLayout) track;
        for (int i = 0; i < tabTrack.getChildCount(); i++) {
            View tabRoot = tabTrack.getChildAt(i);
            boolean selected = (i == 0) == documentScope;
            tabRoot.setBackgroundResource(selected
                    ? R.drawable.lolib_bg_hyperlink_segment_tab_selected
                    : R.drawable.lolib_bg_calc_validation_segment_track);
            TextView label = findTabLabel(tabRoot);
            if (label != null) {
                label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    private View createBottomBar() {
        View divider = new View(host.getContext());
        divider.setBackgroundColor(Color.parseColor("#14000000"));
        LinearLayout wrapper = new LinearLayout(host.getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));

        LinearLayout bar = new LinearLayout(host.getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = host.dpToPx(16);
        bar.setPadding(pad, host.dpToPx(10), pad, host.dpToPx(10));

        TextView browse = new TextView(host.getContext());
        browse.setText("浏览…");
        browse.setTextColor(Color.parseColor("#1278D9"));
        browse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        browse.setOnClickListener(v -> {
            // 列表即浏览；勿在此调 CO 原生宏对话框（会导致闪退）。
        });
        bar.addView(browse, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView ok = new TextView(host.getContext());
        ok.setText("确定");
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ok.setGravity(Gravity.CENTER);
        ok.setBackgroundResource(R.drawable.lolib_bg_calc_sheet_pill_primary);
        int hPad = host.dpToPx(24);
        int vPad = host.dpToPx(8);
        ok.setPadding(hPad, vPad, hPad, vPad);
        ok.setOnClickListener(v -> host.onBack());
        bar.addView(ok);
        wrapper.addView(bar);
        return wrapper;
    }

    private void rebuildTree() {
        if (treeContainer == null) {
            return;
        }
        treeContainer.removeAllViews();
        treeContainer.addView(createFolderRow("Standard", standardExpanded, () -> {
            standardExpanded = !standardExpanded;
            rebuildTree();
        }));
        if (standardExpanded) {
            MacroEntry[] entries = documentScope
                    ? new MacroEntry[] {
                            new MacroEntry("Main",
                                    "vnd.sun.star.script:Standard.Module1.Main?language=Basic&location=document"),
                            new MacroEntry("Module1",
                                    "vnd.sun.star.script:Standard.Module1?language=Basic&location=document"),
                    }
                    : new MacroEntry[] {
                            new MacroEntry("Main",
                                    "vnd.sun.star.script:Standard.Module1.Main?language=Basic&location=application"),
                    };
            for (MacroEntry entry : entries) {
                treeContainer.addView(createMacroRow(entry));
            }
        }
    }

    private View createFolderRow(String label, boolean expanded, Runnable toggleAction) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(48));
        row.setPadding(host.dpToPx(4), host.dpToPx(8), host.dpToPx(4), host.dpToPx(8));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(R.drawable.lolib_ic_calc_macro_code);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));

        TextView text = new TextView(host.getContext());
        text.setText(label);
        text.setTextColor(Color.parseColor("#333333"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(host.dpToPx(8));
        row.addView(text, textLp);

        ImageView chevron = new ImageView(host.getContext());
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        chevron.setImageResource(expanded
                ? R.drawable.lolib_ic_calc_chevron_down
                : R.drawable.lolib_ic_calc_chevron_right);
        row.addView(chevron, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));

        row.setOnClickListener(v -> toggleAction.run());
        return row;
    }

    private View createMacroRow(MacroEntry entry) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(48));
        row.setPadding(host.dpToPx(36), host.dpToPx(8), host.dpToPx(12), host.dpToPx(8));
        boolean selected = entry.url.equals(selectedUrl);

        TextView text = new TextView(host.getContext());
        text.setText(entry.name);
        text.setTextColor(selected ? Color.parseColor("#3B8040") : Color.parseColor("#333333"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            ImageView check = new ImageView(host.getContext());
            check.setImageResource(R.drawable.lolib_ic_font_picker_check_green);
            row.addView(check, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
        }

        row.setOnClickListener(v -> {
            selectedUrl = entry.url;
            host.onMacroSelected(entry.url, entry.name);
        });
        return row;
    }
}
