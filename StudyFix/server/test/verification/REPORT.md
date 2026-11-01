# Kiểm thử phần người 1 — StudyFix

Ngày 21/10/2026, khoảng 15:35–15:46 (Asia/Saigon). Kiểm thử trực tiếp trên một laptop, TCP qua `127.0.0.1`; không kiểm thử hai máy LAN.

## Kết quả chính

**184 kiểm tra PASS, 1 kiểm tra FAIL tái hiện ổn định.** Đây là số assertion/dòng kết quả, không phải 185 tình huống độc lập; có assertion kiểm tra thứ tự từng frame và một assertion bao gồm 1.000 ca diff ngẫu nhiên. Các giới hạn chưa kiểm thử được liệt kê riêng bên dưới.

| Bộ kiểm thử | Kết quả thực chạy | Bằng chứng |
|---|---|---|
| Build Ant/NetBeans, giữ `--release 26` | PASS, `BUILD SUCCESSFUL` | [ant-final.log](ant-final.log) |
| HistoryTest | 78 PASS, gồm migration, TCP nhiều frame và Swing thật | [ant-final.log](ant-final.log) |
| IntegrationTest | 40 PASS, gồm timeout thật, đọc chậm và race | [ant-final.log](ant-final.log) |
| ConsoleClientTest | 6 PASS, tiến trình `studyfix.Client` thật, server stub có ghi rõ | [ant-final.log](ant-final.log) |
| PersonOneTest mới | 57 PASS, server/SQLite/TCP thật, có console nối server thật | [person-one.log](person-one.log) |
| ServerAdminLiveTest mới | 3 PASS, cửa sổ Swing thật, dữ liệu TCP mới, nút dừng | [admin-live.log](admin-live.log) |
| ConsoleHistoryInterleaveTest mới | 1 FAIL, lỗi hiển thị lịch sử khi có thông báo bàn phím | [console-interleave.log](console-interleave.log) |

Không sửa file nghiệp vụ trong `src/`, không đổi JDK, dependency hay build tool. Chỉ bổ sung/sửa test, script và báo cáo. Không triển khai phần người 2/3.

## Môi trường và phạm vi thực chạy

- Temurin JDK **26.0.2+10**, Ant **1.10.17** đi kèm NetBeans. `javap` xác nhận bytecode `Client` có major version **70**.
- SQLite JDBC `3.53.0.0`, SHA-256 khớp `lib/README.md`: `303e8150100982f2ed7d1b82d897278ef7744bd494c28ad4e7042b7914591697`.
- Đã đọc README, build.xml, nbproject/project.properties, mã server/client/protocol/store/room/edit session/admin/gateway và test hiện hữu. Không tìm thấy AGENTS.md trong workspace hoặc các thư mục cha đã kiểm tra.
- Giao thức thực: signed int big endian 4 byte độ dài + UTF-8, tối đa 65.536 byte payload/frame. Login lấy role từ SQLite; không có lệnh tự chọn role. Tài khoản demo lấy từ README/Store; mật khẩu không ghi trong log/báo cáo này.
- `studyfix.port` và `studyfix.db` là thuộc tính cấu hình. Các test dùng `Server(0, path)` hoặc `ServerSocket(0)`, cổng riêng do hệ điều hành cấp; console con được truyền cổng đó. Cổng server thật được ghi trong từng log.
- Heartbeat server timeout 20 giây, kiểm tra mỗi giây; client ping mỗi 5 giây, watchdog pong 20 giây. Đã chờ timeout thật, không thay hằng số để rút ngắn.
- Tối đa **6 kết nối đồng thời**, **2 phòng đồng thời** trong PersonOneTest. IntegrationTest có 5 kết nối ban đầu và thêm socket đọc chậm ở pha sau. Đây không phải kết quả kiểm chứng khả năng chịu 128 client.
- Code hợp lệ tới **32.000 byte**; đã thử code/draft **32.001 byte** bị từ chối. Test diff có 16.000 dòng ngắn. Lịch sử TCP lớn đi qua **9 frame** theo log thực chạy, không dùng số 11 frame ghi từ lần chạy cũ trong README.
- Stub console dùng lịch sử **210.025 byte UTF-8**, **4 frame**, có tiếng Việt, emoji và CRLF. Gửi tối đa 500 sự kiện 60.000 ký tự ASCII tới socket ngừng đọc, dừng khi bị đóng vì queue 64 đầy; không khẳng định toàn bộ 500 sự kiện đã được nhận.
- Ant integration-test mất **75 giây**; PersonOneTest lần cuối mất **11.767 ms**. Chỉ phản ánh máy và dữ liệu lần chạy này.

Database bằng chứng lần cuối:

| Bộ test | Database / tệp bằng chứng |
|---|---|
| HistoryTest | `build/history-2984121940114339916/` chứa history.db và fixture migration |
| IntegrationTest | `build/integration-7417812834038060339/studyfix.db` |
| PersonOneTest | `build/person-one-2896515938906754881/studyfix.db`, `console-live.log`, `console-server-stop.log` |
| ServerAdminLiveTest | `build/admin-live-3282013324121615567/studyfix.db` |
| Lỗi console | `build/console-interleave-4581928369189297568.log` |

Không mở database thật bằng JDBC. SHA-256 của cả ba file đang có trong `data/` giống trước/sau; xem [database-before.txt](database-before.txt) và [database-after.txt](database-after.txt). Không dừng tiến trình Java của người dùng. Các server, socket, scheduler và tiến trình console do test tạo đều được đóng trong `finally`/try-with-resources; các JVM test đã kết thúc. Database/file thử được giữ làm bằng chứng, không còn server dùng chúng.

## Ma trận yêu cầu

PASS bên dưới là kết quả chạy thực; các nhận xét chỉ đọc mã và phần chưa chạy được ghi rõ.

| Nhóm / trường hợp | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|
| A — Login đúng | PASS | PersonOneTest/IntegrationTest đăng nhập nhiều tài khoản |
| Login sai mật khẩu / tài khoản không tồn tại | PASS | A01 |
| Chưa login không thao tác phòng | PASS | A01 và IntegrationTest |
| Role do server quyết định, không đổi danh tính trong phiên | PASS | A02, thử login tài khoản khác trong phiên đã xác thực |
| Nhiều phiên cùng tài khoản | PASS | A03: cùng userId, sessionId khác, được tính thành các thành viên phiên riêng theo chính sách README |
| Học sinh không tạo phòng / nạp toàn code | PASS | A02/A04, cả khi đang giữ quyền |
| Giáo viên khác không quản lý phòng chủ | PASS | A05: đang giữ token vẫn không `/update`; IntegrationTest chặn kick chéo phòng |
| B — Nhiều client, hai phòng đồng thời | PASS | Tối đa 6 kết nối, hai phòng |
| Chat không lẫn phòng | PASS | B05 kiểm tra không có marker chat trong queue nhận của phòng khác |
| Code/history/queue không lẫn phòng | PASS | B03, IntegrationTest và kiểm tra gateway |
| Vào/rời/vào lại không nhân đôi cùng phiên | PASS | B01/B02 |
| Join lại khi đang trong phòng bị yêu cầu leave trước | PASS | B01 |
| Người ngoài không đọc/sửa dữ liệu phòng | PASS | B04/F03 và kiểm tra API gửi sự kiện chéo phòng |
| Thông báo vào/ra đúng danh tính trên tất cả client quan sát | CHƯA TEST | Đã đọc addMember/removeMember và log rời phiên; chưa assertion toàn bộ nội dung broadcast vào/ra |
| C — Một editor, FIFO, request không trùng | PASS | C01/C07/C08 và IntegrationTest |
| Sửa dòng rời giữ quyền tới cuối lượt | PASS | C04/C07, chọn 1,3,5 và thao tác riêng |
| Finish khi chưa đủ lựa chọn bị chặn | PASS | C04 |
| `/code` vẫn đã lưu, `/draft` là preview | PASS | C05/C06 |
| Sửa/xóa/giữ rồi finish: một version/revision, chuyển quyền | PASS | C07, D01/D02, đọc cả snapshot và SQLite |
| Range thay bằng ít/nhiều dòng | PASS | C08/C09, có CRLF/LF |
| Xóa dòng trước, sửa dòng sau theo vị trí gốc | PASS | C05/C07 |
| Dòng trống khác xóa dòng | PASS | C10 |
| No-op bản nháp không tăng version/history, kết thúc lượt | PASS | C11/C16/C18, gồm keep và thay bằng nội dung giống hệt |
| Cancel không đổi code/version/history | PASS | C12 |
| Release khi còn nháp | PASS | C04: từ chối và yêu cầu finish/cancel rõ ràng |
| Sai editor/token cũ/version cũ | PASS | C03, IntegrationTest |
| Lựa chọn trùng/ngoài giới hạn/đảo range/sai cú pháp | PASS | C02/C03, gồm tràn int; trạng thái trước vẫn nguyên |
| Sửa ngoài vùng, setline có xuống dòng | PASS | C04 |
| Draft/code quá giới hạn không phá dữ liệu hợp lệ | PASS | C13/C14/C15; preview trước/sau bằng nhau khi bị từ chối |
| `/deleterange`, `/keeprange` | PASS | C16/C17 |
| D — userId, tên, thời gian, version | PASS | D01 và kiểm tra sau restart của IntegrationTest |
| Đoạn trước/sau theo dòng/vùng chọn | PASS | D02; C08/C09 xác nhận code range, lịch sử range tạo một revision |
| Metadata trước/sau range được đối chiếu riêng từng ký tự | CHƯA TEST | Chưa assertion nội dung changes của mọi loại range; đã đọc EditSession.changes |
| Toàn code giữ nguyên Unicode và newline | PASS | D01, C09/C10, HistoryTest, console live |
| Request bị từ chối không thêm history | PASS | C02–C04, C13–C15 và IntegrationTest/HistoryTest |
| Xem lịch sử không đổi trạng thái hiện tại | PASS | D07, HistoryTest so sánh toàn snapshot |
| Restart giữ phòng/code/version/history/tác giả/thời gian | PASS | D08 và IntegrationTest/HistoryTest |
| Restart không giữ online/editor/queue/draft | PASS | D09/D10, có draft chưa finish và queue trước khi dừng |
| Mã phòng mới sau restart không trùng | PASS | IntegrationTest tạo phòng thứ ba |
| Migration schema cũ, bản thiếu, mở lặp | PASS | HistoryTest, fixture riêng, mở 3 lần, giữ tài khoản/tác giả |
| Migration lỗi rollback DDL và backfill | PASS | HistoryTest trigger gây lỗi |
| Lỗi SQLite khi finish không đổi RAM, không báo commit | PASS | D03/D04 và response SAVE_FAILED; thành công chỉ sau D06 |
| Transaction rollback và nháp/quyền còn để retry | PASS | D04/D05/D06; DROP trigger thành công và retry tăng đúng một version |
| TCP lịch sử nhiều frame đầy đủ, đúng thứ tự | PASS | HistoryTest kiểm tra thứ tự/giới hạn từng frame và ghép nguyên detail |
| Console in lịch sử nhiều frame khi không có output bàn phím | PASS | ConsoleClientTest, 4 frame, chuỗi nhận đúng hoàn toàn |
| Console in lịch sử đồng thời thông báo bàn phím | FAIL | SF-01 dưới đây |
| E — Ngắt đột ngột khi có nháp | PASS | E02: bỏ nháp, code không đổi, người chờ nhận quyền |
| Leave khi có nháp | PASS | E01 |
| Timeout server khi dừng heartbeat | PASS | IntegrationTest chờ thật 20–24 giây, editor được thu hồi |
| Console heartbeat off/on và watchdog pong | PASS | ConsoleClientTest dùng tiến trình Client thật, stub ngừng pong 20 giây |
| Server thật dừng, client thật phát hiện và thoát | PASS | E05/E06 |
| Client không đọc, queue đầy, client khác vẫn hoạt động | PASS | IntegrationTest: log queue đầy, socket bị đóng, client khác tiếp tục chat |
| Request đồng thời, finish tranh chấp, release/leave | PASS | IntegrationTest: hai socket request; một finish hợp lệ và một finish sai actor; release/leave tranh chấp |
| Hai finish hợp lệ từ cùng editor đồng thời / stress lặp dài | CHƯA TEST | Chưa chạy stress hoặc chứng minh trên mọi lịch xen kẽ |
| Không hai editor/version trùng trong các race đã chạy | PASS | Snapshot một editor, một version mới trong IntegrationTest; PK SQLite(room_id,version) |
| Cleanup idempotent, callback một lần, monitor không còn | PASS | IntegrationTest và E03/E04; close được gọi lại không gây lỗi |
| Dừng giải phóng listener/socket/executor | PASS | Các test kết thúc JVM, monitor shutdown, port bind lại được; nút Swing cũng được kiểm tra |
| F — ServerAdmin snapshot và cập nhật dữ liệu thật | PASS | F04; ServerAdminLiveTest tạo phòng/update qua TCP rồi chờ GUI hiện 2 revision |
| Chọn lịch sử, nội dung chỉ đọc | PASS | HistoryTest và ServerAdminLiveTest, Unicode/CRLF |
| Nút Dừng server | PASS | ServerAdminLiveTest gọi JButton.doClick trên EDT, client rời, cổng bind lại |
| Mạng/database/snapshot chạy ngoài EDT | PASS (đọc mã) | ServerAdmin constructor dùng background.execute; snapshot trong scheduled background; đóng server trong background |
| EDT không đơ với dữ liệu lịch sử cực lớn/nhiều phòng | CHƯA TEST | showHistory/showRevision/render vẫn dựng model và chuỗi trên EDT; chưa đo độ trễ/soak/performance UI |
| Gateway identity/membership/owner/token/snapshot | PASS | A02, F01/F03 và IntegrationTest |
| Gửi private/room event, chặn chéo phòng | PASS | F02 |
| Callback rời phòng đúng một lần | PASS | IntegrationTest; callback được gọi ngoài khóa room theo đọc mã |
| Remove listener / listener ném lỗi / listener chạy chậm | CHƯA TEST | Có nhánh bắt RuntimeException trong Server.departed; chưa inject các trường hợp này |

Các trường hợp phụ chưa thực chạy: ngắt heartbeat khi có nháp (đã test riêng timeout và disconnect có nháp), login sai đủ 5 lần để đóng phiên, các dạng frame âm/truncated, kiểm thử firewall/LAN hai máy, bố cục Swing bằng mắt và hoạt động cùng module người 2/3 hoàn chỉnh. Không có mục bắt buộc nào bị BLOCKED do thiếu JDK/dependency/GUI trong lần chạy này.

## Lỗi cần xử lý

### SF-01 — P2 / Trung bình — thông báo bàn phím chen vào lịch sử nhiều frame

- Trạng thái: **FAIL**, đã tái hiện tự động; chưa sửa nghiệp vụ theo yêu cầu.
- Vị trí: `src/studyfix/Client.java`, `main`: keyboard-sender in thông báo heartbeat ở dòng 83/89; luồng nhận in từng HISTORY_PART ở dòng 149–153. Khóa nội bộ của PrintStream chỉ bảo vệ một lần print, không bảo vệ toàn phản hồi nhiều frame.
- Bước tái hiện: chạy `studyfix.ConsoleHistoryInterleaveTest`; stub xác thực console thật, gửi phần 1/4 của lịch sử 210.025 byte; đợi console in BEGIN_HISTORY; nhập `/heartbeat off`; đợi thông báo “Đã tạm dừng heartbeat”; gửi phần 2–4; so sánh đoạn lịch sử đầu ra với chuỗi gốc.
- Mong đợi: phần lịch sử nhiều frame được trình bày liền mạch, thông báo cục bộ nằm ngoài phản hồi lịch sử.
- Thực tế: thông báo bàn phím nằm giữa hai phần. Log có BEGIN_HISTORY và END_HISTORY nhưng không chứa nguyên chuỗi lịch sử. Dữ liệu SQLite và thứ tự frame trên TCP không bị mất; lỗi nằm ở việc hiển thị console. Test thoát mã 1 để giữ lỗi hiển thị rõ ràng.
- Bằng chứng: [console-interleave.log](console-interleave.log) và `build/console-interleave-4581928369189297568.log`. Lần ConsoleClientTest trước khi tách hai tình huống cũng gặp lỗi này.

### SF-02 — P3 / Thấp — README và test cũ chưa theo kịp giao thức hiện tại

- README bảng lệnh chưa mô tả đầy đủ select/setline/setrange/finish/cancel/rangefile; phần kết quả lịch sử cũ ghi số assertion/frame không còn đúng lần chạy này.
- Lần chạy nguyên trạng `ant integration-test` FAIL ở `HistoryTest.tcpTests`, do so sánh changes thiếu nhãn “Nạp toàn bộ code bởi giáo viên”. Sau đó còn kỳ vọng diff dạng `- [4]` và header lịch sử cũ; IntegrationTest vẫn dùng `/update` cho học sinh.
- Bằng chứng nguyên trạng giữ tại [ant-integration.log](ant-integration.log), lần phát hiện thêm tại [ant-current.log](ant-current.log). Đây là test/documentation lỗi thời, không phải bằng chứng server cho học sinh cập nhật toàn code.
- Đã sửa kỳ vọng test theo hành vi/quyền hiện tại: HistoryTest dùng nhãn và định dạng hiển thị mới; IntegrationTest dùng lượt nháp cho học sinh; ConsoleClientTest tách kiểm tra multipart yên lặng khỏi trường hợp xen thông báo và bổ sung `/rangefile`. Không sửa README sản phẩm, không sửa src để làm test vượt qua.

Một chính sách thực tế cần biết: **no-op `/finish` không tạo version**, đã PASS; **giáo viên `/update` cùng nội dung vẫn tạo version**, được HistoryTest xác nhận và README hiện có mô tả. Không đánh đồng hai đường lệnh. Nếu nhóm muốn no-op áp dụng cả nạp code giáo viên thì cần thống nhất lại yêu cầu và thêm regression cho đường `/update` đó.

## Chạy lại

Từ thư mục workspace, giữ JDK 26 trong PATH:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test/verification/run.ps1
```

Script dùng Ant hiện có, chạy bộ cũ, PersonOneTest, ServerAdminLiveTest và test tái hiện lỗi console. **Dự kiến exit 1 vì SF-01 còn tồn tại**; các suite khác đã PASS ở lần ghi nhận này. Log được ghi lại trong test/verification. Database mới được tạo mỗi lần dưới build; không dùng data/studyfix.db.

Chạy riêng từng bước:

```powershell
& 'C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat' integration-test
$vm = @('--enable-native-access=ALL-UNNAMED', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8')
java @vm -cp 'build/integration-classes;lib/*' studyfix.PersonOneTest
java @vm -cp 'build/integration-classes;lib/*' studyfix.ServerAdminLiveTest
java @vm -cp 'build/integration-classes;lib/*' studyfix.ConsoleHistoryInterleaveTest
```

## Nhận định trước khi ghép nhóm

Luồng chính phần người 1 đã qua kiểm thử thực: xác thực/quyền, nhiều phòng, lượt sửa bản nháp theo vị trí gốc, commit một transaction/revision, hủy/mất kết nối, SQLite rollback/retry/migration/restart, heartbeat/đọc chậm, Swing quản trị và gateway. Có thể dùng các test này làm bộ kiểm tra khi ghép nhóm.

Cần xử lý SF-01, cập nhật tài liệu SF-02 và hoàn tất các mục CHƯA TEST trong ma trận, đặc biệt stress đồng thời, metadata mọi dạng range và hiệu năng EDT khi dữ liệu lớn. Chưa có căn cứ kết luận đạt mọi trường hợp đồng thời, chịu tải hoặc mạng LAN thật.
