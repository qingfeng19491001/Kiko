pluginManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        gradlePluginPortal()
        google()
        mavenCentral()
        // 国内镜像兜底（部分镜像偶发 5xx，放在最后）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        // KuiklyKLineChart 发布仓库（本地 mavenLocal 优先，便于源码构建）
        mavenLocal()
        maven("https://qingfeng19491001.github.io/KuiklyKLineChart")
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
    }
}

rootProject.name = "KuiklyStockChat"

include(":shared")
include(":androidApp")
