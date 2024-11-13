plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")  // Підключаємо Google Services тут
}


android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24

        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
dependencies {
    // Основные AndroidX зависимости
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Firebase BOM для управления версиями
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    // Google Play Services
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OSMDroid для работы с картами
    implementation("org.osmdroid:osmdroid-android:6.1.11")
    implementation("org.osmdroid:osmdroid-mapsforge:6.1.11")

    // Jetpack Compose зависимости
    implementation("androidx.activity:activity-compose:1.5.1")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.runtime:runtime-livedata:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")

    implementation("androidx.compose.material:material:1.7.5")
    // ConstraintLayout для Compose
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0")

    // Зависимость для работы с настройками
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Зависимости для отладки и тестирования
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")

    // Тестовые зависимости
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
}
