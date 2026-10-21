package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private View mainPanel;
    private View settingsPanel;
    private AlertDialog floatingDialog;
    private AlertDialog floatingSettingsDialog;
    private boolean replaceTabActive = false;
    /** 编辑模式才有替换；预览只查找。 */
    private boolean replaceEnabled = false;
    private boolean floatingPlaced = false;
    private int floatingX;
    private int floatingY;
    private float dragLastRawX;
    private float dragLastRawY;
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
    /** 竖屏主弹窗锚定高度，设置页与之对齐避免高度跳变。 */
    private int portraitSheetTargetHeightPx;

    public FindReplaceSheetController(Host host) {
        this.host = host;
    }

    public void show() {
        dismiss();
        host.hideKeyboardForBottomSheet();
        replaceEnabled = host.isEditModeActive();
        floatingPlaced = false;
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_find_replace, null);
        mainPanel = panel;
        bindMainPanel(panel);
        floatingDialog = showFloatingDialog(panel);
    }

    public void dismiss() {
        dismissSettings();
        portraitSheetTargetHeightPx = 0;
        floatingPlaced = false;
        if (floatingDialog != null) {
            floatingDialog.dismiss();
            floatingDialog = null;
        }
        if (mainDialog != null) {
            mainDialog.dismiss();
            mainDialog = null;
        }
    }

    /** Size the floating window to the currently visible tab, plus transparent shadow space. */
    private void applyFloatingPanelSize(View panel, AlertDialog dialog, int targetWidthPx, int maxHeightPx) {
        if (dialog == null || dialog.getWindow() == null || panel == null) {
            return;
        }
        int shadowMargin = host.dpToPx(20);
        panel.post(() -> {
            Window window = dialog.getWindow();
            if (window == null || !dialog.isShowing()) {
                return;
            }
            // GONE tabs must not contribute height: the tab callbacks resize the
            // window again when switching between find and replace.
            int widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidthPx, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            panel.measure(widthSpec, heightSpec);
            int contentHeight = Math.max(panel.getMeasuredHeight(), 1);
            ViewGroup.LayoutParams lp = panel.getLayoutParams();
            if (lp != null) {
                lp.width = targetWidthPx;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                panel.setLayoutParams(lp);
            }
            int windowWidth = targetWidthPx + shadowMargin * 2;
            int windowHeight = Math.min(contentHeight + shadowMargin * 2, maxHeightPx);
            window.setLayout(windowWidth, windowHeight);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.gravity = Gravity.TOP | Gravity.START;
            attrs.x = floatingX;
            attrs.y = floatingY;
            window.setAttributes(attrs);
            Log.i("FindReplaceSheet", "find_panel_size content=" + targetWidthPx + "x" + contentHeight
                    + " window=" + windowWidth + "x" + windowHeight + " replace=" + replaceTabActive);
        });
    }

    /** 悬浮查找窗：默认在顶栏下方靠右，按住标题可拖开，避免挡住正文。 */
    private AlertDialog showFloatingDialog(View panel) {
        android.content.Context context = host.getContext();
        AlertDialog dialog = new AlertDialog.Builder(context).create();
        // Keep the shadow gutter transparent and outside the white content card.
        int shadowMargin = host.dpToPx(20);
        FrameLayout content = new FrameLayout(context);
        content.setPadding(shadowMargin, shadowMargin, shadowMargin, shadowMargin);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setView(content, 0, 0, 0, 0);
        AiDialogHelper.applyCloseOnlyDismiss(dialog);
        AiDialogHelper.applyTransparentWindow(dialog);
        panel.setBackgroundResource(R.drawable.lolib_bg_find_floating_panel);
        dialog.show();
        if (dialog.getWindow() != null) {
            // The shared helper uses a white window card; only our content is white.
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // AlertDialog 默认 dimAmount=0.6f 有遮罩，需清除（文档保持可见）。
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int screenW = metrics.widthPixels;
            int screenH = metrics.heightPixels;
            int targetW = Math.min(host.dpToPx(421), screenW - host.dpToPx(72));
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

            WindowManager.LayoutParams placed = dialog.getWindow().getAttributes();
            placed.gravity = Gravity.TOP | Gravity.START;
            if (!floatingPlaced) {
                int margin = host.dpToPx(16);
                floatingX = Math.max(margin, screenW - targetW - shadowMargin * 2 - margin);
                floatingY = host.getTopToolbarHeightPx() + host.dpToPx(8);
                floatingPlaced = true;
            }
            placed.x = floatingX;
            placed.y = floatingY;
            dialog.getWindow().setAttributes(placed);
            attachDragHandle(dialog, panel.findViewById(R.id.find_sheet_title));
            installFloatingInsets(dialog);
        }
        return dialog;
    }

    private void applyFindMode(View panel) {
        TextView title = panel.findViewById(R.id.find_sheet_title);
        View tabBar = panel.findViewById(R.id.find_tab_bar);
        if (title != null) {
            title.setText(replaceEnabled ? "查找替换" : "查找");
        }
        if (tabBar != null) {
            tabBar.setVisibility(replaceEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void relayoutFloating(View panel) {
        if (floatingDialog == null || panel == null || !floatingDialog.isShowing()) {
            return;
        }
        android.util.DisplayMetrics metrics = host.getContext().getResources().getDisplayMetrics();
        int targetW = Math.min(host.dpToPx(421), metrics.widthPixels - host.dpToPx(72));
        int maxH = (int) (metrics.heightPixels * 0.92f);
        applyFloatingPanelSize(panel, floatingDialog, targetW, maxH);
    }

    private void attachDragHandle(AlertDialog dialog, View handle) {
        if (handle == null) {
            return;
        }
        handle.setOnTouchListener((v, event) -> {
            if (dialog.getWindow() == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragLastRawX = event.getRawX();
                    dragLastRawY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - dragLastRawX);
                    int dy = Math.round(event.getRawY() - dragLastRawY);
                    dragLastRawX = event.getRawX();
                    dragLastRawY = event.getRawY();
                    moveFloatingWindow(dialog, floatingX + dx, floatingY + dy);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void moveFloatingWindow(AlertDialog dialog, int x, int y) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        android.util.DisplayMetrics metrics = host.getContext().getResources().getDisplayMetrics();
        int windowW = window.getDecorView().getWidth();
        int windowH = window.getDecorView().getHeight();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (windowW <= 0) {
            windowW = lp.width > 0 ? lp.width : 0;
        }
        if (windowH <= 0) {
            windowH = lp.height > 0 ? lp.height : 0;
        }
        int maxX = Math.max(0, metrics.widthPixels - windowW);
        int maxY = Math.max(0, metrics.heightPixels - windowH);
        floatingX = Math.max(0, Math.min(x, maxX));
        floatingY = Math.max(0, Math.min(y, maxY));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = floatingX;
        lp.y = floatingY;
        window.setAttributes(lp);
    }

    private void installFloatingInsets(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        View decor = window.getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decor, (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = imeVisible ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            int screenH = host.getContext().getResources().getDisplayMetrics().heightPixels;
            int windowH = v.getHeight();
            if (windowH <= 0 && window.getAttributes().height > 0) {
                windowH = window.getAttributes().height;
            }
            int maxY = Math.max(0, screenH - imeBottom - windowH - host.dpToPx(8));
            if (floatingY > maxY) {
                moveFloatingWindow(dialog, floatingX, maxY);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(decor);
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

        applyFindMode(panel);

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
            relayoutFloating(panel);
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
            relayoutFloating(panel);
        };

        if (findTab != null) {
            findTab.setOnClickListener(v -> showFindTab.run());
        }
        if (replaceTab != null) {
            replaceTab.setOnClickListener(v -> {
                if (replaceEnabled) {
                    showReplaceTab.run();
                }
            });
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
        installKeyboardLift(findQuery, replaceQuery, replaceWith);

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
        SwitchCompat caseSensitiveSwitch = panel.findViewById(R.id.find_opt_case_sensitive);
        SwitchCompat wholeWordSwitch = panel.findViewById(R.id.find_opt_whole_word);
        ImageButton back = panel.findViewById(R.id.find_settings_back);

        SwitchUiHelper.applySettingsSwitch(caseSensitiveSwitch);
        SwitchUiHelper.applySettingsSwitch(wholeWordSwitch);

        caseSensitiveSwitch.setChecked(caseSensitive);
        wholeWordSwitch.setChecked(wholeWord);

        caseSensitiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            caseSensitive = isChecked;
            pushOptionsToBridge();
        });
        wholeWordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wholeWord = isChecked;
            pushOptionsToBridge();
        });
        back.setOnClickListener(v -> dismissSettings());

        settingsPanel = panel;
        if (BottomSheetAnchorHelper.isLandscape(host.getContext())) {
            floatingSettingsDialog = showFloatingDialog(panel);
        } else {
            settingsDialog = new BottomSheetDialog(host.getContext());
            settingsDialog.setContentView(panel);
            AiDialogHelper.applyCloseOnlyDismiss(settingsDialog);
            settingsDialog.setOnDismissListener(dialog -> {
                settingsDialog = null;
                settingsPanel = null;
            });
            settingsDialog.setOnShowListener(d -> expandSettingsSheet(settingsDialog, panel));
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

    /**
     * 主弹窗高度取「查找 / 替换」两 tab 各自测量后的较大值（避免双 panel 叠测偏高）。
     */
    private int measureMainPanelHeightPx(View panel) {
        int findHeight = measureWithTabVisibility(panel, View.VISIBLE, View.GONE);
        int replaceHeight = measureWithTabVisibility(panel, View.GONE, View.VISIBLE);
        return Math.max(findHeight, replaceHeight);
    }

    private int measureWithTabVisibility(View panel, int findVisibility, int replaceVisibility) {
        View findPanel = panel.findViewById(R.id.find_panel_find);
        View replacePanel = panel.findViewById(R.id.find_panel_replace);
        int findVis = findPanel != null ? findPanel.getVisibility() : View.VISIBLE;
        int replaceVis = replacePanel != null ? replacePanel.getVisibility() : View.GONE;
        if (findPanel != null) {
            findPanel.setVisibility(findVisibility);
        }
        if (replacePanel != null) {
            replacePanel.setVisibility(replaceVisibility);
        }
        try {
            return measurePanelHeightPx(panel);
        } finally {
            if (findPanel != null) {
                findPanel.setVisibility(findVis);
            }
            if (replacePanel != null) {
                replacePanel.setVisibility(replaceVis);
            }
        }
    }

    private int measurePanelHeightPx(View panel) {
        int screenWidth = host.getContext().getResources().getDisplayMetrics().widthPixels;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        panel.measure(widthSpec, heightSpec);
        return Math.max(panel.getMeasuredHeight(), 1);
    }

    private void installKeyboardLift(EditText... fields) {
        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if (!hasFocus) {
                return;
            }
            BottomSheetDialog dialog = mainDialog != null ? mainDialog : settingsDialog;
            if (dialog != null) {
                BottomSheetAnchorHelper.requestInsetsRefresh(dialog);
            }
        };
        for (EditText field : fields) {
            if (field != null) {
                field.setOnFocusChangeListener(listener);
            }
        }
    }

    private void expandMainSheet(BottomSheetDialog dialog, View contentRoot) {
        int measured = measureMainPanelHeightPx(contentRoot);
        portraitSheetTargetHeightPx = measured;
        expandPortraitSheet(dialog, contentRoot, measured);
    }

    private void expandSettingsSheet(BottomSheetDialog dialog, View contentRoot) {
        int measured = measurePanelHeightPx(contentRoot);
        int target = portraitSheetTargetHeightPx > 0 ? portraitSheetTargetHeightPx : measured;
        expandPortraitSheet(dialog, contentRoot, target);
    }

    private void expandPortraitSheet(BottomSheetDialog dialog, View contentRoot, int measuredHeight) {
        if (dialog == null || contentRoot == null) {
            return;
        }
        BottomSheetStyleHelper.applyFigmaPanel(dialog, contentRoot, host.dpToPx(28));
        int screenHeight = host.getContext().getResources().getDisplayMetrics().heightPixels;
        int maxHeight = Math.round(screenHeight * 0.92f);
        int targetHeight = Math.min(Math.max(measuredHeight, 1), maxHeight);

        BottomSheetAnchorHelper.Options options = new BottomSheetAnchorHelper.Options();
        options.logTag = "FindReplaceSheet";
        options.draggable = false;
        options.applyNavBarPadding = false;
        options.internalBottomDesignPadPx = contentRoot.getResources()
                .getDimensionPixelSize(org.libreoffice.androidlib.R.dimen.lolib_function_sheet_bottom_pad);
        BottomSheetAnchorHelper.clearAppliedHeight(dialog);
        BottomSheetAnchorHelper.expandFixed(dialog, targetHeight, options);

        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null && bottomSheet.getChildCount() > 0) {
            View root = bottomSheet.getChildAt(0);
            ViewGroup.LayoutParams params = root.getLayoutParams();
            if (params != null) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                root.setLayoutParams(params);
            }
        }
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
