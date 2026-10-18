plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "com.katiearose.sobriety"
        minSdk = 21
        targetSdk = 33
        versionCode = 22
        versionName = "v10.0.0"
        setProperty("archivesBaseName", "Sobriety $versionName")
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles += getDefaultProguardFile("proguard-android.txt")
            proguardFiles += file("proguard-rules.pro")
        }

        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles += getDefaultProguardFile("proguard-android.txt")
            proguardFiles += file("proguard-rules.pro")
            versionNameSuffix = "debug"
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            //signingConfig = signingConfigs.debug
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
    }
    kotlinOptions {
        jvmTarget = "9"
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        disable += listOf("MissingTranslation")
    }
    namespace = "com.katiearose.sobriety"
}

dependencies {

    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.0")
    //required for AndroidX Preference library
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs") {
        version {
            strictly ("1.2.0") //2.0.0 requires gradle 7.4.0-alpha10
        }
    }
    implementation("com.github.vipulasri:timelineview:1.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}
