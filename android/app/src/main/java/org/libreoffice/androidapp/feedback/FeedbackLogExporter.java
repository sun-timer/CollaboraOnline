package org.libreoffice.androidapp.feedback;

import android.content.Context;

import java.io.File;

/**
 * 应用日志导出（供 {@link FeedbackConfig#PATH_UPLOAD_LOG} 联调）。
 * 后端定稿前 UI 勾选日志会 Toast 拦截，不调用本类。
 */
public final class FeedbackLogExporter {

    private FeedbackLogExporter() {
    }

    /**
     * @return 待上传的日志 zip；尚未实现时返回 null
     */
    public static File exportZip(Context context) {
        return null;
    }
}
