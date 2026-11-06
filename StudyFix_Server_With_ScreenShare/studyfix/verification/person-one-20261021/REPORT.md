# StudyFix — nghiệm thu phần người 1

Chỉ kiểm tra, **không sửa mã nghiệp vụ, README hay cấu hình NetBeans/Ant**. Đối chiếu SHA-256 source/config: 17 file, 0 file thay đổi. Không triển khai GUI client, màn hình, bộ chấm, bài tập/thi/điểm; code được lưu thành công không chứng minh biên dịch hoặc giải bài đúng. `Room.INITIAL_CODE` có ví dụ vòng lặp sai; đây là dữ liệu bài thực hành, không tính là lỗi chức năng người 1.

## Môi trường, cách chạy và cách đọc báo cáo

- Windows/PowerShell, Temurin **JDK 26.0.2+10**, cấu hình NetBeans/Ant hiện tại giữ `javac.release/source/target=26`. Ant đi kèm NetBeans; SQLite JDBC 3.53.0.0. Bytecode build major 70; checksum dependency tại [dependency.csv](dependency.csv).
- Build sạch với `ant -Dbuild.dir=verification/person-one-20261021/build -Ddist.dir=verification/person-one-20261021/dist clean jar`. Chỉ clean build/dist do audit sở hữu; không clean build mặc định hoặc đụng database thật.
- Tất cả client đến **127.0.0.1**; server dùng cổng động 0 rồi đọc cổng thực tế. Các cổng và file SQLite được ghi trong `FIXTURE`, `TEST_DATABASE`, `RECOVERY_DATABASE_DIR`, `TEST_DATABASE_DIR` và `PACKAGED_RUNTIME` của log. Không mở/sửa/reset `data/studyfix.db`. Các fixture mới nằm trong `verification/person-one-20261021/fixture-*`, `packaged-*`, `port-conflict-*`, `same-port-*`; bộ test sẵn dùng `build/history-*`, `build/recovery-*`, `build/integration-*`, `build/person-one-*`, `build/admin-live-*` với tên ngẫu nhiên riêng.
- Client test đọc đủ frame, ghép multipart theo thứ tự/code point, reader riêng và chờ sự kiện/điều kiện với timeout. Lấy version/token từ snapshot thực tế. Chỉ bài kiểm tra tiêu cực mới cố ý gửi version/token sai. Các mốc thời gian nghỉ nhỏ trong vòng polling không được dùng riêng làm bằng chứng PASS. Bài chat isolation trong harness mới dùng mốc ping/pong có thứ tự, không phụ thuộc `sleep(100)` của test cũ.
- Mã A01–I07 dưới đây là mã yêu cầu của bạn. Mã A/B/C... bên trong `PersonOneTest` là mã nội bộ cũ, **không ánh xạ máy móc**; báo cáo đối chiếu cả mô tả assertion. `PASS` của một helper tái hiện lỗi không có nghĩa sản phẩm không lỗi; trạng thái yêu cầu lấy theo assertion chức năng cuối cùng.
- `CHẠY`: đã thực thi trên server/socket/SQLite/Swing thật hoặc stub có nhãn rõ; `RÀ SOÁT MÃ`: kết luận từ source, không thay thế kiểm thử trực quan/hiệu năng. GUI chạy tự động qua Swing EDT và component thực, chưa đánh giá bố cục bằng mắt. Stub chỉ phục vụ giao thức/pong/console, không giả lập người 2/3.
- Không ghi mật khẩu/payload login vào log hoặc báo cáo. Script dùng tài khoản demo đã có để kết nối; mật khẩu không được echo. Không đụng tiến trình Java/NetBeans có sẵn của người dùng.

## Tổng kết 109 mã yêu cầu

**PASS: 108 — FAIL: 1 — BLOCKED: 0 — CHƯA TEST: 0 — Tổng: 109.**

Build sạch thành công. Bộ test chuẩn: RecoveryTest **22**, HistoryTest **78**, IntegrationTest **40**, và **6 assertion console** đều PASS. PersonOneTest **57 PASS, 0 FAIL**. ServerAdminLiveTest PASS. Các assertion/mã của các bộ có thể kiểm tra trùng chức năng; không cộng tổng assertion này thành số mã yêu cầu. Một kiểm tra bổ sung `ConsoleHistoryInterleaveTest` FAIL xác định lỗi AUD-01.

## Ma trận từng mã test

| Mã | Yêu cầu | Kết quả | Loại bằng chứng | Bằng chứng cụ thể |
|---|---|---|---|---|
| A01 | Build sạch toàn project bằng cấu hình hiện có. | **PASS** | CHẠY | Ant clean jar với build/dist riêng; toàn bộ source dùng release 26. [build.log](build.log) |
| A02 | Kiểm tra dependency JDBC SQLite và classpath runtime. | **PASS** | CHẠY | JAR đóng gói đã mở SQLite thật qua classpath dist/lib; checksum tại dependency.csv. [packaged.log](packaged.log) |
| A03 | Khởi động server trên cổng và database thử. | **PASS** | CHẠY | Server JVM riêng chạy cổng động, database packaged-*/server.db. [packaged.log](packaged.log) |
| A04 | Chạy client Java console thực tế, đăng nhập và gửi lệnh. | **PASS** | CHẠY | Client Java đóng gói thật login/create/request/code/members/quit trên 127.0.0.1. [packaged.log](packaged.log) |
| A05 | Khởi động server thứ hai cùng cổng: báo lỗi rõ, không làm ảnh hưởng server đang chạy. | **PASS** | CHẠY | JVM server thứ hai cùng cổng báo BindException; server đầu vẫn trả pong. [packaged.log](packaged.log) |
| A06 | Dừng rồi chạy lại server: cổng được giải phóng. | **PASS** | CHẠY | Dừng tiến trình server do audit tạo, JVM mới mở lại cùng cổng/database. [packaged.log](packaged.log) |
| B01 | Đăng nhập đúng thông tin. | **PASS** | CHẠY | Real logins, two teacher-owned rooms, server-side member identities [full-audit.log](full-audit.log) |
| B02 | Sai mật khẩu và tài khoản không tồn tại. | **PASS** | CHẠY | Wrong password and unknown account rejected [full-audit.log](full-audit.log) |
| B03 | Chưa đăng nhập gửi lệnh phòng/code. | **PASS** | CHẠY | Unauthenticated room/code and self-declared teacher role rejected [full-audit.log](full-audit.log) |
| B04 | Client cố tự khai vai trò giáo viên. | **PASS** | CHẠY | Unauthenticated room/code and self-declared teacher role rejected [full-audit.log](full-audit.log) |
| B05 | Đăng nhập lại để đổi danh tính trên cùng kết nối. | **PASS** | CHẠY | Identity cannot change on an authenticated connection [full-audit.log](full-audit.log) |
| B06 | Nhiều phiên dùng cùng tài khoản: ghi chính sách thực tế; kiểm tra có gây nhân đôi danh tính/thành viên hoặc phá quyền sửa không. | **PASS** | CHẠY | Same account has distinct session IDs/members; disconnect removes only its session [full-audit.log](full-audit.log) |
| B07 | Học sinh tạo phòng: phải bị từ chối. | **PASS** | CHẠY | Server rejects student create and full-code update even when holding the edit token [full-audit.log](full-audit.log) |
| B08 | Học sinh gửi /update hoặc nạp toàn bộ code: phải bị server từ chối, không chỉ ẩn khỏi menu. | **PASS** | CHẠY | Server rejects student create and full-code update even when holding the edit token [full-audit.log](full-audit.log) |
| B09 | Giáo viên khác quản lý/kick trong phòng không sở hữu. | **PASS** | CHẠY | Other teacher cannot kick or load code in a room it does not own [full-audit.log](full-audit.log) |
| B10 | Người ngoài phòng đọc/sửa/xem lịch sử. | **PASS** | CHẠY | Authenticated outsider cannot read code/lines/history or operate a draft [full-audit.log](full-audit.log) |
| B11 | Đọc mã kiểm tra mật khẩu có hash và salt, không lưu mật khẩu rõ trong database. | **PASS** | CHẠY | SQLite contains independently salted PBKDF2 hashes, not plaintext (values never logged) [full-audit.log](full-audit.log) |
| B12 | Tham số đăng nhập thiếu/sai/quá dài không làm server chết. | **PASS** | CHẠY | Missing, malformed and oversized login parameters leave the server responsive [full-audit.log](full-audit.log) |
| C01 | Tạo phòng, vào phòng, xem thành viên. | **PASS** | CHẠY | Real logins, two teacher-owned rooms, server-side member identities [full-audit.log](full-audit.log) |
| C02 | Vào phòng không tồn tại hoặc thiếu mã phòng. | **PASS** | CHẠY | Invalid/missing room ID rejected; rejoin while already in a room does not duplicate members [full-audit.log](full-audit.log) |
| C03 | Vào lại khi đang ở phòng không nhân đôi thành viên. | **PASS** | CHẠY | Invalid/missing room ID rejected; rejoin while already in a room does not duplicate members [full-audit.log](full-audit.log) |
| C04 | Rời rồi vào lại: số người chính xác. | **PASS** | CHẠY | Leave/rejoin counts and correctly scoped identity notifications verified with ordered barriers [full-audit.log](full-audit.log) |
| C05 | Thông báo vào/ra đúng danh tính và đúng phòng. | **PASS** | CHẠY | Leave/rejoin counts and correctly scoped identity notifications verified with ordered barriers [full-audit.log](full-audit.log) |
| C06 | Ít nhất hai phòng hoạt động đồng thời: chat/code/lịch sử/quyền/hàng đợi độc lập. | **PASS** | CHẠY | Two independent rooms: Unicode/emoji chat both ways; code/history/editor/queue remain scoped [full-audit.log](full-audit.log) |
| C07 | Chat hai chiều và tiếng Việt/emoji. | **PASS** | CHẠY | Two independent rooms: Unicode/emoji chat both ways; code/history/editor/queue remain scoped [full-audit.log](full-audit.log) |
| C08 | Chat ngoài phòng, chat trống, chat quá giới hạn. | **PASS** | CHẠY | Outside-room, empty and oversized chat rejected [full-audit.log](full-audit.log) |
| C09 | Một client thoát không làm server/client khác dừng. | **PASS** | CHẠY | One client exiting leaves server and other clients operational [full-audit.log](full-audit.log) |
| C10 | /members, /code, /lines phản ánh trạng thái đúng. | **PASS** | CHẠY | Members/code/numbered lines match the authoritative room snapshot [full-audit.log](full-audit.log) |
| C11 | Kick thành viên: kiểm tra quyền, dọn thành viên, bản nháp và hàng đợi đúng. | **PASS** | CHẠY | Owner kick discards holder draft and removes session/queue before handing over [full-audit.log](full-audit.log) |
| C12 | Phân biệt danh sách online với điểm danh: không tuyên bố đã có điểm danh buổi học nếu chưa triển khai. | **PASS** | RÀ SOÁT MÃ | members là map các kết nối hiện tại, SQLite có users/rooms/revisions; không có nghiệp vụ điểm danh buổi học và không tuyên bố có. [../../src/studyfix/Room.java](../../src/studyfix/Room.java) |
| D01 | Chỉ một người được giữ quyền tại một thời điểm. | **PASS** | CHẠY | Exactly one editor; ordered FIFO and idempotent repeated requests [full-audit.log](full-audit.log) |
| D02 | Nhiều người xin quyền theo thứ tự FIFO. | **PASS** | CHẠY | Exactly one editor; ordered FIFO and idempotent repeated requests [full-audit.log](full-audit.log) |
| D03 | Xin lại khi đang giữ quyền. | **PASS** | CHẠY | Exactly one editor; ordered FIFO and idempotent repeated requests [full-audit.log](full-audit.log) |
| D04 | Xin nhiều lần khi đang chờ không xếp trùng. | **PASS** | CHẠY | Exactly one editor; ordered FIFO and idempotent repeated requests [full-audit.log](full-audit.log) |
| D05 | Trả quyền khi không có bản nháp. | **PASS** | CHẠY | Release without draft advances FIFO, invalid/old tokens rejected, empty queue clears editor [full-audit.log](full-audit.log) |
| D06 | /release khi còn bản nháp bị chặn/hướng dẫn rõ. | **PASS** | CHẠY | Draft blocks release, and owner cannot load code over an active turn [full-audit.log](full-audit.log) |
| D07 | Người đang chờ hoặc ngoài phòng dùng token của chủ lượt. | **PASS** | CHẠY | Release without draft advances FIFO, invalid/old tokens rejected, empty queue clears editor [full-audit.log](full-audit.log) |
| D08 | Token cũ sau khi chuyển quyền. | **PASS** | CHẠY | Release without draft advances FIFO, invalid/old tokens rejected, empty queue clears editor [full-audit.log](full-audit.log) |
| D09 | Người trong hàng đợi rời phòng. | **PASS** | CHẠY | Leaving/disconnected queued sessions removed or skipped when transferring rights [full-audit.log](full-audit.log) |
| D10 | Chủ lượt rời phòng hoặc bị kick. | **PASS** | CHẠY | Owner kick discards holder draft and removes session/queue before handing over [full-audit.log](full-audit.log) |
| D11 | Hàng đợi có người đã mất kết nối: bỏ qua người không hợp lệ. | **PASS** | CHẠY | Leaving/disconnected queued sessions removed or skipped when transferring rights [full-audit.log](full-audit.log) |
| D12 | Hàng đợi trống: kết thúc lượt không còn editor. | **PASS** | CHẠY | Release without draft advances FIFO, invalid/old tokens rejected, empty queue clears editor [full-audit.log](full-audit.log) |
| D13 | Giáo viên nạp code khi có bản nháp: không được làm lệch phiên bản gốc của lượt. | **PASS** | CHẠY | Draft blocks release, and owner cannot load code over an active turn [full-audit.log](full-audit.log) |
| E01 | Chọn một dòng, chọn nhiều dòng rời, chọn đoạn liên tiếp. | **PASS** | CHẠY | Single/discrete/range selection works; zero/negative/duplicate/overflow/reversed/malformed selections rejected [full-audit.log](full-audit.log) |
| E02 | Chọn dòng 0/âm/vượt giới hạn/trùng, đoạn đảo đầu-cuối, lựa chọn sai cú pháp. | **PASS** | CHẠY | Single/discrete/range selection works; zero/negative/duplicate/overflow/reversed/malformed selections rejected [full-audit.log](full-audit.log) |
| E03 | Chọn lại khi đang có bản nháp. | **PASS** | CHẠY | Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected [full-audit.log](full-audit.log) |
| E04 | Sửa dòng nằm ngoài lựa chọn. | **PASS** | CHẠY | Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected [full-audit.log](full-audit.log) |
| E05 | Sửa một dòng có tiếng Việt/emoji và thụt lề. | **PASS** | CHẠY | Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected [full-audit.log](full-audit.log) |
| E06 | /setline chứa ký tự xuống dòng phải bị từ chối. | **PASS** | CHẠY | Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected [full-audit.log](full-audit.log) |
| E07 | Dòng trống khác xóa dòng. | **PASS** | CHẠY | Blank selected line is distinct from a deleted line [full-audit.log](full-audit.log) |
| E08 | Giữ nguyên một dòng đã chọn. | **PASS** | CHẠY | Each selected line must be processed; early finish preserves draft and rights [full-audit.log](full-audit.log) |
| E09 | Sửa lại bản nháp trước khi hoàn tất. | **PASS** | CHẠY | Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected [full-audit.log](full-audit.log) |
| E10 | Chưa xử lý đủ lựa chọn mà /finish: từ chối, giữ bản nháp và quyền. | **PASS** | CHẠY | Each selected line must be processed; early finish preserves draft and rights [full-audit.log](full-audit.log) |
| E11 | /draft là code dự kiến; /code vẫn là code đã lưu. | **PASS** | CHẠY | Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected [full-audit.log](full-audit.log) |
| E12 | Xóa dòng phía trước, sửa dòng rời phía sau: số dòng vẫn tính theo bản gốc, không lệch. | **PASS** | CHẠY | Original line indices survive deletion; one atomic revision has author/time/selected before-after/keep and full code [full-audit.log](full-audit.log) |
| E13 | Thay cả đoạn bằng ít dòng hơn/nhiều dòng hơn. | **PASS** | CHẠY | Range shrink/expand preserves the next original line and history uses original range numbers [full-audit.log](full-audit.log) |
| E14 | Xóa hoặc giữ nguyên cả đoạn. | **PASS** | CHẠY | Delete range is committed; keeping or restoring identical content ends turn without a revision [full-audit.log](full-audit.log) |
| E15 | Ranh giới đoạn mới không nối nhầm với dòng phía sau. | **PASS** | CHẠY | Range shrink/expand preserves the next original line and history uses original range numbers [full-audit.log](full-audit.log) |
| E16 | /rangefile qua client Java thực tế: đọc UTF-8, giữ nhiều dòng và đường dẫn có khoảng trắng. | **PASS** | CHẠY | /rangefile chạy bằng client Java thật và server thật; Unicode/CRLF, đường dẫn có dấu và khoảng trắng. [person-one.log](person-one.log) |
| E17 | File không tồn tại, quá lớn hoặc đường dẫn không hợp lệ. | **PASS** | CHẠY | Actual Java console rejects missing/oversized/invalid rangefile and load paths without sending updates [full-audit-extras.log](full-audit-extras.log) |
| E18 | Bản nháp/code vượt giới hạn bị từ chối, trạng thái hợp lệ trước đó không bị phá. | **PASS** | CHẠY | Oversized single edit/combined draft/range/load rejected without losing valid preview [full-audit.log](full-audit.log) |
| E19 | /finish không có bản nháp; /cancel không có bản nháp: hành vi rõ và không gây lỗi. | **PASS** | CHẠY | Missing drafts have clear responses; stale tokens/versions cannot mutate an active draft [full-audit.log](full-audit.log) |
| E20 | Version/token sai khi chọn hoặc thao tác bản nháp. | **PASS** | CHẠY | Missing drafts have clear responses; stale tokens/versions cannot mutate an active draft [full-audit.log](full-audit.log) |
| E21 | Hoàn tất cả lượt tạo một version và một lịch sử, đồng bộ code rồi chuyển quyền. | **PASS** | CHẠY | Original line indices survive deletion; one atomic revision has author/time/selected before-after/keep and full code [full-audit.log](full-audit.log) |
| E22 | Lượt không thay đổi nội dung: không tăng version/thêm lịch sử. | **PASS** | CHẠY | Delete range is committed; keeping or restoring identical content ends turn without a revision [full-audit.log](full-audit.log) |
| E23 | Hủy lượt: code/version/lịch sử không đổi, trả quyền đúng hàng đợi. | **PASS** | CHẠY | Cancel preserves code/history and transfers queue; history reads are read-only and bad versions rejected [full-audit.log](full-audit.log) |
| F01 | Phiên bản 0 được nhận diện là code khởi tạo. | **PASS** | CHẠY | Initial revision has version zero and explicit initialization label [full-audit.log](full-audit.log) |
| F02 | Đúng userId, tên tại thời điểm sửa, thời gian. | **PASS** | CHẠY | Original line indices survive deletion; one atomic revision has author/time/selected before-after/keep and full code [full-audit.log](full-audit.log) |
| F03 | Dòng rời: ghi riêng các đoạn trước/sau và giữ nguyên. | **PASS** | CHẠY | Original line indices survive deletion; one atomic revision has author/time/selected before-after/keep and full code [full-audit.log](full-audit.log) |
| F04 | Đoạn liên tiếp: ghi dòng đầu/cuối theo bản trước, không nhầm số dòng sau khi độ dài đoạn thay đổi. | **PASS** | CHẠY | Range shrink/expand preserves the next original line and history uses original range numbers [full-audit.log](full-audit.log) |
| F05 | Lưu toàn bộ code sau sửa đúng nội dung. | **PASS** | CHẠY | Original line indices survive deletion; one atomic revision has author/time/selected before-after/keep and full code [full-audit.log](full-audit.log) |
| F06 | Yêu cầu bị từ chối hoặc bản nháp bị hủy không tạo lịch sử. | **PASS** | CHẠY | Cancel preserves code/history and transfers queue; history reads are read-only and bad versions rejected [full-audit.log](full-audit.log) |
| F07 | Xem lịch sử không thay đổi code hiện tại. | **PASS** | CHẠY | Cancel preserves code/history and transfers queue; history reads are read-only and bad versions rejected [full-audit.log](full-audit.log) |
| F08 | Xem version không tồn tại/âm/sai cú pháp. | **PASS** | CHẠY | Cancel preserves code/history and transfers queue; history reads are read-only and bad versions rejected [full-audit.log](full-audit.log) |
| F09 | Không lẫn lịch sử giữa các phòng. | **PASS** | CHẠY | Two independent rooms: Unicode/emoji chat both ways; code/history/editor/queue remain scoped [full-audit.log](full-audit.log) |
| F10 | Đọc được lịch sử cũ trước khi bổ sung sửa theo lượt. | **PASS** | CHẠY | Student qua TCP đọc /history từ legacy fixture; giữ old-user, tên lúc sửa, timestamp gốc và snapshot b. [legacy-read.log](legacy-read.log) |
| F11 | Lịch sử dài chia nhiều frame: đủ dữ liệu, đúng thứ tự, không cắt hỏng Unicode. | **FAIL** | CHẠY | Wire/SQLite PASS, nhưng console chèn thông báo bàn phím vào giữa history 4 frame/210025 byte. Lỗi AUD-01; xem proof và raw evidence. [console-interleave.log](console-interleave.log) |
| F12 | Khởi động lại giữ phòng/code/version/lịch sử. | **PASS** | CHẠY | Restart reloads durable data only; old sockets/editor/queue/token/draft absent [full-audit-extras.log](full-audit-extras.log) |
| F13 | Không khôi phục thành viên online, socket, editor, hàng đợi hoặc bản nháp. | **PASS** | CHẠY | Restart reloads durable data only; old sockets/editor/queue/token/draft absent [full-audit-extras.log](full-audit-extras.log) |
| F14 | Tạo phòng sau khởi động lại không trùng mã đã lưu. | **PASS** | CHẠY | SQLite room IDs remain unique after server restart [full-audit-extras.log](full-audit-extras.log) |
| F15 | Migration schema cũ trên database thử: không mất dữ liệu, không gán lại tác giả, khởi động nhiều lần không lỗi. | **PASS** | CHẠY | Schema cũ có revision thiếu; migration/backfill 3 lần, giữ tác giả/time/account/code; lỗi migration rollback DDL. [integration.log](integration.log) |
| F16 | Gây lỗi SQLite có kiểm soát ở bước lưu lịch sử: kiểm tra transaction rollback cả code/version/lịch sử; RAM không lệch database, không báo thành công; bản nháp/quyền còn để thử lại. | **PASS** | CHẠY | Injected SQLite revision error rolls back code/version/history, retains draft/rights, retry commits exactly once [full-audit.log](full-audit.log) |
| F17 | Thử lại sau khi bỏ lỗi: chỉ lưu một lần. | **PASS** | CHẠY | Injected SQLite revision error rolls back code/version/history, retains draft/rights, retry commits exactly once [full-audit.log](full-audit.log) |
| F18 | Các kiểu LF/CRLF, dấu xuống dòng cuối file, tiếng Việt và emoji không làm mất code đã lưu. | **PASS** | CHẠY | Actual TCP/SQLite turns preserve LF/CRLF/CR/EOF, Unicode and indentation [full-audit-extras.log](full-audit-extras.log) |
| G01 | Đọc timeout thực tế từ code, báo chính xác. Không mặc định là 25 giây; bản trước dùng 20 giây. | **PASS** | RÀ SOÁT MÃ + CHẠY | Ping mỗi 5s; timeout ping/pong 20s. Đo thực tế timeout trong full-audit-extras.log, không dùng giả định 25s. [full-audit-extras.log](full-audit-extras.log) |
| G02 | Client bình thường không nhập lệnh hơn timeout vẫn online vì heartbeat tự gửi. | **PASS** | CHẠY | Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue [full-audit-extras.log](full-audit-extras.log) |
| G03 | Dùng /heartbeat off hoặc client test ngừng gửi ping: server đóng kết nối theo timeout. | **PASS** | CHẠY | Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue [full-audit-extras.log](full-audit-extras.log) |
| G04 | Người bị timeout đang giữ quyền và có bản nháp: bỏ nháp, giữ code/version, chuyển quyền. | **PASS** | CHẠY | Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue [full-audit-extras.log](full-audit-extras.log) |
| G05 | Ngắt socket/process đột ngột với cùng trạng thái trên. | **PASS** | CHẠY | Abrupt RST with draft discards uncommitted work and transfers rights [full-audit-extras.log](full-audit-extras.log) |
| G06 | Người đang chờ bị mất kết nối: loại khỏi hàng đợi. | **PASS** | CHẠY | Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue [full-audit-extras.log](full-audit-extras.log) |
| G07 | Dừng server hoặc giả lập không trả pong: client Java phát hiện và thoát chờ theo timeout. | **PASS** | CHẠY | Console Java thật thoát khi stub không trả pong trong 20s; PersonOneTest cũng kiểm tra server thật dừng. [integration.log](integration.log) |
| G08 | Cleanup đúng một lần, không thông báo rời trùng, không còn tác vụ heartbeat của phiên đã đóng. | **PASS** | CHẠY | Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue [full-audit-extras.log](full-audit-extras.log) |
| G09 | Client khác vẫn chat/sửa sau khi một client timeout. | **PASS** | CHẠY | Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue [full-audit-extras.log](full-audit-extras.log) |
| H01 | Frame bị chia nhỏ, nhiều frame nối liên tiếp. | **PASS** | CHẠY | Fragmented header/payload writes and coalesced frames parsed in order [full-audit-extras.log](full-audit-extras.log) |
| H02 | Độ dài frame âm/quá lớn, frame thiếu dữ liệu, socket đóng giữa frame: server xử lý có kiểm soát. | **PASS** | CHẠY | Negative/oversized lengths, short payload, and socket EOF inside frame close only that session [full-audit-extras.log](full-audit-extras.log) |
| H03 | Client đọc chậm/ngừng đọc và đầy hàng đợi: client khác vẫn tiến triển; không âm thầm bỏ code/sự kiện quan trọng. | **PASS** | CHẠY | Slow socket fills bounded queue; server logs disconnection and other clients keep progressing [full-audit-extras.log](full-audit-extras.log) |
| H04 | Nhiều client đồng thời xin quyền. | **PASS** | CHẠY | Eight simultaneously released requesters produce one editor and seven unique queue entries [full-audit-extras.log](full-audit-extras.log) |
| H05 | Hoàn tất và rời phòng gần đồng thời. | **PASS** | CHẠY | Finish/leave race over one framed socket, 8 iterations; final data is one legal atomic outcome [full-audit-extras.log](full-audit-extras.log) |
| H06 | Hoàn tất lặp lại bằng token cũ: không tăng version hoặc lưu trùng. | **PASS** | CHẠY | Injected SQLite revision error rolls back code/version/history, retains draft/rights, retry commits exactly once [full-audit.log](full-audit.log) |
| H07 | Hai client gửi yêu cầu sửa cùng lúc: chỉ chủ quyền hợp lệ được chấp nhận. | **PASS** | CHẠY | Concurrent holder finish and non-holder select accept only the authorized turn [full-audit-extras.log](full-audit-extras.log) |
| H08 | Nhiều phòng sửa đồng thời: dữ liệu nhất quán, không deadlock. | **PASS** | CHẠY | Two rooms each complete 20 turns concurrently without deadlock or cross-room content [full-audit-extras.log](full-audit-extras.log) |
| H09 | Ghi rõ số client, thời gian và khối lượng đã thử, không suy rộng thành cam kết chịu tải. | **PASS** | CHẠY | High-water quan sát 13 kết nối; 8 xin quyền đồng thời; 2 phòng x20 lượt; 8 finish/leave races; tải cụ thể bên dưới, không cam kết chịu tải. [full-audit-extras.log](full-audit-extras.log) |
| I01 | Trạng thái/cổng/số kết nối, danh sách phòng/thành viên, editor/hàng đợi/version đúng với client. | **PASS** | CHẠY | Swing thực tế + 2 client: trước Stop có member/editor/queue; sau Stop bảng/status/log khớp snapshot 0 kết nối. [integration.log](integration.log) |
| I02 | Chọn lịch sử trong ServerAdmin hiện đúng chi tiết. | **PASS** | CHẠY | Cửa sổ Swing thật cập nhật từ TCP, chọn bản 0/1 và kiểm tra metadata/diff/code Unicode chỉ đọc. [admin-live.log](admin-live.log) |
| I03 | Snapshot không làm lộ đối tượng mutable đang thay đổi. | **PASS** | CHẠY | Immutable snapshots; authenticated gateway identity/membership/owner/edit rights/events/departure callbacks [full-audit-extras.log](full-audit-extras.log) |
| I04 | Mạng/database/tính toán nặng không chạy trên Swing EDT. | **PASS** | RÀ SOÁT MÃ | ServerAdmin tạo server/lấy snapshot/đóng server ở background; EDT render Swing. Diff LCS/Myers và migration ở worker/Store, không gọi từ EDT. Chưa profiling độ mượt khi lịch sử cực lớn. [../../src/studyfix/ServerAdmin.java](../../src/studyfix/ServerAdmin.java) |
| I05 | Dừng server đóng listener/socket/executor, sau đó khởi động lại được. | **PASS** | CHẠY | Stop cleans listener/client/monitor/workers/SQLite; same port starts a new server [full-audit-extras.log](full-audit-extras.log) |
| I06 | Interface cho nhóm: danh tính, thành viên, quyền giáo viên, snapshot code, gửi sự kiện, callback rời/mất kết nối. | **PASS** | CHẠY | Immutable snapshots; authenticated gateway identity/membership/owner/edit rights/events/departure callbacks [full-audit-extras.log](full-audit-extras.log) |
| I07 | Nếu không có môi trường GUI: phân biệt nhận xét từ đọc mã và giao diện chưa test. Không đánh dấu PASS giao diện chỉ vì build thành công. | **PASS** | CHẠY | Có môi trường GUI; đã chạy kiểm thử component trên cửa sổ thật, không suy PASS từ build. Không có kiểm tra bố cục bằng mắt trong lượt này. [admin-live.log](admin-live.log) |

## Lỗi cần sửa trước bàn giao

### AUD-01 — P2: thông báo bàn phím chen giữa các frame lịch sử khi console hiển thị

- Liên quan **F11**. TCP/SQLite vẫn có đủ code/diff và Unicode; lỗi ở lớp hiển thị console, không phải mất bản ghi database hay server broadcast sang sai phòng.
- Vị trí: [Client.java:149](../../src/studyfix/Client.java#L149), nhánh nhận `HISTORY_PART` tại `main` in từng phần riêng (`System.out.print` ở khoảng dòng 152). Cùng lúc `keyboard-sender` in thông báo `/heartbeat off` tại khoảng dòng 83; các thông báo local khác có thể cùng nguy cơ. PrintStream chỉ tuần tự hóa từng lần in, không bảo vệ toàn bộ phản hồi nhiều frame.
- Fixture: `test/studyfix/ConsoleHistoryInterleaveTest.java`; server stub gửi history có tiếng Việt/emoji **210025 byte / 4 frame**. Sau frame đầu, chờ client thật in `BEGIN_HISTORY`, gửi `/heartbeat off` qua stdin, chờ thông báo xuất hiện rồi gửi các frame còn lại. Chờ marker có timeout, không giả kết quả bằng thời gian nghỉ.
- Mong đợi: history hiển thị liền mạch; thông báo bàn phím ở trước hoặc sau toàn bộ history.
- Thực tế: `Đã tạm dừng heartbeat...` nằm giữa `BEGIN_HISTORY` và `END_HISTORY`; chuỗi lịch sử đầy đủ không còn liền mạch trong output. [console-interleave-proof.txt](console-interleave-proof.txt) ghi chỉ số ký tự; [raw output](console-interleave-evidence.log) giữ bằng chứng.
- Chạy lại: `java -cp 'verification/person-one-20261021/test-classes;verification/person-one-20261021/build/classes;lib/*' studyfix.ConsoleHistoryInterleaveTest`. Exit code 1 là lỗi đã tái hiện, không phải thiếu môi trường. Không có mật khẩu được in trong output.
- Hướng sửa đề nghị, chưa thực hiện: một luồng sở hữu việc in console hoặc buffer/đồng bộ toàn bộ multipart rồi in nguyên phản hồi; điều phối cả thông báo local. Sau đó giữ bài regression xen kẽ này trong target test thường chạy.

Không sửa AUD-01 trong lượt nghiệm thu theo yêu cầu. Các test SQLite cleanup sau commit và bảng quản trị sau Dừng đã PASS trên bản hiện tại; không báo lại hai lỗi cũ như lỗi chưa sửa.

## Giới hạn tải, thời gian và dữ liệu đã thử

```text
METRIC peak_observed_connections=7 elapsed_ms=22870 transport=127.0.0.1
METRIC slow_reader_accepted_responses=67 each_utf8_bytes=60000 elapsed_ms=49
METRIC concurrent_rooms=2 turns_per_room=20 committed_turns=40
METRIC heartbeat_since_last_ping_ms=20774 wait_ms=20773
METRIC peak_observed_connections=13 elapsed_ms=55475 transport=127.0.0.1
METRIC frames=4 history_utf8_bytes=210025
```

- Peak quan sát của harness: **13 socket**, gồm 8 requester đồng thời và 5 phiên fixture; không phải test giới hạn 128 hoặc cam kết chịu tải production. PersonOneTest quan sát peak 6. Các tiến trình/bộ test độc lập có thể chạy song song trên database/cổng riêng; không cộng peak các bài thành khả năng một server.
- Hai phòng chạy đồng thời: 20 lượt/phòng = **40 commit**, kiểm tra version và prefix code riêng; finish/leave race **8 vòng**. Nhiều client đồng thời không có thứ tự gửi duy nhất; FIFO được nghiệm thu riêng bằng yêu cầu tuần tự có xác nhận.
- Slow reader: **67 phản hồi được enqueue**, 60000 byte/phản hồi (4.02 MB nội dung ứng dụng trước khi disconnect); giới hạn mỗi response/frame được kiểm tra riêng. Đã thấy log queue overflow, đóng socket, client khác tiếp tục chat. Không diễn giải đóng client quá chậm là âm thầm bỏ sự kiện rồi cho nó tiếp tục.
- Payload code/range tối đa hợp lệ 32000 byte; thử 32001 byte để bị từ chối. History multipart lớn và diff 1000 ca ngẫu nhiên có test riêng. Bằng chứng client history interleave dùng 210025 byte/4 frame.
- Timeout server đọc từ code là **20 giây từ ping cuối**, client ping mỗi 5 giây; harness đo khoảng **20.8 giây** do nhịp monitor 1 giây. Client khỏe idle qua thời hạn vẫn online; sau timeout client khác còn chat/sửa được. Client Java watchdog pong 20 giây được thử bằng stub không trả pong.
- Chưa thử mạng LAN hai máy, firewall/router, rút cáp thật, mất điện/đĩa hỏng thật, tải 128 client, hoặc memory stress lịch sử không giới hạn. Không suy rộng kết quả localhost thành nghiệm thu LAN.

## Script, tái chạy và tiến trình thử

- [Run-Audit.ps1](Run-Audit.ps1) build sạch riêng, compile toàn bộ test hiện có + harness, chạy từng đợt và tạo lại báo cáo. Chạy từ thư mục project bằng `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\verification\person-one-20261021\Run-Audit.ps1`. Tham số quyền chạy script chỉ áp dụng cho tiến trình này, không đổi chính sách hệ thống. Không đổi build tool/JDK hoặc cấu hình project. Bộ script tổng được kiểm tra cú pháp; các lệnh/test bên trong đã chạy riêng trong lượt audit.
- [FullAudit.java](FullAudit.java): tài khoản, phòng, chat, queue, bản nháp, history và rollback/retry; [FullAuditExtras.java](FullAuditExtras.java): framing/slow reader/timeout/races/gateway/restart. [PackagedSmoke.java](PackagedSmoke.java): chạy JAR Server và Client thành JVM riêng, kiểm tra conflict/restart cùng cổng. [LegacyHistorySmoke.java](LegacyHistorySmoke.java): đọc legacy history bằng TCP thật.
- [build.log](build.log), [integration.log](integration.log), [person-one.log](person-one.log), [admin-live.log](admin-live.log), [full-audit.log](full-audit.log), [full-audit-extras.log](full-audit-extras.log), [packaged.log](packaged.log), [legacy-read.log](legacy-read.log) và [matrix.csv](matrix.csv) là bằng chứng máy đọc được.
- `try/finally`/try-with-resources đóng socket, executor và Server; process smoke chỉ `destroy` các Process handle chính nó tạo. `packaged.log` ghi `CLEANUP all packaged smoke JVM processes terminated`. Các JVM chạy qua tool đã trả exit code; kiểm tra socket/executor/SQLite đóng có assertion riêng. Không dùng lệnh kill-all/java/NetBeans. Giữ lại source test, fixture, database thử và log.

## Điều kiện bàn giao và ghép nhóm sau này

1. Sửa AUD-01, chạy lại regression interleave và toàn bộ suite; bản hiện tại chưa đạt đầy đủ F11 trên console khi có thông báo local xen kẽ.
2. Thống nhất README với lệnh sửa theo lượt hiện tại: menu/tài liệu cũ vẫn có đoạn no-op full load tạo revision, còn `/finish` no-op không tạo revision. Cần mô tả rõ hai hành vi, không đổi nghiệp vụ trong lượt audit.
3. Người 2 cần ghép `LOGIN_OK`, sự kiện bất đồng bộ, multipart, draft/finish/cancel và token/version; chưa có GUI client trong phạm vi này. Người 3 dùng gateway từ danh tính phiên tin cậy, không tin userId/role do client tự khai.
4. Kiểm thử LAN hai máy sau: chat/code/history/queue, client ngắt mạng khi giữ nháp, server restart, firewall và client pong timeout. Không yêu cầu bộ chấm/thi/màn hình/điểm để nghiệm thu người 1.

Chỉ kết luận PASS cho phạm vi và bằng chứng của từng dòng. **LAN thật và kiểm tra bố cục Swing bằng mắt: CHƯA TEST, ngoài tổng 109 mã yêu cầu có điều kiện của lượt này.**
