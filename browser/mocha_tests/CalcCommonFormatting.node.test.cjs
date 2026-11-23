const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const { JSDOM } = require('jsdom');

function setup() {
 const dom = new JSDOM('<div id="addressInput"><input aria-label="A1"></div>');
 let session = 'doc-1', part = 0, serial = 0;
 const timers = new Map(), listeners = new Map(), sent = [], events = [];
 const map = { _docLayer: { _viewId: 7 }, getDocType: () => 'spreadsheet', getCurrentPartNumber: () => part,
  isEditMode: () => true, sendUnoCommand: (...args) => sent.push(args), stateChangeHandler: {getItemValue: () => undefined},
  on: (name, cb) => {const set=listeners.get(name)||new Set();set.add(cb);listeners.set(name,set);},
  off: (name, cb) => listeners.get(name)?.delete(cb),
  fire: (name, e) => { events.push([name,e]); for(const cb of listeners.get(name)||[])cb(e); } };
 const context = vm.createContext({window: dom.window, document: dom.window.document, console,
  WriterEditorIcons: {has:()=>false,get:()=>''},
  NativeBridge: {getInstance: () => ({getDocumentSessionId: () => session})} });
 dom.window.ThisIsTheiOSApp = true; dom.window.app = {map};
 dom.window.setTimeout = (fn) => {timers.set(++serial,fn);return serial;};
 dom.window.clearTimeout = (id) => timers.delete(id);
 dom.window.postMobileMessage = (message) => sent.push(message);
 for (const name of ['CalcEditorFormat','CalcFormatStateBridge','CalcEditorBorder','CalcEditorCatalog','CalcEditorPanel']) {
  const source=fs.readFileSync(path.join(__dirname,'../src/app',name+'.ts'),'utf8');
  vm.runInContext(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2020}}).outputText+`;globalThis.${name}=${name};`,context);
 }
 dom.window.CalcFormatStateBridge = context.CalcFormatStateBridge;
 const bridge=context.CalcFormatStateBridge;
 function request() {bridge.request();return JSON.parse(sent.filter(s=>typeof s==='string').at(-1).slice('CALC_FORMAT_STATE '.length));}
 function reply(payload,states,success=true) {bridge.onResult({...payload,states,success});}
 function states(values) {const payload=request();reply(payload,values);}
 const panel=Object.create(context.CalcEditorPanel.prototype);
 panel.controller={applyFontSize: value=>sent.push(['font',value])};panel.subHost=()=>null;
 panel.requestReadback=()=>{};
 panel.content=dom.window.document.createElement('div');
 return {context,dom,map,bridge,sent,events,timers,panel,request,reply,states,
  session: s=>session=s, part: p=>part=p,
  select: address=>{dom.window.document.querySelector('input').setAttribute('aria-label',address);map.fire('celladdress',{address});}};
}

test('Calc numeric sizes retain fractional and non-preset values without Word aliases',()=>{
 const {context,panel,states}=setup();
 for(const [input,expected] of [[14,'14 pt'],['10.5 pt','10.5 pt'],[13.25,'13.25 pt'],[null,'—'],['disabled','—']]){
  states({'.uno:FontHeight':input});assert.equal(panel.fontSizeLabel(),expected);
 }
 assert.equal(context.CalcEditorPanel.fontSizeValue('四号'),null);
});

test('font picker includes current custom size and sends its pt value',()=>{
 const {context,panel,states,sent}=setup();states({'.uno:FontHeight':13.25});
 context.WriterEditorChooseDialog=class {constructor(...args){this.args=args;}};
 panel.presentSub=dialog=>{const [,options,pick,selected]=dialog.args;
  assert.equal(selected,'13.25 pt');assert.ok(options.some(x=>x.label==='13.25 pt'));
  assert.ok(options.every(x=>/ pt$/.test(x.label)));pick(options.find(x=>x.value==='10.5'));};
 panel.openFontSizeDialog();assert.deepEqual(sent.at(-1),['font','10.5']);
});

test('direction controls dispatch direction, independently of indentation',()=>{
 const {context,panel,sent}=setup();panel.sendUno=command=>sent.push(command);
 for(const [label,command] of [['从左到右','.uno:ParaLeftToRight'],['从右到左','.uno:ParaRightToLeft']]){
  const cell=context.CalcEditorCatalog.ALIGN_GRID.find(x=>x.label===label);
  const button=panel.createCalcGridChip(cell,'iconCompact','align-grid');button.click();
  assert.equal(sent.at(-1),command);assert.equal(button.dataset.calcState,command);
 }
});

test('fraction and logical formats apply exact Calc category indices',()=>{
 const {context}=setup();
 for(const [icon,index] of [['calc-num-fraction',7],['calc-num-boolean',8],['calc-num-text',9]]){
  const command=context.CalcEditorCatalog.NUMFMT_GRID.find(x=>x.icon===icon).unocmd;
  assert.ok(command.startsWith('.uno:NumberFormatType '));
  assert.equal(JSON.parse(command.slice(command.indexOf(' '))).NumberFormatType.value,index);
 }
});

test('black remains numeric zero and thick borders carry a distinct line width',()=>{
 const {context}=setup();const build=context.CalcEditorBorder.buildPresetCommand;
 const parse=icon=>JSON.parse(build(icon,0).split(' ').slice(1).join(' '));
 const normal=parse('calc-border-outer-solid-inner-dashed'),thick=parse('calc-border-outer-thick');
 assert.equal(normal.OuterBorder.value[0].value.Color.value,0);
 assert.ok(thick.OuterBorder.value[0].value.LineWidth.value>normal.OuterBorder.value[0].value.LineWidth.value);
 assert.equal(normal.InnerBorder.value[0].value.LineWidth.value,0);
 const inner=parse('calc-border-inner-vertical');
 assert.ok(inner.OuterBorder.value.slice(0,4).every(x=>x.value.LineWidth.value===0));
 assert.ok(inner.InnerBorder.value[1].value.LineWidth.value>0);
});

test('two diagonal controls use independent UNO commands, never full-grid geometry',()=>{
 const {context}=setup();const build=context.CalcEditorBorder.buildPresetCommand;
 assert.ok(build('calc-border-diag-tl-br',0).startsWith('.uno:BorderTLBR '));
 assert.ok(build('calc-border-diag-tr-bl',0).startsWith('.uno:BorderBLTR '));
 const payload=JSON.parse(build('calc-border-diag-tl-br',0).split(' ').slice(1).join(' '));
 assert.equal(payload.BorderTLBR.type,'any');
 assert.equal(payload.BorderTLBR.value.type,'com.sun.star.table.BorderLine2');
});

test('selected-model readback preserves mixed states and black',()=>{
 const {context,states}=setup();states({'.uno:FontHeight':null,'.uno:Bold':null,'.uno:FrameLineColor':0,'.uno:ParaIndent':127*72/2540});
 assert.equal(context.CalcEditorFormat.color('.uno:FrameLineColor'),0);
 assert.equal(context.CalcEditorFormat.checked('.uno:Bold'),undefined);
 assert.equal(context.CalcEditorFormat.numeric('.uno:ParaIndent'),3.6);
});

test('selection changes clear previous values and reject old responses',()=>{
 const {bridge,request,reply,states,select}=setup();states({'.uno:FontHeight':10});
 const old=request();select('B2');assert.equal(bridge.state('.uno:FontHeight'),undefined);
 reply(old,{'.uno:FontHeight':99});assert.equal(bridge.state('.uno:FontHeight'),undefined);
});

test('document and view changes reject a response for the previous context',()=>{
 const x=setup();const old=x.request();x.session('doc-2');x.reply(old,{'.uno:Bold':true});assert.equal(x.bridge.state('.uno:Bold'),undefined);
 const current=x.request();x.map._docLayer._viewId=8;x.reply(current,{'.uno:Bold':true});assert.equal(x.bridge.state('.uno:Bold'),undefined);
});

test('readback timeout clears previous model values',()=>{
 const x=setup();x.states({'.uno:FontHeight':12});x.request();for(const fn of [...x.timers.values()])fn();
 assert.equal(x.bridge.state('.uno:FontHeight'),undefined);assert.equal(x.events.at(-1)[1].error,'timeout');
});

test('black recolor is acknowledged, and failed recolor never reports success',async()=>{
 const x=setup();let result=x.bridge.applyBorderColor(0);
 let payload=JSON.parse(x.sent.at(-1).slice('CALC_FORMAT_STATE '.length));assert.equal(payload.borderColor,0);
 // A background read cannot discard the in-flight write acknowledgment.
 const count=x.sent.length;x.bridge.request();assert.equal(x.sent.length,count);
 x.reply(payload,{'.uno:FrameLineColor':0});assert.equal(await result,true);
 result=x.bridge.applyBorderColor(0xff0000);payload=JSON.parse(x.sent.at(-1).slice('CALC_FORMAT_STATE '.length));
 x.reply(payload,{},false);assert.equal(await result,false);
});

test('moving selection cancels a pending recolor acknowledgment',async()=>{
 const x=setup();const result=x.bridge.applyBorderColor(0);x.select('C3');assert.equal(await result,false);
});


test('missing Calc status view id recovers only from an unambiguous live view',()=>{
 const x=setup();x.map._docLayer._viewId=undefined;x.map._viewInfo={'7':{}};
 const payload=x.request();assert.equal(payload.viewId,7);assert.equal(x.map._docLayer._viewId,7);
 x.map._docLayer._viewId=undefined;x.map._viewInfo={'7':{},'8':{}};
 assert.equal(x.bridge.selectionKey(),null);
});

test('Calc status without viewid does not erase the known view',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../src/layer/tile/CalcTileLayer.js'),'utf8');
 const start=source.indexOf('// Incremental iOS status messages');
 const end=source.indexOf('console.assert(this._viewId',start);
 const apply=new Function('statusJSON','app','window',source.slice(start,end));
 const layer={_viewId:7,_map:{_viewInfo:{'7':{}}}};
 const activeDocument={setActiveViewID(id){this.activeViewID=id;}};
 apply.call(layer,{}, {activeDocument}, {ThisIsTheiOSApp:true});
 assert.equal(layer._viewId,7);assert.equal(activeDocument.activeViewID,7);
 apply.call(layer,{viewid:8}, {activeDocument}, {ThisIsTheiOSApp:true});
 assert.equal(layer._viewId,8);
});

test('merge-identical sends a selection-bound native operation instead of ordinary merge',async()=>{
 const x=setup();const result=x.bridge.mergeIdenticalCells();
 const payload=JSON.parse(x.sent.at(-1).slice('CALC_FORMAT_STATE '.length));
 assert.equal(payload.mergeSame,true);assert.equal(payload.borderColor,undefined);
 assert.deepEqual(payload.range,{sheet:0,col1:0,row1:0,col2:0,row2:0});
 x.reply(payload,{'merge.same.count':2});assert.equal(await result,true);
 assert.equal(x.bridge.state('merge.same.count'),2);
});

test('merged selection uses unmerge; an unmerged selection opens the merge options',()=>{
 const x=setup();x.panel.sendUno=command=>x.sent.push(command);
 x.states({'.uno:ToggleMergeCells':true});x.panel.onFeature({id:'merge-cells'});
 assert.equal(x.sent.at(-1),'.uno:SplitCell');
 let opened=false;x.panel.openMergeCellsDialog=()=>opened=true;
 x.states({'.uno:ToggleMergeCells':false});x.panel.onFeature({id:'merge-cells'});assert.equal(opened,true);
});

test('merge-identical chooser item calls the dedicated operation',async()=>{
 const x=setup();let options,pick,calls=0;
 x.panel.close=()=>{};x.panel.recoverDocumentCanvas=()=>{};
 x.panel.sendUno=()=>assert.fail('ordinary merge must not handle identical cells');
 x.bridge.mergeIdenticalCells=()=>{calls++;return Promise.resolve(true);};
 x.context.WriterEditorChooseDialog=class{constructor(title,items,callback){options=items;pick=callback;}open(){}};
 x.panel.openMergeCellsDialog();
 const runNext=()=>{const [id,fn]=x.timers.entries().next().value;x.timers.delete(id);fn();};
 runNext();assert.equal(options[2].value,'merge-identical');pick(options[2]);runNext();
 await Promise.resolve();assert.equal(calls,1);
});
