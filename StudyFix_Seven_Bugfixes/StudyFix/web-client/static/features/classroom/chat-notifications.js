import { state } from "../../core/state.js";
import { api } from "../../core/api.js";
import { notifyChatMessage } from "../../ui/toast.js";

const records = new Map();
const baseTitle = typeof document === "undefined" ? "StudyFix" : document.title || "StudyFix — Học cùng nhau";
function scope() {return state.identity?.userId && state.room?.roomId ? `${state.identity.userId}.${state.room.roomId}` : "";}
function record(key) {
  if (!records.has(key)) {
    let saved=null;
    try {saved=JSON.parse(globalThis.localStorage?.getItem(`studyfix.chat.read.${key}`)||"null");} catch (_) { /* Nếu trình duyệt chặn lưu trữ thì tạm giữ mốc đã đọc trong bộ nhớ. */ }
    records.set(key,{readId:Number.isSafeInteger(saved)&&saved>=0?saved:null, latestId:0, observed:0, initialized:false, unread:0, messages:[]});
  }
  return records.get(key);
}
function persist(key,r) {
  try {globalThis.localStorage?.setItem(`studyfix.chat.read.${key}`,JSON.stringify(r.readId));} catch (_) { /* Vẫn theo dõi tin chưa đọc trong bộ nhớ dù không lưu được trên máy. */ }
}
export function unreadChatCount() {const key=scope();return key?record(key).unread:0;}
export function updateChatBadges() {
  const count=unreadChatCount();
  for(const el of document.querySelectorAll?.("[data-chat-unread]")||[]) {
    el.textContent=count>99?"99+":String(count);el.hidden=count===0;
    el.setAttribute("aria-label",`${count} tin nhắn chưa đọc`);
  }
  document.title=count?`(${count}) ${baseTitle}`:baseTitle;
}
export function markChatReadIfVisible() {
  const key=scope();if(!key)return false;
  if(state.page!=="room"||state.meetingPanel!=="chat"||document.visibilityState==="hidden"||document.hasFocus?.()===false)return false;
  if(document.getElementById?.("active-modal"))return false;
  const chat=document.querySelector("#chat-list");
  if(!chat||chat.scrollHeight-chat.scrollTop-chat.clientHeight>32)return false;
  const r=record(key);if(!r.initialized)return false;
  const changed=r.unread>0||r.readId!==r.latestId;
  r.readId=r.latestId;r.unread=0;persist(key,r);updateChatBadges();return changed;
}
export async function loadChat() {
  const key=scope();if(!key)return false;
  const token=state.token,roomId=state.room.roomId,r=record(key),after=r.readId??0;
  let response;
  try {response=await api(`/api/rooms/current/chat?after=${after}`);}
  catch(e) {
    if(e.status!==404)throw e;
    // Server đang chạy JAR cũ vẫn có thể trả về các file giao diện vừa được cập nhật.
    // Giữ nội dung chat trong lúc chờ server hỗ trợ API mới về người gửi và số tin chưa đọc.
    const old=await api("/api/classroom"),messages=old.classroom?.chat||[];
    response={messages,latestId:Number(messages.at(-1)?.id||0),unreadCount:messages.filter(m=>Number(m.id)>after&&!isOwn(m)).length};
  }
  if(key!==scope()||token!==state.token)return false;
  const latest=Number(response.latestId||0);
  if(latest<r.observed)return false; // Bỏ qua phản hồi đến chậm để không ghi đè trạng thái chat mới hơn.
  const messages=(response.messages||[]).filter(m=>!m.system&&Number(m.id)>0);
  const oldUnread=r.unread,oldMessages=JSON.stringify(r.messages);
  const newlyReceived=r.initialized?messages.filter(m=>Number(m.id)>r.observed&&!isOwn(m)):[];
  if(r.readId===null) {r.readId=latest;persist(key,r);r.unread=0;} // Lấy mốc đã đọc ban đầu khi vừa vào phòng lần đầu.
  else if(r.readId===after)r.unread=Math.max(0,Number(response.unreadCount)||0);
  else r.unread=messages.filter(m=>Number(m.id)>r.readId&&!isOwn(m)).length;
  r.messages=messages;r.latestId=latest;r.observed=latest;r.initialized=true;
  state.chatItems=messages;
  markChatReadIfVisible();updateChatBadges();
  if(newlyReceived.length) {
    const last=newlyReceived.at(-1),summary=String(last.text||"").replace(/\s+/g," ").slice(0,100);
    notifyChatMessage({name:last.name,text:summary,count:newlyReceived.length,roomId:state.room.roomCode||roomId});
  }
  return oldUnread!==r.unread||oldMessages!==JSON.stringify(messages);
}
function isOwn(message) {
  return message.userId ? message.userId===state.identity?.userId : message.name===state.identity?.displayName;
}
export function resetChatNotifications() {
  // Giữ mốc đã đọc của từng tài khoản để dùng khi tải lại hoặc đăng nhập; chỉ xóa số đếm đang hiển thị.
  for(const r of records.values())r.unread=0;
  document.title=baseTitle;
}
