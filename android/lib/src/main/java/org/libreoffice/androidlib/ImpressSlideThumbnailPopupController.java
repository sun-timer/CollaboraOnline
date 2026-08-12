package org.libreoffice.androidlib;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.json.JSONObject;
import org.libreoffice.androidlib.ai.AiDialogHelper;

/**
 * Impress 缩略图长按/二次点击 — 原生菜单（复制 / 删除）。
 */
public final class ImpressSlideThumbnailPopupController {

    private static final String TAG = "ImpressSlidePopup";
    private static final float MARGIN_DP = 12f;
    private static final float GAP_ABOVE_ANCHOR_DP = 8f;

    public interface Host {
        android.content.Context getContext();

        View findViewById(int id);

        View getBrowserView();

        float dpToPx(float dp);

        void evaluateJavascript(String script);

        void ensureEditModeThen(Runnable action);
    }

    private final Host host;
    private View overlayView;
    private View popupView;
    private View copyRow;
    private View deleteRow;
    private AiDialogHelper.CompactPanelSession deleteConfirmSession;
    private int currentPartIndex = -1;
    private int currentSlideNumber = 1;
    private boolean currentCanCopy = true;
    private boolean currentCanDelete = false;
    private float lastAnchorX;
    private float lastAnchorY;
    private float lastAnchorBottom;

    public ImpressSlideThumbnailPopupController(Host host) {
        this.host = host;
    }

    public void setup() {
        overlayView = host.findViewById(R.id.impress_slide_thumbnail_popup_overlay);
        popupView = host.findViewById(R.id.impress_slide_thumbnail_popup_panel);
        if (overlayView == null || popupView == null) {
            return;
        }
        overlayView.setOnClickListener(v -> hide());
        copyRow = popupView.findViewById(R.id.impress_slide_thumbnail_popup_copy);
        deleteRow = popupView.findViewById(R.id.impress_slide_thumbnail_popup_delete);
        if (copyRow != null) {
            copyRow.setOnClickListener(v -> onCopyClicked());
        }
        if (deleteRow != null) {
            deleteRow.setOnClickListener(v -> onDeleteClicked());
        }
    }

    public boolean isVisible() {
        return (popupView != null && popupView.getVisibility() == View.VISIBLE)
                || (deleteConfirmSession != null && deleteConfirmSession.isShowing());
    }

    /** 横竖屏切换后重算缩略图菜单位置（删除确认由 CompactPanel 统一处理）。 */
    public void onConfigurationChanged() {
        if (popupView == null || popupView.getVisibility() != View.VISIBLE) {
            return;
        }
        popupView.post(() -> positionPopup(lastAnchorX, lastAnchorY, lastAnchorBottom));
    }

    public void hide() {
        hideDeleteConfirmDialog();
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        currentPartIndex = -1;
        currentSlideNumber = 1;
        currentCanCopy = true;
        currentCanDelete = false;
    }

    public void showFromJson(String json) {
        if (popupView == null) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(json);
            int partIndex = payload.optInt("partIndex", -1);
            int slideNumber = payload.optInt("slideNumber", partIndex + 1);
            boolean canCopy = payload.optBoolean("canCopy", true);
            boolean canDelete = payload.optBoolean("canDelete", false);
            float anchorX = (float) payload.optDouble("anchorX", 0);
            float anchorY = (float) payload.optDouble("anchorY", 0);
            float anchorBottom = (float) payload.optDouble("anchorBottom", anchorY);
            show(partIndex, slideNumber, canCopy, canDelete, anchorX, anchorY, anchorBottom);
        } catch (Exception e) {
            Log.w(TAG, "showFromJson failed: " + e.getMessage());
        }
    }

    public void show(int partIndex, int slideNumber, boolean canCopy, boolean canDelete,
                     float anchorX, float anchorY, float anchorBottom) {
        if (popupView == null || partIndex < 0) {
            return;
        }
        currentPartIndex = partIndex;
        currentSlideNumber = slideNumber > 0 ? slideNumber : (partIndex + 1);
        currentCanCopy = canCopy;
        currentCanDelete = canDelete;
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        lastAnchorBottom = anchorBottom > 0 ? anchorBottom : anchorY;

        if (copyRow != null) {
            copyRow.setEnabled(canCopy);
            copyRow.setAlpha(canCopy ? 1f : 0.4f);
        }
        if (deleteRow != null) {
            deleteRow.setEnabled(canDelete);
            deleteRow.setAlpha(canDelete ? 1f : 0.4f);
        }

        if (overlayView != null) {
            overlayView.setVisibility(View.VISIBLE);
        }
        popupView.setVisibility(View.VISIBLE);
        popupView.post(() -> positionPopup(anchorX, anchorY, lastAnchorBottom));
        Log.i(TAG, "show partIndex=" + partIndex + " slide=" + currentSlideNumber
                + " anchor=" + anchorX + "," + anchorY + "," + lastAnchorBottom);
    }

    private void positionPopup(float anchorX, float anchorY, float anchorBottom) {
        positionFloatingPanel(popupView, anchorX, anchorY, anchorBottom,
                Math.round(host.dpToPx(206f)));
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

        int widthSpec = fixedWidthPx > 0
                ? View.MeasureSpec.makeMeasureSpec(fixedWidthPx, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        panel.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float popupW = panel.getMeasuredWidth();
        float popupH = panel.getMeasuredHeight();

        float anchorXInParent = baseX + anchorXWeb;
        float anchorBottomInParent = baseY + anchorBottomWeb;
        float left = anchorXInParent - popupW * 0.5f;

        View bottomToolbar = host.findViewById(R.id.doc_bottom_toolbar);
        float top = anchorBottomInParent + gap;
        if (bottomToolbar != null && bottomToolbar.getTop() > 0) {
            float maxBottom = bottomToolbar.getTop() - gap;
            if (top + popupH > maxBottom) {
                top = anchorBottomInParent - popupH - gap;
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
    }

    private void onCopyClicked() {
        if (!currentCanCopy || currentPartIndex < 0) {
            return;
        }
        final int partIndex = currentPartIndex;
        hide();
        host.ensureEditModeThen(() -> host.evaluateJavascript(buildSelectAndDuplicateScript(partIndex)));
    }

    private void onDeleteClicked() {
        if (!currentCanDelete || currentPartIndex < 0) {
            Toast.makeText(host.getContext(), "至少需要保留一张幻灯片", Toast.LENGTH_SHORT).show();
            return;
        }
        final int partIndex = currentPartIndex;
        final int slideNumber = currentSlideNumber;
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        showDeleteConfirmDialog(partIndex, slideNumber);
    }

    private void showDeleteConfirmDialog(int partIndex, int slideNumber) {
        View panelView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.lolib_dialog_calc_sheet_delete_confirm, null, false);
        TextView titleView = panelView.findViewById(R.id.ai_dialog_header_title);
        TextView messageView = panelView.findViewById(R.id.calc_sheet_delete_message);
        View closeBtn = panelView.findViewById(R.id.ai_dialog_header_close);
        View cancelBtn = panelView.findViewById(R.id.calc_sheet_delete_cancel);
        View confirmBtn = panelView.findViewById(R.id.calc_sheet_delete_confirm);

        if (titleView != null) {
            titleView.setText("删除幻灯片");
        }
        if (messageView != null) {
            messageView.setText("确定要删除第 " + slideNumber + " 张幻灯片吗？");
        }

        hideDeleteConfirmDialog();
        deleteConfirmSession = AiDialogHelper.showCompactPanel(
                host.getContext(), panelView, TAG + ":delete");
        deleteConfirmSession.setOnDismissListener(() -> deleteConfirmSession = null);

        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> hideDeleteConfirmDialog());
        }
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> hideDeleteConfirmDialog());
        }
		if (confirmBtn != null) {
			confirmBtn.setBackgroundResource(R.drawable.lolib_bg_impress_primary_button_pill);
			confirmBtn.setOnClickListener(v -> {
                hideDeleteConfirmDialog();
                host.ensureEditModeThen(() -> host.evaluateJavascript(buildSelectAndDeleteScript(partIndex)));
            });
        }
    }

    private void hideDeleteConfirmDialog() {
        if (deleteConfirmSession != null) {
            deleteConfirmSession.dismiss();
            deleteConfirmSession = null;
        }
    }

    private static String buildSelectAndDuplicateScript(int partIndex) {
        return "(function(){try{"
                + "if(!app||!app.map){return;}"
                + "app.map.setPart(" + partIndex + ");"
                + "app.map.selectPart(" + partIndex + ",1,false);"
                + "app.map.duplicatePage();"
                + "}catch(e){console.log('duplicate slide failed',e);}})();";
    }

    private static String buildSelectAndDeleteScript(int partIndex) {
        return "(function(){try{"
                + "if(!app||!app.map){return;}"
                + "app.map.setPart(" + partIndex + ");"
                + "app.map.selectPart(" + partIndex + ",1,false);"
                + "app.map.deletePage();"
                + "}catch(e){console.log('delete slide failed',e);}})();";
    }
}
