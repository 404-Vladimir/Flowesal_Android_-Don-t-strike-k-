plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val byedpiVersion = "0.1.0"
val byedpiAar = layout.buildDirectory.file("libs/libbyedpi-android-v$byedpiVersion.aar")

val downloadByedpi = tasks.register("downloadByedpi") {
    inputs.property("byedpiVersion", byedpiVersion)
    outputs.file(byedpiAar)
    doLast {
        val target = byedpiAar.get().asFile
        target.parentFile.mkdirs()
        val url = "https://github.com/oviron/libbyedpi-android/releases/download/v$byedpiVersion/libbyedpi-android-v$byedpiVersion.aar"
        target.outputStream().use { out ->
            uri(url).toURL().openStream().use { input -> input.copyTo(out) }
        }
    }
}

android {
    namespace = "com.flowesal.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flowesal.vpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(files(byedpiAar).builtBy(downloadByedpi))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

tasks.named("preBuild") {
    dependsOn(downloadByedpi)
}
