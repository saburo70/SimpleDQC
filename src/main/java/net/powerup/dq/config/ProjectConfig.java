package net.powerup.dq.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;

@Configuration
public class ProjectConfig {

    @Bean
    public CommandLineRunner init(
            @Qualifier("repoJdbcTemplate") JdbcTemplate repoJdbcTemplate,
            @Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate,
            @org.springframework.beans.factory.annotation.Value("${repo.schema:}") String repoSchema,
            @org.springframework.beans.factory.annotation.Value("${repo.use-source:false}") boolean useSource,
            @org.springframework.beans.factory.annotation.Value("${dq.demo:false}") boolean demoEnabled) {
        final String schema = (useSource && !repoSchema.isEmpty()) ? repoSchema + "." : "";
        return args -> {
            // 1. Ensure controls directory exists
            File dir = new File("controls");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 2. Initialize REPO Tables (MySQL)
            try {
                repoJdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS " + schema + "`dqExecution` (" +
                    "   `id` int NOT NULL AUTO_INCREMENT," +
                    "   `ts` datetime DEFAULT CURRENT_TIMESTAMP," +
                    "   `controlCode` varchar(45) DEFAULT NULL," +
                    "   `controlDescription` varchar(255) DEFAULT NULL," +
                    "   `issues` int DEFAULT NULL," +
                    "   `known` int DEFAULT NULL," +
                    "   `elapsed` int DEFAULT NULL," +
                    "   `sqlFile` varchar(255) DEFAULT NULL," +
                    "   `status` varchar(45) DEFAULT NULL," +
                    "   PRIMARY KEY (`id`)" +
                    ") ENGINE=InnoDB"
                );
                try {
                    repoJdbcTemplate.execute("ALTER TABLE " + schema + "`dqExecution` ADD COLUMN IF NOT EXISTS `status` varchar(45) DEFAULT NULL");
                } catch (Exception e) {
                    try {
                        repoJdbcTemplate.execute("ALTER TABLE " + schema + "`dqExecution` ADD COLUMN `status` varchar(45) DEFAULT NULL");
                    } catch (Exception e2) { /* already exists */ }
                }

                repoJdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS " + schema + "`dqKnownIssue` (" +
                    "  `id` int NOT NULL AUTO_INCREMENT," +
                    "  `controlCode` varchar(45) NOT NULL," +
                    "  `issueKey` varchar(45) NOT NULL," +
                    "  `description` varchar(255) DEFAULT NULL," +
                    "  `since` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  `isActive` int NOT NULL DEFAULT 1," +
                    "  PRIMARY KEY (`id`)" +
                    ") ENGINE=InnoDB"
                );
                try {
                    repoJdbcTemplate.execute("ALTER TABLE " + schema + "`dqKnownIssue` ADD COLUMN IF NOT EXISTS `isActive` int NOT NULL DEFAULT 1");
                } catch (Exception e) {
                    // In case IF NOT EXISTS fails, try a direct add if column is missing
                    try {
                        repoJdbcTemplate.execute("ALTER TABLE " + schema + "`dqKnownIssue` ADD COLUMN `isActive` int NOT NULL DEFAULT 1");
                    } catch (Exception e2) {
                        // Probably already exists or other error
                    }
                }
                // Mark any executions left in 'running' state as 'cancelled' (server crashed mid-run)
                int cancelled = repoJdbcTemplate.update(
                    "UPDATE " + schema + "`dqExecution` SET `status` = 'cancelled' WHERE `status` = 'running'"
                );
                if (cancelled > 0) {
                    System.out.println("Marked " + cancelled + " interrupted execution(s) as 'cancelled'.");
                }

                System.out.println("Repository tables initialized successfully.");
            } catch (Exception e) {
                System.err.println("Repository initialization failed: " + e.getMessage());
            }

            // 3. Initialize demo tables in Source DB (only when demo mode is enabled)
            if (demoEnabled) {
                try {
                    sourceJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
                    System.out.println("Demo source tables checked/initialized.");
                } catch (Exception e) {
                    System.err.println("Demo source DB initialization failed: " + e.getMessage());
                }
            }
        };
    }
}
