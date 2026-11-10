import {esc} from "../../shared/format.js";
import {templateIcon as icon} from "../../ui/template.js";
import {modal,clearModal} from "../../ui/modal.js";

const normalize=value=>String(value||"").normalize("NFD").replace(/\p{M}/gu,"").replaceAll("đ","d").replaceAll("Đ","D").toLowerCase();

export function renderClassMemberRows(members,query="",editable=true){
  const term=normalize(query).trim();
  const rows=members.filter(m=>normalize(`${m.studentCode} ${m.displayName}`).includes(term));
  return rows.map(m=>`<div class="cd-roster-row"><div><strong>${esc(m.studentCode)}</strong><span>${esc(m.displayName)}</span><small>${esc(({ENROLLED:"Đang thuộc lớp",REMOVED:"Đã gỡ",PENDING:"Chờ duyệt",REJECTED:"Đã từ chối"})[m.membershipStatus]||m.membershipStatus)}</small></div>${editable&&m.membershipStatus==="ENROLLED"?`<button type="button" class="btn btn-soft cd-delete" data-remove-member="${esc(m.userId)}">Gỡ khỏi lớp</button>`:""}</div>`).join("")||`<div class="cd-empty">${term?"Không tìm thấy sinh viên phù hợp.":"Chưa có sinh viên trong lớp."}</div>`;
}

export function openClassMembers(data,selected,{enroll,remove}){
  modal(`<div class="cd-roster"><div class="modal-head"><div><h3 id="class-members-title">Sinh viên · ${esc(selected.roomCode)}</h3><p>${esc(selected.name)}</p></div><button type="button" class="btn btn-soft" id="class-members-close" aria-label="Đóng danh sách sinh viên">Đóng</button></div><div class="modal-body"><div class="cd-roster-toolbar"><label class="cd-search" for="class-members-query">${icon("search")}<input id="class-members-query" type="search" maxlength="160" placeholder="Tìm theo MSSV hoặc họ tên..." autocomplete="off"></label>${selected.active?'<button type="button" class="btn btn-primary" id="class-members-add" aria-expanded="false" aria-controls="class-members-enroll">+ Thêm bằng MSSV</button>':""}</div><form id="class-members-enroll" class="cd-roster-enroll" hidden><label for="class-members-code">Mã sinh viên đã được cấp</label><div><input id="class-members-code" name="studentCode" class="field" required maxlength="64" placeholder="Nhập MSSV" autocomplete="off"><button type="submit" class="btn btn-primary">Thêm vào lớp</button></div></form><p id="class-members-error" role="alert"></p><p id="class-members-count" role="status"></p><div id="class-members-list" class="cd-roster-list"></div></div></div>`);
  const dialog=document.querySelector("#active-modal .modal");
  dialog.setAttribute("aria-labelledby","class-members-title");
  const query=document.getElementById("class-members-query"),list=document.getElementById("class-members-list"),form=document.getElementById("class-members-enroll"),error=document.getElementById("class-members-error"),add=document.getElementById("class-members-add");
  let busy=false;
  const update=()=>{
    list.innerHTML=renderClassMemberRows(data.members||[],query.value,selected.active);
    document.getElementById("class-members-count").textContent=`${(data.members||[]).filter(m=>m.membershipStatus==="ENROLLED").length} sinh viên trong lớp`;
  };
  const mutate=async operation=>{
    if(busy)return;
    busy=true;error.textContent="";
    dialog.querySelectorAll("button:not(#class-members-close)").forEach(button=>button.disabled=true);
    try{await operation();if(dialog.isConnected){update();if(data.error)error.textContent=data.error;}}
    catch(e){if(dialog.isConnected)error.textContent=e.message;}
    finally{busy=false;if(dialog.isConnected)dialog.querySelectorAll("button").forEach(button=>button.disabled=false);}
  };
  document.getElementById("class-members-close").onclick=clearModal;
  query.oninput=()=>{if(!busy)update();};
  if(add)add.onclick=()=>{form.hidden=!form.hidden;add.setAttribute("aria-expanded",String(!form.hidden));if(!form.hidden)form.elements.studentCode.focus();};
  form.onsubmit=event=>{
    event.preventDefault();
    const code=form.elements.studentCode.value.trim();
    if(!code)return;
    void mutate(async()=>{await enroll(code);form.reset();});
  };
  list.onclick=event=>{
    const button=event.target.closest?.("[data-remove-member]");
    if(!button||busy)return;
    if(confirm("Gỡ sinh viên khỏi lớp và chặn tham gia lại cho tới khi được thêm lại?"))void mutate(()=>remove(button.dataset.removeMember));
  };
  update();query.focus();
}
