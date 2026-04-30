package net.powerup.dq.controller;

import net.powerup.dq.service.DqcService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DqcService dqcService;
    private final JdbcTemplate repoJdbcTemplate;
    private final String repoSchema;

    public DashboardController(
            DqcService dqcService,
            @Qualifier("repoJdbcTemplate") JdbcTemplate repoJdbcTemplate,
            @org.springframework.beans.factory.annotation.Value("${repo.schema:}") String repoSchema,
            @org.springframework.beans.factory.annotation.Value("${repo.use-source:false}") boolean useSource) {
        this.dqcService = dqcService;
        this.repoJdbcTemplate = repoJdbcTemplate;
        this.repoSchema = (useSource && !repoSchema.isEmpty()) ? repoSchema + "." : "";
    }

    @GetMapping("/dashboard")
    public List<Map<String, Object>> getDashboard() {
        // Fetch the latest execution record for each control that still has issues from REPO
        return repoJdbcTemplate.queryForList(
            "SELECT e1.* FROM " + repoSchema + "dqExecution e1 " +
            "INNER JOIN (SELECT controlCode, MAX(ts) as max_ts FROM " + repoSchema + "dqExecution GROUP BY controlCode) e2 " +
            "ON e1.controlCode = e2.controlCode AND e1.ts = e2.max_ts " +
            "WHERE e1.issues > 0"
        );
    }

    @GetMapping("/controls/summary")
    public List<Map<String, Object>> getControlsSummary() {
        // Fetch the latest execution record for ALL controls
        return repoJdbcTemplate.queryForList(
            "SELECT e1.* FROM " + repoSchema + "dqExecution e1 " +
            "INNER JOIN (SELECT controlCode, MAX(ts) as max_ts FROM " + repoSchema + "dqExecution GROUP BY controlCode) e2 " +
            "ON e1.controlCode = e2.controlCode AND e1.ts = e2.max_ts " +
            "ORDER BY e1.issues DESC"
        );
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory() {
        return repoJdbcTemplate.queryForList(
            "SELECT * FROM " + repoSchema + "dqExecution ORDER BY ts DESC LIMIT 100"
        );
    }

    @DeleteMapping("/history/{id}")
    public void deleteHistory(@PathVariable long id) {
        repoJdbcTemplate.update("DELETE FROM " + repoSchema + "dqExecution WHERE id=?", id);
    }

    @GetMapping("/controls/{code}/issues")
    public List<Map<String, Object>> getIssues(@PathVariable String code) {
        return dqcService.getActiveIssues(code);
    }

    @PostMapping("/run")
    public String runControls() throws IOException {
        dqcService.runAllControls();
        return "Controls executed successfully";
    }

    @GetMapping("/controls/{code}/known-issues")
    public List<Map<String, Object>> getKnownIssues(@PathVariable String code) {
        return dqcService.getKnownIssues(code);
    }

    @PostMapping("/known-issues")
    public String addKnownIssue(@RequestBody Map<String, String> payload) {
        repoJdbcTemplate.update(
            "INSERT INTO " + repoSchema + "dqKnownIssue (controlCode, issueKey, description, isActive) VALUES (?, ?, ?, 1)",
            payload.get("controlCode"), payload.get("issueKey"), payload.get("description")
        );
        return "Known issue added";
    }

    @PostMapping("/known-issues/remove")
    public String removeKnownIssue(@RequestBody Map<String, String> payload) {
        repoJdbcTemplate.update(
            "UPDATE " + repoSchema + "dqKnownIssue SET isActive = 0 WHERE controlCode = ? AND issueKey = ? AND isActive = 1",
            payload.get("controlCode"), payload.get("issueKey")
        );
        return "Known issue removed";
    }
}
