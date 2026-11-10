# Bật HTTPS/WSS để dùng mic, camera và chia sẻ màn hình qua LAN

Áp dụng Chrome/Edge trên Windows, cùng mạng LAN. Script dùng `keytool` của JDK đã có, không cần mkcert/OpenSSL hay backend mới. Script không tự cài chứng chỉ vào Windows, mở Firewall hoặc khởi động lại dịch vụ.

## Máy server

Đóng Server Manager. Trong PowerShell tại thư mục dự án:

```powershell
cd C:\Users\ADMIN\Downloads\StudyFix_Seven_Bugfixes
$tls = & .\StudyFix\server\setup-lan-https.ps1 -ServerIp 192.168.1.251 -Configure
$tls
Copy-Item -LiteralPath $tls.CertificateFile -Destination .\StudyFix-LAN-root.cer
Import-Certificate -FilePath .\StudyFix-LAN-root.cer -CertStoreLocation Cert:\CurrentUser\Root
```

Nếu Windows hỏi xác nhận tin cậy CA, kiểm tra đúng chứng chỉ vừa tạo cho StudyFix rồi chấp nhận. Đây là thao tác tin cậy CA cục bộ cho tài khoản Windows đang dùng. Không cài chứng chỉ từ nguồn không xác định. Thumbprint trong kết quả `$tls` dùng để đối chiếu giữa các máy.

IP `192.168.1.251` lấy từ ảnh truy cập gần nhất. Nếu IP máy server khác, thay bằng IPv4 từ `ipconfig` trước khi chạy. SAN của chứng chỉ gồm IP đó, localhost, 127.0.0.1 và ::1.

Script tạo thư mục mới dưới `server/data/https`, không ghi đè chứng chỉ đã có. Khi có `-Configure`, chỉ cập nhật ba khóa TLS/WebSocket trong `.env`; giữ nguyên cấu hình MySQL và lưu bản sao `.env` cũ tại `env-before-https.backup` trong thư mục chứng chỉ. Mật khẩu PKCS12 sinh ngẫu nhiên, không hiện trong console/dòng lệnh. Khóa ký CA tạm bị xóa sau khi phát hành; file PKCS12 server và cấu hình vẫn là dữ liệu riêng tư cần giữ trên server.

Mỗi lần chạy tạo CA mới; cần cài lại file CA mới trên các máy. Không chạy lại nếu chỉ muốn mở ứng dụng. Khi đổi IP hoặc chứng chỉ server hết hạn (365 ngày), tạo và phân phối lại chứng chỉ.

Chạy server như cũ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
```

Nếu Terminal đang có biến môi trường TLS cũ, mở Terminal mới rồi chạy launcher; biến môi trường process được ưu tiên hơn `.env`.

Mở `https://localhost:8080`. Cổng 8080 bây giờ phục vụ HTTPS; đường dẫn HTTP cũ không còn dùng cho listener này. `/api/health` cần có `webSocket.tls=true`; frontend tự dùng WSS cổng 8081 theo cấu hình đang có.

## Mỗi máy sinh viên

Chỉ sao chép **file công khai `StudyFix-LAN-root.cer`** từ máy server sang máy sinh viên, ví dụ Downloads. Không sao chép `.env`, `https.env`, file `.p12` hay bản sao cấu hình có mật khẩu.

Trên máy sinh viên, mở PowerShell bằng đúng tài khoản Windows sử dụng trình duyệt:

```powershell
Import-Certificate -FilePath "$env:USERPROFILE\Downloads\StudyFix-LAN-root.cer" -CertStoreLocation Cert:\CurrentUser\Root
```

Đối chiếu thumbprint với máy server. Đóng rồi mở lại Chrome/Edge, truy cập:

```text
https://192.168.1.251:8080
```

Đăng nhập lại, xin giảng viên duyệt vào lớp, bấm Bật mic/Bật camera và chọn **Cho phép** khi trình duyệt hỏi. Phiên đăng nhập HTTP cũ không tự chuyển sang origin HTTPS.

## Khi gặp lỗi

- `ERR_CERT_AUTHORITY_INVALID`: chưa cài đúng root CA trên tài khoản Windows đang dùng, hoặc đang dùng file CA của lần tạo khác. Không coi việc bấm bỏ qua cảnh báo là cấu hình tin cậy đã hoàn tất.
- `ERR_CERT_COMMON_NAME_INVALID`: đang mở IP khác IP tạo chứng chỉ. Kiểm tra `ipconfig` và tạo lại với IP mới.
- Không truy cập được: server phải chạy HTTPS; cho phép cổng TCP 8080 và 8081 trên Firewall server cho mạng LAN thích hợp. Không cần mở cổng MySQL cho Student.
- Trang chạy nhưng báo dùng polling: kiểm tra WSS 8081/Firewall và cài CA đúng máy, đúng người dùng.
- Mic/camera vẫn bị từ chối: mở quyền của website trong trình duyệt và bật Camera/Microphone; kiểm tra Windows Settings → Privacy & security → Camera/Microphone và thiết bị không bị ứng dụng khác giữ độc quyền.
- F12 Console có thể kiểm tra `window.isSecureContext` phải là `true`, và `navigator.mediaDevices?.getUserMedia` phải tồn tại.
- Gỡ tin cậy sau khi không dùng: mở `certmgr.msc` → Trusted Root Certification Authorities → Certificates, đối chiếu thumbprint của StudyFix rồi xóa đúng chứng chỉ đó.

## Kiểm chứng đã chạy

Script đã được chạy trong thư mục `server/build` với file cấu hình kiểm thử, giữ nguyên khóa MySQL mẫu và không sửa `.env` vận hành. `HttpsLanIntegrationTest` đạt với HTTPS thực tế, kiểm tra chuỗi CA/SAN, đăng nhập HTTPS và xác thực/presence WSS trên server StudyFix. Log: `server/build/tls-integration-test.log`. Không cài CA kiểm thử vào Windows.

Chưa kiểm thử mic/camera vật lý giữa hai máy LAN; bước cài CA và cấp quyền trên từng máy vẫn cần người sử dụng thực hiện.

Nguồn: [JDK keytool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html), [Microsoft Import-Certificate](https://learn.microsoft.com/en-us/powershell/module/pki/import-certificate), [điều kiện secure context của getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia#privacy_and_security).
