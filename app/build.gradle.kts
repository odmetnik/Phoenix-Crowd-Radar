plugins { id("com.android.application") }
android {
    namespace = "hr.phoenixart.crowdradar"
    compileSdk = 35
    defaultConfig {
        applicationId = "hr.phoenixart.crowdradar"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-dev"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.activity:activity:1.9.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.android.gms:play-services-nearby:19.5.0")
}
