package org.libreoffice.androidapp.feedback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * V1.2 反馈 HTTP 客户端（{@link HttpURLConnection}）。
 */
final class FeedbackClient {

    private static final String TAG = "LOActivity";
    private static final int CONNECT_MS = 30_000;
    private static final int READ_MS = 60_000;
    private static String newBoundary() {
        return "----aioffice-" + System.currentTimeMillis();
    }

    private FeedbackClient() {
    }

    static String uploadAvatar(Context context, String userId, Uri fileUri)
            throws FeedbackApiException {
        return postMultipartSingle(context, FeedbackConfig.PATH_UPLOAD_AVATAR,
                userId, "file", fileUri);
    }

    static List<String> uploadImages(Context context, List<Uri> uris)
            throws FeedbackApiException {
        if (uris == null || uris.isEmpty()) {
            throw new FeedbackApiException("feedback_upload_empty", "files empty");
        }
        long tAll = SystemClock.elapsedRealtime();
        Log.i(TAG, "feedback_upload_images start count=" + uris.size());
        String boundary = newBoundary();
        long tBuild = SystemClock.elapsedRealtime();
        byte[] body = buildMultipartImages(context, boundary, uris);
        long buildMs = SystemClock.elapsedRealtime() - tBuild;
        long tHttp = SystemClock.elapsedRealtime();
        JSONObject root = postBytes(FeedbackConfig.PATH_UPLOAD_IMAGES,
                "multipart/form-data; boundary=" + boundary, body);
        long httpMs = SystemClock.elapsedRealtime() - tHttp;
        JSONArray data = root.optJSONArray("data");
        List<String> paths = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                paths.add(data.optString(i));
            }
        }
        Log.i(TAG, "feedback_upload_images ok buildMs=" + buildMs + " httpMs=" + httpMs
                + " totalBytes=" + body.length + " pathCount=" + paths.size()
                + " totalMs=" + (SystemClock.elapsedRealtime() - tAll));
        return paths;
    }

    static String uploadLog(Context context, String userId, File logFile)
            throws FeedbackApiException {
        if (logFile == null || !logFile.isFile()) {
            throw new FeedbackApiException("feedback_log_upload", "missing file");
        }
        long size = logFile.length();
        if (size <= 0 || size > FeedbackConfig.MAX_LOG_BYTES) {
            throw new FeedbackApiException("feedback_log_upload", "file size");
        }
        String boundary = newBoundary();
        byte[] body = buildMultipartLogFile(boundary, userId, logFile);
        JSONObject root = postBytes(FeedbackConfig.PATH_UPLOAD_LOG,
                "multipart/form-data; boundary=" + boundary, body);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new FeedbackApiException("feedback_log_upload", "missing data");
        }
        return data.optString("logPath", data.optString("path"));
    }

    static FeedbackRecord submit(Context context, String userId, String nickname,
            String avatarPath, String feedbackType, String content, String contact,
            List<String> imagePaths, String logPath, String appVersion, String deviceModel,
            String osVersion) throws FeedbackApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("userId", userId);
            if (nickname != null && !nickname.isEmpty()) {
                body.put("nickname", nickname);
            }
            if (avatarPath != null && !avatarPath.isEmpty()) {
                body.put("avatar", avatarPath);
            }
            body.put("feedbackType", feedbackType);
            body.put("content", content);
            if (contact != null && !contact.isEmpty()) {
                body.put("contact", contact);
            }
            if (imagePaths != null && !imagePaths.isEmpty()) {
                body.put("imagePaths", new JSONArray(imagePaths));
            }
            if (logPath != null && !logPath.isEmpty()) {
                body.put("logPath", logPath);
            }
            if (appVersion != null && !appVersion.isEmpty()) {
                body.put("appVersion", appVersion);
            }
            if (deviceModel != null && !deviceModel.isEmpty()) {
                body.put("deviceModel", deviceModel);
            }
            if (osVersion != null && !osVersion.isEmpty()) {
                body.put("osVersion", osVersion);
            }
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_json_build", e.getMessage());
        }
        Log.i(TAG, "feedback submit request feedbackType=" + feedbackType
                + " contentLen=" + content.codePointCount(0, content.length()));
        JSONObject data = postJson(FeedbackConfig.PATH_SUBMIT, body).optJSONObject("data");
        if (data == null) {
            throw new FeedbackApiException("feedback_parse", "missing data");
        }
        FeedbackRecord record = new FeedbackRecord();
        record.id = data.optString("feedbackNo");
        record.type = feedbackType;
        record.content = content;
        record.contact = contact != null ? contact : "";
        record.status = FeedbackRecord.Status.fromApiLabel(data.optString("status", "已提交"));
        record.submitTime = parseServerTime(data.optString("submitTime"));
        if (imagePaths != null) {
            record.imageUris.addAll(imagePaths);
        }
        return record;
    }

    static FeedbackListPage list(Context context, String userId, String nickname,
            String avatarPath, int pageNum, int pageSize) throws FeedbackApiException {
        JSONObject body = baseUserBody(userId, nickname, avatarPath);
        try {
            body.put("pageNum", pageNum);
            body.put("pageSize", pageSize);
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_json_build", e.getMessage());
        }
        JSONObject data = postJson(FeedbackConfig.PATH_LIST, body).optJSONObject("data");
        if (data == null) {
            throw new FeedbackApiException("feedback_parse", "missing data");
        }
        FeedbackListPage page = new FeedbackListPage();
        page.pageNum = data.optInt("pageNum", pageNum);
        page.pageSize = data.optInt("pageSize", pageSize);
        page.total = data.optInt("total", 0);
        page.pages = data.optInt("pages", 0);
        JSONArray list = data.optJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                FeedbackRecord r = new FeedbackRecord();
                r.id = item.optString("feedbackNo");
                r.type = item.optString("feedbackType");
                r.content = item.optString("contentSummary");
                r.status = FeedbackRecord.Status.fromApiLabel(item.optString("status"));
                r.submitTime = parseServerTime(item.optString("submitTime"));
                page.list.add(r);
            }
        }
        return page;
    }

    static FeedbackRecord detail(Context context, String userId, String nickname,
            String avatarPath, String feedbackNo) throws FeedbackApiException {
        JSONObject body = baseUserBody(userId, nickname, avatarPath);
        try {
            body.put("feedbackNo", feedbackNo);
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_json_build", e.getMessage());
        }
        JSONObject data = postJson(FeedbackConfig.PATH_DETAIL, body).optJSONObject("data");
        if (data == null) {
            throw new FeedbackApiException("feedback_parse", "missing data");
        }
        return FeedbackRecord.fromDetailJson(data);
    }

    static void close(Context context, String userId, String nickname,
            String avatarPath, String feedbackNo) throws FeedbackApiException {
        JSONObject body = baseUserBody(userId, nickname, avatarPath);
        try {
            body.put("feedbackNo", feedbackNo);
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_json_build", e.getMessage());
        }
        postJson(FeedbackConfig.PATH_CLOSE, body);
    }

    private static JSONObject baseUserBody(String userId, String nickname, String avatarPath)
            throws FeedbackApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("userId", userId);
            if (nickname != null && !nickname.isEmpty()) {
                body.put("nickname", nickname);
            }
            if (avatarPath != null && !avatarPath.isEmpty()) {
                body.put("avatar", avatarPath);
            }
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_json_build", e.getMessage());
        }
        return body;
    }

    private static String postMultipartSingle(Context context, String path, String userId,
            String fileField, Uri uri) throws FeedbackApiException {
        String boundary = newBoundary();
        byte[] body = buildMultipartSingle(context, boundary, userId, fileField, uri);
        JSONObject root = postBytes(path, "multipart/form-data; boundary=" + boundary, body);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new FeedbackApiException("feedback_upload_parse", "missing data");
        }
        return data.optString("avatar", data.optString("path"));
    }

    private static byte[] buildMultipartSingle(Context context, String boundary, String userId,
            String fileField, Uri uri) throws FeedbackApiException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeField(out, boundary, "userId", userId);
            writeFileField(out, boundary, fileField, uri, context);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (FeedbackApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_multipart", e.getMessage());
        }
        return out.toByteArray();
    }

    private static byte[] buildMultipartLogFile(String boundary, String userId, File logFile)
            throws FeedbackApiException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeField(out, boundary, "userId", userId);
            writeFileFieldBytes(out, boundary, "file", logFile.getName(),
                    "text/plain", readFileBytes(logFile));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (FeedbackApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_multipart", e.getMessage());
        }
        return out.toByteArray();
    }

    private static byte[] readFileBytes(File file) throws FeedbackApiException {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_read_file", e.getMessage());
        }
    }

    private static void writeFileFieldBytes(ByteArrayOutputStream out, String boundary,
            String fieldName, String fileName, String mime, byte[] bytes) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] buildMultipartImages(Context context, String boundary, List<Uri> uris)
            throws FeedbackApiException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int i = 0; i < uris.size(); i++) {
                UploadImagePart part = prepareUploadImage(context, uris.get(i), i);
                writeFileFieldBytes(out, boundary, "files", part.fileName,
                        part.mime, part.bytes);
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (FeedbackApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_multipart", e.getMessage());
        }
        return out.toByteArray();
    }

    private static final class UploadImagePart {
        final byte[] bytes;
        final String fileName;
        final String mime;

        UploadImagePart(byte[] bytes, String fileName, String mime) {
            this.bytes = bytes;
            this.fileName = fileName;
            this.mime = mime;
        }
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name,
            String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFileField(ByteArrayOutputStream out, String boundary,
            String fieldName, Uri uri, Context context) throws FeedbackApiException {
        String fileName = "upload.jpg";
        String mime = "image/jpeg";
        byte[] bytes = readUriBytes(context, uri);
        String path = uri.getLastPathSegment();
        if (path != null) {
            fileName = path;
            String lower = path.toLowerCase(Locale.US);
            if (lower.endsWith(".png")) {
                mime = "image/png";
            } else if (lower.endsWith(".gif")) {
                mime = "image/gif";
            } else if (lower.endsWith(".webp")) {
                mime = "image/webp";
            } else if (lower.endsWith(".bmp")) {
                mime = "image/bmp";
            }
        }
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fieldName
                    + "\"; filename=\"" + fileName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_multipart_file", e.getMessage());
        }
    }

    private static byte[] readUriBytes(Context context, Uri uri) throws FeedbackApiException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new FeedbackApiException("feedback_read_uri", "openInputStream null");
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } catch (FeedbackApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_read_uri", e.getMessage());
        }
    }

    private static UploadImagePart prepareUploadImage(Context context, Uri uri, int index)
            throws FeedbackApiException {
        long t0 = SystemClock.elapsedRealtime();
        String baseName = uploadImageBaseName(uri);
        int maxEdge = FeedbackConfig.UPLOAD_IMAGE_MAX_EDGE_PX;
        int quality = FeedbackConfig.UPLOAD_IMAGE_JPEG_QUALITY;
        try {
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            try (InputStream boundsIn = context.getContentResolver().openInputStream(uri)) {
                if (boundsIn == null) {
                    throw new FeedbackApiException("feedback_read_uri", "openInputStream null");
                }
                BitmapFactory.decodeStream(boundsIn, null, boundsOpts);
            }
            int srcW = boundsOpts.outWidth;
            int srcH = boundsOpts.outHeight;
            if (srcW <= 0 || srcH <= 0) {
                return fallbackUploadImagePart(context, uri, index, baseName, t0, "invalid_bounds");
            }
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSizeForMaxEdge(srcW, srcH, maxEdge);
            Bitmap bitmap;
            try (InputStream decodeIn = context.getContentResolver().openInputStream(uri)) {
                if (decodeIn == null) {
                    throw new FeedbackApiException("feedback_read_uri", "openInputStream null");
                }
                bitmap = BitmapFactory.decodeStream(decodeIn, null, decodeOpts);
            }
            if (bitmap == null) {
                return fallbackUploadImagePart(context, uri, index, baseName, t0, "decode_null");
            }
            long readMs = SystemClock.elapsedRealtime() - t0;
            long tCompress = SystemClock.elapsedRealtime();
            Bitmap toCompress = scaleBitmapToMaxEdge(bitmap, maxEdge);
            if (toCompress != bitmap) {
                bitmap.recycle();
            }
            ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
            if (!toCompress.compress(Bitmap.CompressFormat.JPEG, quality, jpegOut)) {
                toCompress.recycle();
                return fallbackUploadImagePart(context, uri, index, baseName, t0, "compress_fail");
            }
            toCompress.recycle();
            byte[] outBytes = jpegOut.toByteArray();
            long compressMs = SystemClock.elapsedRealtime() - tCompress;
            Log.i(TAG, "feedback_upload_images read_uri i=" + index + " src=" + srcW + "x" + srcH
                    + " readMs=" + readMs + " compressMs=" + compressMs
                    + " outBytes=" + outBytes.length + " reason=jpeg");
            return new UploadImagePart(outBytes, jpegFileName(baseName), "image/jpeg");
        } catch (FeedbackApiException e) {
            throw e;
        } catch (Exception e) {
            return fallbackUploadImagePart(context, uri, index, baseName, t0, e.getMessage());
        }
    }

    private static UploadImagePart fallbackUploadImagePart(Context context, Uri uri, int index,
            String baseName, long t0, String reason) throws FeedbackApiException {
        byte[] raw = readUriBytes(context, uri);
        long readMs = SystemClock.elapsedRealtime() - t0;
        String mime = mimeFromFileName(baseName);
        Log.i(TAG, "feedback_upload_images read_uri i=" + index + " readMs=" + readMs
                + " outBytes=" + raw.length + " reason=fallback_" + reason);
        return new UploadImagePart(raw, baseName, mime);
    }

    private static int sampleSizeForMaxEdge(int width, int height, int maxEdge) {
        int maxDim = Math.max(width, height);
        int sample = 1;
        while (maxDim / sample > maxEdge * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleBitmapToMaxEdge(Bitmap bitmap, int maxEdge) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int maxDim = Math.max(w, h);
        if (maxDim <= maxEdge) {
            return bitmap;
        }
        float scale = (float) maxEdge / maxDim;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true);
    }

    private static String uploadImageBaseName(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null || path.isEmpty()) {
            return "upload.jpg";
        }
        return path;
    }

    private static String jpegFileName(String baseName) {
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        return stem + ".jpg";
    }

    private static String mimeFromFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    private static JSONObject postJson(String path, JSONObject body) throws FeedbackApiException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        return postBytes(path, "application/json; charset=UTF-8", bytes);
    }

    private static JSONObject postBytes(String path, String contentType, byte[] body)
            throws FeedbackApiException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(FeedbackConfig.endpoint(path));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_MS);
            connection.setReadTimeout(READ_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }
            int http = connection.getResponseCode();
            String text = readStream(http >= 200 && http < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            JSONObject root;
            try {
                root = new JSONObject(text);
            } catch (Exception e) {
                Log.w(TAG, "feedback_core_fail reason=feedback_parse http=" + http
                        + " body=" + truncate(text));
                throw new FeedbackApiException("feedback_parse", "invalid json", http, 0);
            }
            int code = root.optInt("code", 0);
            String msg = root.optString("msg", "");
            if (http < 200 || http >= 300 || code != 200) {
                Log.w(TAG, "feedback_core_fail reason=feedback_api http=" + http
                        + " code=" + code + " msg=" + msg);
                throw new FeedbackApiException("feedback_api", msg, http, code);
            }
            return root;
        } catch (FeedbackApiException e) {
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "feedback_core_fail reason=feedback_network " + e.getMessage());
            throw new FeedbackApiException("feedback_network", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = stream.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    private static long parseServerTime(String text) {
        if (text == null || text.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            Date d = fmt.parse(text);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    static String deviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    static String osVersion() {
        return "Android " + Build.VERSION.RELEASE;
    }

    static final class FeedbackListPage {
        int pageNum;
        int pageSize;
        int total;
        int pages;
        final List<FeedbackRecord> list = new ArrayList<>();
    }
}
