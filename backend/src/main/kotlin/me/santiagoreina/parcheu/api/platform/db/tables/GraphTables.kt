package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.util.UUID

/* Grafo (ADR-0003): aristas con `user_low < user_high`, vista en ambos sentidos y comunidades. */

object Edges : Table("edges") {
    val userLow = userRef("user_low", ReferenceOption.CASCADE)
    val userHigh = userRef("user_high", ReferenceOption.CASCADE)
    val mode = enumText<Mode>("mode")
    val state = enumText<EdgeState>("state")
    val affinity = short("affinity").nullable()
    val lastSimulationId = javaUUID("last_simulation_id").references(Simulations.id).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val connectedAt = timestampWithTimeZone("connected_at").nullable()
    override val primaryKey = PrimaryKey(userLow, userHigh, mode)

    /**
     * Ordena un par para cumplir el CHECK `user_low < user_high`. Compara como lo hace Postgres
     * (bytes sin signo), no con `UUID.compareTo`, que compara con signo.
     */
    fun orderedPair(a: UUID, b: UUID): Pair<UUID, UUID> {
        require(a != b) { "Una arista necesita dos usuarios distintos" }
        return if (compareUnsigned(a, b) < 0) a to b else b to a
    }

    internal fun compareUnsigned(a: UUID, b: UUID): Int {
        val high = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
        return if (high != 0) high else java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
    }
}

/** Vista `edges_v`: cada arista aparece dos veces, con `me` = cada extremo. Solo lectura. */
object EdgesView : Table("edges_v") {
    val me = javaUUID("me")
    val other = javaUUID("other")
    val mode = enumText<Mode>("mode")
    val state = enumText<EdgeState>("state")
    val affinity = short("affinity").nullable()
    val lastSimulationId = javaUUID("last_simulation_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val connectedAt = timestampWithTimeZone("connected_at").nullable()
}

object Communities : Table("communities") {
    val id = uuidPk()
    val week = date("week")
    val name = text("name")
    val stableKey = text("stable_key")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CommunityMembers : Table("community_members") {
    val communityId = javaUUID("community_id").references(Communities.id, onDelete = ReferenceOption.CASCADE)
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(communityId, userId)
}
