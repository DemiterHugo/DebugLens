plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.demiterhugo.debuglens.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
