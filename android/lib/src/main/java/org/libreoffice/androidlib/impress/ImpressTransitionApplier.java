package org.libreoffice.androidlib.impress;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 通过 Core Sidebar（SlideTransitionPane）应用幻灯片切换动画。
 * Mobile 上 sidebar DOM 可能不存在，但 dialogevent 仍可直接发往 Core（windowId = -1）。
 *
 * <p>Core 侧 {@code SlideTransitionPane} 仅监听 iconview 的 item_activated；
 * dialogevent 须使用 cmd=activate（仅 select 不会应用切换效果）。
 */
public final class ImpressTransitionApplier {
    private static final String TAG = "ImpressTransition";
    /** Core Definitions.WindowId.Sidebar */
    private static final int SIDEBAR_WINDOW_ID = -1;
    private static final int MAX_ATTEMPTS = 8;
    private static final long INITIAL_DELAY_MS = 600L;
    private static final long RETRY_DELAY_MS = 400L;

    public interface Host {
        void postUnoCommand(String cmd, String args, boolean notify);

        void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback);

        void onApplyFinished(String phase, String detail);
    }

    private ImpressTransitionApplier() {
    }

    public static void apply(Host host, int iconViewIndex, boolean applyToAll) {
        if (host == null) {
            return;
        }
        Log.i(TAG, "apply_start iconViewIndex=" + iconViewIndex + " applyToAll=" + applyToAll);
        host.postUnoCommand(".uno:SidebarShow", "{}", false);
        host.postUnoCommand(".uno:SlideChangeWindow", "{}", false);
        scheduleAttempt(host, iconViewIndex, applyToAll, 0);
    }

    private static void scheduleAttempt(
            Host host, int iconViewIndex, boolean applyToAll, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            host.onApplyFinished("timeout", "attempts=" + MAX_ATTEMPTS);
            return;
        }
        long delay = INITIAL_DELAY_MS + (long) attempt * RETRY_DELAY_MS;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            host.evaluateJavascript(buildScript(iconViewIndex, applyToAll, attempt), value -> {
                String phase = parsePhase(value);
                String detail = decodeJsJson(value);
                Log.i(TAG, "apply_result attempt=" + attempt + " phase=" + phase
                        + " reason=" + detail);
                if (isSuccessPhase(phase)) {
                    host.onApplyFinished(phase, detail);
                    return;
                }
                if (shouldRetry(phase) && attempt + 1 < MAX_ATTEMPTS) {
                    scheduleAttempt(host, iconViewIndex, applyToAll, attempt + 1);
                    return;
                }
                if (attempt + 1 >= MAX_ATTEMPTS) {
                    host.onApplyFinished(phase, detail != null ? detail : String.valueOf(value));
                } else {
                    scheduleAttempt(host, iconViewIndex, applyToAll, attempt + 1);
                }
            });
        }, delay);
    }

    private static boolean isSuccessPhase(String phase) {
        return "activated".equals(phase) || "apply_all".equals(phase);
    }

    private static boolean shouldRetry(String phase) {
        return "waiting".equals(phase) || "init".equals(phase);
    }

    /** WebView evaluateJavascript 会把 JSON 字符串再包一层引号并转义。 */
    static String decodeJsJson(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            try {
                return new JSONArray("[" + s + "]").getString(0);
            } catch (Exception ignored) {
                return s.substring(1, s.length() - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
        }
        return s;
    }

    private static String parsePhase(String raw) {
        String json = decodeJsJson(raw);
        if (json == null || json.isEmpty()) {
            return "empty";
        }
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString("phase", "unknown");
        } catch (Exception e) {
            Log.w(TAG, "parsePhase_failed raw=" + raw + " err=" + e);
            return "unknown";
        }
    }

    private static String buildScript(int iconViewIndex, boolean applyToAll, int attempt) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){");
        sb.append("var s={phase:'init',attempt:").append(attempt).append(",wId:").append(SIDEBAR_WINDOW_ID).append("};");
        sb.append("try{");
        sb.append("if(typeof app==='undefined'||!app.socket){s.phase='waiting';return JSON.stringify(s);}");
        sb.append("var wId=").append(SIDEBAR_WINDOW_ID).append(";");
        sb.append("if(window.sidebarId!==undefined&&window.sidebarId!==null){wId=window.sidebarId;}");
        sb.append("s.wId=wId;");
        sb.append("function send(wId,cmd,data,type,id){");
        sb.append("var msg='dialogevent '+wId");
        sb.append("+' {\\\"id\\\":\\\"'+id+'\\\", \\\"cmd\\\": \\\"'+cmd+'\\\", \\\"data\\\": \\\"'+data+'\\\", \\\"type\\\": \\\"'+type+'\\\"}';");
        sb.append("app.socket.sendMessage(msg);}");
        sb.append("try{app.socket.sendMessage('uno .uno:SidebarShow');}catch(e1){}");
        sb.append("try{app.socket.sendMessage('uno .uno:SlideChangeWindow');}catch(e2){}");
        sb.append("if(typeof app.map!=='undefined'&&app.map.sidebar){");
        sb.append("try{app.map.sidebar.setupTargetDeck('.uno:SlideChangeWindow');}catch(e3){}}");
        // 与 Web IconView 单击一致：先 select 再 activate；Core 仅 item_activated 会 applyToSelectedPages
        sb.append("send(wId,'select','").append(iconViewIndex).append("','iconview','transitions_icons');");
        sb.append("send(wId,'activate','").append(iconViewIndex).append("','iconview','transitions_icons');");
        sb.append("s.phase='activated';");
        if (applyToAll) {
            sb.append("send(wId,'click','0','pushbutton','apply_to_all');");
            sb.append("s.phase='apply_all';");
        }
        sb.append("}catch(e){s.phase='error';s.err=e+'';}");
        sb.append("return JSON.stringify(s);");
        sb.append("})()");
        return sb.toString();
    }
}
