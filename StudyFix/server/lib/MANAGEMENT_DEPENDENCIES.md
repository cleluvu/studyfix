# Management dependencies

Pinned JARs are included under `lib` and loaded by Ant, NetBeans and both launchers.
Run `install-management-dependencies.ps1` to verify SHA-256 or retrieve missing files from Maven Central.

| Library | Version | Purpose | Upstream |
|---|---|---|---|
| MySQL Connector/J | 9.5.0 | JDBC for MySQL management tables | https://dev.mysql.com/doc/connector-j/en/ |
| Java-WebSocket | 1.6.0 | RFC6455 server, frame validation and TLS | https://github.com/TooTallNate/Java-WebSocket |
| SLF4J API + Simple | 2.0.16 | Library logging | https://www.slf4j.org/ |

Upstream licenses/notices are embedded in the respective JAR files. SQL migration is packaged as `studyfix/db/mysql-schema.sql`.
