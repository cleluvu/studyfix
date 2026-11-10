# Trang quản lý sinh viên — triển khai ngày 10/11/2026

Đã thay trang Sinh viên bằng giao diện theo ảnh: tiêu đề và ba nút thao tác, bốn thẻ số liệu thật, bộ lọc, bảng danh sách, thao tác hàng loạt và phân trang 10 dòng. Không hiển thị khối “Chức năng & nghiệp vụ chính”.

## Sử dụng

- **Thêm sinh viên** mở popup riêng: MSSV, họ tên, tên đăng nhập, mật khẩu, email tùy chọn, lớp tùy chọn, trạng thái. Tạo tài khoản sinh viên và gán lớp trong cùng giao dịch SQLite. Mật khẩu tối thiểu 12 ký tự; có nút hiện/ẩn.
- **Chỉnh sửa** mở popup riêng: sửa họ tên, tên đăng nhập, email, trạng thái và lớp. MSSV được giữ cố định. Khi đổi thông tin, các phiên đăng nhập cũ bị thu hồi.
- **Chi tiết** mở phần thông tin bên dưới danh sách, hiển thị tài khoản và các lớp trong phạm vi giảng viên quản lý.
- Tìm kiếm theo MSSV, họ tên, tên đăng nhập, không phân biệt dấu/chữ hoa. Lọc theo lớp, trạng thái; bộ lọc nâng cao theo ngày tạo hoặc chưa phân lớp. Có sắp xếp MSSV/trạng thái.
- Chọn nhiều sinh viên để thêm vào lớp, chuyển từ một lớp nguồn cụ thể sang lớp đích, khóa, kích hoạt hoặc vô hiệu hóa. Chuyển lớp giữ nguyên các lớp khác; lỗi một người thì cả lô không thay đổi.
- **Xóa** sử dụng vô hiệu hóa tài khoản, có xác nhận, giữ lịch sử học tập và vẫn thấy trong bộ lọc “Đã vô hiệu hóa”. Không có thao tác lưu trữ.
- Trạng thái ACTIVE/LOCKED/DISABLED được lưu trên server. Khóa hoặc vô hiệu hóa chặn đăng nhập và thu hồi phiên hiện tại; kích hoạt cho phép đăng nhập lại.
- Tài khoản dùng chung với giảng viên khác được xem và quản lý thành viên trong lớp của mình, nhưng không được đổi thông tin hoặc khóa toàn bộ tài khoản. Server thực thi quyền này độc lập với nút trên giao diện.
- Các yêu cầu tham gia lớp vẫn có toast duyệt nhanh; trang thông báo có nút Chấp nhận/Từ chối trực tiếp.

## Nhập/xuất

Nhập CSV UTF-8 (128 KiB) hoặc Excel `.xlsx` thật (1 MiB), tối đa 100 sinh viên/lần. Có tải CSV mẫu và xem trước; mật khẩu được che trong bảng xem trước. Các cột: MSSV, Họ và tên, Tên đăng nhập, Mật khẩu, Email, Lớp, Trạng thái. Email/lớp/trạng thái tùy chọn. Nếu nhập định dạng cũ chỉ có username/displayName/password thì MSSV lấy theo username. Dòng có mã lớp được gán vào lớp đó; dòng chưa có lớp dùng lựa chọn lớp mặc định.

File lỗi tài khoản trùng, lớp ngoài quyền quản lý hoặc dữ liệu không hợp lệ sẽ không tạo một phần danh sách. Mật khẩu chỉ được lưu dưới dạng băm trong cơ sở dữ liệu tài khoản. Excel có công thức phải chuyển thành giá trị trước khi nhập; không hỗ trợ `.xls`, file mã hóa hoặc macro.

Xuất CSV/Excel theo bộ lọc hiện tại; nếu đã chọn sinh viên thì chỉ xuất phần đã chọn thỏa bộ lọc. File xuất không chứa mật khẩu hoặc mã băm. CSV có UTF-8 BOM, quote đúng và bảo vệ công thức; XLSX ghi ô chuỗi để giữ MSSV có số 0 đầu.

## API và dữ liệu

- POST `/api/admin`: STUDENT_CREATE, STUDENT_IMPORT, STUDENT_UPDATE, STUDENT_DETAIL, STUDENT_ASSIGN, STUDENT_STATUS, STUDENT_EXPORT.
- POST `/api/admin/students/import-preview`: đọc file XLSX, chỉ giảng viên được gọi, giới hạn kích thước/giải nén, cấm XML external entity, liên kết ngoài và công thức.
- SQLite migration bổ sung `users.email`, `users.account_status`; đồng bộ active với trạng thái đăng nhập. Không dùng MySQL.
- Đã sao lưu trước cập nhật tại `server/backups/before-student-directory-20261110-041920`.
- Đã so sánh tài khoản, thông tin đăng nhập, lớp và thành viên trước/sau: không thay đổi dữ liệu cũ.

## Kiểm chứng

- StudentDirectoryIntegrationTest: **75 assertions PASS**, HTTP/SQLite thật, dữ liệu thử tách riêng. Bao gồm quyền truy cập, tài khoản dùng chung, import/chuyển lớp nguyên tử, khóa/mở tài khoản, Excel, xuất theo bộ lọc và persistence.
- ClassroomIntegrationTest: **112 checks PASS**; SqliteManagementIntegrationTest PASS.
- Chrome: **13 nhóm kiểm tra PASS**, tạo/sửa trên web, tìm kiếm/lọc/phân trang, gán/chuyển lớp, xác nhận vô hiệu hóa, nhập CSV và XLSX do XlsxWriter tạo, tải CSV/XLSX và không có lỗi JavaScript chưa bắt.
- Kiểm tra thêm menu và popup trên mobile; 4 chiều rộng 1672/1024/768/390 không tràn trang. Bảng trên màn hình nhỏ cuộn ngang trong khung.
- Đọc độc lập XML trong file xuất: đúng dòng được lọc, tiếng Việt, toàn bộ ô chuỗi, không công thức/mật khẩu. Bốn file Excel chứa formula, XXE, external relationship, zip bomb đều bị từ chối 400.
- Kiểm tra cú pháp **66 module JavaScript PASS**; bytecode các lớp triển khai khớp bản kiểm thử.
- Chạy lại: `ant -Dbuild.dir=build-student-directory -Ddist.dir=dist-student-directory student-directory-test`; Chrome: đặt PLAYWRIGHT_PACKAGE rồi chạy `node test/student-directory-browser.cjs` với fixture mới từ `StudentDirectoryIntegrationTest --browser`.
- Ảnh kiểm tra: `server/build-student-directory/student-directory-desktop.png`, `student-directory-mobile.png`, `student-create-popup.png`, `student-edit-popup.png`.

## Trạng thái chạy

Server đang chạy tại http://localhost:8080; health báo managementDatabase=sqlite. Launcher PID nằm trong `server/logs/student-directory-server.pid`; log tại `student-directory-server.out.log` và `student-directory-server.err.log`.
