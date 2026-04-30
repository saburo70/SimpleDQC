# Control Queries

This document describes how to create, manage, and deploy data quality control queries in SimpleDQC.

---

## Overview

Data quality controls in SimpleDQC are defined as **SQL query files** stored in the `controls/` directory. Each file represents a single data quality check that runs against your source database.

When executed, each control query:
1. Runs against the **source database** (configured via `source.datasource.*`)
2. Returns rows representing data quality issues
3. Stores execution results in the **repository database** (execution history, issue counts)

---

## File Location

| Location | Path | Purpose |
|----------|------|---------|
| Control Files | `controls/` | Directory containing all SQL control files |
| Demo Controls | `controls/__demo__*.sql` | Pre-packaged example controls (included in repository) |

**Directory Structure:**
```
project-root/
├── controls/
│   ├── __demo__missing_emails.sql
│   ├── __demo__short_names.sql
│   ├── custom_control_001.sql
│   └── custom_control_002.sql
├── ...
```

---

## Control Query Format

Each control file is a **SQL file** with special metadata comments and a query that returns issue data.

### Required Metadata

Every control file **must** include two special comments in the file:

| Metadata | Format | Description |
|----------|--------|-------------|
| Control Code | `$CODE=<unique_code>` | Unique identifier for this control (used in API endpoints, execution history) |
| Description | `$DESCRIPTION=<description>` | Human-readable description of what this control checks |

### SQL Query Requirements

The SQL query must:

1. **Return at least one column named `issueKey`** (as the first column or aliased)
2. **Return data types compatible with the repository** (strings, numbers, dates)
3. **Filter for only the problematic rows** (the query should return only rows that represent issues)

**Example:**
```sql
/*
$CODE=DQ001
$DESCRIPTION=Users without email
*/
SELECT 
    CAST(id AS CHAR) as issueKey,
    name,
    email
FROM users 
WHERE email IS NULL OR email = ''
```

### Query Result Columns

While only `issueKey` is strictly required, additional columns are helpful:

| Column | Purpose |
|--------|---------|
| `issueKey` | **Required** - Unique identifier for each issue row. Often the primary key or a composite value. |
| Any other columns | Displayed in the dashboard and issue details. Use descriptive names. |

**Best Practice:** Include columns that help identify and understand the issue (IDs, names, values, timestamps).

---

## Creating Control Queries

### Step 1: Create the SQL File

Create a new `.sql` file in the `controls/` directory:

**File:** `controls/user_email_validation.sql`
```sql
/*
$CODE=USER_EMAIL_VALID
$DESCRIPTION=Users with invalid email format
*/
SELECT 
    CAST(id AS CHAR) as issueKey,
    id as user_id,
    email,
    name,
    'Invalid email format' as issue_type
FROM users 
WHERE email IS NOT NULL 
  AND email != ''
  AND email NOT LIKE '%@%.%' 
  AND email NOT REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'
```

### Step 2: Verify the File

- Filename must end with `.sql`
- File must be readable by the application
- `$CODE` must be unique across all control files
- `$DESCRIPTION` should be descriptive and clear

### Step 3: Test the Query

You can test the query directly against your source database or use the API:

```bash
# Test via API (requires dq.allow-write=true)
curl -X POST http://localhost:8090/api/queries/run \
  -H "Content-Type: application/json" \
  -d '{"content": "SELECT * FROM users WHERE email IS NULL"}'
```

---

## Control File Metadata Reference

### $CODE

- **Format**: Alphanumeric, underscores, hyphens (avoid spaces and special characters)
- **Length**: Keep under 50 characters
- **Uniqueness**: **Must be unique** across all control files
- **Usage**: Used in API endpoints, execution history, and issue tracking

**Good Examples:**
- `USER_EMAIL_MISSING`
- `ORDER_TOTAL_NEGATIVE`
- `DQ-001`
- `customers-without-address`

**Bad Examples:**
- `User Email Missing` (spaces)
- `control#1` (special characters)
- `USER_EMAIL_MISSING` (duplicate of existing)

### $DESCRIPTION

- **Format**: Plain text, human-readable
- **Length**: Keep under 255 characters
- **Purpose**: Displayed in the dashboard and API responses

**Good Examples:**
- `Users with missing email addresses`
- `Orders with negative total amounts`
- `Customers without a valid billing address`

**Bad Examples:**
- `Check users` (too vague)
- `This control checks for users who have null or empty email fields in the users table` (too verbose)

---

## Viewing Controls

### Via API

**List all controls:**
```bash
GET /api/queries
```

**Response:**
```json
{
  "items": [
    {
      "filename": "user_email_validation.sql",
      "code": "USER_EMAIL_VALID",
      "description": "Users with invalid email format",
      "demo": false
    },
    {
      "filename": "__demo__missing_emails.sql",
      "code": "DQ001",
      "description": "Users without email",
      "demo": true
    }
  ],
  "writeEnabled": false
}
```

**Get a specific control:**
```bash
GET /api/queries/{filename}
```

**Response:**
```json
{
  "filename": "user_email_validation.sql",
  "code": "USER_EMAIL_VALID",
  "description": "Users with invalid email format",
  "content": "/*\n$CODE=USER_EMAIL_VALID\n$DESCRIPTION=Users with invalid email format\n*/\nSELECT ..."
}
```

### Via File System

Simply browse the `controls/` directory:
```bash
ls controls/
cat controls/user_email_validation.sql
```

---

## Editing Controls

### In-Application Editing

Editing controls through the API is **controlled by the `dq.allow-write` setting** in `application.yaml`:

```yaml
dq:
  allow-write: false  # Recommended for production
```

| `dq.allow-write` | Effect |
|-----------------|--------|
| `false` (default recommended) | GET endpoints work; POST, PUT, DELETE are **blocked** (return 403 Forbidden) |
| `true` | All CRUD operations on controls are **enabled** |

**API Endpoints Affected:**
- `POST /api/queries` - Create new control
- `PUT /api/queries/{filename}` - Update existing control
- `DELETE /api/queries/{filename}` - Delete control

**⚠️ WARNING: It is strongly recommended to set `dq.allow-write=false` in production environments.**

### File System Editing

Regardless of `dq.allow-write`, you can always:
1. Directly edit files in the `controls/` directory
2. Add new `.sql` files
3. Delete `.sql` files

**The application automatically detects file system changes on next execution.**

To reload controls without restarting:
```bash
# Trigger a manual run to pick up new/changed files
POST /api/run
```

---

## Deleting Controls

### Via API (if allowed)

```bash
DELETE /api/queries/{filename}
```

**Requires:** `dq.allow-write=true`

**Response:** `200 OK` with body `"Deleted"`

### Via File System

```bash
rm controls/user_email_validation.sql
```

The control will no longer appear in listings and will not run on subsequent executions.

---

## Control Execution

Controls are executed in several scenarios:

1. **Manual Trigger**: `POST /api/run`
2. **Scheduled Execution**: Via cron configuration in `scheduler/cron.txt`
3. **Startup**: Controls are NOT automatically run on startup

### Execution Flow

1. Application reads all `.sql` files from `controls/` directory
2. For each file, extracts `$CODE` and `$DESCRIPTION`
3. Executes the SQL query against the source database
4. Counts returned rows as **issues**
5. Stores execution results in repository database (`dqExecution` table)
6. Compares against known issues (`dqKnownIssue` table)
7. Updates dashboard with latest results

---

## Best Practices

### Query Design

1. **Be specific**: Query should return only rows that represent actual issues
2. **Include context**: Return columns that help understand the issue (IDs, descriptions, values)
3. **Use CAST for issueKey**: Ensure `issueKey` is a string type: `CAST(id AS CHAR) as issueKey`
4. **Keep it simple**: Single-purpose controls are easier to maintain
5. **Avoid heavy joins**: Complex queries may impact performance

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Filename | lowercase_with_underscores.sql | `users_missing_email.sql` |
| CODE | UPPER_CASE_WITH_UNDERSCORES | `USERS_MISSING_EMAIL` |
| Description | Title Case Sentence | `Users with missing email addresses` |

### Demo Controls

Files starting with `__demo__` are considered demo controls:
- They are marked with `"demo": true` in API responses
- Useful for testing and examples
- Can be safely deleted in production

---

## Recommended: External Control File Management

**⚠️ IMPORTANT RECOMMENDATION: Disable in-app editing in production.**

In production environments, **`dq.allow-write` should be set to `false`** to prevent accidental or unauthorized modifications to control queries through the API.

```yaml
# application.yaml (PRODUCTION)
dq:
  allow-write: false  # Disable in-app editing
```

### Why External Management?

| Reason | Benefit |
|--------|---------|
| **Audit Trail** | Track who changed what and when |
| **Version Control** | Roll back to previous versions if issues arise |
| **Peer Review** | Enable code review before changes go live |
| **CI/CD Integration** | Automate testing and deployment |
| **Disaster Recovery** | Restore controls from backup |
| **Team Collaboration** | Multiple team members can contribute |

### Recommended Approaches

Choose the approach that best fits your organization's existing infrastructure:

#### Option 1: Git Repository (Recommended for most teams)

**Benefits:**
- Full version history
- Branching and merging support
- Pull request reviews
- Easy rollback
- Free and widely supported

**Setup:**
```bash
# Initialize a Git repository for controls
cd /path/to/SimpleDQC
mkdir -p controls
cd controls
git init

# Create a .gitignore if needed
echo "*.bak" > .gitignore
echo "*.tmp" >> .gitignore

# Add existing controls
git add .
git commit -m "Initial commit of data quality controls"

# Connect to remote repository
git remote add origin https://github.com/your-org/dqc-controls.git
git push -u origin main
```

**Deployment Workflow:**
```bash
# Developer creates/updates control
vi new_control.sql

# Commit and push
git add new_control.sql
git commit -m "Add new control for X"
git push

# Production server pulls updates
cd /opt/dqc/controls
git pull

# Application picks up changes on next run
curl -X POST http://localhost:8090/api/run
```

**Git Hosting Options:**
- GitHub
- GitLab
- Bitbucket
- Azure DevOps
- Self-hosted GitLab/Gitea

#### Option 2: AWS S3 Bucket

**Benefits:**
- Scalable and durable storage
- Versioning support
- Access control via IAM
- Integration with AWS services
- Good for serverless/container deployments

**Setup:**
```bash
# Create S3 bucket
aws s3 mb s3://your-org-dqc-controls

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket your-org-dqc-controls \
  --versioning-configuration Status=Enabled

# Sync local controls to S3
aws s3 sync ./controls/ s3://your-org-dqc-controls/
```

**Deployment Script:**
```bash
#!/bin/bash
# Download controls from S3
aws s3 sync s3://your-org-dqc-controls/ /opt/dqc/controls/

# Restart application or trigger run
curl -X POST http://localhost:8090/api/run
```

**S3 Configuration:**
```yaml
# If your application supports S3 natively, configure it
aws:
  s3:
    bucket: your-org-dqc-controls
    region: us-east-1
```

#### Option 3: Shared Network Drive

**Benefits:**
- Simple to set up
- Works with existing Windows/AD infrastructure
- No new services to manage

**Setup:**
```bash
# Mount network drive
mount -t cifs //fileserver/controls /mnt/dqc-controls \
  -o username=youruser,password=youpass,domain=yourdomain

# Symlink to application
ln -s /mnt/dqc-controls /opt/dqc/controls
```

**Deployment:**
- Users edit files directly on the network drive
- Changes are immediately available to all instances

#### Option 4: Configuration Management Tool

**Options:**
- **Ansible**: Push controls to servers via playbooks
- **Puppet/Chef**: Manage controls as configuration
- **Terraform**: For cloud deployments
- **Kubernetes ConfigMaps**: For containerized deployments

**Example (Kubernetes):**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dqc-controls
  namespace: data-quality
data:
  user_email_validation.sql: |
    /*
    $CODE=USER_EMAIL_VALID
    $DESCRIPTION=Users with invalid email format
    */
    SELECT ...
```

#### Option 5: Database Storage

Store control definitions in a database table and have the application load them on startup.

**Schema:**
```sql
CREATE TABLE dqControls (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(500) NOT NULL,
    sql_query TEXT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

## Implementation Recommendations

### For Small Teams / Simple Deployments

1. Use **Git** (GitHub/GitLab) for version control
2. Set `dq.allow-write=false` in production
3. Deploy via simple `git pull` script
4. Use branches for development/testing

### For Enterprise / Cloud-Native

1. Use **AWS S3** with versioning enabled
2. Implement CI/CD pipeline:
   - Code commit → Build → Test → Deploy to S3 → Sync to servers
3. Use IAM roles for access control
4. Enable S3 object locking for compliance

### For Kubernetes Environments

1. Use **ConfigMaps** for control definitions
2. Mount ConfigMap as volume at `controls/`
3. Use Git sync sidecar for automatic updates
4. Consider Helm charts for deployment

### For On-Premises with Existing Infrastructure

1. Use **shared network drive** if already in use
2. Or integrate with **existing CMDB/CMS tools**
3. Leverage **existing backup/restore** procedures

---

## Example: Complete Git-Based Workflow

### Directory Structure
```
dqc-controls/                    # Git repository
├── README.md                   # Documentation
├── development/                # Controls in development
│   └── new_control.sql
├── staging/                    # Controls in staging
│   └── tested_control.sql
├── production/                 # Production-ready controls
│   ├── users_missing_email.sql
│   ├── orders_negative_total.sql
│   └── ...
└── .gitignore
```

### CI/CD Pipeline (.gitlab-ci.yml)
```yaml
stages:
  - test
  - deploy

validate:
  stage: test
  script:
    - ./scripts/validate-controls.sh
  only:
    - merge_requests

deploy-staging:
  stage: deploy
  script:
    - rsync -avz production/ user@staging-server:/opt/dqc/controls/
    - curl -X POST http://staging-server:8090/api/run
  only:
    - main

deploy-production:
  stage: deploy
  script:
    - rsync -avz production/ user@prod-server:/opt/dqc/controls/
    - curl -X POST http://prod-server:8090/api/run
  when: manual
  only:
    - tags
```

### Validation Script (validate-controls.sh)
```bash
#!/bin/bash
ERRORS=0

for file in production/*.sql; do
    # Check file has $CODE
    if ! grep -q '\$CODE=' "$file"; then
        echo "ERROR: $file is missing \$CODE"
        ERRORS=$((ERRORS + 1))
    fi
    
    # Check file has $DESCRIPTION
    if ! grep -q '\$DESCRIPTION=' "$file"; then
        echo "ERROR: $file is missing \$DESCRIPTION"
        ERRORS=$((ERRORS + 1))
    fi
    
    # Check CODE is unique
    CODE=$(grep '\$CODE=' "$file" | head -1 | sed 's/.*\([A-Z_0-9]*\).*/\1/')
    if grep -r "\$CODE=$CODE" production/*.sql | wc -l | grep -qv "^1$"; then
        echo "ERROR: Duplicate CODE $CODE in $file"
        ERRORS=$((ERRORS + 1))
    fi
done

exit $ERRORS
```

---

## Common Issues and Solutions

### Control not appearing in list

- **Check**: File must have `.sql` extension
- **Check**: File must be readable by application user
- **Check**: File must contain `$CODE=` and `$DESCRIPTION=`

### Control not executing

- **Check**: Source database connection is configured correctly
- **Check**: SQL syntax is valid for your database
- **Check**: Table/column names match your database schema

### Duplicate CODE error

- **Check**: Each control must have a unique `$CODE` value
- **Fix**: Rename the CODE in one of the conflicting files

### Missing issueKey column

- **Check**: Query must return a column named `issueKey`
- **Fix**: Add `CAST(id AS CHAR) as issueKey` or similar to your query

### Application doesn't see new controls

- **Check**: File permissions on the `controls/` directory
- **Fix**: Restart the application or trigger manual run via `POST /api/run`

---

## Summary

| Aspect | Details |
|--------|---------|
| **File Location** | `controls/` directory |
| **File Format** | `.sql` files with `$CODE=` and `$DESCRIPTION=` metadata |
| **Query Requirement** | Must return `issueKey` column |
| **View Controls** | `GET /api/queries` (always available) |
| **Edit Controls** | Via file system (always) or API (if `dq.allow-write=true`) |
| **Delete Controls** | Via file system (always) or API (if `dq.allow-write=true`) |
| **Production Recommendation** | Set `dq.allow-write=false`, use external version control (Git, S3, etc.) |
