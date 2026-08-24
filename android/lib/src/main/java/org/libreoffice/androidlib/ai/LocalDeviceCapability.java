package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.os.Build;

import java.util.Locale;

/** 本机配置评估：硬门槛（架构/内存）+ 按模型的软门槛。 */
public final class LocalDeviceCapability {
    private static final long GB = 1024L * 1024L * 1024L;
    private static final long MIN_DEVICE_RAM_BYTES = 4L * GB;
    private static final long MIN_STORAGE_HEADROOM_BYTES = 500L * 1024L * 1024L;

    public enum RamTier {
        UNSUPPORTED,
        LOW,
        MEDIUM,
        HIGH
    }

    public final String primaryAbi;
    public final boolean arm64;
    public final String cpuLabel;
    public final int coreCount;
    public final long totalRamBytes;
    public final long freeStorageBytes;
    public final RamTier ramTier;
    public final boolean slowCpuWarning;

    private LocalDeviceCapability(String primaryAbi, boolean arm64, String cpuLabel, int coreCount,
            long totalRamBytes, long freeStorageBytes, RamTier ramTier, boolean slowCpuWarning) {
        this.primaryAbi = primaryAbi;
        this.arm64 = arm64;
        this.cpuLabel = cpuLabel;
        this.coreCount = coreCount;
        this.totalRamBytes = totalRamBytes;
        this.freeStorageBytes = freeStorageBytes;
        this.ramTier = ramTier;
        this.slowCpuWarning = slowCpuWarning;
    }

    public static LocalDeviceCapability assess(Context context) {
        Context appContext = context.getApplicationContext();
        String primaryAbi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        boolean arm64 = "arm64-v8a".equals(primaryAbi);
        int coreCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        long totalRamBytes = LocalMemoryProbe.totalRamBytes(appContext);
        long freeStorageBytes = appContext.getFilesDir().getFreeSpace();
        RamTier ramTier = classifyRamTier(arm64, totalRamBytes);
        boolean slowCpuWarning = coreCount < 4;
        return new LocalDeviceCapability(primaryAbi, arm64, resolveCpuLabel(), coreCount, totalRamBytes,
                freeStorageBytes, ramTier, slowCpuWarning);
    }

    private static RamTier classifyRamTier(boolean arm64, long totalRamBytes) {
        if (!arm64 || totalRamBytes < MIN_DEVICE_RAM_BYTES) {
            return RamTier.UNSUPPORTED;
        }
        if (totalRamBytes < 6L * GB) {
            return RamTier.LOW;
        }
        if (totalRamBytes < 8L * GB) {
            return RamTier.MEDIUM;
        }
        return RamTier.HIGH;
    }

    private static String resolveCpuLabel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String soc = Build.SOC_MODEL;
            if (soc != null && !soc.isEmpty()) {
                return soc;
            }
        }
        String hardware = Build.HARDWARE;
        if (hardware != null && !hardware.isEmpty()) {
            return hardware;
        }
        return Build.BOARD != null ? Build.BOARD : "unknown";
    }

    public boolean isHardBlocked() {
        return ramTier == RamTier.UNSUPPORTED;
    }

    public long getMinRamBytesForModel(LocalModelManager.CatalogEntry entry) {
        if (entry == null) {
            return 6L * GB;
        }
        switch (entry.id) {
            case "qwen3-0.6b-q4":
                return 4L * GB;
            case "gemma-3-1b-q4":
            case "qwen2.5-1.5b-q4":
            case "qwen3-1.7b-q4":
                return 6L * GB;
            case "gemma-3-4b-q4":
                return 8L * GB;
            default:
                return 6L * GB;
        }
    }

    public boolean canDownloadModel(LocalModelManager.CatalogEntry entry) {
        if (isHardBlocked() || entry == null) {
            return false;
        }
        if (totalRamBytes < getMinRamBytesForModel(entry)) {
            return false;
        }
        long requiredStorage = (long) (entry.sizeBytes * 1.5f) + MIN_STORAGE_HEADROOM_BYTES;
        return freeStorageBytes >= requiredStorage;
    }

    public boolean isModelRamMarginal(LocalModelManager.CatalogEntry entry) {
        return entry != null && !isHardBlocked() && totalRamBytes < getMinRamBytesForModel(entry);
    }

    public static String formatBytesShort(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        double gb = bytes / (double) GB;
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.1f GB", gb);
        }
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.0f MB", mb);
    }
}
