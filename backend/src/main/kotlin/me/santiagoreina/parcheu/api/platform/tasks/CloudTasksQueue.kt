package me.santiagoreina.parcheu.api.platform.tasks

import com.google.api.gax.rpc.AlreadyExistsException
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.HttpMethod
import com.google.cloud.tasks.v2.HttpRequest
import com.google.cloud.tasks.v2.OidcToken
import com.google.cloud.tasks.v2.Task
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import org.slf4j.LoggerFactory

/**
 * Cola real de Cloud Tasks. Cada tarea es un POST a `<serviceUrl>/internal<path>` con token OIDC de
 * `sa-tasks` y la audiencia fija de `/internal` (`INTERNAL_OIDC_AUDIENCE`, E0.5).
 */
class CloudTasksQueue(
    internal val queues: Map<QueueKind, String>,
    private val serviceUrl: String,
    private val serviceAccount: String,
    private val audience: String,
    private val client: Lazy<CloudTasksClient> = lazy { CloudTasksClient.create() },
) : TaskQueue {
    private val log = LoggerFactory.getLogger(CloudTasksQueue::class.java)

    override suspend fun enqueue(task: TaskRequest) {
        val queue = queues[task.queue] ?: error("Falta ${task.queue.envVar}")
        val request = HttpRequest.newBuilder()
            .setUrl(serviceUrl.trimEnd('/') + "/internal" + task.path)
            .setHttpMethod(HttpMethod.POST)
            .putHeaders("Content-Type", "application/json")
            .setBody(ByteString.copyFromUtf8(task.body))
            .setOidcToken(OidcToken.newBuilder().setServiceAccountEmail(serviceAccount).setAudience(audience))
            .build()
        val created = Task.newBuilder().setName("$queue/tasks/${task.name}").setHttpRequest(request).build()
        try {
            withContext(Dispatchers.IO) { client.value.createTask(queue, created) }
        } catch (e: AlreadyExistsException) {
            log.info("Tarea {} ya existía en {}: no se duplica", task.name, queue)
        }
    }

    companion object {
        private fun String?.orBlankNull() = this?.takeIf { it.isNotBlank() }

        /**
         * Cloud Tasks exige `projects/<p>/locations/<r>/queues/<cola>`. Si [value] ya es la ruta se respeta;
         * si es el nombre corto se completa con [project] y [region].
         */
        internal fun queuePath(value: String, project: String?, region: String?): String =
            if (value.startsWith("projects/")) value else "projects/$project/locations/$region/queues/$value"

        /**
         * Lee `TASKS_QUEUE_*` y `SERVICE_URL`. Si falta algo devuelve [UnconfiguredTaskQueue] para que
         * el servicio arranque y el fallo se vea (y se registre) al encolar.
         */
        fun fromEnv(config: AppConfig, vars: Map<String, String>): TaskQueue {
            val raw = QueueKind.entries.mapNotNull { kind -> vars[kind.envVar]?.takeIf { it.isNotBlank() }?.let { kind to it } }.toMap()
            val sqlParts = vars["CLOUD_SQL_INSTANCE"]?.split(':')?.takeIf { it.size == 3 }
            val project = vars["GCP_PROJECT"].orBlankNull() ?: vars["FIREBASE_PROJECT_ID"].orBlankNull() ?: sqlParts?.get(0)
            val region = vars["GCP_REGION"].orBlankNull() ?: sqlParts?.get(1)
            // El Terraform de E0.5 entrega el nombre corto de la cola (`ai-light`), no la ruta completa.
            val unresolved = raw.values.any { !it.startsWith("projects/") } && (project == null || region == null)
            if (unresolved) {
                return UnconfiguredTaskQueue("las colas usan nombre corto y falta GCP_PROJECT o GCP_REGION para armar la ruta")
            }
            val queues = raw.mapValues { (_, v) -> queuePath(v, project, region) }
            val url = vars["SERVICE_URL"]?.takeIf { it.isNotBlank() }
            val missing = buildList {
                if (url == null) add("SERVICE_URL")
                QueueKind.entries.filter { it !in queues }.forEach { add(it.envVar) }
            }
            // Basta con la cola de IA ligera para E2.3; las demás las exige quien las use.
            if (url == null || QueueKind.AI_LIGHT !in queues) {
                return UnconfiguredTaskQueue("faltan ${missing.joinToString()}")
            }
            return CloudTasksQueue(queues, url, config.tasksServiceAccount, config.oidcAudience)
        }
    }
}
