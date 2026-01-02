import io.gitlab.arturbosch.detekt.Detekt

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

detekt {
  buildUponDefaultConfig = true // preconfigure defaults
  allRules = false // activate all available (even unstable) rules.
}

tasks.withType<Detekt>().configureEach {
  reports {
    html.required.set(true) // observe findings in your browser with structure and code snippets
  }
}

android {
  namespace = "com.wboelens.polarrecorder"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.wboelens.polarrecorder"
    minSdk = 26
    targetSdk = 35
    versionCode = 22
    versionName = "2.0.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    debug { isDebuggable = true }
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      ndk { debugSymbolLevel = "SYMBOL_TABLE" }
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions { jvmTarget = "11" }

  buildFeatures { compose = true }

  composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

  testOptions { unitTests.all { it.useJUnitPlatform() } }
}

tasks.withType<Test> {
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showStandardStreams = true
  }
}

dependencies {
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.core.testing)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
  coreLibraryDesugaring(libs.android.desugar)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.navigation.compose)
  testImplementation(libs.junit)
  testImplementation(libs.junit5.api)
  testImplementation(libs.junit5.params)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit.vintage.engine)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)

  implementation(libs.polar.ble.sdk)
  implementation(libs.hivemq.mqtt.client.shaded)
  implementation(libs.rxjava)
  implementation(libs.rxandroid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.rx3)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.fragment.ktx)

  implementation(libs.androidx.material3)
  implementation(libs.androidx.material3.windowsizeclass)
  implementation(libs.androidx.material3.adaptive.navigation.suite)
  implementation(libs.androidx.compose.material.iconsExtended)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.runtime.livedata)

  implementation(libs.gson)
}
