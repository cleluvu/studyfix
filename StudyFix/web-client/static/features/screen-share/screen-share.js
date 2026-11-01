// Browser screen sharing. Signaling is room-scoped by WebGatewayServer; either role may publish.
import { state } from "../../core/state.js";
import { api } from "../../core/api.js";
import { toast } from "../../ui/toast.js";
import { render } from "../../ui/render.js";
import { bindVideoElements } from "../../core/dom.js";
import { btn } from "../../ui/components.js";
import { loadCurrentRoom, setScreenSignalHandler } from "../../core/polling.js";

const rtcConfig = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };

export async function startScreenShare() {
  if (!state.identity || !state.token) throw new Error("Hãy đăng nhập trước khi chia sẻ màn hình.");
  if (state.screenActive || state.screenLocalStarting) return;
  if (state.activeScreenPresenterId && state.activeScreenPresenterId !== state.peerId)
    throw new Error("Đang có thành viên khác chia sẻ màn hình trong phòng. Hãy đợi họ dừng trước khi chia sẻ.");
  if (!navigator.mediaDevices?.getDisplayMedia)
    throw new Error("Trình duyệt không hỗ trợ chia sẻ màn hình. Hãy dùng phiên bản mới và mở bằng HTTPS hoặc localhost.");

  state.screenLocalStarting = true;
  let stream = null;
  try {
    stream = await navigator.mediaDevices.getDisplayMedia({ video: { frameRate: 15 }, audio: false });
    state.localStream = stream;
    state.screenActive = true;
    state.remoteStream = null;
    state.remoteScreenPresenter = "";
    state.remoteScreenPresenterName = "";
    state.screenPeers = new Map();
    state.screenPendingIce = new Map();
    await loadCurrentRoom(false);
    if (!state.room) throw new Error("Bạn chưa ở trong phòng học.");
    const result = await sendSignal("", "START", state.identity.displayName || state.identity.username || "Thành viên");
    state.activeScreenPresenterId = result.activePresenterPeerId || state.peerId;
    state.screenRequestedPresenterPeerId = "";
    stream.getVideoTracks()[0]?.addEventListener("ended", () => {
      if (state.screenActive) stopScreenShare(true).catch(() => {});
    }, { once: true });
    state.page = "room";
    state.roomTab = "screen";
    render();
    bindVideoElements();
    toast("Đã bắt đầu chia sẻ. Thành viên trong phòng có thể yêu cầu nhận luồng.", "success");
  } catch (e) {
    // If the server rejects a second publisher or startup fails, do not leave the capture running.
    state.screenActive = false;
    state.localStream = null;
    state.activeScreenPresenterId = "";
    if (stream) stream.getTracks().forEach((track) => { try { track.stop(); } catch (_) {} });
    throw e;
  } finally {
    state.screenLocalStarting = false;
  }
}

export async function offerScreenTo(peerId) {
  if (!state.localStream || !state.screenActive || !peerId || peerId === state.peerId) return;
  const existing = state.screenPeers.get(peerId);
  if (existing && !["closed", "failed"].includes(existing.connectionState)) return;
  if (existing) { try { existing.close(); } catch (_) {} }

  const pc = new RTCPeerConnection(rtcConfig);
  state.screenPeers.set(peerId, pc);
  state.localStream.getTracks().forEach((track) => pc.addTrack(track, state.localStream));
  pc.onicecandidate = (event) => {
    if (event.candidate) sendSignal(peerId, "ICE", JSON.stringify(event.candidate)).catch(() => {});
  };
  pc.onconnectionstatechange = () => {
    if (["failed", "closed"].includes(pc.connectionState) && state.screenPeers.get(peerId) === pc)
      state.screenPeers.delete(peerId);
  };
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  await sendSignal(peerId, "OFFER", JSON.stringify(pc.localDescription));
}

export async function sendSignal(toPeerId, kind, payload) {
  return api("/api/screen/signal", {
    method: "POST",
    body: { toPeerId: toPeerId || "", kind, payload: payload || "" },
  });
}

function setRemotePresenter(peerId, displayName = "") {
  state.activeScreenPresenterId = peerId || "";
  state.remoteScreenPresenter = peerId || "";
  state.remoteScreenPresenterName = displayName || state.remoteScreenPresenterName || "Thành viên trong phòng";
}

async function flushPendingIce(peerId, pc) {
  if (!pc.remoteDescription) return;
  const waiting = state.screenPendingIce.get(peerId) || [];
  state.screenPendingIce.delete(peerId);
  for (const candidate of waiting) {
    try { await pc.addIceCandidate(candidate); } catch (e) { console.debug("Ignoring stale ICE candidate", e); }
  }
}

function showRemoteStream(peerId, stream) {
  state.remoteStream = stream;
  state.remoteScreenPresenter = peerId;
  if (state.page !== "room") state.page = "room";
  state.roomTab = "screen";
  render();
  bindVideoElements();
}

export async function receiveScreenSignal(signal) {
  if (!signal || !signal.kind) return;
  if (signal.roomId && state.room?.roomId && signal.roomId !== state.room.roomId) return;
  const peerId = String(signal.fromPeerId || "");
  const kind = String(signal.kind).toUpperCase();
  if (kind === "START" || kind === "ACTIVE") {
    if (!peerId || peerId === state.peerId) {
      if (peerId === state.peerId) state.activeScreenPresenterId = peerId;
      return;
    }
    setRemotePresenter(peerId, signal.payload || signal.activePresenterName || "");
    if (state.screenRequestedPresenterPeerId !== peerId) {
      state.screenRequestedPresenterPeerId = peerId;
      try { await sendSignal("", "REQUEST", ""); }
      catch (e) {
        state.screenRequestedPresenterPeerId = "";
        console.warn("Could not request active screen share", e);
      }
    }
    if (state.page === "room" && state.roomTab === "screen") render();
    return;
  }

  if (kind === "PRESENCE_END") {
    await clearRemotePresenter(peerId);
    return;
  }

  if (!peerId) return;
  try {
    if (kind === "REQUEST") {
      if (state.screenActive) await offerScreenTo(peerId);
      return;
    }
    if (kind === "OFFER") {
      // Teachers and students can both be the viewer/answerer. Room membership is enforced server-side.
      const previous = state.screenPeers.get(peerId);
      if (previous) { try { previous.close(); } catch (_) {} }
      const pc = new RTCPeerConnection(rtcConfig);
      state.screenPeers.set(peerId, pc);
      pc.ontrack = (event) => {
        const stream = event.streams?.[0];
        if (stream) showRemoteStream(peerId, stream);
      };
      pc.onicecandidate = (event) => {
        if (event.candidate) sendSignal(peerId, "ICE", JSON.stringify(event.candidate)).catch(() => {});
      };
      pc.onconnectionstatechange = () => {
        if (["failed", "closed"].includes(pc.connectionState) && state.screenPeers.get(peerId) === pc)
          state.screenPeers.delete(peerId);
      };
      await pc.setRemoteDescription(JSON.parse(signal.payload));
      await flushPendingIce(peerId, pc);
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      await sendSignal(peerId, "ANSWER", JSON.stringify(pc.localDescription));
      setRemotePresenter(peerId, state.remoteScreenPresenterName);
      return;
    }
    if (kind === "ANSWER") {
      const pc = state.screenPeers.get(peerId);
      if (pc) {
        await pc.setRemoteDescription(JSON.parse(signal.payload));
        await flushPendingIce(peerId, pc);
      }
      return;
    }
    if (kind === "ICE") {
      if (!signal.payload) return;
      const candidate = JSON.parse(signal.payload);
      const pc = state.screenPeers.get(peerId);
      if (pc?.remoteDescription) {
        try { await pc.addIceCandidate(candidate); } catch (e) { console.debug("Ignoring stale ICE candidate", e); }
      } else {
        const waiting = state.screenPendingIce.get(peerId) || [];
        waiting.push(candidate);
        state.screenPendingIce.set(peerId, waiting.slice(-64));
      }
      return;
    }
    if (kind === "END") await clearRemotePresenter(peerId);
  } catch (e) {
    console.warn("Screen signaling failed", e);
  }
}

async function clearRemotePresenter(peerId) {
  const pc = state.screenPeers.get(peerId);
  if (pc) { try { pc.close(); } catch (_) {} }
  state.screenPeers.delete(peerId);
  state.screenPendingIce.delete(peerId);
  if (!peerId || state.activeScreenPresenterId === peerId) {
    state.activeScreenPresenterId = "";
    state.screenRequestedPresenterPeerId = "";
  }
  if (!peerId || state.remoteScreenPresenter === peerId || state.remoteScreenPresenter === state.activeScreenPresenterId) {
    state.remoteStream = null;
    state.remoteScreenPresenter = "";
    state.remoteScreenPresenterName = "";
  }
  if (state.page === "room" && state.roomTab === "screen") render();
}

export async function stopScreenShare(notify = true) {
  const wasActive = state.screenActive;
  state.screenActive = false;
  if (notify && wasActive && state.token) {
    try { await sendSignal("", "END", ""); } catch (e) { console.warn("Could not notify room about stopped share", e); }
  }
  for (const pc of state.screenPeers.values()) { try { pc.close(); } catch (_) {} }
  state.screenPeers = new Map();
  state.screenPendingIce = new Map();
  if (state.localStream) state.localStream.getTracks().forEach((track) => { try { track.stop(); } catch (_) {} });
  state.localStream = null;
  state.remoteStream = null;
  state.remoteScreenPresenter = "";
  state.remoteScreenPresenterName = "";
  state.activeScreenPresenterId = "";
  state.screenRequestedPresenterPeerId = "";
  if (state.page === "room" && state.roomTab === "screen") render();
}

export function renderScreenWorkspace() {
  const isPublisher = state.screenActive;
  const hasRemotePresenter = Boolean(state.activeScreenPresenterId && state.activeScreenPresenterId !== state.peerId);
  const presenterName = state.remoteScreenPresenterName || "thành viên trong phòng";
  const stage = isPublisher
    ? `<video id="local-screen-video" autoplay muted playsinline></video>`
    : state.remoteStream
      ? `<video id="remote-screen-video" autoplay playsinline></video>`
      : `<div class="screen-placeholder"><div class="big">▣</div><strong>${hasRemotePresenter ? `Đang nhận màn hình của ${escapeText(presenterName)}` : "Chưa có ai chia sẻ màn hình"}</strong><p>${hasRemotePresenter ? "Đang thiết lập kết nối video. Nếu không xuất hiện hình ảnh, hãy kiểm tra quyền chia sẻ và kết nối mạng." : "Giáo viên và học sinh đều có thể bắt đầu chia sẻ. Mỗi phòng chỉ có một người phát tại một thời điểm."}</p></div>`;
  const shareButton = isPublisher
    ? btn("■ Dừng chia sẻ", "stop-screen", "btn-danger")
    : btn("▣ Bắt đầu chia sẻ màn hình", "start-screen", hasRemotePresenter ? "btn-soft" : "btn-primary", hasRemotePresenter ? "disabled title=\"Đợi người đang chia sẻ dừng lại\"" : "");
  return `<div class="card"><div class="card-head"><div class="stat-icon teal">▣</div><div><h3>Chia sẻ màn hình</h3><p>Giáo viên và học sinh có thể chia sẻ trong cùng phòng bằng WebRTC.</p></div><span class="push"></span>${btn("← Quay lại code", "room-tab", "btn-soft btn-sm", 'data-tab="code"')}</div><div class="card-body"><div class="screen-stage">${stage}</div><div class="footer-row">${shareButton}<span class="subtle-text">Chia sẻ qua trình duyệt yêu cầu HTTPS hoặc localhost; relay TCP của client Swing tiếp tục hoạt động riêng.</span></div><div class="notice-box warn">Mỗi phòng chỉ cho phép một người chia sẻ tại một thời điểm để tránh chồng luồng. Khi người đó dừng, thành viên khác có thể bắt đầu chia sẻ.</div></div></div>`;
}

function escapeText(value) {
  return String(value || "").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
}

setScreenSignalHandler(receiveScreenSignal);
