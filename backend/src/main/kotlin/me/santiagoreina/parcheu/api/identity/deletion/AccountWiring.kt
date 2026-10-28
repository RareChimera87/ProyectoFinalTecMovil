package me.santiagoreina.parcheu.api.identity.deletion

import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.export.AccountExportService
import me.santiagoreina.parcheu.api.identity.export.ExportRepository
import me.santiagoreina.parcheu.api.identity.export.ExportRows
import me.santiagoreina.parcheu.api.identity.export.ExposedExportRepository
import me.santiagoreina.parcheu.api.identity.retention.ExposedRetentionRepository
import me.santiagoreina.parcheu.api.identity.retention.FirestoreRetentionMirror
import me.santiagoreina.parcheu.api.identity.retention.NoopRetentionMirror
import me.santiagoreina.parcheu.api.identity.retention.NoopRetentionRepository
import me.santiagoreina.parcheu.api.identity.retention.RetentionMirror
import me.santiagoreina.parcheu.api.identity.retention.RetentionRepository
import me.santiagoreina.parcheu.api.identity.retention.RetentionService
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.twin.TwinModule
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/**
 * Borrado (`DELETE /v1/me` y `purge-user`) y exportación (`GET /v1/me/export`) de la cuenta (E8.3) y
 * retención de datos (job diario `retention`, E8.8).
 */
class AccountModule(val deletion: AccountDeletionService, val export: AccountExportService, val retention: RetentionService)

/**
 * Cableado real: Postgres, Cloud Tasks (cola `maintenance`), Firestore y Firebase Auth. Con
 * `ENV=local` (o sin proyecto de Firebase) Firestore y Auth son Fakes.
 */
fun accountModule(
    env: Env,
    database: Database,
    identity: IdentityService,
    graph: GraphModule,
    twin: TwinModule,
    tasks: TaskQueue,
    firebaseProjectId: String?,
    clock: Clock,
    closedMatches: ClosedMatchesListener = ClosedMatchesListener {},
): AccountModule {
    val cloud = env != Env.LOCAL && firebaseProjectId != null
    val mirror = if (cloud) FirestoreAccountMirrorCleaner.forProject(requireNotNull(firebaseProjectId)) else NoopAccountMirrorCleaner
    val auth = if (cloud) FirebaseAuthAccountRemover(requireNotNull(firebaseProjectId)) else FakeAuthAccountRemover()
    val deletion = AccountDeletionService(identity.repo, ExposedAccountPurgeRepository(database), mirror, auth, tasks, clock, closedMatches)
    val retentionMirror = if (cloud) FirestoreRetentionMirror.forProject(requireNotNull(firebaseProjectId)) else NoopRetentionMirror
    return AccountModule(
        deletion = deletion,
        export = AccountExportService(identity, ExposedExportRepository(database), graph.anonIds, twin.repository::state, clock),
        retention = RetentionService(ExposedRetentionRepository(database), retentionMirror, deletion::sweepStalled, clock),
    )
}

/** En memoria: pruebas de rutas. La exportación sale vacía salvo que la prueba pase su [exportRepository]. */
fun inMemoryAccountModule(
    identity: IdentityService,
    graph: GraphModule,
    twin: TwinModule,
    clock: Clock,
    tasks: TaskQueue = FakeTaskQueue(),
    purge: AccountPurgeRepository = InMemoryAccountPurgeRepository(),
    mirror: AccountMirrorCleaner = NoopAccountMirrorCleaner,
    auth: AuthAccountRemover = FakeAuthAccountRemover(),
    closedMatches: ClosedMatchesListener = ClosedMatchesListener {},
    retentionRepository: RetentionRepository = NoopRetentionRepository,
    retentionMirror: RetentionMirror = NoopRetentionMirror,
    exportRepository: ExportRepository = ExportRepository { ExportRows() },
): AccountModule {
    val deletion = AccountDeletionService(identity.repo, purge, mirror, auth, tasks, clock, closedMatches)
    return AccountModule(
        deletion = deletion,
        export = AccountExportService(identity, exportRepository, graph.anonIds, twin.repository::state, clock),
        retention = RetentionService(retentionRepository, retentionMirror, deletion::sweepStalled, clock),
    )
}
