plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

val kuiklyVersion: String = providers.gradleProperty("KUIKLY_VERSION").get()
val kuiklyMarkdownVersion: String = providers.gradleProperty("KUIKLY_MARKDOWN_VERSION").get()
val kuiklyKLineVersion: String = providers.gradleProperty("KUIKLY_KLINE_VERSION").get()

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("com.tencent.kuikly-open:core:$kuiklyVersion")
            implementation("io.github.qingfeng19491001:kuiklyklinechart:$kuiklyKLineVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api("com.tencent.kuikly-open:core-render-android:$kuiklyVersion")
            implementation("com.tencent.kuiklybase:KuiklyMarkdown:$kuiklyMarkdownVersion")
            api("io.github.qingfeng19491001:kuiklyklinechartandroid:$kuiklyKLineVersion")
        }
        iosMain.dependencies {
            implementation("com.tencent.kuiklybase:KuiklyMarkdown:$kuiklyMarkdownVersion")
        }
    }
}

android {
    namespace = "com.kuikly.components"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
