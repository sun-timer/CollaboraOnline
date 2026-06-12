package org.libreoffice.androidlib;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;

public final class DocumentShareHelper {
    private DocumentShareHelper() {
    }

    public static void shareDocument(Activity activity, Uri sourceUri, File tempFile) {
        if (activity == null) {
            return;
        }
        Uri shareUri = resolveShareUri(activity, sourceUri, tempFile);
        if (shareUri == null) {
            return;
        }
        String mimeType = activity.getContentResolver().getType(shareUri);
        if (mimeType == null) {
            mimeType = guessMimeType(shareUri);
        }
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(shareIntent, "分享文档"));
    }

    private static Uri resolveShareUri(Activity activity, Uri sourceUri, File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            return FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    tempFile);
        }
        return sourceUri;
    }

    private static String guessMimeType(Uri uri) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (ext == null || ext.isEmpty()) {
            return null;
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
    }
}
