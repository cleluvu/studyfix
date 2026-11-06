import {state} from "../../core/state.js";
import {assignment} from "../../core/api.js";
import {modal,clearModal} from "../../ui/modal.js";
import {render} from "../../ui/render.js";
import {toast} from "../../ui/toast.js";
import {esc} from "../../shared/format.js";
import {btn} from "../../ui/components.js";
let draft=null;
export function randomPick(items,count){
 if(!Number.isInteger(count)||count<1||count>items.length)throw new Error("Số bài phải từ 1 đến số bài trong danh sách.");
 const result=[...items];for(let i=result.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[result[i],result[j]]=[result[j],result[i]];}return result.slice(0,count);
}
function show(){
 const d=draft;if(!d)return;
 modal(`<div class="modal-head"><div><h3>Giao đề cho sinh viên</h3><p>${esc(d.title)}</p></div>${btn("Đóng","close-modal","btn-soft btn-sm")}</div><div class="modal-body"><h4>1. Chọn sinh viên nhận đề</h4>${btn("Chọn tất cả sinh viên","contest-assign-all","btn-soft btn-sm")}<div class="contest-problem-list">${d.students.map(s=>`<label class="check-row"><input type="checkbox" data-exam-recipient="${esc(s.userId)}" ${d.users.includes(s.userId)?"checked":""}/><span><strong>${esc(s.displayName)}</strong><small>${esc(s.username)}</small></span></label>`).join("")||"Chưa có sinh viên khả dụng trong lớp. Hãy thêm sinh viên hoặc cho sinh viên vào phòng."}</div><h4>2. Chọn bài giao</h4><div class="exam-settings"><label>Số bài chọn ngẫu nhiên<input class="field" id="contest-assign-count" type="number" min="1" max="${d.problems.length}" value="${d.count}"/></label>${btn("Chọn ngẫu nhiên","contest-assign-random","btn-primary btn-sm")}${btn("Chọn tất cả bài","contest-assign-all-problems","btn-soft btn-sm")}</div><p>Chọn ngẫu nhiên sẽ tự tích đủ số bài trong danh sách dưới; bạn có thể chỉnh lại trước khi giao.</p><div class="contest-problem-list">${d.problems.map(p=>`<label class="check-row"><input type="checkbox" data-exam-assigned-problem="${esc(p.id)}" ${d.ids.includes(p.id)?"checked":""}/><span><strong>${esc(p.id)} · ${esc(p.title)}</strong><small>${esc(p.difficulty||"")}</small></span></label>`).join("")}</div><p>Chỉ sinh viên được giao mới nhận đề. Bộ đề đã phát trước đó được giữ nguyên, không bốc lại.</p><div class="footer-row">${btn("Giao đề đã chọn","contest-assign-save","btn-primary")}</div></div>`);
}
export async function contestAssignmentClick(action,data){
 if(state.identity?.role!=="TEACHER")throw new Error("Chỉ giáo viên được giao đề.");
 if(action==="contest-pick-random"){
  const count=Number(document.getElementById("contest-pool-draw-count")?.value||1);
  const pool=state.catalog.filter(e=>e.mode==="BUILD_FROM_SCRATCH"&&(!state.contestQuery||`${e.id} ${e.title} ${e.topic}`.toLowerCase().includes(state.contestQuery.toLowerCase())));
  if(count>20)throw new Error("Mỗi kỳ thi tối đa 20 bài.");state.contestPoolDrawCount=count;state.contestSelected=randomPick(pool,count).map(p=>p.id);render();toast(`Đã chọn ngẫu nhiên ${count} bài.`,"success");return;
 }
 if(action==="contest-assign-open"){
  const token=state.token,room=state.room?.roomId;
  const [details,roster]=await Promise.all([assignment("CONTEST_PROBLEMS_GET",{contestId:data.contestId}),assignment("CONTEST_RECIPIENTS",{contestId:data.contestId})]);
  if(token!==state.token||room!==state.room?.roomId)return;
  draft={token,room,id:data.contestId,title:details.contest.title,problems:details.problems,students:roster.students,users:[],ids:[],count:Math.min(3,details.problems.length)};show();return;
 }
 if(!draft||draft.token!==state.token||draft.room!==state.room?.roomId)throw new Error("Phiên lớp đã đổi, hãy mở lại Giao đề.");
 if(action==="contest-assign-all"){draft.users=draft.students.map(s=>s.userId);show();}
 if(action==="contest-assign-all-problems"){draft.ids=draft.problems.map(p=>p.id);show();}
 if(action==="contest-assign-random"){draft.count=Number(document.getElementById("contest-assign-count")?.value);draft.ids=randomPick(draft.problems,draft.count).map(p=>p.id);show();}
 if(action==="contest-assign-save"){
  if(!draft.users.length||!draft.ids.length)throw new Error("Chọn sinh viên và bài trước khi giao.");
  const current=draft;
  await assignment("CONTEST_DISTRIBUTE",{contestId:current.id,studentIds:current.users,exerciseIds:current.ids});
  clearModal();draft=null;
  if(current.token===state.token&&current.room===state.room?.roomId&&state.contestDetail?.contest?.id===current.id){state.contestDetail=await assignment("CONTEST_PROBLEMS_GET",{contestId:current.id});render();}
  toast("Đã giao đề cho sinh viên được chọn. Đề đã nhận trước đó vẫn giữ nguyên.","success");
 }
}
document.addEventListener?.("change",event=>{
 if(!draft)return;const el=event.target;
 for(const [key,field] of [["examRecipient","users"],["examAssignedProblem","ids"]])if(el.dataset?.[key]){const id=el.dataset[key];draft[field]=el.checked?[...new Set([...draft[field],id])]:draft[field].filter(x=>x!==id);}
});
