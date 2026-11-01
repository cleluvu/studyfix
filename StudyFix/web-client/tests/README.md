# Kiểm thử `web-client` và các tích hợp liên quan

## Luyện code cá nhân

`node tests/check_practice.mjs`: 21 kiểm tra HTTP/DOM mô phỏng cho giao diện hai role giống nhau, không cần phòng, bố cục dọc, nháp theo bài, phản hồi muộn, source snapshot, polling AC, file import dùng lại, retry idempotent, refresh và đổi tài khoản. `ant practice-test`: 63 kiểm tra HTTP/SQLite/worker; `ant practice-docker-test`: 25 kiểm tra chấm Java thật bằng Docker cho AC/WA/CE và public run. [Hướng dẫn](../../docs/CODE_PRACTICE_VI.md).

## Danh sách và mã phòng

`node tests/check_room_directory.mjs`: 28 kiểm tra HTTP/DOM mô phỏng cho mã tự nhập, quyền hiển thị Xóa, phân trang cố định 5, phòng trống, escape Unicode, giữ mã đang gõ, phản hồi trả muộn, điều hướng và dừng track khi xóa phòng. `ant room-test` dùng HTTP/TCP/SQLite thật để kiểm tra giữ phòng và xóa chủ động (72 checks), gồm quyền xóa, tách thành viên nhưng giữ đăng nhập và phục hồi qua restart. `ant integration-test` chạy thêm các suite lịch sử, transaction, cuộc gọi và bài tập; không cần profile lưu phòng riêng.

## Tin chưa đọc

`node tests/check_chat_notifications.mjs`: 23 kiểm tra HTTP/DOM mô phỏng cho huy hiệu, toast, tiêu đề tab, loại trừ tin tự gửi, tài khoản trùng tên, tab ẩn/mất focus, cuộn xem lịch sử, phạm vi tài khoản/phòng, localStorage, hơn 80 tin và phản hồi cũ sau khi đã đọc. `ant chat-test` từ server kiểm tra HTTP/SQLite thật (16 checks), gồm quyền đọc phòng, userId, cursor và đếm 90 tin ngoài cửa sổ hiển thị 80 tin. Cần thử giao diện trên hai tài khoản thật sau khi tải lại trang.

## Giao diện cuộc họp và media

`node tests/check_meeting_navigation.mjs`: 19 kiểm tra HTTP/media/DOM mô phỏng cho Rời cuộc họp giữ tham gia phòng, Rời phòng gửi lệnh riêng, dọn track, không tự nối lại màn hình, vào lại cuộc họp, khôi phục màn hình theo tài khoản/phòng khi refresh, không thêm mục Phòng học vào sidebar và bỏ phản hồi phòng cũ đến muộn. Cần nghiệm thu Ctrl+Shift+R và thiết bị trên trình duyệt thật.

`node tests/check_screen_audio.mjs`: 13 kiểm tra mô phỏng về yêu cầu audio khi screen capture, gửi cả audio/video track, trường hợp video-only, streamless ontrack, autoplay bị chặn, phát lại và dọn track khi dừng. `check_classroom.mjs` hiện có 30 kiểm tra, bổ sung trạng thái và nút phát âm thanh chia sẻ. Các kiểm tra này không thay thế thử YouTube thật giữa hai máy.

`node tests/check_meeting.mjs`: 29 kiểm tra với WebRTC/thiết bị/HTTP mô phỏng cho listen-only, offer/answer, ghép sender vào transceiver đã thương lượng, ICE sớm, camera bật sau answer, polling đồng thời, mute/unmute, từ chối quyền và dọn kết nối. Test sender của phía trả lời tái hiện lỗi media một chiều trên mã cũ. `check_classroom.mjs` hiện có 29 kiểm tra, gồm lưới giảng viên, terminal sinh viên, bộ lọc và drawer. Không phải browser media E2E. Backend `ant meeting-test` kiểm tra HTTP thật và quyền signaling; hướng dẫn: `StudyFix/docs/MEETING_UI_VI.md`.

## Phòng học với nhánh riêng

`node tests/check_classroom.mjs` chạy 22 kiểm tra hành vi với DOM/HTTP giả lập cho workspace Master/nhánh, autosave, nháp xung đột, polling cũ, modal và khôi phục nháp. Đây không phải browser E2E. Từ `server`, `ant classroom-test` kiểm tra HTTP/TCP/SQLite thật, snapshot, quyền, vòng cũ, rollback, chat và restart; `ant integration-test` chạy thêm các suite cũ. Hướng dẫn hiện hành: `../docs/CLASSROOM_BRANCH_WORKFLOW_VI.md` tính từ thư mục `web-client`.

Các kiểm tra frontend chạy từ thư mục `web-client/`:

## 1. Cú pháp và module

```powershell
Get-ChildItem .\static -Recurse -Filter *.js | ForEach-Object { node --check $_.FullName }
node .\tests\check_imports.mjs
```

- `node --check`: kiểm tra cú pháp từng file JS.
- `check_imports.mjs`: kiểm tra import/export và vòng import tĩnh.

## 2. Hồi quy bảy nhóm lỗi

```powershell
node .\tests\check_seven_bugfixes.mjs
```

Script có 24 kiểm tra tĩnh cho: starter/restore chính xác, bỏ nút chọn bài thừa, nạp file, trạng thái AC, quyền bảng xếp hạng, signaling chia sẻ màn hình hai chiều và đóng modal. Đây là kiểm tra tĩnh, không thay thế test trong trình duyệt thật.

## 3. Backend integration tests

Từ thư mục `server/`, với JDK phù hợp và Apache Ant:

```powershell
.\test-all.ps1
```

Hoặc dùng target Ant `integration-test` theo build script. Các test Java dùng SQLite tạm và các cổng loopback tự chọn khi được cấu hình như trong test. Một số test console dùng tên file tiếng Việt; trên Linux cần locale UTF-8. Môi trường đóng gói hiện tại dùng JDK 21 override để xác minh compile/test, trong khi project mặc định nhắm JDK 26.

## 4. Giới hạn screen share

- Web FE sử dụng WebRTC trong trình duyệt; chia sẻ màn hình từ máy khác trong LAN cần HTTPS với chứng chỉ được trình duyệt tin cậy (ngoại lệ là localhost).
- Client Swing dùng TCP screen-share relay. Hai transport hiện hoạt động riêng; chưa có bridge hỗn hợp giữa Swing publisher và browser viewer hoặc ngược lại.
- Các test gateway xác minh signaling/authorization. Test relay xác minh truyền JPEG qua TCP theo cả hai chiều vai trò. Chưa có browser media E2E test hoàn chỉnh trong môi trường đóng gói.
