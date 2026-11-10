import {state} from "./state.js";
import {isAdminPage} from "./roles.js";

let socket=null,heartbeat=null,retry=null,generation=0,currentToken="",currentConfig="";
const emit=(name,detail)=>document.dispatchEvent(new CustomEvent(name,{detail}));
export function subscribePresence(){
  if(socket?.readyState===1)socket.send(JSON.stringify({type:"SUBSCRIBE",view:isAdminPage(state.page)?"admin":"room"}));
}
export function stopPresenceSocket(){
  ++generation;clearTimeout(retry);clearInterval(heartbeat);retry=null;heartbeat=null;
  const old=socket;socket=null;currentToken="";currentConfig="";if(old){old.onclose=null;old.close();}
  state.presenceTransport="polling";
}
export function startPresenceSocket(config){
  if(!config?.enabled||!state.token||typeof WebSocket==="undefined")return;
  const key=JSON.stringify(config);if(currentToken===state.token&&currentConfig===key&&socket&&socket.readyState<=1)return;
  stopPresenceSocket();currentToken=state.token;currentConfig=key;
  const run=generation,token=currentToken;
  const connect=(attempt=0)=>{
    if(run!==generation||state.token!==token)return;
    const url=new URL(location.href);url.protocol=config.tls?"wss:":"ws:";url.port=String(config.port);url.pathname=config.path;url.search="";url.hash="";
    const ws=new WebSocket(url.href);socket=ws;
    ws.onopen=()=>{if(run!==generation){ws.close();return;}ws.send(JSON.stringify({type:"AUTH",token}));};
    ws.onmessage=event=>{
      if(run!==generation||state.token!==token)return;
      let message;try{message=JSON.parse(event.data);}catch(_){return;}
      if(message.examSnapshot)emit("studyfix:exam-update",message.examSnapshot);
      if(message.type==="AUTH_OK"){
        state.presenceTransport="websocket";subscribePresence();clearInterval(heartbeat);
        heartbeat=setInterval(()=>{if(ws.readyState===1){ws.send(JSON.stringify({type:"HEARTBEAT"}));subscribePresence();}},10000);
      }else if(message.type==="PRESENCE"&&state.identity?.role==="TEACHER")emit("studyfix:presence",message);
      else if(message.type==="ADMIN_UPDATE"&&state.identity?.role==="TEACHER")emit("studyfix:admin-update",message.data);
      else if(message.type==="ADMISSION_UPDATE"&&state.identity?.role==="STUDENT")emit("studyfix:admission-update",message.admissions||[]);
    };
    ws.onerror=()=>{};
    ws.onclose=()=>{if(run!==generation)return;clearInterval(heartbeat);state.presenceTransport="polling";retry=setTimeout(()=>connect(attempt+1),Math.min(15000,1000*2**Math.min(attempt,4)));};
  };
  connect();
}
