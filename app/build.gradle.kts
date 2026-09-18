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

fun Properties.signingValue(key: String): String? =
    getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFilePath = localProperties.signingValue("RELEASE_STORE_FILE")
val releaseStorePassword = localProperties.signingValue("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProperties.signingValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProperties.signingValue("RELEASE_KEY_PASSWORD")

val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

android {
    namespace = "space.liushenme.markdownreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "space.liushenme.markdownreader"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

gradle.taskGraph.whenReady {
    val requestsReleaseArtifact = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("Release", ignoreCase = true) &&
            (
                taskName.contains("assemble", ignoreCase = true) ||
                    taskName.contains("bundle", ignoreCase = true) ||
                    taskName.contains("package", ignoreCase = true) ||
                    taskName.contains("sign", ignoreCase = true)
                )
    }
    if (!requestsReleaseArtifact) return@whenReady

    if (!releaseSigningConfigured) {
        error(
            """
            Release 构建需要签名配置，请在项目根目录 local.properties 或环境变量中设置：
              RELEASE_STORE_FILE
              RELEASE_STORE_PASSWORD
              RELEASE_KEY_ALIAS
              RELEASE_KEY_PASSWORD
            不要将密码提交到 Git 仓库。
            """.trimIndent(),
        )
    }

    val keystore = rootProject.file(releaseStoreFilePath!!)
    if (!keystore.isFile) {
        error("Release 签名 keystore 不存在: ${keystore.absolutePath}")
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
    val defaultApkName = "app-$buildType.apk"
    val gradleApkOutputPath = layout.buildDirectory.dir("outputs/apk/$buildType")
    // Android Studio / AGP 的 ApkListingFileRedirect 会把产物同步到 app/<buildType>/
    val studioApkOutputPath = layout.projectDirectory.dir(buildType)

    val renameApk = tasks.register("rename${capitalizedBuildType}Apk") {
        notCompatibleWithConfigurationCache("Renames APK files on disk after packaging")
        val targetName = targetApkName
        val defaultName = defaultApkName
        val buildTypeName = buildType
        val baseName = apkBaseName
        val gradleOutputPath = gradleApkOutputPath
        val studioOutputPath = studioApkOutputPath

        doLast {
            fun renameDefaultApkIn(directory: File) {
                if (!directory.isDirectory) return
                val targetApk = directory.resolve(targetName)
                val defaultApk = directory.resolve(defaultName)
                when {
                    targetApk.isFile && defaultApk.isFile -> defaultApk.delete()
                    !targetApk.isFile && defaultApk.isFile -> {
                        check(defaultApk.renameTo(targetApk)) {
                            "Failed to rename ${defaultApk.name} to $targetName in ${directory.path}"
                        }
                    }
                    !targetApk.isFile -> {
                        // 兼容偶发非默认命名：取最新 apk 改名
                        val sourceApk = directory.listFiles()
                            ?.filter {
                                it.isFile &&
                                    it.extension.equals("apk", ignoreCase = true) &&
                                    !it.name.startsWith(baseName)
                            }
                            ?.maxByOrNull { it.lastModified() }
                            ?: return
                        check(sourceApk.renameTo(targetApk)) {
                            "Failed to rename ${sourceApk.name} to $targetName in ${directory.path}"
                        }
                    }
                }
            }

            fun patchOutputMetadata(directory: File) {
                val metadata = directory.resolve("output-metadata.json")
                if (!metadata.isFile) return
                val original = metadata.readText()
                val updated = original.replace(
                    Regex(""""outputFile"\s*:\s*"app-$buildTypeName\.apk""""),
                    """"outputFile": "$targetName"""",
                )
                if (updated != original) {
                    metadata.writeText(updated)
                }
            }

            val gradleOutputDir = gradleOutputPath.get().asFile
            val studioOutputDir = studioOutputPath.asFile

            renameDefaultApkIn(gradleOutputDir)

            val renamedGradleApk = gradleOutputDir.resolve(targetName)
            if (renamedGradleApk.isFile) {
                // 复制到 app/<buildType>/ 供 README / Studio 定位；只保留当前版本 APK
                studioOutputDir.mkdirs()
                renamedGradleApk.copyTo(studioOutputDir.resolve(targetName), overwrite = true)
                studioOutputDir.listFiles()
                    ?.filter {
                        it.isFile &&
                            it.extension.equals("apk", ignoreCase = true) &&
                            it.name.startsWith("${baseName}_") &&
                            it.name != targetName
                    }
                    ?.forEach { it.delete() }
            }

            // Studio 的 listing redirect 常在 assemble 末尾写入 app-release.apk，必须在此之后清理
            renameDefaultApkIn(studioOutputDir)
            patchOutputMetadata(studioOutputDir)
            patchOutputMetadata(gradleOutputDir)
        }
    }

    // 只挂在 assemble* 上，确保排在 create*ApkListingFileRedirect 之后执行
    tasks.matching { it.name == "assemble$capitalizedBuildType" }.configureEach {
        finalizedBy(renameApk)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
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
    implementation(libs.okhttp)
    implementation(libs.jgit)

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
