plugins {
    alias(libs.plugins.jetbrainsKotlinMultiplatform)
}

group = "org.yt8492"
version = "1.0.0"

kotlin {
    jvm()
    iosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.uuid)
            }
        }
    }
}
