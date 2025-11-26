plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.autonomous_delivery_robot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.autonomous_delivery_robot"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {


    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Material Components
    implementation ("com.google.android.material:material:1.9.0")

    // ConstraintLayout
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")


// Firebase
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-database:20.3.0")
    implementation("com.google.firebase:firebase-core:21.1.1")
    implementation("com.google.firebase:firebase-auth")

// Add the dependency for Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.0.0")

// MQTT - Eclipse Paho
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

// RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

// Fix for LocalBroadcastManager issue in Paho
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

// Material Design Components
    implementation("com.google.android.material:material:1.12.0")

// AppCompat & ConstraintLayout
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

// Optional: Glide for image/video streaming
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

// Optional: Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    implementation ("com.journeyapps:zxing-android-embedded:4.3.0")

}