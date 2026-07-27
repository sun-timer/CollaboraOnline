package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.libreoffice.androidlib.LOActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
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

    public static CatalogEntry getDefaultCatalogEntry() {
        return new CatalogEntry(
                "qwen2.5-1.5b-q4",
                "Qwen2.5-1.5B-Instruct",
                "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                1100000000L,
                "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "");
    }

    public static boolean isDeviceSupported(Context context) {
        if (Build.SUPPORTED_ABIS.length == 0 || !"arm64-v8a".equals(Build.SUPPORTED_ABIS[0])) {
            return false;
        }
        return LocalMemoryProbe.totalRamBytes(context) >= 4L * 1024 * 1024 * 1024;
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

    public boolean canUseLocalInference() {
        return isDeviceSupported() && isInstalled() && isEnabled();
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

        long freeSpace = appContext.getFilesDir().getFreeSpace();
        if (freeSpace < (long) (entry.sizeBytes * 1.2f)) {
            if (callback != null) {
                callback.onError("storage_insufficient", "存储空间不足");
            }
            return;
        }

        prefs.edit().putString(KEY_DOWNLOAD_STATE, STATE_DOWNLOADING).apply();
        downloadExecutor.execute(() -> {
            File dest = new File(getModelsDir(), entry.fileName);
            File tmp = new File(dest.getAbsolutePath() + ".part");
            HttpURLConnection connection = null;
            try {
                URL url = new URL(entry.url);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(120000);
                long existing = tmp.exists() ? tmp.length() : 0L;
                if (existing > 0) {
                    connection.setRequestProperty("Range", "bytes=" + existing + "-");
                }
                connection.connect();
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
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
                    total = existing + connection.getContentLengthLong();
                }

                InputStream in = connection.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp, existing > 0);
                byte[] buffer = new byte[8192];
                long downloaded = existing;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (callback != null && total > 0) {
                        int percent = (int) Math.min(100, downloaded * 100 / total);
                        callback.onProgress(percent, downloaded, total);
                    }
                }
                out.flush();
                out.close();
                in.close();

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
                        prefs.edit().putString(KEY_DOWNLOAD_STATE, STATE_ERROR).apply();
                        Log.e(TAG, "local_sha256_mismatch expected=" + entry.sha256 + " actual=" + actual);
                        if (callback != null) {
                            callback.onError("local_sha256_mismatch", "模型校验失败");
                        }
                        return;
                    }
                }

                prefs.edit()
                        .putString(KEY_MODEL_ID, entry.id)
                        .putString(KEY_MODEL_PATH, dest.getAbsolutePath())
                        .putString(KEY_SHA256, entry.sha256)
                        .putString(KEY_DOWNLOAD_STATE, STATE_READY)
                        .apply();
                Log.i(TAG, "local_download_ok path=" + dest.getAbsolutePath());
                if (callback != null) {
                    callback.onComplete(true, dest.getAbsolutePath());
                }
            } catch (Exception e) {
                prefs.edit().putString(KEY_DOWNLOAD_STATE, STATE_ERROR).apply();
                Log.e(TAG, "local_download_fail reason=" + e.getMessage(), e);
                if (callback != null) {
                    callback.onError("download_failed", e.getMessage());
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
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
