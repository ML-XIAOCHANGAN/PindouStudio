import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 签名信息从 keystore.properties（已 gitignore）读取，仓库里不放任何密码
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val releaseJks = file(keystoreProps.getProperty("storeFile") ?: "pindou-release.jks")

/**
 * 完整字库开关。
 *
 * **默认 true**：99 款字体全部打包（assets 里 42 款精选 + fonts-full/fonts 里 57 款补充），
 * 字体约 470MB，APK 会很大（适合自己用网盘分发 / 侧载）。
 * 想要精简版就加 -PpindouFullFonts=false，只保留精选 42 款（字体约 41MB、APK 约 36MB）。
 */
val fullFonts = (project.findProperty("pindouFullFonts") as String?)?.toBoolean() ?: true

android {
    namespace = "com.pindou.studio"
    compileSdk = 34

    sourceSets {
        getByName("main") {
            if (fullFonts) {
                // 只是把字库目录并进 assets/fonts，应用代码无需区分
                assets.srcDir(rootProject.file("fonts-full"))
            }
        }
    }

    defaultConfig {
        applicationId = "com.pindou.studio"
        minSdk = 24
        targetSdk = 34
        versionCode = 53
        versionName = "2.19.3"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (releaseJks.exists()) {
                storeFile = releaseJks
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "pindou"
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 没有 keystore 时退化为未签名包（CI / 他人克隆后也能 assembleRelease）
            if (releaseJks.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        noCompress += listOf("json")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // viewModelScope：保存 / 加载协程跟随 ViewModel 生命周期
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
