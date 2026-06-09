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

        void executeUnoCommand(String command);

        void toastTodo(String text);

        String getDocumentTitle();
    }

    private final Host host;
    private LinearLayout topToolbarView;
    private LinearLayout previewToolbarView;
    private LinearLayout editToolbarView;
    private TextView previewTitleView;
    private boolean isEditModeActive = false;

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

        bindClick(R.id.top_btn_back, v -> host.onTopToolbarBack());
        bindClick(R.id.top_btn_done, v -> host.switchToViewingMode());
        bindClick(R.id.top_btn_close_edit, v -> host.switchToViewingMode());

        bindClick(R.id.top_btn_undo, v -> host.executeUnoCommand(".uno:Undo"));
        bindClick(R.id.top_btn_redo, v -> host.executeUnoCommand(".uno:Redo"));

        bindClick(R.id.top_btn_search_preview, v -> host.toastTodo("搜索功能后续接入。"));
        bindClick(R.id.top_btn_share_preview, v -> host.toastTodo("分享功能后续接入。"));
        bindClick(R.id.top_btn_recent_preview, v -> host.toastTodo("最近打开功能后续接入。"));
        bindClick(R.id.top_btn_search_edit, v -> host.toastTodo("搜索功能后续接入。"));
        bindClick(R.id.top_btn_recent_edit, v -> host.toastTodo("最近打开功能后续接入。"));

        refreshDocumentTitle();
        updateEditModeState(isEditModeActive, "toolbar_setup");
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

    private static LinearLayout asLinearLayout(View view) {
        return view instanceof LinearLayout ? (LinearLayout) view : null;
    }

    private static TextView asTextView(View view) {
        return view instanceof TextView ? (TextView) view : null;
    }
}
