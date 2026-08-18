package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;

public class FindReplaceSheetController {
    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        /** 顶部栏实际像素高度（横屏悬浮弹窗定位用）。 */
        int getTopToolbarHeightPx();

        /** 文档底栏 + 三键导航占位（竖屏 BottomSheet 锚点用）。 */
        int getBottomChromeHeightPx();

        void hideKeyboardForBottomSheet();

        void runFindBridge(String js);

        void ensureEditModeThen(Runnable action);

        boolean isEditModeActive();

        void onFindReplaceEditDispatched(boolean replaceAll);
    }

    private final Host host;
    private BottomSheetDialog mainDialog;
    private BottomSheetDialog settingsDialog;
    private AlertDialog floatingDialog;
    private AlertDialog floatingSettingsDialog;
    private boolean replaceTabActive = false;
    private boolean ignoreCase = true;
    private boolean caseSensitive = false;
    private boolean wholeWord = false;
    private boolean syncingQueryFields = false;

    private EditText findQueryView;
    private EditText replaceQueryView;
    private EditText replaceWithView;
    private TextView findPrevView;
    private TextView findNextView;
    private TextView replacePrevView;
    private TextView replaceNextView;
    private TextView replaceAllView;
    private TextView replaceOneView;

    public FindReplaceSheetController(Host host) {
        this.host = host;
    }

    public void show() {
        dismiss();
        host.hideKeyboardForBottomSheet();
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_find_replace, null);
        bindMainPanel(panel);
        if (BottomSheetAnchorHelper.isLandscape(host.getContext())) {
            floatingDialog = showFloatingDialog(panel);
        } else {
            mainDialog = new BottomSheetDialog(host.getContext());
            mainDialog.setContentView(panel);
            AiDialogHelper.applyCloseOnlyDismiss(mainDialog);
            mainDialog.setOnDismissListener(dialog -> mainDialog = null);
            mainDialog.setOnShowListener(d -> expandSheet(mainDialog, panel));
            mainDialog.show();
            AiDialogHelper.applyNoDimScrim(mainDialog);
        }
    }

    public void dismiss() {
        dismissSettings();
        if (floatingDialog != null) {
            floatingDialog.dismiss();
            floatingDialog = null;
        }
        if (mainDialog != null) {
            mainDialog.dismiss();
            mainDialog = null;
        }
    }

    /**
     * 横屏悬浮弹窗尺寸：宽度固定 targetWidthPx；高度随内容（两个 tab 中较高者），
     * 上限 maxHeightPx 仅用于防止超高溢出屏幕，不主动裁切（切 tab 不截断）。
     */
    private void applyFloatingPanelSize(View panel, AlertDialog dialog, int targetWidthPx, int maxHeightPx) {
        if (dialog == null || dialog.getWindow() == null || panel == null) {
            return;
        }
        int shadowMargin = host.dpToPx(20);
        panel.post(() -> {
            if (dialog.getWindow() == null) {
                return;
            }
            // 替换 tab 初始 gone，measure 会漏掉其高度导致横屏截断：
            // 测量前临时展开全部 panel 取最高值，之后再恢复初始可见性。
            View findPanel = panel.findViewById(R.id.find_panel_find);
            View replacePanel = panel.findViewById(R.id.find_panel_replace);
            int findVis = findPanel != null ? findPanel.getVisibility() : View.VISIBLE;
            int replaceVis = replacePanel != null ? replacePanel.getVisibility() : View.GONE;
            if (findPanel != null) {
                findPanel.setVisibility(View.VISIBLE);
            }
            if (replacePanel != null) {
                replacePanel.setVisibility(View.VISIBLE);
            }
            try {
                int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(targetWidthPx,
                        android.view.View.MeasureSpec.EXACTLY);
                int heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0,
                        android.view.View.MeasureSpec.UNSPECIFIED);
                panel.measure(widthSpec, heightSpec);
                int contentHeight = Math.max(panel.getMeasuredHeight(), 1);
                // 窗口比内容大一圈，为 elevation 阴影留空间（透明窗口下阴影溢出）。
                int windowWidth = targetWidthPx + shadowMargin * 2;
                int windowHeight = Math.min(contentHeight + shadowMargin * 2, maxHeightPx);
                dialog.getWindow().setLayout(windowWidth, windowHeight);
                // contentView 固定目标宽度、居中于窗口内，四周留出阴影边距。
                android.view.ViewGroup.LayoutParams lp = panel.getLayoutParams();
                if (lp instanceof WindowManager.LayoutParams) {
                    WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
                    wlp.width = targetWidthPx;
                    wlp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    wlp.gravity = Gravity.CENTER;
                    panel.setLayoutParams(wlp);
                } else if (lp != null) {
                    lp.width = targetWidthPx;
                    lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    panel.setLayoutParams(lp);
                }
            } finally {
                if (findPanel != null) {
                    findPanel.setVisibility(findVis);
                }
                if (replacePanel != null) {
                    replacePanel.setVisibility(replaceVis);
                }
            }
        });
    }

    /** 横屏：固定大小悬浮右上角（顶部栏下方），无遮罩。对齐 Figma 5632:26433。 */
    private AlertDialog showFloatingDialog(View panel) {
        android.content.Context context = host.getContext();
        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(panel);
        AiDialogHelper.applyCloseOnlyDismiss(dialog);
        AiDialogHelper.applyTransparentWindow(dialog);
        panel.setBackgroundResource(R.drawable.lolib_bg_find_floating_panel);
        dialog.show();
        if (dialog.getWindow() != null) {
            // AlertDialog 默认 dimAmount=0.6f 有遮罩，需清除（文档保持可见）。
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int screenW = metrics.widthPixels;
            int screenH = metrics.heightPixels;
            int targetW = Math.min(host.dpToPx(421), screenW - host.dpToPx(32));
            int maxH = (int) (screenH * 0.92f);
            applyFloatingPanelSize(panel, dialog, targetW, maxH);
            // 阴影：透明窗口下 root elevation 提供投影（设计稿 #0000001f 淡阴影）。
            panel.setElevation(host.dpToPx(12));
            panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), host.dpToPx(16));
                }
            });
            panel.setClipToOutline(true);

            layoutParams = dialog.getWindow().getAttributes();
            layoutParams.gravity = Gravity.TOP | Gravity.END;
            layoutParams.x = -host.dpToPx(16);
            layoutParams.y = host.getTopToolbarHeightPx() + host.dpToPx(8);
            dialog.getWindow().setAttributes(layoutParams);
        }
        return dialog;
    }

    private void bindMainPanel(View panel) {
        TextView findTab = panel.findViewById(R.id.find_tab_find);
        TextView replaceTab = panel.findViewById(R.id.find_tab_replace);
        LinearLayout findPanel = panel.findViewById(R.id.find_panel_find);
        LinearLayout replacePanel = panel.findViewById(R.id.find_panel_replace);
        EditText findQuery = panel.findViewById(R.id.find_query_input);
        EditText replaceQuery = panel.findViewById(R.id.find_replace_query_input);
        EditText replaceWith = panel.findViewById(R.id.find_replace_with_input);

        ImageButton close = panel.findViewById(R.id.find_sheet_close);
        ImageButton settings = panel.findViewById(R.id.find_sheet_settings);
        TextView findPrev = panel.findViewById(R.id.find_btn_prev);
        TextView findNext = panel.findViewById(R.id.find_btn_next);
        TextView replacePrev = panel.findViewById(R.id.find_replace_btn_prev);
        TextView replaceNext = panel.findViewById(R.id.find_replace_btn_next);
        TextView replaceAll = panel.findViewById(R.id.find_replace_btn_all);
        TextView replaceOne = panel.findViewById(R.id.find_replace_btn_one);

        findQueryView = findQuery;
        replaceQueryView = replaceQuery;
        replaceWithView = replaceWith;
        findPrevView = findPrev;
        findNextView = findNext;
        replacePrevView = replacePrev;
        replaceNextView = replaceNext;
        replaceAllView = replaceAll;
        replaceOneView = replaceOne;

        Runnable showFindTab = () -> {
            replaceTabActive = false;
            if (findTab != null) {
                styleTab(findTab, true);
            }
            if (replaceTab != null) {
                styleTab(replaceTab, false);
            }
            findPanel.setVisibility(View.VISIBLE);
            replacePanel.setVisibility(View.GONE);
            refreshStateColors();
        };
        Runnable showReplaceTab = () -> {
            replaceTabActive = true;
            if (findTab != null) {
                styleTab(findTab, false);
            }
            if (replaceTab != null) {
                styleTab(replaceTab, true);
            }
            findPanel.setVisibility(View.GONE);
            replacePanel.setVisibility(View.VISIBLE);
            mirrorQueryField(findQuery, replaceQuery);
            refreshStateColors();
        };

        if (findTab != null) {
            findTab.setOnClickListener(v -> showFindTab.run());
        }
        if (replaceTab != null) {
            replaceTab.setOnClickListener(v -> showReplaceTab.run());
        }
        close.setOnClickListener(v -> dismiss());
        settings.setOnClickListener(v -> showSettings());

        TextWatcher syncWatcher = new SimpleTextWatcher(() -> {
            if (syncingQueryFields) {
                return;
            }
            if (replaceTabActive) {
                mirrorQueryField(replaceQuery, findQuery);
            } else {
                mirrorQueryField(findQuery, replaceQuery);
            }
            refreshStateColors();
            pushOptionsToBridge();
        });
        findQuery.addTextChangedListener(syncWatcher);
        replaceQuery.addTextChangedListener(syncWatcher);
        replaceWith.addTextChangedListener(new SimpleTextWatcher(() -> {
            refreshStateColors();
            pushOptionsToBridge();
        }));

        findPrev.setOnClickListener(v -> runFindAction(
                "AndroidFindReplaceBridge.findPrevious()"));
        findNext.setOnClickListener(v -> runFindAction(
                buildFindJs(replaceTabActive ? replaceQuery : findQuery)));
        findNext.setOnLongClickListener(v -> {
            runFindAction("AndroidFindReplaceBridge.findNext()");
            return true;
        });

        replacePrev.setOnClickListener(v -> runReplaceNavigation(
                "AndroidFindReplaceBridge.findPrevious()"));
        replaceNext.setOnClickListener(v -> runReplaceNavigation(
                buildFindJs(replaceQuery)));
        replaceAll.setOnClickListener(v -> runReplaceAction(
                buildReplaceJs(replaceQuery, replaceWith, true), true, hasText(replaceQuery)));
        replaceOne.setOnClickListener(v -> runReplaceAction(
                buildReplaceJs(replaceQuery, replaceWith, false), false, hasText(replaceQuery)));

        showFindTab.run();
        pushOptionsToBridge();
    }

    private void showSettings() {
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_find_settings, null);
        SwitchCompat fuzzySwitch = panel.findViewById(R.id.find_opt_ignore_case);
        SwitchCompat caseSensitiveSwitch = panel.findViewById(R.id.find_opt_case_sensitive);
        SwitchCompat wholeWordSwitch = panel.findViewById(R.id.find_opt_whole_word);
        ImageButton back = panel.findViewById(R.id.find_settings_back);

        styleFindSwitch(fuzzySwitch);
        styleFindSwitch(caseSensitiveSwitch);
        styleFindSwitch(wholeWordSwitch);

        fuzzySwitch.setChecked(ignoreCase);
        caseSensitiveSwitch.setChecked(caseSensitive);
        wholeWordSwitch.setChecked(wholeWord);

        fuzzySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ignoreCase = isChecked;
            if (isChecked) {
                caseSensitive = false;
                caseSensitiveSwitch.setChecked(false);
            }
            pushOptionsToBridge();
        });
        caseSensitiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            caseSensitive = isChecked;
            if (isChecked) {
                ignoreCase = false;
                fuzzySwitch.setChecked(false);
            }
            pushOptionsToBridge();
        });
        wholeWordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wholeWord = isChecked;
            pushOptionsToBridge();
        });
        back.setOnClickListener(v -> dismissSettings());

        if (BottomSheetAnchorHelper.isLandscape(host.getContext())) {
            floatingSettingsDialog = showFloatingDialog(panel);
        } else {
            settingsDialog = new BottomSheetDialog(host.getContext());
            settingsDialog.setContentView(panel);
            AiDialogHelper.applyCloseOnlyDismiss(settingsDialog);
            settingsDialog.setOnDismissListener(dialog -> settingsDialog = null);
            settingsDialog.setOnShowListener(d -> expandSheet(settingsDialog, panel));
            settingsDialog.show();
            AiDialogHelper.applyNoDimScrim(settingsDialog);
        }
    }

    private void dismissSettings() {
        if (settingsDialog != null) {
            settingsDialog.dismiss();
            settingsDialog = null;
        }
        if (floatingSettingsDialog != null) {
            floatingSettingsDialog.dismiss();
            floatingSettingsDialog = null;
        }
    }

    private static void styleFindSwitch(SwitchCompat toggle) {
        if (toggle == null) {
            return;
        }
        toggle.setTrackResource(R.drawable.lolib_bg_find_toggle_track);
        toggle.setThumbResource(R.drawable.lolib_bg_find_toggle_thumb);
        toggle.setShowText(false);
        toggle.setSplitTrack(false);
    }

    private void runFindAction(String js) {
        pushOptionsToBridge();
        host.runFindBridge(js);
    }

    private void runReplaceNavigation(String js) {
        host.ensureEditModeThen(() -> {
            pushOptionsToBridge();
            host.runFindBridge(js);
        });
    }

    private void runReplaceAction(String js, boolean replaceAll, boolean hasQuery) {
        host.ensureEditModeThen(() -> {
            pushOptionsToBridge();
            host.runFindBridge(js);
            if (hasQuery) {
                host.onFindReplaceEditDispatched(replaceAll);
            }
        });
    }

    private void mirrorQueryField(EditText source, EditText target) {
        CharSequence sourceText = source.getText() == null ? "" : source.getText();
        if (TextUtils.equals(sourceText, target.getText())) {
            return;
        }
        syncingQueryFields = true;
        try {
            target.setText(sourceText);
        } finally {
            syncingQueryFields = false;
        }
    }

    private void pushOptionsToBridge() {
        host.runFindBridge(
                "AndroidFindReplaceBridge.setOptions({"
                        + "ignoreCase:" + ignoreCase + ","
                        + "caseSensitive:" + caseSensitive + ","
                        + "wholeWord:" + wholeWord
                        + "})");
    }

    private String buildFindJs(EditText queryField) {
        String query = escapeJs(queryField.getText() == null ? "" : queryField.getText().toString());
        return "AndroidFindReplaceBridge.find('" + query + "')";
    }

    private String buildReplaceJs(EditText queryField, EditText withField, boolean replaceAll) {
        String query = escapeJs(queryField.getText() == null ? "" : queryField.getText().toString());
        String with = escapeJs(withField.getText() == null ? "" : withField.getText().toString());
        return "AndroidFindReplaceBridge.replaceForQuery('"
                + query + "','" + with + "'," + replaceAll + ")";
    }

    private static boolean hasText(EditText field) {
        return field != null
                && field.getText() != null
                && !field.getText().toString().trim().isEmpty();
    }

    private static String escapeJs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active
                ? R.drawable.lolib_bg_find_segment_active
                : R.drawable.lolib_bg_find_segment_inactive);
        tab.setTextColor(active ? 0xFF101010 : 0xFF6A6A6A);
    }

    /** 依据输入框是否有文字，动态切换按钮文字色与输入框边框色（Figma 空态灰 / 有输入深）。 */
    private void refreshStateColors() {
        boolean hasQuery = hasText(findQueryView) || hasText(replaceQueryView);
        if (findPrevView != null) {
            findPrevView.setTextColor(hasText(findQueryView) ? 0xFF101010 : 0xFFCCCCCC);
        }
        if (findNextView != null) {
            findNextView.setTextColor(hasText(findQueryView) ? 0xFF101010 : 0xFFCCCCCC);
        }
        if (replacePrevView != null) {
            replacePrevView.setTextColor(hasQuery ? 0xFF101010 : 0xFFCCCCCC);
        }
        if (replaceNextView != null) {
            replaceNextView.setTextColor(hasQuery ? 0xFF101010 : 0xFFCCCCCC);
        }
        if (replaceAllView != null) {
            replaceAllView.setTextColor(hasQuery ? 0xFF101010 : 0xFFCCCCCC);
        }
        // 替换主按钮固定蓝底白字（Figma 26359 主操作）
        if (replaceOneView != null) {
            replaceOneView.setTextColor(0xFFFFFFFF);
            replaceOneView.setBackgroundResource(R.drawable.lolib_bg_find_replace_primary);
        }
        setInputFieldBorder(findQueryView, hasText(findQueryView));
        setInputFieldBorder(replaceQueryView, hasText(replaceQueryView));
        setInputFieldBorder(replaceWithView, hasText(replaceWithView));
    }

    private void setInputFieldBorder(EditText field, boolean hasText) {
        if (field == null || field.getBackground() == null) {
            return;
        }
        try {
            android.graphics.drawable.GradientDrawable drawable =
                    (android.graphics.drawable.GradientDrawable) field.getBackground();
            drawable.setStroke(Math.max(1, host.dpToPx(1)), hasText ? 0xFF101010 : 0xFFD8D8D8);
        } catch (ClassCastException ignored) {
        }
    }

    private void expandSheet(BottomSheetDialog dialog, View contentRoot) {
        if (dialog == null) {
            return;
        }
        BottomSheetStyleHelper.applyFigmaPanel(dialog, contentRoot, host.dpToPx(28));
        BottomSheetAnchorHelper.Options options = new BottomSheetAnchorHelper.Options();
        options.logTag = "FindReplaceSheet";
        options.draggable = false;
        BottomSheetAnchorHelper.expandWrapContent(dialog, 0.92f, options);
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleTextWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChange.run();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
