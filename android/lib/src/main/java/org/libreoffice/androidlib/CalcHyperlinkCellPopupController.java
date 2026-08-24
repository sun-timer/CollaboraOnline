package org.libreoffice.androidlib;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Calc 超链接单元格 — 原生悬浮条（替代 Web URLPopUpSection）。
 */
public final class CalcHyperlinkCellPopupController {

    private static final String TAG = "CalcHyperlinkPopup";
    private static final float MARGIN_DP = 12f;
    private static final float GAP_ABOVE_CELL_DP = 8f;

    public interface Host {
        Context getContext();

        View findViewById(int id);

        View getBrowserView();

        float dpToPx(float dp);

        void executeUnoCommand(String command);

        void showExternalLinkConfirm(String url);

        int getOverlayBottomReservedPx();
    }

    private final Host host;
    private View overlayView;
    private View popupView;
    private TextView linkView;
    private String currentUrl = "";
    private float lastAnchorX;
    private float lastAnchorY;

    public CalcHyperlinkCellPopupController(Host host) {
        this.host = host;
    }

    public void setup() {
        overlayView = host.findViewById(R.id.hyperlink_cell_popup_overlay);
        popupView = host.findViewById(R.id.hyperlink_cell_popup_panel);
        if (overlayView == null || popupView == null) {
            return;
        }
        overlayView.setOnClickListener(v -> hide());
        linkView = popupView.findViewById(R.id.hyperlink_cell_popup_link);
        ImageButton copyBtn = popupView.findViewById(R.id.hyperlink_cell_popup_copy);
        ImageButton removeBtn = popupView.findViewById(R.id.hyperlink_cell_popup_remove);
        if (linkView != null) {
            linkView.setOnClickListener(v -> onLinkClicked());
        }
        if (copyBtn != null) {
            copyBtn.setOnClickListener(v -> copyLink());
        }
        if (removeBtn != null) {
            removeBtn.setOnClickListener(v -> removeLink());
        }
    }

    public boolean isVisible() {
        return popupView != null && popupView.getVisibility() == View.VISIBLE;
    }

    public void onConfigurationChanged() {
        if (!isVisible() || popupView == null) {
            return;
        }
        popupView.post(() -> positionPopup(lastAnchorX, lastAnchorY));
    }

    public void hide() {
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (popupView != null) {
            popupView.setVisibility(View.GONE);
        }
        currentUrl = "";
    }

    public void show(String url, String displayText, float anchorX, float anchorY) {
        if (popupView == null || linkView == null) {
            return;
        }
        currentUrl = url != null ? url : "";
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        String label = displayText != null && !displayText.isEmpty() ? displayText : currentUrl;
        linkView.setText(label);

        if (overlayView != null) {
            overlayView.setVisibility(View.VISIBLE);
        }
        popupView.setVisibility(View.VISIBLE);
        popupView.post(() -> positionPopup(anchorX, anchorY));
        Log.i(TAG, "show url=" + currentUrl + " anchor=" + anchorX + "," + anchorY);
    }

    private void positionPopup(float anchorX, float anchorY) {
        View browser = host.getBrowserView();
        if (!(popupView.getLayoutParams() instanceof ConstraintLayout.LayoutParams) || browser == null) {
            return;
        }
        int[] browserLoc = new int[2];
        browser.getLocationInWindow(browserLoc);
        float margin = host.dpToPx(MARGIN_DP);
        float gap = host.dpToPx(GAP_ABOVE_CELL_DP);

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float popupW = popupView.getMeasuredWidth();
        float popupH = popupView.getMeasuredHeight();

        float left = browserLoc[0] + anchorX - popupW * 0.5f;
        float top = browserLoc[1] + anchorY - popupH - gap;
        float maxLeft = browserLoc[0] + browser.getWidth() - popupW - margin;
        if (left < browserLoc[0] + margin) {
            left = browserLoc[0] + margin;
        }
        if (left > maxLeft) {
            left = maxLeft;
        }
        if (top < browserLoc[1] + margin) {
            top = browserLoc[1] + anchorY + gap;
        }
        View bottomToolbar = host.findViewById(R.id.doc_bottom_toolbar);
        top = DocumentOverlayInsets.clampTopInWindow(
                top, popupH, bottomToolbar, gap, browserLoc[1] + margin);

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) popupView.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.rightToRight = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = Math.round(left);
        lp.topMargin = Math.round(top);
        popupView.setLayoutParams(lp);
    }

    private void onLinkClicked() {
        if (currentUrl.isEmpty()) {
            return;
        }
        if (currentUrl.startsWith("#")) {
            String bookmark = currentUrl.substring(1);
            host.executeUnoCommand(".uno:JumpToMark?Bookmark:string="
                    + android.net.Uri.encode(bookmark));
            hide();
            return;
        }
        host.showExternalLinkConfirm(currentUrl);
    }

    private void copyLink() {
        if (currentUrl.isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) host.getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("hyperlink", currentUrl));
            Toast.makeText(host.getContext(), "链接已复制", Toast.LENGTH_SHORT).show();
        }
        hide();
    }

    private void removeLink() {
        host.executeUnoCommand(".uno:RemoveHyperlink");
        hide();
        Log.i(TAG, "remove_hyperlink");
    }
}
