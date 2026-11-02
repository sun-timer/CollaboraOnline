// Execute the actual Java snapshot, repair and internal-paste restore branches.
// Android Html and LOK are contract doubles; this is not a device paste test.
'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {execFileSync}=require('node:child_process');
const java=fs.readFileSync(path.join(__dirname,'../../android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java'),'utf8');
function block(source,needle) {
 const start=source.indexOf(needle); if(start<0) throw Error(needle);
 let i=source.indexOf('{',start),depth=1,end=i+1;
 while(depth && end<source.length){if(source[end]==='{')depth++;if(source[end]==='}')depth--;end++;}
 return source.slice(start,end);
}
const dataSource=fs.readFileSync(path.join(__dirname,'../../android/lib/src/main/java/org/libreoffice/androidlib/lok/LokClipboardData.java'),'utf8');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'ppt-clipboard-'));
try {
 fs.mkdirSync(path.join(temp,'android/text'),{recursive:true});
 fs.writeFileSync(path.join(temp,'android/text/Html.java'),`package android.text; public class Html {
 public static final int FROM_HTML_MODE_LEGACY=0;
 public static CharSequence fromHtml(String value,int flags){return value.replaceAll("<[^>]+>","");}
 }`);
 const snapshotFields=java.slice(java.indexOf('    private String clipboardSelectionTextSnapshot'),java.indexOf('    private void primeSystemClipboardFromSelection'));
 const tokenBlock=block(java.slice(java.indexOf('private final ClipboardPasteResult performPaste()')),'if (!token.isEmpty())');
 const code=`import java.util.*; import java.io.*; import java.nio.charset.StandardCharsets;
 public class PresentationClipboardContract {
 static final String TAG="test",CLIPBOARD_FILE_PATH="clip"; static int count=0;
 static class Log {static void i(String t,String m){} static void w(String t,String m){}}
 static class LokClipboardEntry {String mime;byte[] data;}
 static class LokClipboardData {
 ArrayList<LokClipboardEntry> clipboardEntries=new ArrayList<>();String clipToken;
 ${block(dataSource,'public String getText()')}
 ${block(dataSource,'public String getHtml()')}
 ${block(dataSource,'public void removeEmptyEntries()')}
 static LokClipboardData createFromFile(File f){return cached;}
 }
 boolean mIsImpressDocument=true;long documentLifecycleGeneration=5,activeClipboardDocumentGeneration=5;
 String aiOpPendingSelection="",selected="",selectionHtml="",systemText="",activeClipboardToken="";
 static LokClipboardData cached;LokClipboardData restored;
 Object clipboardManager=new Object();
 ${snapshotFields}
 String getTextSelection(String mime){return mime.equals("text/html")?selectionHtml:selected;}
 void setSystemClipboardPlainText(String s){systemText=s;}
 void runOnUiThread(Runnable r){r.run();}
 ${block(java,'private void primeSystemClipboardFromSelection(String reason)')}
 ${block(java,'private boolean repairPresentationTextClipboard(LokClipboardData data)')}
 enum ClipboardPasteResult {NONE,CORE_CLIPBOARD,NATIVE_PENDING}
 PresentationClipboardContract getApplicationContext(){return this;}
 File getCacheDir(){return new File(".");}
 boolean setClipboardContent(LokClipboardData d){restored=d;return true;}
 ClipboardPasteResult restore(String token){${tokenBlock}return ClipboardPasteResult.NONE;}
 void publish(boolean repairedTextCopy,String token){${java.match(/activeClipboardToken = repairedTextCopy[^;]+;/)[0]}}
 static LokClipboardData data(String text,String html){LokClipboardData d=new LokClipboardData();
 if(text!=null)add(d,"text/plain;charset=utf-8",text);if(html!=null)add(d,"text/html",html);return d;}
 static void add(LokClipboardData d,String mime,String text){LokClipboardEntry e=new LokClipboardEntry();e.mime=mime;e.data=text.getBytes(StandardCharsets.UTF_8);d.clipboardEntries.add(e);}
 static void check(boolean ok){if(!ok)throw new AssertionError("check "+count);count++;}
 public static void main(String[] args)throws Exception {
 PresentationClipboardContract h=new PresentationClipboardContract();
 h.selected="选中文字";h.selectionHtml="<b>选中文字</b>";h.primeSystemClipboardFromSelection("copy");
 LokClipboardData empty=data(null,"<html><body><pre></pre></body></html>");
 check(h.repairPresentationTextClipboard(empty));check(empty.getText().equals(h.selected));check(empty.getHtml().equals(h.selectionHtml));
 empty.clipToken="repaired";cached=empty;h.publish(true,"repaired");new File("clip").createNewFile();
 check(h.restore("repaired")==ClipboardPasteResult.CORE_CLIPBOARD);check(h.restored==empty);
 LokClipboardData stale=data("earlier AI document content","<p>earlier AI document content</p>");
 check(h.repairPresentationTextClipboard(stale));check(stale.getText().equals(h.selected));
 LokClipboardData matching=data(h.selected,h.selectionHtml);check(!h.repairPresentationTextClipboard(matching));
 h.selected="";check(h.repairPresentationTextClipboard(data(null,"<pre></pre>"))); // Cut retains before-command snapshot.
 h.documentLifecycleGeneration++;check(!h.repairPresentationTextClipboard(data(null,"<pre></pre>")));
 h.documentLifecycleGeneration--;h.mIsImpressDocument=false;check(!h.repairPresentationTextClipboard(data(null,"<pre></pre>")));
 h.mIsImpressDocument=true;h.clipboardSelectionTextSnapshot="";check(!h.repairPresentationTextClipboard(data(null,"<img>")));
 System.out.println("PPT clipboard: "+count+" source-contract checks passed");
 }
 }`;
 fs.writeFileSync(path.join(temp,'LOActivity.java'),code.replaceAll('PresentationClipboardContract','LOActivity'));
 execFileSync('javac',['android/text/Html.java','LOActivity.java'],{cwd:temp});
 process.stdout.write(execFileSync('java',['-cp',temp,'LOActivity'],{cwd:temp}));
}finally{fs.rmSync(temp,{recursive:true,force:true});}
