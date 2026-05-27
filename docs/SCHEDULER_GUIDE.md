# Scheduler Guide

SimpleDQC includes a built-in cron-style scheduler that automatically runs all data quality controls on one or more recurring schedules. Schedules are defined in a plain text file and can be managed through the **Schedules** view in the dashboard.

---

## 1. How It Works

- Every schedule line maps to a Spring `CronTrigger`.
- When a trigger fires, SimpleDQC executes **all** control SQL files in the `controls/` folder (the same action as the "Run All Controls" button).
- Multiple named schedules can coexist — for example, a frequent "smoke" check and a slower "deep" run at night.
- Changes made via the GUI take effect **immediately**: existing schedules are cancelled and rescheduled in-process. No application restart required.

---

## 2. The Schedule File

Schedules live in `scheduler/cron.txt`, relative to the application's working directory.

**Format** — one schedule per line:

```
<second> <minute> <hour> <day-of-month> <month> <day-of-week> <name>
```

- Six standard Spring cron fields, followed by a schedule name.
- Lines starting with `#` are treated as comments.
- The name must match `[A-Za-z0-9_-]+` and must be unique within the file.
- A legacy line with only the six cron fields and no name is loaded as `default`.

**Example:**

```
# Schedule lines: <second> <minute> <hour> <day-of-month> <month> <day-of-week> <name>
0 0 0 * * *   daily
0 0 0 * * 0   weekly
0 0 */2 * * * every_two_hours
```

The cron field semantics follow the standard Spring scheduling format. See the [Spring CronExpression documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html) for the full grammar (ranges, lists, step values, named days/months, etc.).

---

## 3. The Schedules View

Open the dashboard and click **Schedules** in the sidebar.

Each row exposes the six cron fields as dropdowns, the schedule name, and a live preview of the resulting cron expression.

| Field | Options |
|-------|---------|
| Second | `*` or 0–59 |
| Minute | `*` or 0–59 |
| Hour | `*` or 0–23 |
| Day of Month | `*` or 1–31 |
| Month | `*` or 1–12 (with `JAN`–`DEC` labels) |
| Day of Week | `*` or 0–6 (with `SUN`–`SAT` labels) |
| Name | Free text, validated against `[A-Za-z0-9_-]+` |

Non-standard expressions (e.g. `*/5`, `MON-FRI`, `1-5`) entered manually via the API are preserved and shown as-is in the dropdown.

### Actions

- **+ Add Schedule** — appends a new row, pre-filled with `0 0 * * * *` and a placeholder name. Click **Save Schedules** to persist.
- **Save Schedules** — validates the entire list server-side, rewrites `scheduler/cron.txt`, and reloads the scheduler.
- **Delete** (per row) — asks for confirmation, then removes the schedule immediately (file + live scheduler). Rows that were just added in the UI and never saved are simply dropped from the local view.

### Write Protection

The **+ Add Schedule**, **Save Schedules**, and **Delete** controls only appear when `dq.allow-write=true` in `application.yaml`. When write is disabled, the Schedules view is read-only.

---

## 4. Troubleshooting

- **Schedule doesn't fire** — check the application logs for `[schedule:<name>] Registered cron '<expr>'` at startup or after a save. If the expression is invalid, an error is logged and the schedule is skipped.
- **Two schedules with the same name** — only the first occurrence is registered; subsequent duplicates are logged as `Skipping duplicate schedule name: <name>` and ignored.
- **Changes aren't taking effect** — confirm `dq.allow-write=true`, and that the API responded `200 OK` on save. The file at `scheduler/cron.txt` should reflect your edits.

---

## 5. Related

- [API Endpoints](API_ENDPOINTS.md) — programmatic access to schedule management
- [Control Query Format](CONTROL_QUERIES.md) — what gets executed when a schedule fires
- [Email Configuration](EMAIL_CONFIGURATION.md) — notifications sent after each scheduled run
