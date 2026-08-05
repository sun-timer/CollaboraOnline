package org.libreoffice.androidlib;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.json.JSONObject;

/**
 * Calc Sheet Tab 长按 — 原生菜单（重命名 / 复制），替代 MobileWizard / C++ 弹窗。
 */
public final class CalcSheetTabPopupController {

    private static final String TAG = "CalcSheetTabPopup";
    private static final float MARGIN_DP = 12f;
    private static final float GAP_ABOVE_ANCHOR_DP = 8f;
    private static final float RENAME_DIALOG_MAX_WIDTH_DP = 670f;
    private static final float RENAME_DIALOG_SIDE_MARGIN_DP = 48f;

    public interface Host {
        Context getContext();

        View findViewById(int id);

        View getBrowserView();

        float dpToPx(float dp);

        void executeUnoCommand(String command);

        void evaluateJavascript(String script);

        void copySheet(int tabIndex);

        void ensureEditModeThen(Runnable action);
    }

    private final Host host;
    private View overlayView;
    private View popupView;
    private View renameRow;
    private View renameOverlayView;
    private View renamePanelView;
    private EditText renameInputView;
    private int renameTabIndex = -1;
    private int currentTabIndex = -1;
    private String currentSheetName = "";
    private boolean currentProtected = false;
    private float lastAnchorX = 0f;
    private float lastAnchorY = 0f;
    private float lastAnchorBottom = 0f;

    public CalcSheetTabPopupController(Host host) {
        this.host = host;
    }

    public void setup() {
        overlayView = host.findViewById(R.id.calc_sheet_tab_popup_overlay);
        popupView = host.findViewById(R.id.calc_sheet_tab_popup_panel);
        renameOverlayView = host.findViewById(R.id.calc_sheet_rename_overlay);
        renamePanelView = host.findViewById(R.id.calc_sheet_rename_panel);
        if (overlayView == null || popupView == null) {
            return;
        }
        overlayView.setOnClickListener(v -> hide());
        renameRow = popupView.findViewById(R.id.calc_sheet_tab_popup_rename);
        View copyRow = popupView.findViewById(R.id.calc_sheet_tab_popup_copy);
        if (renameRow != null) {
            renameRow.setOnClickListener(v -> onRenameClicked());
        }
        if (copyRow != null) {
            copyRow.setOnClickListener(v -> onCopyClicked());
        }
        setupRenameDialog();
    }

    private void setupRenameDialog() {
        Log.i(TAG, "setup_rename_dialog overlayNull=" + (renameOverlayView == null)
                + " panelNull=" + (renamePanelView == null));
        if (renameOverlayView == null || renamePanelView == null) {
            return;
        }
        renameOverlayView.setOnClickListener(v -> hideRenameDialog());
        renameInputView = renamePanelView.findViewById(R.id.calc_sheet_rename_input);
        TextView titleView = renamePanelView.findViewById(R.id.ai_dialog_header_title);
        View closeBtn = renamePanelView.findViewById(R.id.ai_dialog_header_close);
        View cancelBtn = renamePanelView.findViewById(R.id.calc_sheet_rename_cancel);
        View confirmBtn = renamePanelView.findViewById(R.id.calc_sheet_rename_confirm);
        Log.i(TAG, "setup_rename_dialog inputNull=" + (renameInputView == null)
                + " titleNull=" + (titleView == null)
                + " closeNull=" + (closeBtn == null)
                + " cancelNull=" + (cancelBtn == null)
                + " confirmNull=" + (confirmBtn == null));
        if (titleView != null) {
            titleView.setText("重命名工作表");
        }
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> hideRenameDialog());
        }
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> hideRenameDialog());
        }
        if (confirmBtn != null) {
            confirmBtn.setOnClickListener(v -> confirmRename());
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
                || (renamePanelView != null && renamePanelView.getVisibility() == View.VISIBLE);
    }

    public void hide() {
        hideRenameDialog();
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        currentTabIndex = -1;
        currentSheetName = "";
        currentProtected = false;
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
            float anchorX = (float) payload.optDouble("anchorX", 0);
            float anchorY = (float) payload.optDouble("anchorY", 0);
            float anchorBottom = (float) payload.optDouble("anchorBottom", anchorY);
            show(tabIndex, sheetName, isProtected, anchorX, anchorY, anchorBottom);
        } catch (Exception e) {
            Log.w(TAG, "showFromJson failed: " + e.getMessage());
        }
    }

    public void show(int tabIndex, String sheetName, boolean isProtected,
                     float anchorX, float anchorY, float anchorBottom) {
        if (popupView == null || tabIndex < 0) {
            return;
        }
        currentTabIndex = tabIndex;
        currentSheetName = sheetName != null ? sheetName : "";
        currentProtected = isProtected;
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        lastAnchorBottom = anchorBottom > 0 ? anchorBottom : anchorY;

        if (renameRow != null) {
            renameRow.setEnabled(!isProtected);
            renameRow.setAlpha(isProtected ? 0.4f : 1f);
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

    private void showRenameDialog(int tabIndex, String sheetName,
                                  float anchorX, float anchorY, float anchorBottom) {
        if (renameOverlayView == null || renamePanelView == null || renameInputView == null || tabIndex < 0) {
            return;
        }
        renameTabIndex = tabIndex;
        renameInputView.setText(sheetName != null ? sheetName : "");
        renameInputView.setSelection(renameInputView.getText().length());
        renameOverlayView.setVisibility(View.VISIBLE);
        renamePanelView.setVisibility(View.VISIBLE);
        // 确保弹窗面板在透明遮罩之上，否则确定/取消点击会被 overlay 拦截（点确定只关闭弹窗）
        renamePanelView.bringToFront();
        renamePanelView.post(() -> {
            positionRenameDialogNearAnchor(anchorX, anchorY, anchorBottom);
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

    private void positionRenameDialogNearAnchor(float anchorX, float anchorY, float anchorBottom) {
        View parent = (View) renamePanelView.getParent();
        if (parent == null || !(renamePanelView.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) {
            return;
        }
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) {
            return;
        }
        int targetWidth = Math.min(
                Math.round(host.dpToPx(RENAME_DIALOG_MAX_WIDTH_DP)),
                parentWidth - Math.round(host.dpToPx(RENAME_DIALOG_SIDE_MARGIN_DP)));
        renamePanelView.measure(
                View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = renamePanelView.getMeasuredWidth();
        int popupH = renamePanelView.getMeasuredHeight();

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) renamePanelView.getLayoutParams();
        lp.width = targetWidth;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.horizontalBias = 0.5f;
        lp.verticalBias = 0.5f;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        renamePanelView.setLayoutParams(lp);
        Log.i(TAG, "position_rename_dialog_centered size=" + popupW + "x" + popupH
                + " parent=" + parentWidth + "x" + parentHeight);
    }

    private void hideRenameDialog() {
        Log.i(TAG, "hide_rename_dialog from=" + Thread.currentThread().getStackTrace()[3].getMethodName());
        if (renameInputView != null) {
            InputMethodManager imm = (InputMethodManager) host.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(renameInputView.getWindowToken(), 0);
            }
        }
        if (renameOverlayView != null) {
            renameOverlayView.setVisibility(View.GONE);
        }
        if (renamePanelView != null) {
            renamePanelView.setVisibility(View.GONE);
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
            // core FID_TAB_RENAME 需要：文档可编辑(IsDocEditable)、sheet 未保护(IsTabProtected)、
            // 选中单一 sheet；否则直接 return 不重命名。因此用 ensureEditModeThen 包裹，
            // 确保进入编辑模式后再发命令。
            // Index 参数：core 按 1-based 读入后转回 0-based（tabvwshf.cxx），故传 tabIndex+1。
            final String command = ".uno:Name?Name:string=" + newName + "&Index=" + (tabIndex + 1);
            host.ensureEditModeThen(() -> {
                host.executeUnoCommand(command);
                Log.i(TAG, "rename_sheet_uno index=" + tabIndex + " name=" + newName
                        + " command=" + command);
            });
        } catch (Exception e) {
            Log.w(TAG, "rename_sheet failed: " + e.getMessage());
        }
    }
}
