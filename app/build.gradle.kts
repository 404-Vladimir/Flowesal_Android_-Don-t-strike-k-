plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val byedpiVersion = "0.1.0"
val hevVersion = "2.17.1"
val byedpiAar = layout.buildDirectory.file("libs/libbyedpi-android-v$byedpiVersion.aar")
val hevAar = layout.buildDirectory.file("libs/hev-socks5-tunnel.aar")

fun downloadArtifactTask(name: String, url: String, output: Provider<RegularFile>) = tasks.register(name) {
    outputs.file(output)
    doLast {
        val target = output.get().asFile
        target.parentFile.mkdirs()
        target.outputStream().use { out ->
            uri(url).toURL().openStream().use { input -> input.copyTo(out) }
        }
    }
}

val downloadByedpi = downloadArtifactTask(
    "downloadByedpi",
    "https://github.com/oviron/libbyedpi-android/releases/download/v$byedpiVersion/libbyedpi-android-v$byedpiVersion.aar",
    byedpiAar
)

val downloadHev = downloadArtifactTask(
    "downloadHevSocks5Tunnel",
    "https://github.com/heiher/hev-socks5-tunnel/releases/download/$hevVersion/hev-socks5-tunnel.aar",
    hevAar
)

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
    implementation(files(hevAar).builtBy(downloadHev))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

tasks.named("preBuild") {
    dependsOn(downloadByedpi, downloadHev)
}
