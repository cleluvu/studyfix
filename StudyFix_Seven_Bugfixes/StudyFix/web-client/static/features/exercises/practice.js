import {loadAssignedWeeks,renderAssignedWeeks,openWeekAssignmentDialog,loadWeekProgress,pollWeekAssignments} from './week-assignments.js';
import {openOjImport} from "./oj-import.js";
import { loadDebugLibrary, renderDebugLibrary, pollDebugLibrary } from "../classroom/debug-lessons.js";
import {safeMarkdown} from "../problem-builder/markdown.js";
import {viewBankQuestion,editBankQuestion,deleteBankQuestion} from "./teacher-bank.js";
import {weekBank,selectedWeek,loadWeeks,openWeekForm,openWeekManager,openWeekPicker} from "./week-bank.js";
import {renderWeekBank} from "./week-bank-view.js";
import {templateIcon} from "../../ui/template.js";
import {renderBuilder} from "../problem-builder/view.js";
import {renderBankTabs,renderBankTopbar} from "./question-bank-view.js";
import {renderQuestionBank} from "./question-bank-view.js";
import {state} from "../../core/state.js";
import {assignment} from "../../core/api.js";
import {setPracticeHandlers,loadCatalog,loadStudents,navigate} from "../../core/polling.js";
import {render,pageHeader} from "../../ui/render.js";
import {btn,pill} from "../../ui/components.js";
import {esc,dateTime,humanVerdict,statusClass,diffClass} from "../../shared/format.js";
import {cryptoRandomId} from "../../shared/crypto.js";
import {toast} from "../../ui/toast.js";
import {modal} from "../../ui/modal.js";
import {renderSubmissionResult,renderAssignmentForm} from "./exercises.js";


export const practice={owner:"",items:[],topics:[],stats:{},query:"",topic:"",bankAssignment:false,selected:new Map(),difficulty:"",mode:"",filterStatus:"",page:1,total:0,pages:1,exercise:null,history:[],historyPage:1,historyPages:1,historyTotal:0,result:null,resultId:"",pending:new Map(),drafts:new Map(),requests:new Map(),error:"",busy:false,initialized:false};
let listRequest=0,openRequest=0,historyRequest=0;
let filterTimer=null;
const filterSignature=()=>JSON.stringify([state.bankView,weekBank.id,practice.query,practice.topic,practice.difficulty,practice.mode,practice.filterStatus]);
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
  Object.assign(practice,{owner:state.identity?.userId||"",items:[],topics:[],stats:{},query:"",topic:"",bankAssignment:false,selected:new Map(),difficulty:"",mode:"",filterStatus:"",page:1,total:0,pages:1,exercise:null,history:[],historyPage:1,historyPages:1,historyTotal:0,result:null,resultId:"",pending:new Map(),drafts:new Map(),requests:new Map(),error:"",busy:false,initialized:false});
  state.workCode="";state.workCodeDirty=false;
}
export function savePracticeDraft(){if(active()&&practice.exercise&&practice.owner===state.identity?.userId){const draft={code:state.workCode};practice.drafts.set(practice.exercise.id,draft);write(storageKey(`draft.${practice.exercise.id}`),draft);}}
const initial=e=>e.mode==="DEBUG_COLLAB"?(e.starterCode||e.template||""):(e.template||"");
const labels={AC:["✓ Đã AC","easy"],ATTEMPTED:["Đã làm","neutral"],FAILED:["✕ Chưa đạt","hard"],NEW:["Chưa làm","neutral"],PENDING:["Đang chấm","medium"],ERROR:["Lỗi chấm","neutral"]};
export function practiceBadge(value){const [text,tone]=labels[value]||labels.NEW;return pill(text,tone);}
export async function loadPracticeList(){
  scope();const request=++listRequest,owner=practice.owner;
  const snapshot=JSON.stringify([practice.items,practice.stats,practice.error]);
  try {
    if(state.bankView==="weeks"&&!weekBank.id){Object.assign(practice,{items:[],topics:[],stats:{total:0,accepted:0,failed:0,fresh:0},total:0,page:1,pages:1,error:""});return true;}
    const result=await assignment(state.identity?.role==="TEACHER"?"BANK_LIST":"PRACTICE_LIST",{bankScope:state.bankView==="private"?"EXAM_PRIVATE":"PRACTICE",...(state.bankView==="weeks"?{weekId:weekBank.id}:{}),query:practice.query,topic:practice.topic,difficulty:practice.difficulty,mode:practice.mode,filterStatus:practice.filterStatus,page:practice.page});
    if(request!==listRequest||owner!==state.identity?.userId)return false;
    Object.assign(practice,{items:result.exercises||[],topics:result.topics||[],stats:result.stats||{},page:result.page||1,total:result.total||0,pages:result.totalPages||1,error:""});
  }catch(e){if(request!==listRequest||owner!==state.identity?.userId)return false;practice.error=e.code==="UNKNOWN_ASSIGNMENT_ACTION"?"Server đang chạy chưa có API Ngân hàng câu hỏi. Đóng Server Manager cũ rồi mở lại, sau đó tải lại trang bằng Ctrl+Shift+R.":e.message;}
  return snapshot!==JSON.stringify([practice.items,practice.stats,practice.error]);
}
export async function loadPractice(){
  if(state.bankView==="debug"&&state.identity?.role==="TEACHER"){await loadDebugLibrary();return;}
  scope();if(state.bankView==="weeks"&&state.identity?.role==="STUDENT"){practice.exercise=null;selection("");await loadAssignedWeeks();return;}if(state.bankView==="create"&&state.identity?.role==="TEACHER"){const {openBuilderPage}=await import("../problem-builder/controller.js");await openBuilderPage();return;}if(state.bankView==="weeks"){await loadWeeks();if(weekBank.tab==="assigned")await loadWeekProgress();}await loadPracticeList();
  if(state.identity?.role==="TEACHER"){practice.exercise=null;selection("");practice.initialized=true;return;}
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
  if(state.page==="exercises"&&state.bankView==="debug"&&state.identity?.role==="TEACHER")return pollDebugLibrary();
  if(state.bankView==="weeks"&&active())return pollWeekAssignments();
  if(!state.identity||state.identity.role==="TEACHER"||practice.owner!==state.identity.userId||!practice.pending.size)return false;
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
async function refreshWeekBank(){await loadWeeks();await loadPracticeList();practice.selected.clear();practice.bankAssignment=false;render();}
export async function practiceClick(action,d){
  cancelPracticeSearch();
  switch(action){
    case "practice-select":{
      if(state.identity?.role!=="TEACHER")break;const e=practice.items.find(e=>e.id===d.id);if(!e)break;
      if(practice.selected.has(e.id))practice.selected.delete(e.id);else practice.selected.set(e.id,{id:e.id,mode:e.mode,publication:e.publication,oj:e.oj});render();break;
    }
    case "practice-select-page":{
      if(state.identity?.role!=="TEACHER")break;const remove=practice.items.every(e=>practice.selected.has(e.id));
      for(const e of practice.items)if(remove)practice.selected.delete(e.id);else practice.selected.set(e.id,{id:e.id,mode:e.mode,publication:e.publication,oj:e.oj});render();break;
    }
    case "practice-bank-contest":{
      if(state.identity?.role!=="TEACHER")throw new Error("Chỉ giáo viên được tạo đề thi.");
      const selected=[...practice.selected.values()];
      if(!selected.length||selected.length>200||selected.some(e=>e.mode!=="BUILD_FROM_SCRATCH"||e.publication==="DRAFT"))throw new Error("Chọn 1–200 câu hỏi Lập trình từ đầu đã phát hành để tạo bộ đề.");
      const {createExamFromQuestions}=await import("../exam-bank/index.js");await createExamFromQuestions(selected.map(e=>e.id));break;
    }
    case "practice-bank-assign":{
      if(state.bankView==="weeks"){await openWeekAssignmentDialog(practice);break;}
      if(state.identity?.role!=="TEACHER"||!state.room)throw new Error("Giáo viên cần vào lớp trước khi giao bài.");
      if(practice.selected.size!==1)throw new Error("Chọn một câu hỏi để giao.");const id=practice.selected.keys().next().value;if(practice.selected.get(id)?.publication==="DRAFT")throw new Error("Xuất bản đề trước khi giao bài.");
      await loadCatalog();await loadStudents();const data=await assignment("CATALOG_GET",{exerciseId:id});state.chosenExercise=data.exercise;practice.bankAssignment=true;state.assignmentScope="ROOM";render();break;
    }
    case "practice-view":savePracticeDraft();practice.exercise=null;selection("");practice.selected.clear();practice.query="";practice.topic="";practice.difficulty="";practice.mode="";practice.filterStatus="";practice.page=1;if(d.view==="create")state.creationBankScope=state.bankView==="private"?"EXAM_PRIVATE":"PRACTICE";state.bankView=(state.identity?.role==="TEACHER"?["all","weeks","private","create","debug"]:["all","weeks"]).includes(d.view)?d.view:"all";await navigate("exercises");break;
    case "practice-week-select":weekBank.id=d.id;weekBank.tab="list";practice.query="";practice.topic="";practice.difficulty="";practice.mode="";practice.filterStatus="";practice.selected.clear();practice.page=1;await loadPracticeList();render();break;
    case "practice-week-tab":weekBank.tab=["list","info","stats","assigned"].includes(d.tab)?d.tab:"list";if(weekBank.tab==="assigned")await loadWeekProgress();render();break;
    case "practice-week-new":openWeekForm(null,refreshWeekBank);break;
    case "practice-week-edit":openWeekForm(selectedWeek(),refreshWeekBank);break;
    case "practice-week-manage":openWeekManager(refreshWeekBank);break;
    case "practice-week-add":openWeekPicker(refreshWeekBank);break;
    case "practice-week-remove":{
      if(!selectedWeek()?.isMine)throw new Error("Không có quyền sửa Week.");
      if(confirm("Gỡ bài này khỏi Week? Bài vẫn còn trong ngân hàng.")){await assignment("PRACTICE_WEEK_REMOVE",{weekId:weekBank.id,exerciseIds:[d.id]});practice.selected.delete(d.id);await refreshWeekBank();}break;
    }
    case "practice-oj-import":openOjImport(refreshWeekBank,state.bankView==="private"?"EXAM_PRIVATE":"PRACTICE");break;
    case "practice-oj-publish":{const ids=[...practice.selected.values()].filter(e=>e.oj).map(e=>e.id);if(!ids.length)throw Error("Chọn đề OJ trước.");if(!confirm("Bạn xác nhận đã rà soát nội dung và muốn phát hành các đề đã chọn?"))break;await assignment("OJ_PUBLISH_BATCH",{exerciseIds:ids});practice.selected.clear();await refreshWeekBank();toast("Đã phát hành các đề được duyệt.","success");break;}
    case "practice-create":if(state.identity?.role==="TEACHER"){const {openBuilderDialog}=await import("../problem-builder/controller.js");await openBuilderDialog(async()=>{await loadPracticeList();render();});}break;
    case "practice-bank-view":await viewBankQuestion(d.id,refreshWeekBank);break;
    case "practice-bank-edit":await editBankQuestion(d.id,refreshWeekBank);break;
    case "practice-bank-delete":await deleteBankQuestion(d.id,d.revision,refreshWeekBank);break;
    case "practice-open":if(state.identity?.role==="TEACHER")await viewBankQuestion(d.id,refreshWeekBank);else await openPractice(d.id);break;
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
function pagination(page,pages,total,action){return `<div class="room-pagination">${btn("← Trước",action,"btn-soft btn-sm",`data-page="${page-1}" ${page<=1?"disabled":""}`)}<span>Trang ${page}/${pages} · ${total} ${action==="practice-page"?"câu hỏi":"phiên bản"}</span>${btn("Sau →",action,"btn-soft btn-sm",`data-page="${page+1}" ${page>=pages?"disabled":""}`)}</div>`;}
function renderPracticeRows(){
  const teacher=state.identity?.role==="TEACHER",weeks=state.bankView==="weeks";
  if(teacher)return practice.items.map(e=>`<tr class="bank-management-row" data-action="practice-bank-view" data-id="${esc(e.id)}"><td class="bank-check-cell"><input type="checkbox" data-action="practice-select" data-id="${esc(e.id)}" aria-label="Chọn ${esc(e.title)}" ${practice.selected.has(e.id)?"checked":""}/></td><td class="bank-question-id">${esc(e.displayCode||e.id)}</td><td><button class="link-button" data-action="practice-bank-view" data-id="${esc(e.id)}">${esc(e.title)}</button><small class="bank-revision">v${e.revision||1}</small></td><td><span class="bank-category">${templateIcon("folder")}${esc(e.topic)}</span></td><td>${pill(e.difficulty,diffClass(e.difficulty))}</td>${weeks?"":`<td class="bank-usage">${Number(e.usageCount)||0}</td>`}<td>${btn(templateIcon("eye"),"practice-bank-view","bank-icon-action",`data-id="${esc(e.id)}" title="Xem đề" aria-label="Xem đề ${esc(e.title)}"`)}${btn(templateIcon("edit"),"practice-bank-edit","bank-icon-action",`data-id="${esc(e.id)}" title="Sửa đề" aria-label="Sửa đề ${esc(e.title)}" ${e.canManage?"":"disabled"}`)}${btn(templateIcon("trash"),"practice-bank-delete","bank-icon-action bank-remove-question",`data-id="${esc(e.id)}" data-revision="${e.revision}" title="Xóa đề khỏi ngân hàng" aria-label="Xóa đề ${esc(e.title)}" ${e.canManage?"":"disabled"}`)}${weeks&&selectedWeek()?.isMine?btn("×","practice-week-remove","bank-icon-action",`data-id="${esc(e.id)}" title="Gỡ khỏi Week (giữ đề)" aria-label="Gỡ ${esc(e.title)} khỏi Week"`):""}</td></tr>`).join("")||`<tr><td colspan="${weeks?6:7}" class="empty-slim">Không có đề phù hợp.</td></tr>`;
  return practice.items.map(e=>`<tr class="practice-row ${e.practiceStatus==="AC"?"practice-ac":e.practiceStatus==="FAILED"?"practice-failed":""}" data-action="practice-open" data-id="${esc(e.id)}">
    ${teacher?`<td class="bank-check-cell"><input type="checkbox" data-action="practice-select" data-id="${esc(e.id)}" aria-label="Chọn ${esc(e.title)}" ${practice.selected.has(e.id)?"checked":""}/></td>`:""}
    <td class="bank-question-id">${esc(e.id)}</td>
    <td><button class="link-button" data-action="practice-open" data-id="${esc(e.id)}"><strong>${esc(e.title)}</strong></button>${weeks&&["AC","FAILED"].includes(e.practiceStatus)?`<span class="bank-week-result">${practiceBadge(e.practiceStatus)}</span>`:""}</td>
    <td><span class="bank-category">${templateIcon("folder")}${esc(e.topic||"Chưa phân loại")}</span></td>
    <td>${pill(e.difficulty,diffClass(e.difficulty))}</td>${weeks?"":`<td>${practiceBadge(e.practiceStatus)}</td>`}
    <td>${btn(templateIcon("eye"),"practice-open","bank-icon-action bank-open-question",`data-id="${esc(e.id)}" title="Xem bài / Làm bài" aria-label="Xem bài ${esc(e.title)}"`)}${teacher&&weeks&&selectedWeek()?.isMine?btn(templateIcon("trash"),"practice-week-remove","bank-icon-action bank-remove-question",`data-id="${esc(e.id)}" title="Gỡ khỏi Week" aria-label="Gỡ ${esc(e.title)} khỏi Week"`):""}</td>
  </tr>`).join("")||`<tr><td colspan="${weeks?(teacher?6:5):(teacher?7:6)}" class="empty-slim">Không tìm thấy bài phù hợp. Thử từ khóa hoặc bộ lọc khác.</td></tr>`;
}
function renderPracticeSearchResults(){
  const rows=document.getElementById("practice-catalog-rows"),pager=document.getElementById("practice-catalog-pagination");
  if(!rows||!pager){render();return;}
  // Keep the actual input node alive: replacing it would interrupt Vietnamese IME composition.
  rows.innerHTML=renderPracticeRows();
  pager.innerHTML=pagination(practice.page,practice.pages,practice.total,"practice-page");
  const selectPage=document.getElementById("bank-select-page");if(selectPage)selectPage.checked=practice.items.length>0&&practice.items.every(e=>practice.selected.has(e.id));
  const count=document.getElementById("practice-catalog-count");if(count)count.textContent=`${practice.total} câu hỏi`;
  const warning=document.getElementById("practice-filter-error");
  if(warning){warning.textContent=practice.error;warning.hidden=!practice.error;}
}
export function renderPractice(){
  if(state.bankView==="debug"&&state.identity?.role==="TEACHER")return renderDebugLibrary();
  if(state.bankView==="weeks"&&state.identity?.role==="STUDENT")return renderAssignedWeeks();
  if(state.bankView==="create"&&state.identity?.role==="TEACHER")return `<div class="question-bank is-teacher">${renderBankTopbar(state,"Tạo bài tập")}<div class="bank-page">${renderBankTabs(state)}${renderBuilder()}</div></div>`;
  const warning=`<div id="practice-filter-error" class="notice-box warn" ${practice.error?"":"hidden"}>${esc(practice.error)}</div>`;
  if(practice.exercise&&state.identity?.role!=="TEACHER")return `<div class="question-bank-work">${renderPracticeWork(warning)}</div>`;
  const filters=`<div class="filter-panel practice-filters bank-filters"><label class="bank-search"><span class="bank-filter-label">Tìm kiếm</span><input class="field" id="practice-query" maxlength="160" aria-label="Tìm câu hỏi" placeholder="Tìm theo mã hoặc tên câu hỏi…" value="${esc(practice.query)}"></label><label><span class="bank-filter-label">${state.bankView==="weeks"?"Dạng bài":"Week"}</span><select class="select" id="practice-topic" aria-label="${state.bankView==='weeks'?'Dạng bài':'Week / Dạng bài'}"><option value="">${state.bankView==="weeks"?"Dạng bài: Tất cả":"Week: Tất cả"}</option>${options(practice.topics,practice.topic)}</select></label><label><span class="bank-filter-label">Loại bài</span><select class="select" id="practice-mode" aria-label="Loại bài"><option value="">Tất cả loại bài</option><option value="PROGRAM" ${practice.mode==="PROGRAM"?"selected":""}>Program</option><option value="METHOD" ${practice.mode==="METHOD"?"selected":""}>Method</option><option value="CLASS" ${practice.mode==="CLASS"?"selected":""}>Class</option></select></label><label><span class="bank-filter-label">Độ khó</span><select class="select" id="practice-difficulty" aria-label="Độ khó"><option value="">Tất cả độ khó</option>${options(["Dễ","Trung bình","Khó"],practice.difficulty)}</select></label>${state.identity?.role==="TEACHER"?"":`<label><span class="bank-filter-label">Kết quả của bạn</span><select class="select" id="practice-status" aria-label="Trạng thái"><option value="">Tất cả trạng thái</option>${Object.entries(labels).map(([value,[label]])=>`<option value="${value}" ${value===practice.filterStatus?"selected":""}>${label}</option>`).join("")}</select></label>`}</div>`;
  const renderBank=state.bankView==="weeks"?renderWeekBank:renderQuestionBank;
  return renderBank(practice,state,{weekBank,warning,filters,rows:renderPracticeRows(),pagination:pagination(practice.page,practice.pages,practice.total,"practice-page"),assignment:state.identity?.role==="TEACHER"&&practice.bankAssignment&&state.chosenExercise?renderAssignmentForm(state.chosenExercise):""});
}
function renderPracticeWork(warning){
  const e=practice.exercise,row=practice.result;
  return `${btn("← Danh sách bài","practice-back","btn-soft btn-sm")}${pageHeader("▤",`${e.id} · ${e.title}`,`${e.topic} · ${e.difficulty} · Luyện tập cá nhân`,practiceBadge(e.practiceStatus))}${warning}<section class="card"><div class="card-body practice-statement"><h3>Đề bài</h3><article class="pb-markdown">${safeMarkdown(e.statement)}</article><h4>Đầu vào</h4><p>${esc(e.inputFormat)}</p><h4>Đầu ra</h4><p>${esc(e.outputFormat)}</p><h4>Ràng buộc</h4><p>${esc(e.constraints)}</p>${e.hints?`<details><summary>Gợi ý</summary><p>${esc(e.hints)}</p></details>`:""}<h4>Ví dụ công khai</h4>${(e.publicTests||[]).map((test,i)=>`<div class="test-row"><strong>Ví dụ ${i+1}</strong><div>Input<pre>${esc(test.input)}</pre></div><div>Output<pre>${esc(test.expectedOutput)}</pre></div></div>`).join("")}</div></section><section class="card code-card practice-editor"><div class="card-head"><strong>${esc(e.entryFile||"Main.java")}</strong><span class="push"></span><span>Java · Bản nháp hiện tại</span></div><textarea id="work-code" class="code-editor" spellcheck="false" aria-label="Code Java">${esc(state.workCode)}</textarea><div class="code-actions">${btn("▷ Chạy thử","practice-run","btn-soft btn-sm",practice.busy?"disabled":"")}${btn("↑ Nộp bài","practice-submit","btn-primary btn-sm",practice.busy?"disabled":"")}${btn("Nạp code từ file","choose-work-code-file","btn-soft btn-sm")}${btn("Khôi phục code ban đầu","practice-reset","btn-soft btn-sm")}<input id="work-code-file" type="file" accept=".java,.txt,text/plain" hidden><small>Ví dụ công khai để chạy thử · Nộp bài để chấm đầy đủ</small></div><div class="card-body"><h3>Kết quả ${row?(row.isRun?"chạy thử":`phiên bản v${row.version}`):"chấm"}</h3>${row?renderSubmissionResult(row):'<div class="empty-slim">Chưa có bài nộp. Chạy thử ví dụ hoặc nộp phiên bản đầu tiên.</div>'}</div></section><section class="card"><div class="card-head"><div><h3>Lịch sử nộp của bạn</h3><p>Mỗi phiên bản giữ nguyên code và kết quả lúc nộp. Chạy thử không tính vào thành tích AC.</p></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Phiên bản</th><th>Thời điểm</th><th>Trạng thái</th><th>Test</th><th>Điểm</th><th>Thời gian</th><th></th></tr></thead><tbody>${practice.history.map(s=>`<tr><td class="mono">v${s.version}</td><td>${dateTime(s.submittedAt)}</td><td>${pill(humanVerdict(s.verdict||s.status),statusClass(s.verdict||s.status))}</td><td>${s.passedTests}/${s.totalTests}</td><td>${s.score}/100</td><td>${s.durationMs||0} ms</td><td>${btn("Xem code / kết quả","practice-detail","btn-soft btn-sm",`data-id="${esc(s.id)}"`)}</td></tr>`).join("")||'<tr><td colspan="7" class="empty-slim">Bạn chưa nộp bài này.</td></tr>'}</tbody></table></div><div class="card-body">${pagination(practice.historyPage,practice.historyPages,practice.historyTotal,"practice-history-page")}</div></section>`;
}
setPracticeHandlers({load:loadPractice,poll:pollPractice});
