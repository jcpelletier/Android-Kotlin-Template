import com.android.build.api.variant.VariantOutputConfiguration

plugins {
    id("com.android.application") version "8.1.4"
    id("org.jetbrains.kotlin.android") version "1.9.10"
}

val githubRunNumberProvider = providers.environmentVariable("GITHUB_RUN_NUMBER")
val resolvedRunNumber = githubRunNumberProvider.orElse("1").map(String::toInt)

android {
    namespace = "com.example.helloworld"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.helloworld"
        minSdk = 24
        targetSdk = 34
        versionCode = resolvedRunNumber.getOrElse(1)
        versionName = "1.0.${resolvedRunNumber.getOrElse(1)}"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val buildNumber = githubRunNumberProvider.orElse("1")
        variant.outputs
            .filter { output ->
                output.outputType == VariantOutputConfiguration.OutputType.SINGLE
            }
            .forEach { output ->
                output.outputFileName.set("app-debug-${buildNumber.get()}.apk")
            }
    }
}

dependencies {
    // Compose BOM controls Compose library versions
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

