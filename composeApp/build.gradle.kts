import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Networking (multiplatform-friendly)
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
                implementation("com.squareup.retrofit2:retrofit:2.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

                // Multiplatform settings / storage alternative
                implementation("com.russhwolf:multiplatform-settings:1.2.0")
                implementation("com.russhwolf:multiplatform-settings-coroutines:1.2.0")
                implementation("com.russhwolf:multiplatform-settings-serialization:1.2.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
                implementation("androidx.navigation:navigation-compose:2.8.4")
                implementation("androidx.datastore:datastore-preferences:1.1.1")
                implementation("io.coil-kt:coil-compose:2.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
    }
}

android {
    namespace = "com.quickgerrit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quickgerrit.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.59"
        val repo = (project.findProperty("githubRepo") as String?)
            ?: (System.getenv("GITHUB_REPOSITORY") ?: "")
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
        buildConfigField("String", "UPDATE_APK_NAME", "\"QuickGerrit-debug.apk\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.quickgerrit.app.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage
            )
            packageName = "QuickGerrit"
            packageVersion = "1.0.59"
            description = "Modern Gerrit Code Review client"
            copyright = "© 2026 QuickGerrit"
            vendor = "QuickGerrit"

            windows {
                menuGroup = "QuickGerrit"
                // iconFile.set(project.file("icon.ico"))
            }
            linux {
                // iconFile.set(project.file("icon.png"))
            }
            macOS {
                // iconFile.set(project.file("icon.icns"))
                bundleID = "com.quickgerrit.app"
            }
        }
    }
}
