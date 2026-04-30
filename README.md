# SimpleDQC

SimpleDQC is a lightweight, SQL-driven Data Quality Control framework designed for BI and analytics environments where complex system interconnections (ERP, CRM, ODS, DWH) often lead to data integrity challenges.

## The Philosophy

In many data projects, Data Quality (DQ) is an extremely broad issue that manifests as in-system glitches or cross-system inconsistencies. While systems might technically function as designed, they often fail to enforce specific business rules required by the organization.

SimpleDQC was born from a few key observations:
- **Reactive Nature**: DQ controls are often reactive. An issue is discovered, a ticket is opened, and a developer writes a SQL query to find the root cause.
- **Low Friction**: If implementing a control requires significant effort, it won't be done. The path from "SQL query that detects an issue" to "Daily automated control" should be effortless.
- **Simplicity Wins**: No fancy interfaces or complex configurations—just drop a SQL file into a folder, and you're done.

## Core Concepts

### 1. Frictionless Ingestion
To add a new control, you simply create a `.sql` file and place it in the `controls/` directory. There is no central configuration file to update. Everything the system needs is embedded directly within the SQL file using simple metadata tags:
- `$CODE=`: A unique identifier for the control.
- `$DESCRIPTION=`: A human-readable explanation of the check.

### 2. Identifiable Issues (`issueKey`)
A control query should produce one row per issue found (e.g., one row for every user missing an email). Each row must have an `issueKey`—a unique identifier for that specific instance of the problem.

### 3. Handling "Known Issues"
Not every DQ exception is a bug; some are "known issues" or valid exceptions that are there to stay. Because SimpleDQC identifies specific issue instances via the `issueKey`, you can mark them as "Known Issues" in the system to suppress daily notifications for them while still monitoring for new occurrences.

### 4. Risk-Free Deployment
Adding a new control should never break existing ones. By using independent SQL files, the risk of regression is minimized. We recommend a Git-driven workflow:
1. **Develop**: An operator creates and verifies a SQL query locally.
2. **Commit**: The file is added to a Git repository.
3. **Deploy**: Automation (CI/CD) pushes the file to the production server.

## Authentication & Security

By design, **SimpleDQC does not include a built-in authentication layer**. 

In professional data environments, tools like SimpleDQC are typically integrated into existing ecosystems. Authentication is usually handled by:
- **Platform Integration**: Running behind a corporate proxy or within a data platform (like Airflow, Superset, or a custom portal) that already manages identity.
- **Network Security**: Restricting access via VPN or IP allow-listing.
- **Custom Security**: Adding a security layer that matches your organization's standards (LDAP, OAuth2, Active Directory).

### Adding Authentication to SimpleDQC

Since SimpleDQC is a standard Spring Boot application, you can easily add security by including the **Spring Security** starter in your `pom.xml`:

#### 1. Basic Auth / LDAP
For quick internal use, you can configure Basic Authentication or connect to your company's LDAP:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .anyRequest().authenticated()
            .and().httpBasic();
    }
}
```

#### 2. OAuth2 / OpenID Connect (OIDC)
To integrate with providers like Okta, Auth0, or Azure AD:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          my-provider:
            client-id: ${CLIENT_ID}
            client-secret: ${CLIENT_SECRET}
            scope: openid, profile, email
```

## Getting Started

For detailed instructions on how to configure and run SimpleDQC, please refer to the documentation:

- [Running the Application](docs/RUNNING_THE_APPLICATION.md)
- [Docker Deployment](docs/DOCKER_GUIDE.md)
- [Dashboard Guide (with Screenshots)](docs/DASHBOARD_GUIDE.md)
- [API Endpoints](docs/API_ENDPOINTS.md)
- [TLS/SSL Configuration](docs/TLS_CONFIGURATION.md)
- [Control Query Format](docs/CONTROL_QUERIES.md)
- [Email Configuration](docs/EMAIL_CONFIGURATION.md)
- [Repository Configuration](docs/REPOSITORY_CONFIGURATION.md)

## License

SimpleDQC is released under the [Apache License 2.0](LICENSE).
