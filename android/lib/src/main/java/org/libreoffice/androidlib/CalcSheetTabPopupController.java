package org.libreoffice.androidlib;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.libreoffice.androidlib.ai.AiDialogHelper;
import org.json.JSONObject;

/**
 * Calc Sheet Tab 长按 — 原生菜单（重命名 / 复制 / 删除），替代 MobileWizard / C++ 弹窗。
 */
public final class CalcSheetTabPopupController {

    private static final String TAG = "CalcSheetTabPopup";
    private static final float MARGIN_DP = 12f;
    private static final float GAP_ABOVE_ANCHOR_DP = 8f;

    public interface Host {
        Context getContext();

        View findViewById(int id);

        View getBrowserView();

        float dpToPx(float dp);

        void executeUnoCommand(String command);

        void evaluateJavascript(String script);

        void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback);

        void copySheet(int tabIndex);

        void deleteSheet(int tabIndex);

        void ensureEditModeThen(Runnable action);
    }

    private final Host host;
    private View overlayView;
    private View popupView;
    private View renameRow;
    private View deleteRow;
    private View renamePanelView;
    private EditText renameInputView;
    private boolean renamePanelBound;
    private AiDialogHelper.CompactPanelSession renameSession;
    private AiDialogHelper.CompactPanelSession deleteConfirmSession;
    private int renameTabIndex = -1;
    private int currentTabIndex = -1;
    private String currentSheetName = "";
    private boolean currentProtected = false;
    private boolean currentCanDelete = false;
    private float lastAnchorX = 0f;
    private float lastAnchorY = 0f;
    private float lastAnchorBottom = 0f;

    public CalcSheetTabPopupController(Host host) {
        this.host = host;
    }

    public void setup() {
        overlayView = host.findViewById(R.id.calc_sheet_tab_popup_overlay);
        popupView = host.findViewById(R.id.calc_sheet_tab_popup_panel);
        setupRenameDialog();
        if (overlayView == null || popupView == null) {
            return;
        }
        overlayView.setOnClickListener(v -> hide());
        renameRow = popupView.findViewById(R.id.calc_sheet_tab_popup_rename);
        View copyRow = popupView.findViewById(R.id.calc_sheet_tab_popup_copy);
        deleteRow = popupView.findViewById(R.id.calc_sheet_tab_popup_delete);
        if (renameRow != null) {
            renameRow.setOnClickListener(v -> onRenameClicked());
        }
        if (copyRow != null) {
            copyRow.setOnClickListener(v -> onCopyClicked());
        }
        if (deleteRow != null) {
            deleteRow.setOnClickListener(v -> onDeleteClicked());
        }
    }

    private void setupRenameDialog() {
        ensureRenamePanel();
    }

    /** 懒创建重命名面板；横竖屏分别用居中 AlertDialog / 贴底 BottomSheet 展示。 */
    private void ensureRenamePanel() {
        if (renamePanelView != null) {
            return;
        }
        renamePanelView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.lolib_dialog_calc_sheet_rename, null, false);
        bindRenamePanel(renamePanelView);
    }

    private void bindRenamePanel(View panel) {
        if (panel == null || renamePanelBound) {
            return;
        }
        renamePanelBound = true;
        renameInputView = panel.findViewById(R.id.calc_sheet_rename_input);
        TextView titleView = panel.findViewById(R.id.ai_dialog_header_title);
        View closeBtn = panel.findViewById(R.id.ai_dialog_header_close);
        View cancelBtn = panel.findViewById(R.id.calc_sheet_rename_cancel);
        View confirmBtn = panel.findViewById(R.id.calc_sheet_rename_confirm);
        Log.i(TAG, "setup_rename_dialog inputNull=" + (renameInputView == null)
                + " titleNull=" + (titleView == null)
                + " closeNull=" + (closeBtn == null)
                + " cancelNull=" + (cancelBtn == null)
                + " confirmNull=" + (confirmBtn == null));
        if (titleView != null) {
            titleView.setText("重命名工作表");
            titleView.setTextColor(0xFF1F1F1F);
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
            titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        }
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> onCloseClicked());
        }
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> onCancelClicked());
        }
        if (confirmBtn != null) {
            confirmBtn.setOnClickListener(v -> onConfirmClicked());
        }
        if (renameInputView != null) {
            renameInputView.setOnEditorActionListener((v, actionId, event) -> {
                confirmRename();
                return true;
            });
        }
    }

    public boolean isVisible() {
        return (popupView != null && popupView.getVisibility() == View.VISIBLE)
                || (renameSession != null && renameSession.isShowing())
                || (deleteConfirmSession != null && deleteConfirmSession.isShowing());
    }

    public void hide() {
        hideRenameDialog();
        hideDeleteConfirmDialog();
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        currentTabIndex = -1;
        currentSheetName = "";
        currentProtected = false;
        currentCanDelete = false;
    }

    public void showFromJson(String json) {
        if (popupView == null) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(json);
            int tabIndex = payload.optInt("tabIndex", -1);
            String sheetName = payload.optString("sheetName", "");
            boolean isProtected = payload.optBoolean("isProtected", false);
            boolean canDelete = payload.optBoolean("canDelete", false);
            float anchorX = (float) payload.optDouble("anchorX", 0);
            float anchorY = (float) payload.optDouble("anchorY", 0);
            float anchorBottom = (float) payload.optDouble("anchorBottom", anchorY);
            show(tabIndex, sheetName, isProtected, canDelete, anchorX, anchorY, anchorBottom);
        } catch (Exception e) {
            Log.w(TAG, "showFromJson failed: " + e.getMessage());
        }
    }

    public void show(int tabIndex, String sheetName, boolean isProtected, boolean canDelete,
                     float anchorX, float anchorY, float anchorBottom) {
        if (popupView == null || tabIndex < 0) {
            return;
        }
        currentTabIndex = tabIndex;
        currentSheetName = sheetName != null ? sheetName : "";
        currentProtected = isProtected;
        currentCanDelete = canDelete;
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        lastAnchorBottom = anchorBottom > 0 ? anchorBottom : anchorY;

        if (renameRow != null) {
            renameRow.setEnabled(!isProtected);
            renameRow.setAlpha(isProtected ? 0.4f : 1f);
        }
        if (deleteRow != null) {
            boolean deleteEnabled = canDelete && !isProtected;
            deleteRow.setEnabled(deleteEnabled);
            deleteRow.setAlpha(deleteEnabled ? 1f : 0.4f);
        }

        if (overlayView != null) {
            overlayView.setVisibility(View.VISIBLE);
        }
        popupView.setVisibility(View.VISIBLE);
        popupView.post(() -> positionPopup(anchorX, anchorY, lastAnchorBottom));
        Log.i(TAG, "show tabIndex=" + tabIndex + " name=" + currentSheetName
                + " anchor=" + anchorX + "," + anchorY + "," + lastAnchorBottom);
    }

    private void positionPopup(float anchorX, float anchorY, float anchorBottom) {
        // 固定 206dp 宽（对齐设计稿 412×224px ÷2），避免 WRAP_CONTENT 被内容撑满屏
        positionFloatingPanel(popupView, anchorX, anchorY, anchorBottom,
                Math.round(host.dpToPx(206f)));
    }

    /**
     * 锚定浮层：anchor* 为 WebView 内 CSS 坐标（与 SelectionMenuController 一致）。
     */
    private void positionFloatingPanel(View panel, float anchorXWeb, float anchorYWeb,
                                       float anchorBottomWeb, int fixedWidthPx) {
        if (panel == null || !(panel.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) {
            return;
        }
        View parent = (View) panel.getParent();
        if (parent == null) {
            return;
        }
        View browser = host.getBrowserView();
        float baseX = browser != null ? browser.getLeft() : 0f;
        float baseY = browser != null ? browser.getTop() : 0f;
        float margin = host.dpToPx(MARGIN_DP);
        float gap = host.dpToPx(GAP_ABOVE_ANCHOR_DP);

        if ((anchorYWeb <= 0 || anchorBottomWeb <= 0) && browser != null && browser.getHeight() > 0) {
            float sheetBarHeight = host.dpToPx(48f);
            anchorBottomWeb = browser.getHeight() - host.dpToPx(4f);
            anchorYWeb = anchorBottomWeb - sheetBarHeight;
            if (anchorXWeb <= 0) {
                anchorXWeb = browser.getWidth() * 0.5f;
            }
            Log.w(TAG, "position_panel_fallback_anchor bottom=" + anchorBottomWeb);
        }

        int widthSpec = fixedWidthPx > 0
                ? View.MeasureSpec.makeMeasureSpec(fixedWidthPx, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        panel.measure(
                widthSpec,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float popupW = panel.getMeasuredWidth();
        float popupH = panel.getMeasuredHeight();

        float anchorXInParent = baseX + anchorXWeb;
        float anchorTopInParent = baseY + anchorYWeb;
        float anchorBottomInParent = baseY + anchorBottomWeb;
        float left = anchorXInParent - popupW * 0.5f;

        View bottomToolbar = host.findViewById(R.id.doc_bottom_toolbar);
        float top;
        if (bottomToolbar != null && bottomToolbar.getTop() > 0) {
            // Sheet Tab 栏紧贴原生底栏上方，垂直位置以底栏为锚更可靠
            top = bottomToolbar.getTop() - popupH - gap;
        } else {
            top = anchorTopInParent - popupH - gap;
            if (top < margin) {
                top = anchorBottomInParent + gap;
            }
        }
        float maxLeft = parent.getWidth() - popupW - margin;
        if (left < margin) {
            left = margin;
        }
        if (left > maxLeft) {
            left = maxLeft;
        }
        top = Math.max(margin, Math.min(top, parent.getHeight() - popupH - margin));

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) panel.getLayoutParams();
        lp.width = fixedWidthPx > 0 ? fixedWidthPx : ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.rightToRight = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = Math.round(left);
        lp.topMargin = Math.round(top);
        panel.setLayoutParams(lp);
        Log.i(TAG, "position_panel base=" + baseX + "," + baseY
                + " anchorWeb=" + anchorXWeb + "," + anchorYWeb
                + " bottomToolbarTop="
                + (bottomToolbar != null ? bottomToolbar.getTop() : -1)
                + " margin=" + lp.leftMargin + "," + lp.topMargin
                + " size=" + popupW + "x" + popupH);
    }

    private void onRenameClicked() {
        if (currentProtected) {
            Toast.makeText(host.getContext(), "受保护的工作表无法重命名", Toast.LENGTH_SHORT).show();
            return;
        }
        int tabIndex = currentTabIndex;
        String sheetName = currentSheetName;
        float anchorX = lastAnchorX;
        float anchorY = lastAnchorY;
        float anchorBottom = lastAnchorBottom;
        // 仅关闭 Tab 菜单，保留 anchor；重命名弹窗用透明遮罩，不再整页变暗
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        showRenameDialog(tabIndex, sheetName, anchorX, anchorY, anchorBottom);
    }

    private void onCopyClicked() {
        int tabIndex = currentTabIndex;
        hide();
        if (tabIndex >= 0) {
            host.copySheet(tabIndex);
        }
    }

    private void onDeleteClicked() {
        if (currentProtected) {
            Toast.makeText(host.getContext(), "受保护的工作表无法删除", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!currentCanDelete) {
            Toast.makeText(host.getContext(), "至少需要保留一个工作表", Toast.LENGTH_SHORT).show();
            return;
        }
        int tabIndex = currentTabIndex;
        String sheetName = currentSheetName;
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        showDeleteConfirmDialog(tabIndex, sheetName);
    }

    private void showDeleteConfirmDialog(int tabIndex, String sheetName) {
        if (tabIndex < 0) {
            return;
        }
        View panelView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.lolib_dialog_calc_sheet_delete_confirm, null, false);
        TextView titleView = panelView.findViewById(R.id.ai_dialog_header_title);
        TextView messageView = panelView.findViewById(R.id.calc_sheet_delete_message);
        View closeBtn = panelView.findViewById(R.id.ai_dialog_header_close);
        View cancelBtn = panelView.findViewById(R.id.calc_sheet_delete_cancel);
        View confirmBtn = panelView.findViewById(R.id.calc_sheet_delete_confirm);

        if (titleView != null) {
            titleView.setText("删除工作表");
        }
        String displayName = sheetName != null && !sheetName.isEmpty() ? sheetName : ("Sheet " + (tabIndex + 1));
        if (messageView != null) {
            messageView.setText("确定要删除工作表「" + displayName + "」吗？");
        }

        hideDeleteConfirmDialog();
        deleteConfirmSession = AiDialogHelper.showCompactPanel(
                host.getContext(), panelView, TAG + ":delete");
        deleteConfirmSession.setOnDismissListener(() -> deleteConfirmSession = null);

        Runnable dismiss = this::hideDeleteConfirmDialog;
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismiss.run());
        }
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> dismiss.run());
        }
        if (confirmBtn != null) {
            confirmBtn.setOnClickListener(v -> {
                dismiss.run();
                hide();
                host.deleteSheet(tabIndex);
            });
        }

        Log.i(TAG, "show_delete_confirm tabIndex=" + tabIndex + " name=" + displayName);
    }

    private void hideDeleteConfirmDialog() {
        if (deleteConfirmSession != null) {
            deleteConfirmSession.dismiss();
            deleteConfirmSession = null;
        }
    }

    private void showRenameDialog(int tabIndex, String sheetName,
                                  float anchorX, float anchorY, float anchorBottom) {
        ensureRenamePanel();
        if (renamePanelView == null || renameInputView == null || tabIndex < 0) {
            return;
        }
        renameTabIndex = tabIndex;
        renameInputView.setText(sheetName != null ? sheetName : "");
        renameInputView.setSelection(renameInputView.getText().length());
        if (renameSession != null && renameSession.isShowing()) {
            renameSession.dismiss();
        }
        renameSession = AiDialogHelper.showCompactPanel(
                host.getContext(), renamePanelView, TAG + ":rename");
        renameSession.setOnDismissListener(() -> {
            renameSession = null;
            renameTabIndex = -1;
        });
        renamePanelView.post(() -> {
            renameInputView.requestFocus();
            InputMethodManager imm = (InputMethodManager) host.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(renameInputView, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        Log.i(TAG, "show_rename_dialog tabIndex=" + tabIndex + " name=" + sheetName
                + " anchor=" + anchorX + "," + anchorY + "," + anchorBottom);
    }

    private void onCloseClicked() {
        Log.i(TAG, "click_target=close");
        hideRenameDialog();
    }

    private void onCancelClicked() {
        Log.i(TAG, "click_target=cancel");
        hideRenameDialog();
    }

    private void onConfirmClicked() {
        Log.i(TAG, "click_target=confirm");
        confirmRename();
    }

    private void hideRenameDialog() {
        if (renameInputView != null) {
            InputMethodManager imm = (InputMethodManager) host.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(renameInputView.getWindowToken(), 0);
            }
        }
        if (renameSession != null) {
            renameSession.dismiss();
            renameSession = null;
        }
        renameTabIndex = -1;
    }

    private void confirmRename() {
        Log.i(TAG, "confirm_rename entered renameTabIndex=" + renameTabIndex
                + " inputNull=" + (renameInputView == null));
        if (renameInputView == null || renameTabIndex < 0) {
            hideRenameDialog();
            return;
        }
        String newName = renameInputView.getText() != null
                ? renameInputView.getText().toString().trim() : "";
        Log.i(TAG, "confirm_rename newName='" + newName + "' len=" + newName.length());
        if (TextUtils.isEmpty(newName)) {
            Toast.makeText(host.getContext(), "请输入工作表名称", Toast.LENGTH_SHORT).show();
            return;
        }
        int tabIndex = renameTabIndex;
        hideRenameDialog();
        renameSheet(tabIndex, newName);
    }

    private void renameSheet(int tabIndex, String newName) {
        try {
            // 复用官方已验证的 map.renamePage（Parts.js）：通过 socket 直发
            // 'uno .uno:Name {JSON}'（core 只认 JSON 参数，URL ?Name:string= 不被解析）。
            // evaluateJavascript 走 Web 层，绕开原生 postMobileMessage 链路。
            host.ensureEditModeThen(() -> {
                // 走 Web socket 直发（与可用字体命令 .uno:CharFontName 同链路）。
                // 原生 executeUnoCommand → postMobileMessage 链路对 .uno:Name 的 JSON 参数解析失败
                // （aName 空 → "Invalid sheet name"）。
                String jsName = newName.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
                String script = "(function(){"
                        + "if(window.app&&app.socket&&typeof app.socket.sendMessage==='function'){"
                        + "app.socket.sendMessage('uno .uno:Name {\\\"Name\\\":{\\\"type\\\":\\\"string\\\",\\\"value\\\":\\\""
                        + jsName + "\\\"}}');}"
                        + "return true;})();";
                host.evaluateJavascript(script);
                Log.i(TAG, "rename_sheet_via_socket index=" + tabIndex + " name=" + newName
                        + " script=" + script);
            });
        } catch (Exception e) {
            Log.w(TAG, "rename_sheet failed: " + e.getMessage());
        }
    }
}
