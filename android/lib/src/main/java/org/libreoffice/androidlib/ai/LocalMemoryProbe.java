package org.libreoffice.androidlib.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;

public final class LocalMemoryProbe {
    private LocalMemoryProbe() {}

    public static long[] samplePssBytes() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        long nativePss = info.nativePss * 1024L;
        long totalPss = info.getTotalPss() * 1024L;
        return new long[] {nativePss, totalPss};
    }

    public static long totalRamBytes(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am == null) {
            return 0L;
        }
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }
}
