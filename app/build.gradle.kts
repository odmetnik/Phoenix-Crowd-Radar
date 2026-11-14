plugins { id("com.android.application") }
android {
    namespace = "hr.phoenixart.crowdradar"
    compileSdk = 35
    defaultConfig {
        applicationId = "hr.phoenixart.crowdradar"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-dev"
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
