plugins { id("com.android.application") }
android {
    namespace = "hr.phoenixart.crowdradar"
    compileSdk = 36
    defaultConfig {
        applicationId = "hr.phoenixart.crowdradar"
        minSdk = 31
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 5
        versionName = "0.5.0-dev"
    }
    signingConfigs {
        create("development") {
            storeFile = file("../signing/phoenix-development.jks")
            storePassword = "android"
            keyAlias = "phoenix-development"
            keyPassword = "android"
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("development") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core.uwb:uwb-rxjava3:1.0.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.android.gms:play-services-nearby:19.5.0")
}

val verifyHelp by tasks.registering {
    inputs.file("src/main/assets/help.txt")
    doLast {
        val expected = android.defaultConfig.versionName ?: error("Missing APK version")
        check(file("src/main/assets/help.txt").readText().startsWith("VERSION: $expected\n")) {
            "Update in-app Help for APK version $expected"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyHelp) }
