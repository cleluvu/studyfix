# Nộp bài thi từ file

Trong màn hình làm một bài thi, sinh viên nhấn **Nộp file code**, chọn một file `.java` hoặc `.txt` chứa mã nguồn Java (UTF-8). Hệ thống nạp nội dung vào trình soạn thảo và gửi chấm ngay, không cần nhấn thêm Nộp bài thi. File tối đa 32 KiB, không rỗng và không chứa byte NUL. Chương trình vẫn phải tuân thủ yêu cầu `Main.java` của bộ chấm.

Kỳ thi phải đang diễn ra và sinh viên phải còn thuộc phòng. Nút chọn file bị khóa ngoài thời gian thi; server vẫn kiểm tra lại thời hạn, tài khoản và bài thi khi nhận yêu cầu. Nếu đọc file xong mà đã đổi tài khoản/phòng/bài, thao tác bị hủy để tránh nộp nhầm. Nếu server từ chối hoặc mất mạng, nội dung đã nạp vẫn hiện trong editor để sinh viên có thể kiểm tra và thử nộp lại.

Nộp file là cách nộp được cho phép cả khi Exam Mode bật. Nó không phát sinh sự kiện dán bị chặn. Giới hạn dán clipboard và theo dõi chuyển tab vẫn hoạt động. Do đó, bật chặn paste không đồng nghĩa cấm đưa mã nguồn bên ngoài vào bằng file.

Không có endpoint upload hay thư mục chứa file mới. Browser đọc văn bản rồi gửi `sourceCode` đến API `CONTEST_SUBMIT` hiện có; server lưu bản nộp và đưa vào hàng đợi chấm như cách nhập code thủ công.

Các file thay đổi: `features/contests/contests.js`, `core/events.js`. Kiểm thử: `tests/check_contest_file.mjs` và luồng chọn file trên Chrome trong `tests/exam_mode_browser.cjs`.

Sau khi cập nhật source, nhấn Ctrl + Shift + R trên trình duyệt. Thử file hợp lệ trong kỳ thi đang mở để nhận biên nhận/kết quả; thử file rỗng, sai đuôi hoặc lớn hơn giới hạn để thấy lỗi. Không thay đổi thời gian kỳ thi hoặc dữ liệu thật khi kiểm thử.
