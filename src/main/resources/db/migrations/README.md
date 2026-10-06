# Migrations

Plain SQL, applied in file-name order by `MigrationRunner`. History lives in the
`schema_migrations` table.

1. `NNN_name.sql` is the migration; optional `NNN_name.down.sql` reverts it.
   Down files are for local development only (`--db.rollback=N`). Production
   only goes forward: a rollback is a new migration.
2. Never edit a migration once it was applied anywhere. The runner stores a
   SHA-256 of each file and warns when an applied one changed.
3. Schema changes are generated, then reviewed: change `PlayerTables.kt`, run
   `./gradlew generateMigration --name what_changed`, read the draft and fix
   intent the generator can't see (a rename looks like DROP + ADD and loses
   data; type changes may need `USING`; a NOT NULL column on a filled table
   needs a default or a backfill). Data moves are written by hand.
4. `SchemaInSyncTest` fails when `PlayerTables.kt` and the migrations disagree.
5. `MigrationsOnDataTest` applies the migrations one by one and loads
   `src/test/resources/db/seeds/<version>.sql` right after the migration that
   created those tables, so every later migration runs against data, as in
   production. A migration that creates a table gets a seed; a seed is written
   once, in the schema of its migration, and never updated.
