package org.libreoffice.androidlib;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.json.JSONObject;

/**
 * Calc 行/列头长按 — 原生菜单，替代 MobileWizard。
 */
public final class CalcHeaderPopupController {

    private static final String TAG = "CalcHeaderPopup";
    private static final float MARGIN_DP = 12f;
    private static final float GAP_ABOVE_ANCHOR_DP = 8f;
    private static final float PANEL_WIDTH_DP = 206f;
    private static final float MAX_PANEL_HEIGHT_DP = 360f;

    public interface Host {
        Context getContext();

        View findViewById(int id);

        View getBrowserView();

        float dpToPx(float dp);

        void evaluateJavascript(String script);

        int getOverlayBottomReservedPx();
    }

    private static final class MenuAction {
        final String id;
        final String label;
        final int iconRes;

        MenuAction(String id, String label, int iconRes) {
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
        }
    }

    private static final MenuAction[] ROW_ACTIONS = {
            new MenuAction("insert_before", "在上方插入行",
                    R.drawable.lolib_ic_calc_sheet_insert_row_up),
            new MenuAction("insert_after", "在下方插入行",
                    R.drawable.lolib_ic_calc_sheet_insert_row_down),
            new MenuAction("delete", "删除行",
                    R.drawable.lolib_ic_calc_sheet_delete_row),
            new MenuAction("size", "行高",
                    R.drawable.lolib_ic_calc_row_chevron),
            new MenuAction("optimal", "最优行高",
                    R.drawable.lolib_ic_calc_row_chevron),
            new MenuAction("hide", "隐藏行",
                    R.drawable.lolib_ic_calc_hide_detail),
            new MenuAction("show", "显示行",
                    R.drawable.lolib_ic_calc_show_detail),
            new MenuAction("freeze", "冻结窗格",
                    R.drawable.lolib_ic_calc_sheet_freeze_panes),
    };

    private static final MenuAction[] COLUMN_ACTIONS = {
            new MenuAction("insert_before", "在左侧插入列",
                    R.drawable.lolib_ic_calc_sheet_insert_col),
            new MenuAction("insert_after", "在右侧插入列",
                    R.drawable.lolib_ic_calc_sheet_insert_col),
            new MenuAction("delete", "删除列",
                    R.drawable.lolib_ic_calc_sheet_delete_col),
            new MenuAction("size", "列宽",
                    R.drawable.lolib_ic_calc_sheet_insert_col),
            new MenuAction("optimal", "最优列宽",
                    R.drawable.lolib_ic_calc_sheet_insert_col),
            new MenuAction("hide", "隐藏列",
                    R.drawable.lolib_ic_calc_hide_detail),
            new MenuAction("show", "显示列",
                    R.drawable.lolib_ic_calc_show_detail),
            new MenuAction("freeze", "冻结窗格",
                    R.drawable.lolib_ic_calc_sheet_freeze_panes),
    };

    private final Host host;
    private View overlayView;
    private View popupView;
    private LinearLayout itemsContainer;
    private String currentType = "";
    private int currentIndex = -1;
    private float lastAnchorX;
    private float lastAnchorY;
    private float lastAnchorBottom;

    public CalcHeaderPopupController(Host host) {
        this.host = host;
    }

    public void setup() {
        overlayView = host.findViewById(R.id.calc_header_popup_overlay);
        popupView = host.findViewById(R.id.calc_header_popup_panel);
        itemsContainer = host.findViewById(R.id.calc_header_popup_items) instanceof LinearLayout
                ? (LinearLayout) host.findViewById(R.id.calc_header_popup_items) : null;
        if (overlayView == null || popupView == null || itemsContainer == null) {
            Log.w(TAG, "setup_incomplete overlay=" + (overlayView != null)
                    + " panel=" + (popupView != null)
                    + " items=" + (itemsContainer != null));
        }
        if (overlayView != null) {
            overlayView.setOnClickListener(v -> hide());
        }
    }

    public boolean isVisible() {
        return popupView != null && popupView.getVisibility() == View.VISIBLE;
    }

    public void onConfigurationChanged() {
        if (!isVisible() || popupView == null) {
            return;
        }
        popupView.post(() -> positionPopup(lastAnchorX, lastAnchorY, lastAnchorBottom));
    }

    public void hide() {
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        currentType = "";
        currentIndex = -1;
    }

    public void showFromJson(String json) {
        if (popupView == null) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(json);
            String type = payload.optString("type", "");
            int index = payload.optInt("index", -1);
            float anchorX = (float) payload.optDouble("anchorX", 0);
            float anchorY = (float) payload.optDouble("anchorY", 0);
            float anchorBottom = (float) payload.optDouble("anchorBottom", anchorY);
            show(type, index, anchorX, anchorY, anchorBottom);
        } catch (Exception e) {
            Log.w(TAG, "showFromJson failed: " + e.getMessage());
        }
    }

    public void show(String type, int index, float anchorX, float anchorY, float anchorBottom) {
        if (popupView == null || itemsContainer == null || index < 0) {
            Log.w(TAG, "show_aborted panel=" + (popupView != null)
                    + " items=" + (itemsContainer != null) + " index=" + index);
            return;
        }
        if (!"row".equals(type) && !"column".equals(type)) {
            return;
        }

        currentType = type;
        currentIndex = index;
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        lastAnchorBottom = anchorBottom > 0 ? anchorBottom : anchorY;
        populateItems("row".equals(type) ? ROW_ACTIONS : COLUMN_ACTIONS);

        if (overlayView != null) {
            overlayView.setVisibility(View.VISIBLE);
            overlayView.bringToFront();
        }
        popupView.setVisibility(View.VISIBLE);
        popupView.bringToFront();
        ViewGroup parent = (ViewGroup) popupView.getParent();
        if (parent != null) {
            parent.bringChildToFront(overlayView);
            parent.bringChildToFront(popupView);
        }
        popupView.post(() -> positionPopup(anchorX, anchorY, anchorBottom));
        Log.i(TAG, "show type=" + type + " index=" + index
                + " anchor=" + anchorX + "," + anchorY + "," + anchorBottom);
    }

    private void populateItems(MenuAction[] actions) {
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < actions.length; i++) {
            MenuAction action = actions[i];
            View row = inflater.inflate(R.layout.lolib_calc_header_popup_item, itemsContainer, false);
            ImageView iconView = row.findViewById(R.id.calc_header_popup_item_icon);
            TextView labelView = row.findViewById(R.id.calc_header_popup_item_label);
            if (iconView != null) {
                iconView.setImageResource(action.iconRes);
            }
            if (labelView != null) {
                labelView.setText(action.label);
            }
            row.setOnClickListener(v -> onActionClicked(action.id));
            itemsContainer.addView(row);
            if (i < actions.length - 1) {
                View divider = new View(host.getContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(host.dpToPx(1f))));
                divider.setBackgroundColor(0x14000000);
                itemsContainer.addView(divider);
            }
        }
    }

    private void onActionClicked(String actionId) {
        String type = currentType;
        int index = currentIndex;
        hide();
        if (index < 0 || actionId == null || actionId.isEmpty()) {
            return;
        }
        host.evaluateJavascript(buildActionScript(type, index, actionId));
        Log.i(TAG, "action type=" + type + " index=" + index + " action=" + actionId);
    }

    private static String buildActionScript(String type, int index, String actionId) {
        boolean isRow = "row".equals(type);
        String sectionName = isRow ? "app.CSections.RowHeader.name"
                : "app.CSections.ColumnHeader.name";
        StringBuilder body = new StringBuilder();
        body.append("(function(){");
        body.append("var sec=app.sectionContainer&&app.sectionContainer.getSectionWithName(")
                .append(sectionName).append(");");
        body.append("if(!sec)return false;");
        switch (actionId) {
            case "insert_before":
                body.append(isRow ? "sec.insertRowAbove(" : "sec.insertColumnBefore(")
                        .append(index).append(");");
                break;
            case "insert_after":
                body.append(isRow ? "sec.insertRowBelow(" : "sec.insertColumnAfter(")
                        .append(index).append(");");
                break;
            case "delete":
                body.append(isRow ? "sec.deleteRow(" : "sec.deleteColumn(")
                        .append(index).append(");");
                break;
            case "size":
                body.append("sec._select").append(isRow ? "Row" : "Column")
                        .append("(").append(index).append(",0);");
                body.append("sec._map.sendUnoCommand('")
                        .append(isRow ? ".uno:RowHeight" : ".uno:ColumnWidth")
                        .append("');");
                break;
            case "optimal":
                body.append(isRow ? "sec.optimalHeight(" : "sec.optimalWidth(")
                        .append(index).append(");");
                break;
            case "hide":
                body.append(isRow ? "sec.hideRow(" : "sec.hideColumn(")
                        .append(index).append(");");
                break;
            case "show":
                body.append(isRow ? "sec.showRow(" : "sec.showColumn(")
                        .append(index).append(");");
                break;
            case "freeze":
                body.append("sec._select").append(isRow ? "Row" : "Column")
                        .append("(").append(index).append(",0);");
                body.append("sec._map.sendUnoCommand('.uno:FreezePanes');");
                break;
            default:
                body.append("return false;");
                break;
        }
        body.append("return true;})();");
        return body.toString();
    }

    private void positionPopup(float anchorX, float anchorY, float anchorBottom) {
        positionFloatingPanel(popupView, anchorX, anchorY, anchorBottom,
                Math.round(host.dpToPx(PANEL_WIDTH_DP)));
    }

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
        float maxHeight = host.dpToPx(MAX_PANEL_HEIGHT_DP);

        int widthSpec = fixedWidthPx > 0
                ? View.MeasureSpec.makeMeasureSpec(fixedWidthPx, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        panel.measure(
                widthSpec,
                View.MeasureSpec.makeMeasureSpec(Math.round(maxHeight), View.MeasureSpec.AT_MOST));
        float popupW = panel.getMeasuredWidth();
        float popupH = panel.getMeasuredHeight();

        float anchorXInParent = baseX + anchorXWeb;
        float anchorTopInParent = baseY + anchorYWeb;
        float anchorBottomInParent = baseY + anchorBottomWeb;
        float left = anchorXInParent - popupW * 0.5f;

        float top = anchorBottomInParent + gap;
        if (top + popupH > parent.getHeight() - margin) {
            top = anchorTopInParent - popupH - gap;
        }

        float maxLeft = parent.getWidth() - popupW - margin;
        if (left < margin) {
            left = margin;
        }
        if (left > maxLeft) {
            left = maxLeft;
        }
        View bottomToolbar = host.findViewById(R.id.doc_bottom_toolbar);
        int bottomReserved = DocumentOverlayInsets.resolveBottomReservedPx(
                parent, bottomToolbar, host.getOverlayBottomReservedPx());
        top = DocumentOverlayInsets.clampTopInParent(
                top, popupH, parent.getHeight(), margin, bottomReserved);

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
    }
}
