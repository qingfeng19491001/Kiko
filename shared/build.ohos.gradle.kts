// shared 模块的鸿蒙构建脚本
// 用法：./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64
//
// 注意：OHOS 端使用 KBA 工具链，与 Android/iOS 的依赖不完全一致。
// kotlinx 官方版（特别是 -serialization-json）没有 ohos_arm64 klib 分发，
// 因此本工程 shared 在 OHOS 上依赖解析时仅保留 Kuikly Core。

plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val KEY_PAGE_NAME = "pageName"

kotlin {
    androidTarget()

    // iOS targets 保留以便 CocoaPods 生成的 xcframework 可被 IDE 解析
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "StockChat - AI stock Q&A demo built with Kuikly"
        homepage = "https://github.com/Tencent-TDS/KuiklyUI"
        version = "1.0"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
            license = "MIT"
        }
    }

    // 鸿蒙 target
    ohosArm64 {
        binaries.sharedLib {
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":components"))
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
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
                // kotlinx 移到 androidMain/iosMain
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }
        // iOS 端补充 kotlinx（OHOS 不参与）。
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }
    }
}

ksp {
    arg(KEY_PAGE_NAME, getPageName())
}

dependencies {
    compileOnly("com.tencent.kuikly-open:core-ksp:${Version.getKuiklyOhosVersion()}") {
        add("kspAndroid", this)
        add("kspIosArm64", this)
        add("kspIosSimulatorArm64", this)
        add("kspOhosArm64", this)
    }
}

android {
    namespace = "com.kuikly.stockchat.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}

fun getPageName(): String {
    return (project.properties[KEY_PAGE_NAME] as? String) ?: ""
}
