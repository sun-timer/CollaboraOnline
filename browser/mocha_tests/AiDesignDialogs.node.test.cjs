const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {stripTypeScriptTypes}=require('node:module');
const {JSDOM}=require('jsdom');
function harness(file='MobileAiImageDialog') {
 const dom=new JSDOM('<body/>');const {window}=dom;const calls=[],cancels=[],inserts=[];let listener;let session='doc-1';
 const bridge={accept:()=>true,subscribe:f=>(listener=f,()=>{}),cancel:id=>(cancels.push(id),true),request:p=>(calls.push(p),'request-'+calls.length)};
 class Sheet{constructor(options){this.options=options;this.root=window.document.createElement('div');this.root.innerHTML='<button class="mobile-ai-sheet-close"></button>';window.document.body.append(this.root);}setBody(body){this.root.append(body);}open(){}close(){this.options.onClose?.();}}
 const context=vm.createContext({window,document:window.document,console,
  MobileAiBridge:{getInstance:()=>bridge},MobileAiSheet:Sheet,
  NativeBridge:{getInstance:()=>({getDocumentSessionId:()=>session})},
  WriterEditorController:{getInstance:()=>({insertImage:(name,value)=>(inserts.push(value),{dispatched:'message'})})},
  WriterAiController:{getInstance:()=>({subscribe:()=>()=>{},getState:()=>({state:'idle',preview:''})})},
  WriterAiCatalog:{TYPESET_TYPES:[{key:'paper',label:'论文'},{key:'gov',label:'党政公文'},{key:'contract',label:'合同协议'},{key:'general',label:'通用文档'}]}
 });
 vm.runInContext(stripTypeScriptTypes(fs.readFileSync(__dirname+'/../src/app/'+file+'.ts','utf8'))+';window.Dialog='+file+';',context);
 const dialog=new window.Dialog();return {window,dialog,calls,cancels,inserts,emit:payload=>listener({type:'ai.done',requestId:'request-1',documentSessionId:'doc-1',payload}),setSession:value=>session=value,close:()=>dom.window.close()};
}
test('image request specifies four candidates and actual dimensions for all three ratios',()=>{
 const h=harness();try{for(const [ratio,size]of [['9:16','576x1024'],['1:1','1024x1024'],['16:9','1024x576']]){const p=h.window.Dialog.buildPayload(' 景色 ',ratio);assert.equal(p.n,4);assert.equal(p.size,size);assert.equal(p.selection,'景色');}}finally{h.close();}
});
test('four distinct images open the selected preview and insert only the selected image',()=>{
 const h=harness();try{h.dialog.description.value='山林';h.dialog.generate();h.emit({images:['one','two','three','four']});assert.equal(h.dialog.gallery.children.length,4);h.dialog.gallery.children[2].click();assert.equal(h.dialog.enlarged,true);h.dialog.pages.children[3].click();h.dialog.insert();assert.deepEqual(h.inserts,['four']);}finally{h.close();}
});
test('partial response does not duplicate images to fill missing candidates',()=>{
 const h=harness();try{h.dialog.description.value='山林';h.dialog.generate();h.emit({images:['one','two'],warning:'已生成 2/4 张'});assert.equal(h.dialog.gallery.children.length,2);assert.match(h.dialog.status.textContent,/2\/4/);}finally{h.close();}
});
test('closing cancels the active parent request once, and a changed document cannot receive insertion',()=>{
 const h=harness();try{h.dialog.description.value='山林';h.dialog.generate();h.dialog.close();h.dialog.close();assert.deepEqual(h.cancels,['request-1']);}finally{h.close();}
 const h2=harness();try{h2.dialog.description.value='山林';h2.dialog.generate();h2.emit({images:['one']});h2.setSession('doc-2');h2.dialog.insert();assert.equal(h2.inserts.length,0);}finally{h2.close();}
});
test('typesetting presents four original preview assets and updates a single selected card',()=>{
 const h=harness('MobileAiTypesetDialog');try{const cards=h.window.document.querySelectorAll('.mobile-ai-typeset-card');assert.equal(cards.length,4);assert.equal(new Set([...cards].map(card=>card.querySelector('img').src)).size,4);cards[2].click();assert.equal(h.dialog.selectedType,'contract');assert.equal([...cards].filter(card=>card.getAttribute('aria-pressed')==='true').length,1);}finally{h.close();}
});
