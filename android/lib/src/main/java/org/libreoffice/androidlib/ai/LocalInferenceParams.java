package org.libreoffice.androidlib.ai;

import android.content.Context;

public final class LocalInferenceParams {
    public final int contextSize;
    public final int maxTokens;
    public final int threads;

    public LocalInferenceParams(int contextSize, int maxTokens, int threads) {
        this.contextSize = contextSize;
        this.maxTokens = maxTokens;
        this.threads = threads;
    }

    public static LocalInferenceParams defaults() {
        return new LocalInferenceParams(4096, 1024, 4);
    }

    public static LocalInferenceParams fromDevice(Context context) {
        long totalRam = LocalMemoryProbe.totalRamBytes(context);
        boolean highRam = totalRam >= 8L * 1024 * 1024 * 1024;
        return new LocalInferenceParams(
                highRam ? 4096 : 2048,
                highRam ? 1024 : 512,
                4);
    }
}
