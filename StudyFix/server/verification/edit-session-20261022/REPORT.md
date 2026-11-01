# Đối chiếu và kiểm thử EditSession

## Kết luận về log cũ

`test-results.log` **không đủ bằng chứng để xác nhận** đã chạy đầy đủ EditSession và chín lệnh được yêu cầu trên mã nguồn hiện tại. Không suy luận rằng một ca đã chạy chỉ vì mã test hiện đang tồn tại.

- Dòng 289: `HISTORY ALL PASSED: 86 checks`; dòng 378: `ALL PASSED: 40 checks (localhost only)`; dòng 379–383: console `/load`, heartbeat và multipart. Không có tên ca draft, EditSession hay kết quả `/rangefile` cụ thể.
- Dòng 210 còn ghi `No-op policy: accepted save creates version`. Kết quả này không xác nhận chính sách `/finish` không đổi nội dung của draft hiện tại; ca DT06 mới kiểm tra riêng hành vi đó.
- `test/studyfix/IntegrationTest.java:141` hiện có đường đi `/select` → `/setrange` → `/finish`, nhưng nhãn tổng quát trong log cũ không chứng minh toàn bộ chín lệnh đã được chạy.
- `test/studyfix/ConsoleClientTest.java:44` hiện chạy Client thật và đối chiếu `/rangefile` chuyển thành `/setrange` qua server giả. Nó chứng minh payload, không thay thế bằng chứng commit qua Server/Room/SQLite thật.
- `test/studyfix/PersonOneTest.java:168` trở đi đã chứa nhiều tình huống draft được yêu cầu; dòng 86 có console `/rangefile` tới server thật. Tuy nhiên target `integration-test` ban đầu không gọi lớp này, và log cũ không có các nhãn C04/C07/C11/D05/F05 của nó.
- Log cũ không ghi hash nguồn hay nguồn class đã nạp; số file biên dịch incremental không phải bằng chứng thiếu/thừa phạm vi test.

## Thay đổi trong lần kiểm tra này

Thêm `test/studyfix/EditSessionTest.java`, target `ant edit-session-test` và gọi lớp test mới trong `ant integration-test`. Không sửa mã nghiệp vụ, cấu hình JDK, hoặc nội dung log cũ.

SHA256 trước/sau trong `before.csv` và `after.csv`: **15/15 file không đổi**, gồm toàn bộ 14 file Java dưới `src/studyfix` và `test-results.log`.

## Kết quả riêng của test mới

Lệnh chạy từ thư mục project: `ant edit-session-test` (Ant cài tại `C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat`). Log: `new-tests.log`.

Runtime thực tế: **JDK 26.0.2+10**, giữ nguyên `javac.release=26` của workspace; dependency `lib/sqlite-jdbc-3.53.0.0.jar`. Đây không phải kết quả kiểm thử Java 17.

**11 PASS, 0 FAIL**, 12.274 giây chạy ca test; Ant BUILD SUCCESSFUL, tổng 22 giây. Các ca TCP chạy nhiều kết nối tại `127.0.0.1`; DT01–DT02 gọi trực tiếp EditSession.

| Ca | Kết quả | Bằng chứng được assert | Dòng new-tests.log |
|---|---|---|---|
| DT01 | PASS | Gọi trực tiếp EditSession: lựa chọn rời rạc chỉ complete sau khi xử lý đủ, dùng số dòng gốc | 178 |
| DT02 | PASS | Gọi trực tiếp EditSession: thay/giữ/xóa khoảng dòng, Unicode và ranh giới xuống dòng | 179 |
| DT03 | PASS | `/finish` khi chưa xử lý đủ bị từ chối; giữ draft, editor/token/queue, code/version/history RAM và SQLite | 198 |
| DT04 | PASS | `/setrange` và `/draft` chỉ đổi preview; client khác `/code` vẫn nhận code chung cũ; SQLite không đổi | 217 |
| DT05 | PASS | `/setline`, `/delete`, `/keep`, `/finish` cả lượt chỉ thêm một version/revision rồi chuyển FIFO; token cũ không lưu lặp | 237 |
| DT06 | PASS | Cả giữ nguyên và thay bằng nội dung giống hệt đều NO_CHANGE, không tăng version/history, vẫn chuyển quyền | 256 |
| DT07 | PASS | Xóa dòng gốc 1 rồi sửa dòng gốc 3 vẫn sửa `three`; lịch sử ghi Dòng 3 | 276 |
| DT08 | PASS | `/cancel` bỏ draft, không đổi dữ liệu đã commit, chuyển FIFO; người kế tiếp không có draft cũ | 295 |
| DT09 | PASS | Đóng socket đột ngột bằng RST: dọn phiên, bỏ draft, chuyển FIFO, dữ liệu đã commit không đổi | 314 |
| DT10 | PASS | SQLite trigger gây lỗi INSERT revision: rollback cả UPDATE room; giữ draft/editor/token/queue; bỏ trigger và retry cùng token lưu đúng một lần | 335 |
| DT11 | PASS | JVM Client thật đọc file nhiều dòng rồi gửi qua Server thật; preview không commit, finish lưu chính xác Unicode/emoji/CRLF/thụt dòng, một revision đúng tác giả | 360 |

Các kiểm tra dữ liệu bền vững dùng **kết nối JDBC độc lập**, so sánh code, version và số revision với RAM. Trigger gây lỗi DT10 chỉ được tạo trong DB thử của DT10 và được xóa trong finally.

Phạm vi lệnh được thực thi và đối chiếu kết quả:

| Lệnh | Ca mới |
|---|---|
| `/select` | DT03–DT11 |
| `/setline` | DT03, DT05–DT07, DT10 |
| `/delete` | DT05, DT07 |
| `/keep` | DT05, DT06 |
| `/setrange` | DT04, DT08, DT09; DT11 qua chuyển đổi trong Client |
| `/rangefile` | DT11: nhập vào stdin của JVM `studyfix.Client`, không tự giả lập payload bằng test peer |
| `/draft` | DT03, DT04, DT08–DT11 |
| `/finish` | DT03, DT05–DT07, DT10, DT11 |
| `/cancel` | DT08 |

`/rangefile` là lệnh phía Client (`src/studyfix/Client.java:153`): đọc UTF-8 rồi gửi `/setrange` qua TCP. Server không nhận nguyên văn `/rangefile`; DT11 kiểm tra đúng luồng này. File thử có tên chứa khoảng trắng và tiếng Việt, hai dòng nội dung, CRLF cuối dòng. Test so sánh toàn bộ code nhận được, không chỉ tìm một từ khóa.

Mốc thực thi trong `new-tests.log`: dòng 177 ghi `CODE_SOURCE=.../build/classes/`; dòng 361 xác nhận chín lệnh; dòng 362 ghi tổng 11/0; dòng 364 BUILD SUCCESSFUL.

## Chạy lại target integration sau khi bổ sung

`ant integration-test` hoàn tất với exit code 0, **BUILD SUCCESSFUL**, tổng 2 phút. Log riêng: `integration.log`; không ghi đè log cũ.

| Nhóm | Kết quả | Dòng integration.log |
|---|---|---|
| EditSession mới | 11 PASS, 0 FAIL; chạy lại trên bộ class integration | 176–361 |
| Recovery hiện có | 22 checks PASS | 463 |
| History hiện có | 78 checks PASS | 567 |
| Integration TCP hiện có | 40 checks PASS | 656 |
| Console history interleave hiện có | 2 ca PASS | 659–660 |
| Console client hiện có | 6 ca PASS | 662–667 |
| Ant | BUILD SUCCESSFUL | 669 |

Đây là hai lần thực thi cùng 11 ca mới, không phải 22 ca khác nhau. Lần integration dùng DB mới dưới `build/edit-session-8491745116393324841`; các nhóm hiện có cũng tạo DB thử riêng dưới build. Không khẳng định đã chạy `PersonOneTest` trong lần này: target chạy các lớp được liệt kê ở bảng trên.

## Cách ly dữ liệu và giới hạn

- Lần chạy riêng dùng `build/edit-session-11920992761972618491/DT03` đến `DT11`, mỗi ca một `studyfix.db` mới, cổng tự cấp phát.
- Bằng chứng console thật: `build/edit-session-11920992761972618491/DT11/client-console.log`; file đầu vào đặt cùng thư mục.
- Các fixture truyền đường dẫn DB thử trực tiếp vào `new Server(0, db)`. Không chạy server mặc định trên `data/studyfix.db`, không đọc/ghi lại hoặc reset dữ liệu thật.
- Test đóng các socket/server/JVM client do mình tạo, dừng executor heartbeat. Giữ DB thử và log để có thể đối chiếu; Ant clean sẽ xóa các artifact trong build.
- Đây là kiểm thử localhost trên một máy. **Chưa kiểm thử LAN**. Bộ test bổ sung này tập trung vào draft, không thay thế kiểm thử thủ công mọi thao tác GUI hoặc phần người 2/3.
