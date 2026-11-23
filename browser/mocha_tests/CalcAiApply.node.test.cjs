const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {stripTypeScriptTypes}=require('node:module');
function harness(fail='') {
 const messages=[]; let listener;
 const window={setTimeout,clearTimeout,app:{map:{_docLayer:{_lastFormula:''},on:(_n,f)=>listener=f,off:()=>{}},socket:{sendMessage:m=>{messages.push(m);if(m.includes('EnterString'))window.app.map._docLayer._lastFormula=JSON.parse(m.slice(m.indexOf('{'))).StringName.value;queueMicrotask(()=>listener({commandName:m.split(' ')[1].split('?')[0],success:m.includes('EnterString') ? false : !m.includes(fail||'NEVERFAIL'),result:m.includes('EnterString') ? {type:'void'} : undefined}));}}}};
 const c=vm.createContext({window,console,CalcAiContext:{scope:()=> 'session:0',columnToLetters:n=>String.fromCharCode(65+n)}});
 for(const f of ['WriterEditorCatalog','CalcAiCatalog','CalcAiMutateApply'])vm.runInContext(stripTypeScriptTypes(fs.readFileSync(__dirname+'/../src/app/'+f+'.ts','utf8')),c);
 return {messages,apply:(type,p,range)=>window.CalcAiMutateApply.apply(type,JSON.stringify(p),range)};
}
test('writes every cell of a rectangle then selects the bold action range',async()=>{
 const h=harness();assert.equal((await h.apply('calc_data_process',{actions:[{type:'set_value',range:'B2:C3',value:'x{row}'},{type:'bold',range:'D1:D2'}]})).ok,true);
 assert.equal(h.messages.filter(m=>m.includes('EnterString')).length,4);
 assert.deepEqual(h.messages.filter(m=>m.includes('StringName')).map(m=>JSON.parse(m.slice(m.indexOf('{'))).StringName.value),['x2','x2','x3','x3']);
 assert.match(h.messages.at(-2),/D1:D2/);assert.equal(h.messages.at(-1),'uno .uno:Bold');
});
test('a failed action stops the batch and reports partial completion',async()=>{
 const h=harness('Bold');const r=await h.apply('calc_data_process',{actions:[{type:'calculate'},{type:'bold',range:'A1'},{type:'sort',range:'B1:B3'}]});assert.equal(r.ok,false);assert.match(r.error,/1\/3/);assert.ok(!h.messages.some(m=>m.includes('Sort')));
});
test('conditional format uses the real greater rule and explicit neutral style',async()=>{
 const h=harness();assert.equal((await h.apply('calc_cond_format',{conditionType:'greater',range:'B2:B4',value:100})).ok,true);const a=JSON.parse(h.messages[0].slice(h.messages[0].indexOf('{')));assert.equal(a.FormatRule.value,2);assert.equal(a.Style.value,'');assert.equal(JSON.parse(a.FormatSpec.value).backgroundColor,'#FFEB9C');
});
test('chart ignores model data range and binds the captured source',async()=>{
 const h=harness();assert.equal((await h.apply('calc_chart',{chart:{chartType:'pie',dataRange:'Z1:Z3'},preprocess:[{value:99}]},'A1:B4')).ok,true);assert.match(h.messages[0],/A1:B4/);assert.ok(!h.messages[0].includes('Z1'));
});
test('accepted, cancelled and failed requests cannot cancel again; stale context cannot apply',async()=>{
 let receive,scope='doc:0:A1',cancels=0;
 const bridge={subscribe:f=>(receive=f,()=>{}),isAvailable:()=>true,request:()=> 'r1',cancel:()=>{cancels++;return true;},accept:()=>true};
 const c=vm.createContext({window:{app:{}},console,CalcAiContext:{snapshot:()=>scope},NativeBridge:{getInstance:()=>({getDocumentSessionId:()=> 'doc'})}});
 vm.runInContext(stripTypeScriptTypes(fs.readFileSync(__dirname+'/../src/app/CalcAiCatalog.ts','utf8')),c);
 let source=fs.readFileSync(__dirname+'/../src/app/CalcAiController.ts','utf8');source=source.slice(0,source.lastIndexOf("if (typeof window !== 'undefined')"));
 vm.runInContext(stripTypeScriptTypes(source)+';globalThis.Controller=CalcAiController;',c);
 const controller=new c.Controller(bridge,{pastePlainText:()=>true});
 const ready=()=>{controller.request('calc_formula','求和',{cellAddress:'A1'});receive({type:'ai.done',requestId:'r1',payload:{fullText:'=SUM(A1:A2)'}});};
 ready();assert.equal(await controller.accept(),true);assert.equal(controller.cancel(),false);
 ready();assert.equal(controller.cancel(),true);assert.equal(controller.cancel(),false);assert.equal(cancels,1);
 ready();scope='doc2:0:A1';assert.equal(await controller.accept(),false);assert.match(controller.getState().error,/已变化/);assert.equal(controller.cancel(),false);
});
test('clears an entire column in one native range command without cell iteration',async()=>{
 const h=harness();const r=await h.apply('calc_data_process',{actions:[{type:'clear_contents',range:'B1:B1048576'}]});assert.equal(r.ok,true);assert.equal(h.messages.length,1);assert.equal(h.messages[0],'uno .uno:ClearContentsInRange '+JSON.stringify({Range:{type:'string',value:'B1:B1048576'}}));
});
test('empty set_value clears contents rather than skipping the action',async()=>{
 const h=harness();assert.equal((await h.apply('calc_data_process',{actions:[{type:'set_value',range:'B1:B5',value:''}]})).ok,true);assert.ok(h.messages.at(-1).startsWith('uno .uno:ClearContentsInRange '));
});
test('million-row generation reports the exact limit without writing cells',async()=>{
 const h=harness();const r=await h.apply('calc_data_process',{actions:[{type:'set_formula',range:'B1:B1048576',value:'=ROW()-ROW($A$1)+1'}]});assert.equal(r.ok,false);assert.match(r.error,/1000/);assert.ok(!h.messages.some(m=>m.includes('EnterString')));
});
test('actual core range wins over stale address input and active A5',()=>{
 const c=vm.createContext({window:{app:{calc:{cellAddress:{x:0,y:4}},map:{_docLayer:{_selectedPart:1,_calcAiSelection:{part:1,address:'B1:B1048576'}}}}},document:{querySelector:()=>({getAttribute:()=> 'A5'})}});
 vm.runInContext(stripTypeScriptTypes(fs.readFileSync(__dirname+'/../src/app/CalcAiContext.ts','utf8'))+';globalThis.Context=CalcAiContext;',c);assert.equal(c.Context.getSelectedRange(),'B1:B1048576');
});
