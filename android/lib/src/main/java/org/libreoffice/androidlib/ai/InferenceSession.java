package org.libreoffice.androidlib.ai;

import java.util.concurrent.atomic.AtomicBoolean;

public final class InferenceSession {
    public final String requestId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public InferenceSession(String requestId) {
        this.requestId = requestId == null ? "" : requestId;
    }

    public void resetCancelled() {
        cancelled.set(false);
    }

    public void requestCancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public long cancelFlagAddress() {
        // JNI reads std::atomic<bool> via pointer; AtomicBoolean uses internal state on ART.
        // LocalInferenceEngine passes session reference to native via cancel check on Java side only.
        return 0L;
    }
}
