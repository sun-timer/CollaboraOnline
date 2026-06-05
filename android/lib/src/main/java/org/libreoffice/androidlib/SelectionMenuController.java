package org.libreoffice.androidlib;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Native floating menu for text selection in mobile preview (read-only UI) mode.
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
        menuView = host.findViewById(R.id.selection_menu_panel);
        if (overlayView == null || menuView == null) {
            return;
        }

        overlayView.setOnClickListener(v -> hide());
        host.findViewById(R.id.selection_menu_select_all).setOnClickListener(v -> onSelectAll());
        host.findViewById(R.id.selection_menu_copy).setOnClickListener(v -> onCopy());
        host.findViewById(R.id.selection_menu_paste).setOnClickListener(v -> onPaste());
        host.findViewById(R.id.selection_menu_cut).setOnClickListener(v -> onCut());
        hide();
    }

    /** @param windowX anchor X in window coordinates (from Web getBoundingClientRect). */
    public void showAtWindow(float windowX, float windowY) {
        if (overlayView == null || menuView == null) {
            return;
        }
        host.hideQuickActionPanel();

        menuView.setVisibility(View.VISIBLE);
        overlayView.setVisibility(View.VISIBLE);
        visible = true;

        menuView.post(() -> positionMenuAtWindow(windowX, windowY));
        Log.i(TAG, "selection_menu_show x=" + windowX + " y=" + windowY);
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

    private void positionMenuAtWindow(float windowX, float windowY) {
        View parent = (View) menuView.getParent();
        if (!(parent instanceof ConstraintLayout)) {
            return;
        }

        int[] parentLoc = new int[2];
        parent.getLocationInWindow(parentLoc);
        float anchorX = windowX - parentLoc[0];
        float anchorY = windowY - parentLoc[1];

        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        int menuWidth = menuView.getWidth();
        int menuHeight = menuView.getHeight();
        if (menuWidth <= 0 || menuHeight <= 0) {
            return;
        }

        float margin = dpToPx(12);
        float x = anchorX - menuWidth / 2f;
        float y = anchorY - menuHeight - dpToPx(16);

        x = Math.max(margin, Math.min(x, parentWidth - menuWidth - margin));
        y = Math.max(margin, Math.min(y, parentHeight - menuHeight - margin));

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) menuView.getLayoutParams();
        lp.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
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

    private void toastReadOnlyDocument() {
        Toast.makeText(host.getContext(), "当前文档为只读，无法粘贴或剪切", Toast.LENGTH_SHORT).show();
    }

    private float dpToPx(float dp) {
        return dp * host.getContext().getResources().getDisplayMetrics().density;
    }
}
