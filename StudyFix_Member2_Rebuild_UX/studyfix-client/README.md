# StudyFix Client — phần thành viên 2 (xây mới)

Đây là client Swing viết mới, lấy server `Server_LTM/studyfix` hiện tại làm chuẩn. Client dùng Java 17, không phụ thuộc thư viện ngoài. Không có file server nào trong project client này bị sửa.

## Nội dung đã có

- Màn hình nhập IP/host, cổng, username, password và đăng nhập bằng `/login username password`.
- Một kết nối TCP điều khiển lâu dài; frame 4 byte big-endian + UTF-8, tối đa 65.536 byte; heartbeat `/ping` mỗi 5 giây; ghép `[HISTORY_PART i/n]`.
- Tạo phòng, vào/rời phòng, xem thành viên, chat trong phòng; phân tích trạng thái phòng và code chung từ text response server hiện tại.
- Xem code chung, xin quyền sửa và trả quyền; chọn trực tiếp một vùng dòng liên tiếp trong code chung để mở bản nháp, sửa code ngay trong vùng bản nháp, xem trước toàn bộ code đã ghép, rồi hoàn tất hoặc hủy lượt. Phần sửa không còn các nút riêng cho sửa/xóa/giữ từng dòng; server vẫn xác thực token/version và là nơi lưu chính thức. Xem lịch sử code và nạp toàn bộ code (server chỉ cho giáo viên chủ phòng).
- Module chụp ảnh desktop, nén JPEG, hiển thị ảnh và client transport cho giao thức màn hình dự kiến. **Stream qua mạng chỉ hoạt động sau khi ghép `server-screen-share-module` vào server**, vì server hiện chưa có lệnh cấp ticket/listener màn hình.
- Vị trí tích hợp cho phần bài tập/nộp bài/thi của thành viên 3 thông qua `ClientModuleContext` và tab placeholder. Không giả lập kết quả chấm.

## Chạy bằng Maven (nếu đã cài Maven)

Tại thư mục `studyfix-client`:

```powershell
mvn clean package
.\self-test.ps1
java -jar target/studyfix-client-1.0.0.jar
```

Project không dùng thư viện ngoài. Nếu Maven chưa cài hoặc không tải được plugin, dùng các script PowerShell:

```powershell
.\build.ps1
.\self-test.ps1
.\run.ps1
```

Java 17 trở lên là đủ cho client. Server trong gói thành viên 1 được cấu hình cho JDK 26; hãy chạy server theo README server của nhóm, không cần hạ phiên bản Java của server để chạy client.

Để test nhiều tài khoản đồng thời, chạy `run.ps1` một lần để build và mở client đầu tiên. Sau đó mở thêm PowerShell khác trong cùng thư mục và chạy `.\run-client-only.ps1` hoặc `java -jar target/studyfix-client-1.0.0.jar` để mở cửa sổ mới mà không build lại. Đóng các cửa sổ client trước khi chạy `build.ps1` lại vì Windows có thể khóa JAR đang được dùng.

## Luồng sửa code đã rút gọn

1. Nhấn **Xin quyền sửa** và chờ server xác nhận bạn đang là editor.
2. Bôi đen một hoặc nhiều dòng liên tiếp trong khung **Code chung**. Nếu không bôi đen, client hiểu dòng đang đặt caret là vùng chọn.
3. Nhấn **Mở vùng chọn thành bản nháp**. Phần được chọn sẽ xuất hiện trong khung bản nháp editable.
4. Sửa trực tiếp nội dung trong khung bản nháp, không cần các nút sửa/xóa/giữ riêng.
5. Nhấn **Xem bản nháp**. Client gửi phần thay thế tới server, chờ xác nhận, rồi mở cửa sổ xem toàn bộ code sau khi ghép. Nếu tiếp tục sửa sau đó, cần xem trước lại trước khi hoàn tất.
6. Nhấn **Hoàn tất lượt sửa** để server lưu phiên bản mới, hoặc **Hủy lượt sửa** để bỏ bản nháp.

UI mới chọn một vùng liên tiếp (bao gồm trường hợp một dòng được biểu diễn là `N-N`) vì server hiện tại hỗ trợ thay thế vùng bằng `/setrange`. Các dòng ngoài vùng được chọn sẽ được giữ nguyên. Nút **Xem bản nháp** đồng bộ phần đang soạn trước khi hiển thị bản xem trước; nó không lưu phiên bản chung.

## Cổng mặc định

- Điều khiển: `5000`.
- Chia sẻ màn hình dự kiến: `5001`, có thể đổi qua `-Dstudyfix.screen.port=...` phía server.

Đổi host mặc định bằng `-Dstudyfix.host=192.168.1.10`, cổng điều khiển bằng `-Dstudyfix.port=5000`. Màn hình đăng nhập vẫn cho nhập host/port trực tiếp.

## Tài liệu phải đọc trước khi ghép nhóm

- `docs/TASK2_COMPLETE_PLAN.md`: nhiệm vụ thành viên 2, cấu trúc source, luồng xử lý và tiêu chí nghiệm thu.
- `docs/SERVER_PROTOCOL_MAP.md`: hợp đồng đã xác minh từ server hiện tại.
- `docs/INTEGRATION_WITH_MEMBER3.md`: giao diện để người 3 đưa panel bài tập vào ứng dụng.
- `docs/SERVER_GAPS_AND_SCREEN_SHARE.md`: chức năng server chưa có và thiết kế relay màn hình dự kiến.
- `docs/TEST_PLAN.md`: cách test nhiều client, xin quyền sửa và test tích hợp.
- `docs/BUILD_REPORT_VI.md`: phân biệt các mục đã compile/test với các mục còn chờ test trên server thật.

## Tài khoản demo (server tạo khi database khởi tạo lần đầu)

| Username | Password | Role |
|---|---|---|
| `teacher` | `Teacher123!` | TEACHER |
| `teacher2` | `Teacher234!` | TEACHER |
| `student1` | `Student123!` | STUDENT |
| `student2` | `Student234!` | STUDENT |

## Giới hạn cần nói rõ

Server hiện gửi sự kiện dạng chuỗi văn bản, chưa có request ID/JSON schema chuẩn. Client vì thế phân tích marker và text như `[LOGIN_OK]`, `[Thành viên]`, `[Phòng ...] version=...`, `--- CODE CHUNG ---`. Server vẫn là nguồn quyết định quyền. Nếu thành viên 1 đổi định dạng message, phải cập nhật `ServerMessageParser` và `SERVER_PROTOCOL_MAP.md` cùng lúc.

Server hiện chưa triển khai giao bài/nộp bài/worker chấm/thi/xếp hạng hoặc screen relay TCP; các tab đó được chuẩn bị chỗ ghép và được ghi rõ là chưa tích hợp.
