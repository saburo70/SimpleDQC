package net.powerup.dq.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean("repoDataSource")
    @Primary
    @ConditionalOnExpression("${repo.use-source:false} || ${repo.embedded:false}")
    public DataSource repoDataSourceLocalOrDelegated(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("${repo.use-source:false}") boolean useSource) {
        if (useSource) {
            return sourceDataSource;
        }
        new java.io.File("repository").mkdirs();
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:file:./repository/dq-repo;MODE=MySQL;NON_KEYWORDS=VALUE")
                .username("sa")
                .password("")
                .build();
    }

    @Bean("repoDataSource")
    @Primary
    @ConfigurationProperties("repo.datasource")
    @ConditionalOnExpression("!${repo.use-source:false} && !${repo.embedded:false}")
    public DataSource repoDataSourceExternal() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("source.datasource")
    public DataSource sourceDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public JdbcTemplate repoJdbcTemplate(@Qualifier("repoDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public PlatformTransactionManager repoTransactionManager(@Qualifier("repoDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public PlatformTransactionManager sourceTransactionManager(@Qualifier("sourceDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "repo.use-source", havingValue = "false", matchIfMissing = true)
    public DataSourceInitializer repoSchemaInitializer(@Qualifier("repoDataSource") DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-repo.sql"));
        populator.setContinueOnError(true);
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
