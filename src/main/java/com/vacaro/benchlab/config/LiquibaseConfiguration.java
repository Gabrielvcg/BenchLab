package com.vacaro.benchlab.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.liquibase.SpringLiquibaseUtil;

@Configuration
public class LiquibaseConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    private final Environment env;

    public LiquibaseConfiguration(Environment env) {
        this.env = env;
    }

    @Bean
    public SpringLiquibase liquibase(
        @Qualifier("taskExecutor") Executor executor,
        LiquibaseProperties liquibaseProperties,
        @LiquibaseDataSource ObjectProvider<DataSource> liquibaseDataSource,
        ObjectProvider<DataSource> dataSource,
        ApplicationProperties applicationProperties,
        DataSourceProperties dataSourceProperties
    ) {
        SpringLiquibase liquibase;
        if (Boolean.TRUE.equals(applicationProperties.getLiquibase().getAsyncStart())) {
            liquibase = SpringLiquibaseUtil.createAsyncSpringLiquibase(
                this.env,
                executor,
                liquibaseDataSource.getIfAvailable(),
                liquibaseProperties,
                dataSource.getIfUnique(),
                dataSourceProperties
            );
        } else {
            liquibase = SpringLiquibaseUtil.createSpringLiquibase(
                liquibaseDataSource.getIfAvailable(),
                liquibaseProperties,
                dataSource.getIfUnique(),
                dataSourceProperties
            );
        }
        liquibase.setChangeLog("classpath:config/liquibase/master.xml");
        if (!CollectionUtils.isEmpty(liquibaseProperties.getContexts())) {
            liquibase.setContexts(StringUtils.collectionToCommaDelimitedString(liquibaseProperties.getContexts()));
        }
        liquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());
        liquibase.setLiquibaseSchema(liquibaseProperties.getLiquibaseSchema());
        liquibase.setLiquibaseTablespace(liquibaseProperties.getLiquibaseTablespace());
        liquibase.setDatabaseChangeLogLockTable(liquibaseProperties.getDatabaseChangeLogLockTable());
        liquibase.setDatabaseChangeLogTable(liquibaseProperties.getDatabaseChangeLogTable());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        if (!CollectionUtils.isEmpty(liquibaseProperties.getLabelFilter())) {
            liquibase.setLabelFilter(StringUtils.collectionToCommaDelimitedString(liquibaseProperties.getLabelFilter()));
        }
        Map<String, String> parameters = new HashMap<>();
        if (!CollectionUtils.isEmpty(liquibaseProperties.getParameters())) {
            parameters.putAll(liquibaseProperties.getParameters());
        }
        boolean migrationsEnabled = liquibaseProperties.isEnabled() && !env.matchesProfiles(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE);
        if (migrationsEnabled) {
            Map<String, String> existingAuthors = ExistingChangeSetAuthorResolver.resolve(
                liquibase.getDataSource(),
                liquibaseProperties.getLiquibaseSchema(),
                liquibaseProperties.getDefaultSchema(),
                liquibaseProperties.getDatabaseChangeLogTable()
            );
            existingAuthors.forEach((parameter, author) -> {
                if (parameters.containsKey(parameter) && !author.equals(parameters.get(parameter))) {
                    throw new IllegalStateException("A configured changelog parameter conflicts with a preserved migration identity");
                }
            });
            parameters.putAll(existingAuthors);
        }
        liquibase.setChangeLogParameters(parameters);
        liquibase.setRollbackFile(liquibaseProperties.getRollbackFile());
        liquibase.setTestRollbackOnUpdate(liquibaseProperties.isTestRollbackOnUpdate());
        if (env.matchesProfiles(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE)) {
            liquibase.setShouldRun(false);
        } else {
            liquibase.setShouldRun(liquibaseProperties.isEnabled());
            LOG.debug("Configuring Liquibase");
        }
        return liquibase;
    }
}
