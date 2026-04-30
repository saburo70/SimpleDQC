# Email Configuration Guide

SimpleDQC can send email reports after data quality control executions. Email configuration involves two parts: **SMTP connection settings** and **DQ-specific email options**.

---

## Configuration Overview

| Setting | Property | Default | Description |
|---------|----------|---------|-------------|
| SMTP Host | `spring.mail.host` | - | SMTP server hostname |
| SMTP Port | `spring.mail.port` | - | SMTP server port |
| SMTP Username | `spring.mail.username` | - | SMTP authentication username |
| SMTP Password | `spring.mail.password` | - | SMTP authentication password |
| SMTP Auth | `spring.mail.properties.mail.smtp.auth` | - | Enable SMTP authentication |
| SMTP STARTTLS | `spring.mail.properties.mail.smtp.starttls.enable` | - | Enable STARTTLS encryption |
| Email Enabled | `dq.mail.enabled` | `false` | Enable/disable DQ email reports |
| Recipients | `dq.mail.recipients` | - | Comma-separated list of email addresses |
| Send On | `dq.mail.send-on` | `on-failure` | When to send emails |

---

## SMTP Connection Configuration

Configure the connection to your SMTP server under the `spring.mail` namespace.

### Gmail / Google Workspace

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**Important:** For Gmail, use an [App Password](https://myaccount.google.com/apppasswords) instead of your regular password. You may need to enable "Less secure app access" or 2FA with app passwords.

### Microsoft 365 / Outlook

```yaml
spring:
  mail:
    host: smtp.office365.com
    port: 587
    username: your-email@yourdomain.com
    password: your-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### Yahoo Mail

```yaml
spring:
  mail:
    host: smtp.mail.yahoo.com
    port: 465
    username: your-email@yahoo.com
    password: your-password
    properties:
      mail:
        smtp:
          auth: true
          socketFactory:
            port: 465
            class: javax.net.ssl.SSLSocketFactory
```

### SMTP2GO

```yaml
spring:
  mail:
    host: mail.smtp2go.com
    port: 2525
    username: your-username
    password: your-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### SendGrid

```yaml
spring:
  mail:
    host: smtp.sendgrid.net
    port: 587
    username: apikey
    password: your-sendgrid-api-key
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**Note:** SendGrid uses `apikey` as the username and your API key as the password.

### Amazon SES

```yaml
spring:
  mail:
    host: email-smtp.your-region.amazonaws.com
    port: 587
    username: your-ses-username
    password: your-ses-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### Custom SMTP Server

```yaml
spring:
  mail:
    host: mail.your-company.com
    port: 587
    username: your-username
    password: your-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
          # Optional: Timeout settings
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
```

---

## DQ-Specific Email Options

Configure when and to whom emails are sent under the `dq.mail` namespace.

### Enable Email Sending

```yaml
dq:
  mail:
    enabled: true
```

When enabled, the application will send email reports after each scheduled or manual control run.

### Set Recipients

```yaml
dq:
  mail:
    recipients: "operations1@company.com,operations2@company.com,team@company.com"
```

- Multiple recipients are separated by commas
- Whitespace is automatically trimmed
- Empty entries are ignored

### Configure Send Trigger

The `send-on` option determines when emails are sent:

| Value | Description |
|-------|-------------|
| `on-failure` | Send email only when at least one control has issues (default) |
| `always` | Send email after every control run, regardless of results |

```yaml
dq:
  mail:
    send-on: always
```

---

## Complete Configuration Examples

### Development with Gmail

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: dq-alerts@gmail.com
    password: your-app-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

dq:
  mail:
    enabled: true
    recipients: "dev-team@company.com"
    send-on: always
```

### Production with Microsoft 365 (Send on Failure Only)

```yaml
spring:
  mail:
    host: smtp.office365.com
    port: 587
    username: dq-alerts@company.com
    password: secure-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

dq:
  mail:
    enabled: true
    recipients: "operations@company.com,devops@company.com"
    send-on: on-failure
```

### Disabled Email (Default)

```yaml
dq:
  mail:
    enabled: false
```

No SMTP configuration is needed when email is disabled.

---

## Email Content

Emails sent by SimpleDQC contain HTML-formatted reports with the following information:

### Subject Line

- **When issues exist**: `[DQ Alert] N control(s) with active issues`
- **When all pass**: `[DQ Report] All controls passed`

### Email Body

The email includes:

- A summary line indicating pass/fail status
- A table with the following columns:
  - **Code**: Control code
  - **Description**: Control description
  - **Issues**: Number of issues detected
  - **Known**: Number of known (whitelisted) issues
  - **Status**: Execution status (completed, error, etc.)
- Color-coded rows:
  - Red highlight for controls with issues
  - Normal background for passing controls

---

## Security Considerations

### Password Storage

- **Do NOT commit passwords to version control**
- Use environment variables or secret management:
  ```yaml
  spring:
    mail:
      password: ${SMTP_PASSWORD}
  ```
- Or use Spring Cloud Config for externalized configuration

### Recommended SMTP Settings

- Always use **STARTTLS** or **SSL/TLS** for encryption
- Use **App Passwords** for Gmail instead of regular passwords
- Restrict SMTP access to specific IPs if possible
- Use a dedicated email account for DQ alerts

### Testing Configuration

To test email configuration without running controls:

1. Set `dq.mail.enabled: true`
2. Set `dq.mail.send-on: always`
3. Run controls manually via POST `/api/run`
4. Check the application logs for email send status

---

## Troubleshooting

### "Mail is enabled but no JavaMailSender is configured"

**Cause**: SMTP host is not configured.

**Solution**: Add `spring.mail.host` to your configuration.

```yaml
spring:
  mail:
    host: smtp.your-provider.com
```

### "Mail is enabled but dq.mail.recipients is empty"

**Cause**: No recipients are configured.

**Solution**: Add at least one recipient:

```yaml
dq:
  mail:
    recipients: "your-email@company.com"
```

### Connection Timeout / Cannot Connect

**Causes**:
- Incorrect hostname or port
- Firewall blocking the connection
- Network connectivity issues

**Solutions**:
- Verify SMTP server hostname and port
- Test connectivity using `telnet smtp.server.com 587`
- Check firewall rules
- Verify DNS resolution

### Authentication Failed

**Causes**:
- Incorrect username or password
- Account locked or disabled
- SMTP authentication not enabled in configuration

**Solutions**:
- Verify credentials are correct
- Ensure `spring.mail.properties.mail.smtp.auth=true`
- Check if the account requires 2FA (use app password)
- Verify the account has SMTP access enabled

### Email Not Received (No Errors)

**Causes**:
- Email caught in spam folder
- SMTP server not accepting external connections
- Recipient domain blocking emails

**Solutions**:
- Check spam/junk folder
- Verify SMTP server accepts connections from your IP
- Add sender to recipient's allow list
- Check SMTP server logs

### STARTTLS Not Working

**Solution**: Try SSL instead:

```yaml
spring:
  mail:
    port: 465
    properties:
      mail:
        smtp:
          socketFactory:
            port: 465
            class: javax.net.ssl.SSLSocketFactory
```

---

## Environment Variable Support

All email configuration properties can be set via environment variables:

```bash
# SMTP Configuration
export SPRING_MAIL_HOST=smtp.gmail.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME=your-email@gmail.com
export SPRING_MAIL_PASSWORD=your-password
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true

# DQ Email Options
export DQ_MAIL_ENABLED=true
export DQ_MAIL_RECIPIENTS="email1@company.com,email2@company.com"
export DQ_MAIL_SEND-ON=on-failure
```

Or use Java system properties:

```bash
java -Dspring.mail.host=smtp.gmail.com -Ddq.mail.enabled=true -jar your-app.jar
```
