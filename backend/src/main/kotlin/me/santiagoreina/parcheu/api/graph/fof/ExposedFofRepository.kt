package me.santiagoreina.parcheu.api.graph.fof

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

/**
 * Amigos de amigos sobre Postgres: la consulta de 2 saltos de arquitectura.md §6.2 en una sola
 * sentencia (ADR-0012: los filtros de Pareja, HIDDEN, borrados y bloqueos van en el WHERE).
 *
 * Sobre la consulta del documento agrega, para que coincida con lo que el ego-grafo muestra:
 * - `mine` sin duplicados y solo con amigos visibles en segundo grado (no borrados, no HIDDEN, sin
 *   bloqueo con el observador): un amigo HIDDEN no debe producir sugerencias ni contarse;
 * - sin bloqueo entre el amigo en común y el candidato;
 * - las aristas que conectan (`links`) para la segunda barrera de [FofService] y los rasgos del
 *   candidato para la afinidad estimada (ADR-0023).
 */
class ExposedFofRepository(private val db: Database) : FofRepository {

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun candidates(viewer: UUID, limit: Int): List<FofRow> = tx {
        val sql = connection.connection as Connection
        sql.prepareStatement(CANDIDATES_SQL).use { st ->
            st.setObject(1, viewer)
            st.setObject(2, viewer)
            st.setObject(3, viewer)
            st.setObject(4, viewer)
            st.setObject(5, viewer)
            st.setObject(6, viewer)
            st.setObject(7, viewer)
            st.setInt(8, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRow()) } }
        }
    }

    override suspend fun traits(userId: UUID): List<Double>? = tx {
        val sql = connection.connection as Connection
        sql.prepareStatement("select traits from twins where user_id = ?").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> if (rs.next()) rs.traits() else null }
        }
    }

    companion object {
        /** Parámetros: 1-3 observador (mine), 4 observador (candidato <> observador), 5 aristas, 6-7 bloqueos, 8 límite. */
        internal val CANDIDATES_SQL = """
            with mine as (
              select distinct m.other
              from edges_v m
              join users mu on mu.id = m.other
              where m.me = ? and m.state = 'CONNECTED' and m.mode <> 'PAREJA'
                and mu.deleted_at is null and mu.visibility <> 'HIDDEN'
                and not exists (select 1 from blocks b
                                where (b.blocker_id = ? and b.blocked_id = m.other)
                                   or (b.blocker_id = m.other and b.blocked_id = ?))
            )
            select c.id, c.display_name, c.photo_url, un.short_name, c.visibility, c.is_demo, t.traits,
                   count(distinct e.me) as mutuals,
                   array_agg(distinct e.me::text || ':' || e.mode) as links
            from edges_v e
            join mine m on e.me = m.other
            join users c on c.id = e.other
            left join universities un on un.id = c.university_id
            left join twins t on t.user_id = c.id
            where e.state = 'CONNECTED' and e.mode <> 'PAREJA' and e.other <> ?
              and c.deleted_at is null and c.visibility <> 'HIDDEN'
              and not exists (select 1 from edges_v d where d.me = ? and d.other = e.other)
              and not exists (select 1 from blocks b
                              where (b.blocker_id = ? and b.blocked_id = e.other)
                                 or (b.blocker_id = e.other and b.blocked_id = ?))
              and not exists (select 1 from blocks b
                              where (b.blocker_id = e.me and b.blocked_id = e.other)
                                 or (b.blocker_id = e.other and b.blocked_id = e.me))
            group by c.id, un.short_name, t.traits
            order by mutuals desc, c.id
            limit ?
        """.trimIndent()
    }
}

private fun ResultSet.toRow(): FofRow {
    val id = getObject("id", UUID::class.java)
    @Suppress("UNCHECKED_CAST")
    val links = (getArray("links").array as Array<Any?>).mapNotNull { raw ->
        val text = raw as? String ?: return@mapNotNull null
        val (mutual, mode) = text.split(':', limit = 2)
        FofLink(UUID.fromString(mutual), Mode.valueOf(mode))
    }
    return FofRow(
        person = PersonRow(
            id = id,
            displayName = getString("display_name"),
            photoUrl = getString("photo_url"),
            universityShort = getString("short_name"),
            visibility = Visibility.valueOf(getString("visibility")),
            isDemo = getBoolean("is_demo"),
            deletedAt = null,
        ),
        mutuals = getInt("mutuals"),
        links = links,
        traits = traits(),
    )
}

private fun ResultSet.traits(): List<Double>? {
    val array = getArray("traits") ?: return null
    return (array.array as Array<*>).map { (it as Number).toDouble() }
}
