import {state} from "../../core/state.js";
import {api} from "../../core/api.js";
import {setAdminHandlers,loadPageData} from "../../core/polling.js";
import {render,pageHeader} from "../../ui/render.js";
import {btn,pill} from "../../ui/components.js";
import {modal,clearModal} from "../../ui/modal.js";
import {toast,notifyJoinRequest,clearJoinRequestNotifications} from "../../ui/toast.js";
import {esc,dateTime} from "../../shared/format.js";
import {subscribePresence} from "../../core/presence-socket.js";
import {renderTeacherAdmin} from "./admin-view.js";
import {isAdminPage} from "../../core/roles.js";
import {joinRoom} from "../room/room.js";

const empty=()=>({owner:"",enabled:false,users:[],classes:[],members:[],connections:[],attendance:[],joinRequests:[],stats:{},recentActivity:[],pages:{},filterStatus:"",selectedClass:"",query:"",error:"",message:"",lastLoad:0,refreshing:false});
export const admin=empty();let requestNumber=0;const seenRequests=new Set();
const requestKey=row=>row.requestId||`${row.classId}:${row.userId}:${row.requestedAt}`;
function scope(){if(admin.owner!==state.identity?.userId){++requestNumber;seenRequests.clear();clearJoinRequestNotifications();Object.assign(admin,empty(),{owner:state.identity?.userId||""});}}
function receiveJoinRequests(rows){
  admin.joinRequests=rows||[];
  const active=new Set(admin.joinRequests.map(requestKey));
  for(const node of document.querySelectorAll?.(".join-request-toast")||[])if(!active.has(node.dataset.requestKey))node.remove();
  for(const row of admin.joinRequests){const key=requestKey(row);if(!seenRequests.has(key)){seenRequests.add(key);notifyJoinRequest(row);}}
}
export async function loadAdmin(){
  scope();if(state.identity?.role!=="TEACHER")return;
  const owner=admin.owner;
  const request=++requestNumber,selected=admin.selectedClass;
  try{const result=await api(`/api/admin?classId=${encodeURIComponent(selected)}`);if(owner!==state.identity?.userId||request!==requestNumber||selected!==admin.selectedClass)return;Object.assign(admin,result.data,{error:"",lastLoad:Date.now()});}
  catch(e){if(owner===state.identity?.userId)admin.error=e.message;}
  receiveJoinRequests(admin.joinRequests);subscribePresence();
}
document.addEventListener("studyfix:admin-update",event=>{
  if(state.identity?.role!=="TEACHER")return;scope();
  const data=event.detail||{};Object.assign(admin,{enabled:data.enabled,users:data.users||[],classes:data.classes||[],stats:data.stats||{},recentActivity:data.recentActivity||[],message:data.message||"",connections:(data.connections||[]).filter(r=>!admin.selectedClass||r.classId===admin.selectedClass),attendance:(data.attendance||[]).filter(r=>!admin.selectedClass||r.classId===admin.selectedClass)});
  receiveJoinRequests(data.joinRequests);
  if(isAdminPage(state.page))render();
});
document.addEventListener("studyfix:presence",event=>{if(state.identity?.role!=="TEACHER")return;scope();receiveJoinRequests(event.detail?.joinRequests);});
document.addEventListener("input",event=>{if(event.target?.id==="admin-query"&&state.identity?.role==="TEACHER"){admin.query=event.target.value;admin.pages={};}});
document.addEventListener("change",async event=>{
  if(state.identity?.role!=="TEACHER")return;
  if(event.target?.id==="admin-filter-status"){admin.filterStatus=event.target.value;admin.pages={};render();}
  if(event.target?.id==="admin-filter-class"){admin.selectedClass=event.target.value;admin.pages={};await loadAdmin();render();}
});
async function action(body){await api("/api/admin",{method:"POST",body});await loadAdmin();render();}
function formModal(title,content,submitLabel,onSubmit){
  modal(`<div class="modal-head"><h3>${esc(title)}</h3><button class="btn btn-soft" type="button" id="admin-dialog-close">Đóng</button></div><form id="admin-dialog-form"><div class="modal-body">${content}<p id="admin-dialog-error" role="alert" style="color:#b42318"></p><button type="submit" class="btn btn-primary">${esc(submitLabel)}</button></div></form>`);
  document.getElementById("admin-dialog-close").onclick=clearModal;
  const form=document.getElementById("admin-dialog-form");
  form.onsubmit=async e=>{e.preventDefault();const submit=form.querySelector('[type="submit"]');submit.disabled=true;try{await onSubmit(Object.fromEntries(new FormData(form)));clearModal();toast("Đã lưu thay đổi.","success");}catch(err){form.querySelector("#admin-dialog-error").textContent=err.message;submit.disabled=false;}};
  return form;
}
const field=(name,label,type="text",attrs="")=>`<label class="form-row" style="display:block;margin-bottom:14px">${label}<input class="field" name="${name}" type="${type}" ${attrs}></label>`;
export async function adminClick(command,data={}){
  if(state.identity?.role!=="TEACHER")throw new Error("Chỉ Teacher được quản trị.");
  scope();
  switch(command){
    case "admin-approve-join":case "admin-reject-join":
      await action({action:command==="admin-approve-join"?"APPROVE_JOIN":"REJECT_JOIN",classId:data.classId,requestId:data.requestId,userId:data.id,requestedAt:Number(data.requestedAt)});
      toast(command==="admin-approve-join"?"Đã chấp nhận sinh viên vào lớp.":"Đã từ chối yêu cầu vào lớp.","success");break;
    case "admin-refresh":{
      if(admin.refreshing)return;
      const owner=admin.owner;
      admin.refreshing=true;render();
      try{
        await loadAdmin();
        if(owner!==state.identity?.userId)return;
        toast(admin.error||"Đã làm mới dữ liệu quản lý.",admin.error?"error":"success");
      }finally{
        if(owner===admin.owner){admin.refreshing=false;render();}
      }
      break;
    }
    case "admin-search":admin.query=document.getElementById("admin-query")?.value||"";render();break;
    case "admin-page":admin.pages[data.key]=Math.max(1,Number(data.page)||1);render();break;
    case "admin-add-user":{
      formModal("Tạo tài khoản Student",`${field("username","Tài khoản","text",'required maxlength="64" pattern="[A-Za-z0-9_.-]+" autocomplete="off"')}${field("displayName","Họ và tên","text",'required maxlength="200"')}${field("studentCode","Mã sinh viên","text",'required maxlength="64" pattern="[A-Za-z0-9_.-]+"')}${field("password","Mật khẩu ban đầu (ít nhất 12 ký tự)","password",'required minlength="12" maxlength="256" autocomplete="new-password"')}`,"Tạo sinh viên",values=>action({action:"CREATE_USER",...values,role:"STUDENT"}));break;
    }
    case "admin-toggle-user":if(confirm(data.active==="true"?"Khóa tài khoản và thu hồi toàn bộ phiên đăng nhập?":"Mở khóa tài khoản?"))await action({action:"SET_ACTIVE",userId:data.id,active:data.active!=="true"});break;
    case "admin-reset-user":formModal("Đặt lại mật khẩu",`${field("password","Mật khẩu mới","password",'required minlength="12" maxlength="256" autocomplete="new-password"')}<p>Tất cả phiên hiện tại của tài khoản sẽ bị thu hồi.</p>`,"Lưu mật khẩu",values=>action({action:"RESET_PASSWORD",userId:data.id,password:values.password}));break;
    case "admin-create-class":formModal("Tạo lớp học",field("roomCode","Mã phòng / lớp","text",'required maxlength="80"'),"Tạo lớp",values=>action({action:"CREATE_CLASS",roomCode:values.roomCode}));break;
    case "admin-select-class":{
      const owner=admin.owner;
      admin.selectedClass=data.id;state.page="admin-classes";
      await loadAdmin();
      if(owner!==state.identity?.userId||admin.selectedClass!==data.id||state.page!=="admin-classes")return;
      render();
      if(admin.error){toast(admin.error,"error");break;}
      const panel=document.getElementById("admin-class-members");
      panel?.focus({preventScroll:true});
      panel?.scrollIntoView({behavior:"auto",block:"start"});
      break;
    }
    case "admin-edit-class":{
      const c=admin.classes.find(c=>c.classId===data.id);if(!c)throw new Error("Lớp ngoài phạm vi quản lý.");
      formModal("Sửa thông tin lớp",`${field("name","Tên lớp","text",`required maxlength="200" value="${esc(c.name)}"`)}<p>Mã phòng và lịch sử code được giữ nguyên.</p>`,"Lưu",values=>action({action:"UPDATE_CLASS",classId:c.classId,name:values.name,closed:!!c.closed}));break;
    }
    case "admin-close-class":{
      const c=admin.classes.find(c=>c.classId===data.id);if(!c)throw new Error("Lớp ngoài phạm vi quản lý.");
      if(confirm(c.closed?"Mở lại lớp học?":"Đóng lớp và đưa các kết nối ra khỏi lớp? Dữ liệu giảng dạy vẫn được giữ."))await action({action:"UPDATE_CLASS",classId:c.classId,name:c.name,closed:!c.closed});break;
    }
    case "admin-disconnect":if(confirm("Ngắt kết nối này khỏi lớp? Phiên đăng nhập và các thiết bị khác vẫn được giữ."))await action({action:"DISCONNECT_SESSION",classId:data.classId,connectionId:data.id});break;
    case "admin-enter-class":case "admin-work-class":{
      const c=admin.classes.find(c=>c.classId===data.id);if(!c||!c.active||c.closed)throw new Error("Lớp không khả dụng.");
      if(state.room&&state.room.roomId!==c.classId)throw new Error("Bạn đang ở lớp khác. Vào Phòng học và rời phòng hiện tại trước khi chuyển lớp.");
      if(!state.room)await joinRoom(c.roomCode);
      if(!state.room||state.room.roomId!==c.classId)return;
      state.page=command==="admin-work-class"?"exercises":"room";state.exerciseMode="room";state.meetingClosed=state.page!=="room";await loadPageData();subscribePresence();render();break;
    }
    case "admin-delete-class":if(confirm("Xóa lớp này theo quy tắc xóa phòng hiện có?"))await action({action:"DELETE_CLASS",classId:data.id});break;
    case "admin-enroll":{
      formModal("Thêm sinh viên vào lớp",`${field("studentCode","Mã sinh viên đã được cấp","text",'required maxlength="64"')}<p>Chỉ thêm vào lớp đang chọn, không thay đổi tư cách thành viên ở lớp khác.</p>`,"Thêm",values=>action({action:"ENROLL_CODE",classId:admin.selectedClass,studentCode:values.studentCode}));break;
    }
    case "admin-remove-member":if(confirm("Gỡ sinh viên khỏi lớp và chặn tham gia lại cho tới khi được thêm lại?"))await action({action:"REMOVE_MEMBER",classId:admin.selectedClass,userId:data.id});break;
  }
}
export function renderAdmin(){return renderTeacherAdmin(admin,state);}
setAdminHandlers({load:loadAdmin,poll:async()=>{if(state.identity?.role!=="TEACHER"||(!isAdminPage(state.page)&&state.page!=="room")||admin.refreshing||Date.now()-admin.lastLoad<10000)return false;await loadAdmin();return true;}});
