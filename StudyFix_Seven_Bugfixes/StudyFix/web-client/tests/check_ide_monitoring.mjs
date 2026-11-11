import assert from 'node:assert/strict';
const events=[],listeners=new Map(),windows=new Map(),timers=new Map(),storage=new Map();let hidden=false,focused=true,now=Date.now(),timer=0,kind='BROWSER',seq=1,changes=[],failed=false,boot='bridge-test';
Date.now=()=>now;
globalThis.sessionStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)};
globalThis.setTimeout=(fn,ms)=>{timers.set(++timer,{fn,ms});return timer;};globalThis.clearTimeout=id=>timers.delete(id);
const node={innerHTML:'',hidden:false};globalThis.document={get hidden(){return hidden},hasFocus:()=>focused,querySelector:()=>null,getElementById:id=>id==='exam-student-status'?node:null,addEventListener:(type,fn)=>{if(!listeners.has(type))listeners.set(type,[]);listeners.get(type).push(fn)}};
globalThis.addEventListener=(type,fn)=>windows.set(type,fn);
globalThis.fetch=async(url,options)=>{
 if(String(url).startsWith('http://127.0.0.1:18765')){if(failed)throw Error('bridge offline');return {ok:true,json:async()=>({protocol:1,boot,sequence:seq,sampledAt:now,kind,truncated:false,changes})};}
 const body=JSON.parse(options.body);if(body.type==='EXAM_EVENT')events.push(body);return {ok:true,status:200,json:async()=>({status:'OK',data:body.type==='EXAM_STATUS'?{enabled:true,live:true,generation:'g',examSessionId:'session',startAt:0,endAt:now+1000000,serverTime:now}:{accepted:true}})};
};
const {state}=await import('../static/core/state.js');const {loadExam,examClick}=await import('../static/features/contests/exam-monitoring.js');
state.token='t';state.identity={userId:'student',role:'STUDENT'};state.room={roomId:'r'};state.page='contests';state.selectedContest={id:'exam'};
const settle=()=>new Promise(resolve=>setImmediate(resolve));
const fire=type=>{for(const fn of listeners.get(type)||[])fn({});};
async function run(ms){const item=[...timers].find(([,t])=>t.ms===ms);assert.ok(item,`timer ${ms}`);timers.delete(item[0]);await item[1].fn();await settle();}
function change(next){now+=700;kind=next;changes.push({sequence:++seq,at:now,kind:next});}
const count=()=>events.filter(e=>e.eventType==='TAB_SWITCH').length;
await loadExam('exam');await examClick('exam-connect-ide',{});assert.ok(node.innerHTML.includes('đã kết nối'));
change('VSCODE');hidden=true;focused=false;fire('visibilitychange');await run(250);assert.equal(count(),0,'VS Code does not trigger a warning');
change('NETBEANS');await run(650);assert.equal(count(),0,'NetBeans does not trigger a warning');
change('BROWSER');await run(650);assert.equal(count(),1,'browser opened directly from NetBeans still triggers a warning');
change('VSCODE');await run(650);assert.equal(events.filter(e=>e.eventType==='TAB_RETURN').length,1,'return to allowed IDE ends forbidden episode');
change('BROWSER');now+=700;hidden=false;focused=true;windows.get('focus')();await settle();assert.equal(count(),2,'browser visit is retained even when returning before the next web poll');
change('VSCODE');hidden=true;focused=false;fire('visibilitychange');await run(250);assert.equal(count(),2);
failed=true;await run(650);assert.equal(count(),3,'helper disconnect falls back to warning while away');
failed=false;change('NETBEANS');await run(650);assert.equal(count(),3,'helper recovery restores NetBeans exception');
change('OTHER');await run(650);assert.equal(count(),4,'unapproved applications are not whitelisted');
change('NETBEANS');await run(650);hidden=false;focused=true;now+=50;windows.get('focus')();change('BROWSER');await run(650);assert.equal(count(),4,'direct return from IDE to exam does not create a warning');
// Same-browser tab switch must warn even though native process category stays BROWSER.
hidden=true;focused=false;fire('visibilitychange');await run(250);assert.equal(count(),5,'switching another tab in same browser warns');
state.page='overview';change('VSCODE');await run(650);assert.equal(count(),5,'leaving StudyFix exam is never exempted by IDE');
state.token='';await loadExam('exam');
console.log('IDE focus PASS: VS Code, NetBeans, IDE-to-browser, delayed history, disconnect/recovery, other app, direct return, same-browser tab and navigation.');
const {IdeFocusMonitor}=await import('../static/features/contests/ide-focus.js');
for(const bad of [{sampledAt:now-6000},{sampledAt:undefined},{kind:'JAVA'},{sequence:-1},{truncated:true},{changes:[{sequence:1,at:now,kind:'VSCODE_FAKE'}]}]){
 let granted=false,failedClosed=false;const monitor=new IdeFocusMonitor({active:()=>true,focused:()=>false,onSample:()=>{granted=true},onFailure:()=>{failedClosed=true},request:async()=>({ok:true,json:async()=>({protocol:1,boot:'test',sequence:1,sampledAt:now,kind:'VSCODE',changes:[],...bad})})});
 await assert.rejects(()=>monitor.connect());assert.ok(failedClosed&&!granted,'invalid or stale samples cannot grant IDE exception');monitor.reset();
}
console.log('IDE sample validation PASS: stale/missing timestamps, unknown app, invalid sequence, dropped history and malformed changes fail closed.');