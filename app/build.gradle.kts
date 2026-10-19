import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Online features (leaderboard, challenges) need app/google-services.json from the Firebase console.
// Without it the app still builds and runs, with those features shown as unavailable.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Optional local settings (PRIVACY_POLICY_URL, REPORT_EMAIL) from local.properties, never committed.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.xmentarik.quiz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xmentarik.quiz"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // The developer's Google Sites page (a copy lives in docs/privacy-policy.html); override in local.properties.
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"${localProperties.getProperty("PRIVACY_POLICY_URL", "https://sites.google.com/view/roadtoamillion")}\"",
        )
        // Where question reports go when Firebase is not configured (opens the email app); empty hides the button.
        buildConfigField("String", "REPORT_EMAIL", "\"${localProperties.getProperty("REPORT_EMAIL", "")}\"")
    }

    buildTypes {
        // AdMob: Google's sample ids in debug (never risk clicks on real ads), the real ones in release.
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "AD_UNIT_APP_OPEN", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "AD_UNIT_BANNER", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_UNIT_NATIVE", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-9412462078913157~9180499717"
            buildConfigField("String", "AD_UNIT_APP_OPEN", "\"ca-app-pub-9412462078913157/8146444634\"")
            buildConfigField("String", "AD_UNIT_BANNER", "\"ca-app-pub-9412462078913157/1553116125\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"ca-app-pub-9412462078913157/2207897257\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"ca-app-pub-9412462078913157/3245646756\"")
            buildConfigField("String", "AD_UNIT_NATIVE", "\"ca-app-pub-9412462078913157/1932565085\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.play.review)
    implementation(libs.androidx.lifecycle.process)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
