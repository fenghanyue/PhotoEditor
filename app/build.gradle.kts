plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 当前版本号，和里程碑对应（M3 是 0.3.0）。发正式版时打同名标签（v0.3.0），
// 开始做下一个里程碑时改成下一个版本号。
val appVersion = "0.3.0"

// GitHub Actions 的编译序号。用它当版本号，保证新版本能覆盖安装旧版本。
val ciBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()

// 从 v 开头的标签编译的是正式版。标签必须和 appVersion 一致，免得发错版本。
val releaseTag = providers.environmentVariable("GITHUB_REF").orNull
    ?.takeIf { it.startsWith("refs/tags/v") }
    ?.removePrefix("refs/tags/")
check(releaseTag == null || releaseTag == "v$appVersion") {
    "标签 $releaseTag 和版本号 $appVersion 对不上，先改 app/build.gradle.kts 里的 appVersion"
}

android {
    namespace = "io.github.fenghanyue.photoeditor"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fenghanyue.photoeditor"
        minSdk = 29
        targetSdk = 37
        versionCode = ciBuildNumber ?: 1
        versionName = when {
            releaseTag != null -> appVersion
            ciBuildNumber != null -> "$appVersion-build$ciBuildNumber"
            else -> "$appVersion-local"
        }
    }

    signingConfigs {
        // 固定签名，存放在仓库里（公开的测试签名，仅供自用）。
        // 每次编译都用同一把钥匙签名，新版本才能直接覆盖安装，设置不会丢。
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 模拟新版 Android 时要访问 JDK 内部的 FileDescriptor 实现
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidsvg)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Compose 测试库默认带的 Espresso 3.5 不认识新版 Android 的输入管理接口，换成新版
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
