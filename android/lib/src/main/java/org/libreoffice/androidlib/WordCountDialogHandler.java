package org.libreoffice.androidlib;

import org.json.JSONObject;

/**
 * Intercepts core {@code WordCountDialog} JSDialog and shows native bottom sheet (Figma 5194:55623).
 */
final class WordCountDialogHandler implements NativeJSDialogController.DialogHandler,
        NativeJSDialogController.UpdatableDialogHandler {

    private final NativeJSDialogController controller;
    private final LOActivity host;
    private final WordCountSheetController sheet;
    private int windowId = -1;
    private boolean sessionOpen;

    WordCountDialogHandler(NativeJSDialogController controller, LOActivity host) {
        this.controller = controller;
        this.host = host;
        this.sheet = new WordCountSheetController(new WordCountSheetController.Host() {
            @Override
            public android.content.Context getContext() {
                return host;
            }

            @Override
            public int dpToPx(int dp) {
                return host.dpToPx(dp);
            }

            @Override
            public int getBottomChromeHeightPx() {
                return host.getDocumentBottomChromeHeightPx();
            }
        });
    }

    @Override
    public boolean canHandle(JSONObject payload) {
        return "WordCountDialog".equals(payload.optString("dialogId", ""));
    }

    @Override
    public boolean isActive() {
        return sessionOpen || sheet.isShowing();
    }

    @Override
    public void show(LOActivity activity, JSONObject payload) {
        sessionOpen = true;
        windowId = payload.optInt("windowId", -1);
        sheet.show(payload, () -> {
            sessionOpen = false;
            if (windowId >= 0) {
                controller.sendResponse(windowId, "close", 7);
            }
            windowId = -1;
        });
    }

    @Override
    public void update(JSONObject payload) {
        if (!isActive()) {
            return;
        }
        sheet.update(payload);
    }

    void dismiss() {
        sheet.dismiss();
        sessionOpen = false;
        windowId = -1;
    }
}
