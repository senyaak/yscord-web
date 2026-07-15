package de.yscord.player.db

/**
 * One reversible schema change — the Knex `up()` / `down()` model. Each migration
 * is a Spring @Component; [MigrationRunner] discovers them, orders by [version],
 * and applies/reverts inside a transaction. Bodies run in an Exposed transaction,
 * so they can call SchemaUtils / DSL directly.
 */
interface Migration {
    /** Ordering + identity key, e.g. "001_create_player_schema". Sorts lexically. */
    val version: String
    fun up()
    fun down()
}
