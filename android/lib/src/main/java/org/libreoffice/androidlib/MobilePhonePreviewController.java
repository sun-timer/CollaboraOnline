package org.libreoffice.androidlib;

import android.util.Log;
import android.webkit.WebView;

/** One-shot page fit in the existing document view; keeps editor chrome and mode. */
public class MobilePhonePreviewController {
    private static final String TAG = "MobilePhonePreview";
    private static final String FIT_PAGE_SCRIPT =
            "(function(){try{"
                    + "if(!(window.app&&app.map&&app.map._docLayer)){return 'not-ready';}"
                    + "var map=app.map,dl=map._docLayer;"
                    + "map.invalidateSize(false);"
                    + "var size=map.getSize(),file=app.activeDocument&&app.activeDocument.fileSize;"
                    + "if(!size||size.x<=16||size.y<=16||!file||file.x<=0||file.y<=0){return 'not-ready';}"
                    + "var rect=[0,0,file.x,file.y],page=-1;"
                    + "if(dl.isWriter()){"
                    + " var pages=app.file.writer.pageRectangleList;"
                    + " page=Math.max(0,Math.min(pages.length-1,dl._currentPage||0));"
                    + " if(!pages.length){return 'not-ready';}"
                    + " rect=pages[page];"
                    + "}"
                    + "if(!rect||rect[2]<=0||rect[3]<=0){return 'not-ready';}"
                    + "var pixels=dl._twipsToPixels({x:rect[2],y:rect[3]});"
                    + "if(!pixels||pixels.x<=0||pixels.y<=0){return 'not-ready';}"
                    + "var ratio=Math.min((size.x-16)/pixels.x,(size.y-16)/pixels.y);"
                    + "var oldZoom=map.getZoom();"
                    + "var zoom=Math.floor(map.getScaleZoom(ratio,oldZoom)+0.000001);"
                    + "zoom=Math.max(map.getMinZoom(),Math.min(map.getMaxZoom(),zoom));"
                    + "map.setZoom(zoom,{animate:false},false);"
                    + "var center=dl._twipsToLatLng({x:rect[0]+rect[2]/2,y:rect[1]+rect[3]/2});"
                    + "map.setView(center,map.getZoom());"
                    + "if(typeof dl._resetClientVisArea==='function'){dl._resetClientVisArea();}"
                    + "if(typeof dl._sendClientZoom==='function'){dl._sendClientZoom(true);}"
                    + "if(typeof dl._requestNewTiles==='function'){dl._requestNewTiles();}"
                    + "if(window.TileManager&&typeof window.TileManager.update==='function'){window.TileManager.update();}"
                    + "var result='page_fit page='+page+' viewport='+size.x+'x'+size.y+' zoom='+oldZoom+'->'+map.getZoom();"
                    + "console.log(result);return result;"
                    + "}catch(e){console.error('page_fit failed',e);return 'failed';}})();";

    public interface Host {
        WebView getWebView();
    }

    private final Host host;
    private int fitRequestId;

    public MobilePhonePreviewController(Host host) {
        this.host = host;
    }

    public void fitToPage() {
        WebView webView = host.getWebView();
        if (webView != null) {
            int requestId = ++fitRequestId;
            webView.post(() -> applyPageFit(webView, requestId, 0));
        }
    }

    private void applyPageFit(WebView webView, int requestId, int attempt) {
        if (requestId != fitRequestId) {
            return;
        }
        webView.evaluateJavascript(FIT_PAGE_SCRIPT, value -> {
            Log.i(TAG, "page_fit result=" + value);
            if ("\"not-ready\"".equals(value) && attempt < 4) {
                webView.postDelayed(() -> applyPageFit(webView, requestId, attempt + 1), 120);
            }
        });
    }
}
