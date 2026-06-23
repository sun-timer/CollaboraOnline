package org.libreoffice.androidlib;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class FindReplaceSheetController {
    public interface Host {
        android.content.Context getContext();

        void runFindBridge(String js);

        void ensureEditModeThen(Runnable action);

        boolean isEditModeActive();

        void onFindReplaceEditDispatched(boolean replaceAll);
    }

    private final Host host;
    private BottomSheetDialog mainDialog;
    private BottomSheetDialog settingsDialog;
    private boolean replaceTabActive = false;
    private boolean ignoreCase = true;
    private boolean caseSensitive = false;
    private boolean wholeWord = false;
    private boolean syncingQueryFields = false;

    public FindReplaceSheetController(Host host) {
        this.host = host;
    }

    public void show() {
        dismiss();
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_find_replace, null);
        bindMainPanel(panel);
        mainDialog = new BottomSheetDialog(host.getContext());
        mainDialog.setContentView(panel);
        mainDialog.setOnDismissListener(dialog -> mainDialog = null);
        mainDialog.show();
        expandSheet(mainDialog);
    }

    public void dismiss() {
        if (settingsDialog != null) {
            settingsDialog.dismiss();
            settingsDialog = null;
        }
        if (mainDialog != null) {
            mainDialog.dismiss();
            mainDialog = null;
        }
    }

    private void bindMainPanel(View panel) {
        TextView findTab = panel.findViewById(R.id.find_tab_find);
        TextView replaceTab = panel.findViewById(R.id.find_tab_replace);
        LinearLayout findPanel = panel.findViewById(R.id.find_panel_find);
        LinearLayout replacePanel = panel.findViewById(R.id.find_panel_replace);
        EditText findQuery = panel.findViewById(R.id.find_query_input);
        EditText replaceQuery = panel.findViewById(R.id.find_replace_query_input);
        EditText replaceWith = panel.findViewById(R.id.find_replace_with_input);

        ImageButton close = panel.findViewById(R.id.find_sheet_close);
        ImageButton settings = panel.findViewById(R.id.find_sheet_settings);
        ImageButton findPrev = panel.findViewById(R.id.find_btn_prev);
        ImageButton findNext = panel.findViewById(R.id.find_btn_next);
        ImageButton replacePrev = panel.findViewById(R.id.find_replace_btn_prev);
        ImageButton replaceNext = panel.findViewById(R.id.find_replace_btn_next);
        ImageButton replaceAll = panel.findViewById(R.id.find_replace_btn_all);
        ImageButton replaceOne = panel.findViewById(R.id.find_replace_btn_one);

        Runnable showFindTab = () -> {
            replaceTabActive = false;
            styleTab(findTab, true);
            styleTab(replaceTab, false);
            findPanel.setVisibility(View.VISIBLE);
            replacePanel.setVisibility(View.GONE);
        };
        Runnable showReplaceTab = () -> {
            replaceTabActive = true;
            styleTab(findTab, false);
            styleTab(replaceTab, true);
            findPanel.setVisibility(View.GONE);
            replacePanel.setVisibility(View.VISIBLE);
            mirrorQueryField(findQuery, replaceQuery);
        };

        findTab.setOnClickListener(v -> showFindTab.run());
        replaceTab.setOnClickListener(v -> showReplaceTab.run());
        close.setOnClickListener(v -> dismiss());
        settings.setOnClickListener(v -> showSettings());

        TextWatcher syncWatcher = new SimpleTextWatcher(() -> {
            if (syncingQueryFields) {
                return;
            }
            if (replaceTabActive) {
                mirrorQueryField(replaceQuery, findQuery);
            } else {
                mirrorQueryField(findQuery, replaceQuery);
            }
            pushOptionsToBridge();
        });
        findQuery.addTextChangedListener(syncWatcher);
        replaceQuery.addTextChangedListener(syncWatcher);
        replaceWith.addTextChangedListener(new SimpleTextWatcher(this::pushOptionsToBridge));

        findPrev.setOnClickListener(v -> runFindAction(
                "AndroidFindReplaceBridge.findPrevious()"));
        findNext.setOnClickListener(v -> runFindAction(
                buildFindJs(replaceTabActive ? replaceQuery : findQuery)));
        findNext.setOnLongClickListener(v -> {
            runFindAction("AndroidFindReplaceBridge.findNext()");
            return true;
        });

        replacePrev.setOnClickListener(v -> runReplaceNavigation(
                "AndroidFindReplaceBridge.findPrevious()"));
        replaceNext.setOnClickListener(v -> runReplaceNavigation(
                buildFindJs(replaceQuery)));
        replaceAll.setOnClickListener(v -> runReplaceAction(
                buildReplaceJs(replaceQuery, replaceWith, true), true, hasText(replaceQuery)));
        replaceOne.setOnClickListener(v -> runReplaceAction(
                buildReplaceJs(replaceQuery, replaceWith, false), false, hasText(replaceQuery)));

        showFindTab.run();
        pushOptionsToBridge();
    }

    private void showSettings() {
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_find_settings, null);
        SwitchCompat ignoreCaseSwitch = panel.findViewById(R.id.find_opt_ignore_case);
        SwitchCompat caseSensitiveSwitch = panel.findViewById(R.id.find_opt_case_sensitive);
        SwitchCompat wholeWordSwitch = panel.findViewById(R.id.find_opt_whole_word);
        ImageButton close = panel.findViewById(R.id.find_settings_close);

        ignoreCaseSwitch.setChecked(ignoreCase);
        caseSensitiveSwitch.setChecked(caseSensitive);
        wholeWordSwitch.setChecked(wholeWord);

        ignoreCaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ignoreCase = isChecked;
            if (isChecked) {
                caseSensitive = false;
                caseSensitiveSwitch.setChecked(false);
            }
            pushOptionsToBridge();
        });
        caseSensitiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            caseSensitive = isChecked;
            if (isChecked) {
                ignoreCase = false;
                ignoreCaseSwitch.setChecked(false);
            }
            pushOptionsToBridge();
        });
        wholeWordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wholeWord = isChecked;
            pushOptionsToBridge();
        });
        close.setOnClickListener(v -> {
            if (settingsDialog != null) {
                settingsDialog.dismiss();
            }
        });

        settingsDialog = new BottomSheetDialog(host.getContext());
        settingsDialog.setContentView(panel);
        settingsDialog.setOnDismissListener(dialog -> settingsDialog = null);
        settingsDialog.show();
        expandSheet(settingsDialog);
    }

    private void runFindAction(String js) {
        pushOptionsToBridge();
        host.runFindBridge(js);
    }

    private void runReplaceNavigation(String js) {
        host.ensureEditModeThen(() -> {
            pushOptionsToBridge();
            host.runFindBridge(js);
        });
    }

    private void runReplaceAction(String js, boolean replaceAll, boolean hasQuery) {
        host.ensureEditModeThen(() -> {
            pushOptionsToBridge();
            host.runFindBridge(js);
            if (hasQuery) {
                host.onFindReplaceEditDispatched(replaceAll);
            }
        });
    }

    private void mirrorQueryField(EditText source, EditText target) {
        CharSequence sourceText = source.getText() == null ? "" : source.getText();
        if (TextUtils.equals(sourceText, target.getText())) {
            return;
        }
        syncingQueryFields = true;
        try {
            target.setText(sourceText);
        } finally {
            syncingQueryFields = false;
        }
    }

    private void pushOptionsToBridge() {
        host.runFindBridge(
                "AndroidFindReplaceBridge.setOptions({"
                        + "ignoreCase:" + ignoreCase + ","
                        + "caseSensitive:" + caseSensitive + ","
                        + "wholeWord:" + wholeWord
                        + "})");
    }

    private String buildFindJs(EditText queryField) {
        String query = escapeJs(queryField.getText() == null ? "" : queryField.getText().toString());
        return "AndroidFindReplaceBridge.find('" + query + "')";
    }

    private String buildReplaceJs(EditText queryField, EditText withField, boolean replaceAll) {
        String query = escapeJs(queryField.getText() == null ? "" : queryField.getText().toString());
        String with = escapeJs(withField.getText() == null ? "" : withField.getText().toString());
        return "AndroidFindReplaceBridge.replaceForQuery('"
                + query + "','" + with + "'," + replaceAll + ")";
    }

    private static boolean hasText(EditText field) {
        return field != null
                && field.getText() != null
                && !field.getText().toString().trim().isEmpty();
    }

    private static String escapeJs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static void styleTab(TextView tab, boolean active) {
        tab.setBackgroundColor(active ? 0xFFFFFFFF : 0xFFE4E4E6);
        tab.setTextColor(active ? 0xFF202124 : 0xFF80868B);
    }

    private static void expandSheet(BottomSheetDialog dialog) {
        if (dialog == null) {
            return;
        }
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleTextWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChange.run();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
