package org.libreoffice.androidlib;

import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TopToolbarController {
    private static final String TAG = "TopToolbarController";

    public interface Host {
        View findViewById(int id);

        void runOnUiThread(Runnable runnable);

        void onTopToolbarBack();

        void switchToViewingMode();

        void requestCloseDocument();

        void executeUnoCommand(String command);

        void showFindReplaceSheet();

        void shareCurrentDocument();

        void showDocumentTabsSheet();

        String getDocumentTitle();
    }

    private final Host host;
    private LinearLayout topToolbarView;
    private LinearLayout previewToolbarView;
    private LinearLayout editToolbarView;
    private TextView previewTitleView;
    private View undoButton;
    private View redoButton;
    private boolean isEditModeActive = false;
    private int undoCount = 0;
    private int redoCount = 0;

    public TopToolbarController(Host host) {
        this.host = host;
    }

    public void setup() {
        topToolbarView = asLinearLayout(host.findViewById(R.id.doc_top_toolbar));
        if (topToolbarView != null) {
            topToolbarView.setVisibility(View.VISIBLE);
        }
        previewToolbarView = asLinearLayout(host.findViewById(R.id.top_toolbar_preview));
        editToolbarView = asLinearLayout(host.findViewById(R.id.top_toolbar_edit));
        previewTitleView = asTextView(host.findViewById(R.id.top_title_preview));
        undoButton = host.findViewById(R.id.top_btn_undo);
        redoButton = host.findViewById(R.id.top_btn_redo);

        bindClick(R.id.top_btn_back, v -> host.onTopToolbarBack());
        bindClick(R.id.top_btn_done, v -> host.switchToViewingMode());
        bindClick(R.id.top_btn_close_edit, v -> host.requestCloseDocument());

        bindClick(R.id.top_btn_undo, v -> requestUndo());
        bindClick(R.id.top_btn_redo, v -> requestRedo());

        bindClick(R.id.top_btn_search_preview, v -> host.showFindReplaceSheet());
        bindClick(R.id.top_btn_share_preview, v -> host.shareCurrentDocument());
        bindClick(R.id.top_btn_recent_preview, v -> host.showDocumentTabsSheet());
        bindClick(R.id.top_btn_search_edit, v -> host.showFindReplaceSheet());
        bindClick(R.id.top_btn_recent_edit, v -> host.showDocumentTabsSheet());

        refreshDocumentTitle();
        resetUndoRedoState("toolbar_setup");
        updateEditModeState(isEditModeActive, "toolbar_setup");
    }

    public void recordUndoableNativeEdit(String reason) {
        Runnable applyTask = () -> {
            undoCount++;
            redoCount = 0;
            applyUndoRedoButtonState();
            Log.i(TAG, "undo_redo_record_edit reason=" + reason
                    + " undoCount=" + undoCount + " redoCount=" + redoCount);
        };
        runOnUi(applyTask);
    }

    public void updateUndoRedoState(boolean canUndo, boolean canRedo, String reason) {
        Runnable applyTask = () -> {
            undoCount = canUndo ? 1 : 0;
            redoCount = canRedo ? 1 : 0;
            applyUndoRedoButtonState();
            Log.i(TAG, "undo_redo_state_from_web reason=" + reason
                    + " canUndo=" + canUndo + " canRedo=" + canRedo);
        };
        runOnUi(applyTask);
    }

    public void resetUndoRedoState(String reason) {
        Runnable applyTask = () -> {
            undoCount = 0;
            redoCount = 0;
            applyUndoRedoButtonState();
            Log.i(TAG, "undo_redo_reset reason=" + reason);
        };
        runOnUi(applyTask);
    }

    public void updateEditModeState(boolean isEditMode, String reason) {
        isEditModeActive = isEditMode;
        Runnable applyTask = () -> {
            if (previewToolbarView != null) {
                previewToolbarView.setVisibility(isEditMode ? View.GONE : View.VISIBLE);
            }
            if (editToolbarView != null) {
                editToolbarView.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            }
            Log.i(TAG, "top_toolbar_mode edit=" + isEditMode + " reason=" + reason);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyTask.run();
        } else {
            host.runOnUiThread(applyTask);
        }
    }

    public void refreshDocumentTitle() {
        if (previewTitleView == null) {
            return;
        }
        String title = host.getDocumentTitle();
        if (title == null || title.trim().isEmpty()) {
            title = "文档";
        }
        previewTitleView.setText(title);
    }

    public int getReservedTopHeightPx() {
        if (topToolbarView != null && topToolbarView.getVisibility() == View.VISIBLE) {
            return topToolbarView.getHeight();
        }
        return 0;
    }

    private void bindClick(int viewId, View.OnClickListener listener) {
        View view = host.findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    private void requestUndo() {
        if (undoCount <= 0) {
            applyUndoRedoButtonState();
            Log.i(TAG, "undo_click_ignored undoCount=0");
            return;
        }
        undoCount--;
        redoCount++;
        applyUndoRedoButtonState();
        Log.i(TAG, "undo_click_dispatch undoCount=" + undoCount + " redoCount=" + redoCount);
        host.executeUnoCommand(".uno:Undo");
    }

    private void requestRedo() {
        if (redoCount <= 0) {
            applyUndoRedoButtonState();
            Log.i(TAG, "redo_click_ignored redoCount=0");
            return;
        }
        redoCount--;
        undoCount++;
        applyUndoRedoButtonState();
        Log.i(TAG, "redo_click_dispatch undoCount=" + undoCount + " redoCount=" + redoCount);
        host.executeUnoCommand(".uno:Redo");
    }

    private void applyUndoRedoButtonState() {
        setButtonEnabled(undoButton, undoCount > 0);
        setButtonEnabled(redoButton, redoCount > 0);
    }

    private void setButtonEnabled(View button, boolean enabled) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.32f);
    }

    private void runOnUi(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            host.runOnUiThread(task);
        }
    }

    private static LinearLayout asLinearLayout(View view) {
        return view instanceof LinearLayout ? (LinearLayout) view : null;
    }

    private static TextView asTextView(View view) {
        return view instanceof TextView ? (TextView) view : null;
    }
}
