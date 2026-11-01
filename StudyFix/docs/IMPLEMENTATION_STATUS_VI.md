# StudyFix — Báo cáo triển khai theo `StudyFix_UI_UX_Redesign_Plan.md`

**Ghi chú phiên bản:** Báo cáo dưới đây mô tả bản Web Gateway trước chuyển đổi phòng học. Từ 29/10/2026, workspace chính dùng Master và nhánh cá nhân; xem [báo cáo hiện hành](CLASSROOM_BRANCH_WORKFLOW_VI.md). Các con số kiểm thử và cơ chế xin lượt trong báo cáo cũ không mô tả bản mới.

**Bản bàn giao:** functional Web FE + Java Web/API Gateway + các luồng nghiệp vụ được nối vào server hiện có; Swing client cũ vẫn được giữ làm fallback. Đây không phải một server/domain mới.

**Tài liệu chuẩn đối chiếu:** [`StudyFix_UI_UX_Redesign_Plan.md`](StudyFix_UI_UX_Redesign_Plan.md). Các mockup trong `docs/design-reference/` là ảnh concept, không phải ảnh chụp trình duyệt từ bản chạy thật.

## 1. Những gì đã xây dựng trong source

### 1.1 Web FE và đăng nhập

- Thêm `web-client/static/index.html`, `app.css` và `app.js`: SPA responsive, giao diện web sáng, điều hướng bốn khu vực Tổng quan / Phòng học / Bài tập / Thi & xếp hạng.
- Web FE được phục vụ từ chính Java Web Gateway cùng origin; không mở raw TCP protocol cho trình duyệt và không yêu cầu cài Node để chạy runtime.
- Đăng nhập được bridge sang dịch vụ đăng nhập TCP hiện có. Browser giữ token của gateway; `coreSessionId` nội bộ không được nhận từ request browser.
- Gateway hỗ trợ HTTP ở cổng mặc định 8080, TLS qua PKCS12 keystore. Có CSP/security headers, giới hạn body, giới hạn login rate và giới hạn request signaling.
- **Lưu ý về stack:** plan đề xuất React + TypeScript + Vite. Trong môi trường build lần này, `npm registry` không phân giải DNS được (`EAI_AGAIN`), nên UI được giao dưới dạng SPA HTML/CSS/JavaScript không dependency để có thể chạy ngay offline. Không có React/Vite bundle và không được coi yêu cầu stack React/TS/Vite là đã nghiệm thu.

### 1.2 Phòng học / Code cộng tác / chia sẻ màn hình

- Workspace cùng trang gồm editor code chung, mô tả bài sửa lỗi, thao tác xin/trả/lưu lượt sửa, kiểm tra code chung, verdict và lịch sử phiên bản thu gọn cuối trang.
- Không còn nút “Tải code” thường trực. Code tải khi mở phòng và được cập nhật theo event/polling; việc thay toàn bộ code là hành động giáo viên có xác nhận.
- Giáo viên chủ phòng có ưu tiên takeover ở server, không phải chỉ ở UI. Token cũ bị vô hiệu hóa, draft học sinh được giữ trong vùng khôi phục riêng; chỉ chính tài khoản đó nhận draft, và server không cho commit bằng token/version cũ.
- Khi giao `DEBUG_COLLAB`, starter được nạp tự động chỉ khi guard cho biết việc nạp an toàn; không âm thầm ghi đè code không liên quan hoặc draft đang mở.
- Chat chứa tin nhắn người dùng; notification vận hành hiển thị như system notice, không hiện “Server” giả làm người chat. Tổng thành viên và trạng thái online/đang giữ quyền được hiển thị.
- Giữ nguyên relay screen-sharing TCP 5001 cho Swing. Web FE có WebRTC signaling/media path riêng; `getDisplayMedia()` vẫn đòi secure context trong trình duyệt. Gateway hỗ trợ HTTPS, nhưng chưa có kiểm thử media E2E bằng trình duyệt thật trong container.

### 1.3 Bài tập / giao bài / nộp bài / báo cáo

- Ngân hàng và thao tác quản lý chỉ được render cho giáo viên; API phía server cũng kiểm tra role.
- Giáo viên tìm/lọc bài theo từ khóa, mode và chủ đề; mở chi tiết đề, chọn bài từ danh sách để giao; form hỗ trợ Cả phòng / Cá nhân, dropdown thành viên phòng và hạn nộp.
- `DEBUG_COLLAB` chỉ giao cho cả phòng. Bài `BUILD_FROM_SCRATCH` có thể giao cả phòng hoặc cá nhân.
- Học sinh không có ngân hàng hay form giao bài; danh sách bài được giao dẫn tới workspace đề + editor + nộp bài + verdict/test result an toàn + lịch sử nộp ngay trong cùng trang.
- Giáo viên có “Thử làm / chấm thử” riêng, lưu ở bảng `p3_teacher_trials` và dùng hàng đợi chấm nhưng tách khỏi thống kê AC/leaderboard học sinh.
- `CLASS_PROGRESS` chỉ dành cho giáo viên, tổng hợp số người nhận/nộp/AC và trạng thái gần nhất theo bài/học sinh. Từ chi tiết một bài, giáo viên có thể bấm vào học sinh để xem tất cả bài đã giao cho em đó, số bài đã nộp và danh sách bài đã AC. Recipient snapshot được lưu tại thời điểm giao bài để học sinh vào phòng sau đó không thay đổi danh sách người nhận lịch sử.
- Verdict chưa hoàn tất hoặc sandbox không khả dụng không được tính là AC; hidden expected output và reference solution không được trả cho browser.

### 1.4 Thi & xếp hạng

- Giáo viên tạo contest trực tiếp từ `exerciseIds` của catalog, chọn nhiều bài, tên, thời điểm bắt đầu tương đối và thời lượng; không phải tạo assignment trước, không nhập UUID.
- Học sinh vào contest và làm bài trong workspace riêng; kết quả/lịch sử contest và bảng xếp hạng được trả qua API contest.
- Migration nâng learning schema lên version 6; test tạo DB legacy v3 và xác nhận liên kết contest, association assignment và scoring rule cũ vẫn còn sau migration tới v6.
- **Giới hạn còn mở theo plan:** chỉ `INDIVIDUAL` được bật, và chỉ chấp nhận `BUILD_FROM_SCRATCH`. `TEAM_COLLAB` và bài `DEBUG_COLLAB` trong contest chưa được bật vì chưa có mô hình team submission/scoring/leaderboard thực sự. Server từ chối rõ ràng, không âm thầm chấm code chung như điểm cá nhân.

### 1.5 Server Manager và build/run scripts

- Server Manager Swing vẫn là app riêng. Một lần khởi động quản lý core TCP, screen relay, assignment/grading listener và web gateway trong cùng JVM.
- Màn hình quản trị có trạng thái các listener, connection/room, version, editor/queue, lịch sử/log; phần grading hiển thị worker cấu hình, đang chấm, queue/capacity, Docker image và cảnh báo không thể coi listener running là Docker ready.
- Cập nhật `run-server-manager.ps1`, `run-with-assignments.ps1`, `test-assignment-module.ps1` và Ant `integration-test` để thông báo cổng web và kiểm tra gateway. Mặc định Web FE tại `http://localhost:8080`.

## 2. Đối chiếu acceptance criteria

| Hạng mục trong plan | Kết quả bản này | Ghi chú |
|---|---|---|
| Contest chọn bài trực tiếp từ catalog | Đã triển khai | Không cần assignment trước; migration legacy được test tới schema v6. |
| Bài cá nhân có editor, submit, verdict, history cùng trang | Đã triển khai trong Web FE | Cần nghiệm thu browser thật trên Windows trong môi trường của bạn. |
| Ngân hàng và giao bài chỉ giáo viên | Đã triển khai | Role gate ở UI và API/backend. |
| Giao bài cá nhân bằng dropdown thành viên | Đã triển khai | Snapshot người nhận khi tạo assignment. |
| Sửa lỗi cùng phòng gộp vào Code cộng tác | Đã triển khai | Đề, shared check, kết quả, history cùng workspace. |
| Nạp code lỗi tự động có guard | Đã triển khai | Không tự động ghi đè code/draft khác. |
| Giáo viên takeover không làm mất nháp | Đã triển khai + test | Draft riêng cho người bị takeover; token cũ không commit được. |
| Chat không dùng “Server” làm người gửi | Đã triển khai | System notices tách khỏi chat. |
| Thành viên có tổng số | Đã triển khai | Hiển thị count + presence/editor state. |
| Bỏ trang Activity/Feedback khỏi luồng chính | Đã áp dụng trên Web FE | Swing fallback được giữ nguyên theo chiến lược chuyển đổi. |
| Giáo viên chạy thử không ảnh hưởng thống kê | Đã triển khai | `TEACHER_TRIAL_*` và bảng riêng. |
| Server Manager metrics | Đã triển khai mức cơ bản | Core/room/connection + assignment worker/queue; không phải một web admin dashboard mới. |
| Screen share | Đã giữ relay cũ + thêm đường WebRTC cho web | Relay TCP có test; media E2E trình duyệt chưa xác minh trong container. |
| React + TypeScript + Vite | Chưa nghiệm thu | NPM registry không truy cập được trong môi trường này; bản chạy thật dùng SPA không dependency. |
| Contest nhóm `TEAM_COLLAB` | Chưa triển khai | Bị chặn rõ đến khi có group scoring và đơn vị đội hợp lệ. |
| Docker grading trên engine thật | Chưa nghiệm thu | Container hiện tại không có Docker Engine; không giả kết quả PASS khi sandbox không sẵn sàng. |
| Build release 26 trên Windows | Chưa chạy trong môi trường này | Bản source giữ cấu hình mặc định release 26. Local Ant integration được chạy với override `javac.release=21` vì container chỉ có JDK 21. |

## 3. Kiểm thử thực tế trong môi trường này

Ant target `integration-test` chạy hết và kết thúc bằng **BUILD SUCCESSFUL** (JDK 21 override, locale UTF-8, Xvfb cho test Swing). Các nhóm kiểm tra:

| Test | Kết quả |
|---|---:|
| Assignment module, migration và nghiệp vụ | 40 checks PASS |
| Assignment TCP protocol integration | 61 checks PASS |
| Web Gateway HTTP/HTTPS integration | 32 checks PASS |
| Edit-session / takeover / token / draft | 12/12 PASS |
| Recovery / shutdown / durable code | 22 checks PASS |
| History / revision / Server Manager history | 78 checks PASS |
| Core TCP / reconnect / restart / heartbeat | 40 checks PASS |
| Screen relay TCP | 19 checks PASS |
| PersonOne real client/server contract | 57 checks PASS |
| Server Manager live TCP test | 3 checks PASS |
| Console history interleave | 2 checks PASS |
| Actual console client commands | 6 checks PASS |
| **Tổng** | **372 checks** |

Đã chạy thêm `node --check web-client/static/app.js` và biên dịch toàn bộ client main source bằng `javac --release 17`, đều thành công. Gateway integration test xác minh login/role, catalog/contest/assignment/report/trial, quyền edit/token và draft takeover, HTTPS health và static FE response qua HTTP thật trên loopback.

## 4. An toàn dữ liệu và các giới hạn cần nói rõ

- Source database gốc (`server/data/studyfix.db`, WAL/SHM và `studyfix-learning.db`) đã được so hash với ZIP gốc và **khớp byte-for-byte** sau kiểm thử. Test/migration chỉ chạy trên database tạm trong `server/build/`; khi đóng gói sẽ loại bỏ thư mục build này.
- Migration learning schema v6 chưa chạy trên DB runtime của người dùng. Sao lưu database và các file WAL/SHM khi server đã dừng trước khi chạy bản mới lần đầu.
- Docker Engine không có trong container. Vì thế chưa thể chứng minh bài Java compile/runtime thật chạy tới verdict AC trên Docker; ứng dụng phải trả `SANDBOX_UNAVAILABLE`, không fake verdict.
- NPM registry trả `EAI_AGAIN`; không thể tải React/Vite trong container. UI được bàn giao có chức năng nhưng lệch stack so với plan. Nếu React/TypeScript/Vite là điều kiện bắt buộc thay vì ưu tiên, cần môi trường có npm registry/cache để chuyển build pipeline và nghiệm thu riêng.
- Không bật contest nhóm bằng cách làm giả semantics. Trước khi hỗ trợ cần xác định đơn vị đội, thành viên/đội, chính sách submit và tie-break; current API từ chối `TEAM_COLLAB` rõ ràng.
- Web screen share cần HTTPS khi dùng từ IP LAN; HTTP mặc định chỉ nên dùng localhost hoặc mạng tin cậy. Chức năng media browser chưa được chạy E2E với ba browser thật.

## 5. Chạy trên Windows

1. Dừng instance StudyFix cũ, sao lưu toàn bộ `server/data/` (đặc biệt DB, `-wal`, `-shm`).
2. Mở Server Manager bằng `server\run-server-manager.ps1`. Script build lại bằng JDK 26/Ant từ cấu hình project, rồi khởi động listener hiện tại cùng Web Gateway.
3. Mở `http://localhost:8080` để dùng Web FE. Mở Swing client giáo viên/học sinh như trước để kiểm tra tương thích fallback.
4. Test bài chấm thực tế khi Docker Desktop/Engine và image chấm được cấu hình sẵn. Để dùng browser screen share qua LAN, cấu hình keystore TLS tin cậy hoặc reverse proxy HTTPS.
5. Không xóa `server/data/` để xử lý lỗi. Khi migration gặp lỗi, dừng server và phục hồi backup trước khi tiếp tục.
