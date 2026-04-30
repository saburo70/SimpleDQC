# Dashboard Guide

The SimpleDQC dashboard provides a centralized interface to monitor data quality, manage controls, and handle persistent exceptions.

---

## 1. Control Summary
The **Control Summary** is the default view. it provides a high-level overview of the current status of all data quality controls.

- **Status Overview**: Quickly see which controls are passing, failing, or encountering errors.
- **Active vs. Known Issues**: Each control displays the count of "Active Issues" (new problems) versus "Known Issues" (documented exceptions).
- **Drill-down**: Clicking on any control row opens the **Issues Modal**, allowing you to inspect individual records.
- **Run Controls**: The "Run All Controls" button triggers a fresh execution of all SQL queries in the `controls/` directory.

---

## 2. Execution History
The **Execution History** view provides a chronological log of every control run.

- **Traceability**: See when a control was run, how long it took (`elapsed`), and the results at that specific point in time.
- **SQL File Mapping**: Identifies which physical `.sql` file was used for each execution.
- **Cleanup**: Allows administrators to delete old execution logs.

---

## 3. Queries (Editor)
The **Queries** view allows users to browse and manage the SQL files stored in the `controls/` directory.

- **Live Preview**: You can write and test SQL queries directly in the dashboard before saving them as permanent controls.
- **Metadata Management**: The editor ensures that required tags like `$CODE=` and `$DESCRIPTION=` are present.
- **Demo Controls**: Pre-packaged example controls are marked with a `DEMO` badge.
- **Frictionless Deployment**: Saving a query creates or updates a `.sql` file in the folder, instantly making it part of the daily routine.

---

## 4. Managing "Known Issues"

One of the most powerful features of SimpleDQC is the **Known Issues** system.

### What are Known Issues?
In real-world data projects, some data quality issues are "known" but cannot or will not be fixed immediately (e.g., a legacy system user that is not supposed to have an email address). If these are not handled, they create "noise" in your reports, making it harder to spot *new* issues.

**Known Issues are a way to "ignore" specific exceptions so they no longer trigger alerts.**

### How it Works:
1. **Identify**: In the Issues Modal, find a row that represents a persistent exception.
2. **Mark Known**: Click the **"Mark Known"** button for that specific `issueKey`.
3. **Suppression**:
   - The issue will no longer be counted as an "Active Issue."
   - It will no longer trigger email alerts for that control.
   - It is moved to the "Known Issues" bucket.
4. **Visibility**: You can still see these issues by toggling **"Show known issues"** in the modal. They are highlighted in yellow.
5. **Reversal**: If the issue is later fixed in the source system or needs to be monitored again, you can click **"Remove Known"** to bring it back into the "Active" list.
