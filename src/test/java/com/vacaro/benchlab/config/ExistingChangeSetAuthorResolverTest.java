package com.vacaro.benchlab.config;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ExistingChangeSetAuthorResolverTest {
    private static final String CORE = "20260531020000-1";
    private static final String CORE_FILE = "config/liquibase/changelog/20260531020000_benchmark_core.xml";
    private static final String RELIABILITY = "20260810010000-1";
    private static final String RELIABILITY_FILE = "config/liquibase/changelog/20260810010000_reliability_demo.xml";
    private static final String EXECUTION = "20260828010000-1";
    private static final String EXECUTION_FILE = "config/liquibase/changelog/20260828010000_execution_wall_time.xml";

    @Test void newDatabaseUsesDefaultsWithoutQueryingMissingMetadataTable() {
        Fixture fixture = new Fixture(); fixture.tableExists = false;
        Map<String,String> result = resolve(fixture);
        assertEquals(Map.of("benchmarkCoreAuthor","maintainers","reliabilityDemoAuthor","maintainers","executionWallTimeAuthor","maintainers"),result);
        assertNull(fixture.sql); assertTrue(fixture.closed);
    }
    @Test void existingAuthorsArePreservedVerbatim() {
        Fixture fixture = new Fixture();
        fixture.rows.add(row(CORE,"previous.owner",CORE_FILE));
        fixture.rows.add(row(RELIABILITY,"team-a",RELIABILITY_FILE));
        fixture.rows.add(row(EXECUTION,"maintainers",EXECUTION_FILE));
        Map<String,String> result = resolve(fixture);
        assertEquals("previous.owner",result.get("benchmarkCoreAuthor"));
        assertEquals("team-a",result.get("reliabilityDemoAuthor"));
        assertEquals("maintainers",result.get("executionWallTimeAuthor"));
        assertEquals("SELECT ID, AUTHOR, FILENAME FROM \"public\".\"databasechangelog\" WHERE ID IN (?, ?, ?)",fixture.sql);
        assertEquals(List.of(CORE,RELIABILITY,EXECUTION),new ArrayList<>(fixture.bindings.values()));
        assertTrue(fixture.closed);
    }
    @Test void partiallyMigratedDatabasePreservesOnlyExistingIdentities() {
        Fixture fixture = new Fixture(); fixture.rows.add(row(CORE,"previous.owner",CORE_FILE));
        Map<String,String> result = resolve(fixture);
        assertEquals("previous.owner",result.get("benchmarkCoreAuthor"));
        assertEquals("maintainers",result.get("reliabilityDemoAuthor"));
        assertEquals("maintainers",result.get("executionWallTimeAuthor"));
    }
    @Test void duplicateIdentityFailsBeforeMigrationCanStart() {
        Fixture fixture = new Fixture(); fixture.rows.add(row(CORE,"a",CORE_FILE));fixture.rows.add(row(CORE,"b",CORE_FILE));
        assertThrows(IllegalStateException.class,()->resolve(fixture)); assertTrue(fixture.closed);
    }
    @Test void unexpectedRecordedPathFailsRatherThanGuessingAnAuthor() {
        Fixture fixture = new Fixture();fixture.rows.add(row(CORE,"previous.owner","different/path.xml"));
        assertThrows(IllegalStateException.class,()->resolve(fixture));
    }
    @Test void blankRecordedAuthorFailsRatherThanDefaultingAnAppliedIdentity() {
        Fixture fixture = new Fixture();fixture.rows.add(row(CORE," ",CORE_FILE));
        assertThrows(IllegalStateException.class,()->resolve(fixture));
    }
    @Test void configuredSchemaAndTableAreQuotedAndMatchedWithoutWildcardExpansion() {
        Fixture fixture = new Fixture();fixture.schema="Audit_Schema";fixture.table="History_Table";
        fixture.rows.add(row(CORE,"previous.owner",CORE_FILE));
        Map<String,String> result = ExistingChangeSetAuthorResolver.resolve(fixture.dataSource(),"\"Audit_Schema\"","ignored","\"History_Table\"");
        assertEquals("previous.owner",result.get("benchmarkCoreAuthor"));
        assertTrue(fixture.sql.contains("\"Audit_Schema\".\"History_Table\""));
        assertEquals("Audit\\_Schema",fixture.schemaPattern);assertEquals("History\\_Table",fixture.tablePattern);
    }
    @Test void connectionFailureDoesNotFallBackToFreshDatabaseDefaults() {
        Fixture fixture = new Fixture();fixture.connectionFailure=true;
        assertThrows(IllegalStateException.class,()->resolve(fixture));
    }

    private static Map<String,String> resolve(Fixture fixture) {
        return ExistingChangeSetAuthorResolver.resolve(fixture.dataSource(),null,null,null);
    }
    private static Map<String,String> row(String id,String author,String filename) {
        return Map.of("ID",id,"AUTHOR",author,"FILENAME",filename);
    }
    @FunctionalInterface private interface Calls { Object invoke(String method,Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,Calls calls) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(instance,method,args)->{
            if(method.getName().equals("toString"))return "JdbcFixture";
            if(method.getName().equals("hashCode"))return System.identityHashCode(instance);
            if(method.getName().equals("equals"))return instance==args[0];
            return calls.invoke(method.getName(),args==null?new Object[0]:args);
        });
    }
    private static ResultSet resultSet(List<Map<String,String>> rows) {
        int[] index={-1};return proxy(ResultSet.class,(method,args)->switch(method){
            case "next" -> ++index[0]<rows.size();
            case "getString" -> rows.get(index[0]).get((String)args[0]);
            case "close" -> null;
            default -> throw new AssertionError("Unexpected result-set operation: "+method);
        });
    }
    static final class Fixture {
        boolean tableExists=true,connectionFailure=false,closed=false;
        String schema="public",table="databasechangelog",sql,schemaPattern,tablePattern;
        List<Map<String,String>> rows=new ArrayList<>();Map<Integer,String> bindings=new LinkedHashMap<>();
        DataSource dataSource() {
            return proxy(DataSource.class,(method,args)->{
                if(!method.equals("getConnection"))throw new AssertionError("Unexpected datasource operation: "+method);
                if(connectionFailure)throw new SQLException("Disposable fixture connection rejected");
                return connection();
            });
        }
        Connection connection() {
            return proxy(Connection.class,(method,args)->switch(method){
                case "getMetaData" -> metadata();
                case "getSchema" -> schema;
                case "getCatalog" -> "fixture";
                case "close" -> {closed=true;yield null;}
                case "prepareStatement" -> {
                    sql=(String)args[0];assertTrue(sql.startsWith("SELECT "));yield statement();
                }
                default -> throw new AssertionError("Unexpected connection operation: "+method);
            });
        }
        DatabaseMetaData metadata() {
            return proxy(DatabaseMetaData.class,(method,args)->switch(method){
                case "getDatabaseProductName" -> "PostgreSQL";
                case "storesLowerCaseIdentifiers" -> true;
                case "storesUpperCaseIdentifiers" -> false;
                case "getIdentifierQuoteString" -> "\"";
                case "getSearchStringEscape" -> "\\";
                case "getTables" -> {schemaPattern=(String)args[1];tablePattern=(String)args[2];yield resultSet(tableExists?List.of(Map.of("TABLE_SCHEM",schema,"TABLE_NAME",table)):List.of());}
                default -> throw new AssertionError("Unexpected metadata operation: "+method);
            });
        }
        PreparedStatement statement() {
            return proxy(PreparedStatement.class,(method,args)->switch(method){
                case "setString" -> {bindings.put((Integer)args[0],(String)args[1]);yield null;}
                case "executeQuery" -> resultSet(rows);
                case "close" -> null;
                default -> throw new AssertionError("Unexpected statement operation: "+method);
            });
        }
    }
}