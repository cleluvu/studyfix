# Sửa độ tin cậy và đồng bộ backend — 04/11/2026

## 1. Lệnh TCP và rời phòng đồng thời

`ClientHandler.run()` chỉ giữ khóa handler sau khi đã đọc xong frame, trong lúc xử lý một lệnh. Các đường rời/tạo/vào phòng cũng dùng khóa handler. Thứ tự khóa là handler rồi Room; không giữ khóa handler trong lúc chờ dữ liệu mạng. Việc rời phòng từ HTTP không còn chen giữa bước kiểm tra `currentRoom` và sử dụng nó trong một lệnh TCP.

## 2. Hủy heartbeat của phiên đã mất socket

`WebSession.close()` luôn hủy heartbeat/departure và đóng socket, kể cả socket đã đóng trước đó. Reader gọi close trong finally. `closeSession()` luôn thực hiện cleanup tài nguyên thay vì bỏ qua khi reader đã đánh dấu closed. Nếu socket mất đúng lúc tạo heartbeat sau login, bước kiểm tra closed sau đăng ký sẽ hủy tác vụ vừa tạo. Đóng socket đủ để server lõi dọn phiên; không gửi `/quit` trong đường cleanup để tránh chờ ghi mạng.

## 3. Lưu kết quả chấm khi database gặp lỗi

`AssignmentService` tách `gradeTask()` khỏi `persistUntilSaved()`. Sau khi chấm, worker giữ nguyên đối tượng kết quả và thử ghi lại mỗi giây khi ghi thất bại. Nó không chấm lại và không đổi một verdict đã có sang INTERNAL_ERROR chỉ vì ghi database lỗi.

Lỗi chấm thực sự được ghi log theo loại công việc và submissionId, rồi tạo kết quả lỗi dịch vụ. Cùng một đường persistence xử lý PERSONAL, SHARED, CONTEST, TRIAL và PRACTICE. Không còn nuốt lỗi của việc lưu kết quả trong các nhánh worker này.

Server Manager hiển thị số lượt đã chấm đang thử lưu lại database. Nhật ký ghi lần lỗi đầu, định kỳ khi lỗi kéo dài, và khi ghi lại thành công. Worker giữ công việc chờ ghi nên số kết quả giữ trong RAM được giới hạn bởi số worker; queue mới vẫn có giới hạn.

**Giới hạn thực tế:** nếu database vẫn hỏng, kết quả chưa thể được xác nhận đã lưu; worker tiếp tục chờ và log. Khi dừng tiến trình, kết quả chưa ghi trong RAM không được cam kết bền vững; bản ghi công việc chưa hoàn tất được phục hồi theo cơ chế khởi động hiện có. Không thể khẳng định dữ liệu chắc chắn được lưu khi ổ đĩa không cho phép ghi.

## 4. Thu hẹp khóa Store khi đăng nhập

`Store.login()` lấy identity/password hash và đóng ResultSet/Statement trong khóa Store, sau đó thực hiện PBKDF2 bên ngoài khóa. Mảng mật khẩu vẫn được xóa trong finally. Thay đổi này không giảm số vòng PBKDF2 và không thay cách xác thực.

## 5. Giảm lượng dữ liệu Server Manager đọc

`ServerAdmin` chuyển ID phòng đang chọn qua trường volatile cho luồng giám sát. `ServerMonitor.capture(server, selectedRoomId)` chỉ tải code nhánh/lịch sử kiểm tra cho phòng đó. Các phòng khác vẫn có metadata tên, chủ phòng, phase/vòng. Khi chưa chọn phòng, không tải chi tiết tất cả nhánh.

Nhãn owner được dùng lại theo userId trong mỗi lượt đọc. Việc format code/lịch sử diễn ra bên ngoài khóa Room. Metadata vẫn được lấy định kỳ; đây không phải cơ chế cache/event-driven cho toàn bộ server.

## 6. Xung đột SQLite lúc tạo version

Trong hồi quy phát hiện transaction tạo practice version đọc MAX(version) rồi nâng từ đọc sang ghi có thể gặp SQLITE_BUSY khi worker cũng ghi kết quả. `PracticeRepository` cấu hình transaction IMMEDIATE, lấy quyền ghi trước khi đọc version. Snapshot vừa tạo được đọc sau khi kết nối transaction đã đóng. Unique constraint theo user/bài/version được giữ nguyên.

## Kiểm chứng

- `ServerReliabilityIntegrationTest`: HTTP/TCP/SQLite thật, dùng latch để ép chat/rời phòng chồng nhau; ép mất socket; dùng trigger từ chối ghi kết quả rồi gỡ trigger; kiểm tra tải chi tiết đúng phòng; hai repository tạo phiên bản song song.
- `LoginLockTest`: provider thử nghiệm tạm dừng PBKDF2 thật, xác nhận đọc database phòng vẫn hoàn tất trong lúc xác thực đang bị tạm dừng. Provider chỉ được cài trong process test và được gỡ sau test.
- `server-reliability-test` được ghép vào `integration-test` cùng kiểm tra Server Manager và nghiệp vụ hiện có.
- `PracticeDockerIntegrationTest` kiểm tra worker với Docker thật, các kết quả AC/WA/lỗi biên dịch. Fixture persistence trong kiểm thử không phải kết quả chấm người dùng.

Sau khi cập nhật source, dừng Server Manager cũ và chạy lại launcher để nạp bản mới. Các kiểm thử dùng cổng/database tạm, không dùng database đang vận hành.
