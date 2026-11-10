# Tổng quan giảng viên

## Phạm vi

Thay trang `admin` bằng dashboard theo ảnh được duyệt, bổ sung ba trang Thống kê, Thông báo & hoạt động, Cài đặt cá nhân. Giữ các luồng quản lý lớp, sinh viên, phòng học, tạo bài Java và tổ chức thi hiện có.

Giao diện gồm banner chào mừng, năm thẻ số liệu, biểu đồ lượt nộp/AC, biểu đồ kết quả chấm, thao tác nhanh, lớp gần đây, hoạt động gần đây, kỳ thi sắp tới và tiến độ Week. Sidebar và các khối thích ứng với desktop, tablet và điện thoại.

## Dữ liệu và cách tính

- Chỉ dùng SQLite. Dữ liệu quản lý lớp và dữ liệu học tập được tổng hợp qua service trong cùng server; không ghép database từ phía trình duyệt.
- Các lớp và sinh viên được xác định theo danh tính giảng viên đã xác thực. Không nhận `ownerId` từ client. Truy cập lớp của giảng viên khác bị từ chối.
- Sinh viên được đếm duy nhất qua các lớp đang quản lý.
- Bài tập đã giao đếm lượt giao, không đồng nhất với số câu hỏi trong ngân hàng.
- Đề thi đếm bộ đề của tác giả còn phiên bản chưa lưu trữ, không cộng từng revision.
- Lượt luyện tập loại `is_run=1`. Lượt kiểm định và thử bài của giáo viên không thuộc nguồn thống kê. Biểu đồ luyện code gồm luyện tập và bài được giao; biểu đồ kết quả có cả bài thi trong phạm vi quản lý.
- Tỷ lệ kết quả chỉ tính bản ghi đã chấm xong. AC/WA/TLE/CE/RE được hiển thị riêng; kết quả khác và đang chấm không bị gán thành RE.
- Thống kê bài nộp có phân trang 20 dòng, lọc theo 7/30/90 ngày, lớp, Week và verdict. Thời gian nhóm ngày và hiển thị theo UTC+7.
- Tiến độ Week dựa trên danh sách nhận bài đã chụp khi giao. Cặp lớp–sinh viên–bài được đếm một lần kể cả giao lại hoặc nộp nhiều lần. Các phiên bản có cùng root câu hỏi được đối chiếu cùng nhóm.
- Hoạt động gần đây được lấy riêng, không phụ thuộc trang hiện tại của bảng bài nộp.
- Không trả source code, lời giải hoặc test ẩn trong API dashboard.

Không chèn các số liệu minh họa trong ảnh vào database thật. Chưa có lịch sử chụp số liệu theo tháng đầy đủ nên giao diện không hiển thị tỷ lệ tăng trưởng suy đoán.

## API và lưu trữ

- `GET /api/teacher/dashboard`: dữ liệu tổng quan; hỗ trợ `days`, `classId`, `weekId`, `verdict`, `page`.
- `POST /api/teacher/dashboard`, action `SETTINGS`: khoảng ngày mặc định, bảng gọn, chỉ báo thông báo.
- `POST /api/teacher/dashboard`, action `READ`: mốc đánh dấu các thông báo đã đọc, không cho ghi quá thời điểm server.
- Bảng mới trong database quản lý: `teacher_dashboard_preferences`. Migration bổ sung, chạy lại được, không sửa các bảng nội dung học tập.

Thông báo được hình thành từ hoạt động nội bộ và lưu mốc đọc theo tài khoản. Không gửi email hoặc thông báo ra dịch vụ bên ngoài.

## Liên kết chức năng

- Tạo lớp → form tạo lớp hiện có, cập nhật dashboard khi lưu thành công.
- Mở phòng → chọn lớp → vào phòng thật.
- Giao bài → chọn lớp → catalog và form giao bài hiện có.
- Tạo kỳ thi → wizard tổ chức thi.
- Thẻ số liệu, các liên kết xem tất cả → trang quản lý tương ứng.
- Verdict và Week → trang thống kê đã lọc.
- Menu từng lớp → chi tiết, sửa tên, kết nối và điểm danh.
- Thông báo → nội dung liên quan; có đánh dấu đã đọc và truy cập yêu cầu tham gia lớp.
- Cài đặt cá nhân → lưu SQLite theo tài khoản; không bị polling thay mất form đang nhập.

## Xác minh

Kết quả: 38 kiểm tra dashboard PASS, 112 kiểm tra lớp học PASS, bộ kiểm tra quản lý SQLite PASS. Trình duyệt Chrome đã qua 12 nhóm kiểm tra tự động, cùng kiểm tra menu sửa lớp và tài khoản giảng viên không có dữ liệu. Không có lỗi JavaScript chưa được xử lý. Các chiều rộng 1672/1024/768/390 px không tràn ngang toàn trang. Cả ba database thật qua `PRAGMA quick_check`.

- `TeacherDashboardIntegrationTest`: HTTP và SQLite thật trên database tạm, kiểm tra danh tính, phạm vi lớp, số liệu, loại chạy thử, phân trang, lọc, tiến độ giao lại, không lộ code, cài đặt và mốc đọc.
- `ClassroomIntegrationTest`: hồi quy luồng lớp học, quyền riêng tư, bài được giao và hoạt động phòng.
- `SqliteManagementIntegrationTest`: đăng nhập, quyền, yêu cầu tham gia lớp, điểm danh và lưu trữ qua lần khởi động.
- `test/teacher-dashboard-browser.cjs`: kiểm tra trình duyệt trên server fixture riêng; kết quả và ảnh được lưu dưới `server/build-teacher-dashboard/`.

Ảnh kiểm tra: `dashboard-desktop.png`, `dashboard-mobile.png`. Các số liệu trong ảnh kiểm tra thuộc database fixture, không phải dữ liệu tài khoản thật. Kết quả tự động: `browser-results.json`. Log backend: `final-tests.log`, `management-test.log`.

Chạy kiểm tra backend từ thư mục `server`:

```powershell
ant '-Dbuild.dir=build-teacher-dashboard' '-Ddist.dir=dist-teacher-dashboard' teacher-dashboard-test
```

Để kiểm tra trình duyệt, chạy test Java với `--browser`, cấu hình `PLAYWRIGHT_PACKAGE` tới package Playwright cục bộ, rồi chạy `node test/teacher-dashboard-browser.cjs`. Các tài khoản và dữ liệu test chỉ tồn tại trong database fixture.

## Cập nhật server

Server thật được khởi động lại qua `run-with-assignments.ps1`; PID nằm ở `server/logs/teacher-dashboard-server.pid`. Log: `teacher-dashboard-server.out.log` và `teacher-dashboard-server.err.log`.

Bản sao lưu trước migration: `server/backups/before-teacher-dashboard-20261110-011536/`, gồm ba database và bảng đếm đối chiếu. Sau cập nhật, đã kiểm tra số tài khoản, lớp, thành viên, bài tập, bài giao, bài nộp, kỳ thi và bản nháp không đổi. Bytecode dashboard và Docker sandbox trong JAR đang phục vụ trùng bản build đã kiểm thử.
