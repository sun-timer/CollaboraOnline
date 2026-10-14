package org.libreoffice.androidlib;

import android.util.Log;
import android.view.View;
import android.webkit.WebView;

/**
 * Phone preview overlay. Layout and spreadsheet fit live in window.MobilePhonePreview.
 */
public class MobilePhonePreviewController {
    private static final String TAG = "MobilePhonePreview";

    private static final String SHOW_SCRIPT =
            "(function(){if(!window.MobilePhonePreview||typeof window.MobilePhonePreview.show!=='function'){return 'missing';}window.MobilePhonePreview.show();return 'shown';})()";

    private static final String HIDE_SCRIPT =
            "(function(){if(!window.MobilePhonePreview||typeof window.MobilePhonePreview.hide!=='function'){return 'missing';}window.MobilePhonePreview.hide();return 'hidden';})()";

    private static final String RELAYOUT_SCRIPT =
            "(function(){if(!window.MobilePhonePreview||typeof window.MobilePhonePreview.relayout!=='function'){return 'missing';}window.MobilePhonePreview.relayout();return 'relayout';})()";

    public interface Host {
        View findViewById(int id);

        WebView getWebView();

        int dpToPx(int dp);

        void runOnUiThread(Runnable runnable);

        boolean isEditModeActive();

        void switchToViewingMode();

        void setBottomToolbarVisible(boolean visible);
    }

    private final Host host;
    private View overlayRoot;
    private boolean showing;

    public MobilePhonePreviewController(Host host) {
        this.host = host;
        overlayRoot = host.findViewById(R.id.mobile_phone_preview_root);
        View close = host.findViewById(R.id.mobile_phone_preview_close);
        if (close != null) {
            close.setOnClickListener(v -> hide());
        }
    }

    public boolean isShowing() {
        return showing;
    }

    public void toggle() {
        if (showing) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        if (overlayRoot == null) {
            Log.w(TAG, "mobile_phone_preview_show skipped: no overlay");
            return;
        }
        Runnable open = () -> {
            showing = true;
            host.setBottomToolbarVisible(false);
            overlayRoot.setVisibility(View.VISIBLE);
            scheduleWebPreviewApply();
            Log.i(TAG, "mobile_phone_preview_show");
        };
        if (host.isEditModeActive()) {
            host.switchToViewingMode();
            overlayRoot.postDelayed(open, 350);
        } else {
            open.run();
        }
    }

    public void hide() {
        if (!showing || overlayRoot == null) {
            return;
        }
        showing = false;
        overlayRoot.setVisibility(View.GONE);
        host.setBottomToolbarVisible(true);
        applyWebPreview(false);
        Log.i(TAG, "mobile_phone_preview_hide");
    }

    public void relayout() {
        if (!showing) {
            return;
        }
        applyWebRelayout();
        if (overlayRoot != null) {
            overlayRoot.postDelayed(() -> {
                if (showing) {
                    applyWebRelayout();
                }
            }, 450);
        }
    }

    /** Re-apply after bottom toolbar collapse; tile recover is debounced inside JS. */
    private void scheduleWebPreviewApply() {
        applyWebPreview(true);
        if (overlayRoot != null) {
            overlayRoot.postDelayed(() -> applyWebPreview(true), 450);
        }
    }

    private void applyWebPreview(boolean show) {
        WebView webView = host.getWebView();
        if (webView == null) {
            return;
        }
        String script = show ? SHOW_SCRIPT : HIDE_SCRIPT;
        webView.evaluateJavascript(script, value ->
                Log.d(TAG, "applyWebPreview show=" + show + " value=" + value));
    }

    private void applyWebRelayout() {
        WebView webView = host.getWebView();
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(RELAYOUT_SCRIPT, value ->
                Log.d(TAG, "applyWebRelayout value=" + value));
    }
}
