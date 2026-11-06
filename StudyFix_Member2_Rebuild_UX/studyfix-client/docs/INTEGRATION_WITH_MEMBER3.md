# Hợp đồng ghép module với thành viên 3 — bài tập, nộp bài, chấm, thi

## 1. Những gì source server đang có / chưa có

Server hiện tại có phiên đăng nhập, phòng, thành viên, chat, code cộng tác và lịch sử code. Source chưa có command xử lý ngân hàng bài, giao bài, bài nộp, worker chấm, kết quả test, hạn thi hoặc bảng xếp hạng. Vì thế client chưa gọi `/exercise`, `/submit` hay `/contest` như thể chúng đã được hỗ trợ.

Người 3 có thể dựng panel UI và mô hình dữ liệu độc lập; nhưng để test end-to-end, người 1 phải đồng ý một hợp đồng protocol/server implementation. Chỉ dựng JPanel không hoàn thành nghiệp vụ.

## 2. API client dành sẵn cho người 3

Package: `vn.studyfix.client.integration`

### `ClientModule`

```java
public interface ClientModule {
    String tabTitle();
    JComponent view();
}
```

Mỗi module trả về tên tab và root Swing component. Module không tự tạo `JFrame` riêng trừ các dialog nhỏ; như vậy nó gắn vào MainFrame cùng account/session/room.

### `ClientModuleContext`

```java
SessionInfo session();
RoomState roomState();
void sendControlCommand(String command);
void addMessageListener(Consumer<ServerMessage> listener);
void addStateListener(Runnable listener);
void reportClientError(String message);
```

- `session()` là user đã login; không có password.
- `roomState()` cho biết room hiện tại, role/session được biết, code/version và danh sách thành viên.
- `sendControlCommand()` gửi qua đúng socket điều khiển lâu dài; không tạo reader/socket mới.
- `addMessageListener()` nhận tất cả message đã parse nhưng luôn còn `raw()`, nên module có thể parse marker riêng do người 1 bổ sung.
- `addStateListener()` được thông báo khi room state đổi; cập nhật Swing bằng EDT (các callback hiện được route từ MainFrame trên EDT).
- `reportClientError()` đưa lỗi vào log UI, không làm UI biến mất.

Hiện có `ClientModuleContextAdapter` để bọc controller. Tab `Bài tập / Nộp bài` đang là placeholder nêu rõ API server còn thiếu.

## 3. Cách người 3 tích hợp code

1. Tạo package riêng, ví dụ `vn.studyfix.client.exercise`.
2. Tạo `ExerciseModule implements ClientModule`, trong constructor nhận `ClientModuleContext`.
3. Tách panel theo nghiệp vụ: `ExerciseCatalogPanel`, `AssignmentPanel`, `SubmissionPanel`, `ResultPanel`, `ContestPanel`, `LeaderboardPanel`.
4. Phần dữ liệu không đặt trong Swing panel. Tạo `ExerciseService`, `SubmissionService`, `ContestService` để gửi request và chuyển server response thành DTO.
5. Không thêm `new Socket(...)` hoặc `DataInputStream` trong module 3. Mọi lệnh control đi qua context để giữ một reader/writer duy nhất.
6. Khi người 1 chốt command + response markers, cập nhật `ServerMessageParser` để nhận loại message mới (ví dụ `[EXERCISE]`, `[SUBMISSION]`, `[RESULT]`, `[CONTEST]`, `[LEADERBOARD]`) và vẫn giữ raw.
7. Đổi đúng một dòng tạo module trong `MainFrame` từ `new ExerciseIntegrationPanel(new ClientModuleContextAdapter(controller))` sang `new ExerciseModule(new ClientModuleContextAdapter(controller))`. Field đã khai báo theo kiểu interface `ClientModule`; tab được thêm bằng `tabs.addTab(exerciseModule.tabTitle(), exerciseModule.view())`. Không cần sửa login/room/network core.

## 4. Hợp đồng server cần chốt trước khi viết service end-to-end

Đây là **đề xuất để các thành viên thống nhất**, không phải protocol hiện tại:

| Use case | Request cần định nghĩa | Response/event cần định nghĩa |
|---|---|---|
| Liệt kê bài tập | list/filter/page | danh sách ID, title, language, difficulty; marker hoặc schema versioned |
| Xem đề | exerciseId | mô tả, mẫu input/output, giới hạn, visibility |
| Giao bài chung/cá nhân | exerciseId, roomId, recipient scope, deadline | assignmentId, accepted/rejected, danh sách người nhận |
| Nộp bài | assignmentId, source, submissionId/idempotency | receipt có submissionId/serverTime; không coi gửi socket thành công là nộp thành công |
| Chấm bài | được kích hoạt bởi server khi lưu submission | queued/running/finished, compile result, test counts, time/memory, feedback |
| Xem lịch sử nộp | assignmentId / current user | danh sách lần nộp và kết quả; quyền teacher/student được server kiểm |
| Thi | contestId, start/end, assigned problems | trạng thái thời gian server authoritative |
| Leaderboard | contestId | điểm, penalty/tiebreak, rank theo rule đã công bố |

**Khuyến nghị:** người 1 và người 3 chốt tối thiểu tên command, format request/response, giới hạn size, phân quyền, version schema, trạng thái bất đồng bộ và event broadcast trước khi người 3 gắn nút vào UI.

## 5. Bảo mật và nguyên tắc nghiệp vụ

- Không tin `role`, `userId`, điểm hoặc quyền chỉ vì client gửi lên; server phải lấy identity từ authenticated session.
- Submission lưu phải được server gắn với userId từ phiên, không dùng userId tự khai trong payload.
- Chỉ server/worker trả kết quả chấm chính thức; client chỉ render.
- Chạy code người nộp trong worker/container riêng, không dùng `Runtime.exec` không giới hạn trong Swing client hoặc server thread.
- Riêng kết quả cá nhân: định tuyến đúng người nộp/giảng viên có quyền, không broadcast code/result riêng sang cả phòng.
