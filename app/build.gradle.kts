import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tedexcuseme.volumetile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tedexcuseme.volumetile"
        minSdk = 34
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // 刻意不配置 signingConfig：
            // AGP 8.13 的 Gradle Kotlin DSL 中 SigningConfig 类型没有 storeFormat 属性
            // （run #3 失败根因：Unresolved reference: storeFormat，该属性仅存在于
            //  Groovy 动态分发的内部实现类上）。
            // 因此 Gradle 产出 app-release-unsigned.apk，由 CI 使用
            // apksigner --ks-type PKCS12 显式指定格式签名，见 .github/workflows/build.yml。
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Kotlin 编译目标与上方 Java 编译目标保持一致（JVM 17，匹配 AGP 8.13 要求的 JDK 17）
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
