plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.harlow.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    testImplementation(libs.bundles.testing)
}
