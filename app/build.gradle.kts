import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.google.services)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val releaseVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

android {
    namespace = "com.girocerto.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.girocerto.app"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
        testInstrumentationRunner = "com.girocerto.test.GiroCertoTestRunner"
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api30") { device = "Pixel 2"; apiLevel = 30; systemImageSource = "aosp"; testedAbi = "x86_64" }
                create("pixel2api35") { device = "Pixel 2"; apiLevel = 35; systemImageSource = "aosp"; testedAbi = "x86_64" }
            }
        }
    }

    signingConfigs {
        create("production") {
            providers.environmentVariable("RELEASE_STORE_FILE").orNull?.let { storeFile = file(it) }
            storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      buildConfig = true
      aidl = false
      shaders = false
    }
    defaultConfig {
        buildConfigField("String", "CLOUD_BACKUP_URL", "\"https://br-mute-recipe-be1746pa-backups.compute.c-9.us-east-2.aws.neon.tech/\"")
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("androidx.credentials:credentials:1.5.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation("androidx.work:work-runtime-ktx:2.11.0")

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

val prepareChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("changelog.json"))
    into(layout.buildDirectory.dir("generated/changelogAssets"))
    inputs.property("expectedVersion", releaseVersion.getProperty("versionName"))
    doFirst {
        val changes = groovy.json.JsonSlurper().parse(inputs.files.singleFile) as List<*>
        check((changes.first() as Map<*,*>)["version"] == inputs.properties["expectedVersion"]) { "Changelog and version must match" }
    }
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/changelogAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(prepareChangelog) }
