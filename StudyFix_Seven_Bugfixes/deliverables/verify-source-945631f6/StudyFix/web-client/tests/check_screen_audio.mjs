// Giả lập quay màn hình, RTC và DOM để test; chưa xác nhận âm thanh YouTube chạy trên trình duyệt thật.
import assert from "node:assert/strict";
let checks=0,options,requests=[],toasts=[],events=[];
function check(value,message){assert.ok(value,message);checks++;}
globalThis.sessionStorage={getItem:()=>null};
globalThis.setTimeout=()=>1;
globalThis.CustomEvent=class {constructor(type){this.type=type;}};
const elements=new Map(),root={innerHTML:""},toastRoot={appendChild(n){toasts.push(n.innerHTML);}};
globalThis.document={activeElement:null,querySelector:s=>s==="#app"?root:s==="#toast-region"?toastRoot:null,getElementById:id=>elements.get(id)||null,querySelectorAll:()=>[],createElement:()=>({innerHTML:"",remove(){}}),dispatchEvent:e=>events.push(e.type)};
function track(kind){return {id:kind,kind,readyState:"live",addEventListener(){},stop(){this.readyState="ended";}};}
class Stream {
  constructor(tracks=[]){this.tracks=[...tracks];}getTracks(){return this.tracks;}getVideoTracks(){return this.tracks.filter(t=>t.kind==="video");}getAudioTracks(){return this.tracks.filter(t=>t.kind==="audio");}addTrack(t){this.tracks.push(t);}
}
globalThis.MediaStream=Stream;
let captured=new Stream([track("video"),track("audio")]);
Object.defineProperty(globalThis,"navigator",{configurable:true,value:{mediaDevices:{getDisplayMedia:async o=>{options=o;return captured;}}}});
const pcs=[];
globalThis.RTCPeerConnection=class {
  constructor(){this.tracks=[];this.connectionState="new";pcs.push(this);}addTrack(t,s){this.tracks.push({track:t,stream:s});}close(){this.connectionState="closed";}
  async createOffer(){return {type:"offer",sdp:"mock"};}async createAnswer(){return {type:"answer",sdp:"mock"};}
  async setLocalDescription(d){this.localDescription=d;}async setRemoteDescription(d){this.remoteDescription=d;}
};
const {state}=await import("../static/core/state.js");
const {startScreenShare,stopScreenShare,offerScreenTo,receiveScreenSignal,playScreenAudio,renderScreenWorkspace}=await import("../static/features/screen-share/screen-share.js");
const {bindVideoElements}=await import("../static/core/dom.js");
state.identity={userId:"t",role:"TEACHER",displayName:"Teacher"};state.peerId="teacher";state.token="test";state.booting=true;state.room={roomId:"1",version:0,code:"code",members:[],history:[]};
globalThis.fetch=async (path,request={})=>{
  const body=request.body?JSON.parse(request.body):null;if(body)requests.push(body);
  return {ok:true,status:200,json:async()=>path==="/api/rooms/current"?{room:state.room}:{ok:true,activePresenterPeerId:state.peerId}};
};
await startScreenShare();
check(options.audio!==false&&options.audio.suppressLocalAudioPlayback===false,"capture requests tab audio while preserving publisher's local playback");
check(options.systemAudio==="include"&&options.video.displaySurface==="browser","picker offers system audio and prefers browser tabs");
check(state.localStream.getAudioTracks().length===1,"captured audio retained alongside video");
check(renderScreenWorkspace().includes('id="local-screen-video" autoplay muted'),"publisher preview is muted to avoid duplicate audio");
await offerScreenTo("student");
check(pcs[0].tracks.some(x=>x.track.kind==="audio")&&pcs[0].tracks.some(x=>x.track.kind==="video"),"screen WebRTC offer contains both audio and video tracks");
await stopScreenShare();
check(captured.getTracks().every(t=>t.readyState==="ended"),"stop sharing closes both capture tracks");
check(pcs[0].connectionState==="closed"&&requests.some(r=>r.kind==="END"),"stop sharing closes sender and notifies viewers");
captured=new Stream([track("video")]);await startScreenShare();
check(toasts.at(-1).includes("chưa có âm thanh"),"video-only capture is reported instead of claiming shared sound");
await stopScreenShare(false);

state.peerId="student";state.identity.role="STUDENT";
await receiveScreenSignal({kind:"OFFER",fromPeerId:"teacher",roomId:"1",payload:JSON.stringify({type:"offer",sdp:"mock"})});
const receiver=pcs.at(-1),audio=track("audio"),video=track("video");
receiver.ontrack({track:video,streams:[]});receiver.ontrack({track:audio,streams:[]});
check(state.remoteStream.getAudioTracks()[0]===audio&&state.remoteStream.getVideoTracks()[0]===video,"streamless incoming audio/video tracks are combined for playback");
const remote={srcObject:null,muted:true,play:async()=>{throw new Error("autoplay blocked");}};elements.set("remote-screen-video",remote);
bindVideoElements();await Promise.resolve();await Promise.resolve();
check(state.screenPlaybackBlocked&&events.includes("studyfix:playback-blocked"),"autoplay rejection exposes a retry control instead of silently dropping audio");
remote.play=async()=>{};await playScreenAudio();
check(!remote.muted&&!state.screenPlaybackBlocked,"explicit play unmutes and resumes shared media");
await receiveScreenSignal({kind:"END",fromPeerId:"teacher",roomId:"1"});
check(state.remoteStream===null&&receiver.connectionState==="closed","end clears viewer playback");
receiver.ontrack({track:track("audio"),streams:[]});
check(state.remoteStream===null,"late tracks from ended screen session cannot restart playback");
console.log(`Screen audio: ${checks} checks PASS (capture/RTC/DOM mocks; real browser playback not tested).`);
