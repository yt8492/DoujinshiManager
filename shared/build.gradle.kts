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
                implementation(libs.kotlinx.datetime)
                implementation(libs.uuid)
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
