plugins {
    alias(libs.plugins.jetbrainsKotlinMultiplatform)
    alias(libs.plugins.sqlDelight)
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
                api(libs.kotlinx.datetime)
                api(libs.uuid)
                api(libs.napier)
                implementation(libs.koin.core)
            }
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.yt8492.doujinshimanager.database")
        }
    }
}
