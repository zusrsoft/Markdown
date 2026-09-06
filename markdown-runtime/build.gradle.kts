plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hrm.markdown.runtime"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("consumer-rules.pro"))
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "MarkdownRuntime"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.markdownParser)
            implementation(libs.compose.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    // 仅在显式设置 RELEASE_TO_CENTRAL 环境变量时启用 Central 发布与签名
    // （签名密钥由 CI 注入的 signingInMemoryKey* 环境变量提供；本地 mavenLocal 发布不签名）
    if (providers.environmentVariable("RELEASE_TO_CENTRAL").isPresent) {
        publishToMavenCentral(true)
        signAllPublications()
    }

    coordinates(
        "io.github.zusrsoft",
        "markdown-runtime",
        rootProject.property("VERSION").toString()
    )

    pom {
        name.set("Kotlin Multiplatform Markdown Runtime")
        description.set(
            """
            Runtime extension layer for KMP Markdown with:
            - Input transform pipeline
            - Plugin registry
            - Directive-based extension dispatch
            - Multi-platform support (Android/iOS/JVM/JS/WasmJS)
        """.trimIndent()
        )
        inceptionYear.set("2026")
        url.set("https://github.com/zusrsoft/Markdown")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("zusrsoft")
                name.set("Kotlin Multiplatform Specialist")
                url.set("https://github.com/zusrsoft/")
            }
        }
        scm {
            url.set("https://github.com/zusrsoft/Markdown")
            connection.set("scm:git:git://github.com/zusrsoft/Markdown.git")
            developerConnection.set("scm:git:ssh://git@github.com/zusrsoft/Markdown.git")
        }
    }
}
