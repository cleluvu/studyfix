# StudyFix — Báo cáo sửa 7 nhóm lỗi

Ngày kiểm tra: 29/10/2026

## Phạm vi

Sửa trực tiếp trên source StudyFix trong gói được cung cấp, gồm `web-client`, Java Web/API Gateway, assignment service/repository, Swing client và bài kiểm thử. Không tạo dự án thay thế. Không chạy migration lên database gốc.

## Kết quả theo từng nhóm

### 1. Giao bài DEBUG_COLLAB và starter code

- Backend tạo assignment và lưu snapshot người nhận trước khi thử nạp starter; nếu nạp thất bại thì assignment vẫn hợp lệ, phản hồi trạng thái để UI yêu cầu xử lý tiếp, tránh người dùng giao lại.
- Frontend ghép theo đúng ID assignment vừa được trả về. Khi code chưa được nạp, mở modal có ba lựa chọn: nạp starter của bài vừa giao, giữ code phòng, hoặc để sau.
- Nút `Khôi phục code ban đầu` nạp starter theo đúng assignment đang chọn; không tự lấy một bài DEBUG_COLLAB khác làm fallback.
- Trước khi nạp, frontend gọi `DEBUG_STARTER_PREPARE`, kiểm tra assignment ID, draft/edit turn và version; `DEBUG_STARTER_LOAD` gửi `expectedVersion` để tránh ghi đè code mới hơn.
- Nếu đang có draft/lượt sửa hoặc version thay đổi, thao tác bị từ chối rõ ràng và code hiện tại được giữ lại.

### 2. Bỏ nút chọn bài thừa trong chi tiết đề

- Đã bỏ CTA `Chọn bài này để giao` khỏi chi tiết đề; chọn bài vẫn thực hiện qua danh sách/khu vực chọn bài, còn nút `Giao bài` mới tạo assignment.

### 3. Nạp code từ file trong workspace cá nhân

- Đã thêm `Nạp code từ file` cho file `.java`/`.txt`, tối đa 32 KB.
- File rỗng/không đúng loại/đọc lỗi bị từ chối; nếu editor có chỉnh sửa chưa lưu thì hỏi xác nhận trước khi thay thế.
- Chọn file không tự động nộp bài.

### 4. Hiển thị bài đã AC

- Danh sách bài được giao/tổng quan dùng trạng thái `acceptedByMe` để tô nền/viền xanh và gắn nhãn AC.
- Danh sách bài trong contest dùng trạng thái riêng theo người dùng + contest + exercise.
- Backend trả `acceptedByMe` theo ID người dùng và ngữ cảnh bài tương ứng; polling cập nhật trạng thái ngay sau khi verdict cuối cùng là AC/ACCEPTED/PASS.
- Các trạng thái QUEUED/RUNNING hoặc lỗi chấm không được tính là AC.

### 5. Học sinh không xem bảng xếp hạng

- UI học sinh không render khu vực/nút bảng xếp hạng; luồng mở bảng xếp hạng phía frontend có guard role.
- Backend `LEADERBOARD_GET` xác minh quyền teacher/owner trước khi trả dữ liệu. Việc ẩn UI không phải lớp bảo vệ duy nhất.

### 6. Chia sẻ màn hình hai chiều

- Web FE: gateway chấp nhận người chia sẻ là giáo viên hoặc học sinh đã xác thực và ở trong phòng; signaling được giới hạn theo room membership. Mỗi phòng chỉ có một người phát WebRTC tại một thời điểm; thành viên khác yêu cầu nhận luồng, nhận offer/answer/ICE và xử lý END/cleanup.
- Native Swing relay: bổ sung test thật cho cả chiều giáo viên phát → học sinh xem và học sinh phát → giáo viên xem. Swing client tự yêu cầu VIEW khi nhận thông báo STARTED nếu bản thân chưa phát.
- Gateway integration test kiểm tra start/request/offer/answer/end và từ chối người không ở trong phòng.
- **Giới hạn cần biết:** native TCP relay của Swing và WebRTC signaling/media của trình duyệt là hai transport riêng, chưa có cầu nối để một người dùng Swing phát và người dùng Web FE nhận trong cùng một phiên. Các luồng bidirectional đã kiểm tra trong từng transport riêng. Web share từ thiết bị LAN yêu cầu HTTPS với chứng chỉ trình duyệt tin cậy (hoặc localhost); chỉ kiểm tra signaling ở cấp tích hợp, chưa thể chứng nhận chất lượng media WebRTC end-to-end trên trình duyệt thật trong môi trường đóng gói.

### 7. Modal mô tả đề không đóng được

- Event click được lắng nghe ở `document`, nên handler hoạt động dù modal gắn trực tiếp vào `document.body` ngoài root chính.
- Đã giữ `close-modal`, click backdrop và thêm đóng bằng Escape; utility đảm bảo chỉ có một modal đang mount.

## Kiểm thử đã chạy

### Frontend

- `node --check` cho 19 file JS: PASS.
- `node tests/check_imports.mjs`: PASS — không thiếu export và không phát hiện chu trình import.
- `node tests/check_seven_bugfixes.mjs`: PASS — 25 kiểm tra tĩnh cho 7 nhóm lỗi.
- Browser UI smoke test: **BLOCKED** trong môi trường hiện tại vì Chromium trả `net::ERR_BLOCKED_BY_ADMINISTRATOR` khi mở URL localhost; không đánh dấu là PASS.

### Backend/client và tích hợp

- `AssignmentModuleSelfTest`: 40 checks PASS.
- `AssignmentProtocolIntegrationTest`: 61 checks PASS.
- `IntegrationTest`: 40 checks PASS (TCP/SQLite, localhost).
- `WebGatewayIntegrationTest`: 54 checks PASS, có test quyền leaderboard và signaling screen share hai chiều.
- `ScreenShareRelayIntegrationTest`: 27 checks PASS, gồm giáo viên phát/học sinh xem và học sinh phát/giáo viên xem.
- `EditSessionTest`: 12/12 PASS khi chạy với `LC_ALL=C.UTF-8`.
- `RecoveryTest`: 22 checks PASS.
- `HistoryTest`: 78 checks PASS.
- `PersonOneTest`: 57 checks PASS với locale UTF-8.
- `ServerAdminLiveTest`: PASS.
- `ConsoleHistoryInterleaveTest`: PASS.
- `ConsoleClientTest`: có in các assertion PASS, nhưng tiến trình không kết thúc trong giới hạn thử chạy thủ công; không dùng kết quả này để tuyên bố test suite tổng thể kết thúc sạch.

Một lần chạy `ant integration-test` đầy đủ trong môi trường locale POSIX/JDK 21 bị vướng các test tạo tên file Unicode; các test đó chạy qua khi gọi riêng với `LC_ALL=C.UTF-8`. Vì vậy không ghi nhận toàn bộ một lần chạy Ant duy nhất là PASS sạch. Bản build được kiểm tra bằng JDK 21 override do môi trường này không có JDK 26; cấu hình mặc định trong project vẫn giữ release 26 cho máy phát triển mục tiêu.

## Database

SHA-256 của hai database trong source gốc và bản làm việc trùng nhau:

- `server/data/studyfix.db`: `40cf07c52bfaa52b334ef341456f970787f6dc701ffe18ad3c572cb5056dbd70`
- `server/data/studyfix-learning.db`: `cb72892e51092ed07b27e85e3223e90684f6113243bed9a46efe022899a2aa48`

Các bài tích hợp dùng database tạm trong `server/build`; không chạy migration lên database gốc.

## Cách kiểm tra nhanh sau khi giải nén trên Windows

1. Đọc `RUN_IN_VSCODE_VI.md`; dùng JDK 26 + Ant theo hướng dẫn.
2. Khởi động Server Manager, đăng nhập một tài khoản giáo viên và ít nhất hai học sinh.
3. Kiểm tra giao bài DEBUG_COLLAB khi phòng trống và khi code phòng khác starter; kiểm tra khôi phục code và tình huống draft/version conflict.
4. Thử nạp file vào bài cá nhân, gửi một bài AC và một bài sai; xác nhận chỉ bài AC được tô xanh.
5. Mở contest với vai trò học sinh để xác nhận không có BXH; đăng nhập giáo viên để kiểm tra BXH.
6. Kiểm tra screen share trên cùng một loại client trước; browser share từ LAN phải dùng HTTPS. Nếu trộn Swing và Web FE, lưu ý bridge giữa hai transport chưa có.
