import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 发布签名：keystore/release.p12 随仓库提交（个人测试项目专用密钥），
// 保证 CI 每次构建签名一致，手机端可直接覆盖安装。
// 若密钥文件缺失则回退 debug 签名，保证构建绝不会因签名问题中断。
val releaseKeystore = rootProject.file("keystore/release.p12")

android {
    namespace = "dev.volumetile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.volumetile"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storeFormat = "PKCS12"
                storePassword = "VolumeTile#2026"
                keyAlias = "volumetile"
                keyPassword = "VolumeTile#2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
