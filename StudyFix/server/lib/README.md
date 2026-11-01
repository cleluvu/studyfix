# SQLite JDBC

- Dependency: `org.xerial:sqlite-jdbc:3.53.0.0` (JAR đã kèm trong thư mục này).
- [Tài liệu chính thức Xerial](https://github.com/xerial/sqlite-jdbc).
- [Artifact Maven Central](https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.0.0/sqlite-jdbc-3.53.0.0.jar).
- SHA-256: `303e8150100982f2ed7d1b82d897278ef7744bd494c28ad4e7042b7914591697`.
- Driver và các notice/license có trong JAR; Xerial Apache-2.0/BSD-2-Clause, SQLite public domain. Không giải nén/sửa nội dung JAR.

Ant/NetBeans tham chiếu bằng `file.reference.sqlite-jdbc.jar` trong `nbproject/project.properties`. Driver kèm native SQLite cho các nền tảng được hỗ trợ. Bản này không cần JAR SLF4J bổ sung.

Nếu cần tải lại:

```powershell
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.0.0/sqlite-jdbc-3.53.0.0.jar' -OutFile 'lib/sqlite-jdbc-3.53.0.0.jar'
Get-FileHash 'lib/sqlite-jdbc-3.53.0.0.jar' -Algorithm SHA256
```

Thuật toán PBKDF2WithHmacSHA256 dùng JCA của Java: [Java 17 Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html).
