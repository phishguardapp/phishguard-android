# PhishGuard Android - Implementation Plan

## Quick Start Guide

This document provides a step-by-step plan to build PhishGuard for Android.

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34+
- Kotlin 1.9+
- Git

## Phase 1: Project Setup (Week 1)

### Step 1: Create Android Project

1. Open Android Studio
2. New Project → Empty Activity
3. Configure:
   - Name: PhishGuard
   - Package: com.phishguard.android
   - Language: Kotlin
   - Minimum SDK: API 33 (Android 13)
   - Target SDK: API 34 (Android 14)
   - Build system: Gradle (Kotlin DSL)

### Step 2: Configure build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.phishguard.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phishguard.android"
        minSdk = 33  // Android 13 - ~55% market coverage
        targetSdk = 34  // Android 14
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // ML
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.android.gms:play-services-mlkit-aicore:16.0.0")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.1")
    
    // Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // DI
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Domain parsing
    implementation("com.google.guava:guava:32.1.3-android")
}
```

### Step 3: Project Structure

