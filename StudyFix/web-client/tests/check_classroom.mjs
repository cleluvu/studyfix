// Behavioral module tests with a synthetic DOM and HTTP responses, not a real-browser E2E test.
import assert from "node:assert/strict";

const storage = new Map(), elements = new Map(), timers = new Map();
let timerId = 0, checks = 0;
globalThis.sessionStorage = { get length() { return storage.size; }, key: i => [...storage.keys()][i], getItem: k => storage.get(k) ?? null, setItem: (k,v) => storage.set(k,v), removeItem: k => storage.delete(k) };
globalThis.setTimeout = fn => { const id = ++timerId;timers.set(id, fn);return id; };
globalThis.clearTimeout = id => timers.delete(id);
function element() {
  return { innerHTML: "", setAttribute() {}, focus() {}, querySelector() { return { focus() {} }; },
    appendChild(node) { if (node.id) elements.set(node.id,node); }, remove() { elements.delete(this.id); } };
}
const root = element(), toasts = element();
globalThis.document = { activeElement: null, querySelector: s => s === "#app" ? root : s === "#toast-region" ? toasts : null,
  getElementById: k => elements.get(k) || null, createElement: element, body: element() };
globalThis.confirm = () => true;
const { state } = await import("../static/core/state.js");
const { renderClassroom, loadClassroom, classroomInput, classroomClick } = await import("../static/features/classroom/classroom.js");
function check(value, message) { assert.ok(value, message); checks++; }
const branch = { userId: "s1", username: "student1", displayName: "Học sinh A", code: "class Main {}", version: 0, baseVersion: 4, updatedAt: 1, online: true, latestCheck: {}, checks: [] };
function setup(owner = false) {
  state.identity = { userId: owner ? "t1" : "s1", role: owner ? "TEACHER" : "STUDENT" };
  state.token = "test-token";state.page = "overview";state.booting = true;
  state.room = { roomId: "1", code: "// MASTER", version: 4, members: [], history: [] };
  state.classroom = { phase: "BRANCHING", round: 1, isOwner: owner, branches: [structuredClone(branch)], exercise: {}, events: [], chat: [] };
  state.classroomRoomId = "1";state.classroomDrafts = {};state.classroomAutoCheck = false;storage.clear();
}
function response(body, status = 200) { return { status, ok: status >= 200 && status < 300, json: async () => body }; }
setup();
let html = renderClassroom("CHAT", "MEMBERS");
check(/id="classroom-master"[^>]*readonly/.test(html), "student Master is read-only");
check(!/id="classroom-branch"[^>]*readonly/.test(html), "student can edit own branch while branching");
check(!html.includes('data-op="FORK"'), "student cannot see teacher actions");
state.classroom.phase = "MERGING";
check(/id="classroom-branch"[^>]*readonly/.test(renderClassroom("", "")), "freeze makes branch read-only");

setup(true);state.classroom.phase = "LECTURE";
html = renderClassroom("", "");
check(html.includes('data-op="FORK"') && !/id="classroom-master"[^>]*readonly/.test(html), "owner can edit Master and fork in lecture");

setup();
classroomInput({ id: "classroom-branch", value: "// local unsaved" });
const key = "studyfix.classroom.s1.1.branch.1";
check(JSON.parse(storage.get(key)).code === "// local unsaved", "unsaved branch persisted locally");
state.classroom.branches[0].code = "// changed remotely";state.classroom.branches[0].version = 1;
html = renderClassroom("", "");
check(html.includes("// local unsaved") && html.includes("phiên đăng nhập khác"), "remote updates preserve draft and show conflict");
let sent;
globalThis.fetch = async (path, options) => { sent = JSON.parse(options.body);return response({ message: "Version conflict" }, 409); };
await assert.rejects(classroomClick("classroom-save-branch", {}), /Version conflict/);checks++;
check(sent.expectedVersion === 0 && sent.code === "// local unsaved", "conflicted save uses original expected version");
check(JSON.parse(storage.get(key)).code === "// local unsaved", "failed save keeps local draft");

setup();classroomInput({ id: "classroom-branch", value: "// snapshot being saved" });
let resolveSave;
globalThis.fetch = async (path, options = {}) => {
  if (path === "/api/classroom" && options.method === "POST") {
    sent = JSON.parse(options.body);
    return new Promise(resolve => { resolveSave = resolve; });
  }
  if (path === "/api/rooms/current") return response({ room: state.room });
  throw new Error(`Unexpected request ${path}`);
};
const saving = classroomClick("classroom-save-branch", {});
await Promise.resolve();
classroomInput({ id: "classroom-branch", value: "// typed while save was in flight" });
const savedView = structuredClone(state.classroom);savedView.branches[0].code = "// snapshot being saved";savedView.branches[0].version = 1;
resolveSave(response({ classroom: savedView }));await saving;
check(sent.code === "// snapshot being saved", "request has fixed snapshot");
check(state.classroomDrafts[key].code === "// typed while save was in flight" && state.classroomDrafts[key].dirty, "typing during save is retained");
check(state.classroomDrafts[key].baseVersion === 1, "new draft continues from acknowledged version");

// A poll started before a mutation must not restore an older server view afterward.
let resolvePoll;
globalThis.fetch = async (path, options = {}) => {
  if (path === "/api/classroom" && !options.method) return new Promise(resolve => { resolvePoll = resolve; });
  if (path === "/api/classroom") {
    const next = structuredClone(state.classroom);next.branches[0].code = "// typed while save was in flight";next.branches[0].version = 2;
    return response({ classroom: next });
  }
  return response({ room: state.room });
};
const oldPoll = loadClassroom();await Promise.resolve();
await classroomClick("classroom-save-branch", {});
resolvePoll(response({ classroom: savedView }));await oldPoll;
check(state.classroom.branches[0].version === 2, "late polling response cannot regress saved branch");
classroomInput({ id: "classroom-branch", value: "// unfinished previous round" });
state.classroom.round = 2;state.classroom.branches[0].version = 0;
check(renderClassroom("", "").includes("Lấy nháp vòng 1"), "previous-round unsaved draft remains recoverable");
await classroomClick("classroom-local-recovery", { key });
check(elements.get("active-modal").innerHTML.includes("// unfinished previous round"), "recovery exposes exact old local draft");

setup(true);state.classroom.phase = "MERGING";
globalThis.fetch = async path => response({ branch: { ...branch, round: 1, version: 3, code: "// <script>unsafe</script>", checks: [] } });
await classroomClick("classroom-preview", { userId: "s1" });
html = elements.get("active-modal").innerHTML;
check(html.includes("&lt;script&gt;") && html.includes('data-version="3"'), "preview escapes code and pins branch version");
check(html.includes('data-action="close-modal"') && html.includes("Chọn nhánh này làm Master"), "preview modal has body, close and merge controls");
state.classroom.round = 2;
await classroomClick("classroom-preview", { userId: "s1", round: "1" });
check(!elements.get("active-modal").innerHTML.includes("Chọn nhánh này làm Master"), "historical branch preview cannot merge into current round");

setup();state.classroom.branches[0].latestCheck = { id: "c1", status: "DONE", result: { verdict: "SANDBOX_UNAVAILABLE", feedback: "Docker unavailable" } };
html = renderClassroom("", "");
check(html.includes("SANDBOX_UNAVAILABLE") && !html.includes("ACCEPTED"), "unavailable sandbox is never shown as accepted");
setup(true);state.classroom.branches[0].codePreview='public class Main {\n// <script>unsafe</script>\n}';
html=renderClassroom("CHAT","MEMBERS");
check(html.includes('data-meeting-view="teacher"')&&html.includes("meeting-code-grid"),"teacher receives meeting grid layout");
check(html.includes("syntax-keyword")&&html.includes("&lt;script&gt;"),"real code previews highlighted and escaped");
await classroomClick("classroom-filter",{filter:"attention"});
check(renderClassroom("","").includes("Chưa có bài làm phù hợp"),"teacher attention filter uses real result state");
state.meetingFilter="";
await classroomClick("classroom-panel",{panel:"chat"});
check(renderClassroom("CHAT","MEMBERS").includes("meeting-drawer"),"chat drawer opens from meeting controls");
await classroomClick("classroom-panel",{panel:"chat"});
check(!renderClassroom("CHAT","MEMBERS").includes('class="meeting-drawer"'),"same control closes chat drawer");
setup();html=renderClassroom("","");
check(html.includes('data-meeting-view="student"')&&html.includes("Challenge Terminal"),"student has dedicated editor and real judge terminal");
check(html.includes('data-action="call-mic"')&&html.includes('data-action="call-camera"'),"meeting exposes real device controls");
state.roomTab="screen";state.remoteStream={getAudioTracks:()=>[{readyState:"live"}]};state.screenPlaybackBlocked=true;
html=renderClassroom("","");
check(html.includes("Có luồng âm thanh chia sẻ")&&html.includes('data-action="play-screen-audio"'),"screen audio status and blocked-playback control are visible");
state.remoteStream=null;state.screenPlaybackBlocked=false;state.roomTab="code";
const { loadCurrentRoom } = await import("../static/core/polling.js");
const beforeNetworkError = state.room;
globalThis.fetch = async () => { throw new Error("Temporary network failure"); };
await loadCurrentRoom(false);
check(state.room === beforeNetworkError, "temporary network failure preserves room and branch workspace");
globalThis.fetch = async () => response({ error: "NO_ROOM", message: "Bạn chưa vào phòng" }, 409);
await loadCurrentRoom(false);
check(state.room === null && state.classroom === null, "server-confirmed departure clears stale room");
console.log(`Classroom frontend: ${checks} behavioral checks PASS (synthetic DOM/HTTP; no browser E2E claim).`);
