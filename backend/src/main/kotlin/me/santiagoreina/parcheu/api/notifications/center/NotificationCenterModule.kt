package me.santiagoreina.parcheu.api.notifications.center

import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.simulation.graphPersonPresenter
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Lo que el centro de notificaciones (E5.4) expone al contenedor: la bandeja y su servicio. */
class NotificationCenterModule(val store: NotificationStore, val service: NotificationCenterService)

/** Cableado común: el actor se presenta con la policy del grafo y Plus sale del cupo (ADR-0013). */
fun notificationCenterModule(store: NotificationStore, clock: Clock, quota: QuotaModule, graph: GraphModule): NotificationCenterModule {
    val people = graphPersonPresenter(graph)
    val service = NotificationCenterService(
        store = store,
        actors = { viewer, actor -> people.present(viewer, actor) },
        isPlus = { viewer -> quota.service.status(viewer).plus },
        clock = clock,
    )
    return NotificationCenterModule(store, service)
}

fun inMemoryNotificationCenter(clock: Clock, quota: QuotaModule, graph: GraphModule) =
    notificationCenterModule(InMemoryNotificationStore(), clock, quota, graph)

fun exposedNotificationCenter(db: Database, clock: Clock, quota: QuotaModule, graph: GraphModule) =
    notificationCenterModule(ExposedNotificationStore(db), clock, quota, graph)
