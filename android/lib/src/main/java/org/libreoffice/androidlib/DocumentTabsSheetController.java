package org.libreoffice.androidlib;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentTabsSheetController {
    public static final int REQUEST_OPEN_DOCUMENT = 520;

    public interface Host {
        android.content.Context getContext();

        View findViewById(int id);

        SharedPreferences getExplorerPrefs();

        String getCurrentDocumentUri();

        void startActivityForResult(Intent intent, int requestCode);

        void openDocumentUri(Uri uri);

        void onOpenDocumentListChanged();
    }

    private final Host host;
    private View overlayView;
    private View scrimView;
    private boolean showingOpened = true;
    private LinearLayout listContainer;
    private TextView openedTabView;
    private TextView closedTabView;

    public DocumentTabsSheetController(Host host) {
        this.host = host;
    }

    public void bindOverlayViews() {
        overlayView = host.findViewById(R.id.document_tabs_overlay);
        scrimView = host.findViewById(R.id.document_tabs_scrim);
        View panel = host.findViewById(R.id.document_tabs_panel_include);
        if (panel != null) {
            bindPanel(panel);
        }
        if (scrimView != null) {
            scrimView.setOnClickListener(v -> dismiss());
        }
    }

    public void show() {
        if (overlayView == null) {
            bindOverlayViews();
        }
        if (overlayView == null) {
            return;
        }
        showingOpened = true;
        if (openedTabView != null && closedTabView != null) {
            styleTab(openedTabView, true);
            styleTab(closedTabView, false);
        }
        refreshList();
        overlayView.setVisibility(View.VISIBLE);
    }

    public void dismiss() {
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        host.onOpenDocumentListChanged();
    }

    public boolean isVisible() {
        return overlayView != null && overlayView.getVisibility() == View.VISIBLE;
    }

    private void bindPanel(View panel) {
        openedTabView = panel.findViewById(R.id.tabs_tab_opened);
        closedTabView = panel.findViewById(R.id.tabs_tab_closed);
        listContainer = panel.findViewById(R.id.tabs_list_container);
        View openDocument = panel.findViewById(R.id.tabs_open_document);
        ImageButton close = panel.findViewById(R.id.tabs_sheet_close);

        Runnable refresh = this::refreshList;
        if (openedTabView != null) {
            openedTabView.setOnClickListener(v -> {
                showingOpened = true;
                styleTab(openedTabView, true);
                styleTab(closedTabView, false);
                refresh.run();
            });
        }
        if (closedTabView != null) {
            closedTabView.setOnClickListener(v -> {
                showingOpened = false;
                styleTab(openedTabView, false);
                styleTab(closedTabView, true);
                refresh.run();
            });
        }
        if (close != null) {
            close.setOnClickListener(v -> dismiss());
        }
        if (openDocument != null) {
            openDocument.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                host.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
                dismiss();
            });
        }

        styleTab(openedTabView, true);
        styleTab(closedTabView, false);
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
        host.onOpenDocumentListChanged();

        if (uris.isEmpty()) {
            TextView empty = new TextView(host.getContext());
            empty.setText(showingOpened ? "暂无最近文档" : "暂无最近关闭文档");
            empty.setTextColor(0xFF80868B);
            empty.setPadding(0, 24, 0, 24);
            container.addView(empty);
            return;
        }

        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(host.getContext());
        for (String uriString : uris) {
            View row = inflater.inflate(R.layout.lolib_item_document_tab, container, false);
            bindRow(row, uriString, currentUri, showingOpened);
            container.addView(row);
        }
    }

    private void bindRow(View row, String uriString, String currentUri, boolean openedTab) {
        ImageView icon = row.findViewById(R.id.tab_item_icon);
        TextView title = row.findViewById(R.id.tab_item_title);
        TextView subtitle = row.findViewById(R.id.tab_item_subtitle);
        TextView current = row.findViewById(R.id.tab_item_current);
        ImageButton remove = row.findViewById(R.id.tab_item_remove);

        Uri uri = Uri.parse(uriString);
        String displayName = queryDisplayName(uri);
        if (TextUtils.isEmpty(displayName)) {
            displayName = uri.getLastPathSegment();
        }
        if (icon != null) {
            icon.setImageResource(fileTypeIconRes(displayName));
        }
        title.setText(stripDisplayExtension(displayName));
        subtitle.setText(formatSubtitle(uri));

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

    private static int fileTypeIconRes(String name) {
        if (TextUtils.isEmpty(name)) {
            return R.drawable.lolib_ic_file_type_writer;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ods") || lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) {
            return R.drawable.lolib_ic_file_type_calc;
        }
        if (lower.endsWith(".odp") || lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return R.drawable.lolib_ic_file_type_impress;
        }
        return R.drawable.lolib_ic_file_type_writer;
    }

    private static String stripDisplayExtension(String name) {
        if (TextUtils.isEmpty(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) {
            return name;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "odt":
            case "ods":
            case "odp":
            case "odg":
            case "odf":
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
            case "pdf":
            case "txt":
            case "rtf":
            case "csv":
                return name.substring(0, dot);
            default:
                return name;
        }
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
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        if (updated <= 0L) {
            return timeFormat.format(new Date());
        }
        Calendar today = Calendar.getInstance();
        Calendar fileDay = Calendar.getInstance();
        fileDay.setTimeInMillis(updated);
        Date updatedDate = new Date(updated);
        if (isSameDay(today, fileDay)) {
            return timeFormat.format(updatedDate);
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, fileDay)) {
            return "昨天 " + timeFormat.format(updatedDate);
        }
        return dateFormat.format(updatedDate);
    }

    private static boolean isSameDay(Calendar left, Calendar right) {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private static void styleTab(TextView tab, boolean active) {
        if (tab == null) {
            return;
        }
        tab.setBackgroundResource(active
                ? R.drawable.lolib_bg_document_tabs_tab_active
                : android.R.color.transparent);
        tab.setTextColor(active ? 0xFF101010 : 0xFFCCCCCC);
        tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.NORMAL);
    }
}
