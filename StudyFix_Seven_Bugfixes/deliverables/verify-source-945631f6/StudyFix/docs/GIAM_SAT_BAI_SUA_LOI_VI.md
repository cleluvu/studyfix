# Chặn dán và giám sát bài sửa lỗi

## Cách sử dụng

1. Đóng Server Manager cũ và chạy lại `server/run-server-manager.ps1` để build backend mới và khởi tạo bảng dữ liệu bổ sung.
2. Cả giáo viên và sinh viên nhấn Ctrl + Shift + R trên trình duyệt.
3. Giáo viên vào phòng, chọn code lỗi và nhấn **Bắt đầu làm**.
4. Trong cuộc họp, giáo viên mở tab **Giám sát gian lận**, nhấn **Bật Exam Mode**. Tab **Theo dõi bài làm** hiển thị lưới code và kết quả. Hai tab riêng, chuyển tab không tắt giám sát hoặc xóa số liệu.
5. Sinh viên mở **Nhánh của tôi**. Khi hiện thông báo chế độ kiểm tra, Ctrl + V, Shift + Insert, paste và thả nội dung vào ô code sẽ bị chặn. Gõ code, sửa trực tiếp và Undo/Redo vẫn hoạt động.
6. Sinh viên chuyển tab hoặc cửa sổ rồi quay lại. Giáo viên xem số lần rời trang, dán bị chặn và lịch sử tín hiệu trong cùng khối giám sát.
7. Giáo viên tắt Exam Mode để cho phép dán lại. Khi dừng sửa/chọn lời giải hoặc kết thúc bài làm, server không nhận tín hiệu của giai đoạn làm bài nữa.

Mỗi vòng làm bài có cấu hình và số liệu riêng; vòng mới mặc định tắt. Tiếp tục sửa trong cùng vòng giữ cấu hình. Dữ liệu kỳ thi và bài sửa lỗi được tách riêng.

## Cách hoạt động và file liên quan

- `StudyFixGateway.java`: đọc vòng và giai đoạn thật từ bảng `classroom_sessions`, dưới khóa phòng và sau kiểm tra thành viên. Browser không quyết định vòng hoặc giai đoạn được phép báo sự kiện.
- `AssignmentService.java`: tái sử dụng API `EXAM_*`, kiểm tra chủ phòng và giới hạn đúng vòng hiện tại. Bản chụp realtime hiện có chứa cả giám sát nhánh; Student không được nhận monitor của Teacher.
- `ExamMonitoringRepository.java`: tái sử dụng xử lý phiên, hash phiên đăng nhập, chống trùng, giới hạn tần suất, bộ đếm và lịch sử. Tạo nhóm bảng `p3_branch_exam_*` trong database learning hiện có; không tạo kỳ thi giả, không thay đổi bảng chấm bài. Cột nội bộ `contest_id` của nhóm bảng này chứa khóa phạm vi `classroom:<roomId>:<round>`.
- `exam-monitoring.js`: cùng bộ xử lý sự kiện dùng cho `contest-code` hoặc `classroom-branch` theo ngữ cảnh hiện tại; không chặn chat/luyện tập thông thường. Có hàng đợi metadata khi mạng lỗi tạm thời.
- `ui/exam-ui.js`: đăng ký bộ render để không tạo vòng lặp import giữa màn hình cuộc họp, API và polling.
- `meeting-view.js`: hiện điều khiển và bảng giám sát cho chủ phòng, cảnh báo cho Student.
- `polling.js`: lấy chính sách khi mở màn hình lớp; tiếp tục dùng polling/WebSocket sẵn có.

## Kiểm thử

- `ExamModeIntegrationTest.java`: thêm tình huống cấu hình riêng theo vòng, quyền Student/Teacher khác lớp, session của người khác, đếm trùng, lịch sử, bản chụp realtime, khóa sửa và chuyển vòng.
- `check_exam_monitoring.mjs`: thêm kiểm tra ô nhánh, không ảnh hưởng ô luyện tập, Undo, sự kiện và tắt chế độ.
- `branch_monitor_browser.cjs`: chạy Chrome trên server test thật, kiểm tra dán thường, bật/tắt từ Teacher, chặn clipboard, gõ/Undo và bộ đếm realtime.

## Giới hạn

Kết quả kiểm chứng: Ant `exam-mode-test` build thành công; 112 kiểm tra lớp học và 59 kiểm tra Exam Mode mở rộng đạt, gồm hai luồng Chrome thật cho kỳ thi và bài sửa lỗi. Toàn bộ 16 script kiểm tra frontend đạt. Chrome kiểm tra Ctrl + V thật; chuyển trạng thái hidden/focus được phát có kiểm soát trong test. Chưa xác nhận thao tác menu chuột phải của Windows hoặc chạy trên hai máy LAN vật lý.

Đây là tín hiệu để giáo viên xem xét, không tự kết luận gian lận và không trừ điểm. Không đọc/lưu nội dung clipboard. Mất focus không chứng minh người học đã mở ứng dụng gì. Người dùng sửa JavaScript có thể bỏ qua phần chặn ở trình duyệt; server vẫn xác thực quyền và phiên của sự kiện nhận được. Khi mất mạng, sự kiện chờ đồng bộ trong giới hạn hàng đợi; thời gian vắng đo theo lúc server nhận có thể khác thời gian thực.
