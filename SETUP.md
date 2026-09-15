# Local Setup Guide — auditlog-service

This guide walks you through setting up and running the `auditlog-service` project locally on **Windows** and **macOS**.

## Project Overview

- **Framework:** Spring Boot 4.1.1 (Java)
- **Build tool:** Apache Maven (no Maven wrapper included — Maven must be installed)
- **Java version:** **JDK 25** (required — see `java.version` in `pom.xml`)
- **Database:** H2 in-memory database (no external DB install needed)
- **API style:** Contract-first — API interfaces/models are generated from `src/main/resources/static/openapi.yml` by the `openapi-generator-maven-plugin` during the build
- **Code coverage:** JaCoCo enforces a **90% line coverage** gate on `mvn verify`

---

## 1. Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 25 | Compile and run the service |
| Maven | 3.9+ | Build, test, and run |
| Git | any recent | Clone the repository |

Any JDK 25 distribution works (Eclipse Temurin, Azul Zulu, Oracle, OpenJDK, Microsoft OpenJDK).

### Windows

**Option A — winget (recommended):**

```powershell
winget install EclipseAdoptium.Temurin.25.JDK
winget install Apache.Maven
```

**Option B — manual install:**

1. Download a JDK 25 build (e.g. Temurin or Zulu) and install/extract it.
2. Set environment variables (Settings → System → About → Advanced system settings → Environment Variables):
   - `JAVA_HOME` = path to the JDK, e.g. `C:\Program Files\Eclipse Adoptium\jdk-25`
   - Add `%JAVA_HOME%\bin` to `Path`
3. Download the [Maven binary zip](https://maven.apache.org/download.cgi), extract to e.g. `C:\Program Files\apache-maven-3.9.9`, and add `C:\Program Files\apache-maven-3.9.9\bin` to `Path`.
4. Open a **new** terminal so the updated `Path` takes effect.

### macOS

**Option A — Homebrew:**

```bash
brew install openjdk        # installs the latest JDK (25+)
brew install maven

# Homebrew's openjdk is keg-only; link it so the system finds it:
sudo ln -sfn "$(brew --prefix)/opt/openjdk/libexec/openjdk.jdk" \
    /Library/Java/JavaVirtualMachines/openjdk.jdk
```

**Option B — SDKMAN (useful for managing multiple JDKs):**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-zulu    # or any 25-* distribution: sdk list java
sdk install maven
```

**Option C — manual install:** download a macOS JDK 25 build (Temurin/Zulu `.pkg` or `.tar.gz`) and install it. `JAVA_HOME` is then typically `/Library/Java/JavaVirtualMachines/<jdk>/Contents/Home`.

### Verify the prerequisites

Run these in a new terminal — both must succeed before continuing:

```bash
java -version    # must report version 25.x
mvn -version     # must report Maven 3.9.x and pick up the same Java 25
```

> If `mvn -version` shows a different Java version than `java -version`, fix `JAVA_HOME` first — the build will fail on older JDKs.

---

## 2. Get the Code

```bash
git clone <repository-url>
cd auditlog-service
```

If you already have the project folder, just open a terminal in the project root (the directory containing `pom.xml`).

---

## 3. Build the Project

```bash
mvn clean install
```

What happens during the build:

1. `openapi-generator` generates API interfaces and model classes from `src/main/resources/static/openapi.yml` into `target/generated-sources` — **do not edit these files**; edit `openapi.yml` instead.
2. Lombok annotation processing runs during compile (configured in `pom.xml` — no extra setup needed on the command line).
3. Tests run and JaCoCo produces a coverage report at `target/site/jacoco/index.html`.

To build without running tests:

```bash
mvn clean install -DskipTests
```

---

## 4. Run the Service

**Option A — Maven (recommended for development):**

```bash
mvn spring-boot:run
```

**Option B — run the packaged jar:**

```bash
mvn clean package -DskipTests
java -jar target/auditlog-service-0.0.1-SNAPSHOT.jar
```

The service starts on **http://localhost:8080**. Stop it with `Ctrl+C`.

> Note: the H2 database is **in-memory** — all data is lost when the service stops.

---

## 5. Verify It Works

### Swagger UI (interactive API docs)

Open in a browser:

```
http://localhost:8080/swagger-ui/index.html
```

The UI serves the contract-first spec from `src/main/resources/static/openapi.yml`.

### Try the API with curl

Create an audit event:

```bash
curl -X POST http://localhost:8080/v1/audit/events \
  -H "Content-Type: application/json" \
  -d '{"actorId":"user-1","action":"LOGIN","payload":"{\"ip\":\"10.0.0.1\"}"}'
```

List events:

```bash
curl http://localhost:8080/v1/audit/events
```

Verify the hash chain integrity:

```bash
curl http://localhost:8080/v1/audit/verify
```

Export events:

```bash
curl http://localhost:8080/v1/audit/export
```

(Exact request/response schemas are in `src/main/resources/static/openapi.yml` and the Swagger UI.)

### H2 console

The H2 web console is available at:

```
http://localhost:8080/h2-console
```

Connect with:

- **JDBC URL:** `jdbc:h2:mem:testdb`
- **User:** `sa`
- **Password:** *(leave empty)*

---

## 6. Run Tests

```bash
mvn test          # run all unit/integration tests
mvn verify        # tests + JaCoCo coverage gate (fails below 90% line coverage)
```

Open the coverage report after a test run:

- **Windows:** `start target\site\jacoco\index.html`
- **macOS:** `open target/site/jacoco/index.html`

---

## 7. IDE Setup

### IntelliJ IDEA

1. **File → Open** the project root (select `pom.xml` → "Open as Project").
2. Set **Project SDK** to JDK 25 (File → Project Structure → Project).
3. Install/enable the **Lombok plugin** and enable annotation processing (Settings → Build → Compiler → Annotation Processors) if not auto-configured.
4. Run `AuditlogServiceApplication` directly, or use the Maven tool window → `spring-boot:run`.

### VS Code

1. Install the **Extension Pack for Java** (includes Language Support for Java, Debugger, Maven, etc.) and the **Lombok** extension.
2. Open the project folder; the Java extension will detect `pom.xml` and import it.
3. Use the Spring Boot dashboard or press F5 on `AuditlogServiceApplication` to run/debug.

### Eclipse / STS

Import via **File → Import → Existing Maven Projects**. Ensure the workspace JDK is 25.

---

## 8. Configuration

All runtime config lives in `src/main/resources/application.yaml`:

| Property | Default | Description |
|----------|---------|-------------|
| `audit.events.retention-days` | `30` | Events older than this are archived by the scheduler |
| `audit.events.archive-interval-ms` | `86400000` (24h) | Delay between archive runs |
| `audit.events.archive-initial-delay-ms` | `60000` (1m) | Delay before the first archive run |
| `spring.datasource.url` | `jdbc:h2:mem:testdb` | In-memory H2 datasource |

Override any property at startup without editing the file, e.g.:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090 --audit.events.retention-days=7"
```

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `mvn: command not found` / `'mvn' is not recognized` | Maven isn't installed or not on `PATH`. Re-check step 1 and open a new terminal. |
| `invalid target release: 25` or `class file has wrong version` | `JAVA_HOME` points to an older JDK. Run `mvn -version` to see which Java Maven is using and fix `JAVA_HOME`. |
| `Cannot find symbol` on generated API/model classes in the IDE | Run `mvn compile` once so `target/generated-sources` exists; in IntelliJ, mark `target/generated-sources/openapi/src/gen/java/main` as Generated Sources Root (usually automatic after a Maven reload). |
| `Port 8080 was already in use` | Stop the other process, or run with `--server.port=<free-port>` as shown above. |
| Lombok errors like "cannot find symbol: getX()" in the IDE | Lombok plugin/annotation processing isn't enabled in the IDE (Maven CLI builds still work). See the IDE section. |
| macOS: `java -version` shows an old version after `brew install openjdk` | The `sudo ln -sfn ...` symlink step in step 1 was skipped, or the shell needs a restart. |

---

## 10. Useful Links

- API spec: `src/main/resources/static/openapi.yml`
- DB schema: `src/main/resources/sql/schema.sql`
- Feature docs: `docs/audit-events-pagination.md`, `docs/audit-export.md`
