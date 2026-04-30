# Running the Application

This guide covers system requirements, installation, and various ways to run SimpleDQC.

---

## System Requirements

### Java

| Requirement | Version |
|-------------|---------|
| Java Runtime | **Java 25** or higher |
| Java Vendor | OpenJDK, Oracle JDK, or any compatible JDK |

**Verify Java Installation:**
```bash
java -version
```

**Note:** This application uses Spring Boot 4.1.0 which requires Java 25.

### Build Tool

| Tool | Version |
|------|---------|
| Apache Maven | **3.9.0** or higher |

**Verify Maven Installation:**
```bash
mvn -version
```

### Memory

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| JVM Heap | 256 MB | 512 MB - 1 GB |
| System RAM | 1 GB | 2 GB |

### Database

The application supports multiple database options for both **source** and **repository** data:

| Database | Source | Repository |
|----------|--------|------------|
| MySQL | Yes | Yes |
| H2 (Embedded) | No | Yes |
| PostgreSQL | Yes | Yes |
| MariaDB | Yes | Yes |
| Oracle | Yes | Yes |
| SQL Server | Yes | Yes |

**Minimum Requirements for Source Database:**
- READ access for querying data quality controls
- (Optional) WRITE access if `dq.allow-write=true`

**Minimum Requirements for Repository Database:**
- CREATE TABLE permissions
- WRITE access for storing execution history
- READ access for retrieving history and known issues

---

## Database Drivers

The application uses the following JDBC drivers:

| Database | Driver Class | Maven Dependency |
|----------|--------------|------------------|
| MySQL | `com.mysql.cj.jdbc.Driver` | `mysql-connector-j` (included) |
| H2 | `org.h2.Driver` | `h2` (included) |
| PostgreSQL | `org.postgresql.Driver` | Add manually if needed |
| Oracle | `oracle.jdbc.OracleDriver` | Add manually if needed |
| SQL Server | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | Add manually if needed |

**Note:** MySQL and H2 drivers are included by default. For other databases, add the appropriate dependency to `pom.xml`.

---

## Installation

### Clone the Repository

```bash
git clone https://github.com/your-repository/SimpleDQC.git
cd SimpleDQC
```

### Build the Application

```bash
mvn clean package
```

This will:
1. Compile the source code
2. Run tests (if any)
3. Create a JAR file in the `target/` directory

**Expected Output:**
```
[INFO] Building dq 0.0.1-SNAPSHOT
[INFO] BUILD SUCCESS
```

---

## Running the Application

### Option 1: Using Maven

```bash
mvn spring-boot:run
```

**Advantages:**
- No need to build separately
- Automatic class reloading during development

**Disadvantages:**
- Slower startup than direct JAR execution

### Option 2: Using the Executable JAR

```bash
java -jar target/dq-0.0.1-SNAPSHOT.jar
```

**Advantages:**
- Faster startup
- Production-ready

**Note:** Replace `dq-0.0.1-SNAPSHOT.jar` with your actual JAR filename.

### Option 3: Using Java with Custom Configuration

```bash
java -Dserver.port=9090 -Dspring.profiles.active=prod -jar target/dq-0.0.1-SNAPSHOT.jar
```

This allows you to override configuration properties via command line.

### Option 4: With Custom JVM Options

```bash
java -Xms512m -Xmx1024m -jar target/dq-0.0.1-SNAPSHOT.jar
```

**Common JVM Options:**
- `-Xms256m` - Initial heap size
- `-Xmx512m` - Maximum heap size
- `-XX:MaxMetaspaceSize=256m` - Metaspace size
- `-Dfile.encoding=UTF-8` - File encoding

---

## Quick Start (Development Mode)

For a quick development setup with embedded H2 database:

### 1. Ensure Java 25 is installed

### 2. Build and run

```bash
mvn clean package
java -jar target/dq-0.0.1-SNAPSHOT.jar
```

### 3. Default Configuration

The application will start with:
- **Server Port**: 8090
- **Repository**: Embedded H2 database (file: `./repository/dq-repo.mv.db`)
- **Source Database**: MySQL (configure in `application.yaml`)
- **Demo Mode**: Enabled (creates demo tables if source DB is accessible)

### 4. Access the Application

- **API Endpoints**: `http://localhost:8090/api/`
- **Dashboard**: `http://localhost:8090/` (if static HTML is configured)

---

## Production Deployment

### Prerequisites

1. **Java 25** installed on the server
2. **Database** configured and accessible
3. **Configuration** file prepared
4. **Network** ports open (default: 8090)

### Steps

1. **Build on CI/CD server:**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Copy JAR to production server:**
   ```bash
   scp target/dq-0.0.1-SNAPSHOT.jar user@production-server:/opt/dqc/
   ```

3. **Create configuration file:**
   ```bash
   # Create external configuration
   vi /opt/dqc/application-prod.yaml
   ```

4. **Run the application:**
   ```bash
   cd /opt/dqc
   java -jar dq-0.0.1-SNAPSHOT.jar --spring.config.location=application-prod.yaml
   ```

### Production Configuration Example

```yaml
server:
  port: 8080

repo:
  embedded: false
  use-source: false
  schema: dq_repo
  datasource:
    url: jdbc:mysql://db-server:3306/dq_repository
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: dq_user
    password: ${REPO_DB_PASSWORD}

source:
  datasource:
    jdbc-url: jdbc:mysql://db-server:3306/source_database
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: readonly_user
    password: ${SOURCE_DB_PASSWORD}

dq:
  mail:
    enabled: true
    recipients: "operations@company.com"
    send-on: on-failure
  allow-write: false
  demo: false

spring:
  mail:
    host: smtp.company.com
    port: 587
    username: dq-alerts@company.com
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### Run as a Service (Linux)

**Using systemd:**

1. Create a service file:
   ```bash
   sudo vi /etc/systemd/system/dqc.service
   ```

2. Add the following content:
   ```ini
   [Unit]
   Description=Simple Data Quality Control System
   After=network.target

   [Service]
   User=dqc
   WorkingDirectory=/opt/dqc
   ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/dqc/dq-0.0.1-SNAPSHOT.jar \
     --spring.config.location=/opt/dqc/application-prod.yaml
   SuccessExitStatus=143
   Restart=always
   RestartSec=30

   [Install]
   WantedBy=multi-user.target
   ```

3. Enable and start the service:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable dqc
   sudo systemctl start dqc
   ```

4. Check status:
   ```bash
   sudo systemctl status dqc
   ```

5. View logs:
   ```bash
   journalctl -u dqc -f
   ```

---

## Docker Deployment (Optional)

### Build Docker Image

Create a `Dockerfile`:

```dockerfile
FROM eclipse-temurin:25-jdk-jammy

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:resolve

COPY src ./src
RUN mvn package -DskipTests

COPY target/dq-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build the image:
```bash
docker build -t simple-dqc:latest .
```

### Run with Docker

```bash
docker run -d \
  --name dqc \
  -p 8090:8090 \
  -v /path/to/config/application.yaml:/app/config/application.yaml \
  -v /path/to/controls:/app/controls \
  -v /path/to/repository:/app/repository \
  -e SPRING_CONFIG_LOCATION=/app/config/application.yaml \
  simple-dqc:latest
```

---

## Configuration Files

The application looks for configuration in the following order (later sources override earlier ones):

1. **Internal**: `src/main/resources/application.yaml` (embedded in JAR)
2. **External**: `application.yaml` in the same directory as the JAR
3. **Command Line**: `--property=value` arguments
4. **Environment Variables**: `PROPERTY_NAME` (uppercase with underscores)

### Multiple Configuration Files

You can use profile-specific configuration:

```yaml
# application.yaml
spring:
  profiles:
    active: prod

# application-prod.yaml
server:
  port: 8080
```

Run with:
```bash
java -jar app.jar --spring.profiles.active=prod
```

---

## Required Directories

The application creates and uses the following directories:

| Directory | Purpose | Required |
|-----------|---------|----------|
| `controls/` | Stores SQL control files | Yes |
| `repository/` | H2 database files (if using embedded) | Auto-created |
| `scheduler/` | Cron configuration file | Auto-created |

**Permissions:**
- The application user must have **read/write** access to these directories
- For production, ensure the service account has proper permissions

---

## Environment Variables

All Spring Boot properties can be set via environment variables:

```bash
# Server
EXPORT SERVER_PORT=8090

# Repository
EXPORT REPO_EMBEDDED=true
EXPORT REPO_USE-SOURCE=false

# Source Database
EXPORT SOURCE_DATASOURCE_JDBC-URL=jdbc:mysql://localhost/db
EXPORT SOURCE_DATASOURCE_USERNAME=user
EXPORT SOURCE_DATASOURCE_PASSWORD=pass

# DQ Settings
EXPORT DQ_MAIL_ENABLED=true
EXPORT DQ_MAIL_RECIPIENTS="email@company.com"

# Run
java -jar app.jar
```

---

## Verifying the Application is Running

### Check Console Output

```
  .   ____          _            __ _ _
 /\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

:: Spring Boot :: (v4.1.0-SNAPSHOT)

... Application started in X.XXX seconds
```

### Test API Endpoint

```bash
curl http://localhost:8090/api/dashboard
```

Expected response:
```json
[]
```

### Check Health (if Actuator is enabled)

```bash
curl http://localhost:8090/actuator/health
```

---

## Troubleshooting

### "No main manifest attribute"

**Cause:** The JAR was not built as an executable JAR.

**Solution:** Ensure you used `mvn package` (not just `mvn compile`).

### "Java 25 is required"

**Cause:** Java version is too old.

**Solution:**
```bash
# Check current version
java -version

# Install Java 25
# For Ubuntu/Debian:
sudo apt-get install openjdk-25-jdk

# For Windows: Download from Oracle or OpenJDK
```

### "Could not load JDBC driver"

**Cause:** Database driver is missing.

**Solution:** Add the appropriate JDBC driver dependency to `pom.xml` and rebuild.

### "Access denied for user"

**Cause:** Database credentials are incorrect or user lacks permissions.

**Solution:**
- Verify `spring.datasource.username` and `spring.datasource.password`
- Check database user permissions
- Test connection manually using a database client

### "Repository initialization failed"

**Cause:** Repository database user cannot create tables.

**Solution:**
- Grant CREATE TABLE permissions to the repository user
- Verify the database URL is correct
- Check if tables already exist (manual creation may be needed)

### Port already in use

**Cause:** Another application is using the configured port.

**Solution:**
```bash
# Find the process using the port (Linux/Mac)
lsof -i :8090

# Kill the process
kill -9 <PID>

# Or change the port
java -jar app.jar --server.port=8081
```

On Windows:
```cmd
netstat -ano | findstr :8090
```

---

## Stopping the Application

### Running in Foreground

Press `Ctrl + C` in the terminal.

### Running as a Service

```bash
sudo systemctl stop dqc
```

### Running in Docker

```bash
docker stop dqc
docker rm dqc
```

---

## Logging

### Console Logs

By default, logs are written to the console. Spring Boot uses Logback for logging.

### Log Levels

Override log levels via command line:

```bash
java -jar app.jar --logging.level.net.powerup.dq=DEBUG
```

### Log File

To write logs to a file:

```bash
java -jar app.jar > application.log 2>&1 &
```

Or with Logback configuration:

Create `logback-spring.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>application.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

---

## Upgrading

1. **Backup existing data:**
   - Copy the `controls/` directory
   - Copy the `repository/` directory (for embedded H2)
   - Export database data if using external databases

2. **Build new version:**
   ```bash
   git pull
   mvn clean package
   ```

3. **Deploy new JAR:**
   ```bash
   # Stop current application
   sudo systemctl stop dqc
   
   # Backup old JAR
   cp /opt/dqc/dq-*.jar /opt/dqc/dq-*.jar.bak
   
   # Deploy new JAR
   cp target/dq-0.0.1-SNAPSHOT.jar /opt/dqc/
   
   # Start application
   sudo systemctl start dqc
   ```
