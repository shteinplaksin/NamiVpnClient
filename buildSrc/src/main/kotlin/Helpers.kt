import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import java.util.Base64
import java.util.Properties
import kotlin.system.exitProcess

private val Project.android get() = extensions.getByName<ApplicationExtension>("android")

private val Project.androidComponents
    get() = extensions.getByType<ApplicationAndroidComponentsExtension>()

private lateinit var localProperties: Properties

fun Project.requireMetadata(): Properties = Properties().apply {
    load(rootProject.file("nami.properties").inputStream())
}

fun Project.requireLocalProperties(): Properties {
    if (!::localProperties.isInitialized) {
        localProperties = Properties()

        val base64 = System.getenv("LOCAL_PROPERTIES")
        if (!base64.isNullOrBlank()) {

            localProperties.load(Base64.getDecoder().decode(base64).inputStream())
        } else if (project.rootProject.file("local.properties").exists()) {
            localProperties.load(rootProject.file("local.properties").inputStream())
        }
    }
    return localProperties
}

fun Project.setupCommon() {
    android.apply {
        buildToolsVersion = "37.0.0"
        compileSdk = 37
        // Keep the NDK aligned with the checked-in/prebuilt libcore AAR. Updating
        // this value would require rebuilding the frozen Go toolchain artifact.
        ndkVersion = "25.2.9519653"
        defaultConfig {
            minSdk = 24
            targetSdk = 37
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        lint {
            showAll = true
            checkAllWarnings = true
            checkReleaseBuilds = true
            warningsAsErrors = true
            textOutput = project.file("build/lint.txt")
            htmlOutput = project.file("build/lint.html")
        }
        packaging {
            resources.excludes.addAll(
                listOf(
                    "**/*.kotlin_*",
                    "/META-INF/*.version",
                    "/META-INF/native/**",
                    "/META-INF/native-image/**",
                    "/META-INF/INDEX.LIST",
                    "DebugProbesKt.bin",
                    "com/**",
                    "org/**",
                    "**/*.java",
                    "**/*.proto",
                    "okhttp3/**"
                )
            )
        }
        buildTypes {
            getByName("release") {
                isShrinkResources = true
                if (System.getenv("nkmr_minify") == "0") {
                    isShrinkResources = false
                    isMinifyEnabled = false
                }
            }
            getByName("debug") {
                applicationIdSuffix = "debug"
                isDebuggable = true
                isJniDebuggable = true
            }
        }
    }
}

fun Project.setupAppCommon() {
    setupCommon()

    val lp = requireLocalProperties()
    val keystorePwd = lp.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS")
    val alias = lp.getProperty("ALIAS_NAME") ?: System.getenv("ALIAS_NAME")
    val pwd = lp.getProperty("ALIAS_PASS") ?: System.getenv("ALIAS_PASS")

    android.apply {
        if (keystorePwd != null) {
            signingConfigs {
                create("release") {
                    storeFile = rootProject.file("release.keystore")
                    storePassword = keystorePwd
                    keyAlias = alias
                    keyPassword = pwd
                }
            }
        }
        buildTypes {
            val key = signingConfigs.findByName("release")
            if (key != null) {
                getByName("release").signingConfig = key
                getByName("debug").signingConfig = key
            }
        }
    }
}

fun Project.setupApp() {
    val pkgName = requireMetadata().getProperty("PACKAGE_NAME")
    val verName = requireMetadata().getProperty("VERSION_NAME")
    val verCode = (requireMetadata().getProperty("VERSION_CODE").toInt()) * 5
    android.apply {
        defaultConfig {
            applicationId = pkgName
            versionCode = verCode
            versionName = verName
            buildConfigField(
                "String",
                "PRE_VERSION_NAME",
                "\"${requireMetadata().getProperty("PRE_VERSION_NAME")}\"",
            )
        }
    }
    setupAppCommon()

    // Version metadata is external to the Gradle script. Make it an explicit
    // task input so changing nami.properties invalidates generated manifests,
    // BuildConfig, and release artifacts even when a daemon is reused.
    tasks.configureEach {
        inputs.file(rootProject.file("nami.properties"))
    }

    android.apply {
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro")
                )
            }
        }

        splits.abi {
            reset()
            isEnable = true
            isUniversalApk = false
            include("armeabi-v7a")
            include("arm64-v8a")
            include("x86")
            include("x86_64")
        }

        flavorDimensions += "vendor"
        productFlavors {
            create("oss")
            create("fdroid")
            create("play")
            create("preview") {
                buildConfigField(
                    "String",
                    "PRE_VERSION_NAME",
                    "\"${requireMetadata().getProperty("PRE_VERSION_NAME")}\""
                )
            }
        }

        for (abi in listOf("Arm64", "Arm", "X64", "X86")) {
            tasks.register("assemble" + abi + "FdroidRelease") {
                dependsOn("assembleFdroidRelease")
            }
        }

        sourceSets.getByName("main").apply {
            jniLibs.srcDir("executableSo")
        }
    }

    androidComponents.onVariants(androidComponents.selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val currentName = output.outputFileName.orNull ?: return@forEach
            val outputVersionName = output.versionName.orNull ?: verName
            val isPreview = variant.name.contains("preview", ignoreCase = true)
            output.outputFileName.set(
                if (isPreview) {
                    currentName.replace(
                        project.name,
                        "Nami-$verName-" + requireMetadata().getProperty("PRE_VERSION_NAME")
                    ).replace("-preview", "")
                } else {
                    currentName.replace(project.name, "Nami-$outputVersionName")
                        .replace("-release", "")
                        .replace("-oss", "")
                }
            )
        }
    }
}
