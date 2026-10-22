package me.santiagoreina.parcheu.contract

import kotlinx.serialization.KSerializer

/**
 * Registro de todos los DTOs del contrato con su ejemplo canónico en
 * `src/test/resources/examples/<Nombre>.json`. Al agregar un DTO hay que registrarlo aquí y
 * crear su ejemplo; [CoverageTest] falla si falta alguno.
 */
object Examples {
    val serializers: Map<String, KSerializer<*>> = linkedMapOf(
        // Common
        "ApiError" to ApiError.serializer(),
        "Page" to Page.serializer(SimulationSummary.serializer()),
        "PersonRef" to PersonRef.serializer(),
        "Health" to Health.serializer(),
        "InternalTask" to InternalTask.serializer(),
        // Session
        "MeDto" to MeDto.serializer(),
        "SessionDto" to SessionDto.serializer(),
        "AcceptTerms" to AcceptTerms.serializer(),
        "ConsentState" to ConsentState.serializer(),
        "ConsentsDto" to ConsentsDto.serializer(),
        "ConsentChange" to ConsentChange.serializer(),
        "University" to University.serializer(),
        "Interest" to Interest.serializer(),
        "CatalogDto" to CatalogDto.serializer(),
        "UpdateMe" to UpdateMe.serializer(),
        "UpdateModes" to UpdateModes.serializer(),
        "AgeVerification" to AgeVerification.serializer(),
        "UpdatePrivacy" to UpdatePrivacy.serializer(),
        "PrivacyDto" to PrivacyDto.serializer(),
        "FcmToken" to FcmToken.serializer(),
        // Twin
        "QuestionDto" to QuestionDto.serializer(),
        "Answer" to Answer.serializer(),
        "Answers" to Answers.serializer(),
        "Trait" to Trait.serializer(),
        "TwinDto" to TwinDto.serializer(),
        "TwinPoint" to TwinPoint.serializer(),
        "MemoryDto" to MemoryDto.serializer(),
        "FeedbackPrompt" to FeedbackPrompt.serializer(),
        "Feedback" to Feedback.serializer(),
        // Home
        "Usage" to Usage.serializer(),
        "QuotaDto" to QuotaDto.serializer(),
        "Suggestion" to Suggestion.serializer(),
        "HomeDto" to HomeDto.serializer(),
        // Match
        "Enqueue" to Enqueue.serializer(),
        "TicketDto" to TicketDto.serializer(),
        "AnonProfile" to AnonProfile.serializer(),
        "MatchDto" to MatchDto.serializer(),
        "Choice" to Choice.serializer(),
        "ScenarioDto" to ScenarioDto.serializer(),
        "ScenarioSessionDto" to ScenarioSessionDto.serializer(),
        // Simulation
        "StartSimulation" to StartSimulation.serializer(),
        "SimulationSummary" to SimulationSummary.serializer(),
        "Progress" to Progress.serializer(),
        "SimulationDto" to SimulationDto.serializer(),
        "Dimension" to Dimension.serializer(),
        "ReportDto" to ReportDto.serializer(),
        "Turn" to Turn.serializer(),
        "ScenarioTranscript" to ScenarioTranscript.serializer(),
        "TranscriptDto" to TranscriptDto.serializer(),
        "Decision" to Decision.serializer(),
        "DecisionResult" to DecisionResult.serializer(),
        // Chat
        "MessageDto" to MessageDto.serializer(),
        "ChatSummary" to ChatSummary.serializer(),
        "SendMessage" to SendMessage.serializer(),
        "Mute" to Mute.serializer(),
        // Graph
        "NodeDto" to NodeDto.serializer(),
        "EdgeDto" to EdgeDto.serializer(),
        "GraphStats" to GraphStats.serializer(),
        "GraphDto" to GraphDto.serializer(),
        "NodeDetail" to NodeDetail.serializer(),
        // Social
        "FofCandidate" to FofCandidate.serializer(),
        "FofDto" to FofDto.serializer(),
        "MonthSnapshot" to MonthSnapshot.serializer(),
        "Moment" to Moment.serializer(),
        "HistoryDto" to HistoryDto.serializer(),
        "CommunityDto" to CommunityDto.serializer(),
        "CommunitiesDto" to CommunitiesDto.serializer(),
        "ConnectRequest" to ConnectRequest.serializer(),
        "ConnectRequestsDto" to ConnectRequestsDto.serializer(),
        "Introduce" to Introduce.serializer(),
        "IntroductionDto" to IntroductionDto.serializer(),
        "IntroResponse" to IntroResponse.serializer(),
        "IntroRequest" to IntroRequest.serializer(),
        "InviteDto" to InviteDto.serializer(),
        "Redeem" to Redeem.serializer(),
        "RedeemResult" to RedeemResult.serializer(),
        // Notifications
        "NotificationDto" to NotificationDto.serializer(),
        "NotificationsDto" to NotificationsDto.serializer(),
        "MarkRead" to MarkRead.serializer(),
        // Billing
        "EntitlementDto" to EntitlementDto.serializer(),
        "VerifyPurchase" to VerifyPurchase.serializer(),
        "GrantPlus" to GrantPlus.serializer(),
        // Safety
        "ReportRequest" to ReportRequest.serializer(),
        "HelpResource" to HelpResource.serializer(),
        "ReportCreated" to ReportCreated.serializer(),
        "BlockedDto" to BlockedDto.serializer(),
        "ExportMessage" to ExportMessage.serializer(),
        "ExportConnection" to ExportConnection.serializer(),
        "ExportChat" to ExportChat.serializer(),
        "ExportDto" to ExportDto.serializer(),
        // Admin
        "Resolve" to Resolve.serializer(),
        "AdminReport" to AdminReport.serializer(),
    )

    fun read(name: String): String {
        val stream = Examples::class.java.getResourceAsStream("/examples/$name.json")
            ?: error("Falta el ejemplo canónico examples/$name.json")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Nombres de los archivos de ejemplo presentes (sin extensión). */
    fun files(): Set<String> {
        val dir = Examples::class.java.getResource("/examples") ?: error("No existe examples/")
        return java.io.File(dir.toURI()).listFiles { f -> f.name.endsWith(".json") }!!
            .map { it.name.removeSuffix(".json") }
            .toSet()
    }

}
