package net.powerup.dq.service;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DqcService {

    private final JdbcTemplate repoJdbcTemplate;
    private final JdbcTemplate sourceJdbcTemplate;
    private final String repoSchema;
    private final boolean demoEnabled;
    private final EmailService emailService;
    private static final String CONTROLS_DIR = "controls";
    private static final String DEMO_FILE_PREFIX = "__demo__";

    public DqcService(
            @Qualifier("repoJdbcTemplate") JdbcTemplate repoJdbcTemplate,
            @Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate,
            @org.springframework.beans.factory.annotation.Value("${repo.schema:}") String repoSchema,
            @org.springframework.beans.factory.annotation.Value("${repo.use-source:false}") boolean useSource,
            @org.springframework.beans.factory.annotation.Value("${dq.demo:false}") boolean demoEnabled,
            EmailService emailService) {
        this.repoJdbcTemplate = repoJdbcTemplate;
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.repoSchema = (useSource && !repoSchema.isEmpty()) ? repoSchema + "." : "";
        this.demoEnabled = demoEnabled;
        this.emailService = emailService;
    }

    public void runAllControls() throws IOException {
        File dir = new File(CONTROLS_DIR);
        if (!dir.exists()) return;

        Collection<File> files = FileUtils.listFiles(dir, new String[]{"sql"}, false);
        List<ControlResult> results = new ArrayList<>();
        for (File file : files) {
            if (!demoEnabled && file.getName().startsWith(DEMO_FILE_PREFIX)) continue;
            results.add(runControl(file));
        }
        emailService.sendControlsReport(results);
    }

    private ControlResult runControl(File file) throws IOException {
        long startTime = System.currentTimeMillis();
        String queryText = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        String controlCode = extractCommentVariable(queryText, "CODE");
        String controlDesc = extractCommentVariable(queryText, "DESCRIPTION");

        // 1. Log start with status = 'running'
        String insertSql = "INSERT INTO " + repoSchema + "dqExecution (controlCode, controlDescription, sqlFile, status) VALUES (?, ?, ?, 'running')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        repoJdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(insertSql, new String[]{"id"});
            ps.setString(1, controlCode);
            ps.setString(2, controlDesc);
            ps.setString(3, file.getName());
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();

        try {
            // 2. Count Total Issues on SOURCE
            String totalQuery = String.format("SELECT count(*) FROM (%s) q", queryText);
            Integer totalIssues = sourceJdbcTemplate.queryForObject(totalQuery, Integer.class);

            // 3. Fetch Known Issues for this control from REPO
            List<String> activeKnownIssues = repoJdbcTemplate.queryForList(
                "SELECT issueKey FROM " + repoSchema + "dqKnownIssue WHERE controlCode = ? AND isActive = 1",
                String.class, controlCode
            );

            // 4. Count Active Issues on SOURCE (filter out known)
            Integer activeIssues;
            if (activeKnownIssues.isEmpty()) {
                activeIssues = totalIssues;
            } else {
                String placeholders = String.join(",", activeKnownIssues.stream().map(s -> "?").toList());
                String activeQuery = String.format(
                    "SELECT count(*) FROM (%s) q WHERE q.issueKey NOT IN (%s)",
                    queryText, placeholders
                );
                activeIssues = sourceJdbcTemplate.queryForObject(activeQuery, Integer.class, activeKnownIssues.toArray());
            }

            int knownCount = (totalIssues != null ? totalIssues : 0) - (activeIssues != null ? activeIssues : 0);
            int issues = activeIssues != null ? activeIssues : 0;
            long elapsed = System.currentTimeMillis() - startTime;

            // 5. Update with results and status = 'done'
            repoJdbcTemplate.update(
                "UPDATE " + repoSchema + "dqExecution SET status='done', issues=?, known=?, elapsed=? WHERE id=?",
                issues, knownCount, elapsed, id
            );
            return new ControlResult(controlCode, controlDesc, issues, knownCount, "done");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            repoJdbcTemplate.update(
                "UPDATE " + repoSchema + "dqExecution SET status='error', elapsed=? WHERE id=?",
                elapsed, id
            );
            return new ControlResult(controlCode, controlDesc, 0, 0, "error");
        }
    }

    public List<Map<String, Object>> getKnownIssues(String controlCode) {
        String queryText = findQueryForControl(controlCode);
        if (queryText == null) return List.of();

        List<String> knownKeys = repoJdbcTemplate.queryForList(
            "SELECT issueKey FROM " + repoSchema + "dqKnownIssue WHERE controlCode = ? AND isActive = 1",
            String.class, controlCode
        );
        if (knownKeys.isEmpty()) return List.of();

        String placeholders = String.join(",", knownKeys.stream().map(s -> "?").toList());
        return sourceJdbcTemplate.queryForList(
            String.format("SELECT q.* FROM (%s) q WHERE q.issueKey IN (%s)", queryText, placeholders),
            knownKeys.toArray()
        );
    }

    private String findQueryForControl(String controlCode) {
        File dir = new File(CONTROLS_DIR);
        Collection<File> files = FileUtils.listFiles(dir, new String[]{"sql"}, false);
        for (File file : files) {
            if (!demoEnabled && file.getName().startsWith(DEMO_FILE_PREFIX)) continue;
            try {
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                if (controlCode.equals(extractCommentVariable(content, "CODE"))) {
                    return content;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public List<Map<String, Object>> getActiveIssues(String controlCode) {
        String queryText = findQueryForControl(controlCode);

        if (queryText == null) return List.of();

        // Fetch known issues from REPO
        List<String> activeKnownIssues = repoJdbcTemplate.queryForList(
            "SELECT issueKey FROM " + repoSchema + "dqKnownIssue WHERE controlCode = ? AND isActive = 1",
            String.class, controlCode
        );

        // Execute filtered query on SOURCE
        if (activeKnownIssues.isEmpty()) {
            return sourceJdbcTemplate.queryForList(queryText);
        } else {
            String placeholders = String.join(",", activeKnownIssues.stream().map(s -> "?").toList());
            String filteredQuery = String.format(
                "SELECT q.* FROM (%s) q WHERE q.issueKey NOT IN (%s)",
                queryText, placeholders
            );
            return sourceJdbcTemplate.queryForList(filteredQuery, activeKnownIssues.toArray());
        }
    }

    private String extractCommentVariable(String text, String varName) {
        Pattern pattern = Pattern.compile("\\$" + varName + "=(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
