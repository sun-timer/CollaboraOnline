package org.libreoffice.androidlib.impress;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 通过 Web JSDialog（SlideTransitionPane）应用幻灯片切换动画。
 */
public final class ImpressTransitionApplier {
    private static final String TAG = "ImpressTransition";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Host {
        void postUnoCommand(String cmd, String args, boolean notify);

        void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback);
    }

    private ImpressTransitionApplier() {
    }

    public static void apply(Host host, int iconViewIndex, boolean applyToAll) {
        if (host == null) {
            return;
        }
        Log.i(TAG, "apply_start iconViewIndex=" + iconViewIndex + " applyToAll=" + applyToAll);
        host.postUnoCommand(".uno:SlideChangeWindow", "{}", false);
        MAIN.postDelayed(() -> host.evaluateJavascript(
                buildScript(iconViewIndex, applyToAll),
                value -> Log.i(TAG, "apply_result " + value)), 450);
    }

    private static String buildScript(int iconViewIndex, boolean applyToAll) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){");
        sb.append("var _s={phase:'init',wId:-1,found:false};");
        sb.append("function _log(k,ok,d){try{console.log('LOActivity transition_'+k+' ok='+ok+' '+d);}catch(e){}}");
        sb.append("try{");
        sb.append("var wId=-1;");
        sb.append("var panels=document.querySelectorAll('.jsdialog');");
        sb.append("for(var i=0;i<panels.length;i++){");
        sb.append("var p=panels[i];");
        sb.append("if(p.querySelector('#transitions_icons')||p.querySelector('[id=\"transitions_icons\"]')){");
        sb.append("wId=parseInt(p.id,10);if(!isNaN(wId))break;wId=-1;}}");
        sb.append("if(wId<0&&window.mobileDialogId!==undefined&&window.mobileDialogId!==null){wId=window.mobileDialogId;}");
        sb.append("_s.wId=wId;");
        sb.append("if(wId<0){_s.phase='no_dialog';return JSON.stringify(_s);}");
        sb.append("var row=").append(iconViewIndex).append(";");
        sb.append("var sel='dialogevent '+wId+' {\\\"id\\\":\\\"transitions_icons\\\",\\\"cmd\\\":\\\"select\\\",\\\"data\\\":\\\"'+row+'\\\",\\\"type\\\":\\\"iconview\\\"}';");
        sb.append("app.socket.sendMessage(sel);");
        sb.append("_s.phase='selected';_s.found=true;");
        if (applyToAll) {
            sb.append("var all='dialogevent '+wId+' {\\\"id\\\":\\\"apply_to_all\\\",\\\"cmd\\\":\\\"click\\\",\\\"data\\\":\\\"0\\\",\\\"type\\\":\\\"pushbutton\\\"}';");
            sb.append("app.socket.sendMessage(all);");
            sb.append("_s.phase='apply_all';");
        }
        sb.append("_log('done',true,_s.phase);");
        sb.append("}catch(e){_s.phase='error';_s.err=e+'';_log('fail',false,e+'');}");
        sb.append("return JSON.stringify(_s);");
        sb.append("})()");
        return sb.toString();
    }
}
