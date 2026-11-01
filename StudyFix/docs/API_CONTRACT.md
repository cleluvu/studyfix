# StudyFix Web Gateway — API contract

## Luyện code không cần phòng

`POST /api/assignments` hỗ trợ `PRACTICE_LIST/GET/SUBMIT/RUN/SUBMISSIONS/RESULT` cho cả giáo viên/sinh viên đã đăng nhập, không cần membership phòng. Snapshot/kết quả riêng của chính người gọi; lịch sử tách khỏi assignment/contest, queue/sandbox dùng chung. [Contract đầy đủ](CODE_PRACTICE_VI.md).

## Mã phòng và directory hiện hành

Web `CREATE_ROOM` bắt buộc `roomCode` tự nhập; tạo/vào phòng đồng bộ trả 200 cùng `roomId`/`roomCode`. GET `/api/rooms?page=1&pageSize=5` liệt kê tất cả phòng chưa bị xóa, kể cả phòng trống, cố định 5 phòng/trang. Phòng được giữ qua restart. `DELETE_ROOM` nhận ID nội bộ, chỉ giáo viên chủ phòng được xóa; thành viên rời phòng nhưng giữ đăng nhập. JOIN tra mã công khai trước; bài nộp dùng ID số bất biến. Tái sử dụng mã đã xóa nhận ID mới. `/api/me` và `/api/rooms/current` trả thêm `roomCode`. POST `/api/auth/departing` báo đóng tab có khoảng đệm 8 giây. [Contract chi tiết](ROOM_CODES_AND_LIFECYCLE_VI.md).

`LEAVE_ROOM` qua `/api/rooms/current/command` trả 200 sau khi đã tách thành viên khỏi phòng, kèm `leftRoomId`. Nút **Rời cuộc họp** chỉ dừng media và về Tổng quan, giữ tham gia phòng; **Rời phòng** trong danh sách mới gửi `LEAVE_ROOM`. [Quy tắc điều hướng và refresh](MEETING_UI_VI.md).

## Chat và tin chưa đọc

`GET /api/rooms/current/chat?after=N` yêu cầu bearer session và membership phòng hiện tại. Trả `messages` (80 tin gần nhất theo thứ tự tăng dần), `latestId` và `unreadCount`. Mỗi tin có `id`, `userId`, `name`, `text`, `timestamp`, `system=false`. `unreadCount` đếm tất cả tin của người khác với ID lớn hơn N trong đúng phòng, không bị giới hạn bởi 80 tin hiển thị. User/room tự khai trong query không quyết định phạm vi đọc hoặc danh tính được loại trừ.

Mốc N do frontend giữ riêng theo tài khoản/phòng trong trình duyệt; GET không ghi biên nhận đọc lên server. Đây là số nhắc người nhận, không phải trạng thái người gửi xem ai đã đọc. Không có migration schema cho endpoint này.

## Cuộc gọi mic và camera

`GET /api/call/signals?after=N` trả `peers` (peerId, displayName, role, audio, video) của phòng hiện tại và `signals` dành riêng cho phiên đăng nhập. Cursor là số nguyên tăng dần; polling sau khi xử lý dùng ID cuối cùng để xác nhận gói đã nhận.

`POST /api/call/signal` nhận `kind`: `JOIN`/`STATE` với `audio`, `video` boolean; `LEAVE`; hoặc `OFFER`/`ANSWER`/`ICE` với `toPeerId`, `payload` chuỗi JSON. Room, sender và danh tính luôn lấy từ phiên xác thực. Gửi/nhận SDP hoặc ICE cần cả hai đã JOIN và cùng phòng. Signaling không thay thế bước người dùng cấp quyền thiết bị ở trình duyệt.

Giới hạn: 16 thiết bị media/phòng; 600 gói/phút/người gửi; 512 gói hoặc 256.000 ký tự payload chờ/người nhận; ICE tối đa 4.096 ký tự, SDP tối đa 64.000 ký tự. Rời phòng/đăng xuất xóa participant và tín hiệu liên quan; participant không polling hơn 20 giây bị loại khi registry được kiểm tra. Media truyền trực tiếp, không ghi vào database. Xem `MEETING_UI_VI.md` cho giới hạn LAN và nghiệm thu.

## Phòng học Master và nhánh riêng từ 29/10/2026

Các API dưới đây lấy room và danh tính từ bearer session hiện tại; không tin `roomId`, `sessionId`, `role` hoặc chủ nhánh tự khai trong payload. Backend kiểm tra membership và teacher owner. `400` là dữ liệu không hợp lệ, `403` là sai quyền, `409` là xung đột trạng thái/phiên bản, lỗi database trả `500` và không báo lưu thành công.

| Method | Path | Kết quả |
|---|---|---|
| GET | `/api/classroom` | Phase, round, bài công khai, danh sách nhánh được phép, chat; nhật ký chỉ teacher owner |
| POST | `/api/classroom` | Thực hiện `action`, trả trạng thái lớp học mới trong `classroom` |
| GET | `/api/classroom/branches/:userId?round=N` | Code nhánh và 20 lượt chấm gần nhất; học sinh chỉ đọc chính mình |
| GET | `/api/classroom/checks/:id` | Snapshot bất biến và kết quả của đúng lượt chấm, kể cả vòng trước |
| GET | `/api/classroom/rounds/:N` | Tổng hợp nhánh/kết quả của vòng cũ, có kiểm tra quyền từng người |

Teacher actions: `START`, `SELECT_EXERCISE`, `MASTER_SAVE`, `FORK`, `FREEZE`, `RESUME`, `MERGE`, `END`, `NEW_SESSION`. Mỗi request gửi `expectedPhase` và `round` đã thấy; sửa/chọn Master, tách nhánh và Merge cần thêm `expectedVersion` của Master. `SELECT_EXERCISE` nhận `exerciseId`; `MASTER_SAVE` nhận `code`; `MERGE` nhận `userId` của nhánh được chọn và `branchVersion` đúng bản đang xem.

Student actions: `BRANCH_SAVE` nhận `round`, `expectedVersion` của nhánh và `code`; `BRANCH_CHECK` nhận `round` và `expectedVersion` của nhánh đã lưu. Server luôn chọn nhánh từ `userId` của tài khoản đăng nhập. Source tối đa 32.000 byte UTF-8. Phase phải là `BRANCHING`. Một lần nộp lưu snapshot trước khi xếp hàng; trạng thái `QUEUED`/`RUNNING` và lỗi chấm không phải AC. `latestCheck.branchVersion` phải trùng nhánh hiện tại mới được coi là kết quả của code hiện tại.

Phòng dùng quy trình mới không cho học sinh sửa Master qua các lệnh TCP xin lượt cũ. Chat `/chat` được lưu trước khi broadcast. Các endpoint dưới đây vẫn được giữ để tương thích và phục vụ các phân hệ hiện có.

Gateway chạy cùng JVM với server và assignment service; mặc định HTTP 8080, cùng origin với static web client. Khi đã cấu hình `STUDYFIX_WEB_KEYSTORE`/`STUDYFIX_WEB_KEYSTORE_PASSWORD` hoặc JVM properties tương ứng, gateway dùng HTTPS. Authentication được bridge từ TCP control service hiện có; browser chỉ giữ bearer token riêng của gateway, không gửi core session id.

## Endpoint nhóm

| Method | Path | Mục đích | Auth/role |
|---|---|---|---|
| GET | `/api/health` | Service status/scheme/port | Public health only |
| POST | `/api/auth/login` | Đăng nhập qua core TCP login | Rate limited |
| POST | `/api/auth/logout` | Đóng phiên gateway/core | Bearer |
| GET | `/api/me` | Identity, peer ID, room hiện tại | Bearer |
| GET | `/api/rooms` | Danh sách phòng được phép thấy | Bearer |
| GET | `/api/rooms/current` | Shared code/version/token người đang sửa, member count, queue, history summary, private draft recovery | Room member; edit token chỉ gửi cho editor hiện tại |
| GET | `/api/rooms/current/history` | Lịch sử code chi tiết | Room member |
| POST | `/api/rooms/current/command` | Room/chat/edit commands | Bearer; core xác thực token/role |
| GET | `/api/events?after=N` | Event stream dạng polling có cursor | Bearer |
| POST | `/api/assignments` | Catalog, assignment, submit, shared check, contest, leaderboard, progress, teacher trial | Bearer; service kiểm tra teacher/student, owner/room/assignment |
| POST/GET | `/api/screen/signal(s)` | WebRTC signaling cho browser screen share | Room member; offer/end được giới hạn theo role |

## Assignment message types

- Catalog: `CATALOG_LIST`, `CATALOG_GET` (chỉ teacher), `ROOM_STUDENTS_LIST` (teacher), `CLASS_PROGRESS` (teacher).
- Assignment: `ASSIGN_CREATE`, `ASSIGN_LIST`, `ASSIGN_GET`, `DEBUG_STARTER_PREPARE`, `DEBUG_STARTER_LOAD`.
- Shared code: `SHARED_CODE_STATE`, `SHARED_CHECK_CREATE`, `SHARED_CHECK_LATEST`, `SHARED_CHECK_STATUS`, `SHARED_CHECK_RESULT`.
- Individual submission: `SUBMIT_CREATE`, `SUBMISSION_LIST`, `SUBMISSION_RESULT`.
- Contest: `CONTEST_CREATE`, `CONTEST_LIST`, `CONTEST_PROBLEMS_GET`, `CONTEST_SUBMIT`, `CONTEST_SUBMISSION_RESULT`, `CONTEST_SUBMISSION_LIST`, `LEADERBOARD_GET`.
- Teacher private trials: `TEACHER_TRIAL_CREATE`, `TEACHER_TRIAL_LIST`, `TEACHER_TRIAL_RESULT`; these do not count in student reports or official leaderboards.

## Contract guarantees / limits

- Contest creation accepts catalog `exerciseIds` directly, so no pre-assignment is required. `INDIVIDUAL` accepts only `BUILD_FROM_SCRATCH`. `TEAM_COLLAB` is explicitly refused until true team submission/scoring is implemented; a DEBUG_COLLAB check is not silently converted to individual AC.
- Student API access is checked server-side; hiding a control in JavaScript is not the authorization boundary.
- Hidden testcase expected values and reference solutions are not returned to browser clients.
- Submission verdicts `QUEUED`, `RUNNING`, `SANDBOX_UNAVAILABLE`, and errors do not count as AC.
- Default HTTP is for localhost or a trusted test LAN only. Use trusted HTTPS/TLS where credentials are sent over untrusted networks or browser screen capture needs a secure context.
