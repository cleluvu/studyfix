# Server protocol map — đối chiếu trực tiếp `src/studyfix/Protocol.java`, `ClientHandler.java`, `Room.java`

## 1. Frame TCP

- Socket điều khiển mặc định port `5000`, lắng nghe các interface mạng.
- Mỗi payload: **4 byte signed big-endian độ dài** + số byte UTF-8 tương ứng.
- Giới hạn payload: 65.536 byte. Client phải đọc đủ payload trước khi parse; không dựa vào newline TCP.
- Lệnh là chuỗi văn bản; protocol hiện tại **không phải JSON**.
- Server có thể chia response dài thành nhiều payload `[HISTORY_PART i/n]\n...`; client phải ráp đủ parts trước khi trình bày. Server gửi các part liên tiếp theo một queue entry.
- Client cần gửi `/ping` mỗi 5 giây; server đóng session nếu không thấy heartbeat trong khoảng 20 giây. Server đáp `/pong`.

## 2. Lệnh được source hiện tại hỗ trợ

| Lệnh gửi từ client | Quy tắc/permission phía server | Response/sự kiện quan sát được |
|---|---|---|
| `/login username password` | Trước login; username tối đa 64 ký tự, password tối đa 256; sau 5 lần sai server đóng connection; không đổi danh tính giữa phiên | `[LOGIN_OK] session=... userId=... role=TEACHER\|STUDENT \| displayName`; lỗi bắt đầu `[LOGIN_FAILED]` hoặc text hướng dẫn |
| `/create` | Teacher only; không được đang trong room | `Đã tạo và tham gia phòng: id`, rồi code snapshot |
| `/join roomId` | Chưa ở room; id là mã phòng database | `Đã tham gia phòng: id`, rồi code snapshot; lỗi `Không tìm thấy phòng...` |
| `/members` | Đang trong room mới có kết quả | `Thành viên phòng id: [sessionId \| displayName, ...]` |
| `/kick sessionId` | Teacher là chủ phòng; không tự kick mình; đóng **connection** của target | Xác nhận text; sự kiện thành viên rời do cleanup |
| `/leave` | Nếu đang trong room: remove member, remove from queue và chuyển lượt nếu editor rời | `Đã rời phòng: id`, `[Hệ thống]`, `[Thành viên]`, room state |
| `/chat text` | Chỉ room hiện tại; text 1–2.000 ký tự Java | Broadcast `[Phòng id] displayName: text` tới room hiện tại |
| `/code` | Room member | Room state + `--- CODE CHUNG ---` + full code |
| `/lines` | Room member | Room state + `--- SỐ DÒNG THEO VERSION HIỆN TẠI ---`, mỗi dòng `n | code` |
| `/request` | Room member; nếu editor trống cấp ngay, không thì FIFO; một session không lặp trong queue | Broadcast `[Phòng id] version=..., editor=..., token=..., hàng đợi=[...]` |
| `/release token` | Chỉ editor hiện tại + token đúng; không được còn draft | Server chuyển lượt, tăng token và broadcast state |
| `/select version token 2,5,8` | Editor/token/version đúng; không có draft đang mở; số dòng hiện hữu, không trùng | Trạng thái `[Bản nháp]`; dạng chọn không liên tiếp |
| `/select version token 4-7` | Như trên, nhưng chọn đoạn liên tiếp | Trạng thái `[Bản nháp]` |
| `/setline token line text` | Chỉ sửa line đã chọn; text không được chứa newline | Phản hồi trạng thái draft |
| `/delete token line`; `/keep token line` | `line` phải nằm trong lựa chọn dòng rời | Phản hồi trạng thái draft |
| `/setrange token text` | Phải chọn đoạn liên tiếp; nội dung replacement được phép nhiều dòng | Phản hồi trạng thái draft |
| `/deleterange token`; `/keeprange token` | Phải chọn đoạn liên tiếp | Phản hồi trạng thái draft |
| `/draft token` | Token/role hợp lệ và draft tồn tại | Trạng thái draft + `--- XEM TRƯỚC CODE (CHƯA LƯU) ---` |
| `/finish token` | Draft phải được xử lý đầy đủ; server kiểm tra version và size, lưu DB trước khi đổi RAM/broadcast | `[COMMITTED]`, `state`, `--- CODE CHUNG ĐÃ CẬP NHẬT ---` hoặc lỗi; không thay đổi thì `[NO_CHANGE]` |
| `/cancel token` | Editor/token đúng; hủy draft và nhả lượt | `[CANCELLED]`, code không đổi |
| `/update version token code` | Toàn bộ code chỉ dành cho **giáo viên chủ phòng**; phải là editor đúng token/version; không có draft; code không rỗng, tối đa 32.000 byte UTF-8 | Broadcast state + `--- CODE CHUNG ĐÃ CẬP NHẬT ---` + code, hoặc lỗi |
| `/history` | Room member | Danh sách revision của phòng |
| `/history version` | Room member; version không âm, phải tồn tại | Nội dung revision cũ, chỉ xem, không đổi code hiện tại |
| `/ping` | Kể cả trước login được xử lý heartbeat | `/pong` |
| `/quit` | Đóng connection | Server dọn phiên/phòng/quyền trong finally |

## 3. State/message format đã thấy trong source

- Code snapshot: `[Phòng 12] version=0, editor=không có, token=0, hàng đợi=[]\n--- CODE CHUNG ---\n...`
- State event: `[Phòng 12] version=1, editor=Student A, token=3, hàng đợi=[Student B]`
- Code update marker: `--- CODE CHUNG ĐÃ CẬP NHẬT ---`
- Thành viên broadcast: `[Thành viên] Student A, Student B`
- `/members` direct response chứa session ID: `Thành viên phòng 12: [uuid-session \| Display Name, ...]`
- Room activity: `[Hệ thống] ...`
- Chat: `[Phòng 12] Display Name: text`
- Draft: `[Bản nháp] ...`
- Server bảo toàn raw UTF-8; một message có thể nhiều dòng.

## 4. Các hạn chế của protocol hiện tại cần phối hợp với người 1

1. Không có request ID; UI không thể ghép từng command với response một cách tuyệt đối nếu hai phản hồi tới gần nhau. Client dùng một event stream và giữ nguyên raw text trong Activity.
2. Không có JSON schema; text tiếng Việt/marker có thể đổi thì parser phải cập nhật.
3. State editor chỉ gửi tên hiển thị, chưa có `editorSessionId`; hai user trùng display name khiến UI khó nhận diện owner. Việc bảo mật không phụ thuộc client vì server tự kiểm token/session.
4. `kick` đóng connection của thành viên, không phải chỉ remove họ khỏi room.
5. Chat không được lưu lịch sử bền vững; `/history` là lịch sử code revision, không phải chat history.
6. Không có `/screen-ticket`, listener screen share, worker bài tập, submission, contest, scoreboard. Các phần này chưa được client đánh dấu là đã có.
