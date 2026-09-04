plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("com.google.devtools.ksp")
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

    cocoapods {
        summary = "StockChat - AI stock Q&A demo built with Kuikly"
        homepage = "https://github.com/Tencent-TDS/KuiklyUI"
        version = "1.0"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            isStatic = true
            baseName = "shared"
        }
        license = "MIT"
        extraSpecAttributes["resources"] = "['src/commonMain/assets/**']"
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.tencent.kuikly-open:core:$kuiklyVersion")
            implementation("com.tencent.kuikly-open:core-annotations:$kuiklyVersion")
            // AI 回复 Markdown 渲染（含流式增量渲染）
            implementation("com.tencent.kuiklybase:KuiklyMarkdown:$kuiklyMarkdownVersion")
            // 专业 K 线图（扩展原生 View）
            implementation("io.github.qingfeng19491001:kuiklyklinechart:$kuiklyKLineVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api("com.tencent.kuikly-open:core-render-android:$kuiklyVersion")
            api("io.github.qingfeng19491001:kuiklyklinechartandroid:$kuiklyKLineVersion")
        }
    }
}

ksp {
    arg("pageName", (project.properties["pageName"] as? String) ?: "")
}

dependencies {
    compileOnly("com.tencent.kuikly-open:core-ksp:$kuiklyVersion") {
        add("kspAndroid", this)
        add("kspIosArm64", this)
        add("kspIosSimulatorArm64", this)
    }
}

android {
    namespace = "com.kuikly.stockchat.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            assets.srcDirs("src/commonMain/assets")
        }
    }
}
