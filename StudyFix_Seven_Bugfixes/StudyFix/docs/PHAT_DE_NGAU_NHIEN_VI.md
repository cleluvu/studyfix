# Phát đề ngẫu nhiên theo sinh viên

## Chọn sinh viên và tự chọn bài ngẫu nhiên

Trên mỗi thẻ kỳ thi còn hạn đã có nút **Giao đề**, cạnh **Xem kỳ thi** và **BXH**:

1. Bấm **Giao đề**.
2. Tích sinh viên nhận đề hoặc bấm **Chọn tất cả sinh viên**.
3. Nhập **Số bài chọn ngẫu nhiên**, bấm **Chọn ngẫu nhiên**: hệ thống tự tích đúng số bài trong danh sách của kỳ thi. Có thể tự tích/bỏ tích để chỉnh bộ bài.
4. Bấm **Giao đề đã chọn**. Các sinh viên được chọn nhận bộ bài vừa tích. Sinh viên chưa được giao không được mở kỳ thi hoặc nộp bài qua API.

Trong form **Tạo kỳ thi** cũng có ô số lượng và nút **Chọn ngẫu nhiên từ danh sách**. Nút này tự chọn bài từ danh sách ngân hàng đang hiển thị theo từ khóa; không phải tự tích từng bài trước. Tối đa 20 bài theo giới hạn hiện có.

Hai thao tác khác nhau: **Chọn ngẫu nhiên** tự tích một bộ bài để giáo viên xem và giao cho những sinh viên được chọn; **Phát đề ngẫu nhiên cho từng sinh viên** khi tạo kỳ thi vẫn bốc độc lập theo tài khoản như mô tả bên dưới. Đề đã phát trước đó giữ nguyên; giao thêm không đổi đề cũ. Muốn thay bộ đề của người đã nhận, hãy tạo kỳ thi mới.

Danh sách chọn người nhận lấy từ thành viên ENROLLED đang hoạt động của lớp trong MySQL, gồm cả người chưa kết nối. Khi dùng chế độ SQLite cũ, danh sách lấy các sinh viên đang trong phòng. Chỉ tên và tài khoản được trả về, không có IP hay thông tin phiên đăng nhập.

Server tái kiểm tra chủ lớp, người nhận và mã bài; việc ẩn nút ở giao diện không phải lớp bảo vệ duy nhất. Dữ liệu giao đề được lưu trong nhóm bảng learning hiện có, thêm `p3_contest_audience` để đánh dấu kỳ thi đã giới hạn người nhận. Nút giao chỉ bổ sung người nhận, không thu hồi đề đã phát.

File bổ sung/sửa: `contest-assignment.js`, `contests.js`, `events.js`, `StudyFixGateway.java`, `WebGatewayServer.java`, `AssignmentService.java`, `ContestDistributionRepository.java`. Kiểm thử bổ sung: `check_contest_assignment.mjs`, luồng Chrome, `ExamModeIntegrationTest`, `MySqlAdminIntegrationTest`.

Kết quả kiểm thử giao đề: 78 kiểm tra tích hợp Java đạt (có Chrome thật); 111 kiểm tra MySQL thật trong instance tạm đạt; 18 script frontend đạt. Không thay đổi kỳ thi hay database thật khi kiểm thử.

## Sử dụng

1. Đóng Server Manager cũ, chạy lại `server/run-server-manager.ps1`. Database learning sẽ tự tạo các bảng bổ sung; không cần chạy SQL thủ công.
2. Cả giáo viên và sinh viên nhấn Ctrl + Shift + R.
3. Giáo viên vào cùng phòng, chọn **Thi & xếp hạng → Tạo kỳ thi**.
4. Đặt tên, giờ bắt đầu, thời lượng. Chọn **Phát đề → Ngẫu nhiên cho từng sinh viên**.
5. Chọn nhóm bài bên dưới, rồi nhập **Số bài mỗi sinh viên**. Ví dụ chọn 10 bài và đặt 3: mỗi người được bốc 3 bài khác nhau trong nhóm 10 bài.
6. Tạo kỳ thi. Khi sinh viên mở kỳ thi lần đầu, server bốc và lưu đề theo tài khoản. Tải lại trang, đổi thiết bị, đăng xuất/đăng nhập hoặc khởi động lại server vẫn giữ bộ đề đã nhận.
7. Giáo viên mở chi tiết kỳ thi để thấy toàn bộ nhóm bài và mục **Đề đã phát ngẫu nhiên**, gồm tên/tài khoản và các bài đã phát. Bấm **Làm mới** để lấy danh sách mới nhất.

**Cùng đề cho cả lớp** vẫn là chế độ mặc định. Kỳ thi cũ giữ nguyên danh sách bài; không tự chuyển thành ngẫu nhiên. Chính sách phát đề được cố định lúc tạo kỳ thi.

## Quy tắc

- Số bài mỗi sinh viên phải từ 1 đến số bài trong nhóm; nhóm tối đa 20 bài theo giới hạn kỳ thi hiện có.
- Một bộ đề không chứa bài trùng. Các sinh viên có thể nhận đề trùng một phần hoặc toàn bộ; đây là bốc độc lập, không bảo đảm tất cả bộ đề đều khác nhau.
- Khi chọn số bài bằng kích thước nhóm, mọi sinh viên nhận đủ nhóm bài, có thể khác thứ tự.
- Đề được phát khi mở kỳ thi lần đầu, kể cả khi kỳ thi chưa tới giờ. Quyền nộp vẫn do thời gian trên server quyết định.
- Sinh viên chỉ nhận nội dung đề của mình từ API kỳ thi. Server xác minh danh tính đăng nhập, không nhận `userId` từ browser để quyết định người được phát đề.
- Nộp bài bằng editor hoặc file đều bị từ chối nếu bài không thuộc bộ đề đã phát. Bật Exam Mode, upload code, hàng đợi chấm và bảng xếp hạng tiếp tục dùng cơ chế hiện có.
- Mỗi người có cùng số bài nhưng độ khó không được tự cân bằng. Giáo viên nên chọn nhóm bài tương đương độ khó/khối lượng để kết quả xếp hạng dễ so sánh. Cách tính điểm hiện có không thay đổi.

## Source và lưu trữ

- `ContestDistributionRepository.java`: ba bảng mới trong SQLite learning hiện có: `p3_contest_distribution` (chính sách), `p3_contest_draws` (người đã nhận đề và tên/tài khoản tại lúc nhận), `p3_contest_student_problems` (bài và thứ tự).
- Dùng `SecureRandom` để trộn nhóm bài. Giao dịch `BEGIN IMMEDIATE` đọc/ghi một lượt bốc nguyên tử, không cho hai request đồng thời tạo hai bộ đề khác nhau.
- `AssignmentRepository.java`: lưu chính sách cùng giao dịch tạo kỳ thi; kỳ thi không có bản ghi chính sách được hiểu là cùng đề cho cả lớp.
- `AssignmentService.java`: kiểm tra cấu hình, trả đề theo danh tính, giới hạn nộp bài và chỉ trả danh sách phân phát cho giáo viên chủ phòng.
- `contests.js`, `events.js`, `state.js`: form cấu hình, số bài trên thẻ kỳ thi, danh sách đề của sinh viên và danh sách phân phát cho giáo viên. Khi đổi kỳ thi sẽ đóng workspace kỳ thi cũ để tránh nộp nhầm ngữ cảnh.

## Kiểm thử

`ExamModeIntegrationTest` bổ sung các trường hợp: quyền tạo, số bài hợp lệ, đủ bài và không trùng trong một bộ đề, hai request đồng thời, mở lại, đề riêng của sinh viên, danh sách giáo viên, từ chối nộp bài ngoài bộ đề, giữ đề qua khởi động lại server và phiên đăng nhập mới.

Luồng Chrome trong `exam_mode_browser.cjs` kiểm tra form giáo viên, sinh viên nhận đúng số bài, tải lại không đổi đề và giáo viên thấy đủ nhóm bài. Test dùng server/database tạm; không thay đổi dữ liệu kỳ thi thật.

Kết quả: Ant build thành công, 112 kiểm tra lớp học và 69 kiểm tra tích hợp Exam Mode/phát đề đạt (gồm luồng Chrome thật). Cả 17 script frontend đạt. Các kiểm tra chấm trong nhóm này xác minh biên nhận và quyền nộp; không thay thế kiểm thử AC/WA thực tế bằng Docker hoặc hai máy LAN vật lý.
