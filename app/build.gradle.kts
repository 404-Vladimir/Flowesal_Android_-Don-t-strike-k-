plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val byedpiVersion = "0.1.0"
val hevVersion = "2.17.1"

val byedpiAar = layout.buildDirectory.file("libs/libbyedpi-android-v$byedpiVersion.aar")
val hevJniDir = layout.buildDirectory.dir("generated/hev-jniLibs")

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

val hevAssets = mapOf(
    "arm64-v8a" to "hev-socks5-tunnel-android-arm64-v8a",
    "armeabi-v7a" to "hev-socks5-tunnel-android-armeabi-v7a",
    "x86" to "hev-socks5-tunnel-android-x86",
    "x86_64" to "hev-socks5-tunnel-android-x86_64"
)

val hevOutputs = hevAssets.mapValues { (abi, _) ->
    hevJniDir.map { it.dir(abi).file("libhev-socks5-tunnel.so") }
}

val downloadHev = tasks.register("downloadHevSocks5Tunnel") {
    outputs.files(hevOutputs.values)
    doLast {
        hevAssets.forEach { (abi, assetName) ->
            val target = hevOutputs.getValue(abi).get().asFile
            target.parentFile.mkdirs()
            val url = "https://github.com/heiher/hev-socks5-tunnel/releases/download/$hevVersion/$assetName"
            target.outputStream().use { out ->
                uri(url).toURL().openStream().use { input -> input.copyTo(out) }
            }
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(hevJniDir)
        }
    }
}

dependencies {
    implementation(files(byedpiAar).builtBy(downloadByedpi))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

tasks.named("preBuild") {
    dependsOn(downloadByedpi, downloadHev)
}
