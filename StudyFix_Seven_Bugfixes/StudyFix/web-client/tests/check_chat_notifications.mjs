// Kiểm tra tin chưa đọc theo từng người nhận bằng HTTP và DOM giả lập, chưa chạy trình duyệt thật.
import assert from "node:assert/strict";
const storage=new Map(),toasts=[],badges=[];
let focused=true,chat=null,checks=0;
function check(ok,message){assert.ok(ok,message);checks++;}
globalThis.localStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v)};
globalThis.sessionStorage={getItem:()=>null};globalThis.setTimeout=()=>1;
globalThis.document={title:"StudyFix",visibilityState:"visible",hasFocus:()=>focused,
  querySelector:s=>s==="#chat-list"?chat:s==="#toast-region"?{appendChild:n=>toasts.push(n.innerHTML)}:{innerHTML:""},
  querySelectorAll:()=>badges,createElement:()=>({innerHTML:"",setAttribute(){},remove(){}})};
const {state}=await import("../static/core/state.js");
const {loadChat,unreadChatCount,markChatReadIfVisible,resetChatNotifications}=await import("../static/features/classroom/chat-notifications.js");
state.identity={userId:"student",displayName:"Student"};state.token="test";state.room={roomId:"r1"};state.page="room";state.meetingPanel="";
let messages=[{id:1,userId:"teacher",name:"Teacher",text:"Old message"}];
function response(){const urlAfter=Number(new URL(lastUrl,"http://test").searchParams.get("after"));return {ok:true,status:200,json:async()=>({messages:[...messages],latestId:messages.at(-1)?.id||0,unreadCount:messages.filter(m=>m.id>urlAfter&&m.userId!==state.identity.userId).length})};}
let lastUrl="";
globalThis.fetch=async url=>{lastUrl=url;return response();};
await loadChat();check(unreadChatCount()===0&&toasts.length===0,"first room visit establishes baseline without replaying old notifications");
messages.push({id:2,userId:"teacher",name:"Teacher",text:"New message"});await loadChat();
check(unreadChatCount()===1&&toasts.length===1,"closed chat gets a count and toast for a new message");
check(document.title==="(1) StudyFix","browser title shows unread count");
await loadChat();check(unreadChatCount()===1&&toasts.length===1,"polling does not duplicate counts or notifications");
messages.push({id:3,userId:"student",name:"Student",text:"Own message"});await loadChat();
check(unreadChatCount()===1&&toasts.length===1,"own messages do not create unread notifications");
messages.push({id:4,userId:"other-student",name:"Student",text:"Same display name"});await loadChat();
check(unreadChatCount()===2,"author IDs distinguish another user with the same display name");
chat={scrollHeight:400,clientHeight:200,scrollTop:200};state.meetingPanel="chat";
check(markChatReadIfVisible()&&unreadChatCount()===0,"viewing bottom of chat marks latest messages read");
check(storage.get("studyfix.chat.read.student.r1")==="4"&&document.title==="StudyFix","read marker is persisted and title restored");
const beforeOpenChat=toasts.length;
messages.push({id:5,userId:"teacher",name:"Teacher",text:"While chat is open"});await loadChat();
check(unreadChatCount()===0,"visible chat still marks incoming message read");
check(toasts.length===beforeOpenChat+1,"incoming message shows a popup even when chat is open");
check(toasts.at(-1).includes("Tin nhắn mới")&&toasts.at(-1).includes('data-action="chat-open"'),"chat popup is clearly labeled and can open chat");
const afterOpenChat=toasts.length;await loadChat();
check(toasts.length===afterOpenChat,"open-chat popup is not repeated on every poll");
document.visibilityState="hidden";messages.push({id:6,userId:"teacher",name:"Teacher",text:"While hidden"});await loadChat();
check(unreadChatCount()===1,"background tab is not marked read even with chat open");
document.visibilityState="visible";markChatReadIfVisible();check(unreadChatCount()===0,"returning to visible chat marks it read");
chat.scrollHeight=1000;chat.scrollTop=0;messages.push({id:7,userId:"teacher",name:"Teacher",text:"While reading history"});await loadChat();
check(unreadChatCount()===1,"reading older chat does not acknowledge new messages below");
chat.scrollTop=800;markChatReadIfVisible();check(unreadChatCount()===0,"scrolling to bottom clears pending count");
focused=false;messages.push({id:8,userId:"teacher",name:"Teacher",text:"Other window focused"});await loadChat();
check(unreadChatCount()===1,"unfocused window does not auto-read messages");focused=true;markChatReadIfVisible();
state.page="exercises";messages.push({id:9,userId:"teacher",name:"Teacher",text:"During exercise"});await loadChat();
check(unreadChatCount()===1,"messages notify while working on another page");
const oldRoom=state.room;state.room={roomId:"r2"};check(unreadChatCount()===0,"new room does not inherit another room's badge");
state.room=oldRoom;check(unreadChatCount()===1,"returning to a room retains its unread state");
state.identity={userId:"other-account",displayName:"Other"};check(unreadChatCount()===0,"different account cannot inherit unread state");state.identity={userId:"student",displayName:"Student"};

// Số tin chưa đọc có thể lớn hơn 80 tin đang hiển thị; lấy số chính xác từ server.
state.room={roomId:"large"};storage.set("studyfix.chat.read.student.large","0");
globalThis.fetch=async()=>({ok:true,status:200,json:async()=>({messages:[{id:100,userId:"teacher",name:"Teacher",text:"latest"}],latestId:100,unreadCount:100})});
await loadChat();check(unreadChatCount()===100,"server count is not limited to displayed history");
let pending;globalThis.fetch=()=>new Promise(resolve=>{pending=resolve;});const stale=loadChat();await Promise.resolve();
state.page="room";state.meetingPanel="chat";chat.scrollTop=800;markChatReadIfVisible();state.meetingPanel="";
pending({ok:true,status:200,json:async()=>({messages:[{id:100,userId:"teacher",name:"Teacher",text:"latest"}],latestId:100,unreadCount:100})});await stale;
check(unreadChatCount()===0,"old request cannot restore counts after user read the messages");

// Tải lại toàn bộ trang vẫn khôi phục mốc đã đọc mà không cần lưu nội dung tin nhắn.
state.room={roomId:"reload"};storage.set("studyfix.chat.read.student.reload","10");document.title="StudyFix";
const reloaded=await import("../static/features/classroom/chat-notifications.js?reload");
globalThis.fetch=async()=>({ok:true,status:200,json:async()=>({messages:[{id:11,userId:"teacher",name:"Teacher",text:"After reload"}],latestId:11,unreadCount:1})});
await reloaded.loadChat();check(reloaded.unreadChatCount()===1,"stored marker restores unread count after module reload");
globalThis.fetch=async()=>{throw new Error("Network failure");};await assert.rejects(reloaded.loadChat(),/Không kết nối/);checks++;
check(reloaded.unreadChatCount()===1,"temporary network error keeps unread state");
resetChatNotifications();check(document.title==="StudyFix","logout reset restores page title");
console.log(`Chat notifications: ${checks} checks PASS (synthetic HTTP/DOM).`);
