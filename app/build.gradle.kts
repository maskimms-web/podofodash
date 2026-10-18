plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.golf.candiagnostic"
 compileSdk = 35
 defaultConfig { applicationId = "com.golf.candiagnostic"; minSdk = 26; targetSdk = 28; versionCode = 2; versionName = "0.2.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
