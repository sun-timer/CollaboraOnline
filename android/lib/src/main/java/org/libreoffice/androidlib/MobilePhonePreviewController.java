package org.libreoffice.androidlib;

import android.util.Log;
import android.view.View;
import android.webkit.WebView;

/**
 * Tablet phone preview: centered portrait frame with real viewport resize.
 */
public class MobilePhonePreviewController {
    private static final String TAG = "MobilePhonePreview";
    private static final float PHONE_ASPECT = 390f / 844f;

    private static final String PREVIEW_STYLE_CSS =
            "body.mobile-phone-preview-active #main-document-content{"
                    + "position:fixed!important;border-radius:16px!important;"
                    + "overflow:hidden!important;z-index:10000!important;"
                    + "pointer-events:auto!important;background:#fff!important;"
                    + "box-shadow:0 0 0 9999px rgba(0,0,0,.55),0 0 0 3px #D0D4DC,0 8px 28px rgba(0,0,0,.25)!important;"
                    + "flex:none!important;touch-action:manipulation!important;}"
                    + "body.mobile-phone-preview-active #document-container,"
                    + "body.mobile-phone-preview-active [data-docType='spreadsheet'] #document-container{"
                    + "position:absolute!important;top:0!important;left:0!important;"
                    + "right:0!important;bottom:0!important;width:auto!important;height:auto!important;}"
                    + "body.mobile-phone-preview-active #map,"
                    + "body.mobile-phone-preview-active #map .leaflet-container,"
                    + "body.mobile-phone-preview-active #map .leaflet-map-pane,"
                    + "body.mobile-phone-preview-active #map .leaflet-tile-pane,"
                    + "body.mobile-phone-preview-active #map .leaflet-canvas-container{"
                    + "position:absolute!important;top:0!important;left:0!important;"
                    + "width:100%!important;height:100%!important;}"
                    + "body.mobile-phone-preview-active #navigation-sidebar,"
                    + "body.mobile-phone-preview-active #sidebar-dock-wrapper,"
                    + "body.mobile-phone-preview-active nav.main-nav,"
                    + "body.mobile-phone-preview-active #toolbar-wrapper,"
                    + "body.mobile-phone-preview-active #toolbar-down,"
                    + "body.mobile-phone-preview-active #mobile-edit-button,"
                    + "body.mobile-phone-preview-active #spreadsheet-toolbar,"
                    + "body.mobile-phone-preview-active #presentation-controls-wrapper{"
                    + "display:none!important;pointer-events:none!important;}";

    private static final String CSS_ESCAPED =
            PREVIEW_STYLE_CSS.replace("\\", "\\\\").replace("'", "\\'");

    /** Layout + guarded tile recover (Writer needs zoom to settle before tilecombine). */
    private static final String TILE_RECOVER_JS =
            "function mppHasValidMapSize(){"
                    + "var s=app.map&&typeof app.map.getSize==='function'?app.map.getSize():null;"
                    + "return!!(s&&s.x>0&&s.y>0);"
                    + "}"
                    + "function mppHasValidPixelBounds(){"
                    + "if(!app.map||typeof app.map.getPixelBounds!=='function'){return false;}"
                    + "var b=app.map.getPixelBounds();if(!b){return false;}"
                    + "var w=(b.max&&b.min)?(b.max.x-b.min.x):0;"
                    + "var h=(b.max&&b.min)?(b.max.y-b.min.y):0;"
                    + "return w>0&&h>0;"
                    + "}"
                    + "function mppApplyTileRecover(tag,deferCount){"
                    + "try{"
                    + "if(!(window.app&&app.map&&app.map._docLayer)){return;}"
                    + "if(!mppHasValidMapSize()||!mppHasValidPixelBounds()){"
                    + "if(deferCount<24){setTimeout(function(){mppApplyTileRecover(tag,deferCount+1);},100);}"
                    + "else if(app.console&&typeof app.console.warn==='function'){"
                    + "app.console.warn('mobile-phone-preview tile recover skipped invalid bounds tag='+tag);}"
                    + "return;}"
                    + "var dl=app.map._docLayer;"
                    + "if(typeof dl._resetClientVisArea==='function'){dl._resetClientVisArea();}"
                    + "if(typeof dl._sendClientZoom==='function'){dl._sendClientZoom(true);}"
                    + "if(typeof dl._requestNewTiles==='function'){dl._requestNewTiles();}"
                    + "var tm=window.TileManager||(typeof TileManager!=='undefined'?TileManager:null);"
                    + "if(tm&&typeof tm.update==='function'){tm.update();}"
                    + "if(app.console&&typeof app.console.debug==='function'){"
                    + "app.console.debug('mobile-phone-preview tile recover tag='+tag);}"
                    + "}catch(e){console.warn('mobile-phone-preview tile recover',tag,e);}"
                    + "}"
                    + "function mppScheduleTileRecover(tag){"
                    + "if(!(window.app&&app.map&&typeof app.map.invalidateSize==='function')){return;}"
                    + "app.map.invalidateSize(false);"
                    + "setTimeout(function(){mppApplyTileRecover(tag,0);},180);"
                    + "setTimeout(function(){mppApplyTileRecover(tag,0);},520);"
                    + "setTimeout(function(){mppApplyTileRecover(tag,0);},980);"
                    + "}";

    private static final String SHOW_SCRIPT =
            "(function(){try{"
                    + "var pad=8,aspect=" + PHONE_ASPECT + ";"
                    + "var iw=window.innerWidth||document.documentElement.clientWidth||800;"
                    + "var ih=window.innerHeight||document.documentElement.clientHeight||600;"
                    + "var maxW=Math.max(120,iw-pad*2),maxH=Math.max(160,ih-pad*2);"
                    + "var frameH=maxH,frameW=frameH*aspect;"
                    + "if(frameW>maxW){frameW=maxW;frameH=frameW/aspect;}"
                    + "var left=Math.max(0,(iw-frameW)/2),top=Math.max(0,(ih-frameH)/2);"
                    + "document.body.classList.add('mobile-phone-preview-active');"
                    + "var mc=document.getElementById('main-document-content');"
                    + "if(!mc){console.error('mobile-phone-preview: no #main-document-content');return false;}"
                    + "mc.style.setProperty('left',left+'px','important');"
                    + "mc.style.setProperty('top',top+'px','important');"
                    + "mc.style.setProperty('width',frameW+'px','important');"
                    + "mc.style.setProperty('height',frameH+'px','important');"
                    + "mc.style.setProperty('position','fixed','important');"
                    + "mc.style.setProperty('z-index','10000','important');"
                    + "var st=document.getElementById('mobile-phone-preview-style');"
                    + "if(!st){st=document.createElement('style');st.id='mobile-phone-preview-style';"
                    + "document.head.appendChild(st);}"
                    + "st.textContent='" + CSS_ESCAPED + "';"
                    + TILE_RECOVER_JS
                    + "console.log('mobile-phone-preview show left='+left+' top='+top"
                    + "+' frame='+frameW+'x'+frameH+' inner='+iw+'x'+ih);"
                    + "mppScheduleTileRecover('show');"
                    + "}catch(e){console.error('mobile-phone-preview show',e);}"
                    + "return true;})();";

    private static final String HIDE_SCRIPT =
            "(function(){try{"
                    + "document.body.classList.remove('mobile-phone-preview-active');"
                    + "var mc=document.getElementById('main-document-content');"
                    + "if(mc){mc.style.removeProperty('left');mc.style.removeProperty('top');"
                    + "mc.style.removeProperty('width');mc.style.removeProperty('height');"
                    + "mc.style.removeProperty('position');mc.style.removeProperty('z-index');}"
                    + "var st=document.getElementById('mobile-phone-preview-style');"
                    + "if(st){st.remove();}"
                    + TILE_RECOVER_JS
                    + "mppScheduleTileRecover('hide');"
                    + "}catch(e){console.error('mobile-phone-preview hide',e);}"
                    + "return true;})();";

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
        if (showing) {
            scheduleWebPreviewApply();
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
}
