# Mã phòng tự chọn và xóa phòng chủ động

Cập nhật 30/10/2026: giảng viên tự nhập mã phòng, danh sách gồm tất cả phòng hiện có với **5 phòng mỗi trang**. Không tự xóa khi hết người; chỉ giáo viên tạo phòng được bấm **Xóa** cạnh nút Vào/Mở.

## Sử dụng

1. Đóng Server Manager cũ, chạy lại `server/run-server-manager.ps1` để build và nạp backend mới. Tải lại trình duyệt bằng Ctrl+Shift+R.
2. Tại Tổng quan, nhập **Tạo phòng bằng mã của bạn**, ví dụ `Lớp Java A`, rồi nhấn **Tạo phòng**. Web không tự lấy số phòng kế tiếp.
3. Thành viên bấm **Vào** hoặc nhập mã ở **Tham gia bằng mã phòng**. Mỗi phiên chỉ ở một phòng, cần rời trước khi tạo hoặc tham gia phòng khác.
   Nút **Rời cuộc họp** trong workspace chỉ dừng media và về Tổng quan, vẫn giữ phòng đang tham gia. Trong danh sách, phòng đó có **Vào phòng** và **Rời phòng** cạnh nhau; Rời phòng mới bỏ tham gia. Sidebar màn hình chính không có mục Phòng học tự sinh; Ctrl+Shift+R khôi phục cuộc họp nếu trước đó đang họp và server xác nhận vẫn tham gia.
4. Danh sách gồm cả phòng trống, phòng người khác và phòng hiện tại. Trước/Sau duyệt toàn bộ danh sách; mỗi trang tối đa 5 phòng, trang cuối có thể ít hơn.
   Ô **Tìm kiếm phòng** lọc toàn bộ danh sách theo một phần mã, không phân biệt hoa/thường hoặc dấu tiếng Việt. Gõ sẽ tự tìm sau khoảng 300 ms; có thể bấm Tìm/Enter. Xóa từ khóa để xem lại tất cả. Khi đổi từ khóa trở về trang 1; kết quả vẫn 5 phòng/trang. Ô tìm kiếm ở thanh trên cùng cũng tìm phòng khi đang ở Tổng quan.
5. Chủ phòng thấy **Xóa** cạnh Vào/Mở. Có xác nhận trước khi xóa. Nếu phòng có người, xác nhận nêu số thành viên: tất cả sẽ về Tổng quan, dừng cuộc gọi/chia sẻ màn hình nhưng vẫn giữ đăng nhập.

Mã dài 1–80 ký tự Unicode, cho phép tiếng Việt, dấu câu và khoảng trắng bên trong. Bỏ khoảng trắng ngoài cùng; từ chối xuống dòng/ký tự điều khiển. Mã trùng bị từ chối kể cả khi phòng trống, không phân biệt hoa/thường và chuẩn hóa Unicode NFC.

## Giữ phòng và xóa phòng

- Rời phòng, đăng xuất, đóng tab hoặc mất mạng chỉ làm thành viên rời phòng. Phòng còn trong danh sách với 0 thành viên.
- Khởi động lại server phục hồi phòng chưa bị xóa, cùng mã và code/lịch sử đã lưu; không phục hồi kết nối thành viên cũ.
- Đóng tab có khoảng đệm 8 giây; request từ trang tải lại hủy hẹn đóng phiên. Phiên Web không còn request hết hạn sau khoảng 2 phút, kiểm tra mỗi 15 giây. Đây là dọn phiên, không xóa phòng.
- Xóa phòng đánh dấu `active=0`, gỡ khỏi danh sách và chặn tham gia lại. Code, revision, nhánh, chat và bài nộp cũ được giữ trong database; không có màn hình mở lại phòng đã xóa.
- Có thể tạo lại mã đã xóa. Phòng mới có ID database mới, không kế thừa bài tập, lịch sử hoặc quyền của phòng cũ.

## Migration và tương thích

Core database có `rooms.public_code`, `code_key`, `active` và unique index cho mã đang active. Không đổi khóa chính/FK hoặc ID bài nộp. Mã phòng cũ được backfill từ ID số. Không còn đánh dấu tất cả phòng inactive khi khởi động; không tự phục hồi bản ghi đã inactive ở phiên bản trước. Sao lưu `server/data/` khi server đã dừng trước khi nâng cấp.

TCP `/create mã tùy chọn` và `/join mã tùy chọn` hỗ trợ khoảng trắng. Console `/create` vẫn sinh mã để tương thích native; Web luôn yêu cầu mã tự nhập. ID số nội bộ tách riêng với `roomCode`; tích hợp cũ có thể dùng ID khi không trùng mã công khai. Không cần cờ `studyfix.rooms.persistent`: giữ phòng là hành vi mặc định duy nhất.

## API

- `POST /api/rooms/current/command`, `{action:"CREATE_ROOM",roomCode:"Lớp Java A"}`: giảng viên tạo và vào phòng đồng bộ, trả 200 cùng `roomId`/`roomCode`. Thiếu mã trả 400, trùng mã trả 409.
- `GET /api/rooms?page=1&pageSize=5&q=lop%20java`: mọi người đã đăng nhập tìm phòng chưa bị xóa, kể cả phòng trống. `q` được URL-decode, giới hạn 160 ký tự, lọc theo mã trước khi phân trang, bỏ qua hoa/thường và dấu tiếng Việt. Bỏ `q` hoặc để trống để xem tất cả. Trả `rooms`, `page`, `pageSize`, `total`, `totalPages`, `customRoomCodes:true`, `emptyRoomAutoDelete:false`, `manualRoomDelete:true`; tổng số tính theo kết quả tìm kiếm. Page size luôn bằng 5; trang quá lớn được đưa về trang cuối.
- `DELETE_ROOM` nhận `roomId` nội bộ bất biến. Chỉ chủ phòng có role TEACHER được xóa, kể cả đang ở ngoài phòng; người khác trả 403. Lưu trạng thái xóa thành công trước khi gỡ khỏi RAM; lỗi database giữ phòng khả dụng. Thành viên được tách khỏi phòng, nhận `[ROOM_DELETED] ID` qua events.
- `JOIN_ROOM` nhận `roomId` chứa mã nhập, tra mã công khai trước. `/api/me` và phòng hiện tại trả thêm `roomCode`; phân quyền/bài nộp dùng ID nội bộ.
- `LEAVE_ROOM` bỏ tham gia đồng bộ, trả 200 và `leftRoomId`; request thành công thì `/api/me` không còn phòng hiện tại. Rời cuộc họp chỉ gửi call `LEAVE` và screen `END` nếu đang chia sẻ, không gửi lệnh này.
- `POST /api/auth/departing`: báo đóng trang có khoảng đệm 8 giây, dùng bearer của chính phiên.

## Kiểm thử

`ant room-test`: 72 kiểm tra HTTP/TCP/SQLite trên database tạm, gồm mã Unicode, trùng mã, quyền tạo/xóa, giữ phòng trống, 5 phòng/trang, tìm không dấu/không phân biệt hoa thường trên toàn danh sách, không có kết quả, xóa từ khóa, giới hạn độ dài, xóa phòng có người, thông báo/giữ đăng nhập, chống tham chiếu phòng đã xóa, tái sử dụng mã, ngắt kết nối, đóng tab/refresh và phục hồi sau restart.

`node web-client/tests/check_room_directory.mjs` từ thư mục StudyFix: 28 kiểm tra HTTP/DOM mô phỏng, gồm nút xóa đúng chủ phòng, ID bất biến, escape mã/từ khóa, phân trang, nội dung đang gõ, URL-encode tìm kiếm, bỏ phản hồi từ khóa cũ và dừng track cuộc gọi. Chưa thay thế kiểm thử media trên trình duyệt thật.

Nghiệm thu thủ công: tạo `Lớp Java A`, cho sinh viên vào, tất cả rời rồi xác nhận phòng còn với 0 thành viên. Restart server và vào lại mã đó. Tạo thêm phòng để xem trang 2. Kiểm tra sinh viên/giảng viên khác không có nút Xóa. Chủ phòng xóa phòng đang có người; các thành viên phải về Tổng quan, mic/camera/chia sẻ dừng lại.
