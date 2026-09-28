package org.libreoffice.androidapp.feedback;

/** 反馈 HTTP / 业务 code 非 200。 */
public final class FeedbackApiException extends Exception {

    public final String reason;
    public final int httpCode;
    public final int apiCode;

    public FeedbackApiException(String reason, String message) {
        this(reason, message, 0, 0);
    }

    public FeedbackApiException(String reason, String message, int httpCode, int apiCode) {
        super(message);
        this.reason = reason;
        this.httpCode = httpCode;
        this.apiCode = apiCode;
    }
}
