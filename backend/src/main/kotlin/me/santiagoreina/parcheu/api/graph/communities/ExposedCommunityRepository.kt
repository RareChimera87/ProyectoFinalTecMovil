package me.santiagoreina.parcheu.api.graph.communities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.platform.db.tables.Communities
import me.santiagoreina.parcheu.api.platform.db.tables.CommunityMembers
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Comunidades sobre Postgres. Las aristas del job salen de una sola consulta que ya aplica los filtros de
 * ADR-0012 (nada de Pareja, HIDDEN, borrados ni bloqueos entre los dos extremos):
 *
 * ```sql
 * SELECT ... FROM edges e JOIN users a ON a.id = e.user_low JOIN users b ON b.id = e.user_high
 * WHERE e.state = 'CONNECTED' AND e.mode <> 'PAREJA'
 *   AND a.deleted_at IS NULL AND b.deleted_at IS NULL AND a.visibility <> 'HIDDEN' AND b.visibility <> 'HIDDEN'
 *   AND NOT EXISTS (bloqueo en cualquier sentido)
 * ```
 *
 * Una comunidad con id estable se actualiza (semana y miembros) en lugar de reinsertarse, así que la
 * tabla guarda la última corrida de cada comunidad viva.
 */
class ExposedCommunityRepository(private val db: Database) : CommunityRepository {

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun connectedEdges(): List<CommunityEdge> = tx {
        val sql = connection.connection as Connection
        sql.prepareStatement(EDGES_SQL).use { st ->
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toEdge()) } }
        }
    }

    override suspend fun all(): List<StoredCommunity> = tx { load(Communities.selectAll().toList().map { it[Communities.id] }) }

    override suspend fun find(id: UUID): StoredCommunity? = tx { load(listOf(id)).singleOrNull() }

    override suspend fun ofUser(userId: UUID): List<StoredCommunity> = tx {
        val ids = CommunityMembers.selectAll().where { CommunityMembers.userId eq userId }.map { it[CommunityMembers.communityId] }
        load(ids)
    }

    override suspend fun apply(week: LocalDate, plan: CommunityPlan) = tx {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (plan.removed.isNotEmpty()) Communities.deleteWhere { Communities.id inList plan.removed }
        for (community in plan.kept) {
            Communities.update({ Communities.id eq community.id }) { it[Communities.week] = week }
            CommunityMembers.deleteWhere { communityId eq community.id }
            insertMembers(community)
        }
        for (community in plan.created) {
            Communities.insert {
                it[id] = community.id
                it[Communities.week] = week
                it[name] = community.name
                it[stableKey] = community.stableKey
                it[createdAt] = now
            }
            insertMembers(community)
        }
    }

    override suspend fun rename(id: UUID, name: String) = tx {
        Communities.update({ Communities.id eq id }) { it[Communities.name] = name }
        Unit
    }

    override suspend fun traitsOf(users: Collection<UUID>): List<List<Double>> = tx {
        if (users.isEmpty()) return@tx emptyList()
        val sql = connection.connection as Connection
        sql.prepareStatement("select traits from twins where traits is not null and user_id = any (?)").use { st ->
            st.setArray(1, sql.createArrayOf("uuid", users.toTypedArray()))
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add((rs.getArray("traits").array as Array<*>).map { (it as Number).toDouble() }) }
            }
        }
    }

    private fun insertMembers(community: StoredCommunity) {
        CommunityMembers.batchInsert(community.members.sorted()) { member ->
            this[CommunityMembers.communityId] = community.id
            this[CommunityMembers.userId] = member
        }
    }

    private fun load(ids: List<UUID>): List<StoredCommunity> {
        if (ids.isEmpty()) return emptyList()
        val membersBy = CommunityMembers.selectAll().where { CommunityMembers.communityId inList ids }
            .groupBy({ it[CommunityMembers.communityId] }, { it[CommunityMembers.userId] })
        return Communities.selectAll().where { Communities.id inList ids }.map { row ->
            StoredCommunity(
                id = row[Communities.id],
                week = row[Communities.week],
                name = row[Communities.name],
                stableKey = row[Communities.stableKey],
                members = membersBy[row[Communities.id]].orEmpty().toSet(),
            )
        }.sortedBy { it.id }
    }

    companion object {
        internal val EDGES_SQL = """
            select e.mode, e.affinity,
                   a.id as a_id, a.display_name as a_name, a.photo_url as a_photo, ua.short_name as a_uni, a.visibility as a_vis, a.is_demo as a_demo,
                   b.id as b_id, b.display_name as b_name, b.photo_url as b_photo, ub.short_name as b_uni, b.visibility as b_vis, b.is_demo as b_demo
            from edges e
            join users a on a.id = e.user_low
            join users b on b.id = e.user_high
            left join universities ua on ua.id = a.university_id
            left join universities ub on ub.id = b.university_id
            where e.state = 'CONNECTED' and e.mode <> 'PAREJA'
              and a.deleted_at is null and b.deleted_at is null
              and a.visibility <> 'HIDDEN' and b.visibility <> 'HIDDEN'
              and not exists (select 1 from blocks k
                              where (k.blocker_id = e.user_low and k.blocked_id = e.user_high)
                                 or (k.blocker_id = e.user_high and k.blocked_id = e.user_low))
            order by e.user_low, e.user_high, e.mode
        """.trimIndent()
    }
}

private fun ResultSet.toEdge(): CommunityEdge {
    fun person(prefix: String) = PersonRow(
        id = getObject("${prefix}_id", UUID::class.java),
        displayName = getString("${prefix}_name"),
        photoUrl = getString("${prefix}_photo"),
        universityShort = getString("${prefix}_uni"),
        visibility = Visibility.valueOf(getString("${prefix}_vis")),
        isDemo = getBoolean("${prefix}_demo"),
        deletedAt = null,
    )
    val affinity = getInt("affinity").takeUnless { wasNull() }
    return CommunityEdge(person("a"), person("b"), Mode.valueOf(getString("mode")), affinity)
}
