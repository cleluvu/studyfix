# Server Manager: phòng, nhật ký, Master và nhánh học sinh

Cập nhật 04/11/2026.

Các sửa đổi đồng bộ phiên, heartbeat, ghi kết quả chấm và tối ưu giám sát được mô tả trong [Sửa độ tin cậy backend](SERVER_RELIABILITY_FIXES_VI.md).

## Những gì hiển thị

- **Mã phòng:** chuỗi giáo viên đặt, cùng mã dùng trên web. **ID nội bộ** ở cột riêng; ID tăng dần để liên kết dữ liệu, không phải tên phòng.
- **Giáo viên tạo phòng:** tên hiển thị và username, thay cho chỉ UUID owner.
- **Master version:** phiên bản code chung, không phải phiên bản nhánh của từng học sinh.
- **Giai đoạn / vòng:** WAITING, LECTURE, BRANCHING, MERGING, ENDED và vòng hiện tại.
- **Master và lịch sử:** các revision code chung của phòng được chọn. Khi đang xem bản mới nhất, có revision mới thì tự chuyển tới bản mới. Khi chủ động chọn bản cũ, giữ lựa chọn để đọc. Đổi phòng sẽ chọn revision mới nhất của phòng mới.
- **Nhánh học sinh và kiểm tra:** code nhánh đã lưu trong vòng hiện tại, học sinh/username/version, trạng thái kiểm tra và tối đa 20 lượt kiểm tra gần nhất. Kết quả của version cũ được ghi rõ. Đây là màn hình chỉ xem; không thay thế thao tác Merge của giáo viên trên web.
- **Nhật ký hoạt động:** tối đa 500 dòng gần nhất trong bộ nhớ của tiến trình. Có tên/mã phòng và danh tính ở sự kiện tạo/vào/rời phòng, đăng nhập, hành động phiên học, lưu nhánh và kiểm tra. Nhật ký không tự khôi phục những sự kiện trước khi chạy bản server này; dữ liệu lịch sử lớp học trong SQLite là phần lưu riêng.

## Vì sao học sinh AC nhưng Master không đổi?

AC là kết quả kiểm tra snapshot nhánh học sinh. Nhánh được lưu thành công vẫn là nhánh riêng. Giáo viên cần **Dừng sửa và chọn lời giải → mở nhánh → chọn làm Master**. Sau MERGE, Master mới tăng version và có revision mới. Không tự merge mọi bài AC vì có thể nhiều học sinh đưa ra các lời giải khác nhau.

## Cách kiểm tra thủ công

1. Dừng Server Manager cũ bằng nút dừng, đóng cửa sổ và chạy lại launcher để build/nạp code mới. Không chạy hai server cùng cổng.
2. Giáo viên đăng nhập web, tạo mã phòng dễ nhận biết. Đối chiếu cột Mã phòng và Giáo viên tạo phòng.
3. Học sinh vào phòng. Xem sự kiện vào phòng có mã và tài khoản; rời phòng để xem sự kiện rời.
4. Giáo viên bắt đầu giảng, chọn bài DEBUG_COLLAB và tách nhánh. Học sinh sửa/lưu/chạy kiểm tra.
5. Trên Server Manager, chọn đúng phòng rồi mở tab Nhánh học sinh và kiểm tra. Code phải khớp bản đã lưu; trạng thái chấm sẽ cập nhật sau lượt polling của Manager.
6. Kiểm tra Master chưa tự thay bằng code học sinh. Giáo viên FREEZE rồi MERGE; tab Master và lịch sử phải có revision mới.
7. Chọn một revision cũ, tạo thêm revision Master mới. Màn hình giữ revision cũ để đọc; chọn bản cuối để theo dõi những commit tiếp theo.

## Code và kiểm thử

`ServerMonitor.capture()` đọc dữ liệu giám sát ở background; UI cập nhật trên EDT. `ServerAdmin` render bảng, lịch sử và nhánh. Store cung cấp nhãn tài khoản; Room/Server/ClassroomService ghi sự kiện sau các thao tác tương ứng.

Target `server-monitor-test` chạy HTTP/TCP/SQLite thật với database/cổng riêng và bộ chấm giả lập có kiểm soát. Nó xác minh mã phòng, chủ phòng, nhánh, stale result, Master sau Merge, log và lựa chọn lịch sử. Bộ chấm giả lập chỉ phục vụ kiểm thử; không phải kết quả Docker thực tế. Target này được đưa vào `integration-test`.
