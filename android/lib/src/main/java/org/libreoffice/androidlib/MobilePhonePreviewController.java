package org.libreoffice.androidlib;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * Simulates a portrait phone viewport over the document. Works when the device is in
 * portrait or landscape (tablet); the preview frame is always portrait-shaped.
 */
public class MobilePhonePreviewController {
    private static final String TAG = "MobilePhonePreview";
    /** iPhone 14 logical width / height — portrait phone aspect only. */
    private static final float PHONE_WIDTH = 390f;
    private static final float PHONE_HEIGHT = 844f;

    /**
     * Injected at runtime so preview works even when bundled device-mobile.css is stale.
     * Web layer handles dimming; native scrim stays transparent for click-to-dismiss only.
     */
    private static final String PREVIEW_STYLE_CSS =
            "body.mobile-phone-preview-active{overflow:hidden!important;}"
                    + "body.mobile-phone-preview-active::before{"
                    + "content:'';position:fixed;inset:0;background:rgba(0,0,0,.6);"
                    + "z-index:500;pointer-events:none;}"
                    + "body.mobile-phone-preview-active #main-document-content{"
                    + "position:fixed!important;left:var(--mpp-left)!important;top:var(--mpp-top)!important;"
                    + "width:var(--mpp-width)!important;height:var(--mpp-height)!important;"
                    + "max-width:var(--mpp-width)!important;max-height:var(--mpp-height)!important;"
                    + "border-radius:16px;overflow:hidden!important;z-index:600;"
                    + "box-shadow:0 12px 40px rgba(0,0,0,.35);background:#fff;"
                    + "transform:translateZ(0);isolation:isolate;flex:none!important;"
                    + "clip-path:inset(0 round 16px);"
                    + "contain:layout size style paint;}"
                    + "body.mobile-phone-preview-active #document-container,"
                    + "body.mobile-phone-preview-active #map,"
                    + "body.mobile-phone-preview-active #map .leaflet-container,"
                    + "body.mobile-phone-preview-active #map .leaflet-map-pane,"
                    + "body.mobile-phone-preview-active #map .leaflet-tile-pane{"
                    + "position:absolute!important;top:0!important;left:0!important;"
                    + "width:100%!important;height:100%!important;"
                    + "max-width:100%!important;max-height:100%!important;"
                    + "overflow:hidden!important;}"
                    + "body.mobile-phone-preview-active #document-container{"
                    + "right:0!important;bottom:0!important;}"
                    + "body.mobile-phone-preview-active #navigation-sidebar,"
                    + "body.mobile-phone-preview-active #sidebar-dock-wrapper,"
                    + "body.mobile-phone-preview-active nav.main-nav,"
                    + "body.mobile-phone-preview-active #toolbar-wrapper,"
                    + "body.mobile-phone-preview-active #toolbar-down,"
                    + "body.mobile-phone-preview-active #mobile-edit-button,"
                    + "body.mobile-phone-preview-active #spreadsheet-toolbar,"
                    + "body.mobile-phone-preview-active #presentation-controls-wrapper{"
                    + "display:none!important;visibility:hidden!important;pointer-events:none!important;}";

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
    private FrameLayout phoneFrame;
    private View scrim;
    private boolean showing;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::relayout;

    public MobilePhonePreviewController(Host host) {
        this.host = host;
        overlayRoot = host.findViewById(R.id.mobile_phone_preview_root);
        phoneFrame = host.findViewById(R.id.mobile_phone_preview_frame) instanceof FrameLayout
                ? (FrameLayout) host.findViewById(R.id.mobile_phone_preview_frame) : null;
        scrim = host.findViewById(R.id.mobile_phone_preview_scrim);
        View close = host.findViewById(R.id.mobile_phone_preview_close);
        if (close != null) {
            close.setOnClickListener(v -> hide());
        }
        if (scrim != null) {
            scrim.setOnClickListener(v -> hide());
        }
        if (phoneFrame != null) {
            phoneFrame.setClickable(false);
            phoneFrame.setFocusable(false);
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
        if (overlayRoot == null || phoneFrame == null) {
            return;
        }
        Runnable open = () -> {
            showing = true;
            host.setBottomToolbarVisible(false);
            if (scrim != null) {
                scrim.setBackgroundColor(Color.TRANSPARENT);
            }
            overlayRoot.setVisibility(View.VISIBLE);
            overlayRoot.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
            relayout();
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
        overlayRoot.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        overlayRoot.setVisibility(View.GONE);
        host.setBottomToolbarVisible(true);
        applyWebPreview(null);
        Log.i(TAG, "mobile_phone_preview_hide");
    }

    public void relayout() {
        if (!showing || phoneFrame == null) {
            return;
        }
        WebView webView = host.getWebView();
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            phoneFrame.post(this::relayout);
            return;
        }

        float density = webView.getResources().getDisplayMetrics().density;
        int webW = webView.getWidth();
        int webH = webView.getHeight();
        int pad = host.dpToPx(8);
        int maxW = webW - pad * 2;
        int maxH = webH - pad * 2;

        float aspect = PHONE_WIDTH / PHONE_HEIGHT;
        int frameH = maxH;
        int frameW = Math.round(frameH * aspect);
        if (frameW > maxW) {
            frameW = maxW;
            frameH = Math.round(frameW / aspect);
        }

        ViewGroup.LayoutParams lp = phoneFrame.getLayoutParams();
        if (lp != null) {
            lp.width = frameW;
            lp.height = frameH;
            phoneFrame.setLayoutParams(lp);
        }

        phoneFrame.post(() -> syncWebClip(webView, density));
    }

    private void syncWebClip(WebView webView, float density) {
        if (!showing || phoneFrame == null || webView == null) {
            return;
        }
        int[] webLoc = new int[2];
        int[] frameLoc = new int[2];
        webView.getLocationInWindow(webLoc);
        phoneFrame.getLocationInWindow(frameLoc);

        int leftPx = frameLoc[0] - webLoc[0];
        int topPx = frameLoc[1] - webLoc[1];
        int widthPx = phoneFrame.getWidth();
        int heightPx = phoneFrame.getHeight();

        float leftCss = leftPx / density;
        float topCss = topPx / density;
        float widthCss = widthPx / density;
        float heightCss = heightPx / density;

        Log.d(TAG, "syncWebClip cssRect=" + leftCss + "," + topCss + " "
                + widthCss + "x" + heightCss + " density=" + density);
        applyWebPreview(new float[] {leftCss, topCss, widthCss, heightCss});
    }

    private void applyWebPreview(float[] rectCss) {
        WebView webView = host.getWebView();
        if (webView == null) {
            return;
        }
        String script;
        if (rectCss == null) {
            script = "(function(){try{document.body.classList.remove('mobile-phone-preview-active');"
                    + "var s=document.documentElement.style;"
                    + "s.removeProperty('--mpp-left');s.removeProperty('--mpp-top');"
                    + "s.removeProperty('--mpp-width');s.removeProperty('--mpp-height');"
                    + "var st=document.getElementById('mobile-phone-preview-style');"
                    + "if(st){st.remove();}"
                    + "if(window.app&&app.map&&typeof app.map.invalidateSize==='function')"
                    + "{app.map.invalidateSize();}"
                    + "}catch(e){console.error('mobile-phone-preview hide',e);}"
                    + "return true;})();";
        } else {
            String cssEscaped = PREVIEW_STYLE_CSS.replace("\\", "\\\\").replace("'", "\\'");
            script = "(function(){try{document.body.classList.add('mobile-phone-preview-active');"
                    + "var s=document.documentElement.style;"
                    + "s.setProperty('--mpp-left','" + rectCss[0] + "px');"
                    + "s.setProperty('--mpp-top','" + rectCss[1] + "px');"
                    + "s.setProperty('--mpp-width','" + rectCss[2] + "px');"
                    + "s.setProperty('--mpp-height','" + rectCss[3] + "px');"
                    + "var st=document.getElementById('mobile-phone-preview-style');"
                    + "if(!st){st=document.createElement('style');st.id='mobile-phone-preview-style';"
                    + "document.head.appendChild(st);}"
                    + "st.textContent='" + cssEscaped + "';"
                    + "function mppRefresh(){try{if(window.app&&app.map"
                    + "&&typeof app.map.invalidateSize==='function'){app.map.invalidateSize(false);}"
                    + "if(window.app&&app.map&&app.map._docLayer){var dl=app.map._docLayer;"
                    + "if(typeof dl._resetClientVisArea==='function'){dl._resetClientVisArea();}"
                    + "if(typeof dl._requestNewTiles==='function'){dl._requestNewTiles();}"
                    + "}}catch(e){console.warn('mpp refresh',e);}}"
                    + "mppRefresh();setTimeout(mppRefresh,80);setTimeout(mppRefresh,280);"
                    + "}catch(e){console.error('mobile-phone-preview show',e);}"
                    + "return true;})();";
        }
        webView.evaluateJavascript(script, null);
    }
}
