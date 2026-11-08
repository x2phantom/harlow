plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.harlow.core.database"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Migration tests read the exported schemas
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.bundles.room)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.junit.ext)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.runner)
}
