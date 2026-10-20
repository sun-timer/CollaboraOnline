package org.libreoffice.androidlib;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Native floating menu for text selection in Writer preview and edit modes.
 * Compact horizontally scrolling button panel positioned near the selection anchor.
 */
public class SelectionMenuController {
    private static final String TAG = "SelectionMenu";
    private static final float POPUP_MARGIN_DP = 16f;
    private static final float POPUP_ANCHOR_GAP_DP = 12f;
    private static final float POPUP_SELECTION_GAP_DP = 24f;
    /** Existing compact preview/calc/graphic menu width. */
    private static final float POPUP_MAX_WIDTH_DP = 301f;
    private static final float POPUP_MIN_WIDTH_DP = 220f;
    private static final float POPUP_EDIT_MAX_WIDTH_DP = 320f;
    private static final int[] SELECTION_AI_ACTION_IDS = new int[] {
            R.id.selection_op_outline,
            R.id.selection_op_continue_write,
            R.id.selection_op_article_generate,
            R.id.selection_op_expand,
            R.id.selection_op_polish,
            R.id.selection_op_condense,
            R.id.selection_op_rewrite
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
    private boolean textEditMode = false;
    private float pendingAnchorX;
    private float pendingAnchorY;
    private float pendingAnchorBottomY;
    private int positionRetry;

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
        View menuParent = (View) menuView.getParent();
        if (menuParent != null) {
            menuParent.addOnLayoutChangeListener((v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                if (visible) {
                    refreshWidthAndPosition();
                }
            });
        }
        menuView.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            boolean sizeChanged = right - left != oldRight - oldLeft
                    || bottom - top != oldBottom - oldTop;
            if (visible && sizeChanged) {
                positionPopupNearAnchor();
            }
        });

        ImageButton nextButton = menuView.findViewById(R.id.selection_strip_next);
        HorizontalScrollView scrollView = menuView.findViewById(R.id.selection_basic_scroll);
        if (nextButton != null && scrollView != null) {
            nextButton.setOnClickListener(v -> {
                int viewport = scrollView.getWidth();
                if (viewport > 0) {
                    scrollView.smoothScrollBy(viewport, 0);
                }
            });
            scrollView.getViewTreeObserver().addOnScrollChangedListener(
                    this::updateNextButtonState);
            scrollView.addOnLayoutChangeListener((v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                applyTextEditButtonLayout();
                updateNextButtonState();
            });
        }

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
        positionRetry = 0;
        updateEditActionVisibility();
        if (textEditMode) {
            HorizontalScrollView scrollView = menuView.findViewById(R.id.selection_basic_scroll);
            if (scrollView != null) {
                scrollView.scrollTo(0, 0);
            }
        }

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
        positionRetry = 0;
        refreshWidthAndPosition();
    }

    private void refreshWidthAndPosition() {
        if (textEditMode) {
            updatePopupWidth(true);
            applyTextEditStripLayout();
        } else if (graphicMode) {
            updatePopupWidthForGraphic();
        } else if (calcMode) {
            updatePopupWidth(false);
        } else {
            updatePopupWidth(true);
        }
        if (menuView != null && visible) {
            menuView.post(this::positionPopupNearAnchor);
        }
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
            // First frame after VISIBLE often has no size. Leaving it there
            // keeps the XML default, which is the top-left corner.
            if (visible && positionRetry < 8) {
                positionRetry++;
                menuView.post(this::positionPopupNearAnchor);
            }
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
        if (!host.isEditModeActive()) {
            return;
        }
        host.pasteFromSystemClipboard(this::hide);
    }

    private void onCut() {
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        if (!host.isEditModeActive()) {
            return;
        }
        host.cutSelectionToSystemClipboard();
        hide();
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
        textEditMode = !calcMode && !graphicMode && editMode;
        applyPopupMaxWidthConstraint();

        if (calcMode) {
            setViewVisibility(R.id.selection_op_select_all, View.GONE);
            setViewVisibility(R.id.selection_op_copy, View.VISIBLE);
            setViewVisibility(R.id.selection_op_paste, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_cut, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_delete, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_image_edit, View.GONE);
            setViewVisibility(R.id.selection_op_save, View.GONE);
            setViewVisibility(R.id.selection_op_translate, editMode ? View.VISIBLE : View.GONE);
            setAiActionsVisibility(View.GONE);
            updateActionLabel(R.id.selection_op_delete, "清除");
            updatePopupWidth(false);
            applyPopupButtonLayout(false);
        } else if (graphicMode) {
            // Impress 图形选中：单行紧凑浮层（复制/剪切/粘贴/删除/图片编辑/保存）
            setViewVisibility(R.id.selection_op_copy, View.VISIBLE);
            setViewVisibility(R.id.selection_op_cut, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_paste, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_delete, docEditable ? View.VISIBLE : View.GONE);
            updateActionLabel(R.id.selection_op_delete, "删除");
            setViewVisibility(R.id.selection_op_image_edit, docEditable ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_save, View.VISIBLE);
            setViewVisibility(R.id.selection_op_select_all, View.GONE);
            setViewVisibility(R.id.selection_op_translate, View.GONE);
            setAiActionsVisibility(View.GONE);
            updatePopupWidthForGraphic();
            applyPopupButtonLayout(false);
        } else {
            updateActionLabel(R.id.selection_op_delete, "删除");
            setViewVisibility(R.id.selection_op_delete, View.GONE);
            setViewVisibility(R.id.selection_op_image_edit, View.GONE);
            setViewVisibility(R.id.selection_op_save, View.GONE);
            setViewVisibility(R.id.selection_op_select_all, View.VISIBLE);
            setViewVisibility(R.id.selection_op_copy, View.VISIBLE);
            // Paste/cut: only when the document is writable and already in edit mode.
            setViewVisibility(R.id.selection_op_paste, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_cut, docEditable && editMode ? View.VISIBLE : View.GONE);
            setViewVisibility(R.id.selection_op_translate, editMode ? View.VISIBLE : View.GONE);
            setAiActionsVisibility(editMode ? View.VISIBLE : View.GONE);
            updatePopupWidth(true);
            if (textEditMode) {
                applyTextEditStripLayout();
            } else {
                applyPopupButtonLayout(true);
            }
        }
        applyPopupAppearance();
    }

    private void setAiActionsVisibility(int visibility) {
        for (int actionId : SELECTION_AI_ACTION_IDS) {
            setViewVisibility(actionId, visibility);
        }
    }

    private void applyPopupAppearance() {
        if (menuView == null) {
            return;
        }
        if (textEditMode) {
            menuView.setBackgroundResource(R.drawable.lolib_bg_selection_popup_edit);
            int padding = Math.round(dpToPx(4f));
            menuView.setPadding(padding, padding, padding, padding);
        } else {
            menuView.setBackgroundResource(R.drawable.lolib_bg_selection_popup);
            int padding = Math.round(dpToPx(8f));
            menuView.setPadding(padding, padding, padding, padding);
        }
        ImageButton nextButton = menuView.findViewById(R.id.selection_strip_next);
        HorizontalScrollView scrollView = menuView.findViewById(R.id.selection_basic_scroll);
        if (nextButton != null) {
            nextButton.setVisibility(textEditMode ? View.VISIBLE : View.GONE);
        }
        if (scrollView != null) {
            ViewGroup.LayoutParams rawParams = scrollView.getLayoutParams();
            if (rawParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
                int width = textEditMode ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
                float weight = textEditMode ? 1f : 0f;
                if (params.width != width || params.weight != weight) {
                    params.width = width;
                    params.weight = weight;
                    scrollView.setLayoutParams(params);
                }
            }
        }
        updateNextButtonState();
    }

    private void applyPopupMaxWidthConstraint() {
        if (menuView == null || !(menuView.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) {
            return;
        }
        ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) menuView.getLayoutParams();
        int maxWidth = Math.round(dpToPx(textEditMode ? POPUP_EDIT_MAX_WIDTH_DP : POPUP_MAX_WIDTH_DP));
        if (params.matchConstraintMaxWidth != maxWidth) {
            params.matchConstraintMaxWidth = maxWidth;
            menuView.setLayoutParams(params);
        }
    }

    private void applyTextEditStripLayout() {
        applyTextEditButtonLayout();
        View row = menuView != null ? menuView.findViewById(R.id.selection_basic_row) : null;
        if (row != null && row.getLayoutParams() != null) {
            ViewGroup.LayoutParams params = row.getLayoutParams();
            if (params.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                row.setLayoutParams(params);
            }
        }
    }

    private void applyTextEditButtonLayout() {
        if (!textEditMode || menuView == null) {
            return;
        }
        HorizontalScrollView scrollView = menuView.findViewById(R.id.selection_basic_scroll);
        int viewport = scrollView != null ? scrollView.getWidth() : 0;
        if (viewport <= 0) {
            return;
        }
        int buttonWidth = Math.max(1, viewport / 5);
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
            View action = menuView.findViewById(actionId);
            if (action == null || !(action.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) action.getLayoutParams();
            if (params.width != buttonWidth || params.weight != 0f) {
                params.width = buttonWidth;
                params.weight = 0f;
                action.setLayoutParams(params);
            }
        }
    }

    private void updateNextButtonState() {
        if (menuView == null) {
            return;
        }
        ImageButton nextButton = menuView.findViewById(R.id.selection_strip_next);
        HorizontalScrollView scrollView = menuView.findViewById(R.id.selection_basic_scroll);
        if (nextButton == null || scrollView == null || !textEditMode) {
            return;
        }
        View row = menuView.findViewById(R.id.selection_basic_row);
        boolean hasMore = row != null
                && scrollView.getScrollX() + scrollView.getWidth() < row.getWidth() - 1;
        nextButton.setEnabled(hasMore);
        nextButton.setAlpha(hasMore ? 1f : 0.35f);
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
                rowLp.width = Math.round(dpToPx(56f));
                rowLp.weight = 0f;
                rowLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            action.setLayoutParams(rowLp);
        }
        View basicRow = host.findViewById(R.id.selection_basic_row);
        if (basicRow != null && basicRow.getLayoutParams() != null) {
            basicRow.getLayoutParams().width = weighted
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            basicRow.requestLayout();
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
        if (textEditMode) {
            targetWidth = computeTextEditPopupWidth();
        } else if (fillAvailable) {
            targetWidth = computePopupTargetWidth();
        } else if (calcMode || graphicMode) {
            // Cap to the screen so chips wider than the viewport scroll inside
            // the row. WRAP_CONTENT grows with the chips and never scrolls.
            int available = computeAvailablePopupWidth();
            int content = computeVisibleChipRowWidthPx();
            // One visible chip (Calc preview: copy only) stays one cell wide.
            targetWidth = Math.min(content, available);
        } else {
            targetWidth = computePopupTargetWidth();
        }
        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            menuView.setLayoutParams(lp);
        }
    }

    /** On-screen width for the calc/graphic chip row, excluding side margins. */
    private int computeAvailablePopupWidth() {
        View parent = menuView != null ? (View) menuView.getParent() : null;
        int parentWidth = parent != null ? parent.getWidth() : 0;
        int margin = Math.round(dpToPx(32f));
        if (parentWidth > margin) {
            return parentWidth - margin;
        }
        return Math.round(dpToPx(POPUP_MAX_WIDTH_DP));
    }

    /** Fixed 62dp chips plus the popup's horizontal padding. */
    private int computeVisibleChipRowWidthPx() {
        int[] chipIds = new int[] {
                R.id.selection_op_copy,
                R.id.selection_op_cut,
                R.id.selection_op_paste,
                R.id.selection_op_select_all,
                R.id.selection_op_delete,
                R.id.selection_op_image_edit,
                R.id.selection_op_save,
                R.id.selection_op_translate,
        };
        int visible = 0;
        for (int chipId : chipIds) {
            View chip = menuView != null ? menuView.findViewById(chipId) : null;
            if (chip != null && chip.getVisibility() == View.VISIBLE) {
                visible++;
            }
        }
        int padding = menuView != null
                ? menuView.getPaddingLeft() + menuView.getPaddingRight() : 0;
        return visible * Math.round(dpToPx(62f)) + padding;
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

    /** Edit strip leaves at least 16dp at each side, including on narrow devices. */
    private int computeTextEditPopupWidth() {
        View parent = menuView != null ? (View) menuView.getParent() : null;
        int parentWidth = parent != null ? parent.getWidth() : 0;
        int maxWidth = Math.round(dpToPx(POPUP_EDIT_MAX_WIDTH_DP));
        if (parentWidth > 0) {
            int available = Math.max(1, parentWidth - Math.round(dpToPx(32f)));
            return Math.min(maxWidth, available);
        }
        return maxWidth;
    }

    private float dpToPx(float dp) {
        return dp * host.getContext().getResources().getDisplayMetrics().density;
    }
}
