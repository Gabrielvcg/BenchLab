# Stable migration identities

Liquibase identifies an applied changeset by its ID, author and recorded filename. BenchLab preserves these identities when an installation has author labels that differ from the source defaults. The three parameterized benchmark changesets retain their IDs, filenames and migration payloads.

| Changeset ID | Managed author parameter |
| --- | --- |
| `20260531020000-1` | `benchmarkCoreAuthor` |
| `20260810010000-1` | `reliabilityDemoAuthor` |
| `20260828010000-1` | `executionWallTimeAuthor` |

The XML defaults are `maintainers`. Before migration initialization, the application reads matching identities from the existing migration metadata table and supplies their recorded authors as changelog parameters. Missing identities retain the defaults and are applied normally. The resolver performs metadata inspection and a parameterized SELECT; it does not update migration history or mark changes as executed.

Duplicate IDs, unexpected recorded filenames, blank authors, conflicting managed parameters and database-read failures stop startup. An unreadable database is not treated as a fresh installation. The managed parameters above should not be overridden in deployment configuration.

The read uses the datasource selected by the existing JHipster Liquibase factory, including a qualified migration datasource or dedicated Liquibase connection settings. The Liquibase metadata schema takes precedence over the default schema; the current connection schema is the fallback. A configured metadata table name is respected. The resolver currently supports PostgreSQL.

## Startup boundary

Identity resolution is a synchronous startup check, including when the subsequent Liquibase update is configured to run asynchronously. It completes before the migration task is scheduled. It is skipped when Liquibase is disabled or the `no-liquibase` profile is active.

Use a single migration owner and a quiescent startup window. Do not start an older migrator concurrently with the compatible version, particularly against a partially migrated database: identity resolution occurs before Liquibase takes its update lock. This mechanism is not a concurrent rolling-upgrade protocol.

No checksum reset or changelog synchronization is required for author resolution. The unchanged migration payloads were checked with Liquibase 4.29.2 checksum versions 8 and 9. Database fixtures verified existing, fresh, partially migrated and ambiguous metadata cases.

Rollback remains subject to normal schema and version compatibility. Preserving already-applied identities allows the original runtime to recognize those same rows, but does not establish rollback safety for migrations newly applied by another version.

## Focused checks

```bash
./mvnw -ntp --batch-mode -Dtest=ExistingChangeSetAuthorResolverTest,LiquibaseConfigurationIdentityTest -DskipITs test
```

The resolver tests check metadata identity handling. Configuration tests cover null default parameters, disabled migrations, datasource precedence, managed-parameter conflicts and preparation of asynchronous migration instances. Separate disposable PostgreSQL verification compared complete metadata rows, checksums, schema shape and business row counts across the compatible update; those validation artifacts are retained privately.