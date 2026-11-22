import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
}

// Keep the runtime fully offline while pinning and verifying the upstream model at build time.
abstract class PrepareArabicOcrModel : DefaultTask() {
    @get:Input abstract val modelUrl: Property<String>
    @get:Input abstract val modelSha256: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val destination = outputDirectory.file("tessdata/ara.traineddata").get().asFile
        check(destination.parentFile.let { it.isDirectory || it.mkdirs() })
        val temp = Files.createTempFile(destination.parentFile.toPath(), "arabic", ".download")
        try {
            val source = URI.create(modelUrl.get()).toURL().openConnection().apply {
                connectTimeout = 20_000
                readTimeout = 20_000
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            source.getInputStream().buffered().use { input ->
                Files.newOutputStream(temp).buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        check(bytes <= 1_432_056L) { "Arabic OCR model is larger than expected." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(actual == modelSha256.get()) { "Arabic OCR model checksum mismatch." }
            Files.move(temp, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}

val prepareArabicOcrModel = tasks.register<PrepareArabicOcrModel>("prepareArabicOcrModel") {
    modelUrl.set("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/65727574dfcd264acbb0c3e07860e4e9e9b22185/ara.traineddata")
    modelSha256.set("e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729")
    outputDirectory.set(layout.buildDirectory.dir("generated/arabic-ocr-assets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(prepareArabicOcrModel, PrepareArabicOcrModel::outputDirectory)
}

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.hamza.studyhub"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hamza.studyhub"
        minSdk = 26
        targetSdk = 37
        versionCode = (System.getenv("VERSION_CODE") ?: "300").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.3.0"
    }

    buildFeatures {
        buildConfig = true
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.microsoft.identity.client:msal:8.4.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.work:work-testing:2.11.2")
}
