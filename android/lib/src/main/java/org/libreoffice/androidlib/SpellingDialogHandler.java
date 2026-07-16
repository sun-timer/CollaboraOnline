package org.libreoffice.androidlib;

import android.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.libreoffice.androidlib.ai.AiDialogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Native replacement for core {@code SpellingDialog} (spell check wizard).
 */
final class SpellingDialogHandler implements NativeJSDialogController.DialogHandler,
        NativeJSDialogController.UpdatableDialogHandler {

    private static final String TAG = "NativeJSDialog";

    private final NativeJSDialogController controller;
    private final LOActivity host;

    private AlertDialog dialog;
    private int windowId = -1;
    /** True from {@link #show} until user closes spell UI; covers the inflate gap before {@link #isActive()}. */
    private boolean sessionOpen = false;

    private TextView sentenceView;
    private ListView suggestionsList;
    private TextView noSuggestionsView;
    private View changeBtn;
    private View changeAllBtn;
    private View ignoreBtn;
    private View ignoreAllBtn;
    private View addDictBtn;
    private View closeBtn;

    private final List<String> suggestionTexts = new ArrayList<>();
    private final List<Integer> suggestionRows = new ArrayList<>();
    private int selectedRow = -1;
    private ArrayAdapter<String> suggestionsAdapter;

    SpellingDialogHandler(NativeJSDialogController controller, LOActivity host) {
        this.controller = controller;
        this.host = host;
    }

    @Override
    public boolean canHandle(JSONObject payload) {
        return "SpellingDialog".equals(payload.optString("dialogId", ""));
    }

    @Override
    public boolean isActive() {
        return dialog != null && dialog.isShowing();
    }

    boolean isSessionOpen() {
        return sessionOpen;
    }

    @Override
    public void show(LOActivity activity, JSONObject payload) {
        sessionOpen = true;
        controller.dismissActive();
        windowId = payload.optInt("windowId", -1);

        View root = LayoutInflater.from(activity).inflate(R.layout.lolib_dialog_spelling, null);
        bindViews(root);

        TextView titleView = root.findViewById(R.id.ai_dialog_header_title);
        String title = payload.optString("title", "");
        if (title.isEmpty()) {
            title = "拼写检查";
        }
        titleView.setText(title);

        root.findViewById(R.id.ai_dialog_header_close).setOnClickListener(v -> onClose());
        closeBtn.setOnClickListener(v -> onClose());
        changeBtn.setOnClickListener(v -> onPushButton("change"));
        changeAllBtn.setOnClickListener(v -> onPushButton("changeall"));
        ignoreBtn.setOnClickListener(v -> onPushButton("ignore"));
        ignoreAllBtn.setOnClickListener(v -> onPushButton("ignoreall"));
        addDictBtn.setOnClickListener(v -> onPushButton("add"));

        suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= suggestionRows.size()) {
                return;
            }
            selectedRow = suggestionRows.get(position);
            controller.sendDialogEvent(windowId, "suggestionslb", "select",
                    String.valueOf(selectedRow), "treeview");
        });

        applyControls(payload.optJSONArray("controls"));
        syncSelectionToCore();

        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setView(root);
        AiDialogHelper.applyCloseOnlyDismiss(alertDialog);
        AiDialogHelper.applyTransparentWindow(alertDialog);
        alertDialog.setOnDismissListener(d -> {
            if (dialog == alertDialog) {
                dialog = null;
                sessionOpen = false;
            }
        });
        alertDialog.show();
        controller.applyFlexibleDialogSize(alertDialog, root);
        controller.bindActiveDialog(alertDialog, windowId);
        dialog = alertDialog;
        Log.i(TAG, "spelling_dialog_show windowId=" + windowId);
    }

    @Override
    public void update(JSONObject payload) {
        if (!isActive()) {
            return;
        }
        applyControls(payload.optJSONArray("controls"));
    }

    private void bindViews(View root) {
        sentenceView = root.findViewById(R.id.spell_sentence);
        suggestionsList = root.findViewById(R.id.spell_suggestions);
        noSuggestionsView = root.findViewById(R.id.spell_no_suggestions);
        changeBtn = root.findViewById(R.id.spell_change_btn);
        changeAllBtn = root.findViewById(R.id.spell_change_all_btn);
        ignoreBtn = root.findViewById(R.id.spell_ignore_btn);
        ignoreAllBtn = root.findViewById(R.id.spell_ignore_all_btn);
        addDictBtn = root.findViewById(R.id.spell_add_dict_btn);
        closeBtn = root.findViewById(R.id.spell_close_btn);

        suggestionsAdapter = new ArrayAdapter<>(host, android.R.layout.simple_list_item_1, suggestionTexts);
        suggestionsList.setAdapter(suggestionsAdapter);
    }

    private void applyControls(JSONArray controls) {
        if (controls == null) {
            return;
        }
        for (int i = 0; i < controls.length(); i++) {
            JSONObject control = controls.optJSONObject(i);
            if (control != null) {
                applyControl(control);
            }
        }
    }

    private void applyControl(JSONObject control) {
        String id = control.optString("id", "");
        String type = control.optString("type", "");
        if (id.isEmpty()) {
            return;
        }

        switch (id) {
            case "errorsentence":
            case "explain":
                applySentenceText(control);
                break;
            case "suggestionslb":
                applySuggestions(control);
                syncSelectionToCore();
                break;
            case "change":
                applyButtonEnabled(changeBtn, control);
                break;
            case "changeall":
                applyButtonEnabled(changeAllBtn, control);
                break;
            case "ignore":
                applyButtonEnabled(ignoreBtn, control);
                break;
            case "ignoreall":
                applyButtonEnabled(ignoreAllBtn, control);
                applyButtonVisibility(ignoreAllBtn, control);
                break;
            case "add":
                applyButtonEnabled(addDictBtn, control);
                applyButtonVisibility(addDictBtn, control);
                break;
            case "close":
                applyButtonEnabled(closeBtn, control);
                break;
            default:
                if (("fixedtext".equals(type) || "label".equals(type))
                        && !control.optString("text", "").isEmpty()
                        && (id.contains("sentence") || id.contains("explain"))) {
                    sentenceView.setText(control.optString("text", ""));
                }
                break;
        }
    }

    private void applySentenceText(JSONObject control) {
        String text = control.optString("text", "");
        if (!text.isEmpty()) {
            sentenceView.setText(text);
            return;
        }
        String image = control.optString("image", "");
        if (!image.isEmpty()) {
            sentenceView.setText("（错词已在文档中高亮显示）");
        }
    }

    private void applySuggestions(JSONObject control) {
        suggestionTexts.clear();
        suggestionRows.clear();
        selectedRow = -1;

        JSONArray entries = control.optJSONArray("entries");
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                parseSuggestionEntry(entries.opt(i), i);
            }
        }

        JSONArray selectedEntries = control.optJSONArray("selectedEntries");
        if (selectedEntries != null && selectedEntries.length() > 0) {
            selectedRow = selectedEntries.optInt(0, 0);
        } else if (!suggestionRows.isEmpty()) {
            selectedRow = suggestionRows.get(0);
        }

        suggestionsAdapter.notifyDataSetChanged();
        if (suggestionTexts.isEmpty()) {
            suggestionsList.setVisibility(View.GONE);
            noSuggestionsView.setVisibility(View.VISIBLE);
        } else {
            suggestionsList.setVisibility(View.VISIBLE);
            noSuggestionsView.setVisibility(View.GONE);
            int selectIndex = suggestionRows.indexOf(selectedRow);
            if (selectIndex >= 0) {
                suggestionsList.setSelection(selectIndex);
            }
        }
    }

    private void parseSuggestionEntry(Object raw, int fallbackIndex) {
        if (raw instanceof String) {
            suggestionTexts.add((String) raw);
            suggestionRows.add(fallbackIndex);
            return;
        }
        if (!(raw instanceof JSONObject)) {
            return;
        }
        JSONObject entry = (JSONObject) raw;
        int row = entry.has("row") ? entry.optInt("row", fallbackIndex) : fallbackIndex;
        String text = entry.optString("text", "");
        if (text.isEmpty()) {
            JSONArray columns = entry.optJSONArray("columns");
            if (columns != null && columns.length() > 0) {
                Object col0 = columns.opt(0);
                if (col0 instanceof JSONObject) {
                    text = ((JSONObject) col0).optString("text", "");
                } else if (col0 instanceof String) {
                    text = (String) col0;
                }
            }
        }
        if (!text.isEmpty()) {
            suggestionTexts.add(text);
            suggestionRows.add(row);
            if (entry.optBoolean("selected", false)) {
                selectedRow = row;
            }
        }
    }

    private static void applyButtonEnabled(View button, JSONObject control) {
        if (button == null || !control.has("enabled")) {
            return;
        }
        boolean enabled = control.optBoolean("enabled", true);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }

    private static void applyButtonVisibility(View button, JSONObject control) {
        if (button == null) {
            return;
        }
        if (control.has("visible")) {
            button.setVisibility(control.optBoolean("visible", true) ? View.VISIBLE : View.GONE);
        } else if (control.optBoolean("hidden", false)) {
            button.setVisibility(View.GONE);
        }
    }

    private void onPushButton(String buttonId) {
        controller.sendDialogEvent(windowId, buttonId, "click", "", "pushbutton");
    }

    private void onClose() {
        sessionOpen = false;
        dialog = null;
        controller.dismissActive();
        controller.sendDialogEvent(windowId, "close", "click", "", "pushbutton");
        host.closeMobileWizardFromNative("spelling_close");
        Log.i(TAG, "spelling_dialog_close windowId=" + windowId);
    }

    private void syncSelectionToCore() {
        if (selectedRow >= 0) {
            controller.sendDialogEvent(windowId, "suggestionslb", "select",
                    String.valueOf(selectedRow), "treeview");
        }
    }
}
