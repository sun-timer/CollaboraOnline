package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.libreoffice.androidlib.LOActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalModelManager {
    private static final String TAG = "LOActivity";

    public static final String KEY_ENABLED = "AI_MODEL_LOCAL_enabled";
    public static final String KEY_MODEL_ID = "AI_MODEL_LOCAL_model_id";
    public static final String KEY_MODEL_PATH = "AI_MODEL_LOCAL_model_path";
    public static final String KEY_SHA256 = "AI_MODEL_LOCAL_sha256";
    public static final String KEY_DOWNLOAD_STATE = "AI_MODEL_LOCAL_download_state";
    public static final String KEY_DOWNLOADING_MODEL_ID = "AI_MODEL_LOCAL_downloading_model_id";

    public static final String STATE_IDLE = "idle";
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_READY = "ready";
    public static final String STATE_ERROR = "error";

    public interface DownloadProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);

        void onComplete(boolean success, String message);

        void onError(String code, String message);
    }

    public static final class CatalogEntry {
        public final String id;
        public final String displayName;
        public final String fileName;
        public final long sizeBytes;
        public final String url;
        public final String sha256;

        public CatalogEntry(String id, String displayName, String fileName, long sizeBytes, String url,
                String sha256) {
            this.id = id;
            this.displayName = displayName;
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    private static volatile LocalModelManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean engineLoaded;
    private volatile boolean downloadRunning;
    private volatile boolean cancelRequested;
    private volatile int lastProgressPercent;
    private volatile int lastNotifiedProgressPercent = -1;
    private volatile long lastProgressNotifyMs;
    private volatile HttpURLConnection activeConnection;

    private static final long PROGRESS_NOTIFY_MIN_INTERVAL_MS = 500L;

    private static final class DownloadCancelledException extends Exception {
    }

    private LocalModelManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(LOActivity.EXPLORER_PREFS_KEY, Context.MODE_PRIVATE);
    }

    public static LocalModelManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LocalModelManager.class) {
                if (instance == null) {
                    instance = new LocalModelManager(context);
                }
            }
        }
        return instance;
    }

    /** Primary mirror for CN networks; HuggingFace as fallback when mirror is down. */
    private static final String[] QWEN25_15B_MODEL_URLS = {
            "https://hf-mirror.com/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
    };

    private static final String[] QWEN3_06B_MODEL_URLS = {
            "https://hf-mirror.com/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
    };

    private static final String[] QWEN3_17B_MODEL_URLS = {
            "https://hf-mirror.com/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
    };

    private static final String[] GEMMA_1B_MODEL_URLS = {
            "https://hf-mirror.com/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
    };

    private static final String[] GEMMA_4B_MODEL_URLS = {
            "https://hf-mirror.com/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
    };

    public static CatalogEntry getDefaultCatalogEntry() {
        return getQwenCatalogEntry();
    }

    public static CatalogEntry getQwen3_06bCatalogEntry() {
        return new CatalogEntry(
                "qwen3-0.6b-q4",
                "Qwen3-0.6B",
                "Qwen3-0.6B-Q4_K_M.gguf",
                397_000_000L,
                QWEN3_06B_MODEL_URLS[0],
                "");
    }

    public static CatalogEntry getQwenCatalogEntry() {
        return new CatalogEntry(
                "qwen2.5-1.5b-q4",
                "Qwen2.5-1.5B-Instruct",
                "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                1_100_000_000L,
                QWEN25_15B_MODEL_URLS[0],
                "");
    }

    public static CatalogEntry getQwen3_17bCatalogEntry() {
        return new CatalogEntry(
                "qwen3-1.7b-q4",
                "Qwen3-1.7B",
                "Qwen3-1.7B-Q4_K_M.gguf",
                1_107_000_000L,
                QWEN3_17B_MODEL_URLS[0],
                "");
    }

    public static CatalogEntry getGemma1bCatalogEntry() {
        return new CatalogEntry(
                "gemma-3-1b-q4",
                "Gemma 3 1B Instruct",
                "gemma-3-1b-it-Q4_K_M.gguf",
                806_000_000L,
                GEMMA_1B_MODEL_URLS[0],
                "");
    }

    public static CatalogEntry getGemma4bCatalogEntry() {
        return new CatalogEntry(
                "gemma-3-4b-q4",
                "Gemma 3 4B Instruct",
                "gemma-3-4b-it-Q4_K_M.gguf",
                2_800_000_000L,
                GEMMA_4B_MODEL_URLS[0],
                "");
    }

    public static List<CatalogEntry> getCatalogEntries() {
        return Collections.unmodifiableList(Arrays.asList(
                getQwen3_06bCatalogEntry(),
                getQwenCatalogEntry(),
                getQwen3_17bCatalogEntry(),
                getGemma1bCatalogEntry(),
                getGemma4bCatalogEntry()));
    }

    public CatalogEntry getInstalledCatalogEntry() {
        String installedId = prefs.getString(KEY_MODEL_ID, "");
        if (installedId == null || installedId.isEmpty()) {
            return null;
        }
        for (CatalogEntry entry : getCatalogEntries()) {
            if (installedId.equals(entry.id)) {
                return entry;
            }
        }
        return null;
    }

    public String getInstalledModelId() {
        return prefs.getString(KEY_MODEL_ID, "");
    }

    public String getDownloadingModelId() {
        return prefs.getString(KEY_DOWNLOADING_MODEL_ID, "");
    }

    public boolean isEntryInstalled(CatalogEntry entry) {
        return isEntryDownloaded(entry);
    }

    public File getEntryModelFile(CatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        return new File(getModelsDir(), entry.fileName);
    }

    /** 模型文件已下载到本地（与当前选用无关）。 */
    public boolean isEntryDownloaded(CatalogEntry entry) {
        File file = getEntryModelFile(entry);
        return file != null && file.isFile();
    }

    /** 当前用于推理的选用模型。 */
    public boolean isEntryActive(CatalogEntry entry) {
        return entry != null
                && entry.id.equals(getInstalledModelId())
                && isEntryDownloaded(entry);
    }

    public boolean hasAnyDownloadedModel() {
        for (CatalogEntry entry : getCatalogEntries()) {
            if (isEntryDownloaded(entry)) {
                return true;
            }
        }
        return false;
    }

    /** 将已下载模型设为当前选用（不自动开启推理开关）。 */
    public void selectActiveModel(CatalogEntry entry) {
        if (!isEntryDownloaded(entry)) {
            return;
        }
        LocalInferenceEngine.getInstance().unloadModel();
        engineLoaded = false;
        File dest = getEntryModelFile(entry);
        prefs.edit()
                .putString(KEY_MODEL_ID, entry.id)
                .putString(KEY_MODEL_PATH, dest.getAbsolutePath())
                .putString(KEY_SHA256, entry.sha256 != null ? entry.sha256 : "")
                .putString(KEY_DOWNLOAD_STATE, STATE_READY)
                .apply();
    }

    public boolean isEntryDownloading(CatalogEntry entry) {
        return entry != null
                && isDownloadActive()
                && entry.id.equals(getDownloadingModelId());
    }

    public static boolean isDeviceSupported(Context context) {
        return !LocalDeviceCapability.assess(context).isHardBlocked();
    }

    public boolean isDeviceSupported() {
        return isDeviceSupported(appContext);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean isInstalled() {
        String path = getModelPath();
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    public String getModelPath() {
        return prefs.getString(KEY_MODEL_PATH, "");
    }

    public String getDownloadState() {
        return prefs.getString(KEY_DOWNLOAD_STATE, STATE_IDLE);
    }

    /** 是否有真正运行中的下载任务（进程重启后 downloading 状态会残留，但无任务）。 */
    public boolean isDownloadActive() {
        return downloadRunning && STATE_DOWNLOADING.equals(getDownloadState());
    }

    public int getLastProgressPercent() {
        return lastProgressPercent;
    }

    /** 取消当前下载；若没有运行中的任务（如进程被杀后残留 downloading 状态）则立即清理。 */
    public void cancelDownload() {
        cancelRequested = true;
        if (!downloadRunning) {
            cleanupIfInterrupted();
        } else if (activeConnection != null) {
            // 打断阻塞在 read() 的下载线程，使其尽快感知取消
            activeConnection.disconnect();
        }
    }

    private void cleanupIfInterrupted() {
        if (!STATE_DOWNLOADING.equals(getDownloadState())) {
            return;
        }
        cleanupInterruptedDownload();
    }

    private void cleanupInterruptedDownload() {
        String downloadingId = getDownloadingModelId();
        String fileName = null;
        for (CatalogEntry entry : getCatalogEntries()) {
            if (downloadingId.equals(entry.id)) {
                fileName = entry.fileName;
                break;
            }
        }
        if (fileName != null) {
            File partial = new File(getModelsDir(), fileName + ".part");
            if (partial.exists()) {
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
            }
        }
        prefs.edit()
                .putString(KEY_DOWNLOAD_STATE, STATE_IDLE)
                .remove(KEY_DOWNLOADING_MODEL_ID)
                .apply();
    }

    public boolean canUseLocalInference() {
        return isDeviceSupported() && isInstalled() && isEnabled()
                && LocalInferenceEngine.isNativeAvailable();
    }

    public boolean isModelLoadedInEngine() {
        return engineLoaded && LocalInferenceEngine.getInstance().isModelLoaded();
    }

    public void markEngineLoaded(boolean loaded) {
        engineLoaded = loaded;
    }

    public AiBackendRouter.LocalModelState getState() {
        return new AiBackendRouter.LocalModelState(isDeviceSupported(), isInstalled(), isEnabled());
    }

    public File getModelsDir() {
        File dir = new File(appContext.getFilesDir(), "models");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public void deleteModel() {
        String path = getModelPath();
        if (path != null && !path.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
        LocalInferenceEngine.getInstance().unloadModel();
        engineLoaded = false;
        prefs.edit()
                .remove(KEY_MODEL_PATH)
                .remove(KEY_SHA256)
                .remove(KEY_MODEL_ID)
                .remove(KEY_DOWNLOADING_MODEL_ID)
                .putString(KEY_DOWNLOAD_STATE, STATE_IDLE)
                .putBoolean(KEY_ENABLED, false)
                .apply();
    }

    public void downloadDefaultModel(DownloadProgressCallback callback) {
        downloadModel(getDefaultCatalogEntry(), callback);
    }

    public void downloadModel(CatalogEntry entry, DownloadProgressCallback callback) {
        if (!isDeviceSupported()) {
            if (callback != null) {
                callback.onError("device_unsupported", "当前设备不支持本地推理");
            }
            return;
        }

        LocalDeviceCapability capability = LocalDeviceCapability.assess(appContext);
        if (!capability.canDownloadModel(entry)) {
            if (callback != null) {
                callback.onError("model_too_heavy", getModelCapabilityMessage(capability, entry));
            }
            return;
        }

        long freeSpace = appContext.getFilesDir().getFreeSpace();
        if (freeSpace < (long) (entry.sizeBytes * 1.2f)) {
            if (callback != null) {
                callback.onError("storage_insufficient", "存储空间不足");
            }
            return;
        }

        synchronized (this) {
            if (downloadRunning) {
                if (callback != null) {
                    callback.onError("download_busy", "已有模型正在下载");
                }
                return;
            }
            downloadRunning = true;
        }

        if (isEntryDownloaded(entry)) {
            synchronized (this) {
                downloadRunning = false;
            }
            if (callback != null) {
                callback.onComplete(true, getEntryModelFile(entry).getAbsolutePath());
            }
            return;
        }

        cancelRequested = false;
        lastProgressPercent = 0;
        lastNotifiedProgressPercent = -1;
        lastProgressNotifyMs = 0L;
        prefs.edit()
                .putString(KEY_DOWNLOAD_STATE, STATE_DOWNLOADING)
                .putString(KEY_DOWNLOADING_MODEL_ID, entry.id)
                .apply();
        downloadExecutor.execute(() -> {
            try {
                String[] urls = resolveDownloadUrls(entry);
                Exception lastError = null;
                for (int i = 0; i < urls.length; i++) {
                    String url = urls[i];
                    Log.i(TAG, "local_download_try url_index=" + i + " url=" + url);
                    try {
                        downloadModelFromUrl(entry, url, callback);
                        return;
                    } catch (DownloadCancelledException e) {
                        cleanupIfInterrupted();
                        if (callback != null) {
                            callback.onError("cancelled", "下载已取消");
                        }
                        return;
                    } catch (Exception e) {
                        if (cancelRequested) {
                            cleanupIfInterrupted();
                            if (callback != null) {
                                callback.onError("cancelled", "下载已取消");
                            }
                            return;
                        }
                        lastError = e;
                        File partial = new File(getModelsDir(), entry.fileName + ".part");
                        if (partial.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            partial.delete();
                        }
                        Log.w(TAG, "local_download_fail url_index=" + i + " reason=" + e.getMessage());
                    }
                }
                prefs.edit()
                        .putString(KEY_DOWNLOAD_STATE, STATE_ERROR)
                        .remove(KEY_DOWNLOADING_MODEL_ID)
                        .apply();
                Log.e(TAG, "local_download_fail reason=all_urls_failed", lastError);
                if (callback != null) {
                    String message = lastError != null && lastError.getMessage() != null
                            ? lastError.getMessage()
                            : "all_urls_failed";
                    callback.onError("download_failed", message);
                }
            } finally {
                downloadRunning = false;
            }
        });
    }

    private static String[] resolveDownloadUrls(CatalogEntry entry) {
        if (entry == null) {
            return QWEN25_15B_MODEL_URLS;
        }
        switch (entry.id) {
            case "qwen3-0.6b-q4":
                return QWEN3_06B_MODEL_URLS;
            case "qwen2.5-1.5b-q4":
                return QWEN25_15B_MODEL_URLS;
            case "qwen3-1.7b-q4":
                return QWEN3_17B_MODEL_URLS;
            case "gemma-3-1b-q4":
                return GEMMA_1B_MODEL_URLS;
            case "gemma-3-4b-q4":
                return GEMMA_4B_MODEL_URLS;
            default:
                break;
        }
        if (entry.url == null || entry.url.isEmpty()) {
            return new String[] {QWEN25_15B_MODEL_URLS[0]};
        }
        return new String[] {entry.url};
    }

    private void downloadModelFromUrl(CatalogEntry entry, String urlString, DownloadProgressCallback callback)
            throws Exception {
        File dest = new File(getModelsDir(), entry.fileName);
        File tmp = new File(dest.getAbsolutePath() + ".part");
        HttpURLConnection connection = null;
        InputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            activeConnection = connection;
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000);
            long existing = tmp.exists() ? tmp.length() : 0L;
            if (existing > 0) {
                connection.setRequestProperty("Range", "bytes=" + existing + "-");
            }
            connection.connect();
            int code = connection.getResponseCode();
            if (existing > 0) {
                if (code == HttpURLConnection.HTTP_PARTIAL) {
                    // resume append
                } else if (code == HttpURLConnection.HTTP_OK) {
                    // 镜像忽略 Range 返回全量 — 丢弃 .part 从头写，避免 append 损坏
                    existing = 0L;
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                } else {
                    throw new IllegalStateException("http_" + code);
                }
            } else if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("http_" + code);
            }

            long total = entry.sizeBytes;
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null && contentRange.contains("/")) {
                try {
                    total = Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1));
                } catch (NumberFormatException ignored) {
                }
            } else {
                long contentLength = connection.getContentLengthLong();
                total = existing + (contentLength > 0 ? contentLength : 0);
            }

            in = connection.getInputStream();
            out = new java.io.FileOutputStream(tmp, existing > 0);
            byte[] buffer = new byte[8192];
            long downloaded = existing;
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelRequested) {
                    throw new DownloadCancelledException();
                }
                out.write(buffer, 0, read);
                downloaded += read;
                if (total > 0) {
                    int percent = (int) Math.min(100, downloaded * 100 / total);
                    notifyDownloadProgress(callback, percent, downloaded, total);
                }
            }
            out.flush();

            if (dest.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
            }
            if (!tmp.renameTo(dest)) {
                throw new IllegalStateException("rename_failed");
            }

            if (entry.sha256 != null && !entry.sha256.isEmpty()) {
                String actual = sha256Hex(dest);
                if (!entry.sha256.equalsIgnoreCase(actual)) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                    throw new IllegalStateException("local_sha256_mismatch");
                }
            }

            prefs.edit()
                    .putString(KEY_DOWNLOAD_STATE, STATE_IDLE)
                    .remove(KEY_DOWNLOADING_MODEL_ID)
                    .apply();
            Log.i(TAG, "local_download_ok path=" + dest.getAbsolutePath() + " url=" + urlString);
            if (callback != null) {
                callback.onComplete(true, dest.getAbsolutePath());
            }
        } finally {
            activeConnection = null;
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void notifyDownloadProgress(DownloadProgressCallback callback, int percent, long downloaded,
            long total) {
        lastProgressPercent = percent;
        if (callback == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (percent >= 100
                || percent != lastNotifiedProgressPercent
                || now - lastProgressNotifyMs >= PROGRESS_NOTIFY_MIN_INTERVAL_MS) {
            lastNotifiedProgressPercent = percent;
            lastProgressNotifyMs = now;
            callback.onProgress(percent, downloaded, total);
        }
    }

    public static String getModelCapabilityMessage(LocalDeviceCapability capability,
            CatalogEntry entry) {
        if (capability == null || entry == null) {
            return "当前设备不适合该模型";
        }
        if (capability.totalRamBytes < capability.getMinRamBytesForModel(entry)) {
            return "内存不足，建议使用 "
                    + LocalDeviceCapability.formatBytesShort(capability.getMinRamBytesForModel(entry))
                    + " 以上设备运行 " + entry.displayName;
        }
        long requiredStorage = (long) (entry.sizeBytes * 1.5f) + 500L * 1024L * 1024L;
        if (capability.freeStorageBytes < requiredStorage) {
            return "存储空间不足，请清理后再下载 " + entry.displayName;
        }
        return "当前设备不适合该模型";
    }

    public static String sha256Hex(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        in.close();
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
