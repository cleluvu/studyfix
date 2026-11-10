# Trang Lớp học

## Giao diện và phạm vi

Trang `admin-classes` dùng giao diện danh sách theo ảnh: tiêu đề, nút Tạo lớp học, một ô tìm kiếm, bảng và phân trang. Không có dashboard, biểu đồ, lịch học hoặc bảng quản trị thành viên trên trang này.

Bảng có đúng năm cột: **Mã lớp – Tên lớp – Sinh viên – Trạng thái – Thao tác**. Số sinh viên online hiển thị dưới sĩ số trong cùng cột Sinh viên. Trạng thái chỉ có Đang mở / Đã đóng, lấy từ server. Lớp đã xóa không xuất hiện trong danh sách.

- Tìm theo mã hoặc tên, không phân biệt hoa/thường và dấu tiếng Việt; tìm kiếm đưa về trang đầu.
- Phân trang 10 lớp/trang, hiển thị khoảng dòng và tổng số lớp phù hợp.
- Tạo lớp với tên bắt buộc, mã tham gia tùy chọn, mô tả tùy chọn. Để trống mã thì server tự cấp mã `LOP-…`.
- Chỉnh sửa tên, mô tả và trạng thái. Mã tham gia giữ cố định để bảo toàn liên kết phòng và lịch sử.
- Vào lớp mở phòng realtime tương ứng, nơi đã có chat, chia sẻ màn hình và giao bài. Lớp đã đóng không thể vào từ danh sách. Chặn gửi lặp khi đang vào lớp.
- Xóa yêu cầu xác nhận có tên/mã lớp. Bản ghi phòng ngừng hoạt động, lịch sử học tập không bị xóa theo.
- Giao diện dùng được ở 1672, 1024, 768 và 390 px. Bảng cuộn ngang bên trong trên màn hình nhỏ, không làm tràn ngang toàn trang.

## Backend và SQLite

Migration bổ sung cột `classes.description TEXT NOT NULL DEFAULT ''`. Kiểm tra cột trước khi thêm để chạy lại an toàn. Không thay thế database và không dùng MySQL.

API `/api/admin` tiếp tục dùng danh tính đã xác thực:

- `CREATE_CLASS`: hỗ trợ `name`, `roomCode`, `description`; kiểm tra tên 1–200 ký tự, mô tả tối đa 2000 ký tự và mã phòng hợp lệ/không trùng. Các caller cũ chỉ gửi mã vẫn hoạt động.
- `UPDATE_CLASS`: hỗ trợ mô tả và trạng thái; caller cũ bỏ qua mô tả sẽ giữ giá trị đã lưu. Chỉ chủ lớp được cập nhật.
- `DELETE_CLASS`: giữ kiểm tra chủ lớp và dùng chung quy tắc xóa tại `Server.deleteRoom`.
- Dữ liệu lớp bổ sung `description`, `onlineStudents`, `deleteBlocked`. Online đếm sinh viên đang thật sự trong phòng, loại trùng nhiều kết nối cùng tài khoản.

Server chặn xóa khi có phiên học hoạt động hoặc lượt sửa/nháp code đang hoạt động. Quy tắc áp dụng cả API quản lý lớp và API xóa phòng trực tiếp. Kiểm tra trạng thái và xóa cùng khóa phòng, tránh bỏ qua kiểm tra bằng request cũ. Giảng viên có thể kết thúc phiên hoặc đóng lớp để giữ lại dữ liệu trước khi xóa. Đóng lớp ngắt các kết nối và chặn tham gia; mở lại qua form chỉnh sửa.

Quản lý sinh viên, yêu cầu tham gia lớp và nghiệp vụ dạy học tiếp tục nằm ở các màn hình riêng. Liên kết xem yêu cầu từ thông báo dẫn tới trang Sinh viên, nơi còn các thao tác duyệt hiện có.

## Xác minh

- `ClassDirectoryIntegrationTest`: **57 kiểm tra PASS** với HTTP và SQLite thật, gồm metadata, mã tự sinh, mã trùng, giới hạn đầu vào, phân quyền, online, duyệt tham gia, đóng/mở lớp, chặn xóa phiên chạy qua hai API, ngừng mã phòng và lưu qua lần mở database tiếp theo.
- `ClassroomIntegrationTest`: **112 kiểm tra PASS**.
- `SqliteManagementIntegrationTest`: **PASS**, gồm đăng nhập, quyền, duyệt tham gia, điểm danh và lưu qua lần khởi động.
- `test/class-directory-browser.cjs`: **11 nhóm kiểm tra PASS** trên Chrome, gồm tìm kiếm, phân trang, tạo/sửa, đóng/mở, hủy/xác nhận xóa, vào phòng realtime và bốn kích thước màn hình. Không có lỗi JavaScript chưa được xử lý.

Ảnh và kết quả kiểm tra ở `server/build-class-directory/`: `class-directory-desktop.png`, `class-directory-mobile.png`, `browser-results.json`, `class-test.log`, `regression.log`, `management-regression.log`. Dữ liệu trong ảnh thuộc database kiểm thử riêng.

Chạy từ thư mục `server`:

```powershell
ant '-Dbuild.dir=build-class-directory' '-Ddist.dir=dist-class-directory' class-directory-test
```

Để kiểm tra trình duyệt, chạy `ClassDirectoryIntegrationTest --browser` trước, cấu hình `PLAYWRIGHT_PACKAGE` tới package Playwright cục bộ, rồi chạy `node test/class-directory-browser.cjs`.

## Cập nhật server

Bản sao lưu ba database trước cập nhật: `server/backups/before-class-directory-20261110-035238/`. Migration không sửa nội dung lớp hoặc bài học hiện có.

Server chạy qua `run-with-assignments.ps1`. PID: `server/logs/class-directory-server.pid`. Log: `class-directory-server.out.log`, `class-directory-server.err.log`.
