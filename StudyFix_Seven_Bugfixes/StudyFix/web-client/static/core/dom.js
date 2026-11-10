// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state } from "./state.js";

export const root = document.querySelector("#app");

export const toastRegion = document.querySelector("#toast-region");

export function bindVideoElements() {
  for (const el of document.querySelectorAll?.("[data-call-peer], [data-call-audio]") || []) {
    const peer = el.dataset.callPeer || el.dataset.callAudio;
    const stream = peer === state.peerId ? state.callLocal : state.callStreams?.get(peer);
    if (el.srcObject !== (stream || null)) el.srcObject = stream || null;
    if (stream) el.play().catch(() => {
      if(el.dataset.callAudio&&!state.callPlaybackBlocked) {
        state.callPlaybackBlocked=true;
        document.dispatchEvent(new CustomEvent("studyfix:playback-blocked"));
      }
    });
  }
  const local = document.getElementById("local-screen-video");
  if (local && state.localStream && local.srcObject !== state.localStream) {
    local.srcObject = state.localStream;
    local.play().catch(() => {});
  }
  const remote = document.getElementById("remote-screen-video");
  if (remote && state.remoteStream) {
    if(remote.srcObject !== state.remoteStream) remote.srcObject = state.remoteStream;
    remote.play().catch(() => {
      if (!state.screenPlaybackBlocked) {
        state.screenPlaybackBlocked = true;
        document.dispatchEvent(new CustomEvent("studyfix:playback-blocked"));
      }
    });
  }
}
export function captureMediaElements() {
  return Array.from(document.querySelectorAll?.("[data-call-peer], [data-call-audio], #local-screen-video, #remote-screen-video") || []);
}
export function restoreMediaElements(previous) {
  const key = el => el.id || `${el.tagName}:${el.dataset.callPeer || el.dataset.callAudio}`;
  const available = new Map(previous.map(el => [key(el),el]));
  for(const placeholder of captureMediaElements()) {
    const old=available.get(key(placeholder));
    if(old) {placeholder.replaceWith(old);available.delete(key(placeholder));}
  }
  for(const old of available.values()) old.srcObject=null;
}
