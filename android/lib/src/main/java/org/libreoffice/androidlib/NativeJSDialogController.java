package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.libreoffice.androidlib.ai.AiDialogHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes core JSDialog payloads to native Android dialogs and sends dialogevent back to LOKit.
 *
 * <p>Add new dialog handlers in the constructor {@link #registerDialogHandlers()}.
 */
public final class NativeJSDialogController {

    private static final String TAG = "NativeJSDialog";

    interface DialogHandler {
        boolean canHandle(JSONObject payload);

        void show(LOActivity host, JSONObject payload);
    }

    interface UpdatableDialogHandler extends DialogHandler {
        void update(JSONObject payload);

        boolean isActive();
    }

    private final LOActivity host;
    private final Map<String, DialogHandler> dialogHandlers = new HashMap<>();
    private final SpellingDialogHandler spellingDialogHandler;
    private final SimpleConfirmDialogHandler confirmHandler = new SimpleConfirmDialogHandler();
    private AlertDialog activeDialog;
    private int activeWindowId = -1;

    public NativeJSDialogController(LOActivity host) {
        this.host = host;
        spellingDialogHandler = new SpellingDialogHandler(this, host);
        registerDialogHandlers();
    }

    private void registerDialogHandlers() {
        dialogHandlers.put("DeleteContentsDialog", new DeleteContentsDialogHandler());
        dialogHandlers.put("SpellingDialog", spellingDialogHandler);
    }

    public boolean isActive() {
        return activeDialog != null && activeDialog.isShowing();
    }

    public void handlePayload(String json) {
        try {
            JSONObject payload = new JSONObject(json);
            String action = payload.optString("action", "");
            int windowId = payload.optInt("windowId", -1);
            if ("dismiss".equals(action)) {
                dismissIfWindow(windowId);
                return;
            }
            if ("update".equals(action)) {
                applyDialogUpdate(payload);
                return;
            }
            if (!"show".equals(action)) {
                return;
            }
            showDialog(payload);
        } catch (Exception e) {
            Log.e(TAG, "handlePayload_failed " + e.getMessage());
        }
    }

    public void dismissActive() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }
        activeDialog = null;
        activeWindowId = -1;
    }

    private void dismissIfWindow(int windowId) {
        if (windowId == -1 || windowId == activeWindowId) {
            dismissActive();
        }
    }

    private void applyDialogUpdate(JSONObject payload) {
        String dialogId = payload.optString("dialogId", "");
        DialogHandler handler = dialogHandlers.get(dialogId);
        if (handler instanceof UpdatableDialogHandler) {
            UpdatableDialogHandler updatable = (UpdatableDialogHandler) handler;
            if (updatable.isActive()) {
                updatable.update(payload);
            }
        }
    }

    private void showDialog(JSONObject payload) {
        String dialogType = payload.optString("dialogType", "");
        if ("messagebox".equals(dialogType) && shouldSuppressMessageboxDuringSpellCheck(payload)) {
            int windowId = payload.optInt("windowId", -1);
            sendResponse(windowId, "ok", 1);
            return;
        }

        dismissActive();
        String dialogId = payload.optString("dialogId", "");

        DialogHandler handler = dialogHandlers.get(dialogId);
        if (handler != null && handler.canHandle(payload)) {
            handler.show(host, payload);
            return;
        }
        if ("messagebox".equals(dialogType)) {
            confirmHandler.show(host, payload);
            return;
        }
        Log.w(TAG, "unhandled_native_dialog dialogId=" + dialogId + " type=" + dialogType);
    }

    void bindActiveDialog(AlertDialog dialog, int windowId) {
        activeDialog = dialog;
        activeWindowId = windowId;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) {
                activeDialog = null;
                activeWindowId = -1;
            }
        });
    }

    void applyFlexibleDialogSize(AlertDialog dialog, View root) {
        if (dialog == null || dialog.getWindow() == null || root == null) {
            return;
        }
        DisplayMetrics dm = host.getResources().getDisplayMetrics();
        int margin = host.dpToPx(48);
        int targetWidth = Math.min(host.dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, host.dpToPx(280));

        int maxHeight = Math.min(host.dpToPx(756), (int) (dm.heightPixels * 0.80f));
        maxHeight = Math.max(maxHeight, host.dpToPx(200));
        maxHeight = Math.min(maxHeight, dm.heightPixels - host.dpToPx(24));

        AiDialogHelper.applyFlexibleWidth(root, dialog, targetWidth, maxHeight);
    }

    void sendDialogEvent(int windowId, String controlId, String cmd, String data, String type) {
        if (windowId < 0) {
            return;
        }
        String js = "(function(){"
                + "try{"
                + "if(!window.app||!app.socket){return 'no_socket';}"
                + "var msg='dialogevent " + windowId + " '"
                + "+JSON.stringify({id:" + JSONObject.quote(controlId)
                + ",cmd:" + JSONObject.quote(cmd)
                + ",data:" + JSONObject.quote(data)
                + ",type:" + JSONObject.quote(type) + "});"
                + "app.socket.sendMessage(msg);"
                + "return 'ok';"
                + "}catch(e){return 'err:'+e;}"
                + "})()";
        host.evaluateJavascript(js, value -> Log.d(TAG, "dialogevent_sent id=" + controlId
                + " cmd=" + cmd + " result=" + value));
    }

    void sendResponse(int windowId, String buttonId, int responseCode) {
        sendDialogEvent(windowId, buttonId, "click", String.valueOf(responseCode), "responsebutton");
    }

    void sendCheckboxChange(int windowId, String controlId, boolean checked) {
        sendDialogEvent(windowId, controlId, "change", checked ? "true" : "false", "checkbox");
    }

    private static boolean optChecked(JSONObject payload, String controlId, boolean defaultValue) {
        JSONArray controls = payload.optJSONArray("controls");
        if (controls == null) {
            return defaultValue;
        }
        for (int i = 0; i < controls.length(); i++) {
            JSONObject item = controls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (controlId.equals(item.optString("id", ""))) {
                return item.optBoolean("checked", defaultValue);
            }
        }
        return defaultValue;
    }

    private static int optResponseCode(JSONObject payload, String buttonId, int fallback) {
        JSONArray responses = payload.optJSONArray("responses");
        if (responses == null) {
            return fallback;
        }
        for (int i = 0; i < responses.length(); i++) {
            JSONObject item = responses.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (buttonId.equals(item.optString("id", ""))) {
                return item.optInt("response", fallback);
            }
        }
        return fallback;
    }

    private static String resolveMessageboxTitle(String rawTitle, String message) {
        if (message != null && (message.contains("拼写检查") || message.contains("spellcheck"))) {
            return "拼写检查";
        }
        if (isGenericMessageboxTitle(rawTitle)) {
            return "提示";
        }
        if (rawTitle == null || rawTitle.isEmpty()) {
            return "提示";
        }
        return rawTitle;
    }

    private static String resolveMessageboxMessage(String rawTitle, String rawText, JSONArray controls) {
        if (rawText != null && !rawText.isEmpty() && !rawText.equals(rawTitle)
                && !isGenericMessageboxTitle(rawText)) {
            return rawText;
        }
        String fromControls = extractFixedTextFromControls(controls);
        if (!fromControls.isEmpty()) {
            return fromControls;
        }
        return rawText != null ? rawText : "";
    }

    private static boolean isGenericMessageboxTitle(String title) {
        if (title == null || title.isEmpty()) {
            return true;
        }
        return "信息".equals(title)
                || "Information".equals(title)
                || "Warning".equals(title)
                || "警告".equals(title)
                || "Error".equals(title)
                || "错误".equals(title);
    }

    private static String extractFixedTextFromControls(JSONArray controls) {
        if (controls == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < controls.length(); i++) {
            JSONObject item = controls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            if (!"fixedtext".equals(type) && !"label".equals(type)) {
                continue;
            }
            String text = item.optString("text", "");
            if (text.isEmpty()) {
                text = item.optString("label", "");
            }
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private boolean shouldSuppressMessageboxDuringSpellCheck(JSONObject payload) {
        if (!spellingDialogHandler.isSessionOpen()) {
            return false;
        }
        if (shouldAutoDismissSpellFinishMessagebox(payload)) {
            return true;
        }
        return isIncidentalSpellInfoMessagebox(payload);
    }

    private boolean shouldAutoDismissSpellFinishMessagebox(JSONObject payload) {
        String message = resolveMessageboxMessage(
                payload.optString("title", ""),
                payload.optString("text", ""),
                payload.optJSONArray("controls"));
        if (message.contains("已经完成") && message.contains("拼写检查")) {
            return true;
        }
        String lower = message.toLowerCase();
        return lower.contains("spellcheck") && lower.contains("completed");
    }

    /** Core often emits a blank "信息" OK box alongside SpellingDialog; swallow it. */
    private static boolean isIncidentalSpellInfoMessagebox(JSONObject payload) {
        if (!isOkOnlyMessagebox(payload)) {
            return false;
        }
        String title = payload.optString("title", "");
        String text = payload.optString("text", "");
        String message = resolveMessageboxMessage(title, text, payload.optJSONArray("controls"));
        return message.isEmpty()
                || message.equals(title)
                || isGenericMessageboxTitle(title)
                || isGenericMessageboxTitle(message);
    }

    private static boolean isOkOnlyMessagebox(JSONObject payload) {
        JSONArray responses = payload.optJSONArray("responses");
        if (responses != null && responses.length() > 1) {
            return false;
        }
        JSONArray controls = payload.optJSONArray("controls");
        if (controls == null || controls.length() == 0) {
            return true;
        }
        int buttons = 0;
        for (int i = 0; i < controls.length(); i++) {
            JSONObject item = controls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            if ("cancelbutton".equals(type) || "helpbutton".equals(type)) {
                return false;
            }
            if ("okbutton".equals(type) || "pushbutton".equals(type)) {
                buttons++;
            }
        }
        return buttons <= 1;
    }

    // -------------------------------------------------------------------------
    // Delete Contents (.uno:Delete)
    // -------------------------------------------------------------------------

    private final class DeleteContentsDialogHandler implements DialogHandler {
        private final String[] ITEM_IDS = {
                "text", "numbers", "datetime", "formulas", "comments", "formats", "objects"
        };

        @Override
        public boolean canHandle(JSONObject payload) {
            return "DeleteContentsDialog".equals(payload.optString("dialogId", ""));
        }

        @Override
        public void show(LOActivity activity, JSONObject payload) {
            int windowId = payload.optInt("windowId", -1);
            View root = LayoutInflater.from(activity).inflate(R.layout.lolib_dialog_delete_contents, null);

            TextView titleView = root.findViewById(R.id.ai_dialog_header_title);
            String title = payload.optString("title", "删除内容");
            if (title.isEmpty()) {
                title = "删除内容";
            }
            titleView.setText(title);

            root.findViewById(R.id.ai_dialog_header_close).setOnClickListener(v ->
                    onCancel(activity, payload, windowId));

            CheckBox deleteAll = root.findViewById(R.id.delete_contents_deleteall);
            Map<String, CheckBox> itemBoxes = new HashMap<>();
            itemBoxes.put("text", root.findViewById(R.id.delete_contents_text));
            itemBoxes.put("numbers", root.findViewById(R.id.delete_contents_numbers));
            itemBoxes.put("datetime", root.findViewById(R.id.delete_contents_datetime));
            itemBoxes.put("formulas", root.findViewById(R.id.delete_contents_formulas));
            itemBoxes.put("comments", root.findViewById(R.id.delete_contents_comments));
            itemBoxes.put("formats", root.findViewById(R.id.delete_contents_formats));
            itemBoxes.put("objects", root.findViewById(R.id.delete_contents_objects));

            deleteAll.setChecked(optChecked(payload, "deleteall", false));
            for (String id : ITEM_IDS) {
                CheckBox box = itemBoxes.get(id);
                if (box != null) {
                    box.setChecked(optChecked(payload, id, false));
                }
            }
            syncDeleteAllState(deleteAll, itemBoxes);

            deleteAll.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    for (String id : ITEM_IDS) {
                        CheckBox box = itemBoxes.get(id);
                        if (box != null) {
                            box.setChecked(true);
                            box.setEnabled(false);
                        }
                    }
                } else {
                    for (String id : ITEM_IDS) {
                        CheckBox box = itemBoxes.get(id);
                        if (box != null) {
                            box.setEnabled(true);
                        }
                    }
                }
            });

            root.findViewById(R.id.delete_contents_cancel_btn).setOnClickListener(v ->
                    onCancel(activity, payload, windowId));
            root.findViewById(R.id.delete_contents_ok_btn).setOnClickListener(v ->
                    onConfirm(activity, payload, windowId, deleteAll, itemBoxes));

            AlertDialog dialog = new AlertDialog.Builder(activity).create();
            dialog.setView(root);
            AiDialogHelper.applyCloseOnlyDismiss(dialog);
            AiDialogHelper.applyTransparentWindow(dialog);
            dialog.show();
            applyFlexibleDialogSize(dialog, root);
            bindActiveDialog(dialog, windowId);
            Log.i(TAG, "delete_contents_dialog_show windowId=" + windowId);
        }

        private void syncDeleteAllState(CheckBox deleteAll, Map<String, CheckBox> itemBoxes) {
            if (deleteAll.isChecked()) {
                for (String id : ITEM_IDS) {
                    CheckBox box = itemBoxes.get(id);
                    if (box != null) {
                        box.setChecked(true);
                        box.setEnabled(false);
                    }
                }
            }
        }

        private void onCancel(LOActivity activity, JSONObject payload, int windowId) {
            dismissActive();
            int response = optResponseCode(payload, "cancel", 2);
            sendResponse(windowId, "cancel", response);
            activity.closeMobileWizardFromNative("delete_contents_cancel");
        }

        private void onConfirm(LOActivity activity, JSONObject payload, int windowId,
                               CheckBox deleteAll, Map<String, CheckBox> itemBoxes) {
            dismissActive();
            sendCheckboxChange(windowId, "deleteall", deleteAll.isChecked());
            for (String id : ITEM_IDS) {
                CheckBox box = itemBoxes.get(id);
                if (box != null) {
                    sendCheckboxChange(windowId, id, box.isChecked());
                }
            }
            int response = optResponseCode(payload, "ok", 1);
            sendResponse(windowId, "ok", response);
            activity.closeMobileWizardFromNative("delete_contents_ok");
            Log.i(TAG, "delete_contents_dialog_ok windowId=" + windowId);
        }
    }

    // -------------------------------------------------------------------------
    // Generic messagebox (Yes/No/OK/Cancel)
    // -------------------------------------------------------------------------

    private final class SimpleConfirmDialogHandler implements DialogHandler {
        @Override
        public boolean canHandle(JSONObject payload) {
            return "messagebox".equals(payload.optString("dialogType", ""));
        }

        @Override
        public void show(LOActivity activity, JSONObject payload) {
            int windowId = payload.optInt("windowId", -1);
            View root = LayoutInflater.from(activity).inflate(R.layout.lolib_dialog_native_confirm, null);

            TextView titleView = root.findViewById(R.id.ai_dialog_header_title);
            String rawTitle = payload.optString("title", "");
            String rawText = payload.optString("text", "");
            JSONArray controls = payload.optJSONArray("controls");
            String message = resolveMessageboxMessage(rawTitle, rawText, controls);
            String title = resolveMessageboxTitle(rawTitle, message);
            titleView.setText(title);

            TextView messageView = root.findViewById(R.id.native_confirm_message);
            if (message.isEmpty() || message.equals(title)) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setVisibility(View.VISIBLE);
                messageView.setText(message);
            }

            root.findViewById(R.id.ai_dialog_header_close).setOnClickListener(v ->
                    dismissWithResponse(payload, windowId, "cancel", 2));

            LinearLayout buttonRow = root.findViewById(R.id.native_confirm_button_row);
            List<ButtonSpec> buttons = buildButtons(payload);
            if (buttons.isEmpty()) {
                buttons.add(new ButtonSpec("ok", "确定", 1, true));
            }
            for (int i = 0; i < buttons.size(); i++) {
                ButtonSpec spec = buttons.get(i);
                boolean primary = spec.primary;
                View button = buildActionButton(activity, spec.label, primary);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, host.dpToPx(52), 1f);
                if (i < buttons.size() - 1) {
                    lp.setMarginEnd(host.dpToPx(12));
                }
                button.setLayoutParams(lp);
                button.setOnClickListener(v -> dismissWithResponse(payload, windowId, spec.id, spec.response));
                buttonRow.addView(button);
            }

            AlertDialog dialog = new AlertDialog.Builder(activity).create();
            dialog.setView(root);
            AiDialogHelper.applyCloseOnlyDismiss(dialog);
            AiDialogHelper.applyTransparentWindow(dialog);
            dialog.show();
            applyFlexibleDialogSize(dialog, root);
            bindActiveDialog(dialog, windowId);
            Log.i(TAG, "confirm_dialog_show windowId=" + windowId
                    + " title=" + title);
        }

        private View buildActionButton(LOActivity activity, String label, boolean primary) {
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(android.view.Gravity.CENTER);
            container.setClickable(true);
            container.setFocusable(true);
            container.setBackgroundResource(primary
                    ? R.drawable.lolib_bg_gradient_button_outline
                    : R.drawable.lolib_bg_outline_secondary_button);

            TextView text = new TextView(activity);
            text.setText(label);
            text.setTextSize(17);
            text.setTextColor(primary ? 0xFFFFFFFF : 0xFF333333);
            if (primary) {
                text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
            }
            container.addView(text);
            return container;
        }

        private void dismissWithResponse(JSONObject payload, int windowId,
                                         String buttonId, int fallbackResponse) {
            dismissActive();
            int response = optResponseCode(payload, buttonId, fallbackResponse);
            sendResponse(windowId, buttonId, response);
            host.closeMobileWizardFromNative("native_confirm_" + buttonId);
        }

        private List<ButtonSpec> buildButtons(JSONObject payload) {
            List<ButtonSpec> result = new ArrayList<>();
            JSONArray responses = payload.optJSONArray("responses");
            if (responses == null) {
                return result;
            }
            for (int i = 0; i < responses.length(); i++) {
                JSONObject item = responses.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "ok");
                int code = item.optInt("response", 1);
                String label = labelForButton(id);
                boolean primary = "ok".equals(id) || "yes".equals(id);
                result.add(new ButtonSpec(id, label, code, primary));
            }
            return result;
        }

        private String labelForButton(String id) {
            switch (id) {
                case "ok":
                    return "确定";
                case "cancel":
                    return "取消";
                case "yes":
                    return "是";
                case "no":
                    return "否";
                default:
                    return id;
            }
        }
    }

    private static final class ButtonSpec {
        final String id;
        final String label;
        final int response;
        final boolean primary;

        ButtonSpec(String id, String label, int response, boolean primary) {
            this.id = id;
            this.label = label;
            this.response = response;
            this.primary = primary;
        }
    }
}
