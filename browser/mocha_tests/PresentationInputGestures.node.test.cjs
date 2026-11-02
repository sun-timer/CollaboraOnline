 'use strict';
const assert=require('node:assert/strict'), fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const test=require('node:test');
const source=fs.readFileSync(path.join(__dirname,'../src/app/AndroidSelectionMenu.ts'),'utf8');
function harness(ios=false) {
 let selected=false,now=10000;const calls=[],timers=new Map();let id=0;
 const layer={_postMouseEvent:()=>calls.push('bypass'),_docType:'presentation',_selectedPart:0};
 const app={definitions:{},LOButtons:{left:1},pixelsToTwips:1,map:{_docLayer:layer,getDocType:()=> 'presentation',isEditMode:()=>true,isReadOnlyMode:()=>false},
 file:{textCursor:{visible:false,rectangle:{x1:20,y1:20,x2:20,y2:40}}},socket:{sendMessage:x=>calls.push(x)}};
 const window={ThisIsTheAndroidApp:false,ThisIsTheiOSApp:false,setTimeout:fn=>{timers.set(++id,fn);return id},clearTimeout:i=>timers.delete(i),postMobileMessage:x=>calls.push(x)};
 const doc={addEventListener(){}};
 const DateMock={now:()=>now};
 const context=vm.createContext({window,document:doc,app,console,Date:DateMock,CanvasSectionObject:class {},MouseControl:{readModifier:()=>0}});
 vm.runInContext(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.None}}).outputText,context);
 window.ThisIsTheAndroidApp=!ios;window.ThisIsTheiOSApp=ios;
 const m=window.AndroidSelectionMenu;
 m.hasNonDegenerateSelection=()=>selected;
 m.cancelGesture=()=>{m.pendingLongPressSelection=false;};
 m.clearLocalTextSelection=()=>{selected=false;calls.push('clearRange');};
 m.resetForNewGesture=()=>{m.cancelGesture();m.clearLocalTextSelection();};
 m.isKeyboardOpen=()=>false;app.map.focus=()=>calls.push('focus');
 const mouse={sendClick:(_i,n)=>{calls.push('click'+n);if(n===2){app.file.textCursor.visible=true;m.onTextCaretUpdate(true);}}};
 const click=()=>m.dispatchPresentationTap(mouse,{}, {x:20,y:20},true);
 app.activeDocument={mouseControl:mouse};
 layer._postMouseEvent=(kind,x,y,count)=>{calls.push('core:'+kind+':'+count);calls.push([x,y]);if(kind==='buttonup'&&count===2){app.file.textCursor.visible=true;m.onTextCaretUpdate(true)}};
 const shapeSource=fs.readFileSync(path.join(__dirname,'../src/canvas/sections/ShapeHandlesSection.ts'),'utf8');
 vm.runInContext(ts.transpileModule(shapeSource,{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.None}}).outputText,context);
 const shape=Object.create(app.definitions.shapeHandlesSection.prototype);shape.position=[0,0];shape.sectionProperties={};shape.stopPropagating=()=>calls.push('stopPropagation');
 const point=()=>({pX:20,pY:20,get x(){return this.pX},get y(){return this.pY},clone(){return {x:this.x,y:this.y}}});
 return {m,app,calls,mouse,click,shape,point,advance:n=>{now+=n},select:v=>{selected=v}};
}
for(const ios of [false,true]) {
const name=ios?'iOS':'Android';
test(name+' real selected-shape handler preserves document coordinates and owns double input',()=>{const h=harness(ios);const e={pointerType:'touch'};h.shape.position=[100,200];h.shape.onClick(h.point(),e);h.advance(100);h.shape.onClick(h.point(),e);assert.equal(h.calls.filter(x=>x==='core:buttondown:2').length,1);assert.deepEqual(h.calls.filter(Array.isArray),[[120,220],[120,220],[120,220],[120,220]]);assert.ok(h.calls.some(x=>typeof x==='string'&&(x==='focus'||x.startsWith('SHOWTEXTIME '))));h.shape.onDoubleClick(h.point(),e);assert.equal(h.calls.filter(x=>x==='core:buttondown:2').length,1);});

test(name+' object double tap enters input and requests keyboard, without word selection',()=>{const h=harness(ios);h.click();h.advance(100);h.click();assert.ok(h.calls.includes('click2'));assert.ok(h.calls.some(x=>x==='focus'||x.startsWith('SHOWTEXTIME ')));assert.equal(h.m.pendingLongPressSelection,false);});
test(name+' double tap in text uses single core clicks instead of selecting a word',()=>{const h=harness(ios);h.app.file.textCursor.visible=true;h.click();h.advance(100);h.click();assert.equal(h.calls.filter(x=>x==='click1').length,2);assert.ok(!h.calls.includes('click2'));assert.equal(h.m.pendingLongPressSelection,false);});
test(name+' selected range tap moves caret and requests keyboard',()=>{const h=harness(ios);h.app.file.textCursor.visible=true;h.select(true);h.click();assert.deepEqual(h.calls.slice(0,2),['clearRange','click1']);assert.ok(!h.calls.includes('resetselection'));assert.ok(h.calls.some(x=>x==='focus'||x.startsWith('SHOWTEXTIME ')));});
test(name+' core selection from input double click collapses, later long press is preserved',()=>{const h=harness(ios);h.click();h.advance(100);h.click();assert.equal(h.m.consumePresentationInputSelection('1,2,3,4'),true);assert.ok(h.calls.some(x=>x.includes('.uno:GoLeft')));h.m.cancelTextInputRequest('long_press');assert.equal(h.m.consumePresentationInputSelection('1,2,3,4'),false);});
test(name+' standalone presentation double tap never uses generic word selection',()=>{const h=harness(ios);h.m.selectWordAtTwips=()=>{throw Error('word selection called')};h.m.onDoubleTapAtSectionPoint({});});
}
