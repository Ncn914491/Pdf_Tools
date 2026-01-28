import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourname.pdftoolkit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yourname.pdftoolkit"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Play Store requirements
        multiDexEnabled = true
        
        // App Bundle optimization
        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val properties = Properties()
                properties.load(FileInputStream(keystorePropertiesFile))
                storeFile = rootProject.file(properties["storeFile"] as String)
                storePassword = properties["storePassword"] as String
                keyAlias = properties["keyAlias"] as String
                keyPassword = properties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Play Store optimization
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/versions/9/module-info.class"
        }
    }
    
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
    
    // Custom AAB naming with app name and version
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (variant.buildType.name == "release") {
                outputImpl.outputFileName = "PDFToolkit-v${variant.versionName}-${variant.buildType.name}.apk"
            }
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // ExifInterface for EXIF metadata reading (Apache 2.0)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Compose BOM 2023.10.01 - Stable version
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Using extended icons - consider switching to subset for smaller APK
    implementation("androidx.compose.material:material-icons-extended")
    
    // Compose Navigation & ViewModel
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // PDF Tools - PdfBox-Android for PDF manipulation
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // CameraX for Scan to PDF (Apache 2.0)
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ML Kit Text Recognition for OCR (Apache 2.0)
    // Note: Models are downloaded on-demand when first used (~40MB)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // Coil for image loading (Apache 2.0) - lightweight (~2MB)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Glide for advanced image loading with EXIF rotation support (BSD-like license)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // uCrop for lightweight image cropping (Apache 2.0)
    implementation("com.github.yalantis:ucrop:2.2.8")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
