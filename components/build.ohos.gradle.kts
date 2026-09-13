plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    ohosArm64 {
        binaries.sharedLib { }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuiklybase:KuiklyMarkdown:1.0.6-2.0.21-ohos")
                implementation("io.github.qingfeng19491001:kuiklyklinechart:0.1.0-2.0.21-KBA-010")
                // KuiklyMarkdown ohosArm64 依赖 serialization；官方 kotlinx 无 ohos klib，需 KBA 分支
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1-KBA-003")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1-KBA-003")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyOhosVersion()}")
            }
        }
    }
}

android {
    namespace = "com.kuikly.components"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}
