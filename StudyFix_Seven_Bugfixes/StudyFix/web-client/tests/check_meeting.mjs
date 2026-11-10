// Test bằng media và HTTP giả lập. Vẫn cần thử camera và âm thanh giữa các trình duyệt thật.
import assert from "node:assert/strict";
const storage=new Map();
globalThis.sessionStorage={getItem:k=>storage.get(k)||null};
globalThis.document={querySelector:()=>({innerHTML:""}),getElementById:()=>null};
const tracks=[];let captures=0,checks=0;
function check(ok,message){assert.ok(ok,message);checks++;}
function track(kind){const t={kind,id:`${kind}-${tracks.length}`,enabled:true,readyState:"live",addEventListener(){},stop(){this.readyState="ended";}};tracks.push(t);return t;}
class Stream {constructor(list=[]){this.tracks=[...list];}getTracks(){return [...this.tracks];}getAudioTracks(){return this.tracks.filter(t=>t.kind==="audio");}getVideoTracks(){return this.tracks.filter(t=>t.kind==="video");}addTrack(t){this.tracks.push(t);}removeTrack(t){this.tracks=this.tracks.filter(x=>x!==t);}}
globalThis.MediaStream=Stream;
Object.defineProperty(globalThis,"navigator",{configurable:true,value:{mediaDevices:{getUserMedia:async c=>{captures++;return new Stream([track(c.audio?"audio":"video")]);}}}});
const pcs=[];
class PC {
  constructor(){this.signalingState="stable";this.connectionState="new";this.senders=[];this.transceivers=[];this.candidates=[];pcs.push(this);}
  addTransceiver(kind,options={}){const sender={kind,track:null,streams:options.streams||[],replaceTrack:async t=>{sender.track=t;},setStreams(...streams){sender.streams=streams;}};this.senders.push(sender);const transceiver={sender,receiver:{track:{kind}},mid:null,direction:options.direction||"sendrecv"};this.transceivers.push(transceiver);return transceiver;}
  getTransceivers(){return this.transceivers;}
  async createOffer(){return {type:"offer",sdp:"synthetic SDP"};}async createAnswer(){return {type:"answer",sdp:"synthetic SDP"};}
  async setLocalDescription(d){if(d.type==="offer")this.transceivers.forEach((t,i)=>{t.mid=String(i);});this.localDescription=d;this.signalingState=d.type==="offer"?"have-local-offer":"stable";}
  async setRemoteDescription(d){
    // Giả lập đúng cách WebRTC ghép kênh: kênh tạo bằng addTransceiver không được
    // dùng lại cho offer nhận vào. Trình duyệt tạo các kênh riêng theo quá trình thương lượng.
    if(d.type==="offer")for(const [i,kind] of ["audio","video"].entries()) {
      if(!this.transceivers.some(t=>t.mid===String(i))){const t=this.addTransceiver(kind,{direction:"recvonly"});t.mid=String(i);}
    }
    this.remoteDescription=d;this.signalingState=d.type==="offer"?"have-remote-offer":"stable";
  }
  async addIceCandidate(c){this.candidates.push(c);}close(){this.connectionState="closed";}
}
globalThis.RTCPeerConnection=PC;
const {state}=await import("../static/core/state.js");
const {joinCall,toggleDevice,leaveCall,pollCall}=await import("../static/features/classroom/call.js");
state.identity={userId:"u",displayName:"Student",role:"STUDENT"};state.token="token";state.peerId="aaa";state.room={roomId:"r1"};state.page="overview";
let roster=[{peerId:"zzz",displayName:"Teacher",audio:true,video:false}],packets=[],sent=[];
globalThis.fetch=async (url,options={})=>{
  if(options.method==="POST"){
    const m=JSON.parse(options.body);sent.push(m);
    if(m.kind==="JOIN")roster.push({peerId:"aaa",displayName:"Student",audio:m.audio,video:m.video});
    if(m.kind==="STATE")Object.assign(roster.find(p=>p.peerId==="aaa"),{audio:m.audio,video:m.video});
    if(m.kind==="LEAVE")roster=roster.filter(p=>p.peerId!=="aaa");
    return {ok:true,status:200,json:async()=>({ok:true})};
  }
  const signals=packets;packets=[];
  return {ok:true,status:200,json:async()=>({ok:true,peers:roster,signals})};
};
await joinCall();
check(state.callJoined&&captures===0,"listen-only join requests no device permission");
check(pcs.length===1&&pcs[0].senders.length===2,"audio/video transceivers negotiated before devices are enabled");
check(sent.some(m=>m.kind==="OFFER"&&m.toPeerId==="zzz"),"deterministic initiator sends offer");
await toggleDevice("audio");const mic=state.callLocal.getAudioTracks()[0];
check(captures===1&&mic.enabled,"mic capture only after explicit action");
check(pcs[0].senders.find(s=>s.kind==="audio").track===mic,"mic attached to existing connection");
await toggleDevice("audio");check(!mic.enabled,"mute disables outgoing audio track");
await toggleDevice("audio");check(mic.enabled&&captures===1,"unmute reuses the same device");
await toggleDevice("video");const camera=state.callLocal.getVideoTracks()[0];
check(camera&&pcs[0].senders.find(s=>s.kind==="video").track===camera,"camera replaces negotiated video sender without another offer");
await toggleDevice("video");
check(camera.readyState==="ended"&&state.callLocal.getVideoTracks().length===0,"camera off stops capture");
check(pcs[0].senders.find(s=>s.kind==="video").track===null,"camera sender detached");
packets=[{id:1,kind:"ICE",fromPeerId:"zzz",payload:JSON.stringify({candidate:"candidate-1"})},{id:2,kind:"ANSWER",fromPeerId:"zzz",payload:JSON.stringify({type:"answer",sdp:"answer"})}];
await pollCall();check(pcs[0].candidates.length===1&&pcs[0].remoteDescription.type==="answer","ICE arriving before SDP is queued and flushed");
const incoming=track("audio");pcs[0].ontrack({track:incoming});
check(state.callStreams.get("zzz").getAudioTracks()[0]===incoming,"remote track becomes a playable stream");
await leaveCall();
check(mic.readyState==="ended"&&!state.callJoined&&state.callLocal===null,"leaving stops all capture");
check(pcs[0].connectionState==="closed"&&state.callStreams.size===0,"leaving closes peer connections and remote streams");
check(sent.at(-1).kind==="LEAVE","server notified of departure");

state.peerId="zzz";roster=[{peerId:"aaa",displayName:"Teacher",audio:true,video:true}];
globalThis.fetch=async (url,options={})=>{
  if(options.method==="POST"){
    const m=JSON.parse(options.body);sent.push(m);
    if(m.kind==="JOIN")roster.push({peerId:state.peerId,displayName:"Student",audio:m.audio,video:m.video});
    if(m.kind==="STATE")Object.assign(roster.find(p=>p.peerId===state.peerId),{audio:m.audio,video:m.video});
    if(m.kind==="LEAVE")roster=roster.filter(p=>p.peerId!==state.peerId);
    return {ok:true,status:200,json:async()=>({ok:true})};
  }
  const signals=packets;packets=[];return {ok:true,status:200,json:async()=>({ok:true,peers:roster,signals})};
};
const beforeResponder=sent.length;await joinCall();
check(!sent.slice(beforeResponder).some(m=>m.kind==="OFFER"),"responder waits for deterministic initiator instead of glare");
packets=[{id:3,kind:"ICE",fromPeerId:"aaa",payload:JSON.stringify({candidate:"candidate-2"})},{id:4,kind:"OFFER",fromPeerId:"aaa",payload:JSON.stringify({type:"offer",sdp:"offer"})}];
await pollCall();
check(sent.at(-1).kind==="ANSWER"&&sent.at(-1).toPeerId==="aaa","responder answers incoming offer");
check(pcs.at(-1).candidates.length===1,"responder flushes early ICE after remote offer");
check(pcs.at(-1).getTransceivers().length===2,"answerer has exactly the offered audio/video channels, no unassociated duplicates");
await toggleDevice("audio");const disconnectedMic=state.callLocal.getAudioTracks()[0];
check(pcs.at(-1).getTransceivers().some(t=>t.mid!==null&&t.direction==="sendrecv"&&t.sender.track===disconnectedMic),"responder mic is attached to a negotiated sendrecv channel");
await toggleDevice("video");const responderCamera=state.callLocal.getVideoTracks()[0];
check(pcs.at(-1).getTransceivers().some(t=>t.mid!==null&&t.direction==="sendrecv"&&t.sender.track===responderCamera),"responder camera uses the negotiated channel when enabled after answering");
check(pcs.at(-1).getTransceivers().every(t=>t.sender.streams.includes(state.callLocal)),"both negotiated senders use the local stream identity");
roster=roster.filter(p=>p.peerId!==state.peerId);await pollCall();
check(!state.callJoined&&disconnectedMic.readyState==="ended","server-side membership loss stops capture and peer connections");

const beforeDenied=sent.length;
navigator.mediaDevices.getUserMedia=async()=>{throw new Error("Permission denied");};
await assert.rejects(joinCall("audio"),/Permission denied/);checks++;
check(!state.callJoined&&sent.length===beforeDenied,"permission rejection never registers an active call");

let finishCapture;
navigator.mediaDevices.getUserMedia=()=>new Promise(resolve=>{finishCapture=resolve;});
const joining=joinCall("video");await Promise.resolve();await leaveCall(false);
const cancelled=track("video");finishCapture(new Stream([cancelled]));
await assert.rejects(joining,/rời phòng/);checks++;
check(cancelled.readyState==="ended"&&!state.callJoined,"late capture after leaving is stopped immediately");

// Server gửi lại gói tin cho đến khi client xác nhận mốc đã nhận. Nếu bật/tắt thiết bị
// trùng lúc lấy tín hiệu nền, không được áp dụng cùng offer hai lần cho một kết nối.
let heldOffer=null,activePolls=0,maxPolls=0;
roster=[{peerId:"aaa",displayName:"Teacher",audio:true,video:true}];
globalThis.fetch=async (url,options={})=>{
  if(options.method==="POST") {
    const m=JSON.parse(options.body);sent.push(m);
    if(m.kind==="JOIN")roster.push({peerId:state.peerId,displayName:"Student",audio:m.audio,video:m.video});
    if(m.kind==="LEAVE")roster=roster.filter(p=>p.peerId!==state.peerId);
    return {ok:true,status:200,json:async()=>({ok:true})};
  }
  activePolls++;maxPolls=Math.max(maxPolls,activePolls);
  const after=Number(new URL(url,"http://test").searchParams.get("after"));
  const signals=heldOffer&&after<heldOffer.id?[heldOffer]:[];
  await new Promise(resolve=>setImmediate(resolve));activePolls--;
  return {ok:true,status:200,json:async()=>({ok:true,peers:roster,signals})};
};
await joinCall();const beforeDuplicate=sent.filter(m=>m.kind==="ANSWER").length;
heldOffer={id:5,kind:"OFFER",fromPeerId:"aaa",payload:JSON.stringify({type:"offer",sdp:"offer"})};
await Promise.all([pollCall(),pollCall()]);
check(maxPolls===1,"background and device polls cannot overlap");
check(sent.filter(m=>m.kind==="ANSWER").length===beforeDuplicate+1,"re-delivered offer answered exactly once");
await leaveCall();
console.log(`Meeting media: ${checks} checks PASS (synthetic WebRTC/devices; real media E2E not claimed).`);
