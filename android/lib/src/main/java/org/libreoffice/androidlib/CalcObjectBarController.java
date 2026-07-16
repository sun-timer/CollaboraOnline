package org.libreoffice.androidlib;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * 底部操作栏，用于 Calc 嵌入式对象（图表、图片、形状等）被选中时的操作。
 * 显示在文档内容区域底部，包含删除、复制、剪切按钮。
 */
public class CalcObjectBarController {

    private static final String TAG = "CalcObjectBar";

    public interface Host {
        boolean isDocEditable();
        boolean isEditModeActive();
        void ensureEditModeThen(Runnable action);
        void executeUnoCommand(String command);
        void hideQuickActionPanel();
        View findViewById(int id);
    }

    private final Host host;
    private View barView;
    private boolean visible = false;

    public CalcObjectBarController(Host host) {
        this.host = host;
    }

    public void setup() {
        barView = host.findViewById(R.id.calc_object_bar_panel);
        if (barView == null) {
            return;
        }

        host.findViewById(R.id.calc_object_op_delete).setOnClickListener(v -> onDelete());
        host.findViewById(R.id.calc_object_op_copy).setOnClickListener(v -> onCopy());
        host.findViewById(R.id.calc_object_op_cut).setOnClickListener(v -> onCut());

        hide();
    }

    /** 显示对象操作栏。 */
    public void show() {
        if (barView == null) {
            return;
        }
        host.hideQuickActionPanel();

        barView.setVisibility(View.VISIBLE);
        visible = true;
        Log.i(TAG, "calc_object_bar_show");
    }

    /** 隐藏对象操作栏。 */
    public void hide() {
        if (barView == null) {
            return;
        }
        barView.setVisibility(View.GONE);
        visible = false;
        Log.i(TAG, "calc_object_bar_hide");
    }

    public boolean isVisible() {
        return visible;
    }

    private void onDelete() {
        hide();
        if (!host.isDocEditable()) {
            toastReadOnly();
            return;
        }
        host.ensureEditModeThen(() -> host.executeUnoCommand(".uno:Delete"));
    }

    private void onCopy() {
        host.executeUnoCommand(".uno:Copy");
        // 复制不关闭操作栏，用户可能连续操作
    }

    private void onCut() {
        hide();
        if (!host.isDocEditable()) {
            toastReadOnly();
            return;
        }
        host.ensureEditModeThen(() -> host.executeUnoCommand(".uno:Cut"));
    }

    private void toastReadOnly() {
        if (barView == null) {
            return;
        }
        Toast.makeText(
                barView.getContext(),
                "当前文档为只读，无法删除或剪切",
                Toast.LENGTH_SHORT).show();
    }
}
