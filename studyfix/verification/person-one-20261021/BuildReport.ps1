$ErrorActionPreference = 'Stop'
$auditRoot = $PSScriptRoot
$projectRoot = [IO.Path]::GetFullPath((Join-Path $auditRoot '../..'))
$catalogue = Get-Content -Raw -Encoding UTF8 (Join-Path $auditRoot 'catalogue.json') | ConvertFrom-Json
$matrix = @{}
foreach ($row in (Get-Content -Encoding UTF8 (Join-Path $auditRoot 'results.tsv'))) {
    $cells = $row -split "`t", 3
    if ($cells[0] -match '^[A-I][0-9]{2}$') {
        $log = 'full-audit.log'
        if (Select-String -LiteralPath (Join-Path $auditRoot 'full-audit-extras.log') -SimpleMatch "AUDIT $($cells[0]) " -Quiet) { $log = 'full-audit-extras.log' }
        $matrix[$cells[0]] = @{status=$cells[1];mode='CHẠY';evidence=$cells[2];log=$log}
    }
}
function Add-Evidence($id, $file, $pattern, $message, $mode='CHẠY') {
    $path = Join-Path $auditRoot $file
    $found = (Test-Path -LiteralPath $path) -and (Select-String -LiteralPath $path -SimpleMatch $pattern -Quiet)
    $status = if ($found) {'PASS'} else {'CHƯA TEST'}
    $matrix[$id] = @{status=$status;mode=$mode;evidence=$message;log=$file}
}
Add-Evidence 'A01' 'build.log' 'BUILD SUCCESSFUL' 'Ant clean jar với build/dist riêng; toàn bộ source dùng release 26.'
Add-Evidence 'A02' 'packaged.log' 'PASS A02' 'JAR đóng gói đã mở SQLite thật qua classpath dist/lib; checksum tại dependency.csv.'
Add-Evidence 'A03' 'packaged.log' 'PASS A03' 'Server JVM riêng chạy cổng động, database packaged-*/server.db.'
Add-Evidence 'A04' 'packaged.log' 'PASS A04' 'Client Java đóng gói thật login/create/request/code/members/quit trên 127.0.0.1.'
Add-Evidence 'A05' 'packaged.log' 'PASS A05' 'JVM server thứ hai cùng cổng báo BindException; server đầu vẫn trả pong.'
Add-Evidence 'A06' 'packaged.log' 'PASS A06' 'Dừng tiến trình server do audit tạo, JVM mới mở lại cùng cổng/database.'
$matrix['C12'] = @{status='PASS';mode='RÀ SOÁT MÃ';evidence='members là map các kết nối hiện tại, SQLite có users/rooms/revisions; không có nghiệp vụ điểm danh buổi học và không tuyên bố có.';log='../../src/studyfix/Room.java'}
Add-Evidence 'E16' 'person-one.log' 'actual Java console /rangefile commits exact Unicode CRLF through real server' '/rangefile chạy bằng client Java thật và server thật; Unicode/CRLF, đường dẫn có dấu và khoảng trắng.'
Add-Evidence 'F10' 'legacy-read.log' 'PASS F10' 'Student qua TCP đọc /history từ legacy fixture; giữ old-user, tên lúc sửa, timestamp gốc và snapshot b.'
Add-Evidence 'F15' 'integration.log' 'Migration preserves author/time pass 2' 'Schema cũ có revision thiếu; migration/backfill 3 lần, giữ tác giả/time/account/code; lỗi migration rollback DDL.'
Add-Evidence 'F11' 'integration.log' 'Real TCP multipart complete detail and full code' 'TCP multipart/history và Unicode PASS; cần đối chiếu thêm console-interleave.log.'
if (Select-String -LiteralPath (Join-Path $auditRoot 'console-interleave.log') -SimpleMatch 'FAIL multipart history' -Quiet) {
    $matrix['F11'] = @{status='FAIL';mode='CHẠY';evidence='Wire/SQLite PASS, nhưng console chèn thông báo bàn phím vào giữa history 4 frame/210025 byte. Lỗi AUD-01; xem proof và raw evidence.';log='console-interleave.log'}
}
$handler = [IO.File]::ReadAllText((Join-Path $projectRoot 'src/studyfix/ClientHandler.java'))
$console = [IO.File]::ReadAllText((Join-Path $projectRoot 'src/studyfix/Client.java'))
if ($handler.Contains('TimeUnit.SECONDS.toNanos(20)') -and $console.Contains('TimeUnit.SECONDS.toNanos(20)')) {
    $matrix['G01'] = @{status='PASS';mode='RÀ SOÁT MÃ + CHẠY';evidence='Ping mỗi 5s; timeout ping/pong 20s. Đo thực tế timeout trong full-audit-extras.log, không dùng giả định 25s.';log='full-audit-extras.log'}
}
Add-Evidence 'G07' 'integration.log' 'actual client pong deadline, clear disconnect reason, heartbeat executor shutdown' 'Console Java thật thoát khi stub không trả pong trong 20s; PersonOneTest cũng kiểm tra server thật dừng.'
Add-Evidence 'H09' 'full-audit-extras.log' 'METRIC peak_observed_connections=' 'High-water quan sát 13 kết nối; 8 xin quyền đồng thời; 2 phòng x20 lượt; 8 finish/leave races; tải cụ thể bên dưới, không cam kết chịu tải.'
Add-Evidence 'I01' 'integration.log' 'Admin final table clears editor, members and queue' 'Swing thực tế + 2 client: trước Stop có member/editor/queue; sau Stop bảng/status/log khớp snapshot 0 kết nối.'
Add-Evidence 'I02' 'admin-live.log' 'PASS actual ServerAdmin revision selection remains read-only' 'Cửa sổ Swing thật cập nhật từ TCP, chọn bản 0/1 và kiểm tra metadata/diff/code Unicode chỉ đọc.'
$admin = [IO.File]::ReadAllText((Join-Path $projectRoot 'src/studyfix/ServerAdmin.java'))
if ($admin.Contains('background.execute') -and $admin.Contains('background.scheduleWithFixedDelay') -and $admin.Contains('SwingUtilities.invokeLater')) {
    $matrix['I04'] = @{status='PASS';mode='RÀ SOÁT MÃ';evidence='ServerAdmin tạo server/lấy snapshot/đóng server ở background; EDT render Swing. Diff LCS/Myers và migration ở worker/Store, không gọi từ EDT. Chưa profiling độ mượt khi lịch sử cực lớn.';log='../../src/studyfix/ServerAdmin.java'}
}
Add-Evidence 'I07' 'admin-live.log' 'PASS actual ServerAdmin refreshes new room/version/history' 'Có môi trường GUI; đã chạy kiểm thử component trên cửa sổ thật, không suy PASS từ build. Không có kiểm tra bố cục bằng mắt trong lượt này.'
foreach ($case in $catalogue) {
    if (-not $matrix.ContainsKey($case.id)) { $matrix[$case.id]=@{status='CHƯA TEST';mode='';evidence='Chưa có bằng chứng xác nhận.';log=''} }
}
$before = Import-Csv -LiteralPath (Join-Path $auditRoot 'source-before.csv')
$changes = @($before | Where-Object { (Get-FileHash -LiteralPath $_.Path -Algorithm SHA256).Hash -ne $_.Hash })
"PRODUCTION_FILES_CHECKED=$($before.Count)`nPRODUCTION_FILES_CHANGED=$($changes.Count)" | Set-Content -Encoding UTF8 (Join-Path $auditRoot 'source-verification.txt')
if ($changes.Count -ne 0) { throw 'Production source/config changed during audit; inspect before publishing report.' }
$counts = @{}
foreach ($status in @('PASS','FAIL','BLOCKED','CHƯA TEST')) { $counts[$status]=@($catalogue | Where-Object { $matrix[$_.id].status -eq $status }).Count }
$rows = foreach ($case in $catalogue) {
    $entry=$matrix[$case.id]
    $label=$case.description.Replace('|','\|')
    $evidence=$entry.evidence.Replace('|','\|')
    $source=if($entry.log){"[$($entry.log)]($($entry.log))"}else{''}
    "| $($case.id) | $label | **$($entry.status)** | $($entry.mode) | $evidence $source |"
}
$caseCsv = foreach ($case in $catalogue) { [pscustomobject]@{id=$case.id;status=$matrix[$case.id].status;mode=$matrix[$case.id].mode;evidence=$matrix[$case.id].evidence;log=$matrix[$case.id].log} }
$caseCsv | Export-Csv -Encoding UTF8 -NoTypeInformation (Join-Path $auditRoot 'matrix.csv')
$metrics = @((Select-String -LiteralPath (Join-Path $auditRoot 'full-audit.log'),(Join-Path $auditRoot 'full-audit-extras.log'),(Join-Path $auditRoot 'console-interleave.log') -Pattern '^METRIC' | ForEach-Object {$_.Line}))
$report = @'
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

__COUNTS__

Build sạch thành công. Bộ test chuẩn: RecoveryTest **22**, HistoryTest **78**, IntegrationTest **40**, và **6 assertion console** đều PASS. PersonOneTest **57 PASS, 0 FAIL**. ServerAdminLiveTest PASS. Các assertion/mã của các bộ có thể kiểm tra trùng chức năng; không cộng tổng assertion này thành số mã yêu cầu. Một kiểm tra bổ sung `ConsoleHistoryInterleaveTest` FAIL xác định lỗi AUD-01.

## Ma trận từng mã test

| Mã | Yêu cầu | Kết quả | Loại bằng chứng | Bằng chứng cụ thể |
|---|---|---|---|---|
__ROWS__

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

__METRICS__

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
'@
$countText = "**PASS: $($counts['PASS']) — FAIL: $($counts['FAIL']) — BLOCKED: $($counts['BLOCKED']) — CHƯA TEST: $($counts['CHƯA TEST']) — Tổng: $($catalogue.Count).**"
$report=$report.Replace('__COUNTS__',$countText).Replace('__ROWS__',($rows -join "`n")).Replace('__METRICS__',('```text'+"`n"+($metrics -join "`n")+"`n"+'```'))
$report | Set-Content -Encoding UTF8 (Join-Path $auditRoot 'REPORT.md')
$countText
