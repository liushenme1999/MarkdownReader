import com.android.build.api.dsl.ApplicationExtension
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// 与 res/values/strings.xml 中的 app_name 保持一致
val apkBaseName = "MD阅读器"

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.markdownreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.markdownreader"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
                ?: "signing/release.keystore"
            storeFile = rootProject.file(storeFilePath)
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: "markdownreader"
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: "markdownreader"
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: "markdownreader"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        // Hilt/Dagger 构造函数参数注解（如 @ApplicationContext）需要同时作用于 param 和 property
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

val appVersionName = extensions.getByType<ApplicationExtension>()
    .defaultConfig
    .versionName
    ?: "unknown"

listOf("debug", "release").forEach { buildType ->
    val capitalizedBuildType = buildType.replaceFirstChar { it.titlecase() }
    val targetApkName = "${apkBaseName}_${buildType}_${appVersionName}.apk"
    val gradleApkOutputPath = layout.buildDirectory.dir("outputs/apk/$buildType")
    val studioApkOutputPath = layout.projectDirectory.dir(buildType)

    val renameApk = tasks.register("rename${capitalizedBuildType}Apk") {
        notCompatibleWithConfigurationCache("Renames APK files on disk after packaging")
        val targetName = targetApkName
        val gradleOutputPath = gradleApkOutputPath
        val studioOutputPath = studioApkOutputPath

        doLast {
            fun renameInDirectory(directory: File) {
                if (!directory.isDirectory) return

                val targetApk = directory.resolve(targetName)
                if (!targetApk.isFile) {
                    val sourceApk = directory.listFiles()
                        ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                        ?.maxByOrNull { it.lastModified() }
                        ?: return

                    if (sourceApk.name != targetName) {
                        targetApk.delete()
                        check(sourceApk.renameTo(targetApk)) {
                            "Failed to rename ${sourceApk.name} to $targetName in ${directory.path}"
                        }
                    }
                }

                directory.listFiles()
                    ?.filter {
                        it.isFile &&
                            it.extension.equals("apk", ignoreCase = true) &&
                            it.name != targetName
                    }
                    ?.forEach { it.delete() }
            }

            renameInDirectory(gradleOutputPath.get().asFile)

            val studioOutputDir = studioOutputPath.asFile
            val gradleOutputDir = gradleOutputPath.get().asFile
            val renamedGradleApk = gradleOutputDir.resolve(targetName)

            if (renamedGradleApk.isFile) {
                studioOutputDir.mkdirs()
                renamedGradleApk.copyTo(studioOutputDir.resolve(targetName), overwrite = true)
                studioOutputDir.listFiles()
                    ?.filter {
                        it.isFile &&
                            it.extension.equals("apk", ignoreCase = true) &&
                            it.name != targetName
                    }
                    ?.forEach { it.delete() }
            } else {
                renameInDirectory(studioOutputDir)
            }
        }
    }

    tasks.configureEach {
        if (name == "assemble$capitalizedBuildType" || name == "package$capitalizedBuildType") {
            finalizedBy(renameApk)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.image)
    implementation(libs.android.gif.drawable)
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.inline.parser)
    implementation(libs.androidsvg)

    implementation(libs.documentfile)

    implementation(libs.gson)

    implementation(libs.vico.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
