import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun findConfigurationProperty(name: String): String? {
    return providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
}

val releaseStoreFile = findConfigurationProperty("RELEASE_STORE_FILE")
val releaseStorePassword = findConfigurationProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = findConfigurationProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = findConfigurationProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfiguration = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.yt8492.doujinshimanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yt8492.doujinshimanager"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfiguration) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfiguration) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val validateReleaseSigningConfiguration = tasks.register("validateReleaseSigningConfiguration") {
    doLast {
        val missingProperties = buildList {
            if (releaseStoreFile.isNullOrBlank()) add("RELEASE_STORE_FILE")
            if (releaseStorePassword.isNullOrBlank()) add("RELEASE_STORE_PASSWORD")
            if (releaseKeyAlias.isNullOrBlank()) add("RELEASE_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("RELEASE_KEY_PASSWORD")
        }
        check(missingProperties.isEmpty()) {
            "Release signing properties are required: ${missingProperties.joinToString()}. " +
                "Set them in local.properties, ~/.gradle/gradle.properties, or environment variables."
        }
        check(rootProject.file(releaseStoreFile!!).isFile) {
            "Release keystore does not exist: ${rootProject.file(releaseStoreFile).path}"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigningConfiguration)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.sqldelight.androiddriver)
    implementation(libs.koin.compose)
    implementation(libs.coil)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlitedriver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
