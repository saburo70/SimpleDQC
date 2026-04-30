package net.powerup.dq.config;

import net.powerup.dq.service.DqcService;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Configuration
public class DqcSchedulerConfig implements SchedulingConfigurer {

    private final DqcService dqcService;
    private static final String CRON_FILE = "scheduler/cron.txt";

    public DqcSchedulerConfig(DqcService dqcService) {
        this.dqcService = dqcService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
            () -> {
                try {
                    System.out.println("Executing scheduled DQ controls...");
                    dqcService.runAllControls();
                } catch (IOException e) {
                    System.err.println("Scheduled DQ execution failed: " + e.getMessage());
                }
            },
            triggerContext -> {
                String cron = readCronFromFile().orElse("0 0 * * * *"); // Default to hourly if file fails
                CronTrigger trigger = new CronTrigger(cron);
                return trigger.nextExecution(triggerContext);
            }
        );
    }

    private Optional<String> readCronFromFile() {
        try {
            File file = new File(CRON_FILE);
            if (file.exists()) {
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return Optional.of(content);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read cron file: " + e.getMessage());
        }
        return Optional.empty();
    }
}
