package org.libreoffice.androidlib;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Native floating menu for text selection in Writer preview and edit modes.
 * Compact 5+5+2 button panel positioned near the selection anchor.
 */
public class SelectionMenuController {
    private static final String TAG = "SelectionMenu";
    private static final float POPUP_MARGIN_DP = 16f;
    private static final float POPUP_ANCHOR_GAP_DP = 12f;
    private static final float POPUP_SELECTION_GAP_DP = 24f;
    /** Figma 245:7964 outer frame 602px @2x → 301dp max content width. */
    private static final float POPUP_MAX_WIDTH_DP = 301f;
    private static final float POPUP_MIN_WIDTH_DP = 220f;
    private static final int[] SELECTION_AI_SECTION_IDS = new int[] {
            R.id.selection_divider_1,
            R.id.selection_ai_row_1,
            R.id.selection_divider_2,
            R.id.selection_ai_row_2
    };

    public interface Host {
        Context getContext();

        View findViewById(int id);

        boolean isDocEditable();

        boolean isEditModeActive();

        void ensureEditModeThen(Runnable action);

        void executeUnoCommand(String command);

        void copySelectionToSystemClipboard();

        void cutSelectionToSystemClipboard();

        void pasteFromSystemClipboard();

        /** Same as paste; {@code onComplete} runs after async paste finishes (e.g. hide menu). */
        void pasteFromSystemClipboard(Runnable onComplete);

        void saveDocument();

        void hideQuickActionPanel();

        boolean onAiOperation(String taskType);

        void onSelectionPopupShown();

        View getBrowserView();

        /** Fallback when bottom toolbar is not laid out yet. */
        int getOverlayBottomReservedPx();
    }

    private final Host host;
    private View overlayView;
    private View menuView;
    private boolean visible = false;
    private boolean graphicMode = false;
    private boolean calcMode = false;
    private float pendingAnchorX;
    private float pendingAnchorY;
    private float pendingAnchorBottomY;

    public SelectionMenuController(Host host) {
        this.host = host;
    }

    public void setup() {
        overlayView = host.findViewById(R.id.selection_menu_overlay);
        menuView = host.findViewById(R.id.selection_popup_panel);
        if (overlayView == null || menuView == null) {
            Log.w(TAG, "setup_incomplete overlay=" + (overlayView != null)
                    + " menu=" + (menuView != null));
            return;
        }

        overlayView.setOnClickListener(v -> hide());
        menuView.setClickable(true);

        bindOp(R.id.selection_op_copy, "copy", this::onCopy);
        bindOp(R.id.selection_op_delete, "delete", this::onDelete);
        bindOp(R.id.selection_op_paste, "paste", this::onPaste);
        bindOp(R.id.selection_op_cut, "cut", this::onCut);
        bindOp(R.id.selection_op_select_all, "select_all", this::onSelectAll);
        bindOp(R.id.selection_op_image_edit, "image_edit", this::onImageEdit);
        bindOp(R.id.selection_op_save, "save", this::onSave);

        bindOp(R.id.selection_op_translate, "translate", () -> onAiOperation("translate"));
        bindOp(R.id.selection_op_outline, "outline", () -> onAiOperation("outline"));
        bindOp(R.id.selection_op_continue_write, "continue_write", () -> onAiOperation("continue_write"));
        bindOp(R.id.selection_op_article_generate, "article_generate", () -> onAiOperation("article_generate"));
        bindOp(R.id.selection_op_expand, "expand", () -> onAiOperation("expand"));
        bindOp(R.id.selection_op_polish, "polish", () -> onAiOperation("polish"));
        bindOp(R.id.selection_op_condense, "condense", () -> onAiOperation("condense"));
        bindOp(R.id.selection_op_rewrite, "rewrite", () -> onAiOperation("rewrite"));

        updateEditActionVisibility();
        hide();
    }

    private interface OpAction {
        void run();
    }

    /** Prefer menuView subtree lookup (include layout); fall back to activity root. */
    private void bindOp(int viewId, String opName, OpAction action) {
        View target = menuView != null ? menuView.findViewById(viewId) : null;
        if (target == null) {
            target = host.findViewById(viewId);
        }
        if (target == null) {
            Log.e(TAG, "selection_op_missing id=" + opName + " viewId=" + viewId);
            return;
        }
        target.setOnClickListener(v -> {
            Log.i(TAG, "selection_op_click op=" + opName);
            action.run();
        });
    }

    /** @param windowX anchor X in document-area coordinates (from JS). */
    public void showAtWindow(float windowX, float windowY) {
        showAtWindow(windowX, windowY, windowY);
    }

    /** @param anchorBottomY selection bottom Y for flip-below positioning. */
    public void showAtWindow(float windowX, float windowY, float anchorBottomY) {
        if (overlayView == null || menuView == null) {
            Log.w(TAG, "show_aborted overlay=" + (overlayView != null)
                    + " menu=" + (menuView != null)
                    + " calcMode=" + calcMode);
            return;
        }
        host.hideQuickActionPanel();
        pendingAnchorX = windowX;
        pendingAnchorY = windowY;
        pendingAnchorBottomY = anchorBottomY;
        updateEditActionVisibility();

        menuView.setVisibility(View.VISIBLE);
        overlayView.setVisibility(View.VISIBLE);
        // 保证浮层在 WebView/底栏之上接收触摸（部分机型 elevation 不足会点透）
        overlayView.setElevation(58f);
        menuView.setElevation(60f);
        menuView.bringToFront();
        overlayView.bringToFront();
        ViewGroup parent = (ViewGroup) menuView.getParent();
        if (parent != null) {
            parent.bringChildToFront(overlayView);
            parent.bringChildToFront(menuView);
        }
        visible = true;

        menuView.post(this::positionPopupNearAnchor);
        host.onSelectionPopupShown();
        Log.i(TAG, "selection_popup_show x=" + windowX + " y=" + windowY
                + " bottom=" + anchorBottomY);
    }

    public void hide() {
        if (overlayView == null || menuView == null) {
            return;
        }
        menuView.setVisibility(View.GONE);
        overlayView.setVisibility(View.GONE);
        visible = false;
        graphicMode = false;
        calcMode = false;
        updateActionLabel(R.id.selection_op_delete, "删除");
    }

    public boolean isVisible() {
        return visible;
    }

    /** 横竖屏切换后按当前选区坐标重新定位（WebView 布局更新后调用）。 */
    public void onConfigurationChanged() {
        if (!visible || menuView == null) {
            return;
        }
        menuView.post(this::positionPopupNearAnchor);
    }

    /** Switch to graphic/image selection mode showing Delete/Copy/Cut. */
    public void setGraphicMode(boolean graphic) {
        graphicMode = graphic;
        if (graphic) {
            calcMode = false;
        }
        updateEditActionVisibility();
    }

    /** Calc cell selection: copy/paste/cut/clear/translate near the active cell. */
    public void setCalcMode(boolean calc) {
        calcMode = calc;
        if (calc) {
            graphicMode = false;
        }
        updateEditActionVisibility();
    }

    private void positionPopupNearAnchor() {
        View parent = (View) menuView.getParent();
        if (!(parent instanceof ConstraintLayout)) {
            return;
        }

        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        int menuWidth = menuView.getWidth();
        int menuHeight = menuView.getHeight();
        if (menuWidth <= 0 || menuHeight <= 0) {
            return;
        }

        float margin = dpToPx(POPUP_MARGIN_DP);
        float anchorGap = dpToPx(POPUP_ANCHOR_GAP_DP);
        float selectionGap = dpToPx(POPUP_SELECTION_GAP_DP);
        View bottomToolbar = host.findViewById(R.id.doc_bottom_toolbar);
        int bottomReserved = DocumentOverlayInsets.resolveBottomReservedPx(
                (View) parent, bottomToolbar, host.getOverlayBottomReservedPx());
        float maxContentBottom = parentHeight - bottomReserved - margin;

        float x;
        float y;

        if (pendingAnchorX == 0f && pendingAnchorY == 0f) {
            // Fallback for legacy bundle without coordinates.
            x = (parentWidth - menuWidth) / 2f;
            y = Math.max(margin, (maxContentBottom - menuHeight) / 2f);
        } else {
            View browser = host.getBrowserView();
            float baseX = browser != null ? browser.getLeft() : 0f;
            float baseY = browser != null ? browser.getTop() : 0f;
            float anchorX = baseX + pendingAnchorX;
            float anchorY = baseY + pendingAnchorY;
            float anchorBottomY = baseY + pendingAnchorBottomY;

            x = anchorX - menuWidth / 2f;
            x = Math.max(margin, Math.min(x, parentWidth - menuWidth - margin));

            float selectionCenterY = (anchorY + anchorBottomY) / 2f;
            float spaceAbove = selectionCenterY - margin;
            float spaceBelow = maxContentBottom - selectionCenterY;
            float aboveTop = anchorY - menuHeight - anchorGap;
            float belowTop = anchorBottomY + selectionGap;

            boolean canPlaceAbove = aboveTop >= margin;
            boolean canPlaceBelow = belowTop + menuHeight <= maxContentBottom;
            boolean preferAbove = spaceAbove >= spaceBelow;

            if (preferAbove && canPlaceAbove) {
                y = aboveTop;
            } else if (canPlaceBelow) {
                y = belowTop;
            } else if (canPlaceAbove) {
                y = aboveTop;
            } else {
                y = Math.max(margin, Math.min(belowTop, maxContentBottom - menuHeight));
            }
            y = DocumentOverlayInsets.clampTopInParent(
                    y, menuHeight, parentHeight, margin, bottomReserved);
        }

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) menuView.getLayoutParams();
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = Math.round(x);
        lp.topMargin = Math.round(y);
        menuView.setLayoutParams(lp);
    }

    private void onSelectAll() {
        host.executeUnoCommand(".uno:SelectAll");
        hide();
    }

    private void onCopy() {
        host.copySelectionToSystemClipboard();
        hide();
    }

    private void onPaste() {
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        runEditSensitiveClipboardAction(() ->
                host.pasteFromSystemClipboard(this::hide));
    }

    private void onCut() {
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        runEditSensitiveClipboardAction(() -> {
            host.cutSelectionToSystemClipboard();
            hide();
        });
    }

    /** Run immediately in edit mode; otherwise switch to edit first (keeps UNO after mode ready). */
    private void runEditSensitiveClipboardAction(Runnable action) {
        if (host.isEditModeActive()) {
            action.run();
        } else {
            host.ensureEditModeThen(action);
        }
    }

    private void onDelete() {
        hide();
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        if (calcMode) {
            host.ensureEditModeThen(() -> host.executeUnoCommand(".uno:ClearContents"));
            return;
        }
        host.ensureEditModeThen(() -> host.executeUnoCommand(".uno:Delete"));
    }

    private void onImageEdit() {
        hide();
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        host.ensureEditModeThen(() -> host.executeUnoCommand(".uno:Crop"));
    }

    private void onSave() {
        hide();
        host.saveDocument();
    }

    private void onAiOperation(String taskType) {
        hide();
        if (host.onAiOperation(taskType)) {
            Log.i(TAG, "ai_operation started: " + taskType);
        }
    }

    private void toastReadOnlyDocument() {
        Toast.makeText(host.getContext(), "当前文档为只读，无法粘贴或剪切", Toast.LENGTH_SHORT).show();
    }

    private void updateEditActionVisibility() {
        boolean docEditable = host.isDocEditable();
        boolean editMode = host.isEditModeActive();

        if (calcMode) {
            setViewVisibility(R.id.selection_op_select_all, View.GONE);
            setViewVisibility(R.id.selection_op_copy, View.VISIBLE);
            setViewVisibility(R.id.selection_op_paste, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_cut, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_delete, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_image_edit, View.GONE);
            setViewVisibility(R.id.selection_op_save, View.GONE);
            setViewVisibility(R.id.selection_op_translate, editMode ? View.VISIBLE : View.GONE);
            for (int sectionId : SELECTION_AI_SECTION_IDS) {
                setViewVisibility(sectionId, View.GONE);
            }
            updateActionLabel(R.id.selection_op_delete, "清除");
            updatePopupWidth(false);
            applyPopupButtonLayout(false);
        } else if (graphicMode) {
            // Impress 图形选中：单行紧凑浮层（复制/剪切/粘贴/删除/图片编辑/保存）
            setViewVisibility(R.id.selection_op_copy, View.VISIBLE);
            setViewVisibility(R.id.selection_op_cut, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_paste, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_delete, docEditable ? View.VISIBLE : View.GONE);
            updateActionLabel(R.id.selection_op_delete, "删除");
            setViewVisibility(R.id.selection_op_image_edit, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_save, View.VISIBLE);
            setViewVisibility(R.id.selection_op_select_all, View.GONE);
            setViewVisibility(R.id.selection_op_translate, View.GONE);
            for (int sectionId : SELECTION_AI_SECTION_IDS) {
                setViewVisibility(sectionId, View.GONE);
            }
            updatePopupWidthForGraphic();
            applyPopupButtonLayout(false);
        } else {
            updateActionLabel(R.id.selection_op_delete, "删除");
            setViewVisibility(R.id.selection_op_delete, View.GONE);
            setViewVisibility(R.id.selection_op_image_edit, View.GONE);
            setViewVisibility(R.id.selection_op_save, View.GONE);
            setViewVisibility(R.id.selection_op_select_all, View.VISIBLE);
            setViewVisibility(R.id.selection_op_copy, View.VISIBLE);
            // Paste/cut: available whenever the document is editable (preview or edit mode).
            setViewVisibility(R.id.selection_op_paste, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_cut, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_translate, editMode ? View.VISIBLE : View.GONE);
            for (int sectionId : SELECTION_AI_SECTION_IDS) {
                setViewVisibility(sectionId, editMode ? View.VISIBLE : View.GONE);
            }
            updatePopupWidth(true);
            applyPopupButtonLayout(true);
        }
    }

    /** Weighted equal cells (Writer) vs fixed-width chips (graphic/calc). */
    private void applyPopupButtonLayout(boolean weighted) {
        int[] actionIds = new int[] {
                R.id.selection_op_copy,
                R.id.selection_op_cut,
                R.id.selection_op_paste,
                R.id.selection_op_select_all,
                R.id.selection_op_delete,
                R.id.selection_op_image_edit,
                R.id.selection_op_save,
                R.id.selection_op_translate,
                R.id.selection_op_outline,
                R.id.selection_op_continue_write,
                R.id.selection_op_article_generate,
                R.id.selection_op_expand,
                R.id.selection_op_polish,
                R.id.selection_op_condense,
                R.id.selection_op_rewrite,
        };
        for (int actionId : actionIds) {
            View action = host.findViewById(actionId);
            if (!(action instanceof ViewGroup)) {
                continue;
            }
            ViewGroup.LayoutParams lp = action.getLayoutParams();
            if (!(lp instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) lp;
            if (weighted) {
                rowLp.width = 0;
                rowLp.weight = 1f;
                rowLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                rowLp.width = Math.round(dpToPx(62f));
                rowLp.weight = 0f;
                rowLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            action.setLayoutParams(rowLp);
        }
    }

    private void setViewVisibility(int viewId, int visibility) {
        View v = host.findViewById(viewId);
        if (v != null) {
            v.setVisibility(visibility);
        }
    }

    private void updateActionLabel(int viewId, String label) {
        View action = host.findViewById(viewId);
        if (!(action instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) action;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setText(label);
                return;
            }
        }
    }

    private void updatePopupWidthForGraphic() {
        if (menuView == null) return;
        ViewGroup.LayoutParams lp = menuView.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            menuView.setLayoutParams(lp);
        }
    }

    private void updatePopupWidth(boolean fillAvailable) {
        if (menuView == null) {
            return;
        }
        ViewGroup.LayoutParams lp = menuView.getLayoutParams();
        if (lp == null) {
            return;
        }
        int targetWidth;
        if (fillAvailable) {
            targetWidth = computePopupTargetWidth();
        } else if (calcMode || graphicMode) {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            menuView.setLayoutParams(lp);
            return;
        } else {
            targetWidth = computePopupTargetWidth();
        }
        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            menuView.setLayoutParams(lp);
        }
    }

    /** Available document width capped at Figma max (245:7964). */
    private int computePopupTargetWidth() {
        View parent = menuView != null ? (View) menuView.getParent() : null;
        int parentWidth = parent != null ? parent.getWidth() : 0;
        int maxWidth = Math.round(dpToPx(POPUP_MAX_WIDTH_DP));
        int minWidth = Math.round(dpToPx(POPUP_MIN_WIDTH_DP));
        if (parentWidth > 0) {
            int available = parentWidth - Math.round(dpToPx(32f));
            return Math.max(minWidth, Math.min(maxWidth, available));
        }
        return maxWidth;
    }

    private float dpToPx(float dp) {
        return dp * host.getContext().getResources().getDisplayMetrics().density;
    }
}
