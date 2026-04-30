package net.powerup.dq.controller;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/queries")
public class QueryController {

    private static final String CONTROLS_DIR = "controls";
    private static final Pattern CODE_PATTERN = Pattern.compile("\\$CODE=(.+)",        Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_PATTERN = Pattern.compile("\\$DESCRIPTION=(.+)", Pattern.CASE_INSENSITIVE);
    private static final int     RUN_ROW_LIMIT = 500;

    private final boolean       allowWrite;
    private final JdbcTemplate  sourceJdbcTemplate;

    public QueryController(
            @Value("${dq.allow-write:false}") boolean allowWrite,
            @Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate) {
        this.allowWrite         = allowWrite;
        this.sourceJdbcTemplate = sourceJdbcTemplate;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runQuery(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank())
            return ResponseEntity.badRequest().body("No SQL content provided");
        try {
            List<Map<String, Object>> rows = sourceJdbcTemplate.queryForList(content);
            boolean truncated = rows.size() > RUN_ROW_LIMIT;
            if (truncated) rows = new ArrayList<>(rows.subList(0, RUN_ROW_LIMIT));
            return ResponseEntity.ok(Map.of("rows", rows, "truncated", truncated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public Map<String, Object> listQueries() {
        File dir = new File(CONTROLS_DIR);
        List<Map<String, Object>> items = new ArrayList<>();
        if (dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".sql"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    try {
                        String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("filename",    f.getName());
                        item.put("code",        extract(content, CODE_PATTERN));
                        item.put("description", extract(content, DESC_PATTERN));
                        item.put("demo",        f.getName().startsWith("__demo__"));
                        items.add(item);
                    } catch (IOException ignored) {}
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items",        items);
        result.put("writeEnabled", allowWrite);
        return result;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<?> getQuery(@PathVariable String filename) {
        if (!safe(filename)) return ResponseEntity.badRequest().body("Invalid filename");
        File file = new File(CONTROLS_DIR, filename);
        if (!file.exists()) return ResponseEntity.notFound().build();
        try {
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename",    filename);
            result.put("code",        extract(content, CODE_PATTERN));
            result.put("description", extract(content, DESC_PATTERN));
            result.put("content",     content);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not read file");
        }
    }

    @PostMapping
    public ResponseEntity<?> createQuery(@RequestBody Map<String, String> body) {
        if (!allowWrite) return forbidden();
        String filename = body.get("filename");
        if (!safe(filename))            return ResponseEntity.badRequest().body("Invalid filename");
        if (!filename.endsWith(".sql")) return ResponseEntity.badRequest().body("Filename must end with .sql");
        File file = new File(CONTROLS_DIR, filename);
        if (file.exists())              return ResponseEntity.status(HttpStatus.CONFLICT).body("File already exists");
        return persist(file, body.get("content"), null);
    }

    @PutMapping("/{filename}")
    public ResponseEntity<?> updateQuery(@PathVariable String filename, @RequestBody Map<String, String> body) {
        if (!allowWrite) return forbidden();
        if (!safe(filename)) return ResponseEntity.badRequest().body("Invalid filename");
        File file = new File(CONTROLS_DIR, filename);
        if (!file.exists()) return ResponseEntity.notFound().build();
        return persist(file, body.get("content"), filename);
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<?> deleteQuery(@PathVariable String filename) {
        if (!allowWrite) return forbidden();
        if (!safe(filename)) return ResponseEntity.badRequest().body("Invalid filename");
        File file = new File(CONTROLS_DIR, filename);
        if (!file.exists()) return ResponseEntity.notFound().build();
        file.delete();
        return ResponseEntity.ok("Deleted");
    }

    private ResponseEntity<?> persist(File file, String content, String ownFilename) {
        if (content == null || content.isBlank())
            return ResponseEntity.badRequest().body("Content is empty");

        String code = extract(content, CODE_PATTERN);
        String desc = extract(content, DESC_PATTERN);

        if (code == null || code.isBlank())
            return ResponseEntity.badRequest().body("$CODE is missing or empty in the query comment");
        if (desc == null || desc.isBlank())
            return ResponseEntity.badRequest().body("$DESCRIPTION is missing or empty in the query comment");

        // Uniqueness check: no other file may use the same CODE
        File dir = new File(CONTROLS_DIR);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".sql"));
        if (files != null) {
            for (File f : files) {
                if (f.getName().equals(ownFilename)) continue;
                try {
                    String other = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                    if (code.equals(extract(other, CODE_PATTERN)))
                        return ResponseEntity.badRequest()
                            .body("CODE '" + code + "' is already used in " + f.getName());
                } catch (IOException ignored) {}
            }
        }

        try {
            FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("filename", file.getName(), "code", code, "description", desc));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not write file: " + e.getMessage());
        }
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("Write operations are disabled — set dq.allow-write=true to enable");
    }

    private boolean safe(String name) {
        return name != null && !name.isBlank()
            && !name.contains("..") && !name.contains("/") && !name.contains("\\")
            && name.matches("[\\w\\-\\.]+");
    }

    private String extract(String content, Pattern p) {
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }
}
