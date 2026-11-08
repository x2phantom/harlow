import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.io.FileInputStream
import java.util.Properties
import java.util.regex.Pattern

/** Version name from the release tag: `v1.0.0` → `1.0.0`; between tags `1.0.0-3-g1a2b3c4`, `-dirty` if uncommitted. */
fun gitDescribe(): String = providers.exec {
    commandLine("git", "describe", "--tags", "--always", "--dirty")
}.standardOutput.asText.get().trim().removePrefix("v")

/** Puts LICENSE and NOTICE of the repository root into the assets, for the license screen. */
abstract class CopyLegalNotices : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val notices: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDirectory.get().dir("licenses").asFile
        target.mkdirs()
        notices.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.dependencyCheck)
}

android {
    namespace = "dev.harlow.automotive"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.hwld.harlow"
        minSdk = 28
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = (System.getenv("VERSION_NAME") ?: gitDescribe())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = rootProject.file("local.properties")
            if (props.exists()) {
                val localProps = FileInputStream(props).use { stream ->
                    Properties().apply { load(stream) }
                }
                storeFile = file(localProps.getProperty("RELEASE_STORE_FILE", "../release-keystore.jks"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Host tests (screenshot tests) run for debug only: the release variant's merged manifest lacks the
// ComponentActivity that Compose's test rule launches (ui-test-manifest is a debug dependency).
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
    onVariants { variant ->
        val taskName = "copyLegalNotices${variant.name.replaceFirstChar { it.uppercase() }}"
        val copyLegalNotices = tasks.register<CopyLegalNotices>(taskName) {
            notices.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyLegalNotices, CopyLegalNotices::outputDirectory)
    }
}

// Lockfile of everything the release APK ships (all modules, transitive), for the vulnerability scan (OSV-Scanner).
// Lenient: a version update doesn't fail the build, it only needs new locks:
// ./gradlew :app-automotive:dependencies --configuration releaseRuntimeClasspath --write-locks
dependencyLocking {
    lockMode = LockMode.LENIENT
}
configurations.matching { it.name == "releaseRuntimeClasspath" }.configureEach {
    resolutionStrategy.activateDependencyLocking()
}

// OWASP Dependency-Check against the NVD, like the CVE scans of car app stores. Scans what
// the release APK ships. The NVD API key comes from the environment, never from the repository:
// NVD_API_KEY=… ./gradlew :app-automotive:dependencyCheckAnalyze
dependencyCheck {
    scanConfigurations = listOf("releaseRuntimeClasspath")
    formats = listOf("HTML", "JSON")
    // Fails the task from CVSS 7 (high) on; false positives go to the suppression file, each with its reason
    failBuildOnCVSS = 7.0f
    suppressionFile = file("dependency-check-suppressions.xml").path
    nvd {
        apiKey = providers.environmentVariable("NVD_API_KEY").orNull
    }
    analyzers {
        // Only the Maven artifacts matter; OSS Index needs its own account, the rest scans other ecosystems.
        ossIndex { enabled = false }
        assemblyEnabled = false
        nodeEnabled = false
        nodeAudit { enabled = false }
        retirejs { enabled = false }
    }
}

// The NVD changes daily and the plugin doesn't track its inputs (suppressions, settings): always run it. The plugin isn't
// compatible with the configuration cache.
tasks.matching { it.name == "dependencyCheckAnalyze" }.configureEach {
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache("OWASP Dependency-Check plugin")
}

aboutLibraries {
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
        exclusionPatterns.addAll(
            Pattern.compile("org\\.jetbrains\\.compose\\..*"),
            Pattern.compile("org\\.jetbrains\\.androidx\\..*"),
        )
    }
}

dependencies {
    // Session, sign-in and account logic for the sign-in and settings activities
    implementation(project(":core:appcommon"))

    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))

    // Browsing and playback are drawn by the car's media center from HarlowMediaService; the app's own UI is only
    // sign-in and settings.
    implementation(project(":feature:settings"))

    // Background sync — Jellyfin library sync stays the same
    implementation(project(":sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.work.runtime.ktx)
    implementation(libs.work.hilt)
    implementation(libs.aboutlibraries.core)

    coreLibraryDesugaring(libs.desugar.jdk)

    // Security fixes in transitive dependencies (found by the OSV scan)
    constraints {
        implementation(libs.guava) { because("GHSA-xxph-c9ww-hj94: Media3 1.11.1 brings Guava 33.3.1") }
    }

    debugImplementation(libs.bundles.compose.debug)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4.desktop)
    testImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.bundles.android.testing)
    androidTestImplementation(platform(libs.compose.bom))
}
