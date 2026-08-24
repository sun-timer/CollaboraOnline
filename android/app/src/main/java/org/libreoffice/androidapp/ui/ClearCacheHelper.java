package org.libreoffice.androidapp.ui;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;

/** Computes cache sizes and performs cleanup for the clear-cache screen. */
public final class ClearCacheHelper {
    public enum CleanMode {
        QUICK,
        DEEP
    }

    public static final class CategorySize {
        public final long tempFilesBytes;
        public final long imageCacheBytes;
        public final long aiPreviewBytes;
        public final long chatHistoryBytes;
        public final long offlineModelBytes;

        CategorySize(long tempFilesBytes, long imageCacheBytes, long aiPreviewBytes,
                long chatHistoryBytes, long offlineModelBytes) {
            this.tempFilesBytes = tempFilesBytes;
            this.imageCacheBytes = imageCacheBytes;
            this.aiPreviewBytes = aiPreviewBytes;
            this.chatHistoryBytes = chatHistoryBytes;
            this.offlineModelBytes = offlineModelBytes;
        }

        public long totalAppBytes() {
            return tempFilesBytes + imageCacheBytes + aiPreviewBytes + chatHistoryBytes + offlineModelBytes;
        }

        public long clearableBytes(CleanMode mode) {
            long quick = tempFilesBytes + imageCacheBytes + aiPreviewBytes;
            if (mode == CleanMode.QUICK) {
                return quick;
            }
            return quick + chatHistoryBytes + offlineModelBytes;
        }
    }

    public static final class StorageInfo {
        public final CategorySize categories;
        public final long phoneUsedBytes;
        public final long phoneTotalBytes;

        StorageInfo(CategorySize categories, long phoneUsedBytes, long phoneTotalBytes) {
            this.categories = categories;
            this.phoneUsedBytes = phoneUsedBytes;
            this.phoneTotalBytes = phoneTotalBytes;
        }
    }

    private ClearCacheHelper() {
    }

    public static StorageInfo scan(Context context) {
        Context app = context.getApplicationContext();
        File cacheDir = app.getCacheDir();
        File extCacheDir = app.getExternalCacheDir();
        File aiHistoryDir = new File(app.getFilesDir(), "ai_history");
        File modelsDir = new File(app.getFilesDir(), "models");

        long imageCache = 0L;
        long aiPreview = 0L;
        long tempFiles = 0L;
        long[] mainSplit = splitCacheDir(cacheDir);
        tempFiles += mainSplit[0];
        imageCache += mainSplit[1];
        aiPreview += mainSplit[2];
        long[] extSplit = splitCacheDir(extCacheDir);
        tempFiles += extSplit[0];
        imageCache += extSplit[1];
        aiPreview += extSplit[2];

        long chatHistory = dirSize(aiHistoryDir);
        long offlineModels = dirSize(modelsDir);

        CategorySize categories = new CategorySize(tempFiles, imageCache, aiPreview, chatHistory, offlineModels);
        long phoneTotal = 0L;
        long phoneFree = 0L;
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            phoneTotal = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
            phoneFree = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
        } catch (Exception ignored) {
        }
        long phoneUsed = Math.max(0L, phoneTotal - phoneFree);
        return new StorageInfo(categories, phoneUsed, phoneTotal);
    }

    public static long clear(Context context, CleanMode mode) {
        Context app = context.getApplicationContext();
        long freed = 0L;
        freed += clearCacheTree(app.getCacheDir());
        File extCache = app.getExternalCacheDir();
        if (extCache != null) {
            freed += clearCacheTree(extCache);
        }
        if (mode == CleanMode.DEEP) {
            freed += deleteDirContents(new File(app.getFilesDir(), "ai_history"));
            freed += deleteDirContents(new File(app.getFilesDir(), "models"));
        }
        return freed;
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0L) {
            return "0 MB";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0);
        }
        if (mb >= 100.0) {
            return String.format(Locale.getDefault(), "%.0f MB", mb);
        }
        if (mb >= 10.0) {
            return String.format(Locale.getDefault(), "%.1f MB", mb);
        }
        return String.format(Locale.getDefault(), "%.1f MB", mb);
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long total = 0L;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0L;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                total += dirSize(file);
            } else {
                total += file.length();
            }
        }
        return total;
    }

    private static long[] splitCacheDir(File dir) {
        long temp = 0L;
        long image = 0L;
        long preview = 0L;
        if (dir == null || !dir.exists()) {
            return new long[] {temp, image, preview};
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return new long[] {temp, image, preview};
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (isPreviewDir(file.getName())) {
                    preview += dirSize(file);
                } else {
                    long[] nested = splitCacheDir(file);
                    temp += nested[0];
                    image += nested[1];
                    preview += nested[2];
                }
            } else {
                long len = file.length();
                if (isImageCacheFile(file.getName())) {
                    image += len;
                } else if (isPreviewFile(file.getName())) {
                    preview += len;
                } else {
                    temp += len;
                }
            }
        }
        return new long[] {temp, image, preview};
    }

    private static boolean isImageCacheFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("insert_img_")
                || lower.startsWith("avatar_")
                || lower.startsWith("text_extract_")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }

    private static boolean isPreviewFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("ai_preview") || lower.startsWith("ai_gen_");
    }

    private static boolean isPreviewDir(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("preview") || lower.contains("ai_gen");
    }

    private static long clearCacheTree(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long freed = 0L;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    freed += deleteDirContents(file);
                } else {
                    freed += file.length();
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        return freed;
    }

    private static long deleteDirContents(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long freed = 0L;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    freed += deleteDirContents(file);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                } else {
                    freed += file.length();
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        return freed;
    }
}
