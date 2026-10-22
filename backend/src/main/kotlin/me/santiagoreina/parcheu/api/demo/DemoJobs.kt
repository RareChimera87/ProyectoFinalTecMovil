package me.santiagoreina.parcheu.api.demo

import org.slf4j.LoggerFactory

/**
 * Modos `seed-demo` y `purge-demo` (Cloud Run Jobs, misma imagen). Son stubs de E0.3: el
 * contenido real llega con los usuarios semilla (E4.9) y la purga de demos (E10.9).
 */
object DemoJobs {
    private val log = LoggerFactory.getLogger(javaClass)

    fun seed() = log.warn("seed-demo aún no está implementado (E4.9); no se hizo nada")

    fun purge() = log.warn("purge-demo aún no está implementado (E10.9); no se hizo nada")
}
