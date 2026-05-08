plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.goodzh.converter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuanYZ"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
            buildConfigField("Boolean", "FULL_OFFLINE_TRANSLATION", "false")
            resValue("string", "edition_name", "标准版")
        }
        create("fullOffline") {
            dimension = "edition"
            buildConfigField("Boolean", "FULL_OFFLINE_TRANSLATION", "true")
            resValue("string", "edition_name", "全离线翻译版")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.0.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.register("checkFullOfflineTranslationModels") {
    group = "verification"
    description = "Checks whether the fullOffline flavor contains all required local translation model files."
    doLast {
        val root = project.file("src/fullOffline/assets/translation-models")
        val directions = listOf("en-zh", "zh-en", "en-ja", "ja-en", "ko-en", "en-ko")
        val missing = directions.flatMap { direction ->
            val dir = root.resolve(direction)
            val requiredMissing = listOf(
                "manifest.json",
                "encoder_model_quantized.onnx",
                "decoder_model_quantized.onnx",
                "tokenizer.json",
                "vocab.json",
                "config.json",
                "generation_config.json",
                "license.txt"
            )
                .filterNot { dir.resolve(it).isFile }
                .map { "$direction/$it" }
            requiredMissing
        }
        if (missing.isEmpty()) {
            println("All fullOffline translation models are present.")
        } else {
            println("Missing fullOffline translation model files:")
            missing.forEach { println("- $it") }
            println("Use Git LFS or GitHub Releases for these large files.")
        }
    }
}
