package net.powerup.dq.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Optional<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String recipients;
    private final String sendOn;

    public EmailService(
            Optional<JavaMailSender> mailSender,
            @Value("${dq.mail.enabled:false}") boolean enabled,
            @Value("${dq.mail.recipients:}") String recipients,
            @Value("${dq.mail.send-on:on-failure}") String sendOn) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.recipients = recipients;
        this.sendOn = sendOn;
    }

    public void sendControlsReport(List<ControlResult> results) {
        if (!enabled) return;
        if (mailSender.isEmpty()) {
            log.warn("Mail is enabled but no JavaMailSender is configured (check spring.mail.host)");
            return;
        }
        if (recipients.isBlank()) {
            log.warn("Mail is enabled but dq.mail.recipients is empty");
            return;
        }

        long failingCount = results.stream().filter(r -> r.issues() > 0 || "error".equals(r.status())).count();

        if ("on-failure".equalsIgnoreCase(sendOn) && failingCount == 0) return;

        String[] to = Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (to.length == 0) return;

        String subject = failingCount > 0
                ? String.format("[DQ Alert] %d control(s) with active issues", failingCount)
                : "[DQ Report] All controls passed";

        try {
            MimeMessage message = mailSender.get().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildHtmlBody(results, failingCount), true);
            mailSender.get().send(message);
            log.info("DQ report sent to {} recipient(s): {}", to.length, subject);
        } catch (Exception e) {
            log.error("Failed to send DQ report email", e);
        }
    }

    private String buildHtmlBody(List<ControlResult> results, long failingCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif'>");
        sb.append("<h2>Data Quality Controls Report</h2>");

        if (failingCount > 0) {
            sb.append("<p style='color:#c0392b'><strong>")
              .append(failingCount).append(" control(s) have active issues.</strong></p>");
        } else {
            sb.append("<p style='color:#27ae60'><strong>All controls passed.</strong></p>");
        }

        sb.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse'>");
        sb.append("<tr style='background:#f0f0f0'>")
          .append("<th>Code</th><th>Description</th><th>Issues</th><th>Known</th><th>Status</th>")
          .append("</tr>");

        for (ControlResult r : results) {
            boolean failing = r.issues() > 0 || "error".equals(r.status());
            String rowStyle = failing ? " style='background-color:#fdecea'" : "";
            sb.append("<tr").append(rowStyle).append(">")
              .append("<td>").append(r.code() != null ? r.code() : "").append("</td>")
              .append("<td>").append(r.description() != null ? r.description() : "").append("</td>")
              .append("<td>").append(r.issues()).append("</td>")
              .append("<td>").append(r.known()).append("</td>")
              .append("<td>").append(r.status()).append("</td>")
              .append("</tr>");
        }

        sb.append("</table></body></html>");
        return sb.toString();
    }
}
