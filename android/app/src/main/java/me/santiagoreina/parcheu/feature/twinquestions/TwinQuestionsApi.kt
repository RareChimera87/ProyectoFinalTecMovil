package me.santiagoreina.parcheu.feature.twinquestions

import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Endpoints del gemelo que usa B1 (arquitectura.md §5). */
interface TwinQuestionsApi {
    @GET("v1/twin/questions")
    suspend fun questions(): List<QuestionDto>

    @POST("v1/twin/answers")
    suspend fun submit(@Body answers: Answers): TwinDto
}
