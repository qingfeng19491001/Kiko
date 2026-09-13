object Version {

    private const val KUIKLY_VERSION = "2.26.0"
    private const val KOTLIN_VERSION = "2.1.21"
    private const val KOTLIN_OHOS_VERSION = "2.0.21-ohos"

    /**
     * 通用 Kuikly 版本规则：${shortVersion}-${kotlinVersion}
     * 适用于 core、core-ksp、core-annotations、core-render-android
     */
    fun getKuiklyVersion(): String {
        return "$KUIKLY_VERSION-$KOTLIN_VERSION"
    }

    /**
     * Kuikly 鸿蒙版本号（OHOS 专用，与 Android/iOS 不同的 Kotlin 分支）。
     */
    fun getKuiklyOhosVersion(): String {
        return "$KUIKLY_VERSION-$KOTLIN_OHOS_VERSION"
    }
}

object BuildPlugin {
    val kuikly by lazy {
        "com.tencent.kuikly-open:core-gradle-plugin:${Version.getKuiklyVersion()}"
    }
}
