import { state } from "../../core/state.js";
import { api } from "../../core/api.js";
import { render } from "../../ui/render.js";

let epoch = 0;
let busy = false;
let joinedRoom = "";
let cursor = 0;
const connections = new Map();
const ice = new Map();
state.callRoster ||= [];
state.callStreams ||= new Map();

async function signal(kind, data = {}) {
  return api("/api/call/signal", {method:"POST", body:{kind,...data}});
}
function refresh() { if (state.page === "room") render(); }
function localFlags() {
  return {audio: !!state.callLocal?.getAudioTracks().some(t => t.enabled && t.readyState === "live"), video: !!state.callLocal?.getVideoTracks().some(t => t.enabled && t.readyState === "live")};
}
async function capture(kind) {
  if (!navigator.mediaDevices?.getUserMedia) throw new Error("Mic/camera cần HTTPS hoặc localhost và trình duyệt hỗ trợ WebRTC.");
  return navigator.mediaDevices.getUserMedia(kind === "audio" ? {audio:{echoCancellation:true,noiseSuppression:true},video:false} : {audio:false,video:{width:{ideal:640},height:{ideal:360},frameRate:{ideal:15,max:24}}});
}
export async function joinCall(kind = "listen") {
  if (busy || state.callJoined) return;
  if (!state.room) throw new Error("Hãy vào phòng học trước.");
  if (typeof RTCPeerConnection === "undefined") throw new Error("Trình duyệt chưa hỗ trợ gọi WebRTC.");
  busy = true;const version = ++epoch, room = state.room.roomId, token = state.token;
  let stream,registered=false;
  try {
    stream = kind === "listen" ? new MediaStream() : await capture(kind);
    if (epoch !== version || token !== state.token || room !== state.room?.roomId) throw new Error("Bạn đã rời phòng trong lúc mở thiết bị.");
    state.callLocal = stream;
    await signal("JOIN", localFlags());
    registered=true;
    if (epoch !== version || token !== state.token || room !== state.room?.roomId) throw new Error("Phiên cuộc gọi đã thay đổi.");
    state.callJoined = true;joinedRoom = room;state.callError = "";
    watchTracks(stream);await pollCall();refresh();
  } catch(e) {
    stream?.getTracks().forEach(t => t.stop());state.callLocal = null;state.callJoined = false;
    if(registered&&token===state.token&&room===state.room?.roomId)try{await signal("LEAVE");}catch(_){}
    throw e;
  } finally {busy = false;}
}
function watchTracks(stream) {
  for (const t of stream.getTracks()) t.addEventListener("ended", () => {
    if (state.callJoined) signal("STATE", localFlags()).then(refresh).catch(() => {});
  }, {once:true});
}
async function makePeer(peerId) {
  if (connections.has(peerId)) return connections.get(peerId);
  const pc = new RTCPeerConnection({iceServers:[]}); // LAN peers; no third-party signaling or media service.
  const entry = {pc, stream:new MediaStream(), senders:{}, offered:false};
  connections.set(peerId, entry);
  for (const kind of ["audio","video"]) {
    const transceiver = pc.addTransceiver(kind, {direction:"sendrecv",streams:[state.callLocal]});
    entry.senders[kind] = transceiver.sender;
    const track = state.callLocal?.getTracks().find(t => t.kind === kind && t.readyState === "live");
    if (track) await transceiver.sender.replaceTrack(track);
  }
  pc.onicecandidate = e => {if(e.candidate && state.callJoined) signal("ICE",{toPeerId:peerId,payload:JSON.stringify(e.candidate.toJSON())}).catch(err => {state.callError=err.message;});};
  pc.ontrack = e => {
    if (!state.callJoined) return;
    if (!entry.stream.getTracks().some(t => t.id === e.track.id)) entry.stream.addTrack(e.track);
    state.callStreams.set(peerId,entry.stream);refresh();
  };
  pc.onconnectionstatechange = () => {
    if (pc.connectionState === "failed") { dropPeer(peerId);state.callError="Một kết nối media bị gián đoạn; đang kết nối lại.";refresh(); }
  };
  return entry;
}
function dropPeer(id) {
  const entry = connections.get(id);connections.delete(id);ice.delete(id);state.callStreams.delete(id);
  if(entry) {entry.pc.onconnectionstatechange=null;entry.pc.close();}
}
export async function leaveCall(notify = true) {
  ++epoch;
  const joined = state.callJoined;
  state.callJoined = false;joinedRoom = "";
  state.callLocal?.getTracks().forEach(t => t.stop());state.callLocal = null;
  for (const id of [...connections.keys()]) dropPeer(id);
  state.callStreams = new Map();state.callError = "";
  if (notify && joined && state.token && state.room) try {await signal("LEAVE");} catch (_) { /* Server also removes departed room members. */ }
  refresh();
}
export async function toggleDevice(kind) {
  if (busy) return;
  if (!state.callJoined) {await joinCall(kind);return;}
  busy = true;const version = epoch;
  try {
    const current = state.callLocal.getTracks().find(t => t.kind === kind && t.readyState === "live");
    if (current) {
      if (kind === "audio") current.enabled = !current.enabled;
      else {
        for (const entry of connections.values()) await entry.senders.video.replaceTrack(null);
        current.stop();state.callLocal.removeTrack(current);
      }
    } else {
      const captured = await capture(kind);
      if (version !== epoch || !state.callJoined) {captured.getTracks().forEach(t=>t.stop());return;}
      const track = captured.getTracks()[0];state.callLocal.addTrack(track);watchTracks(captured);
      for (const entry of connections.values()) await entry.senders[kind].replaceTrack(track);
    }
    await signal("STATE",localFlags());await pollCall();refresh();
  } finally {busy = false;}
}
export async function pollCall() {
  if (!state.room || !state.token) {if(state.callJoined) await leaveCall(false);return;}
  if (joinedRoom && joinedRoom !== state.room.roomId) await leaveCall(false);
  const room = state.room.roomId, version = epoch;
  const r = await api(`/api/call/signals?after=${cursor}`);
  if (room !== state.room?.roomId || version !== epoch) return;
  const changed = JSON.stringify(state.callRoster) !== JSON.stringify(r.peers);
  state.callRoster = r.peers || [];
  if (state.callJoined) {
    if (!state.callRoster.some(p=>p.peerId===state.peerId)) {
      await leaveCall(false);state.callError="Cuộc gọi đã đóng ở server. Nhấn tham gia để kết nối lại.";refresh();return;
    }
    const ids = new Set(state.callRoster.map(p=>p.peerId));
    for (const id of [...connections.keys()]) if(!ids.has(id))dropPeer(id);
    for (const msg of r.signals || []) {
      if (msg.id <= cursor) continue;
      if (!ids.has(msg.fromPeerId)) {cursor=Math.max(cursor,msg.id);continue;}
      try {
        const entry = await makePeer(msg.fromPeerId), pc = entry.pc;
        if (msg.kind === "ICE") {
          const candidate=JSON.parse(msg.payload);
          if(pc.remoteDescription) await pc.addIceCandidate(candidate);
          else {const pending=ice.get(msg.fromPeerId)||[];pending.push(candidate);ice.set(msg.fromPeerId,pending.slice(-128));}
        } else {
          const description=JSON.parse(msg.payload);
          if (msg.kind === "OFFER" && pc.signalingState !== "stable") throw new Error("Đang thương lượng lại cuộc gọi; vui lòng vào lại cuộc gọi nếu mất media.");
          await pc.setRemoteDescription(description);
          for(const candidate of ice.get(msg.fromPeerId)||[])await pc.addIceCandidate(candidate);
          ice.delete(msg.fromPeerId);
          if(msg.kind==="OFFER") {await pc.setLocalDescription(await pc.createAnswer());await signal("ANSWER",{toPeerId:msg.fromPeerId,payload:JSON.stringify(pc.localDescription)});}
        }
      } catch(e) {state.callError=e.message;dropPeer(msg.fromPeerId);}
      cursor=Math.max(cursor,msg.id);
    }
    for(const peer of state.callRoster) if(peer.peerId!==state.peerId && state.peerId < peer.peerId) {
      const entry=await makePeer(peer.peerId);
      if(!entry.offered && entry.pc.signalingState==="stable") {
        entry.offered=true;
        try {await entry.pc.setLocalDescription(await entry.pc.createOffer());await signal("OFFER",{toPeerId:peer.peerId,payload:JSON.stringify(entry.pc.localDescription)});}
        catch(e) {dropPeer(peer.peerId);state.callError=e.message;}
      }
    }
  }
  if(changed)refresh();
}
export async function callClick(action) {
  if(action==="call-join")await joinCall();
  if(action==="call-mic")await toggleDevice("audio");
  if(action==="call-camera")await toggleDevice("video");
  if(action==="call-leave")await leaveCall();
  if(action==="call-play") {
    for(const el of document.querySelectorAll("[data-call-audio]"))await el.play();
    state.callPlaybackBlocked=false;refresh();
  }
}
