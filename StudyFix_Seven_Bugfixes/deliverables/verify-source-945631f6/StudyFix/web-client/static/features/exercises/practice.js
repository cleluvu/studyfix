import {state} from "../../core/state.js";
import {assignment} from "../../core/api.js";
import {setPracticeHandlers} from "../../core/polling.js";
import {render,pageHeader} from "../../ui/render.js";
import {btn,pill} from "../../ui/components.js";
import {esc,dateTime,humanVerdict,statusClass,diffClass} from "../../shared/format.js";
import {cryptoRandomId} from "../../shared/crypto.js";
import {toast} from "../../ui/toast.js";
import {modal} from "../../ui/modal.js";
import {renderSubmissionResult} from "./exercises.js";
import {openCatalogCreate} from "./catalog-create.js";

export const practice={owner:"",items:[],topics:[],stats:{},query:"",topic:"",difficulty:"",mode:"",filterStatus:"",page:1,total:0,pages:1,exercise:null,history:[],historyPage:1,historyPages:1,historyTotal:0,result:null,resultId:"",pending:new Map(),drafts:new Map(),requests:new Map(),error:"",busy:false,initialized:false};
let listRequest=0,openRequest=0,historyRequest=0;
let filterTimer=null;
const filterSignature=()=>JSON.stringify([practice.query,practice.topic,practice.difficulty,practice.mode,practice.filterStatus]);
function cancelPracticeSearch(){clearTimeout(filterTimer);filterTimer=null;}
function queuePracticeSearch(){
  cancelPracticeSearch();
  if(!active()||practice.exercise)return;
  const token=state.token,owner=state.identity?.userId,filters=filterSignature();
  const current=()=>state.token===token&&state.identity?.userId===owner&&active()&&!practice.exercise&&filterSignature()===filters;
  filterTimer=setTimeout(async()=>{
    filterTimer=null;if(!current())return;
    practice.page=1;await loadPracticeList();if(current())renderPracticeSearchResults();
  },250);
}
const active=()=>state.page==="exercises"&&state.exerciseMode!=="room";
const storageKey=suffix=>`studyfix.practice.${state.identity?.userId}.${suffix}`;
function read(key){try{return JSON.parse(localStorage.getItem(key)||"null");}catch(_){return null;}}
function write(key,value){try{localStorage.setItem(key,JSON.stringify(value));}catch(_){}}
function draftFor(id){return practice.drafts.get(id)||read(storageKey(`draft.${id}`));}
function selection(id){try{sessionStorage.setItem(storageKey("selection"),id||"");}catch(_){}}
function savedSelection(){try{return sessionStorage.getItem(storageKey("selection"))||"";}catch(_){return "";}}
function scope(){
  if(practice.owner===state.identity?.userId)return;
  cancelPracticeSearch();
  ++listRequest;++openRequest;++historyRequest;
  Object.assign(practice,{owner:state.identity?.userId||"",items:[],topics:[],stats:{},query:"",topic:"",difficulty:"",mode:"",filterStatus:"",page:1,total:0,pages:1,exercise:null,history:[],historyPage:1,historyPages:1,historyTotal:0,result:null,resultId:"",pending:new Map(),drafts:new Map(),requests:new Map(),error:"",busy:false,initialized:false});
  state.workCode="";state.workCodeDirty=false;
}
export function savePracticeDraft(){if(active()&&practice.exercise&&practice.owner===state.identity?.userId){const draft={code:state.workCode};practice.drafts.set(practice.exercise.id,draft);write(storageKey(`draft.${practice.exercise.id}`),draft);}}
const initial=e=>e.mode==="DEBUG_COLLAB"?(e.starterCode||e.template||""):(e.template||"");
const labels={AC:["✓ Đã AC","easy"],FAILED:["✕ Chưa đạt","hard"],NEW:["Chưa làm","neutral"],PENDING:["Đang chấm","medium"],ERROR:["Lỗi chấm","neutral"]};
export function practiceBadge(value){const [text,tone]=labels[value]||labels.NEW;return pill(text,tone);}
export async function loadPracticeList(){
  scope();const request=++listRequest,owner=practice.owner;
  const snapshot=JSON.stringify([practice.items,practice.stats,practice.error]);
  try {
    const result=await assignment("PRACTICE_LIST",{query:practice.query,topic:practice.topic,difficulty:practice.difficulty,mode:practice.mode,filterStatus:practice.filterStatus,page:practice.page});
    if(request!==listRequest||owner!==state.identity?.userId)return false;
    Object.assign(practice,{items:result.exercises||[],topics:result.topics||[],stats:result.stats||{},page:result.page||1,total:result.total||0,pages:result.totalPages||1,error:""});
  }catch(e){if(request!==listRequest||owner!==state.identity?.userId)return false;practice.error=e.code==="UNKNOWN_ASSIGNMENT_ACTION"?"Server đang chạy chưa có API Luyện code. Đóng Server Manager cũ rồi mở lại, sau đó tải lại trang bằng Ctrl+Shift+R.":e.message;}
  return snapshot!==JSON.stringify([practice.items,practice.stats,practice.error]);
}
export async function loadPractice(){
  scope();await loadPracticeList();
  if(!practice.initialized){practice.initialized=true;const id=savedSelection();if(id)try{await openPractice(id,false);}catch(e){practice.error=e.message;selection("");}}
  else if(practice.exercise){const draft=draftFor(practice.exercise.id);state.workCode=typeof draft?.code==="string"?draft.code:initial(practice.exercise);state.workCodeDirty=!!draft;}
}
async function refreshExerciseStatus(){
  const id=practice.exercise?.id,owner=practice.owner;if(!id)return;
  const data=await assignment("PRACTICE_GET",{exerciseId:id});
  if(id===practice.exercise?.id&&owner===state.identity?.userId){practice.exercise.practiceStatus=data.exercise.practiceStatus;practice.exercise.acceptedByMe=data.exercise.acceptedByMe;}
}
export async function loadPracticeHistory(page=practice.historyPage){
  const exercise=practice.exercise;if(!exercise)return;
  const request=++historyRequest,owner=practice.owner;
  const data=await assignment("PRACTICE_SUBMISSIONS",{exerciseId:exercise.id,page});
  if(request!==historyRequest||owner!==state.identity?.userId||practice.exercise?.id!==exercise.id)return;
  Object.assign(practice,{history:data.submissions||[],historyPage:data.page||1,historyPages:data.totalPages||1,historyTotal:data.total||0});
  for(const row of practice.history)if(["QUEUED","RUNNING"].includes(row.status))practice.pending.set(row.id,{exerciseId:exercise.id});
}
export async function openPractice(id,paint=true){
  savePracticeDraft();scope();const request=++openRequest,owner=practice.owner;
  const data=await assignment("PRACTICE_GET",{exerciseId:id});
  if(request!==openRequest||owner!==state.identity?.userId)return;
  practice.exercise=data.exercise;practice.history=[];practice.historyPage=1;practice.historyTotal=0;practice.result=null;practice.resultId="";
  const draft=draftFor(id);state.workCode=typeof draft?.code==="string"?draft.code:initial(data.exercise);state.workCodeDirty=!!draft;
  selection(id);await loadPracticeHistory(1);
  if(request!==openRequest||practice.exercise?.id!==id)return;
  if(practice.history[0]) {
    const sub=await assignment("PRACTICE_RESULT",{submissionId:practice.history[0].id});
    if(request!==openRequest||practice.exercise?.id!==id)return;
    practice.result=sub.submission;practice.resultId=sub.submission.id;
  }
  if(paint)render();
}
export async function submitPractice(run=false){
  if(!practice.exercise||practice.busy)return;
  const source=state.workCode;if(!source.trim())throw new Error("Code không được để trống.");
  if(new TextEncoder().encode(source).length>32000)throw new Error("Code tối đa 32.000 byte.");
  const exerciseId=practice.exercise.id,owner=practice.owner;
  // Lưu mã yêu cầu trước khi gửi để khi gửi lại không tạo trùng phiên bản bài nộp.
  const requestKey=storageKey(`request.${exerciseId}.${run?"run":"submit"}`),saved=practice.requests.get(requestKey)||read(requestKey);
  const key=saved?.source===source?saved.key:cryptoRandomId();practice.requests.set(requestKey,{source,key});write(requestKey,{source,key});
  practice.busy=true;savePracticeDraft();render();
  try {
    const result=await assignment(run?"PRACTICE_RUN":"PRACTICE_SUBMIT",{exerciseId,sourceCode:source,idempotencyKey:key});
    if(owner!==state.identity?.userId)return;
    practice.requests.delete(requestKey);write(requestKey,null);
    const row=result.submission;
    if(["QUEUED","RUNNING"].includes(row.status))practice.pending.set(row.id,{exerciseId});
    if(practice.exercise?.id===exerciseId){practice.result=row;practice.resultId=row.id;if(!run)await loadPracticeHistory(1);}
    await loadPracticeList();await refreshExerciseStatus();toast(run?"Đã gửi chạy thử ví dụ công khai.":`Đã nhận phiên bản v${row.version}. Kết quả sẽ cập nhật tự động.`,"success");
  }finally{if(owner===practice.owner){practice.busy=false;render();}}
}
export async function pollPractice(){
  if(!state.identity||practice.owner!==state.identity.userId||!practice.pending.size)return false;
  const owner=practice.owner;let changed=false,finished=false;
  for(const [id,task] of [...practice.pending]) {
    try {
      const data=await assignment("PRACTICE_RESULT",{submissionId:id});
      if(owner!==state.identity?.userId)return false;
      const row=data.submission;
      if(practice.resultId===id&&practice.exercise?.id===task.exerciseId){changed=changed||JSON.stringify(practice.result)!==JSON.stringify(row);practice.result=row;}
      if(!["QUEUED","RUNNING"].includes(row.status)){practice.pending.delete(id);finished=true;}
    }catch(e){if(owner!==state.identity?.userId)return false;practice.error=e.message;}
  }
  if(finished){await loadPracticeList();await loadPracticeHistory();await refreshExerciseStatus();changed=true;}
  return changed&&active();
}
export async function practiceClick(action,d){
  cancelPracticeSearch();
  switch(action){
    case "practice-create":if(state.identity?.role==="TEACHER")openCatalogCreate(async id=>{practice.query=id;practice.topic="";practice.difficulty="";practice.mode="";practice.filterStatus="";practice.page=1;await loadPracticeList();render();});break;
    case "practice-open":await openPractice(d.id);break;
    case "practice-back":savePracticeDraft();++openRequest;practice.exercise=null;practice.result=null;selection("");await loadPracticeList();render();break;
    case "practice-filter":practice.page=1;await loadPracticeList();render();break;
    case "practice-page":practice.page=Number(d.page)||1;await loadPracticeList();render();break;
    case "practice-history-page":await loadPracticeHistory(Number(d.page)||1);render();break;
    case "practice-submit":await submitPractice(false);break;
    case "practice-run":await submitPractice(true);break;
    case "practice-reset":if(confirm("Khôi phục code ban đầu sẽ thay bản nháp đang nhập. Tiếp tục?")){state.workCode=initial(practice.exercise);state.workCodeDirty=true;savePracticeDraft();render();}break;
    case "practice-detail":{
      const id=practice.exercise?.id,owner=practice.owner;
      const data=await assignment("PRACTICE_RESULT",{submissionId:d.id});if(id!==practice.exercise?.id||owner!==state.identity?.userId)return;
      const row=data.submission;
      modal(`<div class="modal-head"><div><h3>${esc(row.exerciseId)} · ${row.isRun?"Chạy thử":`v${row.version}`}</h3><p>${dateTime(row.submittedAt)} · Code đã nộp, chỉ đọc</p></div>${btn("Đóng","close-modal","btn-soft btn-sm")}</div><div class="modal-body">${renderSubmissionResult(row)}<pre class="practice-snapshot">${esc(row.sourceCode)}</pre></div>`);break;
    }
  }
}
export function practiceInput(el,composing=false){
  const fields={"practice-query":"query","practice-topic":"topic","practice-difficulty":"difficulty","practice-mode":"mode","practice-status":"filterStatus"};
  if(fields[el.id]){practice[fields[el.id]]=el.value;++listRequest;cancelPracticeSearch();if(el.id==="practice-query"||!composing)queuePracticeSearch();}
  if(el.id==="work-code")savePracticeDraft();
}
function options(values,current){return values.map(v=>`<option value="${esc(v)}" ${v===current?"selected":""}>${esc(v)}</option>`).join("");}
function pagination(page,pages,total,action){return `<div class="room-pagination">${btn("← Trước",action,"btn-soft btn-sm",`data-page="${page-1}" ${page<=1?"disabled":""}`)}<span>Trang ${page}/${pages} · ${total} ${action==="practice-page"?"bài":"phiên bản"}</span>${btn("Sau →",action,"btn-soft btn-sm",`data-page="${page+1}" ${page>=pages?"disabled":""}`)}</div>`;}
function renderPracticeRows(){return practice.items.map(e=>`<tr class="practice-row ${e.practiceStatus==="AC"?"practice-ac":e.practiceStatus==="FAILED"?"practice-failed":""}" data-action="practice-open" data-id="${esc(e.id)}"><td class="mono">${esc(e.id)}</td><td><button class="link-button" data-action="practice-open" data-id="${esc(e.id)}"><strong>${esc(e.title)}</strong></button><small class="task-sub">${e.mode==="DEBUG_COLLAB"?"Sửa lỗi":"Lập trình từ đầu"}</small></td><td>${esc(e.topic)}</td><td>${pill(e.difficulty,diffClass(e.difficulty))}</td><td>${practiceBadge(e.practiceStatus)}</td><td>${btn("Làm bài →","practice-open","btn-soft btn-sm",`data-id="${esc(e.id)}"`)}</td></tr>`).join("")||'<tr><td colspan="6" class="empty-slim">Không tìm thấy bài phù hợp.</td></tr>';}
function renderPracticeSearchResults(){
  const rows=document.getElementById("practice-catalog-rows"),pager=document.getElementById("practice-catalog-pagination");
  if(!rows||!pager){render();return;}
  // Keep the actual input node alive: replacing it would interrupt Vietnamese IME composition.
  rows.innerHTML=renderPracticeRows();
  pager.innerHTML=pagination(practice.page,practice.pages,practice.total,"practice-page");
  const warning=document.getElementById("practice-filter-error");
  if(warning){warning.textContent=practice.error;warning.hidden=!practice.error;}
}
export function renderPractice(){
  const warning=`<div id="practice-filter-error" class="notice-box warn" ${practice.error?"":"hidden"}>${esc(practice.error)}</div>`;
  if(practice.exercise)return renderPracticeWork(warning);
  const stats=practice.stats;
  return `${pageHeader("▤","Luyện code","Chọn bài, viết code và luyện tập theo tốc độ của bạn.",'<span class="chip">Không cần vào phòng · Không cần giao bài</span>')}${warning}${state.identity?.role==="TEACHER"?`<div style="margin-bottom:16px">${btn("+ Thêm bài vào ngân hàng","practice-create","btn-primary")}</div>`:""}<div class="practice-stats">${[["Tổng bài",stats.total],["Đã AC",stats.accepted],["Chưa đạt",stats.failed],["Chưa làm",stats.fresh]].map(([name,value])=>`<div class="card practice-stat"><span>${name}</span><strong>${value||0}</strong></div>`).join("")}</div><section class="card"><div class="filter-panel practice-filters"><input class="field" id="practice-query" maxlength="160" aria-label="Tìm bài" placeholder="Tìm mã hoặc tên bài…" value="${esc(practice.query)}"><select class="select" id="practice-topic" aria-label="Chủ đề"><option value="">Tất cả chủ đề</option>${options(practice.topics,practice.topic)}</select><select class="select" id="practice-difficulty" aria-label="Độ khó"><option value="">Tất cả độ khó</option>${options(["Dễ","Trung bình","Khó"],practice.difficulty)}</select><select class="select" id="practice-mode" aria-label="Loại bài"><option value="">Tất cả loại bài</option><option value="BUILD_FROM_SCRATCH" ${practice.mode==="BUILD_FROM_SCRATCH"?"selected":""}>Lập trình từ đầu</option><option value="DEBUG_COLLAB" ${practice.mode==="DEBUG_COLLAB"?"selected":""}>Sửa lỗi</option></select><select class="select" id="practice-status" aria-label="Trạng thái"><option value="">Tất cả trạng thái</option>${Object.entries(labels).map(([value,[label]])=>`<option value="${value}" ${value===practice.filterStatus?"selected":""}>${label}</option>`).join("")}</select>${btn("Tìm / Lọc","practice-filter","btn-primary")}</div><div class="table-wrap"><table class="data-table"><thead><tr><th>Mã bài</th><th>Tên bài</th><th>Chủ đề</th><th>Độ khó</th><th>Của bạn</th><th></th></tr></thead><tbody id="practice-catalog-rows">${renderPracticeRows()}</tbody></table></div><div id="practice-catalog-pagination" class="card-body">${pagination(practice.page,practice.pages,practice.total,"practice-page")}</div></section>`;
}
function renderPracticeWork(warning){
  const e=practice.exercise,row=practice.result;
  return `${btn("← Danh sách bài","practice-back","btn-soft btn-sm")}${pageHeader("▤",`${e.id} · ${e.title}`,`${e.topic} · ${e.difficulty} · Luyện tập cá nhân`,practiceBadge(e.practiceStatus))}${warning}<section class="card"><div class="card-body practice-statement"><h3>Đề bài</h3><p>${esc(e.statement)}</p><h4>Đầu vào</h4><p>${esc(e.inputFormat)}</p><h4>Đầu ra</h4><p>${esc(e.outputFormat)}</p><h4>Ràng buộc</h4><p>${esc(e.constraints)}</p>${e.hints?`<details><summary>Gợi ý</summary><p>${esc(e.hints)}</p></details>`:""}<h4>Ví dụ công khai</h4>${(e.publicTests||[]).map((test,i)=>`<div class="test-row"><strong>Ví dụ ${i+1}</strong><div>Input<pre>${esc(test.input)}</pre></div><div>Output<pre>${esc(test.expectedOutput)}</pre></div></div>`).join("")}</div></section><section class="card code-card practice-editor"><div class="card-head"><strong>Main.java</strong><span class="push"></span><span>Java · Bản nháp hiện tại</span></div><textarea id="work-code" class="code-editor" spellcheck="false" aria-label="Code Java">${esc(state.workCode)}</textarea><div class="code-actions">${btn("▷ Chạy thử","practice-run","btn-soft btn-sm",practice.busy?"disabled":"")}${btn("↑ Nộp bài","practice-submit","btn-primary btn-sm",practice.busy?"disabled":"")}${btn("Nạp code từ file","choose-work-code-file","btn-soft btn-sm")}${btn("Khôi phục code ban đầu","practice-reset","btn-soft btn-sm")}<input id="work-code-file" type="file" accept=".java,.txt,text/plain" hidden><small>Ví dụ công khai để chạy thử · Nộp bài để chấm đầy đủ</small></div><div class="card-body"><h3>Kết quả ${row?(row.isRun?"chạy thử":`phiên bản v${row.version}`):"chấm"}</h3>${row?renderSubmissionResult(row):'<div class="empty-slim">Chưa có bài nộp. Chạy thử ví dụ hoặc nộp phiên bản đầu tiên.</div>'}</div></section><section class="card"><div class="card-head"><div><h3>Lịch sử nộp của bạn</h3><p>Mỗi phiên bản giữ nguyên code và kết quả lúc nộp. Chạy thử không tính vào thành tích AC.</p></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Phiên bản</th><th>Thời điểm</th><th>Trạng thái</th><th>Test</th><th>Điểm</th><th>Thời gian</th><th></th></tr></thead><tbody>${practice.history.map(s=>`<tr><td class="mono">v${s.version}</td><td>${dateTime(s.submittedAt)}</td><td>${pill(humanVerdict(s.verdict||s.status),statusClass(s.verdict||s.status))}</td><td>${s.passedTests}/${s.totalTests}</td><td>${s.score}/100</td><td>${s.durationMs||0} ms</td><td>${btn("Xem code / kết quả","practice-detail","btn-soft btn-sm",`data-id="${esc(s.id)}"`)}</td></tr>`).join("")||'<tr><td colspan="7" class="empty-slim">Bạn chưa nộp bài này.</td></tr>'}</tbody></table></div><div class="card-body">${pagination(practice.historyPage,practice.historyPages,practice.historyTotal,"practice-history-page")}</div></section>`;
}
setPracticeHandlers({load:loadPractice,poll:pollPractice});
