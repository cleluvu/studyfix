# Ownership và tài liệu chung khi ghép người 1–2–3

## 1. Ai sở hữu file nào

| Người | Source chính được sở hữu | Không tự ý thay đổi |
|---|---|---|
| 1 — server room/code | `Server_LTM/studyfix/src/studyfix/Server.java`, `ClientHandler.java`, `Room.java`, `Protocol.java`, `Store.java`, `StudyFixGateway.java`, test tích hợp server | Không đổi response/lệnh đã công bố mà không cập nhật `SERVER_PROTOCOL_MAP.md` và báo cho người 2. |
| 2 — client + screen share | `studyfix-client/src/main/java/vn/studyfix/client/**`; `server-screen-share-module/src/studyfix/screenshare/**`; patch tích hợp relay | Không sửa core room/code server trực tiếp trong nhánh người 2; gửi patch cho người 1 review/merge. |
| 3 — bài tập/chấm/thi | Client package mới `vn.studyfix.client.exercise/**` (hoặc package nhóm thống nhất); server module học tập/bộ chấm riêng; DTO và test của module đó | Không tạo socket reader thứ hai; không tự sửa `Room`, auth hay protocol framing lõi mà không phối hợp người 1. |

## 2. Các file “source of truth” phải cập nhật

1. `docs/SERVER_PROTOCOL_MAP.md`: mọi lệnh hiện có, syntax, markers, limits, permission; người 1 cập nhật khi server thay đổi.
2. `docs/INTEGRATION_WITH_MEMBER3.md`: hợp đồng API `ClientModuleContext`, module tab và protocol dự kiến cho bài tập; người 2 giữ giao diện tích hợp, người 3 bổ sung endpoint/schema khi đã thống nhất.
3. `docs/SERVER_GAPS_AND_SCREEN_SHARE.md`: danh sách phần server baseline chưa có và giao thức relay màn hình; người 1/2 cùng chốt trước merge.
4. `docs/TEST_PLAN.md`: thêm test khi có endpoint mới; test gồm thành công, sai quyền, timeout, ngắt kết nối, dữ liệu lớn và nhiều client.

Không chỉ sửa code mà bỏ quên các file này; nếu protocol đổi mà tài liệu cũ, các thành viên sẽ làm client/service không tương thích nhau.

## 3. Hợp đồng chung ở client

- Một `TcpControlConnection` cho mỗi phiên đăng nhập.
- Một reader nền chịu trách nhiệm đọc control socket; module chỉ subscribe `ServerMessage`.
- `FrameCodec` / `HistoryFrameAssembler` là code lõi của client; không copy implementation vào module thành viên 3.
- Gửi command bằng `ClientModuleContext.sendControlCommand(...)`.
- Server-authoritative: kết quả command, role, version, token, score và trạng thái submission phải có response/event server chứng minh.
- Các panel chạy trong cùng `MainFrame`; không tạo ứng dụng client thứ hai.
- Screen JPEG sử dụng socket riêng theo giao thức đã chốt, không trộn với control socket.

## 4. Thủ tục khi ghép thay đổi

1. Thành viên bổ sung code riêng và test riêng.
2. Cập nhật protocol map và integration doc.
3. Review giới hạn frame, phân quyền, xử lý response, cleanup/disconnect.
4. Build server bằng JDK 26 theo cấu hình người 1; build client bằng `javac --release 17` hoặc Maven.
5. Chạy unit/smoke test, rồi kiểm thử server+client với ít nhất 3 session và 2 room.
6. Chỉ đánh dấu chức năng “hoàn thành” khi test đầu-cuối qua server thật, không dựa vào giao diện hoặc mock.
