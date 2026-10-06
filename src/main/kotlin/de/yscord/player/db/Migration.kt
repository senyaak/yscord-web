package de.yscord.player.db

/**
 * One schema change — the Knex `up()` / `down()` model. Each migration is a Spring
 * @Component; [MigrationRunner] discovers them, orders by [version], and runs the
 * returned SQL. Both functions are called inside an Exposed transaction, so they
 * can let Exposed generate statements (e.g. `table.createStatement()`).
 *
 * Rules:
 * 1. A migration is frozen once applied anywhere. Never edit it; add a new one.
 *    The runner hashes [up] and warns when an applied migration changed.
 * 2. Never reference the live table objects in [PlayerTables]: they move on with
 *    the code. Declare a private snapshot of the table inside the migration.
 * 3. [down] is for local development only. Production only goes forward: a
 *    rollback is a new migration.
 */
interface Migration {
    /** Ordering + identity key, e.g. "001_create_player_schema". Sorts lexically. */
    val version: String
    fun up(): List<String>
    fun down(): List<String>
}
