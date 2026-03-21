plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

dependencyLocking {
    lockAllConfigurations()
}

android {
    namespace = "com.x.heartbeep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.x.heartbeep"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystoreFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("KEY_ALIAS")
    val keyPassword = System.getenv("KEY_PASSWORD")

    if (keystoreFile != null && keystoreFile.exists() &&
        keystorePassword != null && keyAlias != null && keyPassword != null
    ) {
        signingConfigs {
            create("ci") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        val ciConfig = signingConfigs.findByName("ci")
        debug {
            if (ciConfig != null) signingConfig = ciConfig
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (ciConfig != null) signingConfig = ciConfig
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Force patched versions of vulnerable transitive dependencies pulled in by AGP/Kotlin build tools.
// These don't end up in the APK but Dependabot flags them in the build dependency graph.
configurations.configureEach {
    resolutionStrategy {
        force("io.netty:netty-common:4.2.10.Final")
        force("io.netty:netty-handler:4.2.10.Final")
        force("io.netty:netty-codec:4.2.10.Final")
        force("io.netty:netty-codec-http:4.2.10.Final")
        force("io.netty:netty-codec-http2:4.2.10.Final")
        force("io.netty:netty-buffer:4.2.10.Final")
        force("io.netty:netty-transport:4.2.10.Final")
        force("io.netty:netty-resolver:4.2.10.Final")
        force("com.google.protobuf:protobuf-java:3.25.5")
        force("com.google.protobuf:protobuf-kotlin:3.25.5")
        force("com.google.protobuf:protobuf-java-util:3.25.5")
        force("org.apache.commons:commons-compress:1.27.1")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.google.android.material:material:1.13.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.test:core:1.7.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.5")

    debugImplementation("androidx.compose.ui:ui-tooling:1.10.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.5")
}
