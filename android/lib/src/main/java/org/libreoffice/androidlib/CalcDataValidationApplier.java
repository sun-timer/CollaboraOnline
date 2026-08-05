package org.libreoffice.androidlib;

import android.util.Log;

/**
 * 通过隐藏 CO Validation 对话框并注入 dialogevent 来应用数据有效性。
 */
final class CalcDataValidationApplier {

    private static final String TAG = "CalcDataValidation";

    /** 强制关闭 Validation 对话框（读/写/用户返回时均需调用）。 */
    static final String FORCE_CLOSE_DIALOG_JS =
            "(function(){try{"
                    + "document.querySelectorAll('.jsdialog,.lokdialog').forEach(function(el){"
                    + "el.style.opacity='0';el.style.pointerEvents='none';el.style.display='none';});"
                    + "}catch(e){}"
                    + "try{if(window.app&&app.map&&typeof app.map.fire==='function')"
                    + "{app.map.fire('closemobilewizard');}}catch(e){}"
                    + "return true;})();";

    interface Host {
        void postUnoCommand(String command, String args, boolean notify);

        void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback);

        void runOnUiThread(Runnable action);
    }

    private final Host host;

    CalcDataValidationApplier(Host host) {
        this.host = host;
    }

    void apply(CalcDataValidationState state) {
        if (state == null) {
            return;
        }
        Log.i(TAG, "apply allow=" + state.allowIndex + " data=" + state.dataIndex);
        host.postUnoCommand(".uno:Validation", "{}", false);
        host.runOnUiThread(() -> {
            try {
                Thread.sleep(450);
            } catch (InterruptedException ignored) {
            }
            host.evaluateJavascript(buildInjectScript(state), value ->
                    Log.i(TAG, "apply_inject_done value=" + value));
        });
    }

    /**
     * 读当前选区已有有效性设置：打开隐藏 Validation 对话框 → JS 读 DOM widget 值 → 关闭 → 回填 target。
     */
    void read(CalcDataValidationState target, Runnable onLoaded) {
        if (target == null) {
            if (onLoaded != null) {
                onLoaded.run();
            }
            return;
        }
        Log.i(TAG, "read_validation_start");
        host.postUnoCommand(".uno:Validation", "{}", false);
        host.runOnUiThread(() -> {
            try {
                Thread.sleep(450);
            } catch (InterruptedException ignored) {
            }
            host.evaluateJavascript(buildReadScript(), value ->
                    Log.i(TAG, "read_validation_script_done"));
            pollResult(target, onLoaded, 3);
        });
    }

    void forceCloseDialog(Runnable onDone) {
        host.evaluateJavascript(FORCE_CLOSE_DIALOG_JS, value -> {
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    private void pollResult(CalcDataValidationState target, Runnable onLoaded, int attemptsLeft) {
        host.evaluateJavascript(READ_RESULT_JS, value -> {
            boolean loaded = parseReadResult(value, target);
            boolean timedOut = value != null && value.contains("timeout");
            Log.i(TAG, "read_validation_poll loaded=" + loaded
                    + " attemptsLeft=" + attemptsLeft + " timedOut=" + timedOut);
            if (loaded || timedOut || attemptsLeft <= 0) {
                forceCloseDialog(onLoaded);
                return;
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            pollResult(target, onLoaded, attemptsLeft - 1);
        });
    }

    private static final String READ_RESULT_JS =
            "(function(){return JSON.stringify(window.__dvRead||{phase:'missing'});})();";

    private String buildReadScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){\n");
        sb.append("window.__dvRead={phase:'start',found:false,vals:null,steps:[]};\n");
        sb.append("function hideDlg(){try{document.querySelectorAll('.jsdialog,.lokdialog').forEach(function(el){el.style.opacity='0';el.style.pointerEvents='none';});}catch(e){}}\n");
        sb.append("function gv(id){try{var el=document.getElementById(id);if(!el)return '';if(el.tagName==='INPUT'||el.tagName==='SELECT'||el.tagName==='TEXTAREA')return el.value||'';var in2=el.querySelector('input,select,textarea');if(in2)return in2.value||'';return el.textContent||'';}catch(e){return '';}}\n");
        sb.append("function gc(id){try{var el=document.getElementById(id);if(!el)return -1;if(el.tagName==='INPUT'&&el.type==='checkbox')return el.checked?1:0;var cb=el.querySelector('input[type=checkbox]');return cb?(cb.checked?1:0):-1;}catch(e){return -1;}}\n");
        sb.append("var attempts=0,timer=setInterval(function(){\n");
        sb.append("attempts++;\n");
        sb.append("var wId=window.mobileDialogId;\n");
        sb.append("if(wId===undefined||wId===null||wId===-1){try{var dlg=document.querySelector('.jsdialog');if(dlg){wId=parseInt(dlg.id)||-1;}}catch(e){}}\n");
        sb.append("if(wId!==undefined&&wId!==null&&wId!==-1){\n");
        sb.append("clearInterval(timer);hideDlg();\n");
        sb.append("try{\n");
        sb.append("var out={};\n");
        sb.append("out.allow=gv('allow-input');out.data=gv('data-input');out.min=gv('min-input');out.max=gv('max-input');out.minlist=gv('minlist-input');\n");
        sb.append("out.title=gv('title-input');out.inputhelp_text=gv('inputhelp_text-input');\n");
        sb.append("out.actionCB=gv('actionCB-input');out.erroralert_title=gv('erroralert_title-input');out.errorMsg=gv('errorMsg-input');\n");
        sb.append("out.allowempty=gc('allowempty-input');out.showlist=gc('showlist-input');out.sortascend=gc('sortascend-input');out.casesens=gc('casesens-input');\n");
        sb.append("out.tsbhelp=gc('tsbhelp-input');out.tsbshow=gc('tsbshow-input');\n");
        sb.append("window.__dvRead.vals=out;window.__dvRead.found=true;\n");
        sb.append("}catch(e){window.__dvRead.steps.push({s:'read',err:e+''});}\n");
        sb.append("window.__dvRead.phase='done';\n");
        sb.append("}\n");
        sb.append("},150);\n");
        sb.append("setTimeout(function(){clearInterval(timer);if(!window.__dvRead.found){window.__dvRead.phase='timeout';}},6000);\n");
        sb.append("})();");
        return sb.toString();
    }

    private boolean parseReadResult(String value, CalcDataValidationState target) {
        if (value == null || target == null) {
            return false;
        }
        String json = value.trim();
        if (json.startsWith("\"")) {
            try {
                json = new org.json.JSONTokener(json).nextValue().toString();
            } catch (Exception e) {
                return false;
            }
        }
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONObject obj = root.optJSONObject("vals");
            if (obj == null && root.has("allow")) {
                obj = root;
            }
            if (obj == null || !obj.has("allow")) {
                return false;
            }
            target.allowIndex = CalcValidationCatalog.findAllowByLabel(obj.optString("allow")).index;
            target.dataIndex = CalcValidationCatalog.findDataByLabel(obj.optString("data")).index;
            target.errorActionIndex = CalcValidationCatalog.findErrorActionByLabel(
                    obj.optString("actionCB")).index;
            target.minValue = obj.optString("min");
            target.maxValue = obj.optString("max");
            target.listEntries = obj.optString("minlist");
            target.inputHelpTitle = obj.optString("title");
            target.inputHelpText = obj.optString("inputhelp_text");
            target.errorTitle = obj.optString("erroralert_title");
            if (target.errorActionIndex == 3) {
                target.macroUrl = target.errorTitle;
            }
            target.errorMessage = obj.optString("errorMsg");
            if (obj.optInt("allowempty", -1) >= 0) {
                target.allowEmpty = obj.optInt("allowempty") == 1;
            }
            if (obj.optInt("showlist", -1) >= 0) {
                target.showDropdownList = obj.optInt("showlist") == 1;
            }
            if (obj.optInt("sortascend", -1) >= 0) {
                target.sortAscending = obj.optInt("sortascend") == 1;
            }
            if (obj.optInt("casesens", -1) >= 0) {
                target.caseSensitive = obj.optInt("casesens") == 1;
            }
            if (obj.optInt("tsbhelp", -1) >= 0) {
                target.showInputHelp = obj.optInt("tsbhelp") == 1;
            }
            if (obj.optInt("tsbshow", -1) >= 0) {
                target.showErrorAlert = obj.optInt("tsbshow") == 1;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "parse_read_result_failed", e);
            return false;
        }
    }

    private String buildInjectScript(CalcDataValidationState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){\n");
        sb.append("window.__dvStatus={phase:'start',found:false,steps:[]};\n");
        sb.append("function _log(s,ok,d){window.__dvStatus.steps.push({s:s,ok:!!ok,d:d||''});}\n");
        sb.append("function hideDlg(){try{document.querySelectorAll('.jsdialog,.lokdialog').forEach(function(el){el.style.opacity='0';el.style.pointerEvents='none';});}catch(e){}}\n");
        sb.append("function send(wId,payload){app.socket.sendMessage('dialogevent '+wId+' '+payload);}\n");
        sb.append("function sel(wId,id,val){send(wId,JSON.stringify({id:id,cmd:'select',data:String(val),type:'list'}));}\n");
        sb.append("function mod(wId,id,val,type){send(wId,JSON.stringify({id:id,cmd:'modify',data:val,type:type||'entry'}));}\n");
        sb.append("function chk(wId,id,on){send(wId,JSON.stringify({id:id,cmd:'click',data:on?'1':'0',type:'checkbox'}));}\n");
        sb.append("function tab(wId,idx){send(wId,JSON.stringify({id:'tabcontrol',cmd:'selecttab',data:String(idx),type:'tabcontrol'}));}\n");
        sb.append("var attempts=0,timer=setInterval(function(){\n");
        sb.append("attempts++;\n");
        sb.append("var wId=window.mobileDialogId;\n");
        sb.append("if(wId===undefined||wId===null||wId===-1){try{var dlg=document.querySelector('.jsdialog');if(dlg){wId=parseInt(dlg.id)||-1;}}catch(e){}}\n");
        sb.append("if(wId!==undefined&&wId!==null&&wId!==-1){\n");
        sb.append("clearInterval(timer);hideDlg();\n");
        sb.append("_log('dialog',true,'wId='+wId);\n");

        sb.append("sel(wId,'allow-input',").append(state.allowIndex).append(");\n");
        if (CalcValidationCatalog.needsDataOperator(state.allowIndex)) {
            sb.append("sel(wId,'data-input',").append(state.dataIndex).append(");\n");
        }
        if (CalcValidationCatalog.isListAllow(state.allowIndex)) {
            appendModify(sb, "wId", "minlist-input", state.listEntries, "multiline");
            sb.append("chk(wId,'allowempty-input',").append(state.allowEmpty).append(");\n");
            sb.append("chk(wId,'showlist-input',").append(state.showDropdownList).append(");\n");
            sb.append("chk(wId,'sortascend-input',").append(state.sortAscending).append(");\n");
            sb.append("chk(wId,'casesens-input',").append(state.caseSensitive).append(");\n");
        } else if (CalcValidationCatalog.isRangeAllow(state.allowIndex)) {
            appendModify(sb, "wId", "min-input", state.minValue, "entry");
            sb.append("chk(wId,'allowempty-input',").append(state.allowEmpty).append(");\n");
            sb.append("chk(wId,'showlist-input',").append(state.showDropdownList).append(");\n");
        } else if (CalcValidationCatalog.isCustomAllow(state.allowIndex)) {
            appendModify(sb, "wId", "min-input", state.minValue, "entry");
            sb.append("chk(wId,'allowempty-input',").append(state.allowEmpty).append(");\n");
        } else if (state.allowIndex != 0) {
            appendModify(sb, "wId", "min-input", state.minValue, "entry");
            if (CalcValidationCatalog.needsBetweenValues(state.dataIndex)) {
                appendModify(sb, "wId", "max-input", state.maxValue, "entry");
            }
            sb.append("chk(wId,'allowempty-input',").append(state.allowEmpty).append(");\n");
        }

        sb.append("tab(wId,1);\n");
        sb.append("chk(wId,'tsbhelp-input',").append(state.showInputHelp).append(");\n");
        appendModify(sb, "wId", "title-input", state.inputHelpTitle, "entry");
        appendModify(sb, "wId", "inputhelp_text-input", state.inputHelpText, "multiline");

        sb.append("tab(wId,2);\n");
        sb.append("chk(wId,'tsbshow-input',").append(state.showErrorAlert).append(");\n");
        sb.append("sel(wId,'actionCB-input',").append(state.errorActionIndex).append(");\n");
        if (state.errorActionIndex == 3) {
            appendModify(sb, "wId", "erroralert_title-input", state.macroUrl, "entry");
        } else if (state.errorActionIndex != 4) {
            appendModify(sb, "wId", "erroralert_title-input", state.errorTitle, "entry");
            appendModify(sb, "wId", "errorMsg-input", state.errorMessage, "multiline");
        }

        sb.append("setTimeout(function(){\n");
        sb.append("try{send(wId,JSON.stringify({id:'ok',cmd:'click',data:'0',type:'pushbutton'}));_log('ok',true,'');}catch(e){_log('ok',false,e+'');}\n");
        sb.append("setTimeout(function(){try{if(window.app&&app.map&&typeof app.map.fire==='function'){app.map.fire('closemobilewizard');}}catch(e){}},200);\n");
        sb.append("},350);\n");
        sb.append("}\n");
        sb.append("},150);\n");
        sb.append("setTimeout(function(){clearInterval(timer);if(!window.__dvStatus.found){window.__dvStatus.phase='timeout';}},8000);\n");
        sb.append("})();");
        return sb.toString();
    }

    private static void appendModify(StringBuilder sb, String wIdVar, String widgetId, String value, String type) {
        sb.append("try{mod(").append(wIdVar).append(",'")
                .append(widgetId).append("','")
                .append(escapeJs(value)).append("','")
                .append(type).append("');}catch(e){}\n");
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
