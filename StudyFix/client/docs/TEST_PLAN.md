# Test plan — client mới và tích hợp LAN

## A. Build / smoke test client

PowerShell tại thư mục `studyfix-client`:

```powershell
.\build.ps1
.\self-test.ps1
```

Nếu Maven có sẵn:

```powershell
mvn clean test package
```

Test tự động phụ thuộc bằng 0 trong `src/test/java/vn/studyfix/client/ClientSelfTest.java` kiểm tra frame UTF-8, ghép history nhiều phần, parse login/state/code/chat, lệnh `/update` nhiều dòng, và binary frame cho JPEG.

## B. Server baseline hiện tại

1. Chạy server của người 1 theo README trong project server.
2. Mở 2 client: đăng nhập `teacher` và `student1`.
3. Teacher `/create` qua UI; ghi room id; student join room id.
4. Cả hai ở tab Members bấm Làm mới, kiểm tra tên/session id.
5. Gửi chat hai chiều; xác nhận chỉ thành viên cùng room nhận.
6. Mở tab Code: tải code và kiểm tra version 0; teacher và student nhìn cùng code.
7. Student bấm Xin quyền sửa khi không có editor; kiểm tra state/editor/token; chọn dòng đúng (ví dụ `4`), thay bằng `i < a.length`, xem nháp và finish. Kiểm tra cả hai client nhận version mới/code mới.
8. Mở 2 student nữa, nhấn xin quyền nhanh; kiểm tra queue FIFO, trả quyền/hết lượt/cancel và disconnect của editor hiện tại.
9. Dùng token/version cũ để thử command nếu test console; server phải từ chối stale token/version.
10. Xem lịch sử `/history`, mở version cũ; code hiện tại không bị đổi.
11. Teacher chủ phòng có thể thử nạp full code sau khi giữ quyền; teacher không phải owner và student phải bị server từ chối.

## C. Chia sẻ màn hình sau khi merge server module

1. Đảm bảo cổng TCP 5001 mở trong Windows Firewall trên máy server và LAN.
2. Người publish phải ở room, bấm Bắt đầu chia sẻ; nhận one-time ticket từ server.
3. Một member cùng room bấm Xem màn hình đang chia sẻ; nhận ảnh.
4. Client ngoài room không thể lấy ticket hợp lệ; ticket hết hạn/dùng hai lần phải bị từ chối.
5. Người thứ hai trong cùng room thử publish: server từ chối vì một publisher/room.
6. Dừng publish; viewer thấy luồng dừng.
7. Kick/leave/disconnect người publish; relay giải phóng room và đóng viewer stream.
8. Làm chậm một viewer; kiểm tra hàng đợi frame không tăng vô hạn và publisher vẫn chạy.
9. Shutdown server; cả control sockets và screen sockets được đóng.

## D. Ghép thành viên 3

Không thể test end-to-end cho đến khi server có command bài tập/nộp/chấm/thi. Khi hợp đồng được chốt:

- List exercise / view details: kiểm tra role, room, Unicode và bài không tồn tại.
- Assignment scope chung/cá nhân; xác nhận dữ liệu cá nhân không broadcast cho học sinh khác.
- Submission lưu idempotent, phân biệt nhận bài và chấm xong.
- Worker test compile error, wrong answer, accepted, timeout, memory/output limit.
- Contest deadline server-side, submission sau hạn, tiebreak trùng điểm, bảng xếp hạng cập nhật.
- Mất kết nối ở lúc nộp: khi reconnect kiểm tra lịch sử từ server, không dựa vào UI state cũ.


## UI rút gọn bản nháp

- Bôi đen một dòng trong Code chung, mở vùng chọn thành bản nháp; vùng được chọn có thể sửa trực tiếp trong text area.
- Lặp lại với nhiều dòng liên tiếp; xác nhận chỉ thay vùng chọn, các dòng ngoài vùng không đổi.
- Nhấn Xem bản nháp; cửa sổ preview phải hiển thị toàn bộ code ghép chưa lưu. Xác nhận chỉ bật hoàn tất sau khi server xác nhận preview.
- Sửa bản nháp sau preview; xác nhận nút Hoàn tất bị khóa cho đến khi xem trước lại.
- Hoàn tất; kiểm tra version/code cập nhật trên các client. Hủy; kiểm tra code/version không đổi.
- Thử vùng rỗng (xóa nội dung), token sai, mất kết nối và bản nháp vượt giới hạn 32.000 byte UTF-8.
