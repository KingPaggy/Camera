import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // AGP 已由 root build.gradle.kts 通过 alias(libs.plugins.android).apply(false)
    // 加入构建类路径；此处不带版本号引用，版本统一由 libs.versions.toml 管理。
    id("com.android.library")
}

android {
    namespace = "org.fossify.camera.drawcore"

    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        // 说明：Android library 模块由宿主 app 决定 targetSdk（app=36），
        // AGP 9 的 library DSL 不提供 targetSdk 入口，故此处不设置。
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }
}

dependencies {
    api(libs.androidx.core.ktx)
}
