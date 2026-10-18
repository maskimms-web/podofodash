plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.golf.candiagnostic"
 compileSdk = 35
 defaultConfig { applicationId = "com.golf.candiagnostic"; minSdk = 26; targetSdk = 28; versionCode = 3; versionName = "0.3" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
