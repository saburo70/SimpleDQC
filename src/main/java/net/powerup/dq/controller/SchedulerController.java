package net.powerup.dq.controller;

import net.powerup.dq.config.DqcSchedulerConfig.DqcScheduleRegistry;
import net.powerup.dq.config.DqcSchedulerConfig.ScheduleLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/schedules")
public class SchedulerController {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+");

    private final DqcScheduleRegistry registry;
    private final boolean allowWrite;

    public SchedulerController(DqcScheduleRegistry registry,
                               @Value("${dq.allow-write:false}") boolean allowWrite) {
        this.registry = registry;
        this.allowWrite = allowWrite;
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", registry.readSchedules());
        result.put("writeEnabled", allowWrite);
        return result;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body) {
        if (!allowWrite) {
            return ResponseEntity.status(403)
                .body("Write operations are disabled — set dq.allow-write=true to enable");
        }

        Object rawItems = body.get("items");
        if (!(rawItems instanceof List<?> rawList)) {
            return ResponseEntity.badRequest().body("'items' must be a list");
        }

        List<ScheduleLine> schedules = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> m)) {
                return ResponseEntity.badRequest().body("Each item must be an object");
            }
            String name = m.get("name") == null ? "" : m.get("name").toString().trim();
            String cron = m.get("cron") == null ? "" : m.get("cron").toString().trim();

            if (name.isEmpty()) return ResponseEntity.badRequest().body("Schedule name is required");
            if (!NAME_PATTERN.matcher(name).matches()) {
                return ResponseEntity.badRequest().body("Invalid name '" + name + "': use letters, digits, _ or -");
            }
            if (!seenNames.add(name)) {
                return ResponseEntity.badRequest().body("Duplicate schedule name: " + name);
            }
            if (cron.isEmpty() || cron.split("\\s+").length != 6) {
                return ResponseEntity.badRequest()
                    .body("Cron expression for '" + name + "' must have 6 fields");
            }
            try {
                CronExpression.parse(cron);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body("Invalid cron for '" + name + "': " + e.getMessage());
            }
            schedules.add(new ScheduleLine(name, cron));
        }

        try {
            registry.writeSchedules(schedules);
            return ResponseEntity.ok(Map.of("saved", schedules.size()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Could not save: " + e.getMessage());
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> delete(@PathVariable String name) {
        if (!allowWrite) {
            return ResponseEntity.status(403)
                .body("Write operations are disabled — set dq.allow-write=true to enable");
        }
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            return ResponseEntity.badRequest().body("Invalid name");
        }

        List<ScheduleLine> remaining = new ArrayList<>();
        boolean found = false;
        for (ScheduleLine s : registry.readSchedules()) {
            if (s.name.equals(name)) {
                found = true;
            } else {
                remaining.add(s);
            }
        }
        if (!found) return ResponseEntity.notFound().build();

        try {
            registry.writeSchedules(remaining);
            return ResponseEntity.ok(Map.of("deleted", name));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Could not save: " + e.getMessage());
        }
    }
}
