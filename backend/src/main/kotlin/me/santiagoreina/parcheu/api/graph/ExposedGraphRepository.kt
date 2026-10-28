package me.santiagoreina.parcheu.api.graph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.Communities
import me.santiagoreina.parcheu.api.platform.db.tables.CommunityMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.EdgesView
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Grafo sobre Postgres (ADR-0003): lecturas en `edges_v`, escrituras en `edges` con
 * `user_low < user_high`. El filtro de privacidad del ego-grafo va en el WHERE (ADR-0012):
 *
 * ```sql
 * SELECT ... FROM edges_v e JOIN users u ON u.id = e.other LEFT JOIN universities un ON un.id = u.university_id
 * WHERE e.me = :viewer AND u.deleted_at IS NULL
 *   AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :viewer AND b.blocked_id = e.other)
 *                                          OR (b.blocker_id = e.other AND b.blocked_id = :viewer))
 *   [AND e.mode = :mode] [AND e.affinity >= :minAffinity]
 *   [AND e.other IN (SELECT user_id FROM community_members WHERE community_id = :c)
 *    AND EXISTS (SELECT 1 FROM community_members WHERE community_id = :c AND user_id = :viewer)]
 * ```
 */
class ExposedGraphRepository(private val db: Database) : GraphRepository {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun egoEdges(viewer: UUID, filter: GraphFilter): List<EgoEdgeRow> = tx {
        val blocked = Blocks.select(Blocks.blockerId).where {
            ((Blocks.blockerId eq viewer) and (Blocks.blockedId eq EdgesView.other)) or
                ((Blocks.blockerId eq EdgesView.other) and (Blocks.blockedId eq viewer))
        }
        var where: Op<Boolean> = (EdgesView.me eq viewer) and Users.deletedAt.isNull() and notExists(blocked)
        filter.mode?.let { mode -> where = where and (EdgesView.mode eq mode) }
        filter.minAffinity?.let { min -> where = where and (EdgesView.affinity greaterEq min.toShort()) }
        filter.community?.let { community ->
            val members = CommunityMembers.select(CommunityMembers.userId).where { CommunityMembers.communityId eq community }
            val viewerIsMember = CommunityMembers.select(CommunityMembers.userId).where {
                (CommunityMembers.communityId eq community) and (CommunityMembers.userId eq viewer)
            }
            where = where and (EdgesView.other inSubQuery members) and exists(viewerIsMember)
        }
        EdgesView
            .join(Users, JoinType.INNER, EdgesView.other, Users.id)
            .join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
            .selectAll()
            .where(where)
            .map { row ->
                EgoEdgeRow(
                    other = row.toPerson(),
                    mode = row[EdgesView.mode],
                    state = row[EdgesView.state],
                    affinity = row[EdgesView.affinity]?.toInt(),
                    lastSimulationId = row[EdgesView.lastSimulationId],
                    connectedAt = row[EdgesView.connectedAt]?.toInstant(),
                    createdAt = row[EdgesView.createdAt].toInstant(),
                )
            }
    }

    override suspend fun connectedWith(viewer: UUID): Set<UUID> = tx {
        EdgesView.select(EdgesView.other)
            .where { (EdgesView.me eq viewer) and (EdgesView.state eq EdgeState.CONNECTED) }
            .map { it[EdgesView.other] }
            .toSet()
    }

    override suspend fun blockedEitherWay(viewer: UUID): Set<UUID> = tx {
        Blocks.selectAll()
            .where { (Blocks.blockerId eq viewer) or (Blocks.blockedId eq viewer) }
            .map { if (it[Blocks.blockerId] == viewer) it[Blocks.blockedId] else it[Blocks.blockerId] }
            .toSet()
    }

    override suspend fun person(userId: UUID): PersonRow? = tx {
        Users.join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
            .selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.toPerson()
    }

    override suspend fun firstCommunityAt(userId: UUID): Instant? = tx {
        CommunityMembers.join(Communities, JoinType.INNER, CommunityMembers.communityId, Communities.id)
            .select(Communities.createdAt)
            .where { CommunityMembers.userId eq userId }
            .orderBy(Communities.createdAt, SortOrder.ASC)
            .limit(1)
            .singleOrNull()
            ?.get(Communities.createdAt)
            ?.toInstant()
    }

    override suspend fun lastSimulation(a: UUID, b: UUID): SimulationRow? = tx {
        Simulations
            .select(Simulations.id, Simulations.status, Simulations.mode, Simulations.origin, Simulations.score, Simulations.createdAt)
            .where {
                (((Simulations.userA eq a) and (Simulations.userB eq b)) or ((Simulations.userA eq b) and (Simulations.userB eq a))) and
                    (Simulations.origin neq SimulationOrigin.INTRODUCTION)
            }
            .orderBy(Simulations.createdAt to SortOrder.DESC, Simulations.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let {
                SimulationRow(
                    id = it[Simulations.id],
                    status = it[Simulations.status],
                    mode = it[Simulations.mode],
                    origin = it[Simulations.origin],
                    score = it[Simulations.score]?.toInt(),
                    createdAt = it[Simulations.createdAt].toInstant(),
                )
            }
    }

    override suspend fun updateEdge(
        a: UUID,
        b: UUID,
        mode: Mode,
        now: Instant,
        change: (EdgeSnapshot?) -> EdgeChange,
    ): EdgeSnapshot? = tx {
        val blocked = Blocks.selectAll().where {
            ((Blocks.blockerId eq a) and (Blocks.blockedId eq b)) or ((Blocks.blockerId eq b) and (Blocks.blockedId eq a))
        }.any()
        if (blocked) return@tx null
        val (low, high) = Edges.orderedPair(a, b)
        val key: Op<Boolean> = (Edges.userLow eq low) and (Edges.userHigh eq high) and (Edges.mode eq mode)

        fun current(): EdgeSnapshot? = Edges.selectAll().where(key).forUpdate().singleOrNull()?.toSnapshot()

        var existing = current()
        var decision = change(existing)
        if (existing == null && decision is EdgeChange.Save) {
            val edge = decision.edge
            val inserted = Edges.insertIgnore {
                it[userLow] = low
                it[userHigh] = high
                it[Edges.mode] = mode
                it[state] = edge.state
                it[affinity] = edge.affinity?.toShort()
                it[lastSimulationId] = edge.lastSimulationId
                it[createdAt] = edge.createdAt.odt()
                it[updatedAt] = now.odt()
                it[connectedAt] = edge.connectedAt?.odt()
            }.insertedCount
            if (inserted == 1) return@tx edge
            // Otra transacción la creó a la vez: se relee bloqueada y se recalcula sobre ella.
            existing = current()
            decision = change(existing)
        }
        when (decision) {
            EdgeChange.Keep -> existing
            EdgeChange.Remove -> {
                Edges.deleteWhere { key }
                null
            }
            is EdgeChange.Save -> {
                val edge = decision.edge
                Edges.update({ key }) {
                    it[state] = edge.state
                    it[affinity] = edge.affinity?.toShort()
                    it[lastSimulationId] = edge.lastSimulationId
                    it[updatedAt] = now.odt()
                    it[connectedAt] = edge.connectedAt?.odt()
                }
                edge
            }
        }
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun ResultRow.toPerson() = PersonRow(
    id = this[Users.id],
    displayName = this[Users.displayName],
    photoUrl = this[Users.photoUrl],
    universityShort = this.getOrNull(Universities.shortName),
    visibility = this[Users.visibility],
    isDemo = this[Users.isDemo],
    deletedAt = this[Users.deletedAt]?.toInstant(),
)

private fun ResultRow.toSnapshot() = EdgeSnapshot(
    state = this[Edges.state],
    affinity = this[Edges.affinity]?.toInt(),
    lastSimulationId = this[Edges.lastSimulationId],
    createdAt = this[Edges.createdAt].toInstant(),
    connectedAt = this[Edges.connectedAt]?.toInstant(),
)

