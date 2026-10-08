import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory

abstract class VerifyNoInternetPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val verifiedManifest: RegularFileProperty

    @TaskAction
    fun verifyManifest() {
        val manifestFile = mergedManifest.get().asFile
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifestFile)

        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val internetPermission = "android.permission.INTERNET"
        val permissionTags = listOf(
            "uses-permission",
            "uses-permission-sdk-23",
            "uses-permission-sdk-m",
        )

        val requestsInternet = permissionTags.any { tagName ->
            val nodes = document.getElementsByTagName(tagName)
            (0 until nodes.length).any { index ->
                val element = nodes.item(index)
                element.attributes?.getNamedItemNS(androidNamespace, "name")?.nodeValue == internetPermission
            }
        }

        if (requestsInternet) {
            throw GradleException(
                "Aside release builds must not request android.permission.INTERNET. " +
                    "The permission was found in the final merged release manifest: ${manifestFile.path}"
            )
        }

        val outputFile = verifiedManifest.get().asFile
        outputFile.parentFile.mkdirs()
        manifestFile.copyTo(outputFile, overwrite = true)

        logger.lifecycle("Verified: Aside's merged release manifest does not request android.permission.INTERNET")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.nickspeelman.localjournal"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nickspeelman.localjournal"
        minSdk = 26
        targetSdk = 37
        versionCode = 26
        versionName = "2.0.0-alpha8.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Android system biometric/device-credential authentication
    implementation(libs.androidx.biometric)
    // Biometric 1.1.0 otherwise pulls Fragment 1.2.5, whose legacy 16-bit
    // request-code validation crashes Activity Result API launchers.
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Privacy invariant: release artifacts must never request ordinary Internet access.
// This transforms the final merged release manifest by validating it and passing it through
// unchanged. Because it is wired into the artifact pipeline, APK/AAB packaging cannot consume
// the release manifest without this verification task succeeding first.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verifyNoInternet = tasks.register<VerifyNoInternetPermissionTask>(
            "verify${variantName}NoInternetPermission"
        ) {
            group = "verification"
            description =
                "Fails if the final merged ${variant.name} manifest requests android.permission.INTERNET."
        }

        variant.artifacts
            .use(verifyNoInternet)
            .wiredWithFiles(
                VerifyNoInternetPermissionTask::mergedManifest,
                VerifyNoInternetPermissionTask::verifiedManifest,
            )
            .toTransform(SingleArtifact.MERGED_MANIFEST)
    }
}
