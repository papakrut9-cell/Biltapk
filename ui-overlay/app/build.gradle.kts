import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.devtools.ksp") version "2.3.6"
}

android {
    namespace = "com.example.llama"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.llama.aichat"
        minSdk = 33
        targetSdk = 36
        versionCode = 8
        versionName = "1.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            // Обрезаем APK: без нативных .so класс-часть ~5 МБ → <2 МБ,
            // правила R8 уже есть в proguard-rules.pro (keep com.arm.aichat.**)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Подписываем debug-keystore, чтобы release-APK ставился сразу из артефакта
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Нативные бэкенды движка (libggml-cpu-android_armv*) должны ЛЕЖАТЬ ФАЙЛАМИ в
    // /data/app/.../lib — движок грузит их через ggml_backend_load_all_from_path(nativeLibDir).
    // С useLegacyPackaging=false (extractNativeLibs=false) каталог lib пуст, бэкенды не
    // находя��ся, и любая нагрузка модели падает: "no backends are loaded".
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// JVM-цель Kotlin должна совпадать с Java-17, иначе Gradle падает
// с ошибкой "Inconsistent JVM-target compatibility detected"
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.bundles.androidx)
    implementation(libs.material)
    implementation(project(":lib"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // lifecycleScope и корутины приходят транзитивно не всегда — декларируем явно
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Splash screen (Theme.SplashScreen / installSplashScreen)
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
}
