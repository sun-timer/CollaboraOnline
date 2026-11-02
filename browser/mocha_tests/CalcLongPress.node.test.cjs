'use strict';
const test=require('node:test'), assert=require('node:assert/strict'), fs=require('fs'), vm=require('vm'), ts=require('typescript');
const source=fs.readFileSync(require('path').join(__dirname,'../src/canvas/CanvasSectionContainer.ts'),'utf8');
function fixture(inside=true, sectionName='mouse') {
 let timer, ups=0, cancels=0, menus=0, clicks=0;
 const section={name:sectionName,boundsList:[]}, mouse={cancelSingleTouchForMultiTouch(){cancels++}};
 const layer={_docType:'spreadsheet',_selectedPart:1};
 const app={dpiScale:1,map:{_docLayer:layer},calc:{cellCursorVisible:true,cellCursorRectangle:{containsPoint:()=>inside}},CSections:{MouseControl:{name:'mouse'},Overlays:{name:'overlay'},ShapeHandlesSection:{name:'shape'}},idleHandler:{notifyActive(){}}};
 const window={ThisIsTheAndroidApp:true,AndroidCalcCellMenu:{showAfterSelection(){menus++}}};
 const context={app,window,Date,console,setTimeout:fn=>(timer=fn,1),clearTimeout:()=>{},MouseEvent:class {},cool:{}};
 vm.runInNewContext(ts.transpileModule(source+'\n;globalThis.Container=CanvasSectionContainer;', {compilerOptions:{target:ts.ScriptTarget.ES2020}}).outputText,context);
 const c=Object.create(context.Container.prototype);Object.assign(c,{multiTouch:false,positionOnMouseDown:null,calcGridPress:null,calcRejectedClickUntil:0,canvas:{},draggingSomething:false,
 clearMousePositions(){this.positionOnMouseDown=null;this.positionOnMouseUp=null;this.draggingSomething=false},
 convertPositionToCanvasLocale:e=>[e.touches?.[0]?.clientX||0,e.touches?.[0]?.clientY||0],findSectionContainingPoint:()=>section,
 iosCalcEventSection:x=>x,getSectionWithName:n=>n==='mouse'?mouse:null,getCalcTapPositionAtClient:(x,y)=>({x,y}),
 convertPositionToSectionLocale:(_s,p)=>p,propagateOnMouseDown(){},propagateOnMouseUp(){ups++},propagateOnClick(){clicks++},requestReDraw(){},isIOSCalc:()=>false});
 const e={touches:[{clientX:10,clientY:10}],changedTouches:[{clientX:10,clientY:10}]};
 return {c,e,layer,fire:()=>timer(),counts:()=>({ups,cancels,menus,clicks})};
}
test('unselected grid long press cancels and consumes touch end',()=>{const f=fixture(false);f.c.onTouchStart(f.e);f.fire();f.c.onTouchEnd(f.e);assert.deepEqual(f.counts(),{ups:0,cancels:1,menus:0,clicks:0});});
test('selected grid long press opens menu without core click',()=>{const f=fixture(true);f.c.onTouchStart(f.e);f.fire();f.c.onTouchEnd(f.e);assert.deepEqual(f.counts(),{ups:0,cancels:1,menus:1,clicks:0});});
test('header is not classified as a grid press',()=>{const f=fixture(false,'header');f.c.onTouchStart(f.e);assert.equal(f.c.calcGridPress,null);});
test('sheet change and stale native gesture cannot consume another press',()=>{const f=fixture(false);f.c.onTouchStart(f.e);assert.equal(f.c.handleCalcGridLongPress(Date.now()-5000),'other');f.layer._selectedPart++;assert.equal(f.c.rejectUnselectedCalcLongPress(),false);});
test('small movement preserves long press and true movement releases it',()=>{const a=fixture(false);a.c.onTouchStart(a.e);a.c.onTouchMove({touches:[{clientX:12,clientY:10}]});a.fire();assert.equal(a.counts().cancels,1);const b=fixture(false);b.c.onTouchStart(b.e);b.c.propagateOnMouseMove=()=>{};b.c.onTouchMove({touches:[{clientX:30,clientY:10}]});assert.equal(b.c.calcGridPress,null);});
test('object section bypasses grid interception',()=>{const f=fixture(false,'shape');f.c.onTouchStart(f.e);assert.equal(f.c.calcGridPress,null);});
