plugins { id("com.android.application") }
android {
    namespace = "com.shari.thumblab"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.shari.thumblab"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
