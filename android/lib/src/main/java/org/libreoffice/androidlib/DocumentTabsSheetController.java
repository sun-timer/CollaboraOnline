package org.libreoffice.androidlib;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentTabsSheetController {
    public static final int REQUEST_OPEN_DOCUMENT = 520;

    public interface Host {
        android.content.Context getContext();

        SharedPreferences getExplorerPrefs();

        String getCurrentDocumentUri();

        void startActivityForResult(Intent intent, int requestCode);

        void openDocumentUri(Uri uri);
    }

    private final Host host;
    private BottomSheetDialog dialog;
    private boolean showingOpened = true;
    private LinearLayout listContainer;
    private TextView openedTabView;
    private TextView closedTabView;

    public DocumentTabsSheetController(Host host) {
        this.host = host;
    }

    public void show() {
        dismiss();
        View panel = LayoutInflater.from(host.getContext()).inflate(R.layout.lolib_sheet_document_tabs, null);
        bindPanel(panel);
        dialog = new BottomSheetDialog(host.getContext());
        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
        expandSheet(dialog);
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void bindPanel(View panel) {
        openedTabView = panel.findViewById(R.id.tabs_tab_opened);
        closedTabView = panel.findViewById(R.id.tabs_tab_closed);
        listContainer = panel.findViewById(R.id.tabs_list_container);
        TextView openDocument = panel.findViewById(R.id.tabs_open_document);
        ImageButton close = panel.findViewById(R.id.tabs_sheet_close);

        Runnable refresh = this::refreshList;
        openedTabView.setOnClickListener(v -> {
            showingOpened = true;
            styleTab(openedTabView, true);
            styleTab(closedTabView, false);
            refresh.run();
        });
        closedTabView.setOnClickListener(v -> {
            showingOpened = false;
            styleTab(openedTabView, false);
            styleTab(closedTabView, true);
            refresh.run();
        });
        close.setOnClickListener(v -> dismiss());
        openDocument.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            host.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
            dismiss();
        });

        styleTab(openedTabView, true);
        styleTab(closedTabView, false);
        refresh.run();
    }

    private void refreshList() {
        if (listContainer == null) {
            return;
        }
        populateList(listContainer);
    }

    private void populateList(LinearLayout container) {
        container.removeAllViews();
        SharedPreferences prefs = host.getExplorerPrefs();
        List<String> uris = showingOpened
                ? RecentDocumentsStore.getRecentUris(prefs)
                : RecentDocumentsStore.getRecentlyClosedUris(prefs);
        String currentUri = host.getCurrentDocumentUri();

        int openedCount = RecentDocumentsStore.getRecentUris(prefs).size();
        if (openedTabView != null) {
            openedTabView.setText("已打开 (" + Math.max(openedCount, 1) + ")");
        }

        if (uris.isEmpty()) {
            TextView empty = new TextView(host.getContext());
            empty.setText(showingOpened ? "暂无最近文档" : "暂无最近关闭文档");
            empty.setTextColor(0xFF80868B);
            empty.setPadding(0, 24, 0, 24);
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(host.getContext());
        for (String uriString : uris) {
            View row = inflater.inflate(R.layout.lolib_item_document_tab, container, false);
            bindRow(row, uriString, currentUri, showingOpened);
            container.addView(row);
        }
    }

    private void bindRow(View row, String uriString, String currentUri, boolean openedTab) {
        TextView badge = row.findViewById(R.id.tab_item_badge);
        TextView title = row.findViewById(R.id.tab_item_title);
        TextView subtitle = row.findViewById(R.id.tab_item_subtitle);
        TextView current = row.findViewById(R.id.tab_item_current);
        ImageButton remove = row.findViewById(R.id.tab_item_remove);

        Uri uri = Uri.parse(uriString);
        String displayName = queryDisplayName(uri);
        if (TextUtils.isEmpty(displayName)) {
            displayName = uri.getLastPathSegment();
        }
        title.setText(displayName);
        subtitle.setText(formatSubtitle(uri));
        badge.setText(fileBadge(displayName));

        boolean isCurrent = uriString.equals(currentUri);
        current.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
        remove.setVisibility(openedTab && !isCurrent ? View.VISIBLE : View.GONE);

        row.setOnClickListener(v -> {
            if (isCurrent) {
                dismiss();
                return;
            }
            if (openedTab) {
                host.openDocumentUri(uri);
            } else {
                RecentDocumentsStore.restoreFromRecentlyClosed(host.getExplorerPrefs(), uriString);
                host.openDocumentUri(uri);
            }
            dismiss();
        });

        remove.setOnClickListener(v -> {
            RecentDocumentsStore.moveToRecentlyClosed(host.getExplorerPrefs(), uriString);
            refreshList();
        });
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = host.getContext().getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private String formatSubtitle(Uri uri) {
        long updated = 0L;
        Cursor cursor = null;
        try {
            cursor = host.getContext().getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                if (idx >= 0) {
                    updated = cursor.getLong(idx);
                }
            }
        } catch (Exception ignored) {
            updated = 0L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (updated <= 0L) {
            return "今天";
        }
        return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date(updated));
    }

    private static String fileBadge(String name) {
        if (TextUtils.isEmpty(name)) {
            return "W";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1);
            if (!ext.isEmpty()) {
                return String.valueOf(Character.toUpperCase(ext.charAt(0)));
            }
        }
        return "W";
    }

    private static void styleTab(TextView tab, boolean active) {
        if (tab == null) {
            return;
        }
        tab.setBackgroundColor(active ? 0xFFFFFFFF : 0xFFE4E4E6);
        tab.setTextColor(active ? 0xFF202124 : 0xFF80868B);
    }

    private static void expandSheet(BottomSheetDialog dialog) {
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
