# Bộ câu hỏi và trả lời để hiểu StudyFix — phiên bản client web

Đối chiếu với source và [hướng dẫn logic](HUONG_DAN_LOGIC_SOURCE_STUDYFIX_VI.md), ngày 04/11/2026. Phạm vi: giao diện web bạn đang dùng và backend phục vụ giao diện đó. Các tình huống là ví dụ giải thích, không phải kết quả thử nghiệm thực tế.

**Cách học:** đọc câu hỏi, tự trả lời trước rồi mới đọc đáp án. Sau mỗi nhóm, mở các file được dẫn để tìm đoạn xử lý. Với một chức năng, tập giải thích đủ bốn ý: người dùng làm gì → web gửi gì → server kiểm tra gì → dữ liệu/kết quả được lưu và trả về thế nào.

## 1. Hiểu kiến trúc tổng thể

### Câu 1. StudyFix đang giải quyết vấn đề gì?

Hệ thống hỗ trợ lớp học lập trình và việc tự luyện code. Trong lớp, giáo viên đưa code Master, học sinh làm trên nhánh riêng, giáo viên theo dõi và chọn lời giải. Ngoài lớp, người dùng có thể mở ngân hàng luyện tập và nộp code để hệ thống chấm. Phòng học còn có chat và cuộc họp với mic, camera, chia sẻ màn hình.

**Ví dụ:** giáo viên giảng một bài sửa lỗi trong phòng; sau buổi học, học sinh mở Luyện code để làm bài khác mà không phải được giao bài.

### Câu 2. Nếu chỉ dùng web, hệ thống có những phần chính nào?

Có trình duyệt hiển thị giao diện; backend Java nhận và xử lý yêu cầu; SQLite lưu dữ liệu; Docker tạo môi trường chạy/chấm code. Docker phục vụ việc chấm, không phải nơi duy nhất chạy toàn bộ ứng dụng StudyFix.

```text
Trình duyệt → WebGatewayServer → nghiệp vụ Java → SQLite
                                      |
                                      └→ queue → worker → Docker → lưu kết quả
```

Cuộc họp còn có luồng media WebRTC giữa các trình duyệt.

### Câu 3. Trang Tổng quan giáo viên có phải server không?

Không. Nó vẫn là giao diện client trong trình duyệt, kể cả khi mở trên máy đang chạy server. Trang gửi API để lấy dữ liệu và thực hiện thao tác. Backend mới quyết định có cho phép thao tác hay không.

**Ví dụ:** nút xóa phòng nằm trên web; việc xác nhận quyền xóa và thay đổi trạng thái phòng nằm ở Java.

### Câu 4. Server Manager khác trang Tổng quan như thế nào?

Server Manager là ứng dụng quản lý/khởi động các dịch vụ backend. Trang Tổng quan là màn hình nghiệp vụ cho tài khoản người dùng. Server Manager dùng Swing nhưng không đồng nghĩa với ứng dụng client Swing trong thư mục `client/`.

**Điểm dễ nhầm:** không dùng client Swing vẫn có thể khởi động backend bằng Server Manager.

### Câu 5. Vì sao bỏ client Swing mà sơ đồ vẫn có TCP 5000?

Gateway web hiện tạo kết nối TCP nội bộ tới server lõi để đăng nhập và chuyển các lệnh điều khiển. Trình duyệt chỉ gửi HTTP/HTTPS tới gateway; gateway mới là bên kết nối TCP 5000.

**Ví dụ:** trình duyệt gửi tin nhắn → gateway chuyển lệnh chat qua kết nối lõi → server xử lý trong phòng. Xóa client Swing không tự loại bỏ được bước TCP nội bộ này.

### Câu 6. Vì sao sơ đồ web còn tên AssignmentTcpServer?

Web dùng lại hàm `handleWebRequest()` của lớp này để vào nghiệp vụ bài tập. Đây là lời gọi hàm Java trong cùng tiến trình, không phải web gửi yêu cầu qua socket TCP 5002.

**Phân biệt:** tên lớp có chữ TCP không có nghĩa mọi cách gọi lớp đều phải đi qua TCP.

### Câu 7. Giao diện dùng JavaScript thì backend có phải Node.js không?

Không. JavaScript ở đây chạy trong trình duyệt. Backend hiện là Java. Có thể dùng Node để chạy các script kiểm thử frontend mà không biến backend thành Node.js.

**Đọc code nhóm 1:** [app.js](../web-client/static/app.js), [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java), [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java), [AssignmentTcpServer.java](../server/src/studyfix/assignment/transport/AssignmentTcpServer.java).

## 2. Từ thao tác trên web tới backend

### Câu 8. Bấm một nút trên web thì code chạy theo thứ tự nào?

Thông thường: sự kiện được bắt trong frontend → hàm chức năng đọc state/input → helper API gửi request → gateway định tuyến → service kiểm tra và xử lý → frontend nhận response, cập nhật state và render.

**Ví dụ nộp luyện tập:** nút nộp → `submitPractice()` → API bài tập → `AssignmentService` → lưu submission và đưa vào queue. Kết quả chấm được lấy ở những request tiếp theo.

### Câu 9. State phía web là gì? Có phải database không?

State là dữ liệu JavaScript đang phục vụ màn hình: người dùng, trang hiện tại, phòng, nội dung editor, bài đang chọn… Nó không thay thế database. Một số dữ liệu được lưu thêm vào storage của trình duyệt để khôi phục giao diện.

**Ví dụ:** gõ code trong editor làm thay đổi bản nháp; chưa có nghĩa server đã ghi một bài nộp chính thức.

### Câu 10. Frontend ẩn nút của học sinh đã đủ để bảo vệ quyền giáo viên chưa?

Chưa. Ẩn nút giúp giao diện phù hợp vai trò, nhưng backend vẫn phải kiểm tra tài khoản, quyền, thành viên phòng và trạng thái nghiệp vụ. Người dùng có thể tự tạo request mà không bấm nút.

**Ví dụ:** học sinh tự gửi yêu cầu MERGE vẫn phải bị backend từ chối khi không đủ quyền.

### Câu 11. Token đăng nhập và userId khác nhau thế nào?

`userId` xác định tài khoản. Token web xác định phiên truy cập và được gửi trong header `Authorization`. Gateway liên hệ phiên web với phiên đã xác thực ở backend. Token trong dự án không nên tự gọi là JWT khi source không triển khai theo dạng đó.

**Ví dụ:** cùng một tài khoản đăng nhập ở hai trình duyệt vẫn cùng userId nhưng có thể có hai phiên truy cập khác nhau.

### Câu 12. Tại sao web phải polling?

Polling là định kỳ gửi request hỏi dữ liệu mới. Nó giúp web cập nhật sự kiện, trạng thái phòng hoặc kết quả chấm mà không cần người dùng bấm tải lại. Source hiện dùng HTTP polling; không nên mô tả là hệ thống WebSocket.

**Ví dụ:** bài đang QUEUED, sau đó RUNNING rồi DONE; web hỏi kết quả để cập nhật trạng thái hiển thị.

### Câu 13. Vì sao Ctrl+Shift+R có thể gây lỗi điều hướng nếu quản lý state sai?

Tải lại trang tạo lại bộ nhớ JavaScript. Frontend phải khôi phục phiên và giao diện từ thông tin đã lưu, đồng thời xác nhận lại trạng thái ở server. Nếu chỉ giữ một phần trạng thái hoặc áp dụng response cũ sau khi người dùng đổi trang, giao diện có thể hiện sai.

Source có cơ chế nhớ/khôi phục view và chặn một số response đã lỗi thời. Đây là phần cần đọc khi tìm lỗi quay ra ngoài hoặc sai sidebar sau refresh.

**Đọc code nhóm 2:** [events.js](../web-client/static/core/events.js), [api.js](../web-client/static/core/api.js), [state.js](../web-client/static/core/state.js), [lifecycle.js](../web-client/static/core/lifecycle.js), [polling.js](../web-client/static/core/polling.js), [auth.js](../web-client/static/features/auth/auth.js).

## 3. Phòng học và cuộc họp

### Câu 14. Mã phòng và ID phòng khác nhau ở đâu?

Mã phòng là chuỗi công khai người dùng nhập và tìm kiếm. ID phòng là định danh nội bộ dùng để liên kết dữ liệu. Không nên lấy tên hiển thị làm khóa cho mọi dữ liệu của phòng.

**Ví dụ:** mã `Lớp Java A` có thể tương ứng một ID nội bộ dạng số. Các bài và lịch sử liên quan dùng định danh nội bộ theo thiết kế backend.

### Câu 15. Tạo mã phòng bất kỳ có thật sự không có giới hạn không?

Không. Source chuẩn hóa mã, kiểm tra độ dài 1–80 ký tự và loại ký tự không hợp lệ như xuống dòng/ký tự điều khiển. Mã phòng đang hoạt động còn phải thỏa điều kiện không trùng theo quy tắc so sánh của hệ thống.

**Ví dụ:** người dùng tự đặt mã có nghĩa như `Java-A`; không phải chèn tùy ý mọi dữ liệu vào mã phòng.

### Câu 16. Tìm phòng và phân trang phải phối hợp ra sao?

Danh sách được tìm/lọc và phân trang ở backend, mỗi trang 5 phòng. Frontend gửi từ khóa và trang mong muốn rồi hiển thị kết quả. Tìm kiếm không chỉ dò những dòng đang thấy trên trang hiện tại.

**Ví dụ:** phòng A ở trang sau vẫn phải tìm được khi tìm `Phòng a`, theo quy tắc chuẩn hóa tìm kiếm hiện có.

### Câu 17. Rời cuộc họp có giống rời phòng không?

Không. Rời cuộc họp đưa người dùng về màn hình chính và dừng media của cuộc họp, nhưng giữ tư cách thành viên phòng. Rời phòng là thao tác riêng trong danh sách phòng, gửi yêu cầu rời membership và dọn trạng thái phòng trên client.

**Ví dụ:** rời cuộc họp xong vẫn có thể thấy phòng hiện tại và bấm vào lại. Bấm Rời phòng thì không còn giữ membership đó.

### Câu 18. Không còn ai trong phòng thì phòng có tự mất không?

Theo thiết kế hiện tại, phòng trống được giữ lại để người dùng chủ động xóa. Backend có thể khôi phục danh sách phòng đang hoạt động khi khởi động lại; không coi phòng trống là yêu cầu xóa.

### Câu 19. Xóa phòng có xóa sạch mọi lịch sử trong database không?

Luồng hiện tại đánh dấu phòng ngừng hoạt động và xử lý các thành viên đang liên quan; dữ liệu lịch sử vẫn được giữ theo cơ chế lưu trữ. Không nên mô tả nút Xóa như lệnh xóa vật lý toàn bộ mọi bảng.

**Ví dụ:** tạo lại cùng mã sau khi xóa có thể tạo phòng với ID nội bộ mới; không nên trộn nó với lịch sử phòng cũ chỉ vì mã giống nhau.

**Đọc code nhóm 3:** [room.js](../web-client/static/features/room/room.js), [RoomCode.java](../server/src/studyfix/RoomCode.java), [Server.java](../server/src/studyfix/Server.java), [StudyFixGateway.java](../server/src/studyfix/StudyFixGateway.java).

## 4. Master, nhánh riêng và quyền sửa

### Câu 20. Master là gì? Nhánh riêng là gì?

Master là code chung trong phiên giảng dạy. Nhánh riêng là bản làm việc của từng học sinh trong vòng thực hành. Chỉnh nhánh riêng không lập tức thay đổi Master của cả lớp.

**Ví dụ:** Master có lỗi vòng lặp; mỗi học sinh sửa một cách trên nhánh của mình, giáo viên theo dõi và chọn lời giải.

### Câu 21. Các phase của phiên học dùng để làm gì?

Phase xác định hoạt động nào hợp lệ tại thời điểm hiện tại: WAITING là chờ; LECTURE là giảng; BRANCHING là làm nhánh; MERGING là giai đoạn dừng sửa và chọn lời giải; ENDED là kết thúc. Service kiểm tra hành động dựa trên phase và các điều kiện liên quan.

**Ví dụ luồng:** bắt đầu giảng → tạo nhánh cho học sinh → dừng sửa để xem lời giải → merge → tiếp tục giảng. Không phải mọi nút đều hợp lệ ở mọi phase.

### Câu 22. Merge ở đây có giống Git merge không?

Không hoàn toàn. Luồng lớp học chọn code của một nhánh để cập nhật Master và ghi phiên bản/trạng thái liên quan. Không nên tuyên bố có thuật toán Git three-way merge hoặc tự giải quyết xung đột nếu source không triển khai.

**Ví dụ:** giáo viên chọn nhánh đúng của An; nội dung được chọn trở thành code Master mới.

### Câu 23. Tại sao phải kiểm tra version trước khi lưu hoặc áp dụng kết quả?

Version giúp phát hiện thao tác dựa trên dữ liệu cũ. Trong lớp học còn phải xét vòng, phase và phiên bản Master/nhánh phù hợp, tránh một kết quả chấm cũ ghi đè hoặc gắn nhầm vào trạng thái mới.

**Ví dụ:** gửi kiểm tra nhánh v2 rồi sửa thành v3; kết quả của snapshot v2 không được coi là kết quả của code v3.

### Câu 24. Quyền sửa code chung khác quyền sửa nhánh thế nào?

Code chung có cơ chế kiểm soát người giữ quyền sửa, token và phiên chỉnh sửa; nhánh riêng được kiểm soát theo danh tính học sinh và trạng thái lớp học. Hai cơ chế phục vụ hai ngữ cảnh, không nên giải thích thành mọi người cùng sửa tự do một chuỗi code.

### Câu 25. Vì sao sửa Master và đổi phase phải lưu nhất quán?

Nếu chỉ lưu được một phần, lần đọc sau có thể thấy code mới nhưng phase cũ. `ClassroomRepository` kết hợp với transaction của `Store` để lưu các thay đổi liên quan trong core database trước khi công bố trạng thái tương ứng trong RAM.

**Đọc code nhóm 4:** [classroom.js](../web-client/static/features/classroom/classroom.js), [ClassroomService.java](../server/src/studyfix/ClassroomService.java), [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java), [Room.java](../server/src/studyfix/Room.java).

## 5. Luyện code và chấm bài

### Câu 26. Luyện code có cần vào phòng hoặc được giao bài không?

Không. Các request `PRACTICE_*` xác thực người dùng nhưng không yêu cầu membership phòng như bài tập được giao. Giáo viên và học sinh dùng chung giao diện luyện tập; dữ liệu tiến độ/bài nộp vẫn gắn với tài khoản tương ứng.

### Câu 27. Mở một bài luyện tập thì frontend lấy gì?

Frontend lấy đề công khai, thông tin bài, test được công khai và tiến độ cá nhân cần cho màn hình. Hidden test và lời giải tham chiếu không phải dữ liệu để trả nguyên về trình duyệt cho người làm bài.

**Ví dụ:** trang có mô tả input/output và ví dụ để hiểu đề; chấm chính thức còn có thể dùng test ẩn ở backend.

### Câu 28. Gõ code, chạy thử và nộp bài khác nhau thế nào?

Gõ code tạo bản nháp. Chạy thử gửi code để thực thi theo test công khai trong luồng luyện tập. Nộp chính thức tạo một phiên bản nộp và chấm theo bộ test được cấu hình cho bài. Bản nháp không phải một kết quả nộp.

**Ví dụ:** chạy thử đúng ví dụ chưa đủ chứng minh nộp chính thức sẽ AC nếu còn trường hợp biên trong test ẩn.

### Câu 29. Một lần nộp code đi qua những bước nào?

1. Web gửi nội dung code và khóa nhận biết request.
2. Gateway xác thực phiên và chuyển vào service.
3. Service kiểm tra dữ liệu, lưu submission chứa snapshot source.
4. Công việc được đưa vào queue chấm.
5. Worker lấy công việc, gọi sandbox và nhận kết quả.
6. Backend lưu kết quả; web hỏi API để cập nhật giao diện/lịch sử.

Không phải bấm nộp là trình duyệt trực tiếp chạy container Docker.

### Câu 30. Vì sao cần queue và worker thay vì chấm ngay trong request HTTP?

Biên dịch và chạy test mất thời gian, còn nhiều người có thể nộp đồng thời. Queue giữ công việc chờ; worker xử lý với mức song song có kiểm soát. API không phải giữ toàn bộ một lượt chấm trong thời gian chờ phản hồi ban đầu.

**Điểm cần nhớ:** queue có giới hạn, không phải cứ thêm người nộp là tài nguyên tự tăng vô hạn.

### Câu 31. Docker dùng để làm gì? Cài Docker trên máy học sinh có bắt buộc không?

Docker ở máy chấm tạo môi trường tách biệt để biên dịch/chạy code và áp dụng giới hạn tài nguyên. Với học sinh chỉ truy cập web, Docker được backend gọi ở máy chạy dịch vụ chấm; học sinh không phải cài Docker chỉ để gửi bài từ trình duyệt.

**Ví dụ:** trình duyệt trên máy B gửi code tới server ở máy A; Docker trên A thực hiện việc chấm.

### Câu 32. Source đang giới hạn việc chạy code bằng cách nào?

`DockerJavaSandbox` cấu hình các giới hạn như bộ nhớ, CPU, số process, thời gian và dung lượng output; tắt mạng container, hạn chế quyền và filesystem. Đây là biện pháp giảm rủi ro, không nên kết luận là chứng minh an toàn tuyệt đối.

**Ví dụ:** chương trình vòng lặp vô hạn phải bị giới hạn thời gian, thay vì chạy mãi và giữ worker.

### Câu 33. AC, WA, CE, RE và TLE nghĩa là gì?

| Trạng thái | Cách hiểu |
|---|---|
| AC | Kết quả được chấp nhận theo bộ test của bài |
| WA | Chạy được nhưng kết quả không đúng |
| CE / COMPILE_ERROR | Code không biên dịch được |
| RE / RUNTIME_ERROR | Có lỗi trong lúc chạy |
| TLE / TIME_LIMIT_EXCEEDED | Vượt thời gian cho phép |

Các lỗi bộ nhớ/output và lỗi hạ tầng cũng có cách biểu diễn riêng. Tên chính xác phải xem verdict backend trả về và formatter frontend; các chữ viết tắt trên giúp hiểu ý nghĩa, không phải khẳng định mọi response dùng cùng một tên.

### Câu 34. Vì sao bài đã AC nên vẫn xanh dù lần nộp sau sai?

Tiến độ luyện tập hiện ưu tiên việc tài khoản đã từng AC. Trong khi đó, mỗi dòng lịch sử phản ánh kết quả riêng của lần nộp đó. Trạng thái tổng quan và trạng thái submission là hai thông tin khác nhau.

**Ví dụ:** v1 WA, v2 AC, v3 WA → bài vẫn được đánh dấu đã giải; lịch sử vẫn ghi v3 sai.

### Câu 35. SubmissionId và version khác nhau thế nào?

`submissionId` xác định đúng một lần gửi để truy vấn kết quả. Version chính thức tăng trong phạm vi tài khoản và bài luyện tập. Chạy thử có `is_run` riêng và version 0 trong cơ chế lưu hiện tại; không tăng số phiên bản nộp chính thức.

**Ví dụ:** hai học sinh đều có v1 của cùng bài, nhưng các submissionId khác nhau.

### Câu 36. Sửa editor sau khi nộp có làm thay đổi code trong lịch sử không?

Không. Submission lưu snapshot source tại lúc tiếp nhận. Editor hiện tại là bản làm việc khác; mở lại lịch sử phải xem source của submission được chọn.

**Ví dụ:** nộp v1 rồi sửa vòng lặp nhưng chưa nộp tiếp; kết quả v1 vẫn gắn với vòng lặp cũ.

### Câu 37. IdempotencyKey giải quyết chuyện gì?

Nó giúp backend nhận biết lần gửi lại của cùng một yêu cầu. Trong luồng practice, khóa được gắn với người dùng và được kiểm tra cùng bài, loại chạy/nộp, nội dung source. Không được dùng cùng khóa để âm thầm thay nội dung của một submission cũ.

**Ví dụ:** mất response sau khi server đã nhận bài; retry đúng khóa và nội dung giúp tránh tạo hai lần nộp cho một thao tác.

### Câu 38. Vì sao có QUEUED, RUNNING và DONE?

Chúng mô tả tiến độ xử lý: chờ worker, đang chấm và đã kết thúc. DONE chưa có nghĩa AC; phải đọc verdict để biết bài đúng, sai hay có lỗi.

**Ví dụ:** `status=DONE` và `verdict=WRONG_ANSWER` nghĩa là hệ thống đã chấm xong và kết quả sai.

### Câu 39. Máy chưa chạy Docker thì có phải mọi chức năng đều hỏng không?

Không nhất thiết. Chức năng cần chạy/chấm code sẽ bị ảnh hưởng; việc xem giao diện, đăng nhập hoặc đọc danh sách bài còn phụ thuộc các dịch vụ khác. Phải kiểm tra lỗi thực tế để phân biệt lỗi Docker với lỗi HTTP, xác thực hoặc database.

Lỗi hạ tầng không nên được giải thích thành học sinh giải sai; frontend hiện có cách hiển thị riêng cho lỗi dịch vụ.

**Đọc code nhóm 5:** [practice.js](../web-client/static/features/exercises/practice.js), [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java), [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java). Tìm `DockerJavaSandbox` trong bản đồ backend của [hướng dẫn logic](HUONG_DAN_LOGIC_SOURCE_STUDYFIX_VI.md) để đọc cấu hình chạy thực tế.

## 6. Chat, mic, camera và chia sẻ màn hình

### Câu 40. Chat và âm thanh cuộc họp có đi cùng một đường không?

Không. Chat là dữ liệu nghiệp vụ được backend xử lý và lưu theo luồng phòng. Mic/camera/màn hình web dùng WebRTC; gateway hỗ trợ trao đổi tín hiệu để các peer thiết lập kết nối.

**Ví dụ:** chat hoạt động không đủ để kết luận đường truyền camera hoặc quyền truy cập mic cũng hoạt động.

### Câu 41. Signaling là gì? Có phải video đi qua request signaling không?

Signaling trao đổi các thông tin thiết lập kết nối như offer, answer và ICE candidate. Nó giúp các trình duyệt tìm cách kết nối media. Không nên mô tả các request này là nơi chứa toàn bộ luồng video.

**Ví dụ dễ hiểu:** signaling giống trao đổi thông tin để hai bên gọi được cho nhau; media là cuộc trao đổi sau khi kết nối được thiết lập.

### Câu 42. Vì sao bật camera của mình thấy được nhưng người khác không thấy?

Xem camera bản thân chỉ xác nhận trình duyệt lấy được hình và hiển thị local preview. Người khác nhận được còn cần signaling đúng, peer connection thành công, track được gửi và phần tử media phía nhận được gắn đúng stream.

Đây là các điểm kiểm tra, không phải kết luận trước nguyên nhân. Source hiện không có triển khai TURN riêng để khẳng định mọi cấu hình mạng đều kết nối được.

### Câu 43. Vì sao chia sẻ màn hình có hình YouTube nhưng không có âm thanh?

Luồng hình và âm thanh là các track riêng. Việc có âm thanh phụ thuộc lựa chọn bề mặt chia sẻ, quyền và khả năng trình duyệt/OS, tùy chọn chia sẻ audio, cùng việc frontend có gửi track audio nhận được hay không.

**Ví dụ:** chỉ nhận được video track thì người khác thấy hình nhưng không thể tự sinh ra âm thanh từ hình đó. Khi debug cần xem stream có audio track trước rồi mới xem phía nhận.

### Câu 44. Số tin chưa đọc có phải bằng tổng số tin trong phòng không?

Không. Frontend xét mốc đã đọc theo tài khoản/phòng và loại trừ tin do chính người dùng gửi. Việc đánh dấu đã đọc còn liên quan tới trạng thái hiển thị, focus và vị trí cuối khung chat. Popup tin mới và bộ đếm chưa đọc là hai hành vi liên quan nhưng khác nhau.

**Điểm cần nhớ:** cơ chế này không tương đương tính năng server xác nhận từng thành viên đã đọc từng tin.

**Đọc code nhóm 6:** [chat-notifications.js](../web-client/static/features/classroom/chat-notifications.js), [call.js](../web-client/static/features/classroom/call.js), [screen-share.js](../web-client/static/features/screen-share/screen-share.js), [MeetingCalls.java](../server/src/studyfix/MeetingCalls.java).

## 7. Dữ liệu, đồng thời và độ tin cậy

### Câu 45. Dự án dùng một hay hai database chính?

Launcher mặc định dùng hai SQLite database: `studyfix.db` cho lõi như tài khoản, phòng, revision và lớp học; `studyfix-learning.db` cho ngân hàng bài, giao bài, luyện tập và thi. Đường dẫn có thể thay đổi bằng cấu hình.

Không nên suy ra mọi thay đổi trên hai database đều nằm trong một transaction chung.

### Câu 46. Service khác repository như thế nào?

Service quyết định quy tắc nghiệp vụ; repository chịu trách nhiệm truy cập dữ liệu. Gateway nhận request/xác thực/điều phối theo vai trò của nó. Việc chia lớp giúp dùng lại nghiệp vụ và tránh viết SQL, kiểm tra quyền, render giao diện lẫn vào một chỗ.

**Ví dụ:** service kiểm tra có được nộp và dùng khóa nào; repository tạo bản ghi bài nộp và đọc lịch sử.

### Câu 47. Mật khẩu có được lưu nguyên văn không?

Source dùng cơ chế băm mật khẩu có salt bằng PBKDF2, thay vì lưu mật khẩu nguyên văn. Khi đăng nhập, hệ thống kiểm tra mật khẩu nhập dựa trên thông tin băm đã lưu.

**Ví dụ:** database không nên có cột để đọc lại trực tiếp mật khẩu người dùng đã nhập. Băm mật khẩu cũng không thay thế bảo vệ đường truyền HTTP/HTTPS.

### Câu 48. Nhiều người thao tác cùng lúc thì synchronized giải quyết gì?

Nó bảo vệ các đoạn truy cập trạng thái dùng chung cần nhất quán, chẳng hạn cập nhật code/version của Room. Nó không tự giải quyết mọi lỗi nghiệp vụ hoặc thay thế transaction database.

**Ví dụ:** cần đọc code và version cùng một thời điểm hợp lệ; nếu đọc rời rạc trong lúc người khác cập nhật thì có thể ghép code cũ với version mới.

### Câu 49. Có ConcurrentHashMap rồi có cần kiểm tra nghiệp vụ nữa không?

Có. Map hỗ trợ thao tác đồng thời trên cấu trúc dữ liệu; nó không tự đảm bảo một chuỗi việc như kiểm tra phase → kiểm tra quyền → ghi code → tăng version được thực hiện đúng toàn bộ.

**Ví dụ:** map an toàn không có nghĩa hai yêu cầu MERGE đồng thời đều được phép thành công trên cùng một phiên bản cũ.

### Câu 50. Queue chấm của luyện tập và kiểm tra nhánh có phải một không?

Không. Luyện tập, giao bài và thi đi qua queue của `AssignmentService`. Kiểm tra nhánh lớp học dùng grader/queue riêng trong `ClassroomService`. Hai nơi cùng dùng sandbox không có nghĩa dùng chung một hàng đợi duy nhất.

**Đọc code nhóm 7:** [Store.java](../server/src/studyfix/Store.java), [Passwords.java](../server/src/studyfix/Passwords.java), [Room.java](../server/src/studyfix/Room.java), [ClassroomService.java](../server/src/studyfix/ClassroomService.java), [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java).

## 8. Câu hỏi tình huống để tập tự phân tích

### Câu 51. Máy khác không mở được trang thì kiểm tra theo thứ tự nào?

Kiểm tra server web có chạy và lắng nghe đúng cổng; máy truy cập có dùng đúng IP/cổng/giao thức; hai máy có đường mạng tới nhau; firewall có cho phép kết nối. Dùng `localhost` trên máy học sinh sẽ trỏ tới chính máy học sinh.

**Ví dụ:** server ở máy A thì máy B phải mở địa chỉ của A, không dùng `localhost:8080` trừ khi B cũng chạy server. Phải lấy IP hiện tại, không mặc định dùng lại IP từ buổi trước.

### Câu 52. Sửa source rồi tải lại trình duyệt mà backend vẫn chạy logic cũ thì sao?

Có thể tiến trình Java vẫn đang chạy bản build cũ. Cần phân biệt source, file đã build và tiến trình đang chạy. Thay đổi Java cần build và khởi động lại đúng dịch vụ; Ctrl+Shift+R chỉ yêu cầu trình duyệt tải lại tài nguyên.

Với frontend cũng cần xem gateway đang phục vụ `dist` hay `static`, tránh sửa một bản nhưng trình duyệt nhận bản khác.

### Câu 53. Nộp bài thành công rồi bị mất mạng thì xử lý thế nào?

Không vội tạo một yêu cầu mới với khóa mới nếu chưa biết lần trước đã được nhận hay chưa. Luồng frontend có cơ chế theo dõi request/submission; retry đúng idempotency key và lấy kết quả giúp tránh nộp trùng. Backend vẫn có thể tiếp tục chấm khi trình duyệt tạm mất kết nối.

### Câu 54. Đổi tài khoản mà thấy bản nháp của người trước thì nên đọc đâu?

Đọc quản lý owner/scope của practice, các Map trong RAM và khóa localStorage. Hướng dẫn logic đã ghi nhận cần kiểm tra kỹ việc reset `drafts`/`requests` khi đổi tài khoản; không nên khẳng định mọi dữ liệu nháp đã được cô lập chỉ vì màn hình chọn bài được reset.

**Tình huống kiểm tra:** A mở bài X và gõ nháp → đăng xuất → B đăng nhập → mở lại X. Quan sát editor và request theo đúng tài khoản. Đây là kịch bản cần kiểm tra, không phải khẳng định đã sửa lỗi.

### Câu 55. Làm sao chứng minh mình hiểu một chức năng thay vì chỉ nhớ tên file?

Chọn một thao tác và trả lời được:

1. Ai được thực hiện và trong điều kiện nào?
2. Sự kiện frontend gọi hàm nào, gửi dữ liệu gì?
3. Gateway/service kiểm tra gì ở backend?
4. Dữ liệu nào thay đổi trong RAM và database?
5. Web nhận/hiển thị kết quả bằng cách nào?
6. Nếu mất mạng, request lặp hoặc version cũ thì xử lý ra sao?

**Bài tập:** lần theo Nộp luyện tập bằng `practice.js` → `core/api.js` → `WebGatewayServer.assignmentRequest()` → `AssignmentTcpServer.handleWebRequest()` → `AssignmentService` → `PracticeRepository` → worker/sandbox → API kết quả → render.

## 9. Mười câu tự kiểm tra cuối cùng

Che các phần đáp án phía trên và tự giải thích bằng lời của bạn:

1. Vì sao trang giáo viên là client dù mở trên máy server? — Xem câu 3.
2. Vì sao chỉ dùng web vẫn cần TCP lõi? — Câu 5.
3. Một nút nộp bài đi tới Docker qua những lớp nào? — Câu 8, 29–31.
4. Gõ nháp, chạy thử và nộp chính thức khác gì? — Câu 28, 35–36.
5. Vì sao DONE chưa chắc AC? — Câu 33, 38.
6. Rời cuộc họp khác rời phòng thế nào? — Câu 17.
7. Vì sao kết quả chấm nhánh phải gắn với version? — Câu 23.
8. Vì sao thấy camera mình chưa chứng minh bạn cùng phòng thấy? — Câu 42.
9. Vì sao AC của bài và kết quả lần nộp cuối có thể khác màu? — Câu 34.
10. Khi mất response nộp bài, điều gì giúp tránh tạo bài trùng? — Câu 37, 53.

**Thứ tự học đề xuất:** ngày đầu đọc nhóm 1–3; tiếp theo đọc nhóm 4–5 và tự lần một yêu cầu trong source; sau đó đọc nhóm 6–8. Khi đã giải thích được bằng ví dụ của mình, dùng [bản đồ source](HUONG_DAN_LOGIC_SOURCE_STUDYFIX_VI.md) để đọc sâu từng hàm.
