// Static regression guard for the seven StudyFix bug groups.
// This complements (does not replace) gateway, TCP, browser and manual acceptance tests.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const read = (p) => readFileSync(path.join(root, p), 'utf8');
const tests = [];
function test(name, condition) {
  assert.ok(condition, name);
  tests.push(name);
  console.log(`PASS ${name}`);
}

const html = read('static/index.html');
const events = read('static/core/events.js');
const room = read('static/features/room/room.js');
const exercises = read('static/features/exercises/exercises.js');
const contests = read('static/features/contests/contests.js');
const render = read('static/ui/render.js');
const modal = read('static/ui/modal.js');
const screen = read('static/features/screen-share/screen-share.js');
const polling = read('static/core/polling.js');
const css = read('static/app.css');

const serverRoot = path.resolve(root, '..', 'server');
const service = readFileSync(path.join(serverRoot, 'src/studyfix/assignment/service/AssignmentService.java'), 'utf8');
const gateway = readFileSync(path.join(serverRoot, 'src/studyfix/WebGatewayServer.java'), 'utf8');

// 1. Exact starter confirmation / restore, with draft and optimistic-version guards.
test('DEBUG_COLLAB confirmation offers replace/keep/defer',
  room.includes('Nạp starter của bài vừa giao') && room.includes('Giữ code phòng') && room.includes('Để sau'));
test('Starter restore requires exact assignment and verifies returned assignmentId',
  /const row = assignmentId\s*\?/.test(room) && room.includes('Không tìm thấy đúng bài sửa lỗi được chọn') && room.includes('prepared.assignmentId !== row.id'));
test('Starter restore protects drafts/edit turns and detects version conflicts',
  room.includes('prepared.draftActive || prepared.editTurnActive') && room.includes('prepared.roomCodeVersion') && room.includes('expectedVersion: prepared.roomCodeVersion'));
test('Successful room assignment opens confirmation for exact returned assignment without re-creating it',
  events.includes('r.starterLoadMessage && !r.starterLoaded') && events.includes('openStarterLoadConfirm(finalRow.id)') && events.includes('state.assignments.find((a) => a.id === created.id)'));

// 2. Remove redundant call-to-action from exercise detail.
test('Exercise detail no longer contains redundant “Chọn bài này để giao” action',
  !exercises.includes('Chọn bài này để giao') && !exercises.includes('select-exercise-to-assign'));

// 3. Personal workspace file import, safe replacement, and no auto-submit.
test('Personal workspace exposes file import control beside template/submit actions',
  exercises.includes('Nạp code từ file') && exercises.includes('id="work-code-file"') && exercises.includes('choose-work-code-file'));
test('File import validates extension, size and empty input, and confirms replacement',
  events.includes('work-code-file') && events.includes('32 KB') && events.includes('File không có code') && events.includes('Nạp file sẽ thay code bạn đang chỉnh sửa'));
test('File import populates editor state without automatically submitting',
  /state\.workCode = content/.test(events) && /state\.workCodeDirty = true/.test(events) && events.includes('Chưa nộp bài'));

// 4. Accepted status is user-specific and context-specific.
test('Assignment lists visually highlight AC only from acceptedByMe',
  exercises.includes('a.acceptedByMe ? "accepted-row"') && render.includes('a.acceptedByMe ? "accepted-row"'));
test('Contest problems visually highlight AC from current participant result',
  contests.includes('p.acceptedByMe ? "contest-card-accepted"') && contests.includes('p.acceptedByMe ? pill("AC", "ac")'));
test('Polling updates local accepted state after final accepted result',
  polling.includes('acceptedByMe') && /verdict.*AC|AC.*verdict/s.test(polling));
test('Backend computes accepted state per user and exact contest context',
  service.includes('hasAcceptedSubmission(a.id(),who.userId())') && service.includes('hasAcceptedContestSubmission(cid,exerciseId,who.userId())'));
test('Accepted styling includes non-color text/icon affordance',
  css.includes('.accepted-row') && contests.includes('✓') && css.includes('.contest-card-accepted'));

// 5. Hide leaderboard in student UI and enforce teacher role in API.
test('Student contest UI omits leaderboard section and buttons',
  contests.includes('${teacher ? `<aside class="card"><div class="card-head"><div><h3>Bảng xếp hạng') && contests.includes('${teacher ? btn("BXH"') && contests.includes('state.identity?.role === "TEACHER"'));
test('Frontend blocks student leaderboard action',
  events.includes('if (state.identity?.role !== "TEACHER") throw new Error("Bảng xếp hạng chỉ dành cho giáo viên.")'));
test('Backend authorization guards leaderboard response',
  /case "LEADERBOARD_GET"\s*->\s*\{requireOwner\(teacherOwner\)/.test(service));

// 6. Role-neutral screen sharing, room-scoped signaling and relay role regression.
test('Browser screen sharing can be initiated by either authenticated role',
  screen.includes('export async function startScreenShare()') && !/role\s*!==\s*["']TEACHER/.test(screen));
test('Browser share uses start/request/offer/answer/ICE/end and active-presenter discovery',
  ['"START"', '"REQUEST"', '"OFFER"', '"ANSWER"', '"ICE"', '"END"', '"ACTIVE"'].every((k) => screen.includes(k)) && polling.includes('activePresenterPeerId'));
test('Gateway routes signaling within the room and supports either presenter role',
  gateway.includes('registerScreenShare(roomId, s, payload)') && gateway.includes('target.addScreenSignal(roomId, kind, s.peerId, payload)') && gateway.includes('sessionIsInRoom(target, roomId)'));
const relayTest = readFileSync(path.join(serverRoot, 'test/studyfix/ScreenShareRelayIntegrationTest.java'), 'utf8');
test('Native relay integration covers teacher publisher and student publisher',
  relayTest.includes('Ticket publish = teacher.requestTicket("PUBLISH")') && relayTest.includes('Ticket studentPublish = student1.requestTicket("PUBLISH")') && relayTest.includes('Student publisher frame is relayed to teacher viewer'));
const swingShare = readFileSync(path.join(serverRoot, '..', 'client/src/main/java/vn/studyfix/client/ui/ScreenSharePanel.java'), 'utf8');
test('Swing client auto-requests VIEW after another room member starts sharing',
  swingShare.includes('raw.startsWith("[SCREEN_SHARE] STARTED")') && swingShare.includes('if (activeMode == null)') && swingShare.includes('requestTicket("VIEW")'));
test('Screen UI attaches streams and cleans up when sharing ends',
  screen.includes('pc.ontrack') && screen.includes('bindVideoElements') && screen.includes('export async function stopScreenShare') && screen.includes('clearRemotePresenter'));

// 7. Modal close works for dynamically mounted modal outside the root.
test('Modal utility mounts one accessible dialog and removes it on close',
  modal.includes('clearModal();') && modal.includes('id = "active-modal"') && modal.includes('role="dialog"') && modal.includes('?.remove()'));
test('Document-level delegated click handles close action and backdrop',
  events.includes('document.addEventListener("click"') && events.includes('case "close-modal":') && events.includes('event.target?.id === "active-modal"'));
test('Escape closes modal', events.includes('event.key === "Escape" && document.getElementById("active-modal")'));

console.log(`\nSeven-bug static regression: ${tests.length}/${tests.length} checks passed.`);
