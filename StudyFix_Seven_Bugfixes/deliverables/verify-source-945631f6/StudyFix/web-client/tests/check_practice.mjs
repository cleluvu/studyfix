import assert from "node:assert/strict";
let checks=0;const check=(ok,message)=>{assert.ok(ok,message);checks++;};
const storage=new Map();const store={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};
globalThis.sessionStorage=store;globalThis.localStorage=store;globalThis.setTimeout=()=>1;
const elements=new Map();const element=()=>({innerHTML:"",appendChild(n){if(n.id)elements.set(n.id,n);},querySelector(){return {focus(){}};},setAttribute(){},remove(){elements.delete(this.id);},focus(){}});
const root=element(),toasts=element();globalThis.document={body:element(),activeElement:null,querySelector:s=>s==="#app"?root:s==="#toast-region"?toasts:null,querySelectorAll:()=>[],getElementById:id=>elements.get(id)||null,addEventListener(){},createElement:element};globalThis.confirm=()=>true;
const {state,rememberView,restoreView}=await import("../static/core/state.js");
const {practice,loadPractice,openPractice,renderPractice,submitPractice,pollPractice,savePracticeDraft,practiceClick,practiceInput}=await import("../static/features/exercises/practice.js");
const {loadPageData}=await import("../static/core/polling.js");
const exercises={A:{id:"A",title:"Bài A",mode:"BUILD_FROM_SCRATCH",topic:"Toán",difficulty:"Dễ",template:"// template A",statement:"Đề A",inputFormat:"n",outputFormat:"n!",constraints:"n<=20",publicTests:[{input:"5",expectedOutput:"120"}],practiceStatus:"NEW"},B:{id:"B",title:"Bài sửa lỗi B",mode:"DEBUG_COLLAB",topic:"Chuỗi",difficulty:"Dễ",starterCode:"// buggy B",statement:"Đề B",publicTests:[],practiceStatus:"NEW"}};
let requests=[],submissions=[],holdA=false,finishA,failNextSubmit=false,done=false,serial=0;
const response=data=>({ok:true,status:200,json:async()=>({status:"OK",data})});
globalThis.fetch=async(path,options)=>{
  const body=JSON.parse(options.body);requests.push(body);
  switch(body.type){
    case "PRACTICE_LIST":return response({exercises:Object.values(exercises),topics:["Toán","Chuỗi"],stats:{total:2,accepted:done?1:0,failed:0,fresh:done?1:2},total:2,page:1,totalPages:1});
    case "PRACTICE_GET":if(body.exerciseId==="A"&&holdA){holdA=false;return new Promise(resolve=>finishA=()=>resolve(response({exercise:exercises.A})));}return response({exercise:exercises[body.exerciseId]});
    case "PRACTICE_SUBMISSIONS":{const rows=submissions.filter(s=>!s.isRun&&s.exerciseId===body.exerciseId);return response({submissions:rows,total:rows.length,page:1,totalPages:1});}
    case "PRACTICE_SUBMIT":case "PRACTICE_RUN":{
      if(failNextSubmit){failNextSubmit=false;throw Error("network lost");}
      const isRun=body.type==="PRACTICE_RUN";const row={id:"s"+(++serial),version:isRun?0:submissions.filter(s=>!s.isRun&&s.exerciseId===body.exerciseId).length+1,exerciseId:body.exerciseId,isRun,sourceCode:body.sourceCode,status:"QUEUED",verdict:"QUEUED",submittedAt:Date.now(),passedTests:0,totalTests:2,score:0};submissions.unshift(row);return response({submission:row});
    }
    case "PRACTICE_RESULT":{const row=submissions.find(s=>s.id===body.submissionId);if(done){row.status="DONE";row.verdict="ACCEPTED";row.score=100;row.passedTests=2;exercises[row.exerciseId].practiceStatus="AC";}return response({submission:{...row}});}
    default:throw Error("Unexpected room-bound request "+body.type);
  }
};
state.identity={userId:"u",role:"STUDENT",displayName:"User"};state.token="token";state.page="exercises";state.room=null;state.booting=true;state.exerciseMode="practice";
await loadPageData();check(practice.items.length===2,"practice loads catalog with no room");
const studentView=renderPractice();state.identity.role="TEACHER";
const teacherView=renderPractice();
check(teacherView.includes('data-action="practice-create"')&&!studentView.includes('data-action="practice-create"'),"only teachers see catalog creation control");
check(teacherView.replace(/<div style="margin-bottom:16px"><button[^>]*data-action="practice-create"[^>]*>.*?<\/button><\/div>/,"")===studentView,"practice remains shared except teacher catalog creation control");
check(!studentView.includes("Bạn cần vào một phòng")&&studentView.includes("Luyện code"),"practice is not gated by classroom membership");
await openPractice("A");let html=renderPractice();
check(html.indexOf("Đề bài")<html.indexOf('id="work-code"')&&html.indexOf('id="work-code"')<html.indexOf("Lịch sử nộp"),"workspace is statement, editor, then history vertically");
check(html.includes('data-action="choose-work-code-file"')&&html.includes('id="work-code-file"'),"practice reuses existing file import action and input");
state.workCode="draft A";savePracticeDraft();await openPractice("B");
check(state.workCode==="// buggy B","debug practice starts from catalog buggy code");
await openPractice("A");check(state.workCode==="draft A","draft restored separately per exercise");
holdA=true;const lateA=openPractice("A");await openPractice("B");finishA();await lateA;
check(practice.exercise.id==="B"&&state.workCode==="// buggy B","late exercise response cannot replace newly selected exercise");
await openPractice("A");state.workCode="snapshot v1";await submitPractice();state.workCode="new editor draft v2";
check(submissions[0].sourceCode==="snapshot v1","submission captures source at click, not later editor changes");
check(practice.history[0].version===1&&practice.pending.size===1,"official submit appears as version one and is polled");
done=true;await pollPractice();
check(practice.result.verdict==="ACCEPTED"&&practice.exercise.practiceStatus==="AC","grading result and exercise AC status synchronize");
check(state.workCode==="new editor draft v2","result polling never overwrites editor draft");
await practiceClick("practice-detail",{id:submissions[0].id});
check(elements.get("active-modal")?.innerHTML.includes("snapshot v1")&&!elements.get("active-modal")?.innerHTML.includes("new editor draft v2"),"history opens immutable code snapshot, not current editor");
done=false;await submitPractice(true);
check(practice.history.length===1&&practice.result.isRun,"public run does not create official history version");
practiceInput({id:"practice-query",value:"Giai thừa"});await practiceClick("practice-filter",{});
check(requests.some(r=>r.type==="PRACTICE_LIST"&&r.query==="Giai thừa"),"search is passed to server for full catalog filtering");
await practiceClick("practice-back",{});check(!practice.exercise,"back returns to catalog");
await openPractice("A");state.workCode="retry uncertain request";failNextSubmit=true;
try{await submitPractice();}catch(_){}
await submitPractice();const retry=requests.filter(r=>r.type==="PRACTICE_SUBMIT"&&r.sourceCode==="retry uncertain request");
check(retry.length===2&&retry[0].idempotencyKey===retry[1].idempotencyKey,"network retry reuses request key to avoid duplicate versions");
rememberView();state.page="overview";
check(restoreView()==="exercises"&&!state.room,"refresh restores practice without a room");
practice.owner="";await loadPractice();check(practice.exercise?.id==="A","selected exercise restores from tab state");
state.identity={userId:"other",role:"STUDENT"};await loadPractice();
check(!practice.exercise&&practice.pending.size===0,"new account cannot inherit old exercise, source or pending requests");
check(practice.drafts.size===0&&practice.requests.size===0,"account switch clears in-memory drafts and request keys");
await openPractice("A");check(state.workCode==="// template A","another account opens its own template, never previous user's draft");
check(!requests.some(r=>["ASSIGN_CREATE","TEACHER_TRIAL_CREATE","JOIN_ROOM"].includes(r.type)),"practice does not synthesize assignments, rooms or teacher trials");
console.log(`Practice frontend: ${checks} checks PASS (HTTP/DOM mocks, no real browser claim).`);
