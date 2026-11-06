package com.vacaro.benchlab.config;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.liquibase.AsyncSpringLiquibase;

class LiquibaseConfigurationIdentityTest {
    @Test void defaultNullParametersAreSupportedAndDefaultsResolveBeforeMigration() throws Exception {
        LiquibaseProperties properties = new LiquibaseProperties();
        assertNull(properties.getParameters());
        var fixture = new ExistingChangeSetAuthorResolverTest.Fixture();fixture.tableExists=false;
        SpringLiquibase result = configure(properties,new MockEnvironment(),fixture.dataSource(),null,false,Runnable::run);
        assertEquals("maintainers",parameters(result).get("benchmarkCoreAuthor"));
        assertEquals(3,parameters(result).size());assertTrue(fixture.closed);
    }
    @Test void disabledLiquibaseDoesNotConnectToTheDatabase() throws Exception {
        LiquibaseProperties properties=new LiquibaseProperties();properties.setEnabled(false);
        var fixture=new ExistingChangeSetAuthorResolverTest.Fixture();fixture.connectionFailure=true;
        SpringLiquibase result=assertDoesNotThrow(()->configure(properties,new MockEnvironment(),fixture.dataSource(),null,false,Runnable::run));
        assertEquals(Boolean.FALSE,ReflectionTestUtils.getField(result,"shouldRun"));assertTrue(parameters(result).isEmpty());
    }
    @Test void noLiquibaseProfileDoesNotConnectToTheDatabase() throws Exception {
        MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE);
        var fixture=new ExistingChangeSetAuthorResolverTest.Fixture();fixture.connectionFailure=true;
        SpringLiquibase result=assertDoesNotThrow(()->configure(new LiquibaseProperties(),environment,fixture.dataSource(),null,true,Runnable::run));
        assertEquals(Boolean.FALSE,ReflectionTestUtils.getField(result,"shouldRun"));
    }
    @Test void qualifiedMigrationDataSourceTakesPrecedenceOverApplicationDataSourceAndUrl() throws Exception {
        LiquibaseProperties properties=new LiquibaseProperties();properties.setUrl("jdbc:postgresql://127.0.0.1:1/not_used");
        var selected=new ExistingChangeSetAuthorResolverTest.Fixture();selected.rows.add(Map.of("ID","20260531020000-1","AUTHOR","previous.owner","FILENAME","config/liquibase/changelog/20260531020000_benchmark_core.xml"));
        var ignored=new ExistingChangeSetAuthorResolverTest.Fixture();ignored.connectionFailure=true;
        DataSource preferred=selected.dataSource();
        SpringLiquibase result=configure(properties,new MockEnvironment(),preferred,ignored.dataSource(),false,Runnable::run);
        assertSame(preferred,result.getDataSource());assertEquals("previous.owner",parameters(result).get("benchmarkCoreAuthor"));
    }
    @Test void dedicatedLiquibaseUrlCreatesItsOwnDataSourceWithoutUsingApplicationPool() throws Exception {
        LiquibaseProperties properties=new LiquibaseProperties();properties.setEnabled(false);properties.setUrl("jdbc:postgresql://127.0.0.1:1/dedicated_migration");
        var ignored=new ExistingChangeSetAuthorResolverTest.Fixture();ignored.connectionFailure=true;
        SpringLiquibase result=configure(properties,new MockEnvironment(),null,ignored.dataSource(),false,Runnable::run);
        assertInstanceOf(HikariDataSource.class,result.getDataSource());
        HikariDataSource migration=(HikariDataSource)result.getDataSource();assertEquals(properties.getUrl(),migration.getJdbcUrl());assertEquals("application_fixture",migration.getUsername());migration.close();
    }
    @Test void conflictingAuthorOverrideFailsBeforeMigrationInitialization() throws Exception {
        LiquibaseProperties properties=new LiquibaseProperties();properties.setParameters(Map.of("benchmarkCoreAuthor","different.owner"));
        var fixture=new ExistingChangeSetAuthorResolverTest.Fixture();fixture.tableExists=false;
        assertThrows(IllegalStateException.class,()->configure(properties,new MockEnvironment(),fixture.dataSource(),null,false,Runnable::run));
    }
    @Test void unrelatedParametersAreRetainedAndOriginalMapIsNotModified() throws Exception {
        LiquibaseProperties properties=new LiquibaseProperties();properties.setParameters(Map.of("customParameter","customValue"));
        var fixture=new ExistingChangeSetAuthorResolverTest.Fixture();fixture.tableExists=false;
        SpringLiquibase result=configure(properties,new MockEnvironment(),fixture.dataSource(),null,false,Runnable::run);
        assertEquals("customValue",parameters(result).get("customParameter"));assertEquals(Map.of("customParameter","customValue"),properties.getParameters());
    }
    @Test void asyncInstanceReceivesResolvedAuthorsBeforeSchedulingAnyMigrationWork() throws Exception {
        var fixture=new ExistingChangeSetAuthorResolverTest.Fixture();fixture.tableExists=false;AtomicInteger scheduled=new AtomicInteger();
        SpringLiquibase result=configure(new LiquibaseProperties(),new MockEnvironment(),fixture.dataSource(),null,true,task->scheduled.incrementAndGet());
        assertInstanceOf(AsyncSpringLiquibase.class,result);assertEquals("maintainers",parameters(result).get("benchmarkCoreAuthor"));assertEquals(0,scheduled.get());
    }
    @SuppressWarnings("unchecked") private static Map<String,String> parameters(SpringLiquibase result) {
        return (Map<String,String>)ReflectionTestUtils.getField(result,"parameters");
    }
    private static SpringLiquibase configure(LiquibaseProperties properties,MockEnvironment environment,DataSource preferred,DataSource application,boolean async,Executor executor) throws Exception {
        ApplicationProperties applicationProperties=new ApplicationProperties();applicationProperties.getLiquibase().setAsyncStart(async);
        DataSourceProperties datasourceProperties = new DataSourceProperties();
        datasourceProperties.setBeanClassLoader(LiquibaseConfigurationIdentityTest.class.getClassLoader());
        datasourceProperties.setUrl("jdbc:postgresql://127.0.0.1:1/application_fixture");
        datasourceProperties.setUsername("application_fixture");
        datasourceProperties.setPassword("unused_fixture");
        datasourceProperties.afterPropertiesSet();
        return new LiquibaseConfiguration(environment).liquibase(executor,properties,provider(preferred),provider(application),applicationProperties,datasourceProperties);
    }
    private static ObjectProvider<DataSource> provider(DataSource dataSource) {
        StaticListableBeanFactory factory=new StaticListableBeanFactory();if(dataSource!=null)factory.addBean("fixture",dataSource);return factory.getBeanProvider(DataSource.class);
    }
}