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
    private final WordCountDialogHandler wordCountDialogHandler;
    private final SimpleConfirmDialogHandler confirmHandler = new SimpleConfirmDialogHandler();
    private final MacroSelectorDialogHandler macroSelectorDialogHandler;
    private AlertDialog activeDialog;
    private int activeWindowId = -1;
    private CalcValidationMacroCatalog.Callback macroHarvestCallback;

    public NativeJSDialogController(LOActivity host) {
        this.host = host;
        spellingDialogHandler = new SpellingDialogHandler(this, host);
        wordCountDialogHandler = new WordCountDialogHandler(this, host);
        macroSelectorDialogHandler = new MacroSelectorDialogHandler();
        registerDialogHandlers();
    }

    private void registerDialogHandlers() {
        dialogHandlers.put("DeleteContentsDialog", new DeleteContentsDialogHandler());
        dialogHandlers.put("SpellingDialog", spellingDialogHandler);
        dialogHandlers.put("WordCountDialog", wordCountDialogHandler);
        dialogHandlers.put("ValidationDialog", new ValidationDialogHandler());
        dialogHandlers.put("MacroSelectorDialog", macroSelectorDialogHandler);
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
        if (wordCountDialogHandler.isActive()) {
            wordCountDialogHandler.dismiss();
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
        int targetWidth = AiDialogHelper.computeTargetWidthPx(host.getResources());
        int maxHeight = AiDialogHelper.computeMaxHeightHugPx(host.getResources());
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

    /** 写数据有效性：把 state 各控件值经 dialogevent 写入 core，最后点 ok。 */
    void applyValidationState(int windowId, CalcDataValidationState state) {
        if (state == null || windowId < 0) {
            return;
        }
        Log.i(TAG, "validation_apply windowId=" + windowId
                + " allow=" + state.allowIndex + " data=" + state.dataIndex);
        selectList(windowId, "allow", state.allowIndex);
        if (CalcValidationCatalog.needsDataOperator(state.allowIndex)) {
            selectList(windowId, "data", state.dataIndex);
        }
        if (CalcValidationCatalog.isListAllow(state.allowIndex)) {
            modifyEntry(windowId, "minlist", state.listEntries);
            check(windowId, "allowempty", state.allowEmpty);
            check(windowId, "showlist", state.showDropdownList);
            check(windowId, "sortascend", state.sortAscending);
            check(windowId, "casesens", state.caseSensitive);
        } else if (CalcValidationCatalog.isRangeAllow(state.allowIndex)) {
            modifyEntry(windowId, "min", state.minValue);
            check(windowId, "allowempty", state.allowEmpty);
            check(windowId, "showlist", state.showDropdownList);
        } else if (CalcValidationCatalog.isCustomAllow(state.allowIndex)) {
            modifyEntry(windowId, "min", state.minValue);
            check(windowId, "allowempty", state.allowEmpty);
        } else if (state.allowIndex != 0) {
            modifyEntry(windowId, "min", state.minValue);
            if (CalcValidationCatalog.needsBetweenValues(state.dataIndex)) {
                modifyEntry(windowId, "max", state.maxValue);
            }
            check(windowId, "allowempty", state.allowEmpty);
        }

        selectTab(windowId, 1);
        check(windowId, "tsbhelp", state.showInputHelp);
        modifyEntry(windowId, "title", state.inputHelpTitle);
        modifyEntry(windowId, "inputhelp_text", state.inputHelpText);

        selectTab(windowId, 2);
        check(windowId, "tsbshow", state.showErrorAlert);
        selectList(windowId, "actionCB", state.errorActionIndex);
        if (state.errorActionIndex == 3) {
            modifyEntry(windowId, "erroralert_title", state.macroUrl);
        } else if (state.errorActionIndex != 4) {
            modifyEntry(windowId, "erroralert_title", state.errorTitle);
            modifyEntry(windowId, "errorMsg", state.errorMessage);
        }

        sendResponse(windowId, "ok", 1);
        Log.i(TAG, "validation_apply_sent windowId=" + windowId);
    }

    /** 读数据有效性：解析拦截 payload 的控件值回填 target。 */
    boolean parseValidationPayload(JSONObject payload, CalcDataValidationState target) {
        if (payload == null || target == null) {
            return false;
        }
        JSONArray controls = payload.optJSONArray("controls");
        if (controls == null) {
            return false;
        }
        try {
            target.allowIndex = optListIndex(controls, "allow", 0);
            target.dataIndex = optListIndex(controls, "data", 0);
            target.errorActionIndex = optListIndex(controls, "actionCB", 0);
            target.minValue = optEntryText(controls, "min");
            target.maxValue = optEntryText(controls, "max");
            target.listEntries = optEntryText(controls, "minlist");
            target.inputHelpTitle = optEntryText(controls, "title");
            target.inputHelpText = optEntryText(controls, "inputhelp_text");
            target.errorTitle = optEntryText(controls, "erroralert_title");
            if (target.errorActionIndex == 3) {
                target.macroUrl = target.errorTitle;
            }
            target.errorMessage = optEntryText(controls, "errorMsg");
            target.allowEmpty = optCheck(controls, "allowempty", true);
            target.showDropdownList = optCheck(controls, "showlist", true);
            target.sortAscending = optCheck(controls, "sortascend", true);
            target.caseSensitive = optCheck(controls, "casesens", false);
            target.showInputHelp = optCheck(controls, "tsbhelp", false);
            target.showErrorAlert = optCheck(controls, "tsbshow", true);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "validation_parse_failed", e);
            return false;
        }
    }

    /** combobox 选中：core executor 认 {type:"combobox", cmd:"selected", data:"pos;text"}。 */
    private void selectList(int windowId, String controlId, int index) {
        String text = comboTextFor(controlId, index);
        sendDialogEvent(windowId, controlId, "selected", index + ";" + text, "combobox");
    }

    private static String comboTextFor(String controlId, int index) {
        if ("allow".equals(controlId)) {
            return CalcValidationCatalog.findAllowByIndex(index).label;
        }
        if ("data".equals(controlId)) {
            return CalcValidationCatalog.findDataByIndex(index).label;
        }
        if ("actionCB".equals(controlId)) {
            return CalcValidationCatalog.findErrorActionByIndex(index).label;
        }
        return "";
    }

    private void selectTab(int windowId, int index) {
        sendDialogEvent(windowId, "tabcontrol", "selecttab", String.valueOf(index), "tabcontrol");
    }

    /** edit 写入：core executor 认 {type:"edit", cmd:"change"}。 */
    private void modifyEntry(int windowId, String controlId, String value) {
        sendDialogEvent(windowId, controlId, "change",
                value == null ? "" : value, "edit");
    }

    /** checkbox：core executor 认 {type:"checkbox", cmd:"change", data:"true"/"false"}。 */
    private void check(int windowId, String controlId, boolean on) {
        sendDialogEvent(windowId, controlId, "change", on ? "true" : "false", "checkbox");
    }

    private static int optListIndex(JSONArray controls, String controlId, int fallback) {
        JSONObject control = findControl(controls, controlId);
        if (control == null) {
            return fallback;
        }
        // listbox 控件：selectedEntries 数组首项为选中 index；无则回退
        JSONArray selected = control.optJSONArray("selectedEntries");
        if (selected != null && selected.length() > 0) {
            try {
                return Integer.parseInt(String.valueOf(selected.opt(0)).trim());
            } catch (Exception ignored) {
            }
        }
        JSONArray entries = control.optJSONArray("entries");
        if (entries != null && selected != null && selected.length() > 0) {
            String selectedText = String.valueOf(selected.opt(0));
            for (int i = 0; i < entries.length(); i++) {
                if (selectedText.equals(String.valueOf(entries.opt(i)))) {
                    return i;
                }
            }
        }
        return fallback;
    }

    private static String optEntryText(JSONArray controls, String controlId) {
        JSONObject control = findControl(controls, controlId);
        if (control == null) {
            return "";
        }
        String text = control.optString("text", "");
        if (text.isEmpty()) {
            text = control.optString("value", "");
        }
        return text;
    }

    private static boolean optCheck(JSONArray controls, String controlId, boolean fallback) {
        JSONObject control = findControl(controls, controlId);
        if (control == null) {
            return fallback;
        }
        if (control.has("checked")) {
            return control.optBoolean("checked", fallback);
        }
        String active = control.optString("active", "");
        if (!active.isEmpty()) {
            return "true".equalsIgnoreCase(active) || "1".equals(active);
        }
        return fallback;
    }

    private static JSONObject findControl(JSONArray controls, String controlId) {
        if (controls == null) {
            return null;
        }
        for (int i = 0; i < controls.length(); i++) {
            JSONObject item = controls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (controlId.equals(item.optString("id", ""))) {
                return item;
            }
        }
        return null;
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

    /** 标题是否是应用名（Collabora Office Calc 等），此时正文错误信息在 controls/text。 */
    private static boolean isAppNameTitle(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        String t = title.trim();
        return t.contains("Collabora") || t.contains("LibreOffice")
                || t.contains("Office Calc") || t.contains("Office Writer")
                || t.contains("Office Impress");
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
            // 只取正文控件：fixedtext/label/multilineedit；排除按钮（pushbutton/okbutton 等）
            if (!"fixedtext".equals(type) && !"label".equals(type) && !"multilineedit".equals(type)) {
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
            Log.i(TAG, "confirm_payload windowId=" + windowId
                    + " title=" + payload.optString("title", "")
                    + " text=" + payload.optString("text", "")
                    + " controls=" + (payload.optJSONArray("controls") == null ? 0
                    : payload.optJSONArray("controls").length())
                    + " responses=" + (payload.optJSONArray("responses") == null ? 0
                    : payload.optJSONArray("responses").length()));
            View root = LayoutInflater.from(activity).inflate(R.layout.lolib_dialog_calc_confirm, null);

            String rawTitle = payload.optString("title", "");
            String rawText = payload.optString("text", "");
            JSONArray controls = payload.optJSONArray("controls");
            // 应用名标题（Collabora Office Calc）→ 错误信息在 controls/text
            boolean appTitle = isAppNameTitle(rawTitle);
            String message = appTitle
                    ? extractFixedTextFromControls(controls)
                    : resolveMessageboxMessage(rawTitle, rawText, controls);
            if (message.isEmpty() && !appTitle) {
                message = rawText;
            }
            String title = appTitle ? "提示" : resolveMessageboxTitle(rawTitle, message);
            Log.i(TAG, "confirm_resolved title=" + title + " message=" + message
                    + " appTitle=" + appTitle);
            String displayTitle = title;
            String displayMessage = message;
            if (displayMessage.isEmpty() || displayMessage.equals(rawTitle)) {
                displayMessage = "";
            }

            TextView titleView = root.findViewById(R.id.calc_confirm_title);
            titleView.setText(displayTitle);

            TextView messageView = root.findViewById(R.id.calc_confirm_message);
            if (displayMessage.isEmpty()) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setVisibility(View.VISIBLE);
                messageView.setText(displayMessage);
            }

            LinearLayout buttonRow = root.findViewById(R.id.calc_confirm_button_row);
            List<ButtonSpec> buttons = buildButtons(payload);
            if (buttons.isEmpty()) {
                buttons.add(new ButtonSpec("ok", "确定", 1, true));
            }
            for (int i = 0; i < buttons.size(); i++) {
                ButtonSpec spec = buttons.get(i);
                boolean primary = spec.primary;
                View button = buildCalcButton(activity, spec.label, primary);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, host.dpToPx(44), 1f);
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

        /** Calc 表格风格按钮：主按钮绿色胶囊，次按钮描边。 */
        private View buildCalcButton(LOActivity activity, String label, boolean primary) {
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(android.view.Gravity.CENTER);
            container.setClickable(true);
            container.setFocusable(true);
            container.setBackgroundResource(primary
                    ? R.drawable.lolib_bg_calc_sheet_pill_primary
                    : R.drawable.lolib_bg_outline_secondary_button);

            TextView text = new TextView(activity);
            text.setText(label);
            text.setTextSize(16);
            text.setTextColor(primary ? 0xFFFFFFFF : 0xFF333333);
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

    // -------------------------------------------------------------------------
    // Calc 数据有效性 (.uno:Validation) — 吞掉原生弹窗，自定义 UI 已接管
    // -------------------------------------------------------------------------

    private final class ValidationDialogHandler implements DialogHandler {
        @Override
        public boolean canHandle(JSONObject payload) {
            return "ValidationDialog".equals(payload.optString("dialogId", ""));
        }

        @Override
        public void show(LOActivity activity, JSONObject payload) {
            int windowId = payload.optInt("windowId", -1);
            Log.i(TAG, "validation_dialog_intercepted windowId=" + windowId
                    + " controls=" + (payload.optJSONArray("controls") == null ? 0
                    : payload.optJSONArray("controls").length()));
            // 拦截原生弹窗，读/写交给 LOActivity 协调（见 onValidationDialogIntercepted）
            activity.onValidationDialogIntercepted(payload);
            activity.closeMobileWizardFromNative("validation_intercepted");
        }
    }

    // -------------------------------------------------------------------------
    // MacroSelectorDialog (.uno:RunMacro) — 懒加载枚举真实宏树
    // -------------------------------------------------------------------------

    private final class MacroSelectorDialogHandler implements UpdatableDialogHandler {
        private static final int STAGE_SCOPE_EXPAND = 0; // 展开作用域根（我的宏/应用宏）
        private static final int STAGE_LIB_EXPAND = 1;   // 展开库
        private static final int STAGE_LIB_SELECT = 2;   // select 库收宏
        private static final int STAGE_DONE = 3;

        private boolean sessionOpen = false;
        private int windowId = -1;
        private CalcValidationMacroCatalog.Callback callback;
        private final CalcValidationMacroCatalog catalog = new CalcValidationMacroCatalog();
        private boolean awaitingUpdate = false;
        private int stage = STAGE_DONE;
        private final java.util.List<String> scopeQueue = new java.util.ArrayList<>();
        private final java.util.List<int[]> libQueue = new java.util.ArrayList<>(); // {row, ...} 待展开库
        private final java.util.List<String> libNames = new java.util.ArrayList<>();
        private String currentScope = "document";
        private int currentScopeRow = -1;
        private int currentLibRow = -1;
        private String currentLibrary = "";

        @Override
        public boolean canHandle(JSONObject payload) {
            return "MacroSelectorDialog".equals(payload.optString("dialogId", ""));
        }

        @Override
        public boolean isActive() {
            return sessionOpen;
        }

        @Override
        public void show(LOActivity activity, JSONObject payload) {
            windowId = payload.optInt("windowId", -1);
            sessionOpen = true;
            awaitingUpdate = false;
            scopeQueue.clear();
            libQueue.clear();
            callback = macroHarvestCallback;
            Log.i(TAG, "macro_selector_show windowId=" + windowId);
            // 解析 categories 顶层 scope 根（我的宏/应用程序的宏）
            JSONArray controls = payload.optJSONArray("controls");
            JSONObject categories = findControl(controls, "categories");
            if (categories == null) {
                finishHarvest(activity);
                return;
            }
            JSONArray entries = categories.optJSONArray("entries");
            if (entries == null || entries.length() == 0) {
                finishHarvest(activity);
                return;
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String text = entry.optString("text", "");
                int row = entry.optInt("row", -1);
                boolean scopeDoc = isDocumentScope(text);
                if (scopeDoc) {
                    currentScopeRow = row;
                }
            }
            if (currentScopeRow < 0) {
                finishHarvest(activity);
                return;
            }
            currentScope = "document";
            scopeQueue.clear();
            stage = STAGE_SCOPE_EXPAND;
            expandScopeRow(activity, currentScopeRow);
        }

        /** 展开作用域根行（我的宏/应用宏）。 */
        private void expandScopeRow(LOActivity activity, int row) {
            if (row < 0) {
                nextScopeOrFinish(activity);
                return;
            }
            sendTreeViewEvent(windowId, "categories", "expand", row);
            awaitingUpdate = true;
            Log.i(TAG, "macro_selector_expand_scope row=" + row + " scope=" + currentScope);
        }

        @Override
        public void update(JSONObject payload) {
            if (!sessionOpen) {
                return;
            }
            JSONArray controls = payload.optJSONArray("controls");
            JSONObject control = controls == null || controls.length() == 0 ? null : controls.optJSONObject(0);
            if (control == null) {
                return;
            }
            String controlId = control.optString("id", "");
            Log.i(TAG, "macro_selector_update id=" + controlId
                    + " entries=" + (control.optJSONArray("entries") == null ? 0
                    : control.optJSONArray("entries").length())
                    + " stage=" + stage);
            awaitingUpdate = false;
            if ("categories".equals(controlId)) {
                handleCategoriesUpdate(control);
            } else if ("commands".equals(controlId)) {
                handleCommandsUpdate(control);
            }
        }

        private void handleCategoriesUpdate(JSONObject control) {
            JSONArray entries = control.optJSONArray("entries");
            if (entries == null) {
                return;
            }
            if (stage == STAGE_SCOPE_EXPAND) {
                // 展开作用域根后：解析库列表（scope 根的 children）
                libQueue.clear();
                collectLibraries(entries, currentScope, libQueue);
                Log.i(TAG, "macro_selector_libraries scope=" + currentScope
                        + " count=" + libQueue.size());
                if (libQueue.isEmpty()) {
                    nextScopeOrFinish(host);
                } else {
                    stage = STAGE_LIB_EXPAND;
                    expandNextLibrary(host);
                }
            } else if (stage == STAGE_LIB_EXPAND) {
                // 库展开后：select 库收宏
                stage = STAGE_LIB_SELECT;
                selectCurrentLibrary(host);
            }
        }

        /** 从 scope 根的 children 收集库列表 {row, name}。 */
        private void collectLibraries(JSONArray entries, String scopeKey, java.util.List<int[]> out) {
            if (entries == null) {
                return;
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String text = entry.optString("text", "");
                int row = entry.optInt("row", -1);
                if (row < 0 || text.isEmpty()) {
                    continue;
                }
                boolean isScope = isDocumentScope(text);
                boolean matchesScope = "document".equals(scopeKey) ? isScope : !isScope;
                if (matchesScope) {
                    JSONArray children = entry.optJSONArray("children");
                    if (children != null) {
                        // 库在 scope 根的 children
                        for (int j = 0; j < children.length(); j++) {
                            JSONObject lib = children.optJSONObject(j);
                            if (lib == null) {
                                continue;
                            }
                            String libText = lib.optString("text", "");
                            int libRow = lib.optInt("row", -1);
                            if (libRow >= 0 && !libText.isEmpty()) {
                                out.add(new int[]{libRow});
                                libNames.add(libText);
                            }
                        }
                    }
                }
            }
        }

        private void expandNextLibrary(LOActivity activity) {
            if (libQueue.isEmpty()) {
                nextScopeOrFinish(activity);
                return;
            }
            int[] lib = libQueue.get(0);
            currentLibRow = lib[0];
            currentLibrary = libNames.isEmpty() ? "" : libNames.get(0);
            if (!libNames.isEmpty()) {
                libNames.remove(0);
            }
            stage = STAGE_LIB_EXPAND;
            sendTreeViewEvent(windowId, "categories", "expand", currentLibRow);
            awaitingUpdate = true;
            Log.i(TAG, "macro_selector_expand_lib row=" + currentLibRow + " lib=" + currentLibrary);
        }

        private void selectCurrentLibrary(LOActivity activity) {
            sendTreeViewEvent(windowId, "categories", "select", currentLibRow);
            awaitingUpdate = true;
            Log.i(TAG, "macro_selector_select_lib row=" + currentLibRow + " lib=" + currentLibrary);
        }

        private void handleCommandsUpdate(JSONObject control) {
            if (stage != STAGE_LIB_SELECT) {
                return;
            }
            JSONArray entries = control.optJSONArray("entries");
            boolean docScope = "document".equals(currentScope);
            String scopeLabel = docScope ? "我的宏" : "应用程序的宏";
            String library = currentLibrary.isEmpty() ? "Standard" : currentLibrary;
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null) {
                        continue;
                    }
                    String name = entry.optString("text", "");
                    if (name.isEmpty() || "<dummy>".equals(name)) {
                        continue;
                    }
                    String uri = buildMacroUri(library, "", name, docScope);
                    catalog.add(new CalcValidationMacroCatalog.MacroItem(
                            docScope, scopeLabel, library, "", name, uri));
                }
            }
            // 移除当前库，继续下一个库 / 下一作用域
            if (!libQueue.isEmpty()) {
                libQueue.remove(0);
            }
            expandNextLibrary(host);
        }

        private void nextScopeOrFinish(LOActivity activity) {
            if (!scopeQueue.isEmpty()) {
                currentScope = scopeQueue.get(0);
                scopeQueue.remove(0);
                currentScopeRow = -1;
                stage = STAGE_SCOPE_EXPAND;
                expandScopeRow(activity, currentScopeRow);
                return;
            }
            finishHarvest(activity);
        }

        private void finishHarvest(LOActivity activity) {
            sessionOpen = false;
            awaitingUpdate = false;
            int cancelCode = 0;
            if (windowId >= 0) {
                sendResponse(windowId, "cancel", cancelCode);
            }
            Log.i(TAG, "macro_selector_finish items=" + catalog.items().size());
            if (callback != null) {
                callback.onCatalogLoaded(catalog);
            }
            if (windowId >= 0) {
                activity.closeMobileWizardFromNative("macro_harvest_finish");
            }
        }
    }

    private static boolean isDocumentScope(String text) {
        if (text == null) {
            return true;
        }
        return !text.contains("应用程序的宏") && !text.contains("Application")
                && !text.contains("share");
    }

    private static String buildMacroUri(String library, String module, String name, boolean docScope) {
        String location = docScope ? "document" : "application";
        StringBuilder sb = new StringBuilder("vnd.sun.star.script:");
        if (library != null) {
            sb.append(library);
        }
        if (module != null && !module.isEmpty()) {
            sb.append('.').append(module);
        }
        sb.append('.').append(name);
        sb.append("?language=Basic&location=").append(location);
        return sb.toString();
    }

    private void sendTreeViewEvent(int windowId, String controlId, String cmd, int row) {
        if (windowId < 0) {
            return;
        }
        String js = "(function(){"
                + "try{"
                + "if(!window.app||!app.socket){return 'no_socket';}"
                + "var msg='dialogevent " + windowId + " '"
                + "+JSON.stringify({id:" + JSONObject.quote(controlId)
                + ",cmd:" + JSONObject.quote(cmd)
                + ",data:" + row
                + ",type:'treeview'});"
                + "app.socket.sendMessage(msg);"
                + "return 'ok';"
                + "}catch(e){return 'err:'+e;}"
                + "})()";
        host.evaluateJavascript(js, value -> Log.d(TAG, "treeview_event_sent id=" + controlId
                + " cmd=" + cmd + " row=" + row + " result=" + value));
    }

    /** 启动真实宏枚举：LOActivity 调用，openMacroPicker 前先 harvest。 */
    void startMacroCatalogHarvest(CalcValidationMacroCatalog.Callback callback) {
        this.macroHarvestCallback = callback;
    }
}
