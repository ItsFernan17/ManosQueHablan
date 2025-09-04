plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.frivasm.manosquehablan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frivasm.manosquehablan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        buildConfigField("String", "DEFAULT_BASE_URL", "\"http://192.168.1.100:5000/\"")
    }
    
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    
    packaging {
        jniLibs {
            excludes += setOf(
                "lib/x86_64/libimage_processing_util_jni.so",
                "lib/x86/libimage_processing_util_jni.so",
                "lib/arm64-v8a/libimage_processing_util_jni.so",
                "lib/armeabi-v7a/libimage_processing_util_jni.so"
            )
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/androidx.*.version"
            )
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://192.168.1.13:8001/\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "BASE_URL", "\"https://your-production-domain.com/\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    lint {
        baseline = file("lint-baseline.xml")
        disable += listOf("UnsafeOptInUsageError")
        warningsAsErrors = false
        abortOnError = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("androidx.camera:camera-camera2:1.4.0-rc01")
    implementation("androidx.camera:camera-lifecycle:1.4.0-rc01")
    implementation("androidx.camera:camera-video:1.4.0-rc01")
    implementation("androidx.camera:camera-view:1.4.0-rc01")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
