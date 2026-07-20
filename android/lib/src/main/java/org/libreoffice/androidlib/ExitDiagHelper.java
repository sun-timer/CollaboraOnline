package org.libreoffice.androidlib;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * 退出文档后进程消失 — 分阶段诊断（只加日志，不改业务）。
 * 所有日志 tag=LOActivity，便于 adb logcat LOActivity:I 一次抓全。
 *
 * <p>验收时重点搜：exit_probe_</p>
 */
public final class ExitDiagHelper {
    private static final String TAG = "LOActivity";
    private static final long[] WATCHDOG_DELAYS_MS = {300, 800, 1500, 3000, 6000, 10000};
    private static final long DOC_EXIT_WINDOW_MS = 15000;

    private static volatile boolean installed;
    private static volatile long lastDocExitUptimeMs;

    private ExitDiagHelper() {
    }

    public static void installOnce() {
        if (installed) {
            return;
        }
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "exit_probe_uncaught thread=" + thread.getName()
                    + " pid=" + Process.myPid(), throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
        Log.i(TAG, "exit_diag_installed pid=" + Process.myPid());
    }

    public static void markDocExitToHome() {
        lastDocExitUptimeMs = SystemClock.uptimeMillis();
        Log.i(TAG, "exit_diag_mark_doc_exit_to_home pid=" + Process.myPid());
    }

    public static boolean isWithinDocExitGracePeriod(long graceMs) {
        if (lastDocExitUptimeMs <= 0) {
            return false;
        }
        return SystemClock.uptimeMillis() - lastDocExitUptimeMs < graceMs;
    }

    public static void logHomeLifecycleAfterDocExit(String event) {
        if (lastDocExitUptimeMs <= 0) {
            return;
        }
        final long sinceMs = SystemClock.uptimeMillis() - lastDocExitUptimeMs;
        if (sinceMs > DOC_EXIT_WINDOW_MS) {
            return;
        }
        Log.i(TAG, "exit_diag_home_after_doc_exit event=" + event
                + " sinceExitMs=" + sinceMs
                + " pid=" + Process.myPid());
    }

    public static void schedulePostDestroyWatchdog(String docTypeHint) {
        final int pid = Process.myPid();
        final Handler handler = new Handler(Looper.getMainLooper());
        for (long delayMs : WATCHDOG_DELAYS_MS) {
            handler.postDelayed(() -> Log.i(TAG, "exit_diag_watchdog phase=after_lo_destroy"
                    + " delayMs=" + delayMs
                    + " pid=" + pid
                    + " docType=" + docTypeHint), delayMs);
        }
    }

    public static void schedulePostHomeReturnWatchdog() {
        final int pid = Process.myPid();
        final Handler handler = new Handler(Looper.getMainLooper());
        for (long delayMs : WATCHDOG_DELAYS_MS) {
            handler.postDelayed(() -> Log.i(TAG, "exit_diag_watchdog phase=after_home_return"
                    + " delayMs=" + delayMs
                    + " pid=" + pid), delayMs);
        }
    }

    public static void logPhase(String phase) {
        Log.i(TAG, "exit_diag_phase=" + phase + " pid=" + Process.myPid());
    }

    /** 记录 finish / finishAndRemoveTask 调用栈，用于定位谁杀了 task。 */
    public static void logTaskFinishProbe(Activity activity, String method, boolean removeTask) {
        Log.i(TAG, "exit_probe_task_finish method=" + method
                + " removeTask=" + removeTask
                + " activity=" + (activity != null ? activity.getClass().getName() : "null")
                + " isFinishing=" + (activity != null && activity.isFinishing())
                + " isTaskRoot=" + (activity != null && activity.isTaskRoot())
                + " pid=" + Process.myPid()
                + " stack=" + compactStack(4, 10));
    }

    /** BYE 进入 Java 层时的完整状态（含被拦截原因）。 */
    public static void logByeProbe(String stage, String message,
            boolean bridgeEnabled, boolean finishing, boolean exitingToHome,
            boolean docLoaded, boolean docSwitch, boolean hasCalling, boolean taskRoot,
            String decision) {
        Log.i(TAG, "exit_probe_bye stage=" + stage
                + " decision=" + decision
                + " msg=" + (message != null ? message : "")
                + " bridgeEnabled=" + bridgeEnabled
                + " isFinishing=" + finishing
                + " exitingToHome=" + exitingToHome
                + " documentLoaded=" + docLoaded
                + " documentSwitchInProgress=" + docSwitch
                + " hasCallingActivity=" + hasCalling
                + " isTaskRoot=" + taskRoot
                + " sinceHomeExitMs=" + sinceHomeExitMs()
                + " pid=" + Process.myPid());
    }

    public static void logProbe(String event, String details) {
        Log.i(TAG, "exit_probe event=" + event
                + " details=" + details
                + " sinceHomeExitMs=" + sinceHomeExitMs()
                + " pid=" + Process.myPid());
    }

    /**
     * 冷启动时读系统记录的「上一进程为何退出」— 比猜 finishAndRemoveTask 更可靠。
     * 需要 API 30+；关文档闪退后再次打开 app 时应出现 exit_probe_prev_death 行。
     */
    public static void logPreviousProcessDeaths(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(TAG, "exit_probe_prev_death skipped reason=api_lt_30 pid=" + Process.myPid());
            return;
        }
        try {
            final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                Log.w(TAG, "exit_probe_prev_death skipped reason=no_activity_manager pid=" + Process.myPid());
                return;
            }
            final List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(
                    context.getPackageName(), 0, 8);
            if (list == null || list.isEmpty()) {
                Log.i(TAG, "exit_probe_prev_death count=0 pid=" + Process.myPid());
                return;
            }
            Log.i(TAG, "exit_probe_prev_death count=" + list.size()
                    + " currentPid=" + Process.myPid());
            for (int i = 0; i < list.size(); i++) {
                final ApplicationExitInfo info = list.get(i);
                Log.i(TAG, "exit_probe_prev_death[" + i + "]"
                        + " pid=" + info.getPid()
                        + " reason=" + exitReasonName(info.getReason())
                        + " status=" + info.getStatus()
                        + " importance=" + info.getImportance()
                        + " desc=" + safe(info.getDescription())
                        + " ts=" + info.getTimestamp());
            }
        } catch (Throwable t) {
            Log.w(TAG, "exit_probe_prev_death fail pid=" + Process.myPid(), t);
        }
    }

    private static long sinceHomeExitMs() {
        if (lastDocExitUptimeMs <= 0) {
            return -1;
        }
        return SystemClock.uptimeMillis() - lastDocExitUptimeMs;
    }

    private static String exitReasonName(int reason) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return String.valueOf(reason);
        }
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH:
                return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR:
                return "ANR";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_USER_REQUESTED:
                return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED:
                return "USER_STOPPED";
            case ApplicationExitInfo.REASON_OTHER:
                return "OTHER";
            default:
                return "UNKNOWN(" + reason + ")";
        }
    }

    private static String safe(String s) {
        return s != null ? s.replace('\n', ' ') : "";
    }

    private static String compactStack(int skipFrames, int maxFrames) {
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        final StringBuilder sb = new StringBuilder();
        int written = 0;
        for (int i = skipFrames; i < stack.length && written < maxFrames; i++) {
            final StackTraceElement frame = stack[i];
            final String cn = frame.getClassName();
            if (cn.startsWith("dalvik.") || cn.startsWith("java.lang.Thread")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
            written++;
        }
        return sb.toString();
    }
}
