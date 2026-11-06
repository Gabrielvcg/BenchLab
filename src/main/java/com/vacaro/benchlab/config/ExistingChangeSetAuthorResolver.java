package com.vacaro.benchlab.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

/** Preserves already-recorded changeset identities without altering migration history. */
public final class ExistingChangeSetAuthorResolver {

    private static final String DEFAULT_AUTHOR = "maintainers";
    private static final List<Identity> IDENTITIES = List.of(
        new Identity("20260531020000-1", "config/liquibase/changelog/20260531020000_benchmark_core.xml", "benchmarkCoreAuthor"),
        new Identity("20260810010000-1", "config/liquibase/changelog/20260810010000_reliability_demo.xml", "reliabilityDemoAuthor"),
        new Identity("20260828010000-1", "config/liquibase/changelog/20260828010000_execution_wall_time.xml", "executionWallTimeAuthor")
    );

    private record Identity(String id, String file, String parameter) {}

    private ExistingChangeSetAuthorResolver() {}

    public static Map<String, String> resolve(
        DataSource dataSource,
        String liquibaseSchema,
        String defaultSchema,
        String changeLogTable
    ) {
        if (dataSource == null) {
            throw new IllegalStateException("A migration datasource is required to preserve existing changeset identities");
        }
        Map<String, String> authors = new LinkedHashMap<>();
        IDENTITIES.forEach(identity -> authors.put(identity.parameter(), DEFAULT_AUTHOR));
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!"PostgreSQL".equals(metadata.getDatabaseProductName())) {
                throw new IllegalStateException("Changeset identity compatibility is currently verified for PostgreSQL only");
            }
            String configuredSchema = firstNonBlank(liquibaseSchema, defaultSchema);
            String schema = configuredSchema == null ? connection.getSchema() : normalizeIdentifier(configuredSchema, metadata);
            if (schema == null || schema.isBlank()) {
                throw new IllegalStateException("Cannot determine the migration metadata schema");
            }
            String table = normalizeIdentifier(firstNonBlank(changeLogTable, "DATABASECHANGELOG"), metadata);
            if (!metadataTableExists(connection, metadata, schema, table)) {
                return Map.copyOf(authors);
            }
            String sql = "SELECT ID, AUTHOR, FILENAME FROM " + quote(metadata, schema) + "." + quote(metadata, table) +
                " WHERE ID IN (?, ?, ?)";
            Set<String> seen = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < IDENTITIES.size(); index++) {
                    statement.setString(index + 1, IDENTITIES.get(index).id());
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String id = rows.getString("ID");
                        Identity identity = IDENTITIES.stream().filter(value -> value.id().equals(id)).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Unexpected changeset identity returned by migration metadata query"));
                        if (!seen.add(id)) {
                            throw new IllegalStateException("Ambiguous migration metadata for changeset " + id);
                        }
                        if (!identity.file().equals(rows.getString("FILENAME"))) {
                            throw new IllegalStateException("Unexpected recorded filename for changeset " + id);
                        }
                        String author = rows.getString("AUTHOR");
                        if (author == null || author.isBlank()) {
                            throw new IllegalStateException("Missing recorded author for changeset " + id);
                        }
                        authors.put(identity.parameter(), author);
                    }
                }
            }
            return Map.copyOf(authors);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read existing migration identities; refusing to guess an applied author", exception);
        }
    }

    private static boolean metadataTableExists(Connection connection, DatabaseMetaData metadata, String schema, String table)
        throws SQLException {
        String escape = metadata.getSearchStringEscape();
        int matches = 0;
        try (ResultSet tables = metadata.getTables(
            connection.getCatalog(), escapePattern(schema, escape), escapePattern(table, escape), new String[] { "TABLE" }
        )) {
            while (tables.next()) {
                if (schema.equals(tables.getString("TABLE_SCHEM")) && table.equals(tables.getString("TABLE_NAME"))) {
                    matches++;
                }
            }
        }
        if (matches > 1) {
            throw new IllegalStateException("Ambiguous migration metadata table");
        }
        return matches == 1;
    }

    private static String escapePattern(String value, String escape) {
        if (escape == null || escape.isEmpty()) {
            if (value.contains("%") || value.contains("_")) {
                throw new IllegalStateException("Cannot safely match migration metadata identifiers");
            }
            return value;
        }
        return value.replace(escape, escape + escape).replace("%", escape + "%").replace("_", escape + "_");
    }

    private static String normalizeIdentifier(String identifier, DatabaseMetaData metadata) throws SQLException {
        if (identifier.startsWith("\"") && identifier.endsWith("\"") && identifier.length() > 1) {
            return identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
        }
        if (metadata.storesLowerCaseIdentifiers()) return identifier.toLowerCase(Locale.ROOT);
        if (metadata.storesUpperCaseIdentifiers()) return identifier.toUpperCase(Locale.ROOT);
        return identifier;
    }

    private static String quote(DatabaseMetaData metadata, String identifier) throws SQLException {
        String quote = metadata.getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            throw new IllegalStateException("The database does not provide identifier quoting");
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}