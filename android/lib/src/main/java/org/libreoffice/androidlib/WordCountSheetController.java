package org.libreoffice.androidlib;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.libreoffice.androidlib.ai.AiDialogHelper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native bottom sheet for Writer word count (Figma 5194:55623).
 */
public class WordCountSheetController {
    private static final String[][] ROWS = new String[][]{
            {"docwords", "字词"},
            {"docchars", "字符数（计空格）"},
            {"doccharsnospaces", "字符数（不计空格）"},
            {"doccjkchars", "东亚文字+韩文"},
            {"docComments", "批注数"},
    };

    public interface Host {
        android.content.Context getContext();

        int dpToPx(int dp);

        int getBottomChromeHeightPx();
    }

    private final Host host;
    private BottomSheetDialog dialog;
    private LinearLayout listContainer;
    private final Map<String, TextView> valueViews = new LinkedHashMap<>();
    private final Map<String, View> rowViews = new LinkedHashMap<>();

    public WordCountSheetController(Host host) {
        this.host = host;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void show(JSONObject payload, Runnable onDismiss) {
        if (dialog != null && dialog.isShowing()) {
            applyPayload(payload);
            return;
        }
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_word_count, null);
        listContainer = panel.findViewById(R.id.word_count_list);
        buildRows();
        applyPayload(payload);

        ImageButton back = panel.findViewById(R.id.word_count_back);
        if (back != null) {
            back.setOnClickListener(v -> dismiss());
        }

        dialog = new BottomSheetDialog(host.getContext());
        dialog.setContentView(panel);
        AiDialogHelper.applyCloseOnlyDismiss(dialog);
        dialog.setOnDismissListener(d -> {
            dialog = null;
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        dialog.setOnShowListener(d -> expandSheet(panel));
        dialog.show();
    }

    public void update(JSONObject payload) {
        if (!isShowing()) {
            return;
        }
        applyPayload(payload);
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void buildRows() {
        listContainer.removeAllViews();
        valueViews.clear();
        rowViews.clear();
        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (int i = 0; i < ROWS.length; i++) {
            String id = ROWS[i][0];
            String label = ROWS[i][1];
            View row = inflater.inflate(R.layout.lolib_item_word_count_row, listContainer, false);
            TextView labelView = row.findViewById(R.id.word_count_row_label);
            TextView valueView = row.findViewById(R.id.word_count_row_value);
            labelView.setText(label);
            valueView.setText("0");
            listContainer.addView(row);
            valueViews.put(id, valueView);
            rowViews.put(id, row);
            if (i < ROWS.length - 1) {
                View divider = new View(host.getContext());
                divider.setBackgroundColor(0x14000000);
                listContainer.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dpToPx(1)));
            }
        }
    }

    private void applyPayload(JSONObject payload) {
        if (payload == null) {
            return;
        }
        JSONArray controls = payload.optJSONArray("controls");
        if (controls == null) {
            return;
        }
        for (int i = 0; i < controls.length(); i++) {
            JSONObject control = controls.optJSONObject(i);
            if (control == null) {
                continue;
            }
            applyControl(control);
        }
    }

    private void applyControl(JSONObject control) {
        String id = control.optString("id", "");
        if (!valueViews.containsKey(id)) {
            return;
        }
        View row = rowViews.get(id);
        if (control.has("visible")) {
            boolean visible = control.optBoolean("visible", true);
            if (row != null) {
                row.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }
        if (control.has("hidden")) {
            boolean hidden = control.optBoolean("hidden", false);
            if (row != null) {
                row.setVisibility(hidden ? View.GONE : View.VISIBLE);
            }
        }
        String text = control.optString("text", "").trim();
        if (!text.isEmpty()) {
            valueViews.get(id).setText(normalizeCount(text));
        }
    }

    private static String normalizeCount(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "0";
        }
        try {
            String digits = trimmed.replace(",", "").replace(" ", "");
            long value = Long.parseLong(digits);
            return String.format(Locale.getDefault(), "%,d", value);
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private void expandSheet(View contentRoot) {
        if (dialog == null) {
            return;
        }
        BottomSheetStyleHelper.applyFigmaPanel(dialog, contentRoot, host.dpToPx(28));
        BottomSheetAnchorHelper.Options options = new BottomSheetAnchorHelper.Options();
        options.logTag = "WordCountSheet";
        options.draggable = false;
        options.applyNavBarPadding = false;
        BottomSheetAnchorHelper.clearAppliedHeight(dialog);
        BottomSheetAnchorHelper.expandWrapContent(dialog, 0.92f, options);
    }
}
