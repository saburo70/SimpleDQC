# SimpleDQC API Endpoints

Base URL: `http://localhost:8090`

---

## Dashboard Controller

Base Path: `/api`

### Data Quality Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dashboard` | Returns the latest execution records for controls that still have issues |
| GET | `/api/controls/summary` | Returns the latest execution records for ALL controls, ordered by issues count (descending) |

### Execution History

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/history` | Returns the last 100 execution records, ordered by timestamp (descending) |
| DELETE | `/api/history/{id}` | Deletes a specific execution record by ID |

### Control Execution

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/run` | Triggers execution of all data quality controls |

### Control Issues

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/controls/{code}/issues` | Returns active issues for a specific control code |
| GET | `/api/controls/{code}/known-issues` | Returns known (whitelisted) issues for a specific control code |

### Known Issues Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/known-issues` | Adds a new known issue entry |
| POST | `/api/known-issues/remove` | Deactivates (removes) a known issue entry |

---

## Query Controller

Base Path: `/api/queries`

### Query Execution

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/queries/run` | Executes a custom SQL query against the source database. Returns up to 500 rows. |

### Query Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/queries` | Lists all SQL control files with metadata (code, description, demo flag) and write enabled status |
| GET | `/api/queries/{filename}` | Retrieves a specific SQL file content with metadata |
| POST | `/api/queries` | Creates a new SQL control file. Requires `dq.allow-write=true`. |
| PUT | `/api/queries/{filename}` | Updates an existing SQL control file. Requires `dq.allow-write=true`. |
| DELETE | `/api/queries/{filename}` | Deletes an SQL control file. Requires `dq.allow-write=true`. |

---

## Request/Response Details

### POST `/api/run`
**Response:** `200 OK`
```text
Controls executed successfully
```

---

### POST `/api/queries/run`
**Request Body:**
```json
{
  "content": "SELECT * FROM your_table LIMIT 10"
}
```
**Response (Success):** `200 OK`
```json
{
  "rows": [
    {"column1": "value1", "column2": "value2"},
    ...
  ],
  "truncated": false
}
```
**Response (Error):** `400 Bad Request`
```json
{
  "timestamp": "...",
  "message": "No SQL content provided"
}
```

---

### GET `/api/queries`
**Response:** `200 OK`
```json
{
  "items": [
    {
      "filename": "control_name.sql",
      "code": "CONTROL_CODE",
      "description": "Control description",
      "demo": false
    }
  ],
  "writeEnabled": true
}
```

---

### GET `/api/queries/{filename}`
**Response:** `200 OK`
```json
{
  "filename": "control_name.sql",
  "code": "CONTROL_CODE",
  "description": "Control description",
  "content": "SELECT ... -- $CODE=CONTROL_CODE\n-- $DESCRIPTION=Control description"
}
```

---

### POST `/api/queries`
**Request Body:**
```json
{
  "filename": "new_control.sql",
  "content": "-- $CODE=NEW_CODE\n-- $DESCRIPTION=New control\nSELECT * FROM table WHERE condition"
}
```
**Response:** `200 OK`
```json
{
  "filename": "new_control.sql",
  "code": "NEW_CODE",
  "description": "New control"
}
```
**Constraints:**
- Filename must end with `.sql`
- Content must include `$CODE=` and `$DESCRIPTION=` comments
- CODE must be unique across all control files

---

### PUT `/api/queries/{filename}`
**Request Body:** Same as POST
**Response:** `200 OK` - Returns updated file metadata

---

### DELETE `/api/queries/{filename}`
**Response:** `200 OK`
```text
Deleted
```

---

### POST `/api/known-issues`
**Request Body:**
```json
{
  "controlCode": "CONTROL_CODE",
  "issueKey": "column_name",
  "description": "This column may have nulls"
}
```
**Response:** `200 OK`
```text
Known issue added
```

---

### POST `/api/known-issues/remove`
**Request Body:**
```json
{
  "controlCode": "CONTROL_CODE",
  "issueKey": "column_name"
}
```
**Response:** `200 OK`
```text
Known issue removed
```

---

### GET `/api/dashboard`
**Response:** `200 OK`
```json
[
  {
    "id": 123,
    "controlCode": "CONTROL_CODE",
    "ts": "2024-01-15T10:30:00",
    "issues": 5,
    "sample": "...",
    ...
  }
]
```

---

### GET `/api/controls/summary`
**Response:** `200 OK` - Same structure as `/api/dashboard` but includes all controls, ordered by issues count descending.

---

### GET `/api/history`
**Response:** `200 OK`
```json
[
  {
    "id": 123,
    "controlCode": "CONTROL_CODE",
    "ts": "2024-01-15T10:30:00",
    "issues": 5,
    "sample": "...",
    ...
  }
]
```

---

### DELETE `/api/history/{id}`
**Response:** `200 OK` (no body)

---

### GET `/api/controls/{code}/issues`
**Response:** `200 OK`
```json
[
  {
    "issueKey": "column_name",
    "description": "Issue description",
    "count": 10,
    ...
  }
]
```

---

### GET `/api/controls/{code}/known-issues`
**Response:** `200 OK` - Same structure as above but for known/whitelisted issues.

---

## Error Responses

| Status Code | Reason |
|-------------|--------|
| 400 Bad Request | Invalid input, missing parameters, or validation failure |
| 403 Forbidden | Write operations disabled (`dq.allow-write=false`) |
| 404 Not Found | Resource not found |
| 409 Conflict | Resource already exists |
| 500 Internal Server Error | Server-side error |

---

## Configuration Notes

- Write operations on queries require `dq.allow-write=true` in `application.yaml`
- By default, query execution limits results to 500 rows
- The server runs on port `8090` by default (configurable in `server.port`)
- Repository schema can be configured via `repo.schema` and `repo.use-source` settings
