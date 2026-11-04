package me.santiagoreina.parcheu.api.demo

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Cómo decide un usuario demo tras una simulación (ADR-0027): dice "Conectar" con probabilidad
 * [probability]. La decisión es **determinista por simulación** (SHA-256 del id → número en [0, 1)), así que
 * repetir la consulta o reintentar la decisión del tester nunca cambia lo que "decidió" el demo, y las pruebas
 * son reproducibles. El demo decide justo antes que el tester (ver `ReportService.decide`).
 */
class DemoConnectPolicy(val probability: Double) {
    init {
        require(probability in 0.0..1.0) { "La probabilidad debe estar entre 0 y 1" }
    }

    fun wantsToConnect(simulationId: UUID): Boolean = bucket(simulationId) < probability

    companion object {
        /** Valor por defecto del contenedor y de las pruebas en memoria. */
        val DEFAULT = DemoConnectPolicy(DemoSettings.DEFAULT_CONNECT_PROBABILITY)

        /** Número uniforme en [0, 1) derivado del id de la simulación. */
        fun bucket(simulationId: UUID): Double {
            val bytes = ByteBuffer.allocate(16).putLong(simulationId.mostSignificantBits).putLong(simulationId.leastSignificantBits).array()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val bits = ByteBuffer.wrap(digest).long ushr 11 // 53 bits
            return bits.toDouble() / (1L shl 53).toDouble()
        }
    }
}
