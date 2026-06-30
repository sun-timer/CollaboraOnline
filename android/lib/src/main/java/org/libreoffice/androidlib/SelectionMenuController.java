package org.libreoffice.androidlib;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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
    private static final float POPUP_MAX_WIDTH_DP = 296f; // 5 × 56dp + padding
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

        void performPasteCommand();

        void hideQuickActionPanel();

        boolean onAiOperation(String taskType);

        void onSelectionPopupShown();

        View getBrowserView();
    }

    private final Host host;
    private View overlayView;
    private View menuView;
    private boolean visible = false;
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
            return;
        }

        overlayView.setOnClickListener(v -> hide());

        host.findViewById(R.id.selection_op_copy).setOnClickListener(v -> onCopy());
        host.findViewById(R.id.selection_op_paste).setOnClickListener(v -> onPaste());
        host.findViewById(R.id.selection_op_cut).setOnClickListener(v -> onCut());
        host.findViewById(R.id.selection_op_select_all).setOnClickListener(v -> onSelectAll());

        host.findViewById(R.id.selection_op_translate).setOnClickListener(v -> onAiOperation("translate"));
        host.findViewById(R.id.selection_op_outline).setOnClickListener(v -> onAiOperation("outline"));
        host.findViewById(R.id.selection_op_continue_write).setOnClickListener(v -> onAiOperation("continue_write"));
        host.findViewById(R.id.selection_op_article_generate).setOnClickListener(v -> onAiOperation("article_generate"));
        host.findViewById(R.id.selection_op_expand).setOnClickListener(v -> onAiOperation("expand"));
        host.findViewById(R.id.selection_op_polish).setOnClickListener(v -> onAiOperation("polish"));
        host.findViewById(R.id.selection_op_condense).setOnClickListener(v -> onAiOperation("condense"));
        host.findViewById(R.id.selection_op_rewrite).setOnClickListener(v -> onAiOperation("rewrite"));

        updateEditActionVisibility();
        hide();
    }

    /** @param windowX anchor X in document-area coordinates (from JS). */
    public void showAtWindow(float windowX, float windowY) {
        showAtWindow(windowX, windowY, windowY);
    }

    /** @param anchorBottomY selection bottom Y for flip-below positioning. */
    public void showAtWindow(float windowX, float windowY, float anchorBottomY) {
        if (overlayView == null || menuView == null) {
            return;
        }
        host.hideQuickActionPanel();
        pendingAnchorX = windowX;
        pendingAnchorY = windowY;
        pendingAnchorBottomY = anchorBottomY;
        updateEditActionVisibility();

        menuView.setVisibility(View.VISIBLE);
        overlayView.setVisibility(View.VISIBLE);
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
    }

    public boolean isVisible() {
        return visible;
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

        float x;
        float y;

        if (pendingAnchorX == 0f && pendingAnchorY == 0f) {
            // Fallback for legacy bundle without coordinates.
            x = (parentWidth - menuWidth) / 2f;
            y = Math.max(margin, (parentHeight - menuHeight) / 2f - dpToPx(40));
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
            float spaceBelow = parentHeight - selectionCenterY - margin;
            float aboveTop = anchorY - menuHeight - anchorGap;
            float belowTop = anchorBottomY + selectionGap;

            boolean canPlaceAbove = aboveTop >= margin;
            boolean canPlaceBelow = belowTop + menuHeight <= parentHeight - margin;
            boolean preferAbove = spaceAbove >= spaceBelow;

            if (preferAbove && canPlaceAbove) {
                y = aboveTop;
            } else if (canPlaceBelow) {
                y = belowTop;
            } else if (canPlaceAbove) {
                y = aboveTop;
            } else {
                y = Math.max(margin, Math.min(belowTop, parentHeight - menuHeight - margin));
            }
            y = Math.max(margin, Math.min(y, parentHeight - menuHeight - margin));
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
        hide();
        host.executeUnoCommand(".uno:SelectAll");
    }

    private void onCopy() {
        hide();
        host.executeUnoCommand(".uno:Copy");
    }

    private void onPaste() {
        hide();
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        host.ensureEditModeThen(host::performPasteCommand);
    }

    private void onCut() {
        hide();
        if (!host.isDocEditable()) {
            toastReadOnlyDocument();
            return;
        }
        host.ensureEditModeThen(() -> host.executeUnoCommand(".uno:Cut"));
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
        boolean showEditActions = host.isEditModeActive();
        View paste = host.findViewById(R.id.selection_op_paste);
        View cut = host.findViewById(R.id.selection_op_cut);
        View translate = host.findViewById(R.id.selection_op_translate);
        if (paste != null) {
            paste.setVisibility(showEditActions ? View.VISIBLE : View.GONE);
        }
        if (cut != null) {
            cut.setVisibility(showEditActions ? View.VISIBLE : View.GONE);
        }
        if (translate != null) {
            translate.setVisibility(showEditActions ? View.VISIBLE : View.GONE);
        }
        for (int sectionId : SELECTION_AI_SECTION_IDS) {
            View section = host.findViewById(sectionId);
            if (section != null) {
                section.setVisibility(showEditActions ? View.VISIBLE : View.GONE);
            }
        }
        updatePopupWidth(showEditActions);
    }

    private void updatePopupWidth(boolean showEditActions) {
        if (menuView == null) {
            return;
        }
        ViewGroup.LayoutParams lp = menuView.getLayoutParams();
        if (lp == null) {
            return;
        }
        if (showEditActions) {
            View parent = (View) menuView.getParent();
            int parentWidth = parent != null ? parent.getWidth() : 0;
            int maxWidth = Math.round(dpToPx(POPUP_MAX_WIDTH_DP));
            if (parentWidth > 0) {
                maxWidth = Math.min(maxWidth, parentWidth - Math.round(dpToPx(32)));
            }
            if (lp.width != maxWidth) {
                lp.width = maxWidth;
                menuView.setLayoutParams(lp);
            }
        } else {
            if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                menuView.setLayoutParams(lp);
            }
        }
    }

    private float dpToPx(float dp) {
        return dp * host.getContext().getResources().getDisplayMetrics().density;
    }
}
