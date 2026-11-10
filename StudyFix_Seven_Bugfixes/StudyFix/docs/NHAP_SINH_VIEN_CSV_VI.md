# Nhập sinh viên hàng loạt từ file CSV

## Cách dùng

1. Khởi động lại Server Manager để dùng backend mới. Trình duyệt nhấn Ctrl + Shift + R.
2. Giáo viên mở **Sinh viên → Nhập sinh viên từ file CSV**.
3. Bấm **Tải file mẫu CSV**. File có ba cột: **Tài khoản, Họ và tên, Mật khẩu**. Không cần cột mã sinh viên.
4. Điền danh sách. Nếu dùng Excel, lưu bằng **CSV UTF-8** rồi chọn file trong hộp thoại. Hỗ trợ phân tách bằng dấu phẩy hoặc dấu chấm phẩy; ô chứa dấu phẩy được đặt trong dấu ngoặc kép theo chuẩn CSV.
5. Xem trước tài khoản và tên. Mật khẩu được che. Nếu file không hợp lệ, nút tạo bị khóa và hiển thị dòng dữ liệu lỗi.
6. Bấm **Tạo danh sách sinh viên**. Sau khi lưu, danh sách quản lý được làm mới. Đây là tạo tài khoản; không tự thêm sinh viên vào lớp hoặc tự phê duyệt yêu cầu vào lớp.

Giới hạn: 1–100 sinh viên/lần, file tối đa 128 KiB. Tài khoản 1–64 ký tự chữ/số/`._-`, họ tên bắt buộc tối đa 200 ký tự, mật khẩu 12–256 ký tự. Header tiếng Anh `username,displayName,password` cũng được hỗ trợ. Không nhận trực tiếp `.xlsx`; xuất CSV UTF-8 trước khi tải lên.

## Lưu và xử lý lỗi

- Server xác thực giáo viên và chỉ tạo role STUDENT; không tin role từ file/browser.
- Mã định danh tương thích hiện có lấy từ tên tài khoản, như form tạo đơn lẻ.
- Tên tài khoản trùng trong file hoặc xung đột với tài khoản/mã có sẵn sẽ bị từ chối; không ghi đè, không đổi mật khẩu tài khoản cũ.
- Toàn bộ batch tạo trong một transaction MySQL. Nếu một dòng bị lỗi khi lưu, các dòng trước trong batch cũng rollback. Không xuất hiện danh sách mới được tạo dở trong MySQL.
- Dùng lại kiểm tra và hash PBKDF2 của chức năng tạo tài khoản đơn lẻ. Không trả mật khẩu hay hash trong response; chỉ trả số tài khoản đã tạo.
- Browser giữ dữ liệu tạm trong hộp thoại, không lưu danh sách/mật khẩu vào localStorage hoặc sessionStorage. File gốc trên máy chứa mật khẩu rõ nên cần bảo quản và thay các mật khẩu minh họa trong file mẫu.
- Không có upload file lên ổ đĩa server: browser đọc CSV, xem trước, rồi gửi danh sách JSON tới API quản trị hiện có.

## File và kiểm thử

- `features/admin/student-import.js`: parser CSV, tải mẫu, chọn file, xem trước, kiểm tra và gửi batch.
- `features/admin/admin.js`, `admin-view.js`: tích hợp nút và làm mới danh sách.
- `AdminManagement.java`: action `IMPORT_STUDENTS` chỉ dành cho giáo viên.
- `MySqlManagement.java`: tái sử dụng validation/hash, kiểm tra trùng và giao dịch batch; không thêm bảng hay thay kiến trúc database.
- `check_student_import.mjs`: CSV UTF-8, BOM, dấu phân cách, ngoặc kép, xuống dòng, header, trùng và giới hạn.
- `MySqlAdminIntegrationTest`: tạo nhiều tài khoản, đăng nhập, quyền Student, rollback khi trùng, cấm nâng role và xác minh hash mật khẩu.
- `teacher_dashboard_browser.cjs`: tải mẫu, chặn file lỗi, che mật khẩu trong preview và tạo batch trên Chrome/MySQL tạm.
