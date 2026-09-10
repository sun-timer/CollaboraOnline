/* -*- tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidapp.ui;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.RecentDocumentTypeStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.recyclerview.widget.RecyclerView;

class RecentFilesAdapter extends RecyclerView.Adapter<RecentFilesAdapter.ViewHolder> {

    private static final int VIEW_TYPE_LIST = 0;
    private static final int VIEW_TYPE_GRID = 1;

    private final LibreOfficeUIActivity mActivity;
    private final boolean gridMode;
    private ArrayList<RecentFile> recentFiles;

    RecentFilesAdapter(LibreOfficeUIActivity activity, List<Uri> recentUris, boolean gridMode) {
        this.mActivity = activity;
        this.gridMode = gridMode;
        initRecentFiles(recentUris);
    }

    @Override
    public int getItemViewType(int position) {
        return gridMode ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = viewType == VIEW_TYPE_GRID
                ? R.layout.item_home_recent_file_grid
                : R.layout.item_home_recent_file;
        View item = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(item);
    }

    /** Validate uris in case of removed/renamed documents and return RecentFile ArrayList from the valid uris */
    public void initRecentFiles(List<Uri> recentUris) {
        this.recentFiles = new ArrayList<>();
        boolean invalidUriFound = false;
        String joined = "";
        for (Uri u : recentUris) {
            String filename = getUriFilename(mActivity, u);
            if (filename != null) {
                long length = getUriFileLength(mActivity, u);
                long openedAt = mActivity.getRecentOpenTime(u);
                recentFiles.add(new RecentFile(u, filename, length, openedAt));
                joined = joined.concat(u.toString() + "\n");
            } else {
                invalidUriFound = true;
            }
        }
        if (invalidUriFound) {
            mActivity.getPrefs().edit().putString(mActivity.RECENT_DOCUMENTS_KEY, joined).apply();
        }
    }

    /** Return the filename of the given Uri. */
    public static String getUriFilename(Activity activity, Uri uri) {
        String filename = "";
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                filename = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (filename.isEmpty()) {
            return null;
        }

        return filename;
    }

    /** Return the size of the given Uri. */
    public static long getUriFileLength(Activity activity, Uri uri) {
        long length = 0;
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                length = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
            }
        } catch (Exception e) {
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return length;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final RecentFile file = recentFiles.get(position);

        View.OnClickListener clickListener = view -> mActivity.open(file.uri);

        holder.filenameView.setOnClickListener(clickListener);
        holder.itemView.setOnClickListener(clickListener);

        if (holder.fileActionsImageView != null && holder.fileActionsImageView.getVisibility() == View.VISIBLE) {
            holder.fileActionsImageView.setOnClickListener(view ->
                    mActivity.showRecentFileActionsPopup(view, file.uri));
        } else {
            holder.itemView.setOnLongClickListener(view -> {
                mActivity.showRecentFileActionsPopup(view, file.uri);
                return true;
            });
        }

        holder.filenameView.setText(file.filename);
        bindFileTypeIcon(holder.imageView, file.uri, file.filename);
        holder.fileDateView.setText(formatOpenedAt(file.openedAt));
    }

    private void bindFileTypeIcon(ImageView iconView, Uri uri, String filename) {
        int fileType = resolveRecentFileType(uri, filename);
        int iconRes = R.drawable.ic_file_type_writer;
        switch (fileType) {
            case FileUtilities.CALC:
                iconRes = R.drawable.ic_file_type_calc;
                break;
            case FileUtilities.IMPRESS:
                iconRes = R.drawable.ic_file_type_impress;
                break;
            case FileUtilities.DOC:
            default:
                iconRes = R.drawable.ic_file_type_writer;
                break;
        }
        iconView.setImageResource(iconRes);
    }

    private int resolveRecentFileType(Uri uri, String filename) {
        String cached = RecentDocumentTypeStore.getNormalizedType(mActivity, uri);
        if (cached != null) {
            switch (cached) {
                case "spreadsheet":
                    return FileUtilities.CALC;
                case "presentation":
                    return FileUtilities.IMPRESS;
                case "drawing":
                    return FileUtilities.DRAWING;
                case "text":
                default:
                    return FileUtilities.DOC;
            }
        }
        return FileUtilities.getType(filename);
    }

    private String formatOpenedAt(long openedAt) {
        if (openedAt <= 0L) {
            return "";
        }
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayStart = today.getTimeInMillis();
        long yesterdayStart = todayStart - 24L * 60L * 60L * 1000L;

        Date date = new Date(openedAt);
        if (openedAt >= todayStart) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return timeFormat.format(date);
        }
        if (openedAt >= yesterdayStart) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return "昨天 " + timeFormat.format(date);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/M/d", Locale.getDefault());
        return dateFormat.format(date);
    }

    @Override
    public int getItemCount() {
        return recentFiles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView filenameView;
        TextView fileDateView;
        ImageView imageView;
        ImageView fileActionsImageView;

        ViewHolder(View itemView) {
            super(itemView);
            filenameView = itemView.findViewById(R.id.file_item_name);
            fileDateView = itemView.findViewById(R.id.file_item_date);
            imageView = itemView.findViewById(R.id.file_item_icon);
            fileActionsImageView = itemView.findViewById(R.id.file_actions_button);
        }
    }

    /** Cache the name & size so that we don't have ask later. */
    private static class RecentFile {
        Uri uri;
        String filename;
        long fileLength;
        long openedAt;

        RecentFile(Uri uri, String filename, long fileLength, long openedAt) {
            this.uri = uri;
            this.filename = filename;
            this.fileLength = fileLength;
            this.openedAt = openedAt;
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
