import {setExamUi} from "../../ui/exam-ui.js";
import {state} from "../../core/state.js";
import {assignment} from "../../core/api.js";
import {setExamHandlers} from "../../core/polling.js";
import {render} from "../../ui/render.js";
import {esc,dateTime} from "../../shared/format.js";
import {btn,pill} from "../../ui/components.js";
import {modal} from "../../ui/modal.js";
import {cryptoRandomId} from "../../shared/crypto.js";

export const exam={owner:"",token:"",roomId:"",contestId:"",mode:null,monitor:null,notice:"",connected:true,query:"",filter:"",tabDraft:null,pasteDraft:null};
let queue=[],sending=false,lastSync=0,away=false,blurTimer=null,lastPaste=0,offset=0,blockedInput=null,syncGeneration=0;
export const classroomExamId=()=>state.room&&state.classroom?`classroom:${state.room.roomId}:${state.classroom.round}`:"";
const selected=()=>state.page==="room"?classroomExamId():state.contestWork?.contestId||state.contestDetail?.contest?.id||state.selectedContest?.id||"";
const storageKey=()=>`studyfix.exam.outbox.${state.identity?.userId}`;
function persist(){try{sessionStorage.setItem(storageKey(),JSON.stringify(queue));}catch(_){}}
function scope(){
  if(exam.owner===state.identity?.userId&&exam.token===state.token)return;
  ++syncGeneration;
  Object.assign(exam,{owner:state.identity?.userId||"",token:state.token||"",roomId:"",contestId:"",mode:null,monitor:null,notice:"",connected:true,query:"",filter:"",tabDraft:null,pasteDraft:null});
  queue=[];away=false;lastPaste=0;lastSync=0;clearTimeout(blurTimer);
  try{queue=JSON.parse(sessionStorage.getItem(storageKey())||"[]").filter(e=>e&&typeof e.eventId==="string").slice(-100);}catch(_){}
}
const isBranch=()=>exam.contestId.startsWith("classroom:");
const editorMatches=target=>target?.id===(isBranch()?"classroom-branch":"contest-code");
const onExamPage=()=>isBranch()?state.page==="room"&&classroomExamId()===exam.contestId:state.page==="contests"&&selected()===exam.contestId;
const active=()=>state.identity?.role==="STUDENT"&&state.room?.roomId===exam.roomId&&exam.mode?.enabled&&exam.mode?.live&&(!isBranch()||!state.classroom||state.classroom.phase==="BRANCHING")&&Date.now()+offset<Number(exam.mode.endAt||0)&&exam.owner===state.identity.userId&&exam.token===state.token;
function paintStatus(){const node=document.getElementById("exam-student-status");if(node){node.innerHTML=studentStatusContent();node.hidden=!active();}}
function studentStatusContent(){return `<strong>Đang trong chế độ kiểm tra — Copy/Paste bị giới hạn.</strong><span>${esc(exam.notice||"Hoạt động rời trang/mất focus được ghi nhận để giáo viên xem xét.")}</span>${!exam.connected?'<small>Mất kết nối: vẫn giữ giới hạn dán, sự kiện chờ đồng bộ.</small>':""}`;}
export function renderStudentExamStatus(){return `<div id="exam-student-status" class="exam-student-status" role="status" aria-live="polite" ${active()?"":"hidden"}>${active()?studentStatusContent():""}</div>`;}
export async function loadExam(contestId=selected()){
  scope();if(!contestId||!state.token||!state.room)return;
  const token=state.token,owner=state.identity.userId,roomId=state.room.roomId,request=++syncGeneration;lastSync=Date.now();
  try{
    const mode=await assignment("EXAM_STATUS",{contestId});if(request!==syncGeneration||token!==state.token||owner!==state.identity?.userId||roomId!==state.room?.roomId)return;
    offset=Number(mode.serverTime||Date.now())-Date.now();
    if(exam.contestId!==contestId){exam.tabDraft=null;exam.pasteDraft=null;}
    if(exam.contestId!==contestId||exam.mode?.generation!==mode.generation||exam.mode?.examSessionId!==mode.examSessionId){away=!!mode.away;exam.notice="";}
    exam.contestId=contestId;exam.roomId=roomId;exam.mode=mode;exam.connected=true;
    if(state.identity.role==="TEACHER"){
      const monitor=await assignment("EXAM_MONITOR",{contestId});if(request===syncGeneration&&token===state.token&&owner===state.identity?.userId)exam.monitor=monitor;
    }else{
      // An old login/generation cannot replay signals into a new exam session.
      queue=queue.filter(e=>e.contestId!==contestId||(e.examSessionId===mode.examSessionId&&e.generation===mode.generation));persist();
      paintStatus();await flush();if(document.hidden)leave("hidden");else back();
    }
    lastSync=Date.now();return mode;
  }catch(error){if(token===state.token){exam.connected=false;paintStatus();}throw error;}
}
async function flush(){
  if(sending||!state.token||!state.room||!queue.length)return;
  const token=state.token,owner=state.identity?.userId;sending=true;
  try{
    while(queue.length&&token===state.token&&owner===state.identity?.userId){
      const event=queue[0];
      try{await assignment("EXAM_EVENT",event);if(token!==state.token)return;queue.shift();exam.connected=true;persist();}
      catch(error){
        if(token!==state.token)return;
        if(["EXAM_INACTIVE","EXAM_SESSION_INVALID","NOT_MEMBER","NOT_FOUND"].includes(error.code)){queue.shift();persist();continue;}
        exam.connected=false;break;
      }
    }
  }finally{sending=false;paintStatus();}
}
function report(eventType,source){
  scope();if(!active()||!exam.mode.examSessionId)return;
  const event={contestId:exam.contestId,examSessionId:exam.mode.examSessionId,generation:exam.mode.generation,eventId:cryptoRandomId(),eventType,source,clientAt:Date.now()};
  if(queue.length>=100){exam.notice="Hàng đợi theo dõi đầy; một số tín hiệu không thể lưu cục bộ.";paintStatus();return;}
  queue.push(event);persist();void flush();
}
function leave(source){
  scope();if(!active()||away)return;away=true;
  exam.notice=source==="blur"?"Cửa sổ kiểm tra mất focus. Đây là tín hiệu cần giáo viên xem xét.":"Bạn đã rời khỏi trang kiểm tra. Hoạt động này đã được ghi nhận.";
  report("TAB_SWITCH",source);paintStatus();
}
function back(){
  clearTimeout(blurTimer);if(!active()||!away||document.hidden||!document.hasFocus())return;
  away=false;report("TAB_RETURN","focus");paintStatus();
}
function blockPaste(event,source){
  scope();if(!editorMatches(event.target)||!active())return;
  event.preventDefault();event.stopPropagation();
  exam.notice="Chế độ kiểm tra đang được bật. Bạn không được phép dán mã nguồn từ clipboard.";paintStatus();
  const now=Date.now();if(now-lastPaste<600||event.repeat)return;lastPaste=now;report("PASTE_ATTEMPT",source);
}
document.addEventListener?.("keydown",event=>{if(((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==="v")||(event.shiftKey&&event.key==="Insert"))blockPaste(event,"shortcut");},true);
document.addEventListener?.("paste",event=>blockPaste(event,"paste"),true);
document.addEventListener?.("beforeinput",event=>{if(["insertFromPaste","insertFromPasteAsQuotation","insertFromDrop"].includes(event.inputType)){
  if(editorMatches(event.target)&&active()&&!event.cancelable)blockedInput={node:event.target,value:event.target.value,start:event.target.selectionStart,end:event.target.selectionEnd};
  blockPaste(event,"beforeinput");
}},true);
document.addEventListener?.("input",event=>{if(blockedInput?.node===event.target){const previous=blockedInput;blockedInput=null;event.target.value=previous.value;event.target.setSelectionRange(previous.start,previous.end);event.stopImmediatePropagation();}},true);
document.addEventListener?.("drop",event=>blockPaste(event,"drop"),true);
document.addEventListener?.("visibilitychange",()=>{if(document.hidden){clearTimeout(blurTimer);leave("hidden");}else back();});
globalThis.addEventListener?.("blur",()=>{clearTimeout(blurTimer);blurTimer=setTimeout(()=>leave(document.hidden?"hidden":"blur"),150);});
globalThis.addEventListener?.("focus",back);
globalThis.addEventListener?.("online",()=>{if(selected())void loadExam().catch(()=>{});});
globalThis.addEventListener?.("offline",()=>{exam.connected=false;paintStatus();});
document.addEventListener?.("studyfix:exam-update",event=>{
  scope();const snapshot=event.detail||{},id=selected()||exam.contestId,mode=(snapshot.modes||[]).find(m=>m.contestId===id);
  if(!mode)return;
  if(state.identity?.role==="TEACHER"){
    const next=snapshot.monitors?.[id]||null,changed=JSON.stringify([exam.mode,exam.monitor])!==JSON.stringify([mode,next]);
    exam.contestId=id;exam.mode=mode;exam.monitor=next;if(changed&&["contests","room"].includes(state.page))render();
  }else{
    const changed=exam.contestId!==id||exam.mode?.generation!==mode.generation||exam.mode?.enabled!==mode.enabled||exam.mode?.live!==mode.live;
    if(changed){
      const session=exam.mode?.generation===mode.generation?exam.mode?.examSessionId:undefined;
      exam.contestId=id;exam.roomId=state.room?.roomId||"";exam.mode={...mode,examSessionId:session};
      if(!mode.enabled){++syncGeneration;away=false;paintStatus();}else{paintStatus();void loadExam(id).catch(()=>{});}
    }
  }
});
const eventLabels={PASTE_ATTEMPT:"Dán bị chặn",TAB_SWITCH:"Rời trang / mất focus",TAB_RETURN:"Quay lại"};
export function renderExamMonitor(contest){
  if(state.identity?.role!=="TEACHER"||!contest)return "";
  const mode=exam.contestId===contest.id?exam.mode:null,monitor=exam.contestId===contest.id?exam.monitor:null,students=monitor?.students||[];
  const q=exam.query.toLocaleLowerCase(),rows=students.filter(s=>(s.displayName+" "+s.username).toLocaleLowerCase().includes(q)&&(!exam.filter||(exam.filter==="PASTE_ATTEMPT"?s.pasteCount>0:s.tabCount>0)));
  return `<section class="card exam-monitor"><div class="card-head"><div><h3>${contest.id.startsWith("classroom:")?"GIÁM SÁT BÀI SỬA LỖI":"GIÁM SÁT BÀI KIỂM TRA"}</h3><p>Tín hiệu để xem xét; không tự kết luận gian lận hoặc trừ điểm.</p></div>${pill(mode?.enabled?"Exam Mode đang bật":"Học bình thường",mode?.enabled?"medium":"neutral")}</div><div class="card-body"><div class="exam-settings"><label>Ngưỡng rời trang<input id="exam-tab-limit" class="field" type="number" min="1" max="100" value="${esc(exam.tabDraft??mode?.tabLimit??3)}"></label><label>Ngưỡng dán bị chặn<input id="exam-paste-limit" class="field" type="number" min="1" max="100" value="${esc(exam.pasteDraft??mode?.pasteLimit??3)}"></label>${btn(mode?.enabled?"Tắt Exam Mode":"Bật Exam Mode","exam-toggle",mode?.enabled?"btn-soft":"btn-primary",`data-id="${esc(contest.id)}" data-enabled="${!mode?.enabled}"`)}${btn("Lưu ngưỡng","exam-thresholds","btn-soft",`data-id="${esc(contest.id)}"`)}</div><p>${students.filter(s=>s.online).length} sinh viên có phiên hoạt động · ${students.filter(s=>s.warning).length} sinh viên cần kiểm tra · cập nhật qua ${state.presenceTransport==="websocket"?"WebSocket":"polling"}.</p><div class="exam-settings"><input id="exam-monitor-query" class="field" aria-label="Tìm sinh viên giám sát" placeholder="Tìm sinh viên…" value="${esc(exam.query)}"><select id="exam-monitor-type" class="select"><option value="">Tất cả tín hiệu</option>${Object.entries(eventLabels).filter(([key])=>key!=="TAB_RETURN").map(([key,label])=>`<option value="${key}" ${exam.filter===key?"selected":""}>${label}</option>`).join("")}</select></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Sinh viên</th><th>Trạng thái</th><th>Rời trang</th><th>Paste bị chặn</th><th>Cảnh báo</th><th></th></tr></thead><tbody>${rows.map(s=>`<tr><td>${esc(s.displayName)}<small class="task-sub">${esc(s.username)}</small></td><td>${!s.online?"Không có heartbeat gần đây":s.away?"Mất focus / rời trang":"Đang thi"}</td><td>${s.tabCount}</td><td>${s.pasteCount}</td><td>${pill(s.warning?"Cần kiểm tra":"Bình thường",s.warning?"hard":"neutral")}</td><td>${btn("Lịch sử tín hiệu","exam-history","btn-soft btn-sm",`data-id="${esc(contest.id)}" data-user-id="${esc(s.userId)}"`)}</td></tr>`).join("")||'<tr><td colspan="6" class="empty-slim">Chưa có sinh viên mở bài thi trong lượt Exam Mode này.</td></tr>'}</tbody></table></div><div class="card-body">${(monitor?.recentEvents||[]).slice(0,8).map(e=>`<p><strong>${esc(e.displayName)}</strong> - ${esc(eventLabels[e.type]||e.type)} - ${dateTime(e.receivedAt)}</p>`).join("")}${btn("Xem tất cả sự kiện","exam-history","btn-soft btn-sm",`data-id="${esc(contest.id)}"`)}</div></section>`;
}
export async function examClick(action,data){
  if(state.identity?.role!=="TEACHER")throw new Error("Chỉ giảng viên quản lý kỳ thi được thao tác.");
  if(action==="exam-toggle"||action==="exam-thresholds"){
    await assignment("EXAM_CONFIG",{contestId:data.id,enabled:action==="exam-toggle"?data.enabled==="true":!!exam.mode?.enabled,tabLimit:Number(document.getElementById("exam-tab-limit")?.value||3),pasteLimit:Number(document.getElementById("exam-paste-limit")?.value||3)});
    exam.tabDraft=null;exam.pasteDraft=null;
    await loadExam(data.id);render();
  }else if(action==="exam-history"){
    const result=await assignment("EXAM_HISTORY",{contestId:data.id,studentId:data.userId||"",eventType:exam.filter,before:Number(data.before)||undefined});
    const rows=result.events||[];
    modal(`<div class="modal-head"><h3>Lịch sử tín hiệu kiểm tra</h3>${btn("Đóng","close-modal","btn-soft btn-sm")}</div><div class="modal-body"><p>Thời gian nhận do server ghi. Blur không xác định được ứng dụng đã mở.</p>${rows.map(e=>`<div class="history-entry"><strong>${esc(e.displayName)}</strong><div>${esc(eventLabels[e.type]||e.type)} · ${esc(e.source)}<small>Server: ${dateTime(e.receivedAt)} · Client báo: ${dateTime(e.clientAt)}</small>${e.durationMs?`<small>Khoảng vắng theo thời điểm server nhận: ${Math.round(e.durationMs/1000)} giây</small>`:""}</div></div>`).join("")||'<p>Không có sự kiện phù hợp.</p>'}${rows.length===100?btn("100 sự kiện trước","exam-history","btn-soft",`data-id="${esc(data.id)}" data-user-id="${esc(data.userId||"")}" data-before="${rows.at(-1).id}"`):""}</div>`);
  }
}
document.addEventListener?.("input",event=>{if(event.target?.id==="exam-monitor-query"&&state.identity?.role==="TEACHER"){exam.query=event.target.value;render();}});
document.addEventListener?.("input",event=>{if(state.identity?.role!=="TEACHER")return;if(event.target?.id==="exam-tab-limit")exam.tabDraft=event.target.value;if(event.target?.id==="exam-paste-limit")exam.pasteDraft=event.target.value;});
document.addEventListener?.("change",event=>{if(event.target?.id==="exam-monitor-type"&&state.identity?.role==="TEACHER"){exam.filter=event.target.value;render();}});
setExamHandlers({poll:async()=>{
  scope();if(!state.token||!state.room)return;
  if(active()){if(!onExamPage())leave("navigation");else back();}
  if(Date.now()-lastSync<5000)return;
  const id=(["contests","room"].includes(state.page)?selected():"")||(active()?exam.contestId:"");if(!id)return;
  try{await loadExam(id);if(state.identity?.role==="TEACHER"&&["contests","room"].includes(state.page))render();}catch(_){lastSync=Date.now();}
}});

setExamUi({classroomExamId,renderStudentExamStatus,renderExamMonitor});
