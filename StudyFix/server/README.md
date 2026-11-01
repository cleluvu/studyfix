# StudyFix — phần người 1

JDK 26, TCP nhiều client/phòng, SQLite, Swing quản trị server và relay chia sẻ màn hình qua TCP riêng. Phần relay, ngân hàng bài tập, worker chấm, thi và bảng xếp hạng đã được tích hợp. Để mở GUI chạy cả ba listener, dùng `run-server-manager.ps1` theo hướng dẫn ở `../RUN_IN_VSCODE_VI.md`.

## Khởi chạy đủ chức năng kèm giao diện Swing

Trong VS Code, mở Terminal PowerShell tại thư mục `server`, đặt `JAVA_HOME`/`ANT_HOME`, rồi chạy `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force` và `.\run-server-manager.ps1`. Script mở cửa sổ Server Manager và chạy cả cổng 5000/5001/5002 trong cùng JVM. Không chạy script này đồng thời với `run-with-assignments.ps1`.

`run-with-assignments.ps1` vẫn được giữ lại để chạy chế độ console không có GUI.

## Build và chạy

Mở thư mục này bằng NetBeans, chọn JDK 26, **Clean and Build**, **Run Project** để mở quản trị server. `Studyfix.main` gọi `ServerAdmin`. Không mở thêm server console trên cùng cổng khi cửa sổ quản trị đang chạy.

PowerShell, từ thư mục `studyfix`:

```powershell
# Nếu ant đã có trong PATH:
ant jar
# Hoặc dùng Ant đi kèm NetBeans trên máy hiện tại:
$ant = (Get-Command ant.bat -ErrorAction SilentlyContinue).Source
if (-not $ant -and $env:ANT_HOME) { $ant = Join-Path $env:ANT_HOME 'bin\ant.bat' }
if (-not $ant) { $found = Get-ChildItem (Join-Path $env:USERPROFILE 'Downloads') -Filter ant.bat -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1; if ($found) { $ant = $found.FullName } }
if (-not $ant) { throw 'Không tìm thấy ant.bat.' }
& $ant jar

# Cửa sổ quản trị (khởi động server ở background):
java -jar dist/studyfix.jar
# Server console, thay cho cửa sổ quản trị:
java -cp 'dist/studyfix.jar;dist/lib/*' studyfix.Server
# Mỗi terminal là một client test:
java -cp 'dist/studyfix.jar;dist/lib/*' studyfix.Client
```

Để chạy ngoài NetBeans với đúng VM Options hiện có, có thể dùng mảng PowerShell:

```powershell
$vm = @('--enable-native-access=ALL-UNNAMED', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8')
java @vm -jar dist/studyfix.jar
# Hoặc server console với database thử riêng (không chạy cùng ServerAdmin):
java @vm '-Dstudyfix.db=data/manual-history-test.db' -cp 'dist/studyfix.jar;dist/lib/*' studyfix.Server
# Terminal khác:
java @vm -cp 'dist/studyfix.jar;dist/lib/*' studyfix.Client
```

Cổng điều khiển mặc định **5000**, relay chia sẻ màn hình mặc định **5001** (cấu hình bằng `-Dstudyfix.screen.port=...`); hai listener đều lắng nghe các địa chỉ mạng. Database mặc định **`data/studyfix.db` tính từ thư mục chạy lệnh**. Dùng cùng thư mục làm việc hoặc chỉ định đường dẫn tuyệt đối để luôn mở đúng database:

```powershell
java '-Dstudyfix.port=5001' '-Dstudyfix.db=D:/Laptrinhmang/studyfix/data/studyfix.db' -cp 'dist/studyfix.jar;dist/lib/*' studyfix.Server
java '-Dstudyfix.port=5001' -cp 'dist/studyfix.jar;dist/lib/*' studyfix.Client
```

JDK mới có thể cảnh báo về native access của SQLite; có thể thêm `--enable-native-access=ALL-UNNAMED` trước `-cp` hoặc `-jar`. Thư viện đã có trong `lib` và được Ant chép vào `dist/lib`; xem [nguồn, phiên bản, checksum](lib/README.md). Project và test dùng `--release 26` trên JDK 26.0.2. Cấu hình trước đây có source/target 17 dù runtime là JDK 26; đã thống nhất `javac.source=26`, `javac.target=26`, `javac.release=26`. Hook compiler trong `build.xml` truyền thuộc tính `release` cho Ant javac, không sửa file sinh tự động `nbproject/build-impl.xml`. Giữ nguyên VM Options native access và UTF-8 trong NetBeans.

## Tài khoản demo

Tạo khi khởi động database lần đầu; không ghi đè tài khoản đã có. Client nhập IP, username và password. Console test có thể hiện ký tự mật khẩu khi gõ.

| Username | Password demo | Vai trò |
|---|---|---|
| teacher | Teacher123! | TEACHER |
| teacher2 | Teacher234! | TEACHER |
| student1 | Student123! | STUDENT |
| student2 | Student234! | STUDENT |

Server quyết định vai trò từ database. Mỗi tài khoản có UUID `userId`, username duy nhất, tên hiển thị, vai trò, hash PBKDF2-HMAC-SHA256 (600.000 vòng, salt ngẫu nhiên 16 byte, output 256 bit). Client không có lệnh đăng ký/chọn role/đổi danh tính trong cùng phiên. Sau 5 lần đăng nhập sai, server đóng phiên. Có thể đăng nhập cùng tài khoản ở nhiều kết nối để test; mỗi kết nối có UUID `sessionId` riêng.

TCP hiện chưa có TLS; tài khoản và code đi qua LAN dưới dạng UTF-8. Các tài khoản trên dành cho demo trong mạng thực hành tin cậy. Chưa có quy trình tạo tài khoản thật, đổi mật khẩu hoặc tự reconnect.

## Lệnh console

| Lệnh | Chức năng |
|---|---|
| `/create` | Giáo viên tạo phòng thuộc tài khoản mình và vào phòng đó |
| `/join mã_phòng` | Tham gia phòng đã lưu, cần `/leave` phòng hiện tại trước |
| `/members` | Tên và sessionId các thành viên của phòng hiện tại |
| `/chat nội_dung` | Chat trong phòng, tối đa 2.000 ký tự Java |
| `/code` | Code hiện tại, version, editor, token và hàng đợi |
| `/request` | Xin quyền sửa, xếp hàng FIFO nếu đang có người sửa |
| `/release token` | Người giữ lượt trả quyền, chuyển cho người tiếp theo |
| `/update version token code` | Gửi code với version/token hiện tại |
| `/load version token đường_dẫn` | Client đọc file UTF-8, gửi `/update`; hỗ trợ đường dẫn có khoảng trắng, không thêm dấu nháy bao đường dẫn |
| `/history` | Danh sách revision của phòng hiện tại |
| `/history version` | Xem nội dung bản cũ, không đổi code hiện tại |
| `/kick sessionId` | Giáo viên chủ phòng đóng kết nối của thành viên trong phòng mình; không tự kick mình |
| `/leave` | Rời phòng, xóa khỏi hàng đợi, chuyển quyền nếu đang giữ |
| `/heartbeat off` | Client ngừng gửi ping để kiểm tra timeout server |
| `/heartbeat on` | Bật lại ping, cần thực hiện trước khi phiên bị đóng |
| `/quit` | Đóng client |

Chỉ người giữ quyền và có đúng token/version mới được cập nhật. Code phải khác rỗng, tối đa **32.000 byte UTF-8**. Ví dụ sau `/request`, đọc version/token hiển thị rồi dùng `/load 0 1 D:\bai tap\Main.java` (thay số bằng trạng thái thực tế).

## Lưu trữ và đồng thời

- Các bảng `users`, `rooms`, `revisions`. Cập nhật code/version và thêm revision cùng transaction. Sau commit mới đổi RAM và broadcast thành công. Lỗi lưu trả `[SAVE_FAILED]`. Phòng mới và revision 0 cũng được tạo cùng transaction; ID phòng do SQLite AUTOINCREMENT cấp.
- Kết quả commit được tách khỏi bước dọn JDBC: nếu commit đã thành công nhưng khôi phục autocommit lỗi, vẫn công bố code/phòng đã lưu và thay connection, tránh trả lỗi giả hoặc để RAM lệch database. Connection mới bật lại foreign keys và busy timeout. Nếu không mở lại được, các thao tác database tiếp theo bị từ chối rõ ràng cho đến khi khởi động lại server. Nếu rollback lỗi, bỏ connection cũ; không bật autocommit trên transaction chưa rollback vì có thể vô tình commit phần ghi dở.
- Revision bất biến: version, userId người sửa, tên tại thời điểm sửa, Instant UTC, phiên bản trước, phần thay đổi, toàn bộ code. Hiển thị lịch sử ở Asia/Ho_Chi_Minh. Revision hệ thống dùng `SYSTEM`; không suy ra tài khoản theo tên hiển thị. Lịch sử RAM của bản cũ đã mất khi tắt server thì không thể khôi phục từ phiên bản này.
- Khởi động lại nạp code/version/history/owner; thành viên, socket, editor, token và hàng đợi bắt đầu rỗng. Lưu cả phòng không còn ai online. Một server sở hữu một database; không chạy nhiều tiến trình server trên cùng file.
- Khóa theo phòng bao quanh xin/trả quyền, cập nhật, join/leave và snapshot. Mỗi socket có reader và writer riêng. Broadcast chỉ đưa vào hàng đợi **64 phản hồi**, không chờ mạng khi giữ khóa phòng. Nếu đầy, ghi log và đóng client để tránh bỏ lặng lẽ sự kiện rồi cho client tiếp tục với dữ liệu thiếu; khi kết nối lại phải join/lấy snapshot mới.
- Giới hạn 128 kết nối, tối đa 256 worker, tối đa 64 KiB/frame. Heartbeat mỗi 5 giây; server timeout ping 20 giây, client timeout pong 20 giây. Dùng thời gian đơn điệu. Đóng socket idempotent; reader dọn phòng đúng một lần và hủy monitor. Server dừng listener, đóng các kết nối, dừng monitor/worker và đóng database.
- Database lưu toàn bộ code mỗi revision; hiện nạp toàn bộ lịch sử vào RAM, chưa có phân trang database/dọn revision cũ. Chat, log quản trị (500 dòng gần nhất), quyền và hàng đợi không lưu bền vững.

## Quản trị server

Hiện trạng thái/cổng/số kết nối, các phòng, owner, thành viên, editor, hàng đợi, version. Chọn phòng rồi chọn revision để xem tác giả, thời gian, phiên bản trước → sau, diff và toàn bộ code chỉ đọc; tab nhật ký hiện hoạt động gần đây. Snapshot bất biến được lấy mỗi giây ở background rồi chuyển sang EDT để hiển thị. Khởi động/database/đóng server đều chạy ngoài EDT. Nút **Dừng server** dọn tài nguyên; đóng cửa sổ cũng dừng server. Muốn chạy lại sau khi dừng, mở lại ứng dụng.

Sau Dừng, quản trị lấy snapshot cuối cùng ở background rồi cập nhật bảng: số kết nối về 0, xóa thành viên/editor/hàng đợi, giữ code và lịch sử để xem. Các lượt refresh đã xếp trước lúc dừng không được ghi đè snapshot cuối cùng. Nhật ký hiển thị cả sự kiện server đã dừng.

## Điểm ghép cho người 2/3

`server.gateway()` trả `StudyFixGateway`:

- `identity(sessionId)` → `Identity(userId, username, displayName, role)` của phiên đã đăng nhập.
- `isMember(sessionId, roomId)`, `isTeacher(sessionId, roomId)` (giáo viên **chủ** phòng), `canEdit(sessionId, roomId, token)`.
- `codeSnapshot(sessionId, roomId)` → snapshot bất biến code/version/history và trạng thái phòng, kiểm tra membership.
- `sendToUser(actorSessionId, recipientSessionId, roomId, event)`, `sendToRoom(actorSessionId, roomId, event)` kiểm tra cùng phòng. Đây là API nội bộ server; không tin actor ID tự khai từ payload client.
- `addDepartureListener` / `removeDepartureListener`: callback gồm session, identity, room, lý do. Callback chạy trên worker rời phòng, ngoài khóa Room; phải trả về nhanh, tự chuyển sang executor của module nếu xử lý lâu. Bắt exception callback để không làm hỏng việc dọn phiên.

`[LOGIN_OK]` trả cả sessionId/userId/role cho console test. Một tài khoản có thể có nhiều phiên; lịch sử dùng userId, quyền sửa dùng sessionId. Chỉ `/join` nhận mã phòng từ client; các thao tác phòng dùng phòng đang gắn với kết nối. Không có lệnh TCP để tự chọn userId/role hay sửa phòng bất kỳ.

Wire protocol tiếp tục dùng **4 byte signed big endian độ dài payload + UTF-8**, tối đa 65.536 byte. Các lệnh là chuỗi text, `/update` giữ phần nội dung nhiều dòng. Ping `/ping`, pong `/pong`. Đây là phiên bản giao thức yêu cầu `/login username password` thay cho `/name`; người 2 cần dùng bước login này trước các lệnh phòng. Event text hiện hữu chưa phải JSON schema. Server có thêm lệnh cấp ticket và relay màn hình; vẫn chưa triển khai bộ chấm, bài tập, thi và điểm.


## Chia sẻ màn hình (đã tích hợp module server)

- Client đã xác thực và đang ở trong phòng gửi `/screen-ticket publish` để chia sẻ hoặc `/screen-ticket view` để xem.
- Server trả `[SCREEN_TICKET] mode=PUBLISH|VIEW room=<id> port=<port> ticket=<token>`; ticket ngẫu nhiên chỉ dùng một lần và hết hạn sau 30 giây.
- Client kết nối TCP riêng đến cổng relay (mặc định **5001**) và gửi packet `HELLO` chứa ticket. Relay xác minh ticket và tư cách thành viên phòng trước khi chấp nhận.
- Tối đa một publisher cho mỗi phòng. Chỉ publisher gửi packet `FRAME`; relay chỉ chuyển JPEG cho viewer đã xác thực trong cùng phòng. Hàng đợi ở mỗi viewer chỉ giữ frame mới nhất để giảm độ trễ; giới hạn packet là 2 MiB, tốc độ relay tối đa 10 frame/giây.
- Packet dùng 4 byte big-endian độ dài + 1 byte loại packet + payload. Các loại gồm `HELLO`, `FRAME`, `STOP`, `ERROR`, `STATUS`. Giao thức này tách biệt với kênh điều khiển 5000.
- Khi phiên control rời phòng/mất kết nối, relay đóng peer liên quan. Khi server dừng, relay đóng listener và peer.
- Giao thức relay hiện dành cho mạng LAN tin cậy; chưa có TLS/mã hóa payload ảnh. Không mở cổng này ra Internet công cộng nếu chưa bổ sung bảo mật vận chuyển.

### Test relay

`ant integration-test` có thêm `ScreenShareRelayIntegrationTest`: khởi động server/SQLite trên cổng loopback ngẫu nhiên, kiểm tra từ chối ticket ngoài phòng, cấp ticket publish/view, handshake hai đầu, relay packet `FRAME` từ publisher tới viewer, chỉ một publisher mỗi phòng, ticket không thể dùng lại và dừng stream. Bài test tạo một ảnh JPEG thật bằng `ImageIO`, chuyển packet `FRAME` qua relay TCP và xác nhận viewer giải mã được ảnh; nó không thay thế việc kiểm tra giao diện Swing trên Windows và kết nối hai máy LAN.

## Kiểm thử trên một máy

```powershell
& $ant integration-test
```

Target biên dịch cả source/test bằng `--release 26`, chạy TCP thật với cổng ngẫu nhiên trên **127.0.0.1**, SQLite thật trong `build/integration-*/studyfix.db`, rồi chạy client console thực tế bằng tiến trình Java con. Thời gian khoảng một phút vì có kiểm tra timeout thật. Test tự đóng server/socket/executor; database test được giữ lại để kiểm tra, không chạm `data/studyfix.db`.

Target cũng chạy `RecoveryTest` (database riêng `build/recovery-*`): chèn lỗi JDBC sau commit khi cập nhật code và tạo phòng; đối chiếu RAM/database/history; kiểm tra connection thay thế vẫn bật foreign keys và tiếp tục cập nhật được; lỗi rollback không commit dữ liệu dở. Phần Swing chạy với hai client thật, bấm Dừng, kiểm tra bảng/status/log cuối cùng, giữ lịch sử và chặn snapshot cũ ghi đè; chỉ bỏ qua riêng kiểm tra Swing nếu môi trường headless.

Các tình huống: nhiều client/hai phòng; chưa đăng nhập/sai mật khẩu/giả tên; học sinh không tạo phòng; owner và giáo viên phòng khác; chat Unicode; token sai/version cũ/code rỗng/quá dài; đúng userId/tên/thời gian/lịch sử; xem bản cũ không đổi code; trigger SQLite cố ý làm INSERT revision lỗi sau UPDATE để xác nhận rollback cả database và RAM; xếp hàng/chuyển quyền; trả quyền đồng thời rời phòng; hai client gửi cập nhật; disconnect/callback một lần; socket ngừng đọc làm đầy hàng đợi nhưng phòng vẫn phản hồi; heartbeat 20 giây; hủy monitor; frame sai; dừng/khởi động lại; ID không trùng; hash và salt; `/load` UTF-8 nhiều dòng với đường dẫn có khoảng trắng; `/heartbeat off/on`; server giả lập ngừng pong khiến client thoát và hủy executor.

Test thủ công bổ sung: mở quản trị, mở 3 terminal client (teacher/student1/student2); tạo/join cùng phòng, chat, `/request` cả ba; cho người giữ quyền cập nhật rồi `/leave`; chọn revision cũ trong quản trị; tắt một client; kiểm tra log và chuyển quyền. Nhấn Dừng server khi client còn kết nối, kiểm tra tất cả thoát, sau đó mở lại và join mã phòng cũ để xem code/history còn nguyên. Giao diện Swing cần kiểm tra trực quan trên máy người dùng; các test tự động không xác nhận bố cục cửa sổ.

## Hướng dẫn test LAN sau này — CHƯA THỰC HIỆN

1. Hai máy cùng mạng LAN; chạy server trên máy A, lấy IPv4 LAN bằng `ipconfig`. Cho phép Java/TCP 5000 trong Windows Firewall với mạng Private phù hợp.
2. Máy B chạy client, nhập IPv4 của máy A; máy A có thể chạy thêm client `127.0.0.1`. Không nhập `127.0.0.1` trên B để kết nối A.
3. Đăng nhập teacher trên A và student trên B; teacher tạo phòng, student join mã đó. Kiểm tra chat hai chiều, code Unicode, queue, token và history.
4. Tạo phòng thứ hai, bảo đảm chat/code/history không lẫn. Rút mạng B để kiểm tra dọn phiên/chuyển quyền sau timeout; mở lại client để join lại.
5. Dừng/khởi động lại server A dùng cùng đường dẫn database; join lại và xác nhận code/history, không còn editor/queue cũ.

Kết quả localhost không được xem là đã kiểm chứng firewall, độ trễ hoặc mất mạng trên LAN thật.

## Lịch sử thay đổi code (người 1)

- `/history` giữ danh sách tóm tắt; `/history 1` hiển thị userId/tên tại lúc sửa, thời gian UTC+07:00, `Phiên bản 0 → 1`, diff và toàn bộ code bản 1. Bản 0 ghi **Code khởi tạo**. Xem bản cũ không thay đổi code, version, editor, token hay hàng đợi.
- Server lấy danh tính từ phiên đăng nhập, tự tạo thời gian/version và tính diff từ code hiện tại. Client chỉ gửi version kỳ vọng, token và code; không gửi tác giả/diff/version kết quả. Giữ nguyên kiểm tra quyền và khóa theo phòng.
- Chính sách nội dung giống hệt: yêu cầu hợp lệ vẫn tạo phiên bản kế tiếp như trước đây, lưu **Không có thay đổi nội dung**. Sai quyền/token/version, code chỉ chứa khoảng trắng hoặc vượt 32.000 byte không tạo lịch sử.
- Dòng xóa dùng `- [số dòng cũ]`, dòng thêm dùng `+ [số dòng mới]`; từng vùng có vị trí `@@`. Nhãn ⟦LF⟧, ⟦CRLF⟧, ⟦CR⟧, ⟦không xuống dòng⟧ làm rõ dòng trống, kiểu xuống dòng và cuối file. Nhãn chỉ nằm trong diff; toàn bộ code phía sau giữ nguyên nội dung gốc, kể cả khoảng trắng/dòng trống đầu file.
- Thuật toán bỏ tiền tố/hậu tố chung, dùng LCS tối đa 1.000.000 ô. Vùng lớn dùng Myers giới hạn 4.000.000 số nguyên trace (~16 MB) và 2.000.000 bước. Nếu vượt ngân sách, ghi rõ và hiển thị thay thế toàn vùng giữa tiền tố/hậu tố chung: đúng nội dung nhưng có thể chứa cả dòng không đổi trong vùng, không đảm bảo diff tối thiểu. Không tạo ma trận không giới hạn; diff chạy trên worker hoặc khi mở Store ở background, không chạy trên Swing EDT.
- Mỗi phản hồi lịch sử là một mục trong hàng đợi 64 phản hồi. Writer chia theo **byte UTF-8**, tối đa 65.536 byte/frame kể cả header; không tách surrogate pair. Phản hồi dài có header `[HISTORY_PART i/n]` và dấu LF. Writer gửi liên tiếp các phần trước phản hồi kế tiếp, tránh xen pong/chat. Console client bỏ header rồi in liên tiếp, chỉ kết thúc sau phần cuối. Interface người 2/3 giữ nguyên; client khác muốn dùng lịch sử dài cần xử lý header này. Giao thức nền vẫn 4 byte big endian + UTF-8.
- Trong lúc đang in lịch sử nhiều frame, client hoãn thông báo local (heartbeat, lỗi đọc file, hướng dẫn lệnh...) đến sau phần cuối. Thao tác bàn phím và gửi lệnh vẫn tiếp tục; không giữ khóa hiển thị trong lúc chờ socket. Nếu kết nối kết thúc giữa chừng, client đánh dấu lịch sử chưa nhận đủ và in các thông báo đang chờ trước khi thoát.

### Nâng cấp SQLite tự động

Khi mở Store, đọc `PRAGMA table_info(revisions)`, bổ sung cột nullable `previous_version INTEGER` và `changes TEXT` nếu thiếu. DDL và backfill cùng transaction; lỗi thì rollback. Chỉ backfill bản ghi có `changes IS NULL`; mở lại nhiều lần không ghi lại dữ liệu đã nâng cấp. Không xóa bảng, không yêu cầu xóa database.

Bản 0 lưu nhãn khởi tạo. Với bản v có bản v−1 trong **cùng phòng**, tính diff từ hai snapshot đã lưu. Thiếu v−1 ghi **Không đủ dữ liệu đối chiếu: thiếu phiên bản trước**; không lấy code hiện tại thay thế, không tạo bản thiếu hoặc đoán tác giả. Giữ nguyên userId/tên/thời gian/code/version cũ và tài khoản/phòng. Cập nhật mới lưu code/version của phòng, snapshot và diff trong cùng transaction; chỉ sau commit mới đổi RAM và broadcast.

### Kiểm thử lịch sử

`ant integration-test` chạy thêm `HistoryTest`: diff vòng lặp; dựng lại code từ diff cho thêm/xóa/thay/nhiều vùng/dòng lặp/trống; 1.000 ca ngẫu nhiên; Unicode và dấu xuống dòng; 32.000 byte nhiều dòng ngắn; TCP nhiều frame và thứ tự; no-op; restart; migration schema cũ có bản thiếu, mở lại ba lần, lỗi migration rollback; giữ tác giả/tài khoản. Kiểm thử Swing mở ServerAdmin với database thử, chọn bản 0/1 và kiểm tra JTextArea chỉ đọc có metadata/diff/code. Môi trường headless ghi SKIP cho riêng kiểm tra Swing. ConsoleClientTest chạy client thật, kiểm tra ghép Unicode nhiều frame và heartbeat.

Target còn chạy `ConsoleHistoryInterleaveTest` để ngăn lỗi F11 tái phát: sau frame đầu, client bật/tắt heartbeat và xử lý lệnh local sai; lệnh xác nhận qua socket chứng minh bàn phím không bị chặn. Lịch sử Unicode 210.025 byte qua 4 frame phải liền mạch, các thông báo local chỉ xuất hiện sau phần cuối. Test cũng ngắt kết nối giữa lịch sử để kiểm tra thông báo thiếu dữ liệu, xả thông báo đang chờ và thoát sạch.

Mọi database thử nằm trong `build/history-*/` hoặc `build/integration-*/`; không mở/sửa `data/studyfix.db` khi chạy test. Migration trên database thật chỉ xảy ra khi người dùng khởi động server với file đó. Vẫn còn giới hạn: lịch sử được nạp hết vào RAM, chưa phân trang/dọn phiên bản; kết quả diff có thể dài hơn code do số dòng và nhãn. Chưa xác minh LAN nhiều máy hoặc bố cục Swing bằng mắt.

Kết quả thực tế ngày 21/10/2026 trên Temurin 26.0.2: **BUILD SUCCESSFUL**, HistoryTest **86** kiểm tra (gồm 1.000 ca diff ngẫu nhiên), IntegrationTest **40**, ConsoleClientTest **5**. Đã mở ServerAdmin bằng kiểm thử Swing, chọn bản lịch sử và xác nhận nội dung chỉ đọc; không bỏ qua kiểm tra vì headless. Phản hồi chi tiết lớn đã đi qua **11 frame** trên TCP, giữ đúng thứ tự và code; client console thật cũng ghép đúng Unicode. Trigger SQLite gây lỗi ghi revision xác nhận rollback database/RAM; trigger gây lỗi migration xác nhận rollback cả DDL/backfill. Bytecode build có major version **70** (Java 26).
