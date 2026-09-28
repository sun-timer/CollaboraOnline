package org.libreoffice.androidapp.feedback;

import android.content.Context;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 导出本进程 logcat 供 {@link FeedbackConfig#PATH_UPLOAD_LOG} 上传。
 */
public final class FeedbackLogExporter {

    private FeedbackLogExporter() {
    }

    /**
     * @return 缓存目录下的 .txt 文件；调用方上传后应删除
     */
    public static File exportLogFile(Context context) throws FeedbackApiException {
        Context app = context.getApplicationContext();
        int pid = Process.myPid();
        Process process;
        try {
            process = new ProcessBuilder(
                    "logcat", "-d", "-v", "threadtime", "--pid", String.valueOf(pid))
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception e) {
            throw new FeedbackApiException("feedback_log_export", e.getMessage());
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outFile = new File(app.getCacheDir(), "feedback_log_" + stamp + ".txt");
        long written = 0;
        try (InputStream raw = process.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(raw, StandardCharsets.UTF_8));
                FileOutputStream fos = new FileOutputStream(outFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                byte[] chunk = (line + "\n").getBytes(StandardCharsets.UTF_8);
                if (written + chunk.length > FeedbackConfig.MAX_LOG_BYTES) {
                    break;
                }
                fos.write(chunk);
                written += chunk.length;
            }
            process.waitFor();
        } catch (Exception e) {
            if (outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            throw new FeedbackApiException("feedback_log_export", e.getMessage());
        }
        if (written == 0) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            throw new FeedbackApiException("feedback_log_empty", "no log lines");
        }
        return outFile;
    }
}
