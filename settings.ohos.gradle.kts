pluginManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        mavenLocal()
        maven("https://qingfeng19491001.github.io/KuiklyKLineChart")
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
    }
}

rootProject.name = "KuiklyStockChat"

val ohosBuildFileName = "build.ohos.gradle.kts"
rootProject.buildFileName = ohosBuildFileName

include(":components")
project(":components").buildFileName = ohosBuildFileName
include(":shared")
project(":shared").buildFileName = ohosBuildFileName
