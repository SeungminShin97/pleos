import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.seungminshin97.japeace"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.seungminshin97.japeace"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "io.github.seungminshin97.japeace.validation.StorageValidationInstrumentation"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "자.peace 데모")
        }
        create("vehicle") {
            dimension = "environment"
            resValue("string", "app_name", "자.peace")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    "vehicleImplementation"(libs.pleos.vehicle)
    "vehicleCompileOnly"(files(androidComponents.sdkComponents.sdkDirectory.map {
        it.file("platforms/android-35/optional/android.car.jar")
    }))
    androidTestCompileOnly(files(androidComponents.sdkComponents.sdkDirectory.map {
        it.file("platforms/android-35/optional/android.car.jar")
    }))
    testImplementation(libs.junit)
}
