# Tái tạo giao diện Lớp học và Phòng học trực tuyến

Triển khai ngày 10/11/2026, chỉ cập nhật giao diện web theo hai ảnh tham chiếu. Server đang chạy phục vụ các file tĩnh mới tại http://localhost:8080; không cần khởi động lại, không thay đổi database hay cấu hình.

## Các giao diện

| Khu vực | Giao diện mới | Xử lý được giữ nguyên |
|---|---|---|
| Danh sách lớp | Thẻ lớp, biểu tượng tím, tìm kiếm, lọc đang mở/đã đóng, gợi ý sử dụng, phân trang | Dữ liệu lớp/online từ server; 10 lớp/trang; kiểm tra quyền và lớp đóng |
| Xem lớp | Mô tả và menu thao tác trên từng thẻ | Sửa lớp và xóa với xác nhận; cấm xóa lớp có phiên học theo dữ liệu server |
| Sinh viên lớp | Panel thành viên, thêm bằng MSSV, gỡ khỏi lớp | Các action admin-select-class/admin-enroll/admin-remove-member và API cũ |
| Tạo/sửa lớp | Popup cùng phong cách template, tên/mã/mô tả/trạng thái | Tạo mã khi bỏ trống; mã lớp cố định khi sửa; đóng lớp theo luồng cũ |
| Phòng học | Sidebar/topbar chung, tiêu đề, trạng thái, code tối, các thẻ bên phải, thanh thiết bị | Vào/rời phòng, vai trò, phiên học, đồng bộ và media cũ |
| Master | Các tab và editor, lưu/nháp/tải code, xử lý xung đột | Giữ ID editor và sự kiện input; sinh viên chỉ đọc; giảng viên sửa khi LECTURE |
| Làm bài | Lưới sinh viên, nhánh riêng, trạng thái chấm, terminal, lọc và phân trang | START/SELECT_EXERCISE/FORK/FREEZE/RESUME/MERGE/END/NEW_SESSION không đổi |
| Thành viên/chat | Hai card; chat mở từ nút có sẵn, đóng về phần xem trước | Form gửi, mốc đã đọc, badge, nhiều thiết bị và phân quyền cũ |
| Bài tập | Card vào Bài tập & tiến độ/ngân hàng | Mở đúng trang giao bài hiện có; không thêm API giao bài nhanh mới |
| Công cụ/lịch sử | Các vùng mở rộng và popup xem code/nhánh/lịch sử | Dùng action và dữ liệu lịch sử đã có, không thay API/quyền |
| Mic/camera/chia sẻ | Nút cùng template, trạng thái bật/tắt, màn hình chia sẻ và cảnh báo âm thanh | Giữ media IDs, capture, signaling, âm thanh, toàn màn hình và dừng media |

Nút END được chuyển lên phần tiêu đề, giữ tên **Kết thúc bài làm**, action và xác nhận cũ; không đổi thành thao tác kết thúc cuộc gọi. **Rời cuộc họp** cũng giữ nguyên: giảng viên trở về trang chủ nhưng giữ tư cách trong phòng, sinh viên rời phòng theo luồng cũ.

Số kết nối/thành viên và trạng thái lấy từ dữ liệu hiện có. Một tài khoản có thể xuất hiện nhiều lần khi dùng nhiều thiết bị; giao diện ghi rõ “kết nối”, không tự tính số liệu giả. Hoạt động gần nhất của lớp chỉ hiển thị nếu dữ liệu đã có. Chat xem trước không tự đổi mốc đã đọc; chỉ vùng chat đang mở dùng xử lý đánh dấu đã đọc hiện có.

## Rà soát và kiểm thử

- Đối chiếu SHA-256: **80 file server/xử lý lớp/media không đổi**. Không sửa Java, API, schema, quyền hay các handler nghiệp vụ của lớp học/media/chia sẻ màn hình.
- Đối chiếu render trước/sau: toàn bộ button action/data/disabled được giữ trong **30 tổ hợp vai trò, giai đoạn và panel**; test tổng **110 kiểm tra PASS**.
- Kiểm tra frontend: lớp học 45, điều hướng cuộc họp 21, media 29, chat 27, âm thanh chia sẻ 13, danh sách phòng 28, dữ liệu kết nối 10; admin, import graph và cú pháp 66 module đều PASS. Hai kiểm tra markup cũ được cập nhật cho sidebar template, fixture kiểm tra admin được cập nhật theo giao diện thông báo/sinh viên hiện tại.
- Chrome thực tế: **11 nhóm PASS** cho lớp học và phòng học: thẻ/phân trang/tìm/lọc, xem lớp/thành viên, popup tạo/sửa, khóa lớp/xác nhận xóa, vào đúng phòng, lưu Master, gửi chat, mic/camera dùng thiết bị tổng hợp của Chrome, lịch sử/popup, màn hình chia sẻ/Master, rời cuộc họp.
- Hai ngữ cảnh Chrome riêng: **6 nhóm PASS**: sinh viên xin vào/giảng viên duyệt, Master chỉ đọc, chọn starter, fork, lưu nhánh, freeze/resume, xem nhánh/merge, chat hai trình duyệt, END/NEW_SESSION, chuyển trang bài tập và sinh viên rời phòng.
- Hai trang đã kiểm tra ở chiều rộng 1672/1024/768/390. Đã xử lý CSS drawer mobile cũ để cột thành viên/chat nằm dưới code; kiểm tra bounding box xác nhận không đè lên editor.
- ClassDirectoryIntegrationTest: **57 assertions PASS**, chạy với fixture SQLite riêng, gồm lưu dữ liệu sau khởi động lại. Không đưa tài khoản/lớp/bài làm thử vào dữ liệu thật.
- Kiểm tra file phục vụ tại localhost:8080 khớp source mới; health báo SQLite.

Kiểm thử media dùng thiết bị tổng hợp; không tuyên bố đã kiểm thử camera vật lý, phát âm thanh từ xa hay chia sẻ màn hình trên nhiều máy LAN. Mã xử lý những phần này được giữ nguyên; kiểm tra mock âm thanh và action màn hình đã đạt.

## File và kết quả

- `web-client/static/classroom-template.css`: theme hai trang, responsive và popup.
- `web-client/static/features/admin/class-directory.js`: trình bày lớp dạng thẻ/panel thành viên.
- `web-client/static/features/admin/admin.js`: thêm trạng thái lọc thuần giao diện; giữ handler nghiệp vụ.
- `web-client/static/features/classroom/meeting-view.js`: bố cục phòng học, giữ editor/media/action.
- `web-client/static/ui/render.js`: dùng shell template cho phòng học, tránh tạo hai audio bank.
- `server/build-classroom-template/browser-results.json`, `lifecycle-results.json`: kết quả Chrome.
- Ảnh: `classes-desktop.png`, `classes-mobile.png`, `classroom-desktop.png`, `classroom-mobile.png`, `classroom-student-desktop.png`, `classroom-student-mobile.png`, các popup nằm trong `server/build-classroom-template/`.
- `web-client/tests/check_classroom_template.mjs`: đối chiếu handler/render và hash với bản trước đổi trong cùng thư mục kết quả.
- Kiểm thử trình duyệt: `server/test/classroom-template-browser.cjs`, `classroom-template-lifecycle.cjs`, dùng fixture riêng từ `ClassDirectoryIntegrationTest --browser`.
