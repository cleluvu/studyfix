# Thêm bài vào ngân hàng

Đăng nhập giáo viên → **Luyện code** → **Thêm bài vào ngân hàng**. Không cần vào phòng học.

Nhập mã bài duy nhất, tên, chủ đề, độ khó, loại bài, đề bài, định dạng vào/ra. Có thể thêm ràng buộc, gợi ý và code mẫu Java (`Main`). Với bài sửa lỗi cộng tác, nhập thêm code có lỗi ban đầu.

Form thay đổi theo loại bài: **Lập trình từ đầu** hiển thị khung code tùy chọn và testcase chấm lời giải; **Sửa lỗi cộng tác** hiển thị code lỗi bắt buộc, yêu cầu sửa lỗi, gợi ý tìm lỗi và testcase kiểm tra sau khi sửa. Khi chuyển loại, form giữ nội dung đã nhập, nhưng chỉ gửi phần code của loại đang chọn. Đề bài, vào/ra, ràng buộc và testcase vẫn dùng chung.

Thêm các testcase gồm input, output mong đợi, trọng số nguyên 1–100 và tùy chọn test ẩn. Cần 1–50 testcase, trong đó ít nhất một test công khai. Output được lưu đúng nội dung nhập; giáo viên cần tự kiểm tra tính đúng đắn của đề và đáp án. Việc lưu đề không tự chạy kiểm chứng lời giải bằng Docker.

Sau khi lưu, danh sách tự tìm theo mã bài mới. Học sinh có thể mở và nộp bài qua luồng luyện/chấm hiện có. Test ẩn không được trả trong nội dung đề công khai. Bài và testcase lưu cùng giao dịch trong SQLite; không sửa file seed, không mất khi khởi động lại. Mã trùng bị từ chối, không ghi đè bài cũ.

Luồng: `practice.js` → `catalog-create.js` → `POST /api/assignments` (`CATALOG_CREATE`) → `WebGatewayServer.assignmentRequest()` → `AssignmentService.createCatalogExercise()` → `AssignmentRepository.createExercise()` / `persistExercises()`.

Quyền giáo viên được kiểm tra tại server, không chỉ ẩn nút trên giao diện. API tạo đề không phụ thuộc quyền chủ phòng vì đây là ngân hàng chung toàn hệ thống.

Sau cập nhật cần khởi động lại server bằng launcher hiện có và tải lại trình duyệt (Ctrl+Shift+R).
