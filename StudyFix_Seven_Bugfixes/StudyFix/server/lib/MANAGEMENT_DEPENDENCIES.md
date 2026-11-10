# SQLite management dependencies

Pinned JARs are included under `lib` and loaded by Ant, NetBeans and both launchers.
Run `install-management-dependencies.ps1` to verify SHA-256 or retrieve missing files from Maven Central.

| Library | Version | Purpose | Upstream |
|---|---|---|---|
| Java-WebSocket | 1.6.0 | RFC6455 server, frame validation and TLS | https://github.com/TooTallNate/Java-WebSocket |
| SLF4J API + Simple | 2.0.16 | Library logging | https://www.slf4j.org/ |

Upstream licenses/notices are embedded in the respective JAR files. SQL migration is packaged as `studyfix/db/sqlite-management-schema.sql`.
