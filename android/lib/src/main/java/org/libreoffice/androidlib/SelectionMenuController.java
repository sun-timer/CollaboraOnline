package org.libreoffice.androidlib;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Native floating menu for text selection in mobile preview (read-only UI) mode.
 * Shows a 12-button popup panel with basic operations and AI operations.
 */
public class SelectionMenuController {
    private static final String TAG = "SelectionMenu";

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
    }

    private final Host host;
    private View overlayView;
    private View menuView;
    private boolean visible = false;

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

        // Basic operations
        host.findViewById(R.id.selection_op_copy).setOnClickListener(v -> onCopy());
        host.findViewById(R.id.selection_op_paste).setOnClickListener(v -> onPaste());
        host.findViewById(R.id.selection_op_cut).setOnClickListener(v -> onCut());
        host.findViewById(R.id.selection_op_select_all).setOnClickListener(v -> onSelectAll());

        // AI operations
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

    /** @param windowX anchor X in window coordinates (from Web getBoundingClientRect). */
    public void showAtWindow(float windowX, float windowY) {
        if (overlayView == null || menuView == null) {
            return;
        }
        host.hideQuickActionPanel();
        updateEditActionVisibility();

        menuView.setVisibility(View.VISIBLE);
        overlayView.setVisibility(View.VISIBLE);
        visible = true;

        menuView.post(() -> positionPopupAtCenter());
        // Pre-read selection so AI buttons work immediately when tapped.
        host.onSelectionPopupShown();
        Log.i(TAG, "selection_popup_show x=" + windowX + " y=" + windowY);
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

    private void positionPopupAtCenter() {
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

        // Center horizontally; place vertically in the upper-middle area
        // (clears the bottom toolbar and stays visible above the selection).
        float margin = dpToPx(16);
        float x = (parentWidth - menuWidth) / 2f;
        float y = Math.max(margin, (parentHeight - menuHeight) / 2f - dpToPx(40));

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) menuView.getLayoutParams();
        // Keep the fixed 602x365 size from the layout; only reposition.
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
        if (paste != null) {
            paste.setVisibility(showEditActions ? View.VISIBLE : View.GONE);
        }
        if (cut != null) {
            cut.setVisibility(showEditActions ? View.VISIBLE : View.GONE);
        }
    }

    private float dpToPx(float dp) {
        return dp * host.getContext().getResources().getDisplayMetrics().density;
    }
}
