buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 自带 Kotlin 支持，默认使用它依赖的 KGP 2.2.10。
        // 这里换成版本目录里的 Kotlin 版本，和 Compose 编译插件保持一致。
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
