# Kiểm thử `web-client` và các tích hợp liên quan

## Giao diện cuộc họp và media

`node tests/check_meeting.mjs`: 23 kiểm tra với WebRTC/thiết bị/HTTP mô phỏng cho listen-only, offer/answer, ICE sớm, track camera, mute/unmute, từ chối quyền và dọn kết nối. `check_classroom.mjs` hiện có 29 kiểm tra, gồm lưới giảng viên, terminal sinh viên, bộ lọc và drawer. Không phải browser media E2E. Backend `ant meeting-test` kiểm tra HTTP thật và quyền signaling; hướng dẫn: `StudyFix/docs/MEETING_UI_VI.md`.

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
