package me.santiagoreina.parcheu.data.account

import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Streaming

/** Borrado y exportación de la cuenta en la API v1 (arquitectura.md §5.2, E8.3). */
interface AccountApi {
    /** Borrado lógico inmediato; la purga corre después en el servidor (202). */
    @DELETE("v1/me")
    suspend fun deleteMe()

    /**
     * `ExportDto` tal como lo manda el servidor: se guarda sin reinterpretarlo, así el archivo de la
     * persona es exactamente la respuesta de la API.
     */
    @Streaming
    @GET("v1/me/export")
    suspend fun export(): ResponseBody
}
