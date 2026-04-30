# Repository Configuration Guide

The SimpleDQC repository stores execution history, known issues, and control metadata. There are **three configuration modes** available for the repository database.

---

## Configuration Modes Overview

| Mode | Configuration | Use Case |
|------|---------------|----------|
| **Embedded (H2)** | `repo.embedded=true` | Quick start, development, single-node deployments |
| **Same as Source** | `repo.use-source=true` | Shared database with source, separate schema |
| **Separate Connection** | `repo.datasource.*` properties | Production, dedicated repository database |

---

## Mode 1: Embedded Database (H2)

Uses an embedded H2 database stored in the local filesystem. No external database server required.

### Configuration

```yaml
repo:
  embedded: true
  # Optional: Set to true to use same connection as source (not typical with embedded)
  use-source: false
  # Schema is not needed for H2 in this configuration
  schema: ""
```

### Behavior

- **Database Location**: `./repository/dq-repo.mv.db` (created automatically)
- **Driver**: `org.h2.Driver`
- **Connection URL**: `jdbc:h2:file:./repository/dq-repo;MODE=MySQL;NON_KEYWORDS=VALUE`
- **Credentials**: Username `sa`, empty password
- **Compatibility**: MySQL-compatible mode (tables use MySQL syntax)
- **Persistence**: Data persists between application restarts

### When to Use

- Development environments
- Local testing
- Single-node deployments where external DB is not available
- Quick evaluation of SimpleDQC

---

## Mode 2: Same Connection as Source Database

Uses the same database connection as the source database, optionally with a different schema.

### Configuration

**Basic - Same database and schema:**
```yaml
repo:
  embedded: false
  use-source: true
  schema: ""
```

**With separate schema:**
```yaml
repo:
  embedded: false
  use-source: true
  schema: repo
```

### Behavior

- **Connection**: Shares the `sourceDataSource` connection pool
- **Schema**: Uses `repo.schema` value as a schema prefix (e.g., `repo.dqExecution`)
- **Tables**: Repository tables are created in the specified schema or default schema
- **Database Type**: Inherits from source database (MySQL, PostgreSQL, etc.)

### When to Use

- You want repository data in the same database as source data
- You can create a separate schema for repository tables
- You want to simplify connection management (single DB connection)
- Your source database user has permission to create tables

### Notes

- If `repo.schema` is set, tables will be created with that schema prefix (e.g., `repo.dqExecution`)
- If `repo.schema` is empty, tables are created in the default schema
- The source database user must have CREATE TABLE and WRITE permissions

---

## Mode 3: Separate Database Connection

Uses a completely independent database connection for the repository.

### Configuration

```yaml
repo:
  embedded: false
  use-source: false
  schema: repo
  datasource:
    url: jdbc:mysql://yourRepositoryHost/yourRepositoryDb
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: yourRepositoryUser
    password: yourRepositoryPassword
    hikari:
      max-lifetime: 600000
      keepalive-time: 60000
      connection-test-query: SELECT 1
```

### Behavior

- **Connection**: Creates a separate `repoDataSource` bean
- **Database**: Can be any supported JDBC database (MySQL, PostgreSQL, H2, etc.)
- **Schema**: Uses `repo.schema` value as a schema prefix
- **Isolation**: Completely independent from source database

### When to Use

- Production deployments
- Multi-node deployments sharing a central repository
- When repository should be on different database server
- When different access credentials are needed
- When you want to isolate repository data from source data

---

## Repository Tables

The following tables are automatically created in the repository database:

### `dqExecution`

Stores execution history for data quality controls.

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | Auto-increment primary key |
| `controlCode` | VARCHAR(100) | Unique code identifying the control |
| `controlDescription` | VARCHAR(500) | Human-readable description of the control |
| `issues` | INT | Number of issues detected in last run |
| `known` | INT | Number of known (whitelisted) issues |
| `elapsed` | BIGINT | Execution time in milliseconds |
| `sqlFile` | VARCHAR(255) | Filename of the SQL control file |
| `status` | VARCHAR(45) | Execution status (running, completed, failed, cancelled) |
| `ts` | TIMESTAMP | Timestamp of execution |

### `dqKnownIssue`

Stores known/whitelisted issues that should not count toward the issue total.

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | Auto-increment primary key |
| `controlCode` | VARCHAR(100) | The control code this issue belongs to |
| `issueKey` | VARCHAR(255) | Identifier for the issue (e.g., column name) |
| `description` | VARCHAR(500) | Description of why this is a known issue |
| `isActive` | TINYINT | Whether this known issue is active (1 = active, 0 = inactive) |

---

## Complete Configuration Examples

### Development (Embedded H2)

```yaml
server:
  port: 8090

repo:
  embedded: true
  use-source: false
  schema: ""

source:
  datasource:
    jdbc-url: jdbc:mysql://localhost/source_db
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: dbuser
    password: userpassword

dq:
  allow-write: true
  demo: true
```

### Production with Separate Repository Database

```yaml
server:
  port: 8090

repo:
  embedded: false
  use-source: false
  schema: dq_repo
  datasource:
    url: jdbc:mysql://db-server.example.com/dq_repository
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: dq_user
    password: secure_password
    hikari:
      max-lifetime: 600000
      keepalive-time: 60000
      connection-test-query: SELECT 1

source:
  datasource:
    jdbc-url: jdbc:mysql://db-server.example.com/source_database
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: readonly_user
    password: readonly_password

dq:
  allow-write: false
  demo: false
```

### Same Database, Separate Schema

```yaml
server:
  port: 8090

repo:
  embedded: false
  use-source: true
  schema: dq_data

source:
  datasource:
    jdbc-url: jdbc:mysql://localhost/company_db
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: dbadmin
    password: admin_password

dq:
  allow-write: true
```

---

## Configuration Priority

The repository data source is determined by the following priority:

1. **If `repo.use-source=true`**: Use the source database connection (with optional `repo.schema` prefix)
2. **Else if `repo.embedded=true`**: Use embedded H2 database
3. **Else**: Use separate connection defined by `repo.datasource.*` properties

---

## Migration Between Modes

### From Embedded to Separate Database

1. Export data from H2 using: `SELECT * FROM dqExecution` and `SELECT * FROM dqKnownIssue`
2. Configure the separate database connection
3. Start the application - tables will be created automatically
4. Import the exported data

### From Same-as-Source to Separate Database

1. Dump the repository tables from the source database
2. Configure the separate database connection
3. Restart the application - tables will be created automatically
4. Import the dumped data

### Changing Schema Name

When using `repo.use-source=true`, changing the `repo.schema` value will cause the application to look for tables in the new schema. Existing data will not be automatically migrated.

---

## Troubleshooting

### "Repository initialization failed"

- Check that the database user has CREATE TABLE permissions
- Verify the database is accessible from the application server
- Check connection string and credentials
- Ensure the JDBC driver is in the classpath

### Tables not found in specified schema

- Verify the schema exists in the database
- Check that the database user has permissions on the schema
- Ensure `repo.schema` is set correctly (case-sensitive in some databases)

### Embedded H2 not persisting

- Check that the `repository` directory exists and is writable
- Verify the application has file system write permissions

### Schema prefix not applied

- When using `repo.use-source=true`, the schema prefix is only applied if `repo.schema` is non-empty
- Tables are referenced as `schema.tablename` in SQL queries
