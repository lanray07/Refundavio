plugins {
    id("com.android.application") version "8.11.1"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("com.google.devtools.ksp") version "2.1.20-1.0.32"
}
android {
    namespace = "app.refundavio"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.refundavio"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.2.10"
        buildConfigField("String", "PREMIUM_SKU", "\"app.refundavio.premium\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
    buildTypes {
        debug {
            val qa = providers.gradleProperty("refundavioQa").orNull
            if (qa != null) applicationIdSuffix = if (qa == "billing") ".qa.billing" else ".qa"
            val sandboxUrl = if (qa != null)
                "http://127.0.0.1:8765/verify" else ""
            buildConfigField("String", "PREMIUM_VERIFIER", "\"$sandboxUrl\"")
        }
        release {
            val lat = providers.gradleProperty("refundavioLat").orNull == "true"
            val verifier = providers.gradleProperty("refundavioPremiumVerifier").orElse("").get()
            require(verifier.isEmpty() || Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?/[A-Za-z0-9/_-]+").matches(verifier)) {
                "refundavioPremiumVerifier must be an HTTPS endpoint"
            }
            if (lat) {
                require(verifier.endsWith("/verify-lat")) { "The LAT build requires the private test verifier route" }
                buildConfigField("String", "PREMIUM_SKU", "\"app.refundavio.premium.lat\"")
            } else {
                require(!verifier.endsWith("/verify-lat")) { "Public releases must use the public verifier route" }
            }
            buildConfigField("String", "PREMIUM_VERIFIER", "\"$verifier\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}
dependencies {
    implementation("com.amazon.device:amazon-appstore-sdk:3.0.9")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-core:1.7.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
