package net.powerup.dq.config;

import net.powerup.dq.service.DqcService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

@Configuration
public class DqcSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public TaskScheduler dqcTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("dq-sched-");
        scheduler.initialize();
        return scheduler;
    }

    @Component
    public static class DqcScheduleRegistry {

        public static final String CRON_FILE = "scheduler/cron.txt";
        private static final String DEFAULT_NAME = "default";

        private final DqcService dqcService;
        private final TaskScheduler taskScheduler;
        private final Map<String, ScheduledFuture<?>> futures = new LinkedHashMap<>();

        public DqcScheduleRegistry(DqcService dqcService,
                                   @Qualifier("dqcTaskScheduler") TaskScheduler taskScheduler) {
            this.dqcService = dqcService;
            this.taskScheduler = taskScheduler;
        }

        @PostConstruct
        public void start() {
            reload();
        }

        public synchronized List<ScheduleLine> readSchedules() {
            List<ScheduleLine> list = new ArrayList<>();
            File file = new File(CRON_FILE);
            if (!file.exists()) return list;
            try {
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                for (String raw : content.split("\\r?\\n")) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length < 6) continue;
                    String cron;
                    String name;
                    if (parts.length == 6) {
                        cron = line;
                        name = DEFAULT_NAME;
                    } else {
                        cron = String.join(" ", Arrays.copyOfRange(parts, 0, 6));
                        name = parts[6];
                    }
                    list.add(new ScheduleLine(name, cron));
                }
            } catch (IOException e) {
                System.err.println("Could not read schedules file: " + e.getMessage());
            }
            return list;
        }

        public synchronized void writeSchedules(List<ScheduleLine> schedules) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("# Schedule lines: <second> <minute> <hour> <day-of-month> <month> <day-of-week> <name>\n");
            sb.append("# Lines starting with # are ignored. The name must be a single token (letters, digits, _ or -).\n");
            for (ScheduleLine s : schedules) {
                sb.append(s.cron).append(' ').append(s.name).append('\n');
            }
            File file = new File(CRON_FILE);
            file.getParentFile().mkdirs();
            FileUtils.writeStringToFile(file, sb.toString(), StandardCharsets.UTF_8);
            reload();
        }

        public synchronized void reload() {
            for (ScheduledFuture<?> f : futures.values()) {
                if (f != null) f.cancel(false);
            }
            futures.clear();

            Set<String> seenNames = new HashSet<>();
            for (ScheduleLine line : readSchedules()) {
                if (!seenNames.add(line.name)) {
                    System.err.println("Skipping duplicate schedule name: " + line.name);
                    continue;
                }
                try {
                    CronTrigger trigger = new CronTrigger(line.cron);
                    final String name = line.name;
                    ScheduledFuture<?> future = taskScheduler.schedule(() -> {
                        try {
                            System.out.println("[schedule:" + name + "] Executing scheduled DQ controls...");
                            dqcService.runAllControls();
                        } catch (Exception e) {
                            System.err.println("[schedule:" + name + "] Scheduled DQ execution failed: " + e.getMessage());
                        }
                    }, trigger);
                    futures.put(line.name, future);
                    System.out.println("[schedule:" + line.name + "] Registered cron '" + line.cron + "'");
                } catch (IllegalArgumentException e) {
                    System.err.println("[schedule:" + line.name + "] Invalid cron '" + line.cron + "': " + e.getMessage());
                }
            }
        }
    }

    public static class ScheduleLine {
        public String name;
        public String cron;

        public ScheduleLine() {}

        public ScheduleLine(String name, String cron) {
            this.name = name;
            this.cron = cron;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}
