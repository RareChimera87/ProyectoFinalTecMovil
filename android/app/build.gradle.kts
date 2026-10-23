import com.android.build.api.artifact.SingleArtifact
import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.play.publisher)
}

// URLs por entorno. Se pueden sobrescribir con -Pparcheu.apiUrl.dev=... (o en ~/.gradle/gradle.properties)
// sin tocar este archivo. La URL de Cloud Run es determinista: https://<servicio>-<número de proyecto>.<región>.run.app
fun urlProperty(name: String, default: String): String =
    providers.gradleProperty(name).getOrElse(default)

// Versión (E10.2). El pipeline android-release pasa -Pparcheu.versionCode (scripts/version-code.sh,
// derivado del número de ejecución) y -Pparcheu.versionName (del tag v*). En local quedan 1 y 0.0.1.
val releaseVersionCode =
    providers.gradleProperty("parcheu.versionCode").map(String::toInt).getOrElse(1)
val releaseVersionName = providers.gradleProperty("parcheu.versionName").getOrElse("0.0.1")
require(releaseVersionCode in 1..2_100_000_000) {
    "parcheu.versionCode=$releaseVersionCode fuera del rango que acepta Play (1..2100000000)"
}

// Llave de subida (E10.2). Nunca en el repo: el keystore y sus contraseñas llegan por variables de
// entorno (secretos UPLOAD_* en CI; en local, ~/.parcheu/upload-key.env más UPLOAD_KEYSTORE_FILE).
// Sin ellas el release se firma con la llave de debug para probar R8 en local, salvo que se pida
// -Pparcheu.release.requireUploadKey=true (lo hace el pipeline), que entonces falla.
val uploadKeystoreFile = providers.environmentVariable("UPLOAD_KEYSTORE_FILE").orNull
val uploadKeystorePassword = providers.environmentVariable("UPLOAD_KEYSTORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("UPLOAD_KEY_PASSWORD").orNull
val hasUploadKey =
    listOf(uploadKeystoreFile, uploadKeystorePassword, uploadKeyAlias, uploadKeyPassword)
        .all { !it.isNullOrBlank() }
val requireUploadKey =
    providers.gradleProperty("parcheu.release.requireUploadKey").map(String::toBoolean)
        .getOrElse(false)
if (requireUploadKey && !hasUploadKey) {
    throw GradleException(
        "parcheu.release.requireUploadKey=true pero falta la llave de subida: define " +
            "UPLOAD_KEYSTORE_FILE, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS y UPLOAD_KEY_PASSWORD."
    )
}
if (hasUploadKey && !file(uploadKeystoreFile!!).isFile) {
    throw GradleException("UPLOAD_KEYSTORE_FILE no apunta a un archivo: $uploadKeystoreFile")
}

android {
    namespace = "me.santiagoreina.parcheu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.santiagoreina.parcheu"
        minSdk = 24
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadKeystoreFile!!)
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            val api =
                urlProperty("parcheu.apiUrl.dev", "https://api-388770932007.us-central1.run.app/")
            val hosting = urlProperty("parcheu.hostingUrl.dev", "https://parcheu-dev.web.app")
            buildConfigField("String", "API_URL", "\"$api\"")
            buildConfigField("String", "HOSTING_URL", "\"$hosting\"")
            buildConfigField("String", "TERMS_URL", "\"$hosting/terminos\"")
            buildConfigField("String", "PRIVACY_URL", "\"$hosting/privacidad\"")
            resValue("string", "app_name", "ParcheU dev")
        }
        create("prod") {
            dimension = "env"
            // Pendiente humano (docs/pendientes-humanos.md, E0.7): cuando exista parcheu-prod y su servicio api,
            // poner aquí la URL real (con el número de proyecto) o pasarla con -Pparcheu.apiUrl.prod.
            val api =
                urlProperty("parcheu.apiUrl.prod", "https://api-parcheu-prod.us-central1.run.app/")
            val hosting = urlProperty("parcheu.hostingUrl.prod", "https://parcheu-prod.web.app")
            buildConfigField("String", "API_URL", "\"$api\"")
            buildConfigField("String", "HOSTING_URL", "\"$hosting\"")
            buildConfigField("String", "TERMS_URL", "\"$hosting/terminos\"")
            buildConfigField("String", "PRIVACY_URL", "\"$hosting/privacidad\"")
            resValue("string", "app_name", "ParcheU")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // Con la llave de subida en el entorno (pipeline android-release) se firma con ella; si no,
            // con la de debug para instalar y probar R8 en local. Un AAB firmado con debug nunca va a Play:
            // el pipeline exige la llave (requireUploadKey) y verifica la huella del certificado.
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // Gradle Play Publisher: solo se publica el flavor prod (ver el bloque play más abajo).
    playConfigs {
        register("prod") {
            enabled.set(true)
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // El contrato usa kotlin.time.Instant, experimental en Kotlin 2.2.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

googleServices {
    // google-services.json no se versiona: lo deja scripts/fetch-google-services.sh (local) o un secreto (CI).
    // Sin el archivo el build sigue y solo avisa; Firebase se inicializa cuando lo usen E0.8/E2.2.
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

// Gradle Play Publisher (E10.2). Solo el flavor prod (me.santiagoreina.parcheu) se publica; dev nunca
// va a Play. Credenciales (ADR-0014, sin llaves por defecto): credenciales por defecto de Google (ADC)
// que deja el WIF de GitHub suplantando a `sa-play` de parcheu-prod; solo si existe
// ANDROID_PUBLISHER_CREDENTIALS (llave JSON de `sa-play`, excepción temporal de la enmienda de
// ADR-0014) se usa esa llave. La pista y el estado se pasan por línea
// de comandos (--track, --release-status); aquí quedan los valores de la subida manual: interna, completa.
// Notas de versión: src/prod/play/release-notes/es-419/default.txt.
play {
    enabled.set(false)
    defaultToAppBundles.set(true)
    useApplicationDefaultCredentials.set(
        System.getenv("ANDROID_PUBLISHER_CREDENTIALS").isNullOrBlank()
    )
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
}

/**
 * Falla si el manifiesto fusionado no declara [requiredPermission]. Play solo deja crear suscripciones
 * si el AAB subido incluye Play Billing, cuyo manifiesto aporta `com.android.vending.BILLING` (E7.0).
 */
abstract class VerifyMergedManifestPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val requiredPermission: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val nodes = document.getElementsByTagName("uses-permission")
        val permissions =
            (0 until nodes.length).map { index ->
                (nodes.item(index) as org.w3c.dom.Element).getAttributeNS(androidNs, "name")
            }
        val permission = requiredPermission.get()
        if (permission !in permissions) {
            throw GradleException(
                "El manifiesto fusionado ${mergedManifest.get().asFile} no declara $permission. " +
                    "Permisos encontrados: $permissions"
            )
        }
        report.get().asFile.writeText("OK: $permission presente en el manifiesto fusionado\n")
    }
}

androidComponents {
    onVariants(selector().withName("prodRelease")) { variant ->
        val verify =
            tasks.register<VerifyMergedManifestPermission>("verifyProdReleaseBillingPermission") {
                group = "verification"
                description =
                    "Verifica que el manifiesto fusionado de prodRelease pida BILLING (E7.0)."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                requiredPermission.set("com.android.vending.BILLING")
                report.set(layout.buildDirectory.file("reports/billing-permission/prodRelease.txt"))
            }
        tasks.named("check") { dependsOn(verify) }
    }
}

roborazzi {
    // Las capturas de referencia se versionan junto a las pruebas.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":designsystem"))
    implementation(libs.parcheu.contract)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)
    // Login con Google (E2.2, ADR-0021): Credential Manager + googleid → Firebase Auth.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // E5.3: push con FCM.
    implementation(libs.firebase.messaging)
    // E4.4: fila de match en tiempo real (Firestore de solo lectura).
    // E4.5: progreso en vivo de simulations/{id} (ADR-0004: Firestore es canal de solo lectura).
    // E5.2: chat en vivo, listener de mensajes con caché offline (arquitectura.md §8).
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    // E7.0: Play Billing. Su manifiesto agrega com.android.vending.BILLING por fusión (lo verifica
    // verifyProdReleaseBillingPermission).
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.kotlin.reflect)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
