import com.android.build.api.artifact.SingleArtifact
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.roborazzi)
}

// URLs por entorno. Se pueden sobrescribir con -Pparcheu.apiUrl.dev=... (o en ~/.gradle/gradle.properties)
// sin tocar este archivo. La URL de Cloud Run es determinista: https://<servicio>-<número de proyecto>.<región>.run.app
fun urlProperty(name: String, default: String): String =
    providers.gradleProperty(name).getOrElse(default)

android {
    namespace = "me.santiagoreina.parcheu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.santiagoreina.parcheu"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // Mientras no exista la llave de subida (E10.2), el release local se firma con la llave de debug
            // para poder instalarlo y probar R8. El pipeline de release la reemplaza por la llave de subida.
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    // Login con Google (E2.2, ADR-0021): Credential Manager + googleid → Firebase Auth.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
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
