# StudyFix — bản client cải tiến màn hình chia sẻ

## Thay đổi giao diện chia sẻ màn hình
- Bỏ nút `Xem trước ảnh local` khỏi giao diện dùng thường ngày. Đây là tiện ích debug Robot capture, không phải luồng chia sẻ trực tiếp.
- Dùng vùng trình chiếu lớn, nền tối, giữ đúng tỷ lệ ảnh và tự căn theo kích thước cửa sổ.
- Khi đang phát, client phát chỉ hiển thị trạng thái chia sẻ; ảnh stream chỉ hiển thị ở client đang xem, không tự phát lại hình capture trong vùng xem local.
- Khi viewer nhận frame đầu tiên, trạng thái hiển thị LIVE kèm kích thước ảnh.
- Có trạng thái rỗng rõ ràng khi chưa có luồng, đang kết nối hoặc đã dừng.

## Chạy trên Windows
1. Cài/đặt JDK 26 hoặc JDK 17+ cho client.
2. Mở PowerShell trong `studyfix-client`.
3. Chạy `Set-ExecutionPolicy -Scope Process Bypass` nếu cần.
4. Chạy `./self-test.ps1`, sau đó `./run.ps1`.

## Lưu ý kiểm thử chia sẻ màn hình
Client capture toàn bộ màn hình chính bằng `Robot`. Khi client phát và viewer chạy trên cùng một desktop vật lý, cửa sổ viewer cũng có thể nằm trong ảnh chụp, dẫn đến hiệu ứng gương vô hạn. Để kiểm thử stream thật, tốt nhất mở viewer trên máy thứ hai trong LAN. Muốn tránh điều này trên cùng một máy, cần triển khai chọn nguồn chia sẻ (màn hình/cửa sổ cụ thể) hoặc cơ chế capture loại trừ cửa sổ viewer; đây không phải lỗi relay.
