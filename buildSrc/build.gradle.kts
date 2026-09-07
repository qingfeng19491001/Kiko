plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Kuikly 鸿蒙端编译需 KBA 工具链
}
