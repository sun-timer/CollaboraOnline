package org.libreoffice.androidlib;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

        /** 枚举真实宏树，回调 catalog。 */
        void loadMacroCatalog(CalcValidationMacroCatalog.Callback callback);
    }

    interface MacroChooseCallback {
        void onMacroChosen(String macroUrl, String displayName);
    }

    private final Host host;
    private boolean documentScope = true;
    private String selectedUrl = "";
    private String searchFilter = "";
    private CalcValidationMacroCatalog catalog;

    private View rootView;
    private LinearLayout treeContainer;
    private LinearLayout scopeBoxContainer;

    CalcValidationMacroPickerController(Host host, String currentMacroUrl) {
        this.host = host;
        this.selectedUrl = currentMacroUrl == null ? "" : currentMacroUrl;
    }

    View buildRootView() {
        if (rootView != null) {
            rebuildTree();
            return rootView;
        }
        LinearLayout root = new LinearLayout(host.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);
        root.addView(createHeader("宏选择器"));

        // 程序库 + 宏名称 + 搜索 + 宏列表 + 说明 全部放进可滚动容器，超高时可滚动看到底部
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(host.getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(host.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(sectionHeader("程序库"));
        scopeBoxContainer = new LinearLayout(host.getContext());
        scopeBoxContainer.setOrientation(LinearLayout.VERTICAL);
        scopeBoxContainer.addView(createScopeBox());
        body.addView(scopeBoxContainer);
        body.addView(sectionHeader("宏名称"));
        body.addView(createSearchRow());
        treeContainer = new LinearLayout(host.getContext());
        treeContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        treeContainer.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(16));
        body.addView(treeContainer);
        body.addView(createDescriptionSection());
        scroll.addView(body);
        root.addView(scroll);

        rootView = root;
        rebuildTree();
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

    /** Figma 5282:61732：程序库蓝色大按钮，内含 我的宏 / 应用程序的宏 可展开行（chevron + 子库）。 */
    /** Figma 5282:62125：程序库框。我的宏（左侧 chevron+图标）+ Standard 子行 + 应用程序的宏（无 chevron）。 */
    private View createScopeBox() {
        LinearLayout box = new LinearLayout(host.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.lolib_bg_calc_macro_scope_box);
        int pad = host.dpToPx(16);
        box.setPadding(pad, host.dpToPx(8), pad, host.dpToPx(8));

        // 我的宏：左侧 chevron + 用户图标 + 文字（Figma frame-3469487）
        box.addView(createMyMacroRow(),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(36)));
        // Standard 子行：我的宏展开时显示（缩进，程序库框内）
        if (documentScope) {
            box.addView(createStandardRow(),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(36)));
        }
        // 应用程序的宏：图标 + 文字，无 chevron（Figma frame-3469488）
        box.addView(createAppMacroRow(),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(36)));

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.setMargins(host.dpToPx(16), host.dpToPx(4), host.dpToPx(16), host.dpToPx(8));
        box.setLayoutParams(boxLp);
        return box;
    }

    /** 我的宏行：左侧 chevron（选中向下/未选向右）+ 用户图标 + 文字。 */
    private View createMyMacroRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.dpToPx(8), 0, host.dpToPx(8), 0);

        ImageView chevron = new ImageView(host.getContext());
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        chevron.setImageResource(documentScope
                ? R.drawable.lolib_ic_calc_chevron_down
                : R.drawable.lolib_ic_calc_chevron_right);
        row.addView(chevron, new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16)));

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(R.drawable.lolib_ic_calc_macro_user);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16));
        iconLp.setMarginStart(host.dpToPx(8));
        row.addView(icon, iconLp);

        TextView text = new TextView(host.getContext());
        text.setText("我的宏");
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(host.dpToPx(8));
        row.addView(text, textLp);

        row.setOnClickListener(v -> {
            documentScope = !documentScope;
            rebuildTree();
        });
        return row;
    }

    /** Standard 子行：我的宏的子项，左侧缩进（对齐我的宏 icon 之后），代码图标 + 文字。 */
    private View createStandardRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // 我的宏 text 起点 = pad8+chevron16+gap8+icon16+gap8 = 56dp；Standard 缩进到 icon 位置（24dp）
        row.setPadding(host.dpToPx(32), 0, host.dpToPx(8), 0);

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(R.drawable.lolib_ic_calc_macro_code);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16)));

        TextView text = new TextView(host.getContext());
        text.setText("Standard");
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(host.dpToPx(8));
        row.addView(text, textLp);
        return row;
    }

    /** 应用程序的宏行：图标 + 文字，无 chevron。 */
    private View createAppMacroRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.dpToPx(8), 0, host.dpToPx(8), 0);

        ImageView icon = new ImageView(host.getContext());
        icon.setImageResource(R.drawable.lolib_ic_calc_macro_app);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(host.dpToPx(16), host.dpToPx(16)));

        TextView text = new TextView(host.getContext());
        text.setText("应用程序的宏");
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(host.dpToPx(8));
        row.addView(text, textLp);

        row.setOnClickListener(v -> {
            documentScope = false;
            rebuildTree();
        });
        return row;
    }

    /** Figma 5282:61732：宏名称搜索框（无搜索图标，纯输入框占位"搜索"）。 */
    private View createSearchRow() {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dpToPx(16);
        row.setPadding(pad, host.dpToPx(4), pad, host.dpToPx(4));

        EditText input = new EditText(host.getContext());
        input.setHint("搜索");
        input.setHintTextColor(Color.parseColor("#CCCCCC"));
        input.setTextColor(Color.parseColor("#333333"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setBackgroundResource(R.drawable.lolib_bg_calc_validation_input);
        input.setPadding(host.dpToPx(12), host.dpToPx(10), host.dpToPx(12), host.dpToPx(10));
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                setSearchFilter(s == null ? "" : s.toString());
            }
        });
        row.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(host.dpToPx(16), host.dpToPx(4), host.dpToPx(16), host.dpToPx(4));
        row.setLayoutParams(rowLp);
        return row;
    }

    /** Figma 5282:62152：说明区 = 说明 label + 写入框（686×88 输入框，描边圆角16）。 */
    private View createDescriptionSection() {
        LinearLayout wrapper = new LinearLayout(host.getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(sectionHeader("说明"));

        EditText input = new EditText(host.getContext());
        input.setTextColor(Color.parseColor("#333333"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setBackgroundResource(R.drawable.lolib_bg_calc_validation_input);
        input.setPadding(host.dpToPx(12), host.dpToPx(10), host.dpToPx(12), host.dpToPx(10));
        input.setMinLines(2);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int pad = host.dpToPx(16);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(pad, host.dpToPx(4), pad, host.dpToPx(12));
        wrapper.addView(input, inputLp);
        return wrapper;
    }

    /** 重建程序库框（Standard 子行随作用域）+ 宏名称区宏列表。 */
    private void rebuildTree() {
        if (scopeBoxContainer != null) {
            scopeBoxContainer.removeAllViews();
            scopeBoxContainer.addView(createScopeBox());
        }
        if (treeContainer == null) {
            return;
        }
        treeContainer.removeAllViews();
        java.util.List<CalcValidationMacroCatalog.MacroItem> items = catalog == null
                ? new java.util.ArrayList<>()
                : catalog.forScope(documentScope);
        for (CalcValidationMacroCatalog.MacroItem item : items) {
            if (searchFilter != null && !searchFilter.isEmpty()) {
                String needle = searchFilter.toLowerCase();
                boolean hit = (item.name != null && item.name.toLowerCase().contains(needle))
                        || (item.library != null && item.library.toLowerCase().contains(needle))
                        || (item.module != null && item.module.toLowerCase().contains(needle));
                if (!hit) {
                    continue;
                }
            }
            treeContainer.addView(createMacroRow(item));
        }
    }

    /** 回填真实宏树 catalog 并刷新。 */
    void setCatalog(CalcValidationMacroCatalog catalog) {
        this.catalog = catalog;
        rebuildTree();
    }

    /** 搜索过滤：宏名/库/模块子串匹配。 */
    void setSearchFilter(String filter) {
        searchFilter = filter;
        rebuildTree();
    }

    private View createMacroRow(CalcValidationMacroCatalog.MacroItem item) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.dpToPx(48));
        row.setPadding(host.dpToPx(36), host.dpToPx(8), host.dpToPx(12), host.dpToPx(8));
        String display = item.module == null || item.module.isEmpty()
                ? item.name : item.module + "." + item.name;
        boolean selected = item.uri != null && item.uri.equals(selectedUrl);

        TextView text = new TextView(host.getContext());
        text.setText(display);
        text.setTextColor(selected ? Color.parseColor("#3B8040") : Color.parseColor("#333333"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            ImageView check = new ImageView(host.getContext());
            check.setImageResource(R.drawable.lolib_ic_font_picker_check_green);
            row.addView(check, new LinearLayout.LayoutParams(host.dpToPx(24), host.dpToPx(24)));
        }

        row.setOnClickListener(v -> {
            selectedUrl = item.uri;
            host.onMacroSelected(item.uri, display);
        });
        return row;
    }
}
