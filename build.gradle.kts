// 根构建脚本：仅声明插件版本，统一由 CI / 开发机解析
// 版本组合依据官方兼容表：
//   AGP 8.13.0  需要 Gradle 8.13 + JDK 17（AGP 8.13.0 release notes）
//   Kotlin 2.3.x 兼容 AGP 8.2.2 - 8.13（developer.android.com/build/kotlin-support）
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
}
